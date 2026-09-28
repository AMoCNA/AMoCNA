"""LaTeX table generators for SCIG academic evaluation."""

from .metrics import compute_stats

def generate_s1_per_image_table(data: dict) -> str:
    lines = [
        "\\begin{table*}[htbp]",
        "\\centering",
        "\\caption{SCIG SBOM generation and CVE detection results across heterogeneous microservice benchmark applications. Latencies are reported as $\\overline{x} \\pm \\sigma$ over iterations.}",
        "\\label{tab:scig_s1_per_image}",
        "\\scriptsize",
        "\\begin{tabular}{llrrrrrrrr}",
        "\\toprule",
        "\\textbf{Application} & \\textbf{Service} & \\textbf{Lang} & \\textbf{Pkgs} & \\multicolumn{2}{c}{\\textbf{Grype CVEs}} & \\multicolumn{2}{c}{\\textbf{Trivy CVEs}} & \\textbf{Syft (ms)} & \\textbf{Grype (ms)} \\\\",
        "\\cmidrule(lr){5-6} \\cmidrule(lr){7-8}",
        " & & & & Total & Crit & Total & Crit & & \\\\",
        "\\midrule"
    ]

    for img, d in data.items():
        app = d.get("app", "Benchmark")
        service = img.split("/")[-1]
        lang = d.get("language", "Unknown")
        pkgs = d.get("packages", 0)
        g_total = d.get("grype_cves", {}).get("total", 0)
        g_crit = d.get("grype_cves", {}).get("critical", 0)
        t_total = d.get("trivy_cves", {}).get("total", 0)
        t_crit = d.get("trivy_cves", {}).get("critical", 0)
        syft_stats = compute_stats(d.get("syft_latencies_ms", []))
        grype_stats = compute_stats(d.get("grype_latencies_ms", []))

        lines.append(f"{app} & {service} & {lang} & {pkgs} & {g_total} & {g_crit} & {t_total} & {t_crit} & {syft_stats.latex_str()} & {grype_stats.latex_str()} \\\\")

    lines.extend([
        "\\bottomrule",
        "\\end{tabular}",
        "\\end{table*}"
    ])
    return "\n".join(lines)

def generate_s1_app_summary_table(data: dict) -> str:
    apps = {}
    for img, d in data.items():
        app = d.get("app", "Benchmark")
        if app not in apps:
            apps[app] = {"images": 0, "packages": 0, "grype_cves": 0, "trivy_cves": 0, "syft_ms": [], "grype_ms": []}
        apps[app]["images"] += 1
        apps[app]["packages"] += d.get("packages", 0)
        apps[app]["grype_cves"] += d.get("grype_cves", {}).get("total", 0)
        apps[app]["trivy_cves"] += d.get("trivy_cves", {}).get("total", 0)
        apps[app]["syft_ms"].extend(d.get("syft_latencies_ms", []))
        apps[app]["grype_ms"].extend(d.get("grype_latencies_ms", []))

    lines = [
        "\\begin{table}[htbp]",
        "\\centering",
        "\\caption{SCIG per-application summary across benchmark suites.}",
        "\\label{tab:scig_s1_app_summary}",
        "\\begin{tabular}{lrrrrr}",
        "\\toprule",
        "\\textbf{Application} & \\textbf{Images} & \\textbf{Total Pkgs} & \\textbf{Grype CVEs} & \\textbf{Syft (ms)} & \\textbf{Grype (ms)} \\\\",
        "\\midrule"
    ]
    for app_name, info in apps.items():
        syft_stats = compute_stats(info["syft_ms"])
        grype_stats = compute_stats(info["grype_ms"])
        lines.append(f"{app_name} & {info['images']} & {info['packages']} & {info['grype_cves']} & {syft_stats.latex_str()} & {grype_stats.latex_str()} \\\\")

    lines.extend(["\\bottomrule", "\\end{tabular}", "\\end{table}"])
    return "\n".join(lines)

def generate_s1_severity_distribution_table(data: dict) -> str:
    apps = {}
    for img, d in data.items():
        app = d.get("app", "Benchmark")
        if app not in apps:
            apps[app] = {"critical": 0, "high": 0, "medium": 0, "low": 0, "total": 0}
        cves = d.get("grype_cves", {})
        apps[app]["critical"] += cves.get("critical", 0)
        apps[app]["high"] += cves.get("high", 0)
        apps[app]["medium"] += cves.get("medium", 0)
        apps[app]["low"] += cves.get("low", 0)
        apps[app]["total"] += cves.get("total", 0)

    lines = [
        "\\begin{table}[htbp]",
        "\\centering",
        "\\caption{CVE severity distribution per target application (Grype scan).}",
        "\\label{tab:scig_s1_severity}",
        "\\begin{tabular}{lrrrrr}",
        "\\toprule",
        "\\textbf{Application} & \\textbf{Critical} & \\textbf{High} & \\textbf{Medium} & \\textbf{Low} & \\textbf{Total} \\\\",
        "\\midrule"
    ]
    for app_name, info in apps.items():
        lines.append(f"{app_name} & {info['critical']} & {info['high']} & {info['medium']} & {info['low']} & {info['total']} \\\\")

    lines.extend(["\\bottomrule", "\\end{tabular}", "\\end{table}"])
    return "\n".join(lines)

def generate_s1_scanner_comparison_table(comp: dict) -> str:
    lines = [
        "\\begin{table}[htbp]",
        "\\centering",
        "\\caption{Cross-scanner CVE detection comparison: Grype vs Trivy across benchmark container images.}",
        "\\label{tab:scig_scanner_comparison}",
        "\\begin{tabular}{lr}",
        "\\toprule",
        "\\textbf{Metric} & \\textbf{Value} \\\\",
        "\\midrule",
        f"Grype unique CVE IDs & {comp.get('grype_total_unique', 0)} \\\\",
        f"Trivy unique CVE IDs & {comp.get('trivy_total_unique', 0)} \\\\",
        f"Intersection ($|G \\cap T|$) & {comp.get('intersection', 0)} \\\\",
        f"Grype-only ($|G \\setminus T|$) & {comp.get('grype_only', 0)} \\\\",
        f"Trivy-only ($|T \\setminus G|$) & {comp.get('trivy_only', 0)} \\\\",
        f"Jaccard similarity $J(G, T)$ & {comp.get('jaccard_similarity', 0.0):.2f} \\\\",
        "\\bottomrule",
        "\\end{tabular}",
        "\\end{table}"
    ]
    return "\n".join(lines)

def generate_s2_remediation_latency_table(results: dict) -> str:
    lines = [
        "\\begin{table}[htbp]",
        "\\centering",
        "\\caption{SCIG end-to-end vulnerability remediation latency breakdown across microservices. Values are $\\overline{x} \\pm \\sigma$ (ms).}",
        "\\label{tab:scig_s2_remediation}",
        "\\begin{tabular}{lllrrrrrr}",
        "\\toprule",
        "\\textbf{Service} & \\textbf{Policy} & \\textbf{Sev.} & \\textbf{Scan} & \\textbf{Sync} & \\textbf{Plan} & \\textbf{Exec} & \\textbf{Rollout} & \\textbf{Total} \\\\",
        "\\midrule"
    ]

    iterations = results.get("iterations", [])
    scan_stats = compute_stats([i["scig_scan_ms"] for i in iterations])
    sync_stats = compute_stats([i.get("redis_sync_ms", 0.0) for i in iterations])
    plan_stats = compute_stats([i.get("planning_ms", 0.0) for i in iterations])
    total_stats = compute_stats([i["total_e2e_ms"] for i in iterations])

    services = []
    for it in iterations:
        for svc in it.get("per_service", {}):
            if svc not in services:
                services.append(svc)
    if not services:
        services = ["front-end", "orders", "carts"]

    for svc in services:
        sample = next(
            (i["per_service"][svc] for i in iterations if svc in i.get("per_service", {})),
            {},
        )
        policy = sample.get("policy", "PATCH")
        sev = sample.get("severity", "HIGH")
        exec_stats = compute_stats([
            i["per_service"][svc].get("execution_ms", 0.0)
            for i in iterations if svc in i.get("per_service", {})
        ])
        rollout_stats = compute_stats([
            i["per_service"][svc].get("rollout_ms", 0.0)
            for i in iterations if svc in i.get("per_service", {})
        ])
        success = results.get("success_rates", {}).get(svc)
        success_note = f" ({success*100:.0f}\\% OK)" if success is not None else ""
        lines.append(
            f"{svc}{success_note} & {policy} & {sev} & {scan_stats.latex_str()} & "
            f"{sync_stats.latex_str()} & {plan_stats.latex_str()} & {exec_stats.latex_str()} & "
            f"{rollout_stats.latex_str()} & {total_stats.latex_str()} \\\\"
        )

    lines.extend([
        "\\bottomrule",
        "\\end{tabular}",
        "\\end{table}"
    ])
    return "\n".join(lines)

def generate_s3_scalability_table(data: dict) -> str:
    lines = [
        "\\begin{table}[htbp]",
        "\\centering",
        "\\caption{SCIG scanning scalability across heterogeneous microservice applications. Values are $\\overline{x} \\pm \\sigma$.}",
        "\\label{tab:scig_s3_scalability}",
        "\\begin{tabular}{lrrrrr}",
        "\\toprule",
        "\\textbf{Scope} & \\textbf{Images} & \\textbf{Total (s)} & \\textbf{Per-Image (s)} & \\textbf{Redis (MB)} & \\textbf{CVEs} \\\\",
        "\\midrule"
    ]

    for round_name, d in data.items():
        imgs = d.get("image_count", 0)
        tot_stats = compute_stats(d.get("total_times_s", []))
        per_img_stats = compute_stats(d.get("per_image_times_s", []))
        mem_stats = compute_stats(d.get("redis_mem_mb", []))
        cves = d.get("cve_count", 0)
        lines.append(f"{round_name} & {imgs} & {tot_stats.latex_str()} & {per_img_stats.latex_str()} & {mem_stats.latex_str()} & {cves} \\\\")

    lines.extend([
        "\\bottomrule",
        "\\end{tabular}",
        "\\end{table}"
    ])
    return "\n".join(lines)

def generate_s4_policy_latency_table(data: dict) -> str:
    lines = [
        "\\begin{table}[htbp]",
        "\\centering",
        "\\caption{In-process catalog ingest / merge / SPARQL VALUES cost vs.\\ record count "
        "(Python mirror of \\texttt{VulnerabilityCatalog}; $N{=}10$). "
        "Values are $\\overline{x} \\pm \\sigma$ (ms).}",
        "\\label{tab:scig_s4_policy}",
        "\\begin{tabular}{rrrrr}",
        "\\toprule",
        "\\textbf{Records} & \\textbf{Parse (ms)} & \\textbf{Merge (ms)} & \\textbf{SPARQL (ms)} & \\textbf{Throughput (rec/s)} \\\\",
        "\\midrule"
    ]

    for rec_count, d in data.items():
        if rec_count.startswith("_"):
            continue
        sync_s = compute_stats(d.get("sync_ms", []))
        merge_s = compute_stats(d.get("merge_ms", []))
        sparql_s = compute_stats(d.get("sparql_ms", []))
        tp_s = compute_stats(d.get("throughput_evts", []))
        lines.append(f"{rec_count} & {sync_s.latex_str()} & {merge_s.latex_str()} & {sparql_s.latex_str()} & {tp_s.latex_str()} \\\\")

    lines.extend([
        "\\bottomrule",
        "\\end{tabular}",
        "\\end{table}"
    ])
    return "\n".join(lines)
