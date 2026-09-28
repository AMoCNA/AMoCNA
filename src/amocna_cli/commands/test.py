from __future__ import annotations

import sys
from typing import Optional
from typing_extensions import Annotated
import typer

from amocna_cli.config import ProjectConfig
from amocna_cli.commands.build import resolve_apps
from amocna_cli.utils.ui import header, info, warn, error, run

app = typer.Typer()

@app.callback(invoke_without_command=True)
def test_cmd(
    ctx: typer.Context,
    app_name: Annotated[Optional[str], typer.Option("--app", help="App name to test")] = None,
    all_apps: Annotated[bool, typer.Option("--all", help="Test all apps")] = False,
):
    """Run Maven tests for specified apps."""
    if ctx.invoked_subcommand is not None:
        return

    cfg: ProjectConfig = ctx.obj
    apps_to_test = resolve_apps(cfg, app_name, all_apps)
    parent_pom = cfg.project_root / cfg.parent_pom

    for app_def in apps_to_test:
        if app_def.app_type not in ("maven-app", "maven-lib"):
            warn(f"Skipping {app_def.name} (not a Maven project)")
            continue

        header(f"Testing {app_def.name}")
        pom_path = cfg.project_root / app_def.path / "pom.xml"
        if not pom_path.is_file():
            error(f"pom.xml not found: {pom_path}")
            sys.exit(1)

        if app_def.is_core:
            if not parent_pom.is_file():
                error(f"parent POM not found: {parent_pom}")
                sys.exit(1)
            run(
                ["mvn", "test", "-pl", app_def.path, "-am"],
                cwd=cfg.project_root,
            )
        else:
            run(["mvn", "test", "-f", str(pom_path)])
        info(f"{app_def.name} tests passed")
