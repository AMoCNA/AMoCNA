import time
import subprocess
import re
import urllib.request
import urllib.parse
import json
import concurrent.futures
from amocna_cli.commands.benchmark.base import Scenario
from amocna_cli.commands.benchmark.registry import ScenarioRegistry
from amocna_cli.utils.ui import run, run_capture, console


def post_resource(url, data_dict):
    try:
        data = json.dumps(data_dict).encode("utf-8")
        req = urllib.request.Request(
            url, data=data, headers={"Content-Type": "application/json"}, method="POST"
        )
        with urllib.request.urlopen(req, timeout=5) as response:
            return response.status in (200, 201, 202)
    except Exception as e:
        if hasattr(e, "code") and e.code == 409:
            return True
        print(f"Error posting resource: {e}")
        return False


def run_query_local(sparql_query):
    url = "http://localhost:7200/repositories/amocna"
    data = urllib.parse.urlencode({"query": sparql_query}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Accept": "application/sparql-results+json",
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )
    with urllib.request.urlopen(req) as response:
        return json.loads(response.read().decode("utf-8"))


def run_update_local(sparql_update):
    url = "http://localhost:7200/repositories/amocna/statements"
    data = urllib.parse.urlencode({"update": sparql_update}).encode("utf-8")
    req = urllib.request.Request(
        url, data=data, headers={"Content-Type": "application/x-www-form-urlencoded"}
    )
    with urllib.request.urlopen(req) as response:
        return response.read()


def delete_collection(url):
    try:
        req = urllib.request.Request(url, method="DELETE")
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status in (200, 202, 204)
    except Exception as e:
        return False


@ScenarioRegistry.register
class Scenario8(Scenario):
    id = "8"
    name = "Semantic Control Plane Scalability Benchmark"
    allowed_intents = ["ConfigRemediationWorkflow", "ResourceRecalibrationIntent"]
    proxy_proc = None

    def initialize(self) -> None:
        self.logger.log(
            "INITIALIZE",
            "Starting proxy and cleaning up existing simulated pods and nodes",
        )

        # Make sure the proxy is running for initialization cleanup
        self._start_proxy()

        # Fast bulk deletion of simulated nodes and pods
        self._run_bulk_cleanup()

        # Clean GraphDB anomalies and actions
        clean_anomalies = """
        PREFIX cnee: <http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/>
        DELETE {
          ?s cnee:hasState ?state .
        }
        WHERE {
          ?s cnee:hasState ?state .
          ?state a ?stateType .
          FILTER(?stateType IN (cnee:ResponseTimeSlaViolatedState, cnee:ResponseTimeNormalState, cnee:SecurityVulnerabilityDetectedState, cnee:ConfigDriftState))
        }
        """
        clean_actions = """
        PREFIX moam: <http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#>
        DELETE {
          ?action ?p ?o .
        }
        WHERE {
          ?action a moam:RemediationAction .
          ?action ?p ?o .
        }
        """
        try:
            run_update_local(clean_anomalies)
            run_update_local(clean_actions)
        except Exception as e:
            self.logger.log("INITIALIZE_WARN", f"Failed to clean GraphDB states: {e}")

    def _start_proxy(self):
        if self.proxy_proc is None:
            self.logger.log("PROXY", "Starting kubectl proxy on port 8001")
            self.proxy_proc = subprocess.Popen(
                ["kubectl", "proxy", "--port=8001"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            time.sleep(2)  # Wait for proxy to bind

    def _stop_proxy(self):
        if self.proxy_proc is not None:
            self.logger.log("PROXY", "Stopping kubectl proxy")
            self.proxy_proc.terminate()
            try:
                self.proxy_proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.proxy_proc.kill()
            self.proxy_proc = None

    def _run_bulk_cleanup(self):
        # Delete pods matching label type=kwok-fake in amocna-benchmark namespace
        pod_delete_url = "http://localhost:8001/api/v1/namespaces/amocna-benchmark/pods?labelSelector=type%3Dkwok-fake"
        node_delete_url = "http://localhost:8001/api/v1/nodes?labelSelector=type%3Dkwok"

        console.print("    Performing fast bulk deletion of simulated pods...")
        pod_ok = delete_collection(pod_delete_url)
        console.print("    Performing fast bulk deletion of simulated nodes...")
        node_ok = delete_collection(node_delete_url)

        if not pod_ok or not node_ok:
            console.print(
                "    Bulk deleteCollection failed or partial. Falling back to kubectl..."
            )
            run(
                [
                    "kubectl",
                    "delete",
                    "pods",
                    "-n",
                    "amocna-benchmark",
                    "-l",
                    "type=kwok-fake",
                    "--grace-period=0",
                    "--force",
                    "--ignore-not-found",
                ],
                check=False,
            )
            run(
                ["kubectl", "delete", "nodes", "-l", "type=kwok", "--ignore-not-found"],
                check=False,
            )

    def setup_baseline(self) -> None:
        self._start_proxy()

        # Ensure benchmark namespace exists
        run(["kubectl", "create", "namespace", "amocna-benchmark"], check=False)

        self.logger.log("SETUP_BASELINE", "Creating 200 virtual nodes in parallel")
        nodes_data = []
        for i in range(200):
            nodes_data.append(
                {
                    "apiVersion": "v1",
                    "kind": "Node",
                    "metadata": {
                        "name": f"kwok-node-{i}",
                        "annotations": {"kwok.x-k8s.io/node": "fake"},
                        "labels": {"type": "kwok"},
                    },
                    "spec": {
                        "taints": [
                            {
                                "effect": "NoSchedule",
                                "key": "kwok.x-k8s.io/node",
                                "value": "fake",
                            }
                        ]
                    },
                }
            )

        node_url = "http://localhost:8001/api/v1/nodes"
        with concurrent.futures.ThreadPoolExecutor(max_workers=30) as executor:
            futures = [
                executor.submit(post_resource, node_url, node) for node in nodes_data
            ]
            results = [f.result() for f in concurrent.futures.as_completed(futures)]

        node_success = sum(1 for r in results if r)
        self.logger.log(
            "SETUP_BASELINE", f"Created {node_success}/200 virtual nodes successfully."
        )

        self.logger.log(
            "SETUP_BASELINE", "Creating 1,000 virtual pods in parallel (80 workers)"
        )
        pods_data = []
        for i in range(1000):
            node_idx = i % 50
            pods_data.append(
                {
                    "apiVersion": "v1",
                    "kind": "Pod",
                    "metadata": {
                        "name": f"kwok-pod-{i}",
                        "namespace": "amocna-benchmark",
                        "labels": {"type": "kwok-fake"},
                    },
                    "spec": {
                        "nodeName": f"kwok-node-{node_idx}",
                        "containers": [
                            {"name": "pause", "image": "registry.k8s.io/pause:3.9"}
                        ],
                    },
                }
            )

        pod_url = "http://localhost:8001/api/v1/namespaces/amocna-benchmark/pods"
        with concurrent.futures.ThreadPoolExecutor(max_workers=80) as executor:
            futures = [
                executor.submit(post_resource, pod_url, pod) for pod in pods_data
            ]
            results = []
            for j, f in enumerate(concurrent.futures.as_completed(futures)):
                results.append(f.result())
                if (j + 1) % 200 == 0:
                    console.print(f"    Posted {j + 1}/1000 pods to API server...")

        pod_success = sum(1 for r in results if r)
        self.logger.log(
            "SETUP_BASELINE", f"Created {pod_success}/1000 virtual pods successfully."
        )

        self.logger.log(
            "SETUP_BASELINE", "Waiting for Metis to ingest pods into GraphDB"
        )

        start_wait = time.time()
        last_count = 0
        no_change_ticks = 0

        while True:
            time.sleep(10)
            query = """
            PREFIX cnee: <http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/>
            SELECT (COUNT(?pod) as ?count) WHERE {
              ?pod a cnee:ExecutionUnit .
              ?pod cnee:namespace "amocna-benchmark" .
            }
            """
            try:
                results = run_query_local(query)
                count = 0
                if results and "results" in results:
                    count = int(results["results"]["bindings"][0]["count"]["value"])
            except Exception as e:
                console.print(f"    Warning: failed to query GraphDB: {e}")
                continue

            console.print(f"    Ingestion progress: {count}/1000 pods")

            if count >= 1000:
                self.logger.log(
                    "INGESTION_COMPLETE",
                    "All 1,000 pods successfully ingested in GraphDB",
                )
                break

            if count == last_count:
                no_change_ticks += 1
                if no_change_ticks >= 12:  # 2 minutes
                    self.logger.log(
                        "INGESTION_TIMEOUT",
                        f"Ingestion stalled at {count} pods. Proceeding.",
                    )
                    break
            else:
                no_change_ticks = 0
                last_count = count

            if time.time() - start_wait > 600:  # 10 minutes timeout
                self.logger.log(
                    "INGESTION_TIMEOUT",
                    f"Metis ingestion timed out at {count} pods. Proceeding.",
                )
                break

        # Record total saturated triples
        try:
            triple_query = "SELECT (COUNT(*) as ?count) WHERE { ?s ?p ?o }"
            trip_results = run_query_local(triple_query)
            total_triples = (
                int(trip_results["results"]["bindings"][0]["count"]["value"])
                if trip_results
                else 0
            )
            self.logger.log(
                "SATURATED_KB", f"Total semantic triples in GraphDB: {total_triples}"
            )
        except Exception as e:
            self.logger.log("SATURATED_KB_ERR", f"Failed to get total triples: {e}")

        # Record GraphDB Memory
        mem_info = run_capture(
            ["kubectl", "top", "pod", "-n", "graphdb", "--no-headers"], check=False
        )
        self.logger.log(
            "GRAPHDB_MEM_BASELINE", f"GraphDB Pod Memory footprint: {mem_info.strip()}"
        )

    def trigger_anomaly(self) -> None:
        self.logger.log(
            "TRIGGER_ANOMALY", "Injecting ConfigDriftState into 500 pods simultaneously"
        )

        query = """
        PREFIX cnee: <http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/>
        INSERT {
          ?pod cnee:hasState [ a cnee:ConfigDriftState ] .
        }
        WHERE {
          SELECT ?pod WHERE {
            ?pod a cnee:Pod .
            ?pod cnee:namespace "amocna-benchmark" .
          }
          LIMIT 500
        }
        """
        start_t = time.time()
        try:
            run_update_local(query)
            dur = (time.time() - start_t) * 1000
            self.logger.log(
                "ANOMALY_INJECT_DURATION",
                f"SPARQL Update anomaly injection completed in {dur:.2f} ms",
            )
        except Exception as e:
            self.logger.log("ANOMALY_INJECT_ERR", f"SPARQL Update failed: {e}")

    def observe_remediation(self) -> None:
        self.logger.log(
            "OBSERVE_REMEDIATION",
            "Waiting for Palamedes reasoning and planning engine (60s)",
        )
        time.sleep(60)

        # Get GraphDB Memory consumption
        mem_info = run_capture(
            ["kubectl", "top", "pod", "-n", "graphdb", "--no-headers"], check=False
        )
        self.logger.log(
            "GRAPHDB_MEM_STRESS",
            f"GraphDB Memory footprint under stress: {mem_info.strip()}",
        )

        # Retrieve Palamedes logs
        self.logger.log(
            "OBSERVE_REMEDIATION",
            "Retrieving Palamedes logs to inspect planning timings",
        )
        logs = run_capture(
            ["kubectl", "logs", "-n", "palamedes", "-l", "app=palamedes", "--since=2m"],
            check=False,
        )

        durations = {}
        for line in logs.splitlines():
            if "succeeded in" in line:
                match = re.search(r'"message":"(.*?) succeeded in (\d+)ms"', line)
                if match:
                    step = match.group(1)
                    dur = int(match.group(2))
                    if step not in durations:
                        durations[step] = []
                    durations[step].append(dur)

        for step, durs in durations.items():
            avg_dur = sum(durs) / len(durs)
            max_dur = max(durs)
            self.logger.log(
                "PLANNER_LATENCY",
                f"{step}: avg={avg_dur:.2f}ms, max={max_dur}ms (count={len(durs)})",
            )

    def cleanup(self) -> None:
        self.logger.log(
            "CLEANUP",
            "Scaling down virtual pods and nodes, and clearing GraphDB states",
        )

        self._start_proxy()
        self._run_bulk_cleanup()
        self._stop_proxy()

        clean_anomalies = """
        PREFIX cnee: <http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/>
        DELETE {
          ?s cnee:hasState ?state .
        }
        WHERE {
          ?s cnee:hasState ?state .
          ?state a ?stateType .
          FILTER(?stateType IN (cnee:ResponseTimeSlaViolatedState, cnee:ResponseTimeNormalState, cnee:SecurityVulnerabilityDetectedState, cnee:ConfigDriftState))
        }
        """
        clean_actions = """
        PREFIX moam: <http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#>
        DELETE {
          ?action ?p ?o .
        }
        WHERE {
          ?action a moam:RemediationAction .
          ?action ?p ?o .
        }
        """
        try:
            run_update_local(clean_anomalies)
            run_update_local(clean_actions)
        except Exception as e:
            self.logger.log("CLEANUP_WARN", f"Failed to clean GraphDB states: {e}")
