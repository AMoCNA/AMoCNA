"""CNEEOnt paper evaluation: two scenarios on three microservice suites."""

from __future__ import annotations

from pathlib import Path

import typer
from rich.console import Console

from amocna_cli.config import ProjectConfig

app = typer.Typer(help="Paper evaluation: image patching and SLA scale-out on three apps.")
console = Console()


@app.command("run")
def paper_eval_run(
    ctx: typer.Context,
    example: str = typer.Option("all", "--example", "-e", help="1, 2, or all"),
    iterations: int = typer.Option(5, "--iterations", "-i", help="Repeats per example (N>=5 recommended)"),
    output_dir: str = typer.Option("./evaluation_results", "--output-dir", "-o"),
    offline: bool = typer.Option(False, "--offline", help="Write table fixtures without talking to the cluster"),
    timeout_s: int = typer.Option(420, "--timeout", help="Per-iteration wait for patch/scale (seconds)"),
):
    """Run Example 1 (patching) and/or Example 2 (horizontal scaling)."""
    cfg: ProjectConfig = ctx.obj
    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=True)
    if offline:
        from amocna_cli.commands.paper_eval.offline import write_offline_artifacts

        write_offline_artifacts(out)
        console.print(f"[yellow]Wrote offline table fixtures to {out}[/yellow]")
        return
    examples = [example] if example != "all" else ["1", "2"]
    for exp in examples:
        if exp in ("1", "e1"):
            from amocna_cli.commands.paper_eval.example1 import run_example1

            run_example1(cfg, iterations=max(iterations, 1), output_dir=out, remediation_timeout_s=timeout_s)
        elif exp in ("2", "e2"):
            from amocna_cli.commands.paper_eval.example2 import run_example2

            run_example2(cfg, iterations=max(iterations, 1), output_dir=out, observe_timeout_s=timeout_s)
        else:
            raise typer.BadParameter("example must be 1, 2, or all")
