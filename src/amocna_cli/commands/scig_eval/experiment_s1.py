"""Experiment S1: SBOM generation and CVE detection — real Job completion + Redis meta."""

from __future__ import annotations

import json
from pathlib import Path

from rich.console import Console

from . import k8s_helpers as kh
from .latex_generator import (
    generate_s1_app_summary_table,
    generate_s1_per_image_table,
    generate_s1_scanner_comparison_table,
    generate_s1_severity_distribution_table,
)
from .metrics import jaccard_similarity

console = Console()

IMAGE_APP_MAP = {
    "weaveworksdemos": "Sock Shop",
    "istio": "BookInfo",
    "microservices-demo": "Online Boutique",
}


def detect_app(image: str) -> str:
    for key, app_name in IMAGE_APP_MAP.items():
        if key in image:
            return app_name
    return "Benchmark"


def detect_language(image: str) -> str:
    """Best-effort language label for tables; check specific names before substrings."""
    name = image.lower().rsplit("/", 1)[-1]
    # Longer / more specific tokens first (avoid "carts" matching cartservice).
    rules = [
        ("cartservice", "C#"),
        ("shippingservice", "Go"),
        ("productcatalogservice", "Go"),
        ("checkoutservice", "Go"),
        ("currencyservice", "Node.js"),
        ("paymentservice", "Node.js"),
        ("emailservice", "Python"),
        ("recommendationservice", "Python"),
        ("adservice", "Java"),
        ("frontend", "Go"),  # Online Boutique frontend (Go)
        ("front-end", "Node.js"),  # Sock Shop
        ("productpage", "Python"),
        ("details", "Ruby"),
        ("ratings", "Node.js"),
        ("reviews", "Java"),
        ("orders", "Java"),
        ("carts", "Java"),
        ("shipping", "Java"),
        ("queue-master", "Java"),
        ("catalogue", "Go"),
        ("payment", "Go"),
        ("user", "Go"),
    ]
    for token, lang in rules:
        if token in name:
            return lang
    return "Unknown"


def _meta_key(m: dict) -> str:
    return f"{m.get('repository', '')}:{m.get('tag', '')}"


def run_s1(
    images: list[str],
    iterations: int,
    output_dir: Path,
    redis_host: str = "localhost",
    redis_port: int = 6379,
    scan_timeout_s: int = 7200,
) -> dict:
    console.print(
        f"[bold green]S1: real SCIG scan ({iterations} iterations, "
        f"image-list mode, wait-for-Job-complete)[/bold green]"
    )

    results: dict = {}
    for img in images:
        results[img] = {
            "app": detect_app(img),
            "language": detect_language(img),
            "syft_latencies_ms": [],
            "grype_latencies_ms": [],
            "trivy_latencies_ms": [],
            "packages": 0,
            "grype_cves": {"total": 0, "critical": 0, "high": 0, "medium": 0, "low": 0},
            "trivy_cves": {"total": 0, "critical": 0, "high": 0, "medium": 0},
        }

    all_grype: set[str] = set()
    all_trivy: set[str] = set()
    job_times_s: list[float] = []
    overhead_samples: list[dict] = []

    for it in range(1, iterations + 1):
        console.print(f"[bold]Iteration {it}/{iterations}: SCIG Job (full completion)...[/bold]")
        _, elapsed_s = kh.run_scig_scan(
            namespaces="sock-shop,bookinfo,online-boutique",
            timeout_s=scan_timeout_s,
            discover=False,
            image_list_only=True,
        )
        job_times_s.append(elapsed_s)
        console.print(f"  Job completed in {elapsed_s:.1f}s")

        overhead_samples.append({
            "scig_job_s": elapsed_s,
            "palamedes": kh.pod_metrics("palamedes", "app=palamedes"),
            "redis_mb": kh.redis_used_memory_mb(redis_host, redis_port),
        })

        metas = kh.collect_sbom_meta(redis_host, redis_port)
        meta_by_ref = {}
        for m in metas:
            ref = m.get("imageRef") or f"{m.get('repository')}:{m.get('tag')}"
            meta_by_ref[ref] = m
            # also index without registry prefix variants
            repo = m.get("repository", "")
            tag = m.get("tag", "")
            meta_by_ref[f"{repo}:{tag}"] = m

        for img in images:
            m = meta_by_ref.get(img)
            if m is None:
                # fuzzy: match by trailing repo:tag
                suffix = img.split("docker.io/")[-1] if "docker.io/" in img else img
                for key, val in meta_by_ref.items():
                    if key.endswith(suffix) or suffix.endswith(f"{val.get('repository')}:{val.get('tag')}"):
                        m = val
                        break
            if m is None:
                console.print(f"  [yellow]No Redis meta for {img}[/yellow]")
                continue

            entry = results[img]
            entry["packages"] = int(m.get("packageCount", 0) or 0)
            entry["grype_cves"] = {
                "total": int(m.get("vulnerabilityCount", 0) or 0),
                "critical": int(m.get("criticalCount", 0) or 0),
                "high": int(m.get("highCount", 0) or 0),
                "medium": int(m.get("mediumCount", 0) or 0),
                "low": int(m.get("lowCount", 0) or 0),
            }
            entry["trivy_cves"] = {
                "total": int(m.get("trivyVulnerabilityCount", 0) or 0),
                "critical": int(m.get("trivyCriticalCount", 0) or 0),
                "high": int(m.get("trivyHighCount", 0) or 0),
                "medium": int(m.get("trivyMediumCount", 0) or 0),
            }
            if m.get("syftDurationMs") is not None:
                entry["syft_latencies_ms"].append(float(m["syftDurationMs"]))
            if m.get("grypeDurationMs") is not None:
                entry["grype_latencies_ms"].append(float(m["grypeDurationMs"]))
            if m.get("trivyDurationMs") is not None:
                entry["trivy_latencies_ms"].append(float(m["trivyDurationMs"]))

            repo = m.get("repository", "")
            tag = m.get("tag", "")
            all_grype |= kh.extract_cve_ids_from_grype(redis_host, redis_port, repo, tag)
            all_trivy |= kh.extract_cve_ids_from_trivy(redis_host, redis_port, repo, tag)

    intersection = all_grype & all_trivy
    scanner_comp = {
        "grype_total_unique": len(all_grype),
        "trivy_total_unique": len(all_trivy),
        "intersection": len(intersection),
        "grype_only": len(all_grype - all_trivy),
        "trivy_only": len(all_trivy - all_grype),
        "jaccard_similarity": jaccard_similarity(all_grype, all_trivy),
        "job_times_s": job_times_s,
        "overhead_samples": overhead_samples,
    }

    output_dir.mkdir(parents=True, exist_ok=True)
    with open(output_dir / "s1_results.json", "w") as f:
        json.dump(results, f, indent=2)
    with open(output_dir / "s1_scanner_comparison.json", "w") as f:
        json.dump(scanner_comp, f, indent=2)
    with open(output_dir / "s1_per_image_table.tex", "w") as f:
        f.write(generate_s1_per_image_table(results))
    with open(output_dir / "s1_app_summary_table.tex", "w") as f:
        f.write(generate_s1_app_summary_table(results))
    with open(output_dir / "s1_severity_table.tex", "w") as f:
        f.write(generate_s1_severity_distribution_table(results))
    with open(output_dir / "s1_scanner_comparison.tex", "w") as f:
        f.write(generate_s1_scanner_comparison_table(scanner_comp))

    console.print("[bold green]Experiment S1 completed (real Redis CVE data).[/bold green]")
    return results
