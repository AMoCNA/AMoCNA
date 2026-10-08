"""Example 1: image vulnerability patching across Sock Shop, Boutique, BookInfo."""

from __future__ import annotations

import json
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from rich.console import Console

from amocna_cli.commands.paper_eval.sparql import (
    binding_count,
    load_cli_sparql,
    run_sparql_select,
)
from amocna_cli.commands.paper_eval.targets import PATCH_TARGETS
from amocna_cli.commands.scig_eval import k8s_helpers as kh
from amocna_cli.commands.scig_eval.experiment_s2 import (
    _clean_stuck_actions,
    _enable_image_update_intent,
    _reset_vulnerable,
    _wait_one,
)
from amocna_cli.config import ProjectConfig

console = Console()


def available_patch_targets() -> list[dict]:
    present = []
    for target in PATCH_TARGETS:
        if kh.namespace_exists(target["namespace"]):
            present.append(target)
        else:
            console.print(
                f"  [yellow]Skipping {target['app']} {target['deployment']}: "
                f"namespace {target['namespace']} not found[/yellow]"
            )
    return present


def _percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    idx = min(len(ordered) - 1, max(0, round((p / 100.0) * (len(ordered) - 1))))
    return ordered[idx]


def run_example1(
    cfg: ProjectConfig,
    iterations: int,
    output_dir: Path,
    remediation_timeout_s: int = 420,
) -> dict:
    targets = available_patch_targets()
    if not targets:
        raise RuntimeError("No evaluation namespaces found (sock-shop / online-boutique / bookinfo).")

    console.print(
        f"[bold green]Paper Example 1: image patching "
        f"({len(targets)} workloads, {iterations} iterations)[/bold green]"
    )
    kh.require_core_loop_ready()
    _enable_image_update_intent()
    _clean_stuck_actions()

    cq_query = load_cli_sparql(cfg.project_root, "cq-vulnerable-workloads.sparql")
    results: dict = {
        "example": 1,
        "iterations": [],
        "success_rates": {},
        "targets": [
            {
                "app": t["app"],
                "namespace": t["namespace"],
                "deployment": t["deployment"],
                "policy": t["policy"],
            }
            for t in targets
        ],
    }

    for i in range(iterations):
        console.print(f"[bold]Iteration {i + 1}/{iterations}[/bold]")
        if i > 0:
            _clean_stuck_actions()
        t0 = time.perf_counter()
        for target in targets:
            console.print(f"  Reset {target['namespace']}/{target['deployment']}")
            _reset_vulnerable(target)

        cq_result, cq_ms = run_sparql_select(cq_query)
        topology_hits = binding_count(cq_result)

        per_service: dict = {}
        with ThreadPoolExecutor(max_workers=max(1, len(targets))) as pool:
            futures = [pool.submit(_wait_one, target, remediation_timeout_s) for target in targets]
            for fut in as_completed(futures):
                dep, data = fut.result()
                data["rollout_ms"] = data["wait_after_scan_ms"]
                key = f"{data.get('policy', '')}:{dep}"
                # unique key per namespace-qualified deployment
                per_service[dep] = data
                status = "OK" if data["success"] else "FAIL"
                console.print(f"  [{status}] {dep} ({data['rollout_ms']:.0f} ms)")

        # Re-key by namespace/deployment to avoid front-end collisions (none today)
        keyed = {}
        for target in targets:
            data = per_service.get(target["deployment"], {})
            keyed[f"{target['namespace']}/{target['deployment']}"] = {
                **data,
                "app": target["app"],
                "namespace": target["namespace"],
                "deployment": target["deployment"],
            }

        _, cq_ms_after = run_sparql_select(cq_query)
        pal_metrics = kh.pod_metrics("palamedes", "app=palamedes")
        gdb_metrics = kh.pod_metrics("graphdb", "app=graphdb") if kh.namespace_exists("graphdb") else {}

        results["iterations"].append(
            {
                "total_e2e_ms": (time.perf_counter() - t0) * 1000.0,
                "sparql_cq1_ms": cq_ms,
                "sparql_cq1_after_ms": cq_ms_after,
                "topology_bindings": topology_hits,
                "per_service": keyed,
                "palamedes_metrics": pal_metrics,
                "graphdb_metrics": gdb_metrics,
            }
        )

    for target in targets:
        key = f"{target['namespace']}/{target['deployment']}"
        oks = [
            1
            for it in results["iterations"]
            if it["per_service"].get(key, {}).get("success")
        ]
        results["success_rates"][key] = sum(oks) / max(len(results["iterations"]), 1)

    cq_latencies = [it["sparql_cq1_ms"] for it in results["iterations"]]
    results["sparql_overhead"] = {
        "cq1_p50_ms": _percentile(cq_latencies, 50),
        "cq1_p95_ms": _percentile(cq_latencies, 95),
        "cq1_n": len(cq_latencies),
    }

    output_dir.mkdir(parents=True, exist_ok=True)
    with open(output_dir / "paper_e1_results.json", "w") as f:
        json.dump(results, f, indent=2)
    from amocna_cli.commands.paper_eval.latex_tables import generate_e1_tables

    for name, body in generate_e1_tables(results).items():
        (output_dir / name).write_text(body)

    console.print("[bold green]Example 1 completed.[/bold green]")
    return results
