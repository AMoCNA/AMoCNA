from __future__ import annotations

import subprocess
import sys
from pathlib import Path
from rich.console import Console

console = Console()
err_console = Console(stderr=True)

def info(msg: str) -> None:
    """Print an informational success message with a green checkmark."""
    console.print(f"[green]✔[/green] {msg}")

def warn(msg: str) -> None:
    """Print a warning message with a yellow exclamation mark."""
    console.print(f"[yellow]⚠[/yellow] {msg}")

def error(msg: str) -> None:
    """Print an error message with a red cross mark to stderr."""
    err_console.print(f"[red]✖[/red] {msg}")

def header(msg: str) -> None:
    """Print a section header decorated with horizontal lines."""
    width = 60
    console.print()
    console.print(f"[cyan]{'─' * width}[/cyan]")
    console.print(f"  [bold]{msg}[/bold]")
    console.print(f"[cyan]{'─' * width}[/cyan]")

def run(
    cmd: list[str], cwd: Path | None = None, check: bool = True, capture: bool = False
) -> subprocess.CompletedProcess:
    """Run a subprocess, streaming output unless capture=True."""
    console.print(f"  [dim]$ {' '.join(cmd)}[/dim]")
    return subprocess.run(
        cmd,
        cwd=cwd,
        check=check,
        capture_output=capture,
        text=True,
    )

def run_capture(cmd: list[str], cwd: Path | None = None, check: bool = True) -> str:
    """Run a subprocess and capture stdout."""
    res = subprocess.run(cmd, cwd=cwd, check=check, capture_output=True, text=True)
    return res.stdout.strip()
