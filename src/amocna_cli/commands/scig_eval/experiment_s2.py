"""Experiment S2: Multi-service E2E remediation via AMoCNA loop (no CLI self-patch)."""

from __future__ import annotations

import json
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from rich.console import Console

from . import k8s_helpers as kh
from .latex_generator import generate_s2_remediation_latency_table

console = Console()

REMEDIATION_TARGETS = [
    {
        "namespace": "sock-shop",
        "deployment": "front-end",
        "container": "front-end",
        "vulnerable_image": "docker.io/weaveworksdemos/front-end:0.3.0",
        "expected_tag": "0.3.12",
        "policy": "PATCH",
        "severity": "HIGH",
    },
    {
        "namespace": "sock-shop",
        "deployment": "orders",
        "container": "orders",
        "vulnerable_image": "docker.io/weaveworksdemos/orders:0.4.0",
        "expected_tag": "0.4.7",
        "policy": "MINOR",
        "severity": "CRITICAL",
    },
    {
        "namespace": "sock-shop",
        "deployment": "carts",
        "container": "carts",
        "vulnerable_image": "docker.io/weaveworksdemos/carts:0.3.5",
        "expected_tag": "0.4.8",
        "policy": "MINOR",
        "severity": "HIGH",
    },
]


def _reset_vulnerable(target: dict) -> None:
    kh.set_deployment_image(
        target["namespace"],
        target["deployment"],
        target["container"],
        target["vulnerable_image"],
    )


def _enable_image_update_intent() -> None:
    """Restrict Palamedes to ImageUpdateIntent (same fixture as Scenario 5)."""
    from amocna_cli.commands.benchmark import set_palamedes_filter

    set_palamedes_filter(["ImageUpdateIntent"])


def _clean_stuck_actions() -> None:
    """Clear non-terminal actions so find-vulnerable-workloads is not filtered out."""
    from pathlib import Path
    import subprocess

    script = Path("cli/resources/sparql/clean-actions.sparql")
    if not script.exists():
        console.print("[yellow]clean-actions.sparql not found — skipping GraphDB cleanup[/yellow]")
        return
    try:
        res = subprocess.run(
            [".cursor/skills/graphdb-sparql/scripts/run_sparql.py", "--file", str(script), "--update"],
            capture_output=True,
            text=True,
            check=False,
        )
        if res.returncode == 0:
            console.print("  Cleared stuck GraphDB actions")
        else:
            console.print(f"  [yellow]Action cleanup warning: {res.stderr or res.stdout}[/yellow]")
    except Exception as e:
        console.print(f"  [yellow]Action cleanup skipped: {e}[/yellow]")


def _wait_one(target: dict, timeout_s: int) -> dict:
    dep = target["deployment"]
    t_wait0 = time.perf_counter()
    try:
        wait_s = kh.wait_for_image_tag(
            target["namespace"],
            dep,
            target["expected_tag"],
            timeout_s=timeout_s,
        )
        success = True
        final_image = kh.get_deployment_image(target["namespace"], dep)
    except TimeoutError as e:
        console.print(f"  [red]{e}[/red]")
        wait_s = time.perf_counter() - t_wait0
        success = False
        final_image = kh.get_deployment_image(target["namespace"], dep)

    return dep, {
        "success": success,
        "final_image": final_image,
        "expected_tag": target["expected_tag"],
        "policy": target["policy"],
        "severity": target["severity"],
        "execution_ms": 0.0,
        "rollout_ms": wait_s * 1000.0,
        "wait_after_scan_ms": wait_s * 1000.0,
    }


def run_s2(
    iterations: int,
    output_dir: Path,
    redis_host: str = "localhost",
    redis_port: int = 6379,
    remediation_timeout_s: int = 420,
    scan_timeout_s: int = 3600,
    trigger_scig_scan: bool = False,
) -> dict:
    console.print(
        f"[bold green]S2: E2E remediation via Palamedes/Themis "
        f"({iterations} iterations, no CLI patch, "
        f"scig_scan={trigger_scig_scan})[/bold green]"
    )
    results: dict = {"iterations": [], "success_rates": {}, "trigger_scig_scan": trigger_scig_scan}

    console.print("  Enabling ImageUpdateIntent filter...")
    _enable_image_update_intent()
    _clean_stuck_actions()

    pre_eval = {
        t["deployment"]: kh.get_deployment_image(t["namespace"], t["deployment"])
        for t in REMEDIATION_TARGETS
    }
    results["pre_eval_observation"] = {
        **pre_eval,
        "note": "Images before intentional vulnerable reset.",
    }

    for i in range(iterations):
        console.print(f"[bold]Iteration {i + 1}/{iterations}[/bold]")
        if i > 0:
            _clean_stuck_actions()
        t0 = time.perf_counter()
        for target in REMEDIATION_TARGETS:
            console.print(f"  Reset {target['deployment']} → {target['vulnerable_image']}")
            _reset_vulnerable(target)

        scan_s = 0.0
        if trigger_scig_scan:
            console.print("  Triggering SCIG scan (wait for completion)...")
            try:
                _, scan_s = kh.run_scig_scan(
                    namespaces="sock-shop",
                    timeout_s=scan_timeout_s,
                    discover=True,
                    image_list_only=False,
                )
            except Exception as e:
                console.print(f"  [yellow]SCIG scan skipped/failed: {e}[/yellow]")
                scan_s = 0.0
        t_after_scan = time.perf_counter()

        # Wait in parallel from reset — planner typically remediates during Metis settle
        console.print(
            f"  Waiting (parallel, {remediation_timeout_s}s) for autonomous remediations..."
        )
        per_service: dict = {}
        with ThreadPoolExecutor(max_workers=len(REMEDIATION_TARGETS)) as pool:
            futures = [
                pool.submit(_wait_one, target, remediation_timeout_s)
                for target in REMEDIATION_TARGETS
            ]
            for fut in as_completed(futures):
                dep, data = fut.result()
                # rollout_ms is wait-from-reset for this service (includes Metis/plan/exec)
                data["rollout_ms"] = data["wait_after_scan_ms"]
                per_service[dep] = data
                status = "OK" if data["success"] else "FAIL"
                console.print(
                    f"  [{status}] {dep} → {data['final_image']} "
                    f"({data['rollout_ms']:.0f} ms)"
                )

        t_end = time.perf_counter()
        iter_data = {
            "scig_scan_ms": scan_s * 1000.0,
            "redis_sync_ms": max(0.0, (t_after_scan - t0 - scan_s) * 1000.0),
            "planning_ms": 0.0,
            "total_e2e_ms": (t_end - t0) * 1000.0,
            "per_service": per_service,
            "redis_mb": kh.redis_used_memory_mb(redis_host, redis_port),
            "palamedes_metrics": kh.pod_metrics("palamedes", "app=palamedes"),
        }
        results["iterations"].append(iter_data)

    for target in REMEDIATION_TARGETS:
        dep = target["deployment"]
        oks = [
            1
            for it in results["iterations"]
            if it["per_service"].get(dep, {}).get("success")
        ]
        results["success_rates"][dep] = sum(oks) / max(len(results["iterations"]), 1)

    all_ok = all(rate >= 1.0 for rate in results["success_rates"].values())
    if not all_ok:
        results["limitation"] = (
            "One or more remediations timed out. Check Palamedes ImageRemediationPlanner "
            "logs and that demo-catalog fix tags are reachable."
        )
    else:
        results.pop("limitation", None)

    output_dir.mkdir(parents=True, exist_ok=True)
    with open(output_dir / "s2_results.json", "w") as f:
        json.dump(results, f, indent=2)
    with open(output_dir / "s2_remediation_latency.tex", "w") as f:
        f.write(generate_s2_remediation_latency_table(results))

    console.print("[bold green]Experiment S2 completed.[/bold green]")
    return results
