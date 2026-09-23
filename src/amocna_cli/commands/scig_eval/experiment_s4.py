"""Experiment S4: Redis CVE parse + catalog merge + SPARQL VALUES clause latency."""

from __future__ import annotations

import json
import time
from pathlib import Path

from rich.console import Console

from . import k8s_helpers as kh
from .latex_generator import generate_s4_policy_latency_table

console = Console()

RECORD_COUNTS = [10, 50, 100, 250, 500]


def _load_cve_records(host: str, port: int, max_keys: int = 15, max_records: int = 600) -> list[dict]:
    """Parse a bounded subset of Grype Redis keys into catalog-like records."""
    keys = kh.redis_scan_keys(host, port, "sbom:cve:*")[:max_keys]
    records: list[dict] = []
    for key in keys:
        # Skip pathological blobs (same policy as ScigRedisSyncService).
        raw = kh.redis_cli(host, port, "STRLEN", key, check=False)
        try:
            if int(raw) > 2_000_000:
                console.print(f"[yellow]Skipping oversized key {key} ({raw} bytes)[/yellow]")
                continue
        except ValueError:
            pass
        parts = key.split(":")
        if len(parts) < 4:
            continue
        # repository may contain host; take everything between sbom:cve: and last :tag
        rest = key[len("sbom:cve:") :]
        last = rest.rfind(":")
        if last <= 0:
            continue
        repo, tag = rest[:last], rest[last + 1 :]
        # Align with Metis resourceName (no registry host)
        if "/" in repo:
            host_part, path = repo.split("/", 1)
            if "." in host_part or ":" in host_part or host_part == "localhost":
                repo = path
        data = kh.redis_get_json(host, port, key)
        if not data:
            continue
        for match in data.get("matches") or []:
            vuln = match.get("vulnerability") or {}
            cve_id = vuln.get("id") or "UNKNOWN"
            severity = vuln.get("severity") or "Medium"
            records.append({
                "id": cve_id,
                "imageRepository": repo,
                "affectedVersions": [tag],
                "fixedVersions": [],  # package fixes ≠ image tags
                "severity": severity,
            })
            if len(records) >= max_records:
                return records
    return records


def _merge_records(base: list[dict], incoming: list[dict]) -> list[dict]:
    """Mirror VulnerabilityCatalog.mergeRecords uniqueness (id + imageRepository)."""
    out = list(base)
    seen = {(r["id"].lower(), r["imageRepository"].lower()) for r in out}
    for rec in incoming:
        key = (rec["id"].lower(), rec["imageRepository"].lower())
        if key not in seen:
            out.append(rec)
            seen.add(key)
    return out


def _sparql_values_clause(records: list[dict]) -> str:
    """Match VulnerabilityCatalog.toSparqlValuesClause (no commas between tuples)."""
    pairs = set()
    for r in records:
        for ver in r.get("affectedVersions") or []:
            pairs.add((r["imageRepository"], ver))
    return "\n    ".join(f'("{repo}" "{ver}")' for repo, ver in sorted(pairs))


def run_s4(
    iterations: int,
    output_dir: Path,
    redis_host: str = "localhost",
    redis_port: int = 6379,
) -> dict:
    console.print(
        f"[bold green]S4: Redis parse / merge / SPARQL-clause latency "
        f"({iterations} iterations)[/bold green]"
    )

    keys = kh.redis_scan_keys(redis_host, redis_port, "sbom:cve:*")
    if not keys:
        console.print("[yellow]No sbom:cve:* keys — running one SCIG scan first...[/yellow]")
        kh.run_scig_scan(
            namespaces="sock-shop,bookinfo,online-boutique",
            discover=False,
            image_list_only=True,
        )

    # One-time Redis load (wall cost reported separately); microbench uses in-memory records.
    t_load0 = time.perf_counter()
    all_records = _load_cve_records(redis_host, redis_port)
    redis_load_ms = (time.perf_counter() - t_load0) * 1000.0
    console.print(
        f"Loaded {len(all_records)} CVE records from Redis "
        f"in {redis_load_ms:.0f} ms (one-shot; not scaled with N)"
    )
    if not all_records:
        console.print("[red]No CVE records available — S4 cannot measure meaningfully[/red]")
        all_records = [
            {
                "id": f"CVE-SYNTH-{i}",
                "imageRepository": "example/app",
                "affectedVersions": ["1.0.0"],
                "fixedVersions": [],
                "severity": "High",
            }
            for i in range(500)
        ]

    results: dict = {
        "_meta": {
            "redis_load_ms": redis_load_ms,
            "records_available": len(all_records),
            "note": (
                "sync_ms = re-serialize/parse of N records (proxy for catalog ingest shape); "
                "merge_ms/sparql_ms = in-process mirrors of VulnerabilityCatalog; "
                "throughput = N / (sync+merge+sparql)."
            ),
        }
    }
    for count in RECORD_COUNTS:
        console.print(f"[bold]N={count} CVE records...[/bold]")
        rec_data = {
            "sync_ms": [],
            "merge_ms": [],
            "sparql_ms": [],
            "throughput_evts": [],
        }
        subset = all_records[: min(count, len(all_records))]
        while len(subset) < count:
            subset = subset + all_records[: min(count - len(subset), len(all_records))]
        subset = subset[:count]
        payload = json.dumps(subset)

        for _ in range(iterations):
            t0 = time.perf_counter()
            parsed = json.loads(payload)
            sync_ms = (time.perf_counter() - t0) * 1000.0

            t0 = time.perf_counter()
            merged = _merge_records([], parsed)
            merge_ms = (time.perf_counter() - t0) * 1000.0

            t0 = time.perf_counter()
            clause = _sparql_values_clause(merged)
            sparql_ms = (time.perf_counter() - t0) * 1000.0
            assert clause  # non-empty for N>=1

            tot_s = (sync_ms + merge_ms + sparql_ms) / 1000.0
            throughput = count / tot_s if tot_s > 0 else 0.0

            rec_data["sync_ms"].append(sync_ms)
            rec_data["merge_ms"].append(merge_ms)
            rec_data["sparql_ms"].append(sparql_ms)
            rec_data["throughput_evts"].append(throughput)

        results[str(count)] = rec_data

    output_dir.mkdir(parents=True, exist_ok=True)
    with open(output_dir / "s4_results.json", "w") as f:
        json.dump(results, f, indent=2)
    with open(output_dir / "s4_policy_latency.tex", "w") as f:
        f.write(generate_s4_policy_latency_table(results))

    console.print("[bold green]Experiment S4 completed.[/bold green]")
    return results
