from __future__ import annotations

import os
import sys
import time
import subprocess
from typing_extensions import Annotated
import typer

from amocna_cli.config import ProjectConfig
from amocna_cli.commands.version import read_pom_version
from amocna_cli.utils.ui import console, header, info, error, run, run_capture
from amocna_cli.utils.shell import (
    LOCUST_SWARM_PYTHON_TEMPLATE,
    LOCUST_STOP_PYTHON_TEMPLATE,
    LOCUST_STATS_PYTHON_TEMPLATE,
    ORDERS_CPU_RESET_PATCH,
    k8s_run_pod,
    k8s_exec,
    k8s_scale,
    k8s_patch,
    k8s_get_jsonpath,
    k8s_get_pods_jsonpath,
    k8s_delete_resource,
)

app = typer.Typer(no_args_is_help=True)

# Sock-shop front-end images (explicit docker.io avoids quay.io redirect / auth failures)
SOCK_SHOP_FRONTEND_IMAGE = "docker.io/weaveworksdemos/front-end"
SOCK_SHOP_FRONTEND_VULNERABLE_TAG = "0.3.0"
SOCK_SHOP_FRONTEND_PATCHED_TAG = "0.3.12"

S6_NAMESPACE = "sock-shop"
S6_SECRET_NAME = "regcred"
S6_SIBLING_DEPLOY = "s6-sibling"
S6_FAILING_DEPLOY = "s6-failing"
S6_PRIVATE_IMAGE_APP = "s7-private-demo"
S6_WORKLOADS_MANIFEST = "s6-registry-credential-workloads.yaml"

# ─── Benchmark helpers ─────────────────────────────────────────────


def _load_sparql_query(cfg: ProjectConfig, filename: str) -> str:
    """Dynamically load SPARQL query template from cli resources directory."""
    path = cfg.project_root / "cli" / "resources" / "sparql" / filename
    if not path.is_file():
        error(f"SPARQL template not found: {path}")
        sys.exit(1)
    return path.read_text().strip()


def run_sparql(cfg: ProjectConfig, update_query: str) -> None:
    """Execute a SPARQL Update query inside GraphDB using a temporary pod in K8s."""
    # Use sh -c to allow piping the query via stdin to curl, avoiding shell escaping hell with large queries
    curl_cmd = [
        "sh",
        "-c",
        "curl -s -X POST http://graphdb.graphdb.svc.cluster.local:7200/repositories/amocna/statements "
        "-H 'Content-Type: application/sparql-update' --data-binary @-",
    ]
    # Clean up any stale trigger pod if it exists from a previous failed run
    subprocess.run(
        ["kubectl", "delete", "pod", "amocna-sparql-trigger", "--ignore-not-found", "--grace-period=0", "--force"],
        capture_output=True,
        text=True,
    )
    # We use subprocess.run directly here to manage the input stream
    console.print(f"  [dim]$ (sparql-update via pod) [/dim]")
    pod_cmd = k8s_run_pod("amocna-sparql-trigger", "curlimages/curl:8.12.1", curl_cmd)
    subprocess.run(pod_cmd, input=update_query, text=True, check=True)


def set_locust_load(users: int, rate: int) -> None:
    """Control Locust swarming programmatically."""
    info(f"Setting Locust traffic to {users} users (spawn rate {rate})...")
    python_snippet = LOCUST_SWARM_PYTHON_TEMPLATE.format(users=users, rate=rate)
    run(
        k8s_exec("sock-shop", "deploy/locust-master", ["python3", "-c", python_snippet])
    )


def set_palamedes_filter(intents: list[str], logger: Optional[Any] = None) -> None:
    """Update Palamedes REST filter for allowed intents."""
    from typing import Optional, Any
    
    if not intents:
        msg = "Clearing Palamedes intent filter (Allow ALL)"
        if logger:
            logger.log("CLEAR_PALAMEDES_FILTER", msg)
        else:
            console.print(f"  [bold yellow]⚠[/bold yellow] {msg}")
        # Use sh -c for consistency and to ensure internal DNS resolution works reliably in the pod
        cmd = [
            "sh",
            "-c",
            "curl -s -X DELETE http://palamedes.palamedes.svc.cluster.local:8081/api/filters/intents",
        ]
    else:
        msg = f"Setting Palamedes intent filter: {intents}"
        if logger:
            logger.log("SET_PALAMEDES_FILTER", msg)
        else:
            console.print(f"  [bold blue]✔[/bold blue] {msg}")
            
        import json

        payload = json.dumps(list(intents))
        # Use sh -c to safely pass the JSON string in single quotes, avoiding shell globbing of brackets []
        cmd = [
            "sh",
            "-c",
            f"curl -s -X POST -H 'Content-Type: application/json' -d '{payload}' "
            "http://palamedes.palamedes.svc.cluster.local:8081/api/filters/intents",
        ]

    # Clean up any stale trigger pod if it exists from a previous failed run
    subprocess.run(
        ["kubectl", "delete", "pod", "palamedes-filter-trigger", "--ignore-not-found", "--grace-period=0", "--force"],
        capture_output=True,
        text=True,
    )
    # We execute via a temporary pod to reach the internal service
    kubectl_cmd = [
        "kubectl",
        "run",
        "palamedes-filter-trigger",
        "--image=curlimages/curl:8.12.1",
        "--restart=Never",
        "--rm",
        "-i",
        "--",
    ] + cmd

    if logger:
        logger.log("TRIGGER_COMMAND", f"Executing: {' '.join(kubectl_cmd)}")

    res = run(
        kubectl_cmd,
        check=False,
        capture=True if logger else False,
    )
    
    if logger:
        stdout_str = res.stdout.strip() if res.stdout else ""
        stderr_str = res.stderr.strip() if res.stderr else ""
        output = stdout_str or stderr_str
        if res.returncode == 0:
            logger.log("SET_PALAMEDES_FILTER_SUCCESS", f"Trigger pod output: {output}")
        else:
            logger.log("SET_PALAMEDES_FILTER_FAILURE", f"Trigger pod failed with exit code {res.returncode}. Output: {output}")


def stop_locust() -> None:
    """Stop Locust traffic swarming."""
    info("Stopping Locust traffic...")
    run(
        k8s_exec(
            "sock-shop",
            "deploy/locust-master",
            ["python3", "-c", LOCUST_STOP_PYTHON_TEMPLATE],
        )
    )


def get_locust_stats() -> None:
    """Get active load statistics from Locust."""
    run(
        k8s_exec(
            "sock-shop",
            "deploy/locust-master",
            ["python3", "-c", LOCUST_STATS_PYTHON_TEMPLATE],
        )
    )


def _load_k8s_manifest(cfg: ProjectConfig, filename: str, **replacements: str) -> str:
    """Load a K8s manifest template from cli/resources/k8s and substitute placeholders."""
    path = cfg.project_root / "cli" / "resources" / "k8s" / filename
    if not path.is_file():
        error(f"K8s manifest template not found: {path}")
        sys.exit(1)
    text = path.read_text()
    for key, value in replacements.items():
        text = text.replace("{" + key + "}", value)
    return text


def _require_registry_credentials() -> tuple[str, str]:
    """Return (username, PAT) from the environment or exit."""
    user = os.environ.get("AMOCNA_USER")
    pat = os.environ.get("AMOCNA_PAT")
    if not user:
        error(
            "AMOCNA_USER is not set. Export your GitHub username: export AMOCNA_USER=..."
        )
        sys.exit(1)
    if not pat:
        error("AMOCNA_PAT is not set. Export your GitHub PAT: export AMOCNA_PAT=...")
        sys.exit(1)
    return user, pat


def _s7_private_image(cfg: ProjectConfig) -> str:
    version = "1.10.3-SNAPSHOT"
    return f"{cfg.registry}/{S6_PRIVATE_IMAGE_APP}:{version}"


def _s7_expected_version(cfg: ProjectConfig) -> str:
    return read_pom_version(cfg.project_root / cfg.parent_pom)


def _s7_deployment_image(namespace: str, deployment: str) -> str:
    return run_capture(
        k8s_get_jsonpath(
            namespace,
            "deployment",
            deployment,
            "{.spec.template.spec.containers[0].image}",
        ),
        check=False,
    )


def _s7_check_runtime_prerequisites(cfg: ProjectConfig) -> bool:
    """Verify control-loop services run the expected version before Scenario 7."""
    expected = _s7_expected_version(cfg)
    services = [
        ("metis", "metis"),
        ("palamedes", "palamedes"),
        ("themis", "themis"),
    ]
    mismatches: list[str] = []
    for namespace, deployment in services:
        image = _s7_deployment_image(namespace, deployment)
        if not image:
            mismatches.append(f"{deployment}: deployment/image not found")
            continue
        if f":{expected}" not in image:
            mismatches.append(
                f"{deployment}: running {image} (expected tag :{expected})"
            )
    if mismatches:
        error("Scenario 6 preflight failed: control-loop images are out of sync.")
        for mismatch in mismatches:
            console.print(f"    - {mismatch}")
        console.print(
            "    Hint: rebuild/push and redeploy metis, palamedes, themis to the same project version."
        )
        return False
    return True


def _s7_print_timeout_diagnostics() -> None:
    """Print concise diagnostics to explain why Scenario 6 may be stuck."""
    info("Collecting Scenario 6 diagnostics...")
    pod = run_capture(
        k8s_get_pods_jsonpath(
            S6_NAMESPACE, "app=s6-failing", "{.items[0].metadata.name}"
        ),
        check=False,
    )
    if pod:
        reason = run_capture(
            [
                "kubectl",
                "get",
                "pod",
                pod,
                "-n",
                S6_NAMESPACE,
                "-o=jsonpath={.status.containerStatuses[0].state.waiting.reason}",
            ],
            check=False,
        )
        msg = run_capture(
            [
                "kubectl",
                "get",
                "pod",
                pod,
                "-n",
                S6_NAMESPACE,
                "-o=jsonpath={.status.containerStatuses[0].state.waiting.message}",
            ],
            check=False,
        )
        console.print(f"    failing pod: {pod}")
        console.print(f"    pull reason: {reason or '(unknown)'}")
        if msg:
            console.print(f"    pull message: {msg}")

    dep_secret = run_capture(
        k8s_get_jsonpath(
            S6_NAMESPACE,
            "deployment",
            S6_FAILING_DEPLOY,
            "{.spec.template.spec.imagePullSecrets[0].name}",
        ),
        check=False,
    )
    console.print(f"    deployment imagePullSecret: {dep_secret or '(none)'}")

    pal_logs = run_capture(
        ["kubectl", "logs", "-n", "palamedes", "deployment/palamedes", "--since=5m"],
        check=False,
    )
    markers = [
        "Planned imagePullSecret patch",
        "AddImagePullSecretIntent",
        "RegistryCredentialPlanner",
    ]
    found = [m for m in markers if m in pal_logs]
    if found:
        console.print(f"    palamedes markers found: {', '.join(found)}")
    else:
        console.print("    palamedes markers found: none")


def _s7_wait_for_sibling_running(
    logger: EventLogger, timeout_seconds: int = 240
) -> bool:
    """Wait until sibling pod is Running so Palamedes can infer a working pull secret."""
    logger.log(
        "WAIT_SIBLING",
        f"Waiting for sibling workload to become Running (timeout {timeout_seconds}s)",
    )
    checks = max(1, timeout_seconds // 5)
    for i in range(checks):
        phase = run_capture(
            k8s_get_pods_jsonpath(
                S6_NAMESPACE, "app=s6-sibling", "{.items[0].status.phase}"
            ),
            check=False,
        )
        if phase == "Running":
            logger.log("SIBLING_READY", f"Sibling pod is Running after {i * 5}s")
            return True
        console.print(
            f"    Waiting for sibling to run (phase={phase or 'unknown'})... ({i * 5}s)"
        )
        time.sleep(5)
    return False


def _create_docker_registry_secret(
    namespace: str, name: str, username: str, password: str
) -> None:
    """Create or update a docker-registry pull secret (ghcr.io)."""
    create = subprocess.run(
        [
            "kubectl",
            "create",
            "secret",
            "docker-registry",
            name,
            "-n",
            namespace,
            "--docker-server=ghcr.io",
            f"--docker-username={username}",
            f"--docker-password={password}",
            "--dry-run=client",
            "-o",
            "yaml",
        ],
        capture_output=True,
        text=True,
        check=True,
    )
    subprocess.run(
        ["kubectl", "apply", "-f", "-"],
        input=create.stdout,
        text=True,
        check=True,
    )


def _apply_manifest_stdin(manifest: str) -> None:
    subprocess.run(
        ["kubectl", "apply", "-f", "-"],
        input=manifest,
        text=True,
        check=True,
    )


def _cleanup_s7_benchmark(cfg: ProjectConfig) -> None:
    """Remove Scenario 6 workloads, pull secret, and related GraphDB triples."""
    for deploy in (S6_FAILING_DEPLOY, S6_SIBLING_DEPLOY):
        run(
            k8s_delete_resource("deployment", deploy, namespace=S6_NAMESPACE),
            check=False,
        )
    run(
        k8s_delete_resource("secret", S6_SECRET_NAME, namespace=S6_NAMESPACE),
        check=False,
    )
    run(
        k8s_delete_resource("serviceaccount", "s6-sibling-sa", namespace=S6_NAMESPACE),
        check=False,
    )
    run(
        k8s_delete_resource("serviceaccount", "s6-failing-sa", namespace=S6_NAMESPACE),
        check=False,
    )
    run_sparql(cfg, _load_sparql_query(cfg, "clean-anomalies.sparql"))
    run_sparql(cfg, _load_sparql_query(cfg, "clean-actions.sparql"))
    clean_s7 = _load_sparql_query(cfg, "clean-s6.sparql")
    run_sparql(cfg, clean_s7)


def _poke_s7_failing_pod() -> None:
    """Trigger a pod update so Metis re-asserts ImagePullBackOffState after phase sensing."""
    pods_raw = run_capture(
        k8s_get_pods_jsonpath(
            S6_NAMESPACE,
            "app=s6-failing",
            "{range .items[*]}{.metadata.name}|{.metadata.deletionTimestamp}{'\\n'}{end}",
        ),
        check=False,
    )
    pod_name = None
    for line in (pods_raw or "").splitlines():
        if not line.strip():
            continue
        parts = line.split("|")
        name = parts[0] if len(parts) > 0 else ""
        deletion_ts = parts[1] if len(parts) > 1 else ""
        deletion_ts = deletion_ts.strip()
        if not deletion_ts:
            pod_name = name
            break
    if pod_name is None:
        # Fallback to first available pod if all are terminating.
        pod_name = run_capture(
            k8s_get_pods_jsonpath(
                S6_NAMESPACE, "app=s6-failing", "{.items[0].metadata.name}"
            ),
            check=False,
        )
    if not pod_name:
        return
    run(
        [
            "kubectl",
            "annotate",
            "pod",
            pod_name,
            "-n",
            S6_NAMESPACE,
            f"amocna.benchmark/s6-poke={int(time.time())}",
            "--overwrite",
        ],
        check=False,
    )


def _s7_failing_pod_snapshots() -> list[tuple[str, str, str, bool]]:
    """
    Return [(podName, phase, waitingReason, terminating), ...] for app=s6-failing.
    """
    raw = run_capture(
        k8s_get_pods_jsonpath(
            S6_NAMESPACE,
            "app=s6-failing",
            "{range .items[*]}{.metadata.name}|{.status.phase}|{.status.containerStatuses[0].state.waiting.reason}|{.metadata.deletionTimestamp}{'\\n'}{end}",
        ),
        check=False,
    )
    snapshots: list[tuple[str, str, str, bool]] = []
    for line in (raw or "").splitlines():
        if not line.strip():
            continue
        parts = line.split("|")
        name = parts[0] if len(parts) > 0 else ""
        phase = parts[1] if len(parts) > 1 else ""
        reason = parts[2] if len(parts) > 2 else ""
        deletion_ts = parts[3].strip() if len(parts) > 3 else ""
        snapshots.append((name, phase or "", reason or "", bool(deletion_ts)))
    return snapshots


# ─── Benchmark Subcommands ─────────────────────────────────────────


@app.command("status")
def benchmark_status(ctx: typer.Context):
    """Get current workload and cluster scaling status."""
    cfg: ProjectConfig = ctx.obj
    header("AMoCNA Benchmark Status")

    # 1. Pod replicas in sock-shop
    console.print(f"\n  [bold]Sock Shop Deployments:[/bold]")
    for deploy in ["front-end", "orders", "carts"]:
        try:
            replicas = run_capture(
                k8s_get_jsonpath(
                    "sock-shop",
                    "deployment",
                    deploy,
                    "{.spec.replicas}/{.status.readyReplicas}",
                ),
                check=False,
            )
            console.print(f"    {deploy:<15} : {replicas} replicas ready")
        except Exception:
            console.print(f"    {deploy:<15} : Not found or unreachable")

    # 2. Stress replica status
    console.print(f"\n  [bold]Load Stress Status:[/bold]")
    try:
        stress_replicas = run_capture(
            k8s_get_jsonpath(
                "default", "deployment", "cluster-stress", "{.spec.replicas}"
            ),
            check=False,
        )
        console.print(f"    cluster-stress  : {stress_replicas} replicas running")
    except Exception:
        console.print("    cluster-stress  : Not deployed")

    # 3. Active Locust stats
    console.print(f"\n  [bold]Locust Load Statistics:[/bold]")
    get_locust_stats()
    console.print()


@app.command("stop")
def benchmark_stop(ctx: typer.Context):
    """Instantly stop all load tests and tear down stress."""
    cfg: ProjectConfig = ctx.obj
    header("Stopping All Benchmark Elements")
    stop_locust()

    info("Scaling down cluster-stress to 0...")
    run(k8s_scale("default", "cluster-stress", 0), check=False)

    info("Scaling down front-end to 1...")
    run(k8s_scale("sock-shop", "front-end", 1), check=False)

    info("Resetting orders CPU requests and scaling to 1 replica...")
    run(k8s_patch("sock-shop", "orders", ORDERS_CPU_RESET_PATCH), check=False)
    run(k8s_scale("sock-shop", "orders", 1), check=False)

    # Clean up ConfigDriftState / SecurityVulnerabilityDetectedState if any
    info("Cleaning up GraphDB anomaly states...")
    clean_query = _load_sparql_query(cfg, "clean-anomalies.sparql")
    run_sparql(cfg, clean_query)

    # Restore RestartPodIntent instruction
    restore_query = _load_sparql_query(cfg, "restore-restart.sparql")
    run_sparql(cfg, restore_query)

    info("Deleting temporary benchmark ConfigMaps...")
    run(
        k8s_delete_resource("configmap", "orders-config", namespace="sock-shop"),
        check=False,
    )

    info("Removing Scenario 7 benchmark workloads and graph triples...")
    _cleanup_s7_benchmark(cfg)

    info("System successfully returned to baseline.")
    console.print()


@app.command("load")
def benchmark_load(
    ctx: typer.Context,
    users: Annotated[int, typer.Option(help="Number of concurrent users")] = 1000,
    rate: Annotated[int, typer.Option(help="User spawn rate per second")] = 10,
):
    """Directly generate load using Locust."""
    header(f"Starting Custom Load: {users} users")
    set_locust_load(users, rate)
    console.print()


from amocna_cli.commands.benchmark.registry import ScenarioRegistry
import amocna_cli.commands.benchmark.scenarios  # Trigger registration


@app.command("run")
def benchmark_run(
    ctx: typer.Context,
    scenario: Annotated[
        str, typer.Option(help="Scenario to run (1, 2, 3, 4, 5, 6, 7, 8, 9, all)")
    ],
    keep_on_failure: Annotated[
        bool,
        typer.Option(
            "--keep-on-failure",
            help="Keep scenario resources for debugging when a scenario fails.",
        ),
    ] = False,
):
    """Run a specific scenario or all benchmarks."""
    cfg: ProjectConfig = ctx.obj

    if scenario == "all":
        header("Running Complete Automated Benchmark Cycle")
        for sc_id in ScenarioRegistry.list_ids():
            try:
                sc = ScenarioRegistry.get(sc_id, cfg)
                sc.run(keep_on_failure=keep_on_failure)
            except Exception as e:
                error(f"✖ Scenario {sc_id} failed, continuing with next. Error: {e}")

            info("Waiting 60 seconds between scenarios for cluster cooling...")
            time.sleep(60)
        info(
            "[green]✔ Complete automated benchmark run successfully completed![/green]"
        )
        return

    try:
        sc = ScenarioRegistry.get(scenario, cfg)
        sc.run(keep_on_failure=keep_on_failure)
    except ValueError as e:
        error(f"✖ {e}")
        sys.exit(1)
    finally:
        set_palamedes_filter([])

    console.print()


@app.command("remove")
def benchmark_remove(
    ctx: typer.Context,
    pods: Annotated[bool, typer.Option("--pods", help="Remove kwok virtual pods")] = False,
    nodes: Annotated[bool, typer.Option("--nodes", help="Remove kwok virtual nodes")] = False,
):
    """Remove kwok virtual pods and/or nodes."""
    cfg: ProjectConfig = ctx.obj
    
    if not pods and not nodes:
        pods = True
        nodes = True

    if pods:
        info("Removing kwok virtual pods...")
        run(["kubectl", "delete", "pods", "-n", "amocna-benchmark", "-l", "type=kwok-fake", "--grace-period=0", "--force", "--ignore-not-found"], check=False)
        info("Kwok virtual pods removed successfully.")

    if nodes:
        info("Checking for any active kwok virtual pods before deleting nodes...")
        pods_raw = run_capture(
            ["kubectl", "get", "pods", "-A", "-l", "type=kwok-fake", "-o", "jsonpath={.items[*].metadata.name}"],
            check=False
        )
        active_pods = pods_raw.strip().split() if pods_raw else []
        if active_pods:
            error(f"Error: Cannot remove kwok nodes. There are still {len(active_pods)} active kwok pods in the cluster.")
            sys.exit(1)
            
        info("Removing kwok virtual nodes...")
        run(["kubectl", "delete", "nodes", "-l", "type=kwok", "--ignore-not-found"], check=False)
        info("Kwok virtual nodes removed successfully.")

