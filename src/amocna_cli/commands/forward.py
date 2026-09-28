from __future__ import annotations

import sys
from typing import Optional
from typing_extensions import Annotated
import typer

from amocna_cli.config import ProjectConfig
from amocna_cli.utils.ui import console, header, info, error, run, run_capture
from amocna_cli.utils.shell import k8s_port_forward

app = typer.Typer()

@app.callback(invoke_without_command=True)
def forward_cmd(
    ctx: typer.Context,
    name: Annotated[str, typer.Argument(help="Forward target name")],
    local_port: Annotated[Optional[int], typer.Option("--local-port", help="Override local port")] = None,
):
    """Port-forward a K8s service or pod to localhost."""
    if ctx.invoked_subcommand is not None:
        return

    cfg: ProjectConfig = ctx.obj
    if name not in cfg.forwards:
        available = ", ".join(cfg.forwards.keys())
        error(f"Unknown forward target: {name}")
        error(f"Available: {available}")
        sys.exit(1)

    fwd = cfg.forwards[name]
    resolved_local_port = local_port or fwd.local_port

    target = fwd.service
    if fwd.pod_label:
        target_name = run_capture(["kubectl", "get", "pod", "-n", fwd.namespace, "-l", fwd.pod_label, "-o", "name"])
        if not target_name:
            error(f"No pods found in namespace {fwd.namespace} matching label {fwd.pod_label}")
            sys.exit(1)
        # If multiple pods match, take the first one
        target = target_name.splitlines()[0]

    header(f"Forwarding {name}")
    info(
        f"http://localhost:{resolved_local_port} → {fwd.namespace}/{target}:{fwd.remote_port}"
    )
    console.print("  [dim]Press Ctrl+C to stop.[/dim]\n")

    try:
        run(k8s_port_forward(fwd.namespace, target, resolved_local_port, fwd.remote_port))
    except KeyboardInterrupt:
        console.print()
        info("Forwarding stopped.")
