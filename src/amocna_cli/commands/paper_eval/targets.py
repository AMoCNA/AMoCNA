"""Shared three-application evaluation targets for CNEEOnt paper examples."""

from __future__ import annotations

PATCH_TARGETS: list[dict] = [
    {
        "app": "Sock Shop",
        "namespace": "sock-shop",
        "deployment": "front-end",
        "container": "front-end",
        "vulnerable_image": "docker.io/weaveworksdemos/front-end:0.3.0",
        "expected_tag": "0.3.12",
        "policy": "PATCH",
        "severity": "HIGH",
        "catalog_repo": "weaveworksdemos/front-end",
        "catalog_version": "0.3.0",
    },
    {
        "app": "Sock Shop",
        "namespace": "sock-shop",
        "deployment": "orders",
        "container": "orders",
        "vulnerable_image": "docker.io/weaveworksdemos/orders:0.4.0",
        "expected_tag": "0.4.7",
        "policy": "MINOR",
        "severity": "CRITICAL",
        "catalog_repo": "weaveworksdemos/orders",
        "catalog_version": "0.4.0",
    },
    {
        "app": "Sock Shop",
        "namespace": "sock-shop",
        "deployment": "carts",
        "container": "carts",
        "vulnerable_image": "docker.io/weaveworksdemos/carts:0.3.5",
        "expected_tag": "0.4.8",
        "policy": "MINOR",
        "severity": "HIGH",
        "catalog_repo": "weaveworksdemos/carts",
        "catalog_version": "0.3.5",
    },
    {
        "app": "Online Boutique",
        "namespace": "online-boutique",
        "deployment": "frontend",
        "container": "server",
        "vulnerable_image": "gcr.io/google-samples/microservices-demo/frontend:v0.8.0",
        "expected_tag": "v0.10.2",
        "policy": "MINOR",
        "severity": "HIGH",
        "catalog_repo": "google-samples/microservices-demo/frontend",
        "catalog_version": "v0.8.0",
    },
    {
        "app": "BookInfo",
        "namespace": "bookinfo",
        "deployment": "productpage-v1",
        "container": "productpage",
        "vulnerable_image": "docker.io/istio/examples-bookinfo-productpage-v1:1.16.2",
        "expected_tag": "1.20.2",
        "policy": "MINOR",
        "severity": "HIGH",
        "catalog_repo": "istio/examples-bookinfo-productpage-v1",
        "catalog_version": "1.16.2",
    },
]

SCALE_TARGETS: list[dict] = [
    {
        "app": "Sock Shop",
        "namespace": "sock-shop",
        "deployment": "front-end",
        "service": "front-end",
        "locust_namespace": "sock-shop",
        "locust_metric_name": "front-end",
        "locust_host": "http://front-end.sock-shop.svc.cluster.local",
        "slo_seconds": 0.85,
        "baseline_users": 150,
        "spike_users": 200,
        "spawn_rate": 20,
        "target_replicas": 3,
    },
    {
        "app": "Online Boutique",
        "namespace": "online-boutique",
        "deployment": "frontend",
        "service": "frontend",
        "locust_namespace": "online-boutique",
        "locust_metric_name": "frontend",
        "locust_host": "http://frontend.online-boutique.svc.cluster.local",
        "slo_seconds": 0.85,
        "baseline_users": 50,
        "spike_users": 110,
        "spawn_rate": 20,
        "target_replicas": 3,
    },
    {
        "app": "BookInfo",
        "namespace": "bookinfo",
        "deployment": "productpage-v1",
        "service": "productpage",
        "locust_namespace": "bookinfo",
        "locust_metric_name": "productpage-v1",
        "locust_host": "http://productpage.bookinfo.svc.cluster.local:9080",
        "slo_seconds": 0.85,
        "baseline_users": 80,
        "spike_users": 200,
        "spawn_rate": 20,
        "target_replicas": 3,
    },
]


def load_scale_targets(calibration_path=None) -> list[dict]:
    """Return scale targets, overlaying baseline/spike from a calibrate JSON if present."""
    import json
    import os
    from pathlib import Path

    targets = [dict(item) for item in SCALE_TARGETS]
    path = calibration_path
    if path is None:
        env = os.environ.get("AMOCNA_SCALE_CALIBRATION")
        path = Path(env) if env else Path("evaluation_results/scale_calibration.json")
    if not Path(path).is_file():
        return targets
    payload = json.loads(Path(path).read_text())
    by_ns = {row["namespace"]: row for row in payload.get("apps", [])}
    for target in targets:
        row = by_ns.get(target["namespace"])
        if not row or not row.get("spike_users"):
            # Skip failed calibrate rows so plot does not mix default spikes with bad R1/R3.
            continue
        target["baseline_users"] = int(row["baseline_users"])
        target["spike_users"] = int(row["spike_users"])
        if row.get("spawn_rate"):
            target["spawn_rate"] = int(row["spawn_rate"])
        target["r1_rps"] = row.get("r1_rps")
        target["r3_rps"] = row.get("r3_rps")
    return targets

