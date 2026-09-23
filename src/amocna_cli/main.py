from __future__ import annotations

import typer

from amocna_cli.config import find_project_root, load_config
from amocna_cli.commands.status import app as status_app
from amocna_cli.commands.version import app as version_app
from amocna_cli.commands.build import app as build_app
from amocna_cli.commands.test import app as test_app
from amocna_cli.commands.deploy import app as deploy_app
from amocna_cli.commands.undeploy import app as undeploy_app
from amocna_cli.commands.forward import app as forward_app
from amocna_cli.commands.login import app as login_app
from amocna_cli.commands.benchmark import app as benchmark_app
from amocna_cli.commands.scig import app as scig_app

app = typer.Typer(
    name="amocna",
    help="Unified orchestration CLI for the AMoCNA project.",
    no_args_is_help=True,
)

# Register command groups using add_typer
app.add_typer(status_app, name="status")
app.add_typer(version_app, name="version")
app.add_typer(build_app, name="build")
app.add_typer(test_app, name="test")
app.add_typer(deploy_app, name="deploy")
app.add_typer(undeploy_app, name="undeploy")
app.add_typer(forward_app, name="forward")
app.add_typer(login_app, name="login")
app.add_typer(benchmark_app, name="benchmark")
app.add_typer(scig_app, name="scig")

@app.callback()
def main_callback(ctx: typer.Context):
    """Load configuration for the project."""
    root = find_project_root()
    cfg = load_config(root)
    ctx.obj = cfg

def main():
    app()

if __name__ == "__main__":
    main()
