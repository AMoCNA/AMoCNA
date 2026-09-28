from __future__ import annotations

import textwrap

# ─── Script & Manifest Templates ──────────────────────────────────────

GRAPHDB_WIPE_JOB_TEMPLATE = """\
apiVersion: batch/v1
kind: Job
metadata:
  name: {job_name}
  namespace: default
spec:
  ttlSecondsAfterFinished: 300
  backoffLimit: 0
  template:
    spec:
      restartPolicy: Never
      nodeSelector:
        kubernetes.io/hostname: {node}
      tolerations:
        - key: amocna
          operator: Equal
          value: "true"
          effect: NoSchedule
      containers:
        - name: wipe
          image: busybox:1.36
          command:
            - sh
            - -c
            - |
              set -e
              echo "Wiping GraphDB data under /mnt/graphdb ..."
              rm -rf /mnt/graphdb/*
              rm -rf /mnt/graphdb/.[!.]* /mnt/graphdb/..?* 2>/dev/null || true
              if [ -n "$(ls -A /mnt/graphdb 2>/dev/null)" ]; then
                echo "ERROR: GraphDB hostPath wipe incomplete — remaining entries:"
                ls -la /mnt/graphdb
                exit 1
              fi
              echo "GraphDB hostPath wipe verified empty."
          volumeMounts:
            - name: graphdb-data
              mountPath: /mnt/graphdb
      volumes:
        - name: graphdb-data
          hostPath:
            path: {host_path}
            type: DirectoryOrCreate
"""

LOCUST_SWARM_PYTHON_TEMPLATE = """\
import urllib.request, urllib.parse
data = urllib.parse.urlencode({{
    'user_count': {users}, 
    'spawn_rate': {rate}, 
    'host': 'http://front-end.sock-shop.svc.cluster.local'
}}).encode()
req = urllib.request.Request('http://localhost:8089/swarm', data=data)
try:
    urllib.request.urlopen(req)
    print("Success")
except Exception as e:
    print("Error:", e)
"""

LOCUST_STOP_PYTHON_TEMPLATE = """\
import urllib.request
try:
    urllib.request.urlopen('http://localhost:8089/stop')
    print("Success")
except Exception as e:
    print("Error:", e)
"""

LOCUST_STATS_PYTHON_TEMPLATE = """\
import urllib.request, json
try:
    res = urllib.request.urlopen('http://localhost:8089/stats/requests')
    data = json.loads(res.read().decode())
    print(f"    Active Users: {data.get('user_count', 0)}, RPS: {data.get('total_rps', 0):.2f}, Avg Response Time: {data.get('stats_average_response_time', 0):.2f}ms")
except Exception as e:
    print("    Locust is currently idle or stopped.")
"""

ORDERS_CPU_RESET_PATCH = '{"spec": {"template": {"spec": {"containers": [{"name": "orders", "resources": {"requests": {"cpu": "100m"}, "limits": {"cpu": "500m"}}}]}}}}'


# ─── Docker Command Builders ──────────────────────────────────────────

def docker_build(image_name: str, dockerfile: str, context: str) -> list[str]:
    """Build a Docker command list for building an image."""
    return ["docker", "build", "-t", image_name, "-f", dockerfile, context]

def docker_push(image_name: str) -> list[str]:
    """Build a Docker command list for pushing an image."""
    return ["docker", "push", image_name]

def docker_login(registry: str, username: str) -> list[str]:
    """Build a Docker command list for logging into a registry."""
    return ["docker", "login", registry, "--username", username, "--password-stdin"]


# ─── Kubernetes Command Builders ──────────────────────────────────────

def k8s_apply_manifest(path: str, dry_run: bool = False) -> list[str]:
    """Build a kubectl apply command for a manifest file."""
    cmd = ["kubectl", "apply", "-f", path]
    if dry_run:
        cmd.append("--dry-run=server")
    return cmd

def k8s_apply_stdin(dry_run: bool = False) -> list[str]:
    """Build a kubectl apply command reading from stdin."""
    cmd = ["kubectl", "apply", "-f", "-"]
    if dry_run:
        cmd.append("--dry-run=server")
    return cmd

def k8s_delete_manifest(path: str, dry_run: bool = False) -> list[str]:
    """Build a kubectl delete command for a manifest file."""
    cmd = ["kubectl", "delete", "-f", path, "--ignore-not-found"]
    if dry_run:
        cmd.append("--dry-run=server")
    return cmd

def k8s_apply_kustomization(path: str, dry_run: bool = False) -> list[str]:
    """Build a kubectl apply command for a kustomization directory."""
    cmd = ["kubectl", "apply", "-k", path]
    if dry_run:
        cmd.append("--dry-run=server")
    return cmd

def k8s_delete_kustomization(path: str, dry_run: bool = False) -> list[str]:
    """Build a kubectl delete command for a kustomization directory."""
    cmd = ["kubectl", "delete", "-k", path, "--ignore-not-found"]
    if dry_run:
        cmd.append("--dry-run=server")
    return cmd

def k8s_delete_resource(resource_type: str, name: str, namespace: str | None = None, dry_run: bool = False, wait: bool | None = None, timeout: str | None = None) -> list[str]:
    """Build a kubectl delete command for a resource."""
    cmd = ["kubectl", "delete", resource_type, name, "--ignore-not-found"]
    if namespace:
        cmd.extend(["-n", namespace])
    if dry_run:
        cmd.append("--dry-run=server")
    if wait is not None:
        cmd.append(f"--wait={'true' if wait else 'false'}")
    if timeout:
        cmd.append(f"--timeout={timeout}")
    return cmd

def k8s_create_secret_generic(name: str, from_file_key: str, from_file_val: str, namespace: str | None = None, dry_run: bool = False) -> list[str]:
    """Build a kubectl command to create a generic secret."""
    cmd = ["kubectl", "create", "secret", "generic", name, f"--from-file={from_file_key}={from_file_val}"]
    if namespace:
        cmd.extend(["--namespace", namespace])
    cmd.extend(["--dry-run=client", "-o", "yaml"])
    return cmd

def k8s_create_configmap(name: str, files: list[str], namespace: str | None = None) -> list[str]:
    """Build a kubectl command to create a configmap (client dry-run)."""
    cmd = ["kubectl", "create", "configmap", name]
    if namespace:
        cmd.extend(["--namespace", namespace])
    for f in files:
        cmd.append(f"--from-file={f}")
    cmd.extend(["--dry-run=client", "-o", "yaml"])
    return cmd

def k8s_rollout_status(resource: str, namespace: str | None = None, timeout: str = "5m") -> list[str]:
    """Build a kubectl rollout status command."""
    cmd = ["kubectl", "rollout", "status", resource, f"--timeout={timeout}"]
    if namespace:
        cmd.extend(["-n", namespace])
    return cmd

def k8s_rollout_restart(resource: str, namespace: str | None = None) -> list[str]:
    """Build a kubectl rollout restart command."""
    cmd = ["kubectl", "rollout", "restart", resource]
    if namespace:
        cmd.extend(["-n", namespace])
    return cmd

def k8s_wait_ready(resource_type: str, label_selector: str, namespace: str | None = None, timeout: str = "5m") -> list[str]:
    """Build a kubectl wait ready command."""
    cmd = ["kubectl", "wait", "--for=condition=ready", resource_type, "-l", label_selector, f"--timeout={timeout}"]
    if namespace:
        cmd.extend(["-n", namespace])
    return cmd

def k8s_wait_job_complete(job_name: str, namespace: str | None = None, timeout: str = "120s") -> list[str]:
    """Build a kubectl wait job complete command."""
    cmd = ["kubectl", "wait", "--for=condition=complete", f"job/{job_name}", f"--timeout={timeout}"]
    if namespace:
        cmd.extend(["-n", namespace])
    return cmd

def k8s_port_forward(namespace: str, target: str, local_port: int, remote_port: int) -> list[str]:
    """Build a kubectl port-forward command."""
    resource = target if "/" in target else f"svc/{target}"
    return ["kubectl", "port-forward", "-n", namespace, resource, f"{local_port}:{remote_port}"]

def k8s_run_pod(name: str, image: str, cmd_args: list[str]) -> list[str]:
    """Build a kubectl run pod command."""
    cmd = ["kubectl", "run", name, f"--image={image}", "--restart=Never", "--rm", "-i", "--"]
    cmd.extend(cmd_args)
    return cmd

def k8s_exec(namespace: str, resource: str, exec_args: list[str]) -> list[str]:
    """Build a kubectl exec command."""
    cmd = ["kubectl", "exec", "-n", namespace, resource, "--"]
    cmd.extend(exec_args)
    return cmd

def k8s_scale(namespace: str, resource: str, replicas: int, dry_run: bool = False) -> list[str]:
    """Build a kubectl scale command."""
    cmd = ["kubectl", "scale", "deployment", resource, "-n", namespace, f"--replicas={replicas}"]
    if dry_run:
        cmd.append("--dry-run=server")
    return cmd

def k8s_patch(namespace: str, resource: str, patch_spec: str, dry_run: bool = False) -> list[str]:
    """Build a kubectl patch command."""
    cmd = ["kubectl", "patch", "deployment", resource, "-n", namespace, "--patch", patch_spec]
    if dry_run:
        cmd.append("--dry-run=server")
    return cmd

def k8s_set_image(namespace: str, deployment: str, container_image: str) -> list[str]:
    """Build a kubectl set image command (container_image e.g. front-end=repo:tag)."""
    return [
        "kubectl",
        "set",
        "image",
        f"deployment/{deployment}",
        container_image,
        "-n",
        namespace,
    ]

def k8s_get_jsonpath(namespace: str, resource_type: str, resource_name: str, jsonpath: str) -> list[str]:
    """Build a kubectl get command with jsonpath output."""
    return ["kubectl", "get", resource_type, resource_name, "-n", namespace, f"-o=jsonpath={jsonpath}"]

def k8s_get_pods_jsonpath(namespace: str, label_selector: str, jsonpath: str) -> list[str]:
    """Build a kubectl get pods command with jsonpath output."""
    return ["kubectl", "get", "pods", "-n", namespace, "-l", label_selector, f"-o=jsonpath={jsonpath}"]
