from __future__ import annotations

import subprocess
import sys
from typing_extensions import Annotated
import typer

from amocna_cli.config import ProjectConfig
from amocna_cli.utils.ui import console, header, info, warn, error, run
from amocna_cli.utils.shell import (
    GRAPHDB_WIPE_JOB_TEMPLATE,
    k8s_delete_kustomization,
    k8s_delete_resource,
    k8s_apply_stdin,
    k8s_wait_job_complete
)

app = typer.Typer()

def _kubectl_apply_stdin(manifest_yaml: str, dry_run: bool = False) -> None:
    subprocess.run(
        k8s_apply_stdin(dry_run=dry_run),
        input=manifest_yaml,
        text=True,
        check=True,
    )

def _wipe_graphdb_host_data(cfg: ProjectConfig, dry_run: bool = False) -> None:
    """Delete GraphDB files on the PV hostPath (survives namespace/PV deletion)."""
    host_path = cfg.graphdb_host_path
    node = cfg.graphdb_node_hostname
    job_name = "amocna-graphdb-wipe"

    header("Wiping GraphDB persistent data")
    info(f"Host path {host_path} on node {node}")

    manifest = GRAPHDB_WIPE_JOB_TEMPLATE.format(
        job_name=job_name,
        node=node,
        host_path=host_path
    )

    with console.status("[bold yellow]Cleaning up old wipe jobs...[/bold yellow]"):
        run(k8s_delete_resource("job", job_name, namespace="default", dry_run=dry_run), check=False)
        
    with console.status("[bold yellow]Launching data wipe Job...[/bold yellow]"):
        _kubectl_apply_stdin(manifest, dry_run=dry_run)

    if not dry_run:
        with console.status("[bold yellow]Waiting for data wipe Job completion...[/bold yellow]"):
            result = run(
                k8s_wait_job_complete(job_name, namespace="default", timeout="120s"),
                check=False,
            )
        
        with console.status("[bold yellow]Cleaning up wipe Job...[/bold yellow]"):
            run(k8s_delete_resource("job", job_name, namespace="default"), check=False)

        if result.returncode != 0:
            error(
                f"GraphDB data wipe failed at {host_path} on node {node}. "
                f"Check: kubectl logs job/{job_name} -n default"
            )
            sys.exit(1)
        info("GraphDB host data wiped.")
    else:
        with console.status("[bold yellow]Dry Run: Cleaning up wipe Job...[/bold yellow]"):
            run(k8s_delete_resource("job", job_name, namespace="default", dry_run=True), check=False)
        info("[yellow](Dry Run) Skipped waiting for wipe Job completion.[/yellow]")

def _undeploy_graphdb(cfg: ProjectConfig, *, keep_data: bool = False, dry_run: bool = False) -> None:
    """Remove GraphDB and optionally wipe its hostPath volume."""
    if not keep_data:
        info("Stopping GraphDB before wiping data...")
        with console.status("[bold red]Stopping GraphDB deployment...[/bold red]"):
            run(
                k8s_delete_resource(
                    "deployment",
                    "graphdb",
                    namespace="graphdb",
                    dry_run=dry_run,
                    wait=True,
                    timeout="120s"
                ),
                check=False,
            )
        if not dry_run:
            with console.status("[bold yellow]Waiting for GraphDB pods to terminate...[/bold yellow]"):
                run(
                    [
                        "kubectl", "wait", "--for=delete",
                        "pod", "-l", "app=graphdb",
                        "-n", "graphdb", "--timeout=120s",
                    ],
                    check=False,
                )
        _wipe_graphdb_host_data(cfg, dry_run=dry_run)
    else:
        warn("Keeping GraphDB hostPath data (--keep-graphdb-data). Old repository data will remain.")

    with console.status("[bold red]Deleting GraphDB namespace, PV, and StorageClass...[/bold red]"):
        run(k8s_delete_resource("namespace", "graphdb", dry_run=dry_run, timeout="60s"), check=False)
        run(k8s_delete_resource("pv", "graphdb-pv", dry_run=dry_run), check=False)
        run(k8s_delete_resource("storageclass", "local-storage", dry_run=dry_run), check=False)

@app.callback(invoke_without_command=True)
def undeploy_cmd(
    ctx: typer.Context,
    keep_graphdb_data: Annotated[bool, typer.Option("--keep-graphdb-data", help="Do not wipe GraphDB hostPath data")] = False,
    dry_run: Annotated[bool, typer.Option("--dry-run", help="Execute in dry-run mode (server dry-run, skip wipe wait)")] = False,
):
    """Remove AMoCNA stack from Kubernetes."""
    if ctx.invoked_subcommand is not None:
        return

    cfg: ProjectConfig = ctx.obj

    if dry_run:
        warn("DRY RUN MODE ACTIVE — server-side dry-run will occur, no actual resources will be deleted")

    header("Undeploying AMoCNA from Kubernetes")

    full_path = cfg.project_root / "infra"
    with console.status(f"[bold red]Deleting Kubernetes Kustomization in {full_path}...[/bold red]"):
        run(k8s_delete_kustomization(str(full_path), dry_run=dry_run), check=False)

    _undeploy_graphdb(cfg, keep_data=keep_graphdb_data, dry_run=dry_run)

    if dry_run:
        info("Dry Run completed successfully. (Commands shown but cluster state unchanged)")
    else:
        info("AMoCNA has been removed from the cluster.")
