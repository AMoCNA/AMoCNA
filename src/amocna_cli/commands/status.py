from __future__ import annotations

import typer
from rich.table import Table
from rich.panel import Panel

from amocna_cli.config import ProjectConfig
from amocna_cli.commands.version import read_pom_version
from amocna_cli.utils.ui import console, header

app = typer.Typer()

@app.callback(invoke_without_command=True)
def status_cmd(ctx: typer.Context):
    """Show project overview, application lists, and port forwarding configuration."""
    if ctx.invoked_subcommand is not None:
        return

    cfg: ProjectConfig = ctx.obj
    header("AMoCNA Project Status")

    current_version = read_pom_version(cfg.project_root / cfg.parent_pom)

    # 1. Overview Panel
    overview_text = (
        f"[bold cyan]Project:[/bold cyan]  {cfg.name}\n"
        f"[bold cyan]Root:[/bold cyan]     {cfg.project_root}\n"
        f"[bold cyan]Registry:[/bold cyan] {cfg.registry}\n"
        f"[bold cyan]Version:[/bold cyan]  [bold green]{current_version}[/bold green] [dim](core apps)[/dim]"
    )
    console.print(Panel(overview_text, title="Project Overview", expand=False))
    console.print()

    # 2. Core Apps Table
    core_table = Table(title="Core Apps [dim](version-synced)[/dim]", show_header=True, header_style="bold cyan")
    core_table.add_column("Status", justify="center", width=8)
    core_table.add_column("App Name", style="bold green", width=22)
    core_table.add_column("Type", style="magenta", width=12)
    core_table.add_column("Description")

    for name, app_def in cfg.apps.items():
        if not app_def.is_core:
            continue
        exists = (cfg.project_root / app_def.path).is_dir()
        status = "[green]●[/green]" if exists else "[red]●[/red]"
        core_table.add_row(status, name, app_def.app_type, app_def.description)

    console.print(core_table)
    console.print()

    # 3. Standalone Apps Table
    standalone_table = Table(title="Standalone Apps [dim](independent versioning)[/dim]", show_header=True, header_style="bold cyan")
    standalone_table.add_column("Status", justify="center", width=8)
    standalone_table.add_column("App Name", style="bold blue", width=22)
    standalone_table.add_column("Type", style="magenta", width=12)
    standalone_table.add_column("Description")

    for name, app_def in cfg.apps.items():
        if app_def.is_core:
            continue
        exists = (cfg.project_root / app_def.path).is_dir()
        status = "[green]●[/green]" if exists else "[red]●[/red]"
        standalone_table.add_row(status, name, app_def.app_type, app_def.description)

    console.print(standalone_table)
    console.print()

    # 4. Port Forward Shortcuts Table
    forward_table = Table(title="Port Forward Shortcuts", show_header=True, header_style="bold cyan")
    forward_table.add_column("Shortcut Name", style="bold yellow", width=22)
    forward_table.add_column("Local Port", justify="right", width=12)
    forward_table.add_column("Service Target")

    for name, fwd in cfg.forwards.items():
        if fwd.pod_label:
            target = f"{fwd.namespace}/pod(label={fwd.pod_label}):{fwd.remote_port}"
        else:
            target = f"{fwd.namespace}/{fwd.service}:{fwd.remote_port}"
        forward_table.add_row(name, str(fwd.local_port), target)

    console.print(forward_table)
    console.print()
