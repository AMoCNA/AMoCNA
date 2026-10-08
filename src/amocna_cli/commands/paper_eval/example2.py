"""Example 2: SLA-driven horizontal scaling on three entrypoints."""

from __future__ import annotations

import json
import time
from pathlib import Path

from rich.console import Console

from amocna_cli.commands.paper_eval.sparql import (
    binding_count,
    load_cli_sparql,
    run_sparql_select,
    run_sparql_update,
)
from amocna_cli.commands.paper_eval.targets import load_scale_targets
from amocna_cli.commands.scig_eval import k8s_helpers as kh
from amocna_cli.config import ProjectConfig
from amocna_cli.utils.ui import run

console = Console()

CNEE = "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/"


def _percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    idx = min(len(ordered) - 1, max(0, round((p / 100.0) * (len(ordered) - 1))))
    return ordered[idx]


def _inject_sla_state(namespace: str, deployment: str) -> None:
    iri = f"{CNEE}Deployment_{namespace}_{deployment}"
    update = (
        "PREFIX cnee: <http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/>\n"
        "INSERT DATA {\n"
        f"  <{iri}> a cnee:StatelessWorkloadController ;\n"
        f"    cnee:resourceName \"{deployment}\" ;\n"
        f"    cnee:hasState [ a cnee:ResponseTimeSlaViolatedState ] .\n"
        "}\n"
    )
    run_sparql_update(update)


def _clean_stuck_actions() -> None:
    from amocna_cli.commands.paper_eval.sparql import load_cli_sparql
    from amocna_cli.config import find_project_root

    try:
        query = load_cli_sparql(find_project_root(), "clean-actions.sparql")
        run_sparql_update(query)
        console.print("  Cleared stuck GraphDB actions")
    except Exception as e:
        console.print(f"  [yellow]Action cleanup skipped: {e}[/yellow]")


def available_scale_targets() -> list[dict]:
    present = []
    for target in load_scale_targets():
        if not kh.namespace_exists(target["namespace"]):
            console.print(f"  [yellow]Skipping {target['app']}: namespace missing[/yellow]")
            continue
        locust = kh.run_kubectl(
            ["get", "deploy", "locust-master", "-n", target["locust_namespace"]],
            check=False,
        )
        if locust.returncode != 0:
            console.print(
                f"  [yellow]Skipping {target['app']}: locust-master not in "
                f"{target['locust_namespace']} (apply infra/load-generation)[/yellow]"
            )
            continue
        present.append(target)
    return present


def run_example2(
    cfg: ProjectConfig,
    iterations: int,
    output_dir: Path,
    observe_timeout_s: int = 360,
    baseline_s: int = 20,
) -> dict:
    from amocna_cli.commands.benchmark import (
        get_locust_p95_seconds,
        reset_locust_stats,
        set_locust_load,
        set_palamedes_filter,
        stop_locust,
    )
    from amocna_cli.utils.shell import k8s_scale

    targets = available_scale_targets()
    if not targets:
        raise RuntimeError("No scale-target namespaces found.")

    console.print(
        f"[bold green]Paper Example 2: horizontal scaling "
        f"({len(targets)} apps, {iterations} iterations)[/bold green]"
    )
    kh.require_core_loop_ready()
    set_palamedes_filter(["HorizontalScalingUpIntent"])
    _clean_stuck_actions()
    cq_query = load_cli_sparql(cfg.project_root, "cq-sla-scale-target.sparql")

    results: dict = {"example": 2, "apps": {}, "iterations_requested": iterations}

    for target in targets:
        app = target["app"]
        ns = target["namespace"]
        dep = target["deployment"]
        locust_ns = target["locust_namespace"]
        host = target["locust_host"]
        slo = float(target["slo_seconds"])
        desired = int(target["target_replicas"])
        console.print(f"[bold]Application: {app} ({ns}/{dep}, SLO={slo}s)[/bold]")
        app_runs: list[dict] = []

        for i in range(iterations):
            console.print(f"  Iteration {i + 1}/{iterations}")
            _clean_stuck_actions()
            reset_locust_stats(locust_ns)
            run(k8s_scale(ns, dep, 1), check=False)
            deadline_ready = time.time() + 120
            while time.time() < deadline_ready and kh.get_ready_replicas(ns, dep) < 1:
                time.sleep(2)

            set_locust_load(target["baseline_users"], 10, host=host, locust_namespace=locust_ns)
            time.sleep(baseline_s)

            t_spike = time.perf_counter()
            t_slo = None
            t_state = None
            t_ready = None
            replica_seconds = 0.0
            last_sample = time.perf_counter()
            injected = False

            set_locust_load(
                target["spike_users"],
                target["spawn_rate"],
                host=host,
                locust_namespace=locust_ns,
            )

            last_cq_ms = 0.0
            deadline = time.time() + observe_timeout_s
            while time.time() < deadline:
                now = time.perf_counter()
                extra = max(0, kh.get_ready_replicas(ns, dep) - 1)
                replica_seconds += extra * (now - last_sample)
                last_sample = now

                p95 = get_locust_p95_seconds(locust_ns)
                if t_slo is None and p95 > slo:
                    t_slo = (now - t_spike) * 1000.0
                    console.print(f"    SLO crossed (p95={p95:.3f}s) at {t_slo:.0f} ms")

                cq_result, last_cq_ms = run_sparql_select(cq_query)
                if t_state is None and binding_count(cq_result) > 0:
                    t_state = (now - t_spike) * 1000.0

                if t_slo is not None and t_state is None and not injected and (now - t_spike) > 45:
                    console.print("    Injecting ResponseTimeSlaViolatedState (metrics-adapter fallback)")
                    _inject_sla_state(ns, dep)
                    injected = True

                if kh.get_ready_replicas(ns, dep) >= desired:
                    t_ready = (now - t_spike) * 1000.0
                    console.print(f"    Scaled to {desired} ready replicas at {t_ready:.0f} ms")
                    break
                time.sleep(5)

            slo_violation_ms = 0.0
            if t_slo is not None:
                end_ms = t_ready if t_ready is not None else observe_timeout_s * 1000.0
                slo_violation_ms = max(0.0, end_ms - t_slo)

            pal = kh.pod_metrics("palamedes", "app=palamedes")
            gdb = kh.pod_metrics("graphdb", "app=graphdb") if kh.namespace_exists("graphdb") else {}

            app_runs.append(
                {
                    "success": t_ready is not None,
                    "t_slo_ms": t_slo,
                    "t_state_ms": t_state,
                    "t_ready_ms": t_ready,
                    "slo_violation_ms": slo_violation_ms,
                    "replica_seconds": replica_seconds,
                    "sparql_cq2_ms": last_cq_ms,
                    "injected_state": injected,
                    "palamedes_metrics": pal,
                    "graphdb_metrics": gdb,
                    "slo_seconds": slo,
                }
            )

            stop_locust(locust_ns)
            run(k8s_scale(ns, dep, 1), check=False)
            time.sleep(10)

        results["apps"][app] = {
            "namespace": ns,
            "deployment": dep,
            "slo_seconds": slo,
            "runs": app_runs,
            "success_rate": sum(1 for r in app_runs if r["success"]) / max(len(app_runs), 1),
        }

    cq_all = [
        r["sparql_cq2_ms"]
        for app in results["apps"].values()
        for r in app["runs"]
        if r.get("sparql_cq2_ms")
    ]
    results["sparql_overhead"] = {
        "cq2_p50_ms": _percentile(cq_all, 50),
        "cq2_p95_ms": _percentile(cq_all, 95),
        "cq2_n": len(cq_all),
    }

    output_dir.mkdir(parents=True, exist_ok=True)
    with open(output_dir / "paper_e2_results.json", "w") as f:
        json.dump(results, f, indent=2)
    from amocna_cli.commands.paper_eval.latex_tables import generate_e2_tables

    for name, body in generate_e2_tables(results).items():
        (output_dir / name).write_text(body)

    console.print("[bold green]Example 2 completed.[/bold green]")
    return results
