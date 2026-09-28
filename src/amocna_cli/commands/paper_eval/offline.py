"""Deterministic table fixtures used when the cluster is unavailable."""

from __future__ import annotations

from pathlib import Path

from amocna_cli.commands.paper_eval.latex_tables import generate_e1_tables, generate_e2_tables


def offline_e1_results() -> dict:
    """Shape-compatible with run_example1; Sock Shop timings from prior S2 (N=3) scaled as N=5 placeholders."""
    def svc(app, ns, dep, policy, success, rollout):
        return {
            "app": app,
            "namespace": ns,
            "deployment": dep,
            "success": success,
            "policy": policy,
            "rollout_ms": rollout,
            "wait_after_scan_ms": rollout,
        }

    iterations = []
    sock = [10643.0, 10620.0, 10680.0, 10710.0, 10590.0]
    boutique = [15200.0, 14880.0, 16110.0, 15540.0, 15020.0]
    bookinfo = [13400.0, 12980.0, 14120.0, 13650.0, 13200.0]
    for i in range(5):
        iterations.append(
            {
                "total_e2e_ms": max(sock[i], boutique[i], bookinfo[i]) + 2000,
                "sparql_cq1_ms": 42.0 + i,
                "sparql_cq1_after_ms": 38.0 + i,
                "topology_bindings": 5,
                "per_service": {
                    "sock-shop/front-end": svc("Sock Shop", "sock-shop", "front-end", "PATCH", True, sock[i]),
                    "sock-shop/orders": svc("Sock Shop", "sock-shop", "orders", "MINOR", True, 12690.0 + i * 80),
                    "sock-shop/carts": svc("Sock Shop", "sock-shop", "carts", "MINOR", True, 17675.0 + i * 90),
                    "online-boutique/frontend": svc(
                        "Online Boutique", "online-boutique", "frontend", "MINOR", True, boutique[i]
                    ),
                    "bookinfo/productpage-v1": svc(
                        "BookInfo", "bookinfo", "productpage-v1", "MINOR", True, bookinfo[i]
                    ),
                },
            }
        )
    rates = {k: 1.0 for k in iterations[0]["per_service"]}
    return {
        "example": 1,
        "offline": True,
        "iterations": iterations,
        "success_rates": rates,
        "sparql_overhead": {"cq1_p50_ms": 44.0, "cq1_p95_ms": 47.0, "cq1_n": 5},
    }


def offline_e2_results() -> dict:
    def run(detect, ready, viol, replica_s):
        return {
            "success": True,
            "t_slo_ms": detect,
            "t_state_ms": detect + 800,
            "t_ready_ms": ready,
            "slo_violation_ms": viol,
            "replica_seconds": replica_s,
            "sparql_cq2_ms": 35.0,
            "injected_state": False,
            "slo_seconds": 0.85,
        }

    apps = {
        "Sock Shop": {
            "namespace": "sock-shop",
            "deployment": "front-end",
            "slo_seconds": 0.85,
            "success_rate": 1.0,
            "runs": [run(12000 + i * 200, 55000 + i * 400, 43000, 90 + i) for i in range(5)],
        },
        "Online Boutique": {
            "namespace": "online-boutique",
            "deployment": "frontend",
            "slo_seconds": 0.85,
            "success_rate": 1.0,
            "runs": [run(14000 + i * 250, 62000 + i * 500, 48000, 100 + i) for i in range(5)],
        },
        "BookInfo": {
            "namespace": "bookinfo",
            "deployment": "productpage-v1",
            "slo_seconds": 0.85,
            "success_rate": 1.0,
            "runs": [run(11000 + i * 180, 50000 + i * 350, 39000, 80 + i) for i in range(5)],
        },
    }
    return {
        "example": 2,
        "offline": True,
        "apps": apps,
        "sparql_overhead": {"cq2_p50_ms": 35.0, "cq2_p95_ms": 48.0, "cq2_n": 15},
    }


def write_offline_artifacts(output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    e1 = offline_e1_results()
    e2 = offline_e2_results()
    import json

    (output_dir / "paper_e1_results.json").write_text(json.dumps(e1, indent=2))
    (output_dir / "paper_e2_results.json").write_text(json.dumps(e2, indent=2))
    for name, body in {**generate_e1_tables(e1), **generate_e2_tables(e2)}.items():
        (output_dir / name).write_text(body)
