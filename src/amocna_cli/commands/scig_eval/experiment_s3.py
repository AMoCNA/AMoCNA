"""Experiment S3: Multi-app scanning scalability with real namespace scoping."""

from __future__ import annotations

import json
from pathlib import Path

from rich.console import Console

from . import k8s_helpers as kh
from .latex_generator import generate_s3_scalability_table

console = Console()

SCAN_ROUNDS = [
    {"name": "Sock Shop", "namespaces": "sock-shop"},
    {"name": "Sock Shop + BookInfo", "namespaces": "sock-shop,bookinfo"},
    {"name": "All 3 Applications", "namespaces": "sock-shop,bookinfo,online-boutique"},
]


def run_s3(
    iterations: int,
    output_dir: Path,
    redis_host: str = "localhost",
    redis_port: int = 6379,
    scan_timeout_s: int = 7200,
) -> dict:
    console.print(
        f"[bold green]S3: multi-app scan scalability "
        f"({iterations} iterations, discover-only per namespace scope)[/bold green]"
    )
    results: dict = {}

    for round_info in SCAN_ROUNDS:
        round_name = round_info["name"]
        namespaces = round_info["namespaces"]
        console.print(f"[bold]Evaluating {round_name} (ns={namespaces})...[/bold]")

        round_data = {
            "namespaces": namespaces,
            "image_count": 0,
            "total_times_s": [],
            "per_image_times_s": [],
            "redis_mem_mb": [],
            "cve_count": 0,
            "overhead": [],
        }

        for it in range(iterations):
            console.print(f"  Iteration {it + 1}/{iterations}")
            _, elapsed_s = kh.run_scig_scan(
                namespaces=namespaces,
                timeout_s=scan_timeout_s,
                discover=True,
                image_list_only=False,
            )

            metas = kh.collect_sbom_meta(redis_host, redis_port)
            # Count only metas whose image likely belongs to scanned namespaces by scannedAt recency
            # Prefer counting unique images discovered: use meta count that matches live discover scope.
            # Practical approach: recount unique repos from latest scan by reading job isn't available;
            # use current meta keys filtered by known app markers for this round.
            markers = []
            if "sock-shop" in namespaces:
                markers.append("weaveworksdemos")
            if "bookinfo" in namespaces:
                markers.append("istio")
            if "online-boutique" in namespaces:
                markers.append("microservices-demo")

            scoped = [
                m for m in metas
                if any(mk in (m.get("repository") or "") or mk in (m.get("imageRef") or "") for mk in markers)
            ]
            image_count = max(len(scoped), 1)
            cve_total = sum(int(m.get("vulnerabilityCount", 0) or 0) for m in scoped)

            round_data["image_count"] = len(scoped)
            round_data["cve_count"] = cve_total
            round_data["total_times_s"].append(elapsed_s)
            round_data["per_image_times_s"].append(elapsed_s / image_count)
            mem = kh.redis_used_memory_mb(redis_host, redis_port)
            round_data["redis_mem_mb"].append(mem)
            round_data["overhead"].append({
                "palamedes": kh.pod_metrics("palamedes", "app=palamedes"),
                "redis_mb": mem,
            })
            console.print(
                f"    {elapsed_s:.1f}s, images={len(scoped)}, CVEs={cve_total}, Redis={mem:.1f}MB"
            )

        results[round_name] = round_data

    output_dir.mkdir(parents=True, exist_ok=True)
    with open(output_dir / "s3_results.json", "w") as f:
        json.dump(results, f, indent=2)
    with open(output_dir / "s3_scalability_table.tex", "w") as f:
        f.write(generate_s3_scalability_table(results))

    console.print("[bold green]Experiment S3 completed.[/bold green]")
    return results
