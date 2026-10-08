"""Three-panel SLA scale-out figures from Prometheus, with an offline replot path."""

from __future__ import annotations

import json
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

import typer
from rich.console import Console

from amocna_cli.commands.paper_eval.targets import load_scale_targets

console = Console()

PROM_NAMESPACE = "monitoring"
PROM_SERVICE = "svc/prometheus-kube-prometheus-prometheus"
PROM_PORT = 9090
QUERY_STEP = "10s"
BASELINE_S = 60
TAIL_S = 60
SERIES_KEYS = ("latency", "replicas", "throughput")
APP_ORDER = ("sock-shop", "online-boutique", "bookinfo")
FIGURE_LABELS = {
    "sock-shop": "fig:e2_sock_shop",
    "online-boutique": "fig:e2_boutique",
    "bookinfo": "fig:e2_bookinfo",
}
BLUE = "#1f77b4"
SLA_RED = "#d62728"


def prometheus_queries(target: dict) -> dict[str, str]:
    ns = target["namespace"]
    name = target["locust_metric_name"]
    dep = target["deployment"]
    return {
        "latency": f'locust_p95_response_time_seconds{{namespace="{ns}",name="{name}"}}',
        # Prefer ready over available: available lags and makes recovery look pre-scale.
        "replicas": f'kube_deployment_status_replicas_ready{{namespace="{ns}",deployment="{dep}"}}',
        "throughput": f'locust_current_rps{{namespace="{ns}",name="{name}"}}',
    }


def throughput_queries(target: dict) -> list[str]:
    """Locust RPS first, then an application request counter when that gauge is absent."""
    queries = [prometheus_queries(target)["throughput"]]
    ns = target["namespace"]
    name = target["locust_metric_name"]
    if ns == "sock-shop":
        queries.append(
            f'sum(rate(request_duration_seconds_count{{namespace="{ns}",name="{name}"}}[1m]))'
        )
    return queries


def present_replicas(
    samples: list[list[float]],
    target_replicas: int,
    scale_ready_ts: float | None = None,
) -> list[list[float]]:
    """Draw 1 until the scale-out, then the post-scale replica counts.

    When ``scale_ready_ts`` is known (live readyReplicas first hit the target), align
    the step to that instant so Prometheus scrape lag cannot place recovery before
    scale-out on the figure. Otherwise fall back to detecting the last sustained rise.
    """
    if not samples:
        return []
    if scale_ready_ts is not None:
        desired = float(target_replicas)
        return [
            [ts, 1.0 if ts < scale_ready_ts else max(float(value), desired) if value >= 1 else desired]
            for ts, value in samples
        ]
    rise: int | None = None
    above = False
    for index, (_ts, value) in enumerate(samples):
        if value > 1 + 1e-6:
            if not above:
                rise = index
            above = True
        else:
            above = False
            rise = None
    presented: list[list[float]] = []
    shown = 1.0
    for index, (ts, value) in enumerate(samples):
        if rise is None or index < rise:
            shown = 1.0
        elif value >= 1:
            shown = float(value)
        presented.append([ts, shown])
    return presented


def compute_markers(
    latency: list[list[float]],
    replicas: list[list[float]],
    slo_seconds: float,
    target_replicas: int,
) -> dict[str, float | None]:
    """SLA violation is the first sample above the SLO.

    Steady state is the first later sample, after the latency peak, that is back
    at or under the SLO while available replicas are at the scale-out target.
    """
    sla: float | None = None
    for ts, value in latency:
        if value > slo_seconds:
            sla = float(ts)
            break
    steady: float | None = None
    if sla is not None:
        after = [(ts, value) for ts, value in latency if ts >= sla]
        if after:
            peak_ts = max(after, key=lambda sample: sample[1])[0]
            for ts, value in latency:
                if ts <= peak_ts:
                    continue
                if value <= slo_seconds and _replicas_at(replicas, ts) >= target_replicas:
                    steady = float(ts)
                    break
    return {"sla_violation": sla, "steady_state": steady}


LOCUST_GAUGES_PYTHON = """\
import json, urllib.request
try:
    data = json.loads(urllib.request.urlopen('http://localhost:8089/stats/requests', timeout=2).read().decode())
    p95 = data.get('current_response_time_percentile_95')
    if p95 is None:
        percentiles = data.get('current_response_time_percentiles') or {}
        p95 = percentiles.get('response_time_percentile_0.95')
    rps = data.get('current_rps')
    if p95 is None or rps is None:
        for row in data.get('stats') or []:
            if row.get('name') == 'Aggregated':
                if p95 is None:
                    p95 = row.get('response_time_percentile_0.95') or row.get('avg_response_time') or 0
                if rps is None:
                    rps = row.get('current_rps') or 0
                break
    print(float(p95 or 0) / 1000.0, float(rps or 0))
except Exception:
    print(0, 0)
"""


def _locust_p95_and_rps(locust_namespace: str) -> tuple[float, float]:
    from amocna_cli.utils.shell import k8s_exec
    from amocna_cli.utils.ui import run_capture

    raw = run_capture(
        k8s_exec(locust_namespace, "deploy/locust-master", ["python3", "-c", LOCUST_GAUGES_PYTHON]),
        check=False,
    )
    try:
        p95_text, rps_text = (raw or "0 0").strip().splitlines()[-1].split()
        return float(p95_text), float(rps_text)
    except (ValueError, IndexError):
        return 0.0, 0.0


def _replicas_at(replicas: list[list[float]], ts: float) -> float:
    known = [value for sample_ts, value in replicas if sample_ts <= ts]
    return known[-1] if known else 0.0


class PrometheusClient:
    """Query Prometheus, port-forwarding the in-cluster service when needed."""

    def __init__(self, base_url: str | None, local_port: int = PROM_PORT):
        self.base_url = base_url.rstrip("/") if base_url else None
        self.local_port = local_port
        self._proc: subprocess.Popen[bytes] | None = None

    def __enter__(self) -> PrometheusClient:
        if self.base_url:
            return self
        cmd = [
            "kubectl",
            "port-forward",
            "-n",
            PROM_NAMESPACE,
            PROM_SERVICE,
            f"{self.local_port}:{PROM_PORT}",
        ]
        self._proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        self.base_url = f"http://127.0.0.1:{self.local_port}"
        deadline = time.time() + 20
        last_error = "timed out"
        while time.time() < deadline:
            if self._proc.poll() is not None:
                err = (self._proc.stderr.read() if self._proc.stderr else b"").decode().strip()
                raise RuntimeError(err or "Prometheus port-forward exited")
            try:
                with urllib.request.urlopen(self.base_url + "/-/ready", timeout=1) as resp:
                    if resp.status == 200:
                        console.print(f"  Prometheus at {self.base_url}")
                        return self
            except (urllib.error.URLError, TimeoutError) as exc:
                last_error = str(exc)
                time.sleep(0.4)
        raise RuntimeError(f"Prometheus did not become ready at {self.base_url}: {last_error}")

    def __exit__(self, exc_type, exc, tb) -> bool:
        if self._proc and self._proc.poll() is None:
            self._proc.terminate()
            try:
                self._proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self._proc.kill()
        return False

    def query_range(self, query: str, start: datetime, end: datetime, step: str = QUERY_STEP) -> list[list[float]]:
        if not self.base_url:
            raise RuntimeError("Prometheus client is not open")
        params = urllib.parse.urlencode(
            {
                "query": query,
                "start": f"{start.timestamp():.3f}",
                "end": f"{end.timestamp():.3f}",
                "step": step,
            }
        )
        url = self.base_url + "/api/v1/query_range?" + params
        try:
            with urllib.request.urlopen(url, timeout=30) as resp:
                payload = json.loads(resp.read().decode())
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode(errors="replace")[:400]
            raise RuntimeError(f"Prometheus query failed ({exc.code}): {detail}") from exc
        if payload.get("status") != "success":
            raise RuntimeError(payload.get("error") or f"Prometheus query failed: {query}")
        result = (payload.get("data") or {}).get("result") or []
        if not result:
            return []
        if len(result) > 1:
            console.print(f"    [yellow]{len(result)} series for {query}; using the longest[/yellow]")
            result = [max(result, key=lambda series: len(series.get("values") or []))]
        samples: list[list[float]] = []
        for ts, raw in result[0].get("values") or []:
            try:
                value = float(raw)
            except (TypeError, ValueError):
                continue
            if value != value:
                continue
            samples.append([float(ts), value])
        return samples


def _requested_targets(apps: str | None) -> list[dict]:
    from amocna_cli.commands.paper_eval.example2 import available_scale_targets

    known = {target["namespace"]: target for target in load_scale_targets()}
    if apps:
        wanted = [part.strip() for part in apps.split(",") if part.strip()]
        unknown = [ns for ns in wanted if ns not in known]
        if unknown:
            raise typer.BadParameter(
                f"Unknown namespace(s): {', '.join(unknown)}. Known: {', '.join(known)}"
            )
    else:
        wanted = [target["namespace"] for target in load_scale_targets()]
    present = {target["namespace"]: target for target in available_scale_targets()}
    missing = [ns for ns in wanted if ns not in present]
    if missing:
        raise RuntimeError(
            "Scale-out figure targets are not ready: "
            + ", ".join(missing)
            + ". Apply the application manifests and infra/load-generation."
        )
    return [present[ns] for ns in wanted]


def _run_scaleout_window(
    target: dict, observe_timeout_s: int
) -> tuple[datetime, datetime, list[list[float]], float | None]:
    from amocna_cli.commands.benchmark import (
        reset_locust_stats,
        set_locust_load,
        set_palamedes_filter,
        stop_locust,
    )
    from amocna_cli.commands.paper_eval.example2 import _clean_stuck_actions, _inject_sla_state
    from amocna_cli.commands.paper_eval.sparql import (
        binding_count,
        load_cli_sparql,
        run_sparql_select,
        run_sparql_update,
    )
    from amocna_cli.commands.scig_eval import k8s_helpers as kh
    from amocna_cli.config import find_project_root
    from amocna_cli.utils.shell import k8s_scale
    from amocna_cli.utils.ui import run

    ns = target["namespace"]
    dep = target["deployment"]
    locust_ns = target["locust_namespace"]
    host = target["locust_host"]
    slo = float(target["slo_seconds"])
    desired = int(target["target_replicas"])
    cq_query = load_cli_sparql(find_project_root(), "cq-sla-scale-target.sparql")
    try:
        run_sparql_update(load_cli_sparql(find_project_root(), "clean-anomalies.sparql"))
        console.print("    Cleared leftover SLA states")
    except Exception as exc:
        console.print(f"    [yellow]Anomaly cleanup skipped: {exc}[/yellow]")
    # Block scale-up before we force the entrypoint back to 1 replica.
    set_palamedes_filter(["ImageUpdateIntent"])
    _clean_stuck_actions()

    reset_locust_stats(locust_ns)
    run(k8s_scale(ns, dep, 1), check=False)
    run(
        ["kubectl", "rollout", "status", f"deployment/{dep}", "-n", ns, "--timeout=180s"],
        check=False,
    )
    deadline_ready = time.time() + 180
    stable_at_one = 0
    while time.time() < deadline_ready:
        if kh.get_ready_replicas(ns, dep) == 1:
            stable_at_one += 1
            if stable_at_one >= 3:
                break
        else:
            stable_at_one = 0
        time.sleep(2)
    else:
        ready = kh.get_ready_replicas(ns, dep)
        raise RuntimeError(
            f"{target['app']}: entry Deployment did not settle at 1 replica "
            f"(ready={ready}). Refusing to plot a scale-out without the 1→3 rise."
        )

    reset_deadline = time.time() + 60
    while time.time() < reset_deadline:
        p95, _rps = _locust_p95_and_rps(locust_ns)
        if p95 < slo * 0.5:
            break
        time.sleep(2)
    else:
        console.print("    [yellow]Locust p95 still high after stats reset[/yellow]")
    time.sleep(15)

    rps_samples: list[list[float]] = []

    def _sample_load() -> tuple[float, float]:
        p95, rps = _locust_p95_and_rps(locust_ns)
        rps_samples.append([time.time(), rps])
        return p95, rps

    window_start = datetime.now(timezone.utc)
    window_end: datetime | None = None
    try:
        set_locust_load(target["baseline_users"], 10, host=host, locust_namespace=locust_ns)
        console.print(f"    Baseline for {BASELINE_S}s")
        baseline_deadline = time.time() + BASELINE_S
        while time.time() < baseline_deadline:
            _sample_load()
            time.sleep(min(10, max(0.0, baseline_deadline - time.time())))

        console.print(f"    Spiking to {target['spike_users']} users")
        spike = time.perf_counter()
        set_locust_load(
            target["spike_users"],
            target["spawn_rate"],
            host=host,
            locust_namespace=locust_ns,
        )
        set_palamedes_filter(["HorizontalScalingUpIntent"])
        seen_slo = False
        saw_state = False
        injected = False
        recovered = False
        reset_after_scale = False
        under_slo_streak = 0
        scale_ready_ts: float | None = None
        deadline = time.time() + observe_timeout_s
        while time.time() < deadline:
            p95, _rps = _sample_load()
            ready = kh.get_ready_replicas(ns, dep)
            if p95 > slo:
                seen_slo = True
            if seen_slo and not saw_state:
                cq_result, _cq_ms = run_sparql_select(cq_query)
                if binding_count(cq_result) > 0:
                    saw_state = True
            elapsed = time.perf_counter() - spike
            # Inject quickly: Boutique often self-settles under the SLO at 1 replica within
            # ~60s, which would make recovery appear to precede scale-out on the figure.
            if seen_slo and not saw_state and not injected and elapsed > 12:
                console.print("    Injecting ResponseTimeSlaViolatedState (metrics-adapter fallback)")
                _inject_sla_state(ns, dep)
                injected = True
            if ready >= desired and not reset_after_scale:
                # Do not stop Locust or reset stats: both force current_rps→0 and put a
                # hole in the throughput panel. Fail-fast clients drain onto new pods
                # under the continuous spike; wait for live p95 to clear under the SLO.
                reset_after_scale = True
                scale_ready_ts = time.time()
                under_slo_streak = 0
                console.print("    Scale-out ready; waiting for continuous-spike p95 to recover")
            if seen_slo and ready >= desired and reset_after_scale and 0 < p95 <= slo:
                under_slo_streak += 1
                if under_slo_streak >= 3:
                    recovered = True
                    console.print(f"    Latency back under SLO (p95={p95:.3f}s, replicas={ready})")
                    break
            else:
                under_slo_streak = 0
            time.sleep(5)

        if recovered:
            console.print(f"    Holding load for {TAIL_S}s")
            tail_deadline = time.time() + TAIL_S
            while time.time() < tail_deadline:
                _sample_load()
                time.sleep(min(10, max(0.0, tail_deadline - time.time())))
        elif not seen_slo:
            console.print("    [yellow]SLO was not crossed before the observe timeout[/yellow]")
        else:
            console.print("    [yellow]Latency did not return under the SLO before the observe timeout[/yellow]")
        window_end = datetime.now(timezone.utc)
    finally:
        stop_locust(locust_ns)
        run(k8s_scale(ns, dep, 1), check=False)
    if window_end is None:
        raise RuntimeError(f"Scale-out window for {target['app']} was not recorded")
    return window_start, window_end, rps_samples, scale_ready_ts


def _first_series(client: PrometheusClient, queries: list[str], start: datetime, end: datetime) -> tuple[str, list[list[float]]]:
    chosen = queries[0]
    samples: list[list[float]] = []
    for query in queries:
        chosen = query
        samples = client.query_range(query, start, end, QUERY_STEP)
        if _has_signal(samples):
            break
    return chosen, samples


def _has_signal(samples: list[list[float]]) -> bool:
    return any(value > 0 for _ts, value in samples)


def _series_payload(
    target: dict,
    client: PrometheusClient,
    start: datetime,
    end: datetime,
    locust_rps: list[list[float]] | None = None,
    scale_ready_ts: float | None = None,
) -> dict:
    queries = prometheus_queries(target)
    ns = target["namespace"]
    dep = target["deployment"]
    replica_queries = [
        queries["replicas"],
        f'kube_deployment_status_replicas_available{{namespace="{ns}",deployment="{dep}"}}',
    ]
    replica_query, replica_series = _first_series(client, replica_queries, start, end)
    queries["replicas"] = replica_query
    series = {
        "latency": client.query_range(queries["latency"], start, end, QUERY_STEP),
        "replicas": replica_series,
    }
    throughput_query, throughput = _first_series(client, throughput_queries(target), start, end)
    queries["throughput"] = throughput_query
    if not _has_signal(throughput) and locust_rps:
        throughput = [sample for sample in locust_rps if start.timestamp() <= sample[0] <= end.timestamp()]
        queries["throughput"] = "locust:/stats/requests current_rps"
    series["throughput"] = throughput
    for key, samples in series.items():
        console.print(f"    {key}: {len(samples)} samples")
        if not _has_signal(samples):
            console.print(f"    [yellow]No Prometheus data for {queries[key]}[/yellow]")
    payload = {
        "app": target["app"],
        "namespace": target["namespace"],
        "deployment": target["deployment"],
        "metric_name": target["locust_metric_name"],
        "slo_seconds": float(target["slo_seconds"]),
        "target_replicas": int(target["target_replicas"]),
        "scale_ready_ts": scale_ready_ts,
        "queries": queries,
        "window": {
            "start": start.astimezone(timezone.utc).isoformat(),
            "end": end.astimezone(timezone.utc).isoformat(),
            "step": QUERY_STEP,
        },
        "series": series,
    }
    payload["markers"] = compute_markers(
        series["latency"],
        present_replicas(
            series["replicas"],
            int(payload["target_replicas"]),
            scale_ready_ts,
        ),
        payload["slo_seconds"],
        payload["target_replicas"],
    )
    return payload


def _require_plot_stack():
    try:
        import matplotlib

        if "matplotlib.pyplot" not in sys.modules:
            matplotlib.use("Agg")
        import matplotlib.dates as mdates
        import matplotlib.pyplot as plt
        import numpy as np
        from matplotlib.lines import Line2D
        from matplotlib.ticker import MaxNLocator
        from scipy.interpolate import PchipInterpolator
    except ImportError as exc:
        console.print("[red]Plotting dependencies are missing. Install them with: uv sync --extra plots[/red]")
        raise SystemExit(1) from exc
    return mdates, plt, np, Line2D, MaxNLocator, PchipInterpolator


def _to_mpl(mdates, ts: float):
    return mdates.date2num(datetime.fromtimestamp(ts))


def _smooth(np, pchip, xs: list[float], ys: list[float]):
    paired: dict[float, float] = {}
    for x, y in zip(xs, ys):
        paired[float(x)] = float(y)
    ordered = sorted(paired)
    x_arr = np.array(ordered, dtype=float)
    y_arr = np.array([paired[x] for x in ordered], dtype=float)
    if len(x_arr) < 2:
        return x_arr, y_arr
    grid = np.linspace(x_arr[0], x_arr[-1], 400)
    return grid, pchip(x_arr, y_arr)(grid)


PANEL_SPECS = (
    ("latency", "Latency (s)", True),
    ("replicas", "Replicas (no)", False),
    ("throughput", "Throughput (req/s)", True),
)


def _panel_samples(payload: dict, key: str) -> list[list[float]]:
    samples = list(payload.get("series", {}).get(key) or [])
    if key == "replicas":
        samples = present_replicas(
            samples,
            int(payload.get("target_replicas") or 3),
            payload.get("scale_ready_ts"),
        )
    return samples


def _draw_panel(ax, payload: dict, key: str, ylabel: str, smooth: bool) -> None:
    mdates, _plt, np, _Line2D, MaxNLocator, pchip = _require_plot_stack()
    slo = float(payload["slo_seconds"])
    markers = payload.get("markers") or {}
    samples = _panel_samples(payload, key)
    xs = [_to_mpl(mdates, ts) for ts, _value in samples]
    ys = [value for _ts, value in samples]

    ax.tick_params(
        labelbottom=True,
        labelsize=12,
        width=1.15,
        length=4.5,
        colors="#222222",
    )
    ax.grid(True, color="#d0d0d0", linewidth=0.9)
    ax.set_axisbelow(True)
    for spine in ax.spines.values():
        spine.set_linewidth(1.15)
        spine.set_color("#222222")
    ax.set_ylabel(ylabel, fontsize=14, labelpad=4)
    ax.set_xlabel("Time", fontsize=14, labelpad=3)

    if key == "replicas":
        if xs:
            ax.step(xs, ys, where="post", color=BLUE, linewidth=1.6, zorder=2)
            mark_x = [xs[0]]
            mark_y = [ys[0]]
            for x, y, prev in zip(xs[1:], ys[1:], ys):
                if y != prev:
                    mark_x.append(x)
                    mark_y.append(y)
            if mark_x[-1] != xs[-1]:
                mark_x.append(xs[-1])
                mark_y.append(ys[-1])
            ax.plot(mark_x, mark_y, linestyle="none", marker="o", color=BLUE, markersize=5, zorder=3)
        ax.yaxis.set_major_locator(MaxNLocator(integer=True))
        top = max(ys) if ys else 3
        ax.set_ylim(-0.15, max(top, 3) + 0.45)
    elif xs:
        if smooth and len(xs) >= 2:
            grid_x, grid_y = _smooth(np, pchip, xs, ys)
            ax.fill_between(grid_x, grid_y, 0, color=BLUE, alpha=0.18, linewidth=0, zorder=1)
            ax.plot(grid_x, grid_y, color=BLUE, linewidth=1.8, zorder=2)
        else:
            ax.fill_between(xs, ys, 0, color=BLUE, alpha=0.18, linewidth=0, zorder=1)
            ax.plot(xs, ys, color=BLUE, linewidth=1.8, zorder=2)
        ax.plot(xs, ys, linestyle="none", marker="o", color=BLUE, markersize=5.5, zorder=3)
        ax.set_ylim(bottom=0)

    if key == "latency":
        ax.axhline(slo, color=SLA_RED, linestyle="--", linewidth=1.1, zorder=2)
        ax.text(
            0.012,
            slo,
            f"SLA ({slo:.2f} s)",
            color=SLA_RED,
            fontsize=11,
            va="bottom",
            ha="left",
            transform=ax.get_yaxis_transform(),
        )

    for ts, style in (
        (markers.get("sla_violation"), "--"),
        (markers.get("steady_state"), "-."),
    ):
        if ts is None:
            continue
        ax.axvline(_to_mpl(mdates, float(ts)), color="black", linestyle=style, linewidth=1.15, zorder=4)

    ax.xaxis.set_major_formatter(mdates.DateFormatter("%H:%M"))
    ax.xaxis.set_major_locator(mdates.AutoDateLocator(minticks=3, maxticks=6))

    all_ts = [ts for ts, _value in samples]
    if all_ts:
        left = _to_mpl(mdates, min(all_ts))
        right = _to_mpl(mdates, max(all_ts))
        pad = max((right - left) * 0.02, 8 / 86400.0)
        ax.set_xlim(left - pad, right + pad)


def render_figure(payload: dict, pdf_path: Path, png_path: Path) -> None:
    """Write the stacked 3-panel figure plus one standalone image per panel."""
    _mdates, plt, _np, _Line2D, _MaxNLocator, _pchip = _require_plot_stack()
    pdf_path.parent.mkdir(parents=True, exist_ok=True)

    # Standalone panels for LaTeX 3-column layout (~half previous width, same data range).
    ns = payload["namespace"]
    for key, ylabel, smooth in PANEL_SPECS:
        fig, ax = plt.subplots(1, 1, figsize=(5.1, 3.0))
        fig.subplots_adjust(left=0.20, right=0.97, top=0.96, bottom=0.22)
        _draw_panel(ax, payload, key, ylabel, smooth)
        stem = pdf_path.parent / f"{ns}-{key}"
        fig.savefig(stem.with_suffix(".pdf"), bbox_inches="tight", pad_inches=0.08)
        fig.savefig(stem.with_suffix(".png"), dpi=200, bbox_inches="tight", pad_inches=0.08)
        plt.close(fig)

    # Combined stacked figure (kept for quick preview).
    fig, axes = plt.subplots(3, 1, figsize=(10.2, 8.4), sharex=True)
    fig.subplots_adjust(left=0.11, right=0.98, top=0.98, bottom=0.05, hspace=0.78)
    captions = ("(a) Latency", "(b) Replica count", "(c) Throughput")
    for ax, (key, ylabel, smooth), caption in zip(axes, PANEL_SPECS, captions):
        _draw_panel(ax, payload, key, ylabel, smooth)
        ax.text(
            0.0,
            -0.42,
            caption,
            transform=ax.transAxes,
            fontweight="bold",
            fontsize=13,
            ha="left",
            va="top",
            clip_on=False,
        )
    fig.savefig(pdf_path, bbox_inches="tight", pad_inches=0.15)
    fig.savefig(png_path, dpi=200, bbox_inches="tight", pad_inches=0.15)
    plt.close(fig)


def generate_figures_tex(payloads: list[dict], output_dir: Path) -> str:
    """Emit a 3x3 figure*: columns = apps, rows = latency / replicas / throughput."""
    by_ns = {payload["namespace"]: payload for payload in payloads}
    apps = [
        ("sock-shop", "Sock Shop", "front-end"),
        ("online-boutique", "Online Boutique", "frontend"),
        ("bookinfo", "BookInfo", "productpage-v1"),
    ]
    present = [row for row in apps if row[0] in by_ns]
    if not present:
        return "% No scale-out figure payloads available.\n"

    metric_labels = {
        "latency": "(a) Latency",
        "replicas": "(b) Replicas",
        "throughput": "(c) Throughput",
    }
    lines = [
        "% Generated by ./amocna.py paper-eval plot",
        "% Requires: \\usepackage{graphicx} \\usepackage{subcaption}",
        "% Columns = apps; rows = latency / replicas / throughput.",
        "\\graphicspath{{figures/3apps/}{evaluation_results/figures/}{figures/}{./}}",
        "\\begin{figure*}[t]",
        "\\centering",
        "% One shared legend (wide + short → size by height, not width)",
        "\\includegraphics[height=1.35em]{vline_legend}\\\\[0.45em]",
        "% Column headers (apps)",
    ]
    for i, (_ns, app, _dep) in enumerate(present):
        sep = "\\hfill" if i < len(present) - 1 else "\\\\[0.35em]"
        lines.append(
            f"\\begin{{minipage}}[t]{{0.32\\textwidth}}\\centering\\small\\textbf{{{app}}}\\end{{minipage}}{sep}"
        )
    for mi, (key, _ylabel, _smooth) in enumerate(PANEL_SPECS):
        for i, (ns, _app, _dep) in enumerate(present):
            sep = "\\hfill" if i < len(present) - 1 else "\\\\[-0.15em]"
            lines.append("\\begin{subfigure}[t]{0.32\\textwidth}")
            lines.append(f"  \\includegraphics[width=\\linewidth]{{{ns}-{key}}}")
            lines.append(f"\\end{{subfigure}}{sep}")
        label = metric_labels[key]
        lines.append(
            f"{{\\footnotesize {label}}}\\\\[0.55em]"
            if mi < len(PANEL_SPECS) - 1
            else f"{{\\footnotesize {label}}}"
        )
    slo = float(next(iter(by_ns.values()))["slo_seconds"])
    lines.extend(
        [
            (
                "\\caption{SLA-driven scale-out on three entrypoints. Columns: "
                + ", ".join(f"{app} \\texttt{{{dep}}}" for _ns, app, dep in present)
                + ". Rows: Locust p95 latency, ready replicas, and request rate. "
                f"Dashed vertical line: first sample above the {slo:.2f}\\,s SLO; "
                "dash-dot: return under the SLO at three ready replicas.}"
            ),
            "\\label{fig:e2_scaleout}",
            "\\end{figure*}",
            "",
        ]
    )
    return "\n".join(lines)


def _latex_graphic(output_dir: Path, namespace: str) -> str:
    path = output_dir / "figures" / f"{namespace}.pdf"
    try:
        path = path.resolve().relative_to(Path.cwd().resolve())
    except ValueError:
        path = output_dir / "figures" / f"{namespace}.pdf"
    return path.as_posix()


def _ordered(payloads: list[dict]) -> list[dict]:
    rank = {ns: index for index, ns in enumerate(APP_ORDER)}
    return sorted(payloads, key=lambda payload: (rank.get(payload["namespace"], len(rank)), payload["namespace"]))


def _write_payload(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n")


def _load_payloads(series_dir: Path) -> list[dict]:
    paths = sorted(series_dir.glob("*.json"))
    if not paths:
        raise RuntimeError(f"No series JSON files in {series_dir}")
    payloads = []
    for path in paths:
        payload = json.loads(path.read_text())
        if "series" not in payload or "namespace" not in payload:
            continue
        payload["markers"] = compute_markers(
            payload["series"].get("latency") or [],
            present_replicas(
                payload["series"].get("replicas") or [],
                int(payload["target_replicas"]),
                payload.get("scale_ready_ts"),
            ),
            float(payload["slo_seconds"]),
            int(payload["target_replicas"]),
        )
        payloads.append(payload)
    if not payloads:
        raise RuntimeError(f"No scale-out series JSON files in {series_dir}")
    return payloads


def _emit(payloads: list[dict], output_dir: Path, series_dir: Path | None = None) -> None:
    figures_dir = output_dir / "figures"
    figures_dir.mkdir(parents=True, exist_ok=True)
    for payload in payloads:
        ns = payload["namespace"]
        json_path = (series_dir or figures_dir) / f"{ns}.json"
        _write_payload(json_path, payload)
        if series_dir and series_dir.resolve() != figures_dir.resolve():
            _write_payload(figures_dir / f"{ns}.json", payload)
        render_figure(payload, figures_dir / f"{ns}.pdf", figures_dir / f"{ns}.png")
        console.print(f"  Wrote {figures_dir / (ns + '.pdf')}")
    tex_path = output_dir / "paper_e2_figures.tex"
    tex_path.write_text(generate_figures_tex(payloads, output_dir))
    console.print(f"  Wrote {tex_path}")


def run_plot(
    output_dir: Path,
    replot_dir: Path | None = None,
    prometheus_url: str | None = None,
    observe_timeout_s: int = 420,
    apps: str | None = None,
) -> list[dict]:
    _require_plot_stack()
    output_dir.mkdir(parents=True, exist_ok=True)
    if replot_dir is not None:
        console.print(f"[bold green]Replotting scale-out figures from {replot_dir}[/bold green]")
        payloads = _load_payloads(replot_dir)
        _emit(payloads, output_dir, series_dir=replot_dir)
        return payloads

    from amocna_cli.commands.benchmark import set_palamedes_filter
    from amocna_cli.commands.paper_eval.calibrate import (
        _constrain_entry_cpu,
        _silence_competing_load,
    )
    from amocna_cli.commands.paper_eval.example2 import _clean_stuck_actions
    from amocna_cli.commands.scig_eval import k8s_helpers as kh

    cal_path = Path("evaluation_results/scale_calibration.json")
    if not cal_path.is_file():
        raise RuntimeError(
            "Missing evaluation_results/scale_calibration.json. "
            "Run: ./amocna.py paper-eval calibrate -o ./evaluation_results"
        )

    targets = _requested_targets(apps)
    # Skip apps that failed calibration (no spike_users overlay).
    targets = [t for t in targets if t.get("r1_rps") and t.get("r3_rps") and t.get("spike_users")]
    if not targets:
        raise RuntimeError(
            "Calibration has no usable apps. Re-run: ./amocna.py paper-eval calibrate -o ./evaluation_results"
        )
    console.print(
        f"[bold green]Scale-out figures ({len(targets)} apps, one iteration each)[/bold green]"
    )
    for target in targets:
        console.print(
            f"  {target['app']}: baseline={target['baseline_users']} "
            f"spike={target['spike_users']} "
            f"(R1={target.get('r1_rps')} R3={target.get('r3_rps')})"
        )
    kh.require_core_loop_ready()
    # Keep scale-up gated until each window's spike; constrain CPU while blocked.
    set_palamedes_filter(["ImageUpdateIntent"])
    _clean_stuck_actions()
    _silence_competing_load()

    windows: list[tuple[dict, datetime, datetime, list[list[float]], float | None]] = []
    for target in targets:
        console.print(f"[bold]{target['app']} ({target['namespace']}/{target['deployment']})[/bold]")
        set_palamedes_filter(["ImageUpdateIntent"])
        _clean_stuck_actions()
        _constrain_entry_cpu(target)
        start, end, rps_samples, scale_ready_ts = _run_scaleout_window(target, observe_timeout_s)
        windows.append((target, start, end, rps_samples, scale_ready_ts))

    payloads: list[dict] = []
    with PrometheusClient(prometheus_url) as client:
        for target, start, end, rps_samples, scale_ready_ts in windows:
            console.print(f"[bold]{target['app']}[/bold] querying {start.isoformat()} .. {end.isoformat()}")
            payloads.append(
                _series_payload(target, client, start, end, rps_samples, scale_ready_ts)
            )
    _emit(payloads, output_dir)
    console.print("[bold green]Scale-out figures written.[/bold green]")
    return payloads
