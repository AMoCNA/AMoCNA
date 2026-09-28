from __future__ import annotations

import os
import subprocess
import sys
from typing import Optional
from typing_extensions import Annotated
import typer

from amocna_cli.config import ProjectConfig
from amocna_cli.utils.ui import header, info, error
from amocna_cli.utils.shell import docker_login

app = typer.Typer()

def check_pat() -> str:
    """Ensure AMOCNA_PAT is set in the environment."""
    pat = os.environ.get("AMOCNA_PAT")
    if not pat:
        error("AMOCNA_PAT environment variable is not set.")
        error("Please set it: export AMOCNA_PAT=your_github_token")
        sys.exit(1)
    return pat

@app.callback(invoke_without_command=True)
def login_cmd(
    ctx: typer.Context,
    registry: Annotated[Optional[str], typer.Option(help="Docker registry (overrides config & env)")] = None,
    user: Annotated[Optional[str], typer.Option(help="GitHub username (overrides AMOCNA_USER env)")] = None,
):
    """Login to Docker registry using GitHub PAT token (AMOCNA_PAT)."""
    if ctx.invoked_subcommand is not None:
        return

    cfg: ProjectConfig = ctx.obj
    resolved_registry = registry or cfg.registry
    pat = check_pat()
    resolved_user = user or os.environ.get("AMOCNA_USER")

    if not resolved_user:
        error("User not specified. Use --user or set AMOCNA_USER.")
        sys.exit(1)

    header(f"Logging in to {resolved_registry}")
    subprocess.run(
        docker_login(resolved_registry, resolved_user),
        input=pat,
        text=True,
        check=True,
    )
    info(f"Successfully logged in to {resolved_registry}")
