"""Timed SPARQL SELECT/UPDATE helpers for paper evaluation (via in-cluster curl)."""

from __future__ import annotations

import json
import subprocess
import time
from pathlib import Path
from typing import Any


def _sparql_pod(name: str, curl_cmd: list[str], body: str) -> subprocess.CompletedProcess:
    subprocess.run(
        ["kubectl", "delete", "pod", name, "--ignore-not-found", "--grace-period=0", "--force"],
        capture_output=True,
        text=True,
    )
    cmd = [
        "kubectl",
        "run",
        name,
        "--image=curlimages/curl:8.12.1",
        "--restart=Never",
        "--rm",
        "-i",
        "--",
        *curl_cmd,
    ]
    return subprocess.run(cmd, input=body, text=True, capture_output=True, check=False)


def run_sparql_select(query: str) -> tuple[dict[str, Any], float]:
    """Execute SPARQL SELECT; return (parsed JSON, elapsed_ms). Prefers local GraphDB."""
    import urllib.error
    import urllib.parse
    import urllib.request

    local = "http://127.0.0.1:7200/repositories/amocna"
    t0 = time.perf_counter()
    try:
        data = urllib.parse.urlencode({"query": query}).encode()
        req = urllib.request.Request(
            local,
            data=data,
            headers={
                "Accept": "application/sparql-results+json",
                "Content-Type": "application/x-www-form-urlencoded",
            },
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            payload = json.loads(resp.read().decode())
        return payload, (time.perf_counter() - t0) * 1000.0
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError):
        pass

    curl_cmd = [
        "sh",
        "-c",
        "curl -s -X POST http://graphdb.graphdb.svc.cluster.local:7200/repositories/amocna "
        "-H 'Accept: application/sparql-results+json' "
        "-H 'Content-Type: application/sparql-query' --data-binary @-",
    ]
    t0 = time.perf_counter()
    res = _sparql_pod("amocna-sparql-select", curl_cmd, query)
    elapsed_ms = (time.perf_counter() - t0) * 1000.0
    payload: dict[str, Any] = {}
    if res.stdout:
        try:
            payload = json.loads(res.stdout)
        except json.JSONDecodeError:
            payload = {"raw": res.stdout, "stderr": res.stderr}
    elif res.stderr:
        payload = {"error": res.stderr}
    return payload, elapsed_ms


def run_sparql_update(query: str) -> float:
    import urllib.error
    import urllib.parse
    import urllib.request

    local = "http://127.0.0.1:7200/repositories/amocna/statements"
    t0 = time.perf_counter()
    try:
        data = urllib.parse.urlencode({"update": query}).encode()
        req = urllib.request.Request(
            local,
            data=data,
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        with urllib.request.urlopen(req, timeout=15) as resp:
            resp.read()
        return (time.perf_counter() - t0) * 1000.0
    except (urllib.error.URLError, TimeoutError, OSError):
        pass

    curl_cmd = [
        "sh",
        "-c",
        "curl -s -X POST http://graphdb.graphdb.svc.cluster.local:7200/repositories/amocna/statements "
        "-H 'Content-Type: application/sparql-update' --data-binary @-",
    ]
    t0 = time.perf_counter()
    _sparql_pod("amocna-sparql-update", curl_cmd, query)
    return (time.perf_counter() - t0) * 1000.0


def load_cli_sparql(project_root: Path, filename: str) -> str:
    path = project_root / "cli" / "resources" / "sparql" / filename
    return path.read_text()


def binding_count(result: dict[str, Any]) -> int:
    bindings = result.get("results", {}).get("bindings", [])
    return len(bindings) if isinstance(bindings, list) else 0
