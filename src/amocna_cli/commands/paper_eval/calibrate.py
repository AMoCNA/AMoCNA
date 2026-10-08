"""Find 1-replica and 3-replica RPS so a spike crosses the SLO and recovers after scale-out."""

from __future__ import annotations

import json
import time
from datetime import datetime, timezone
from pathlib import Path

from rich.console import Console

from amocna_cli.commands.paper_eval.example2 import available_scale_targets
from amocna_cli.commands.paper_eval.targets import SCALE_TARGETS

console = Console()

HOLD_S = 25
STEP = 10
MAX_RPS = 400
SETTLE_S = 45

# Tight entry CPU so one replica saturates and three recover the SLO.
# Without this, backends/shared limits often make R3≈R1 and scale-out never helps.
ENTRY_CPU_LIMIT = {
    # Browse-only Locust + client timeouts keep recovery clean at 100m.
    "sock-shop": "100m",
    # Tight enough that a calibrated spike stays over the SLO at 1 replica until scale-out.
    "online-boutique": "75m",
    "bookinfo": "200m",
}


def _silence_competing_load() -> None:
    """Stop Google Boutique's built-in loadgenerator so Locust alone drives the SLO."""
    from amocna_cli.commands.scig_eval import k8s_helpers as kh
    from amocna_cli.utils.shell import k8s_scale
    from amocna_cli.utils.ui import run

    if kh.namespace_exists("online-boutique"):
        probe = kh.run_kubectl(
            ["get", "deploy", "loadgenerator", "-n", "online-boutique"],
            check=False,
        )
        if probe.returncode == 0:
            run(k8s_scale("online-boutique", "loadgenerator", 0), check=False)
            console.print("    Scaled online-boutique/loadgenerator to 0")


def _constrain_entry_cpu(target: dict) -> None:
    """Cap entry Deployment CPU so horizontal scale-out increases capacity."""
    from amocna_cli.utils.ui import run

    ns = target["namespace"]
    dep = target["deployment"]
    cpu = ENTRY_CPU_LIMIT.get(ns)
    if not cpu:
        return
    patch = {
        "spec": {
            "template": {
                "spec": {
                    "containers": [
                        {
                            "name": target.get("container") or dep.replace("-v1", ""),
                            "resources": {
                                "limits": {"cpu": cpu},
                                "requests": {"cpu": cpu},
                            },
                        }
                    ]
                }
            }
        }
    }
    # Sock Shop front-end container is named front-end; Boutique server; BookInfo productpage.
    name_map = {
        "sock-shop": "front-end",
        "online-boutique": "server",
        "bookinfo": "productpage",
    }
    patch["spec"]["template"]["spec"]["containers"][0]["name"] = name_map.get(ns, dep)
    import json

    console.print(f"    Constraining {ns}/{dep} CPU to {cpu}")
    run(
        [
            "kubectl",
            "patch",
            "deployment",
            dep,
            "-n",
            ns,
            "--type",
            "strategic",
            "-p",
            json.dumps(patch),
        ],
        check=False,
    )
    run(
        ["kubectl", "rollout", "status", f"deployment/{dep}", "-n", ns, "--timeout=180s"],
        check=False,
    )


def _measure_p95(locust_ns: str, host: str, rps: int, hold_s: int) -> float:
    from amocna_cli.commands.benchmark import (
        get_locust_p95_seconds,
        reset_locust_stats,
        set_locust_load,
        stop_locust,
    )

    reset_locust_stats(locust_ns)
    set_locust_load(rps, max(rps, 10), host=host, locust_namespace=locust_ns)
    time.sleep(max(hold_s - 8, 10))
    # Locust often reports 0 until enough samples land; take the last non-zero.
    p95 = 0.0
    for _ in range(8):
        sample = get_locust_p95_seconds(locust_ns)
        if sample > 0:
            p95 = sample
        time.sleep(1)
    stop_locust(locust_ns)
    time.sleep(2)
    return p95


def _capacity(target: dict, replicas: int, start_rps: int, hold_s: int) -> tuple[int, list[dict]]:
    from amocna_cli.commands.scig_eval import k8s_helpers as kh
    from amocna_cli.utils.shell import k8s_scale
    from amocna_cli.utils.ui import run

    ns = target["namespace"]
    dep = target["deployment"]
    locust_ns = target["locust_namespace"]
    host = target["locust_host"]
    slo = float(target["slo_seconds"])
    run(k8s_scale(ns, dep, replicas), check=False)
    deadline = time.time() + 180
    while time.time() < deadline and kh.get_ready_replicas(ns, dep) < replicas:
        time.sleep(2)
    time.sleep(SETTLE_S)

    samples: list[dict] = []
    last_ok = 0
    rps = max(STEP, start_rps)
    console.print(f"    Sweeping ~req/s at {replicas} replica(s) from {rps}")
    while rps <= MAX_RPS:
        p95 = _measure_p95(locust_ns, host, rps, hold_s)
        samples.append({"rps": rps, "p95": round(p95, 4)})
        console.print(f"      {rps} req/s -> p95={p95:.3f}s")
        if p95 > slo:
            break
        last_ok = rps
        rps += STEP
    return last_ok, samples


def recommended_loads(r1: int, r3: int) -> dict:
    """Baseline stays under SLO at 1 replica; spike exceeds it but stays under 3-replica capacity."""
    if r1 <= 0:
        raise RuntimeError("1-replica capacity is 0; Locust is not reaching the app")
    if r3 < int(1.3 * r1):
        raise RuntimeError(
            f"3 replicas only serve {r3} req/s vs {r1} at 1 replica. "
            "Entry scale-out cannot recover the SLO. Check Locust workers and that the "
            "entry Deployment is the bottleneck (and stop competing load generators)."
        )
    # Just above 1-replica capacity so the spike crosses the SLO without a timeout pile-up;
    # leave ≥35% headroom under R3 so scale-out recovers cleanly under sustained load.
    baseline = max(STEP, int(0.4 * r1))
    spike = max(r1 + STEP, int(1.05 * r1 + STEP))
    spike = min(spike, int(0.65 * r3))
    if spike <= r1 or spike >= int(0.85 * r3):
        raise RuntimeError(
            f"No recovery window: R1={r1}, R3={r3}. Need R3 clearly above R1 after scale-out."
        )
    return {
        "baseline_users": baseline,
        "spike_users": spike,
        "spawn_rate": max(10, max(spike // 3, STEP)),
        "r1_rps": r1,
        "r3_rps": r3,
    }


def run_calibrate(
    output_dir: Path,
    apps: str | None = None,
    hold_s: int = HOLD_S,
) -> dict:
    from amocna_cli.commands.benchmark import set_palamedes_filter, stop_locust
    from amocna_cli.commands.paper_eval.example2 import _clean_stuck_actions
    from amocna_cli.commands.scig_eval import k8s_helpers as kh
    from amocna_cli.utils.shell import k8s_scale
    from amocna_cli.utils.ui import run

    known = {target["namespace"]: target for target in SCALE_TARGETS}
    if apps:
        wanted = [part.strip() for part in apps.split(",") if part.strip()]
        missing = [ns for ns in wanted if ns not in known]
        if missing:
            raise RuntimeError(f"Unknown namespace(s): {', '.join(missing)}")
    else:
        wanted = [target["namespace"] for target in SCALE_TARGETS]
    present = {target["namespace"]: target for target in available_scale_targets()}
    targets = [present[ns] for ns in wanted if ns in present]
    if not targets:
        raise RuntimeError("No scale-target namespaces found.")

    kh.require_core_loop_ready()
    set_palamedes_filter(["ImageUpdateIntent"])
    _clean_stuck_actions()
    _silence_competing_load()

    payload = {
        "calibrated_at": datetime.now(timezone.utc).isoformat(),
        "hold_s": hold_s,
        "step": STEP,
        "apps": [],
    }
    # Keep prior successful app calibrations when running a subset.
    prior_path = output_dir / "scale_calibration.json"
    prior_by_ns: dict[str, dict] = {}
    if prior_path.is_file():
        try:
            prior = json.loads(prior_path.read_text())
            prior_by_ns = {
                row["namespace"]: row
                for row in prior.get("apps", [])
                if row.get("spike_users")
            }
        except Exception:
            prior_by_ns = {}
    try:
        for target in targets:
            console.print(f"[bold]{target['app']}[/bold] ({target['namespace']}/{target['deployment']})")
            _clean_stuck_actions()
            _constrain_entry_cpu(target)
            try:
                r1, samples1 = _capacity(target, 1, STEP, hold_s)
                # Start near R1 (faster); if cold pods make R3 look weak, resweep from STEP.
                r3, samples3 = _capacity(target, 3, max(STEP, r1), hold_s)
                if r3 < int(1.3 * r1):
                    console.print("    R3 look weak after scale-out; re-sweeping from STEP after settle")
                    r3, samples3 = _capacity(target, 3, STEP, hold_s)
                loads = recommended_loads(r1, r3)
            except RuntimeError as exc:
                console.print(f"    [red]Calibration failed: {exc}[/red]")
                payload["apps"].append(
                    {
                        "app": target["app"],
                        "namespace": target["namespace"],
                        "error": str(exc),
                        "samples_1": locals().get("samples1", []),
                        "samples_3": locals().get("samples3", []),
                        "r1_rps": locals().get("r1"),
                        "r3_rps": locals().get("r3"),
                    }
                )
                run(k8s_scale(target["namespace"], target["deployment"], 1), check=False)
                stop_locust(target["locust_namespace"])
                continue
            row = {
                "app": target["app"],
                "namespace": target["namespace"],
                **loads,
                "entry_cpu_limit": ENTRY_CPU_LIMIT.get(target["namespace"]),
                "samples_1": samples1,
                "samples_3": samples3,
            }
            payload["apps"].append(row)
            console.print(
                f"    R1={r1} req/s  R3={r3} req/s  "
                f"baseline={loads['baseline_users']}  spike={loads['spike_users']}"
            )
            run(k8s_scale(target["namespace"], target["deployment"], 1), check=False)
            stop_locust(target["locust_namespace"])
    finally:
        set_palamedes_filter(["HorizontalScalingUpIntent"])

    # Merge: successful rows win; never let a failed re-run erase a prior good calibration.
    by_ns: dict[str, dict] = {}
    for row in payload["apps"]:
        ns = row["namespace"]
        if row.get("spike_users") or ns not in by_ns:
            by_ns[ns] = row
    for ns, row in prior_by_ns.items():
        if not by_ns.get(ns, {}).get("spike_users"):
            by_ns[ns] = row
    order = [t["namespace"] for t in SCALE_TARGETS]
    payload["apps"] = [by_ns[ns] for ns in order if ns in by_ns]

    output_dir.mkdir(parents=True, exist_ok=True)
    path = output_dir / "scale_calibration.json"
    path.write_text(json.dumps(payload, indent=2) + "\n")
    ok = [a for a in payload["apps"] if a.get("spike_users")]
    console.print(f"[bold green]Wrote {path} ({len(ok)}/{len(payload['apps'])} apps OK)[/bold green]")
    if not ok:
        raise RuntimeError("No app calibrated successfully; cannot plot scale-out recovery.")
    console.print("paper-eval plot and example 2 pick these rates up automatically from that file.")
    return payload
