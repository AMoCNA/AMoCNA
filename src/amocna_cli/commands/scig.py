from __future__ import annotations

import json
import os
from pathlib import Path
import socket
import subprocess
import time
from typing import Optional
import typer
from rich.console import Console
from rich.table import Table

app = typer.Typer(help="SCIG (Supply Chain Integrity Guardian) management and evaluation commands.")
console = Console()

def is_port_open(host: str, port: int, timeout: float = 1.0) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except Exception:
        return False

def ensure_redis_connection(host: str, port: int) -> tuple[str, int, Optional[subprocess.Popen]]:
    """Verify Redis connection. If unreachable, fallback to k8s port-forward for deployment/redis or svc/redis in namespace redis."""
    if is_port_open(host, port):
        return host, port, None

    console.print(f"[yellow]Redis at {host}:{port} is unreachable. Attempting Kubernetes port-forward from namespace 'redis'...[/yellow]")

    targets = ["deployment/redis", "service/redis", "svc/redis"]
    pf_proc = None

    for target in targets:
        pf_cmd = ["kubectl", "port-forward", "-n", "redis", target, f"{port}:{port}"]
        try:
            pf_proc = subprocess.Popen(pf_cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            for _ in range(6):
                time.sleep(0.5)
                if is_port_open("localhost", port):
                    console.print(f"[bold green]Port-forward established successfully to {target} on localhost:{port}[/bold green]")
                    return "localhost", port, pf_proc
            pf_proc.terminate()
        except Exception:
            if pf_proc:
                pf_proc.terminate()

    console.print(f"[bold red]Could not connect to Redis at {host}:{port} and Kubernetes cluster is unreachable or missing Redis deployment.[/bold red]")
    console.print("[dim]Ensure Redis is running via './amocna.py deploy --all' or port-forwarding is available.[/dim]")
    raise typer.Exit(code=1)

@app.command("scan")
def scan(
    local: bool = typer.Option(False, "--local", "-l", help="Run local scan.sh script instead of Kubernetes job"),
    namespace: str = typer.Option("sock-shop", "--namespace", "-n", help="Target namespace to discover pod images"),
    discover: bool = typer.Option(True, "--discover/--no-discover", help="Automatically discover cluster pod images"),
    redis_host: str = typer.Option("localhost", "--redis-host", "-r", help="Redis host for scanning output"),
    redis_port: int = typer.Option(6379, "--redis-port", "-p", help="Redis port"),
):
    """Trigger a SCIG vulnerability scan across cluster or local images."""
    if local:
        console.print("[bold blue]Starting local SCIG vulnerability scan...[/bold blue]")
        target_host, target_port, pf_proc = ensure_redis_connection(redis_host, redis_port)
        try:
            env = os.environ.copy()
            env["REDIS_HOST"] = target_host
            env["REDIS_PORT"] = str(target_port)
            env["DISCOVER_CLUSTER_IMAGES"] = "true" if discover else "false"
            env["SCAN_NAMESPACES"] = namespace

            cmd = ["./infra/scig/scan.sh"]
            res = subprocess.run(cmd, env=env)
            if res.returncode == 0:
                console.print("[bold green]Local SCIG scan completed successfully![/bold green]")
            else:
                console.print(f"[bold red]SCIG scan failed with exit code {res.returncode}[/bold red]")
                raise typer.Exit(code=res.returncode)
        finally:
            if pf_proc:
                pf_proc.terminate()
    else:
        console.print("[bold blue]Triggering Kubernetes SCIG scan job...[/bold blue]")
        job_name = f"scig-manual-{int(time.time())}"
        cmd = [
            "kubectl", "-n", "amocna-scig", "create", "job", job_name, "--from=cronjob/scig"
        ]
        res = subprocess.run(cmd)
        if res.returncode == 0:
            console.print(f"[bold green]Triggered SCIG Kubernetes Job: {job_name}[/bold green]")
            console.print(f"Follow logs with: kubectl -n amocna-scig logs -f job/{job_name}")
        else:
            console.print(f"[bold red]Failed to create SCIG job in Kubernetes[/bold red]")
            raise typer.Exit(code=res.returncode)

@app.command("status")
def status(
    redis_host: str = typer.Option("localhost", "--redis-host", "-r", help="Redis host"),
    redis_port: int = typer.Option(6379, "--redis-port", "-p", help="Redis port"),
):
    """Check current SCIG Redis vulnerability store status and cached SBOMs."""
    target_host, target_port, pf_proc = ensure_redis_connection(redis_host, redis_port)
    console.print(f"[bold blue]Checking SCIG vulnerability cache at {target_host}:{target_port}...[/bold blue]")
    
    try:
        try:
            keys_out = subprocess.check_output(
                ["redis-cli", "-h", target_host, "-p", str(target_port), "--scan", "--pattern", "sbom:meta:*"],
                text=True
            ).strip().splitlines()
        except Exception as e:
            console.print(f"[bold red]Could not connect to Redis: {e}[/bold red]")
            raise typer.Exit(code=1)

        if not keys_out or keys_out == ['']:
            console.print("[yellow]No SCIG metadata keys found in Redis store.[/yellow]")
            return

        table = Table(title="SCIG Cached Images & Vulnerability Summary")
        table.add_column("Repository", style="cyan")
        table.add_column("Tag", style="magenta")
        table.add_column("Packages", justify="right")
        table.add_column("CVEs Total", justify="right")
        table.add_column("Critical", justify="right", style="bold red")
        table.add_column("High", justify="right", style="red")
        table.add_column("Medium", justify="right", style="yellow")
        table.add_column("Scanned At", style="green")

        for key in keys_out:
            meta_json = subprocess.check_output(
                ["redis-cli", "-h", target_host, "-p", str(target_port), "GET", key],
                text=True
            )
            if meta_json:
                try:
                    data = json.loads(meta_json)
                    table.add_row(
                        data.get("repository", "-"),
                        data.get("tag", "-"),
                        str(data.get("packageCount", 0)),
                        str(data.get("vulnerabilityCount", 0)),
                        str(data.get("criticalCount", 0)),
                        str(data.get("highCount", 0)),
                        str(data.get("mediumCount", 0)),
                        data.get("scannedAt", "-")
                    )
                except json.JSONDecodeError:
                    continue

        console.print(table)
    finally:
        if pf_proc:
            pf_proc.terminate()

@app.command("evaluate")
def evaluate(
    experiment: str = typer.Option("all", "--experiment", "-e", help="Experiment to run: s1, s2, s3, s4, or all"),
    iterations: int = typer.Option(10, "--iterations", "-i", help="Number of benchmark iterations"),
    output_dir: str = typer.Option("./evaluation_results", "--output-dir", "-o", help="Directory for output files"),
    images_file: Optional[str] = typer.Option(None, "--images-file", help="Override path to images.txt"),
    redis_host: str = typer.Option("localhost", "--redis-host", "-r", help="Redis host"),
    redis_port: int = typer.Option(6379, "--redis-port", "-p", help="Redis port"),
):
    """
    Run SCIG academic evaluation experiments.

    Experiments:
      s1 — SBOM & CVE Detection Performance (Grype + Trivy, 24 images)
      s2 — Multi-Service E2E Vulnerability Remediation (3 Sock Shop services)
      s3 — Multi-App Cross-Namespace Scanning Scalability
      s4 — Policy Evaluation Latency & Security Event Throughput
      all — Run all experiments sequentially
    """
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    target_host, target_port, pf_proc = ensure_redis_connection(redis_host, redis_port)

    try:
        experiments = [experiment] if experiment != "all" else ["s1", "s2", "s3", "s4"]
        images_path = Path(images_file) if images_file else Path("infra/scig/images.txt")

        for exp in experiments:
            console.print(f"\n[bold green]═══ Running Experiment {exp.upper()} ═══[/bold green]\n")
            if exp == "s1":
                from .scig_eval.experiment_s1 import run_s1
                images = []
                if images_path.exists():
                    with open(images_path, "r") as f:
                        images = [line.strip() for line in f if line.strip() and not line.startswith("#")]
                if not images:
                    images = ["docker.io/weaveworksdemos/front-end:0.3.0", "docker.io/weaveworksdemos/orders:0.3.1"]
                run_s1(
                    images=images,
                    iterations=iterations,
                    output_dir=output_path,
                    redis_host=target_host,
                    redis_port=target_port,
                )
            elif exp == "s2":
                from .scig_eval.experiment_s2 import run_s2
                run_s2(
                    iterations=min(iterations, 5),
                    output_dir=output_path,
                    redis_host=target_host,
                    redis_port=target_port,
                )
            elif exp == "s3":
                from .scig_eval.experiment_s3 import run_s3
                run_s3(
                    iterations=min(iterations, 5),
                    output_dir=output_path,
                    redis_host=target_host,
                    redis_port=target_port,
                )
            elif exp == "s4":
                from .scig_eval.experiment_s4 import run_s4
                run_s4(
                    iterations=iterations,
                    output_dir=output_path,
                    redis_host=target_host,
                    redis_port=target_port,
                )
            else:
                console.print(f"[red]Unknown experiment: {exp}[/red]")

        console.print(f"\n[bold green]✓ All results saved to {output_path}/[/bold green]")
    finally:
        if pf_proc:
            pf_proc.terminate()

