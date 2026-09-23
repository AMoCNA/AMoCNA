"""Shared Kubernetes / Redis helpers for real SCIG evaluation measurements."""

from __future__ import annotations

import json
import subprocess
import time
from typing import Optional


SCIG_NS = "amocna-scig"
REDIS_NS = "redis"


def run_kubectl(args: list[str], check: bool = True, input_text: str | None = None) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["kubectl", *args],
        capture_output=True,
        text=True,
        check=check,
        input=input_text,
    )


def redis_cli(host: str, port: int, *args: str, check: bool = True) -> str:
    """
    Talk to Redis via RESP TCP (preferred) or kubectl exec fallback.
    """
    from .redis_client import RedisRespClient

    try:
        client = RedisRespClient(host, port)
        if args == ("PING",) or (len(args) == 1 and args[0] == "PING"):
            return "PONG" if client.ping() else ""
        if args[:1] == ("KEYS",) and len(args) == 2:
            return "\n".join(client.keys(args[1]))
        if args[:1] == ("GET",) and len(args) == 2:
            val = client.get(args[1])
            return val or ""
        if args[:2] == ("INFO", "memory"):
            # Return INFO-like line for parser compatibility
            mb = client.info_memory_used()
            return f"used_memory:{int(mb * 1024 * 1024)}"
        # Generic execute
        result = client.execute(*args)
        if result is None:
            return ""
        if isinstance(result, bytes):
            return result.decode("utf-8", errors="replace")
        if isinstance(result, list):
            lines = []
            for item in result:
                if isinstance(item, bytes):
                    lines.append(item.decode("utf-8", errors="replace"))
                else:
                    lines.append(str(item))
            return "\n".join(lines)
        return str(result)
    except Exception:
        pass

    cmd = [
        "kubectl", "exec", "-n", REDIS_NS, "deploy/redis", "--",
        "redis-cli", *args,
    ]
    res = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if check and res.returncode != 0:
        raise subprocess.CalledProcessError(res.returncode, cmd, res.stdout, res.stderr)
    return (res.stdout or "").strip()


def wait_for_job_complete(job_name: str, namespace: str = SCIG_NS, timeout_s: int = 3600) -> float:
    """Block until Job succeeds or fails. Returns wall-clock seconds waited."""
    t0 = time.perf_counter()
    deadline = t0 + timeout_s
    while time.perf_counter() < deadline:
        res = run_kubectl(
            ["get", f"job/{job_name}", "-n", namespace, "-o", "json"],
            check=False,
        )
        if res.returncode != 0:
            time.sleep(5)
            continue
        status = json.loads(res.stdout).get("status", {})
        if int(status.get("succeeded", 0) or 0) >= 1:
            return time.perf_counter() - t0
        if int(status.get("failed", 0) or 0) >= 1:
            raise RuntimeError(f"SCIG Job {job_name} failed")
        time.sleep(5)
    raise TimeoutError(f"SCIG Job {job_name} did not complete within {timeout_s}s")


def delete_job(job_name: str, namespace: str = SCIG_NS) -> None:
    run_kubectl(
        ["delete", f"job/{job_name}", "-n", namespace, "--ignore-not-found", "--wait=false"],
        check=False,
    )


def create_scig_scan_job(
    job_name: str,
    namespaces: str,
    discover: bool = True,
    image_list_only: bool = False,
) -> None:
    """
    Create a one-off Job derived from CronJob/scig with overridden SCAN_NAMESPACES.
    image_list_only=True → DISCOVER=false (scan ConfigMap images.txt only).
    """
    res = run_kubectl(
        [
            "create", "job", job_name,
            "--from=cronjob/scig",
            "-n", SCIG_NS,
            "--dry-run=client",
            "-o", "json",
        ]
    )
    job = json.loads(res.stdout)
    job["metadata"]["name"] = job_name
    job["metadata"].pop("ownerReferences", None)
    containers = job["spec"]["template"]["spec"]["containers"]
    env = containers[0].setdefault("env", [])
    env_map = {e["name"]: e for e in env if "name" in e}

    def upsert(name: str, value: str) -> None:
        if name in env_map:
            env_map[name]["value"] = value
        else:
            entry = {"name": name, "value": value}
            env.append(entry)
            env_map[name] = entry

    upsert("SCAN_NAMESPACES", namespaces)
    if image_list_only or not discover:
        upsert("DISCOVER_CLUSTER_IMAGES", "false")
    else:
        upsert("DISCOVER_CLUSTER_IMAGES", "true")

    run_kubectl(["apply", "-f", "-"], input_text=json.dumps(job))


def run_scig_scan(
    namespaces: str,
    timeout_s: int = 3600,
    discover: bool = True,
    image_list_only: bool = False,
) -> tuple[str, float]:
    """Trigger SCIG scan Job, wait until complete, return (job_name, elapsed_s)."""
    job_name = f"scig-eval-{time.time_ns() % 1_000_000_000}"
    create_scig_scan_job(job_name, namespaces=namespaces, discover=discover, image_list_only=image_list_only)
    try:
        elapsed = wait_for_job_complete(job_name, timeout_s=timeout_s)
        return job_name, elapsed
    finally:
        delete_job(job_name)


def redis_scan_keys(host: str, port: int, pattern: str) -> list[str]:
    # KEYS is acceptable for PoC-scale Redis datasets; --scan is awkward via kubectl exec
    out = redis_cli(host, port, "KEYS", pattern, check=False)
    if not out or out in ("(empty array)", "(nil)"):
        return []
    return [line.strip() for line in out.splitlines() if line.strip() and not line.startswith("(")]


def redis_get_json(host: str, port: int, key: str) -> Optional[dict]:
    raw = redis_cli(host, port, "GET", key, check=False)
    if not raw:
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return None


def redis_used_memory_mb(host: str, port: int) -> float:
    info = redis_cli(host, port, "INFO", "memory", check=False)
    for line in info.splitlines():
        if line.startswith("used_memory:"):
            return int(line.split(":")[1]) / (1024 * 1024)
    return 0.0


def collect_sbom_meta(host: str, port: int) -> list[dict]:
    keys = redis_scan_keys(host, port, "sbom:meta:*")
    metas = []
    for key in keys:
        data = redis_get_json(host, port, key)
        if data:
            data["_key"] = key
            metas.append(data)
    return metas


def extract_cve_ids_from_grype(host: str, port: int, repo: str, tag: str) -> set[str]:
    data = redis_get_json(host, port, f"sbom:cve:{repo}:{tag}")
    if not data:
        return set()
    ids = set()
    for match in data.get("matches") or []:
        vuln = match.get("vulnerability") or {}
        cid = vuln.get("id")
        if cid:
            ids.add(cid)
    return ids


def extract_cve_ids_from_trivy(host: str, port: int, repo: str, tag: str) -> set[str]:
    data = redis_get_json(host, port, f"sbom:trivy:{repo}:{tag}")
    if not data:
        return set()
    ids = set()
    for result in data.get("Results") or []:
        for vuln in result.get("Vulnerabilities") or []:
            cid = vuln.get("VulnerabilityID")
            if cid:
                ids.add(cid)
    return ids


def set_deployment_image(ns: str, dep: str, container: str, image: str) -> None:
    run_kubectl(["set", "image", f"deployment/{dep}", f"{container}={image}", "-n", ns])


def get_deployment_image(ns: str, dep: str) -> str:
    res = run_kubectl(
        ["get", f"deployment/{dep}", "-n", ns, "-o", "jsonpath={.spec.template.spec.containers[0].image}"],
        check=False,
    )
    return (res.stdout or "").strip()


def wait_for_image_tag(ns: str, dep: str, expected_tag: str, timeout_s: int = 360) -> float:
    """Wait until deployment image contains expected_tag. Returns elapsed seconds."""
    t0 = time.perf_counter()
    while time.perf_counter() - t0 < timeout_s:
        image = get_deployment_image(ns, dep)
        if expected_tag in image:
            return time.perf_counter() - t0
        time.sleep(5)
    raise TimeoutError(f"{ns}/{dep} did not reach tag {expected_tag} within {timeout_s}s")


def pod_metrics(namespace: str, label_selector: str) -> dict[str, float]:
    """Return aggregated CPU (millicores) and memory (MiB) from kubectl top pods."""
    res = run_kubectl(
        ["top", "pods", "-n", namespace, "-l", label_selector, "--no-headers"],
        check=False,
    )
    cpu_m = 0.0
    mem_mi = 0.0
    for line in (res.stdout or "").splitlines():
        parts = line.split()
        if len(parts) < 3:
            continue
        cpu = parts[1]
        mem = parts[2]
        if cpu.endswith("m"):
            cpu_m += float(cpu[:-1])
        else:
            cpu_m += float(cpu) * 1000.0
        if mem.endswith("Mi"):
            mem_mi += float(mem[:-2])
        elif mem.endswith("Gi"):
            mem_mi += float(mem[:-2]) * 1024.0
    return {"cpu_m": cpu_m, "mem_mi": mem_mi}
