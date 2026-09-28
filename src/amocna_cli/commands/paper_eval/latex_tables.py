"""LaTeX tables for the CNEEOnt two-scenario paper evaluation."""

from __future__ import annotations

from amocna_cli.commands.scig_eval.metrics import compute_stats


def _success_pct(rate: float | None) -> str:
    if rate is None:
        return "--"
    return f"{rate * 100:.0f}\\%"


def generate_e1_tables(results: dict) -> dict[str, str]:
    iterations = results.get("iterations", [])
    keys: list[str] = []
    for it in iterations:
        for k in it.get("per_service", {}):
            if k not in keys:
                keys.append(k)

    lines = [
        "\\begin{table}[htbp]",
        "\\centering",
        "\\caption{Example 1: ontology-driven image remediation across three applications. "
        "Rollout is wall-clock from vulnerable reset to the catalog fix tag. "
        "Values are $\\overline{x} \\pm \\sigma$ (ms)"
        + (" [offline fixture --- replace after cluster run]" if results.get("offline") else "")
        + ".}",
        "\\label{tab:paper_e1_remediation}",
        "\\footnotesize",
        "\\begin{tabular}{lllrr}",
        "\\toprule",
        "\\textbf{Application} & \\textbf{Workload} & \\textbf{Policy} & \\textbf{Success} & \\textbf{Time-to-patch} \\\\",
        "\\midrule",
    ]
    for key in keys:
        sample = next(
            (it["per_service"][key] for it in iterations if key in it.get("per_service", {})),
            {},
        )
        app = sample.get("app", "")
        dep = sample.get("deployment", key)
        policy = sample.get("policy", "")
        rollout = compute_stats(
            [
                it["per_service"][key].get("rollout_ms", 0.0)
                for it in iterations
                if key in it.get("per_service", {})
            ]
        )
        success = results.get("success_rates", {}).get(key)
        lines.append(
            f"{app} & {dep} & {policy} & {_success_pct(success)} & {rollout.latex_str()} \\\\"
        )
    lines.extend(["\\bottomrule", "\\end{tabular}", "\\end{table}", ""])

    overhead = results.get("sparql_overhead", {})
    topo = compute_stats([float(it.get("topology_bindings", 0)) for it in iterations])
    cq = compute_stats([it.get("sparql_cq1_ms", 0.0) for it in iterations])
    overhead_tex = "\n".join(
        [
            "\\begin{table}[htbp]",
            "\\centering",
            "\\caption{Example 1: SPARQL competency-question latency and topology bindings "
            "(workloads using catalog-vulnerable image versions).}",
            "\\label{tab:paper_e1_sparql}",
            "\\begin{tabular}{lrr}",
            "\\toprule",
            "\\textbf{Metric} & \\textbf{p50} & \\textbf{p95} \\\\",
            "\\midrule",
            f"CQ1 query latency (ms) & {overhead.get('cq1_p50_ms', 0):.1f} & {overhead.get('cq1_p95_ms', 0):.1f} \\\\",
            f"Topology bindings (count) & {topo.mean:.1f} & -- \\\\",
            f"Mean CQ1 latency (ms) & {cq.mean:.1f} & {cq.std_dev:.1f} \\\\",
            "\\bottomrule",
            "\\end{tabular}",
            "\\end{table}",
            "",
        ]
    )
    return {
        "paper_e1_remediation.tex": "\n".join(lines),
        "paper_e1_sparql.tex": overhead_tex,
    }


def generate_e2_tables(results: dict) -> dict[str, str]:
    lines = [
        "\\begin{table}[htbp]",
        "\\centering",
        "\\caption{Example 2: SLA-driven horizontal scale-out on three entrypoints. "
        "Detection is Locust p95 crossing the SLO; remediation is time to "
        "three ready replicas. Values are $\\overline{x} \\pm \\sigma$ (ms) except replica-seconds"
        + (" [offline fixture --- replace after cluster run]" if results.get("offline") else "")
        + ".}",
        "\\label{tab:paper_e2_scaling}",
        "\\footnotesize",
        "\\begin{tabular}{lrrrrr}",
        "\\toprule",
        "\\textbf{Application} & \\textbf{Success} & \\textbf{Detect} & \\textbf{Scale-out} "
        "& \\textbf{SLO-viol.\\ duration} & \\textbf{Replica-s} \\\\",
        "\\midrule",
    ]
    for app, data in results.get("apps", {}).items():
        runs = data.get("runs", [])
        detect = compute_stats([r["t_slo_ms"] for r in runs if r.get("t_slo_ms") is not None])
        ready = compute_stats([r["t_ready_ms"] for r in runs if r.get("t_ready_ms") is not None])
        viol = compute_stats([r.get("slo_violation_ms", 0.0) for r in runs])
        cost = compute_stats([r.get("replica_seconds", 0.0) for r in runs])
        lines.append(
            f"{app} & {_success_pct(data.get('success_rate'))} & {detect.latex_str()} & "
            f"{ready.latex_str()} & {viol.latex_str()} & {cost.latex_str(precision=1)} \\\\"
        )
    lines.extend(["\\bottomrule", "\\end{tabular}", "\\end{table}", ""])

    overhead = results.get("sparql_overhead", {})
    overhead_tex = "\n".join(
        [
            "\\begin{table}[htbp]",
            "\\centering",
            "\\caption{Example 2: SPARQL CQ2 latency (SLA state $\\rightarrow$ scale target) "
            "during closed-loop runs.}",
            "\\label{tab:paper_e2_sparql}",
            "\\begin{tabular}{lrr}",
            "\\toprule",
            "\\textbf{Metric} & \\textbf{p50 (ms)} & \\textbf{p95 (ms)} \\\\",
            "\\midrule",
            f"CQ2 query latency & {overhead.get('cq2_p50_ms', 0):.1f} & {overhead.get('cq2_p95_ms', 0):.1f} \\\\",
            "\\bottomrule",
            "\\end{tabular}",
            "\\end{table}",
            "",
        ]
    )
    return {
        "paper_e2_scaling.tex": "\n".join(lines),
        "paper_e2_sparql.tex": overhead_tex,
    }
