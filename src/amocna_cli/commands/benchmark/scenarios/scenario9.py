import time
import subprocess
import re
import urllib.request
import urllib.parse
import json
import concurrent.futures
import csv
import os
import datetime
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
class Scenario9(Scenario):
    id = "9"
    name = "6-Hour Stress Test & Performance Monitoring (GraphDB, RabbitMQ, Palamedes, Themis)"
    allowed_intents = ["ConfigRemediationWorkflow", "ResourceRecalibrationIntent"]
    proxy_proc = None
    graphdb_pf = None

    def run(self, keep_on_failure: bool = False) -> None:
        try:
            from amocna_cli.commands.benchmark import set_palamedes_filter
            from amocna_cli.utils.ui import header

            header(f"Scenario {self.id}: {self.name}")

            self.logger.log("INIT", "Cleaning up stale resources and states...")
            set_palamedes_filter(self.allowed_intents, logger=self.logger)
            
            # Start proxy and port-forward
            self.initialize()
            time.sleep(15)  # Sync wait

            # Establish baseline: 200 nodes, 1000 pods
            self.setup_baseline()
            
            # Duration (default 6 hours = 21600 seconds)
            duration_s = int(os.environ.get("AMOCNA_BENCHMARK_DURATION", 21600))
            self.logger.log("STRESS_LOOP_START", f"Starting stress test loop. Target duration: {duration_s} seconds.")
            
            csv_filename = f"benchmark_metrics_9_{int(time.time())}.csv"
            csv_filepath = os.path.join(os.getcwd(), csv_filename)
            self.logger.log("STRESS_LOOP_START", f"Writing live metrics to: {csv_filepath}")
            
            headers = [
                "timestamp_s", "wall_clock", 
                "graphdb_cpu_m", "graphdb_mem_mib",
                "rabbitmq_cpu_m", "rabbitmq_mem_mib",
                "palamedes_cpu_m", "palamedes_mem_mib",
                "themis_cpu_m", "themis_mem_mib",
                "metis_cpu_m", "metis_mem_mib",
                "rmq_graph_updates", "rmq_action_queue", "rmq_vulnerability_updates", "rmq_status_queue",
                "graphdb_triples", "graphdb_query_latency_ms", "graphdb_update_latency_ms",
                "palamedes_avg_planning_ms", "themis_avg_execution_ms"
            ]
            
            # Create/open CSV and write header
            with open(csv_filepath, mode="w", newline="") as f:
                writer = csv.writer(f)
                writer.writerow(headers)
            
            start_time = time.time()
            last_anomaly_t = start_time
            last_churn_t = start_time
            
            anomaly_injected = False
            
            # We will run measurements every 30 seconds
            poll_interval = 30
            
            while True:
                now = time.time()
                elapsed = now - start_time
                if elapsed >= duration_s:
                    break
                    
                wall_clock = datetime.datetime.now().strftime("%H:%M:%S")
                
                # --- Periodic Actions ---
                # 1. Anomaly stress cycle (every 10 minutes total, 5 minutes per state):
                #    At 0m: inject ConfigDriftState in 500 pods.
                #    At 5m: clean anomalies and actions.
                #    This gives Palamedes 5 minutes to reason/plan, then resets.
                if now - last_anomaly_t >= 300:
                    last_anomaly_t = now
                    if not anomaly_injected:
                        self.trigger_anomaly()
                        anomaly_injected = True
                    else:
                        self.logger.log("STRESS_RESET", "Clearing injected anomalies and actions for the next cycle")
                        self._clear_anomalies_and_actions()
                        anomaly_injected = False
                
                # 2. Pod churn cycle (every 10 minutes):
                #    Delete 50 pods and re-create 50 pods.
                if now - last_churn_t >= 600:
                    last_churn_t = now
                    self.logger.log("POD_CHURN", "Starting periodic pod churn (re-creating 50 pods)")
                    self._run_pod_churn()
                
                # --- Metrics Collection ---
                # Resource Usage
                gdb_cpu, gdb_mem = self._get_pod_resources("graphdb", "graphdb")
                rmq_cpu, rmq_mem = self._get_pod_resources("rabbitmq", "rabbitmq")
                pal_cpu, pal_mem = self._get_pod_resources("palamedes", "palamedes")
                thm_cpu, thm_mem = self._get_pod_resources("themis", "themis")
                met_cpu, met_mem = self._get_pod_resources("metis", "metis")
                
                # RabbitMQ Queue sizes
                rmq_gu, rmq_aq, rmq_vu, rmq_sq = self._get_rabbitmq_queues()
                
                # GraphDB statistics
                triples = self._get_graphdb_triple_count()
                
                # GraphDB Latencies
                gdb_q_lat, gdb_u_lat = self._measure_graphdb_latencies()
                
                # Palamedes & Themis internal logging latencies
                pal_lat = self._parse_logs_for_latencies("palamedes", "palamedes", poll_interval + 5)
                thm_lat = self._parse_logs_for_latencies("themis", "themis", poll_interval + 5)
                
                # --- Log to CSV ---
                row = [
                    round(elapsed, 2),
                    wall_clock,
                    gdb_cpu, gdb_mem,
                    rmq_cpu, rmq_mem,
                    pal_cpu, pal_mem,
                    thm_cpu, thm_mem,
                    met_cpu, met_mem,
                    rmq_gu, rmq_aq, rmq_vu, rmq_sq,
                    triples,
                    round(gdb_q_lat, 2) if gdb_q_lat >= 0 else "",
                    round(gdb_u_lat, 2) if gdb_u_lat >= 0 else "",
                    round(pal_lat, 2) if pal_lat > 0 else "",
                    round(thm_lat, 2) if thm_lat > 0 else ""
                ]
                
                with open(csv_filepath, mode="a", newline="") as f:
                    writer = csv.writer(f)
                    writer.writerow(row)
                
                # Print periodic progress to console
                console.print(
                    f"    [[bold green]{elapsed:06.1f}s[/bold green]] GDB-Mem: {gdb_mem}MiB, RMQ-Msg: {rmq_gu + rmq_aq + rmq_vu + rmq_sq}, Query-Lat: {gdb_q_lat:.1f}ms, Plan-Lat: {pal_lat:.1f}ms"
                )
                
                # Wait for next interval (taking into account how long collection took)
                elapsed_collect = time.time() - now
                sleep_time = max(0.1, poll_interval - elapsed_collect)
                time.sleep(sleep_time)
                
            self.logger.log("END_SCENARIO", f"Scenario {self.id} completed")
            self.logger.save()
            self.cleanup()
            
        except Exception as e:
            self.logger.log("FAILURE", f"Scenario failed: {str(e)}")
            self.logger.save()
            if not keep_on_failure:
                self.cleanup()
            raise

    def initialize(self) -> None:
        self.logger.log(
            "INITIALIZE",
            "Starting proxy, port-forwards, and cleaning up existing simulated pods and nodes",
        )

        self._start_proxy()
        self._start_graphdb_forward()
        self._run_bulk_cleanup()
        self._clear_anomalies_and_actions()

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

    def _start_graphdb_forward(self):
        if self.graphdb_pf is None:
            try:
                req = urllib.request.Request("http://localhost:7200/protocol", method="GET")
                with urllib.request.urlopen(req, timeout=2) as response:
                    if response.status == 200:
                        self.logger.log("PORT_FORWARD", "GraphDB is already reachable on port 7200")
                        return
            except Exception:
                pass

            self.logger.log("PORT_FORWARD", "Starting kubectl port-forward for GraphDB on port 7200")
            self.graphdb_pf = subprocess.Popen(
                ["kubectl", "port-forward", "-n", "graphdb", "svc/graphdb", "7200:7200"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            time.sleep(3)  # Wait for port-forward to bind

    def _stop_graphdb_forward(self):
        if self.graphdb_pf is not None:
            self.logger.log("PORT_FORWARD", "Stopping kubectl port-forward for GraphDB")
            self.graphdb_pf.terminate()
            try:
                self.graphdb_pf.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.graphdb_pf.kill()
            self.graphdb_pf = None

    def _run_bulk_cleanup(self):
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
        # Standard observe is not called since we customized the flow in run()
        pass

    def cleanup(self) -> None:
        self.logger.log(
            "CLEANUP",
            "Scaling down virtual pods and nodes, stopping port-forwards, and clearing GraphDB states",
        )

        self._start_proxy()
        self._run_bulk_cleanup()
        self._stop_proxy()
        self._stop_graphdb_forward()
        self._clear_anomalies_and_actions()

    # --- Helper methods for metrics collection ---

    def _get_pod_resources(self, namespace: str, app_label: str) -> tuple[int, int]:
        try:
            cmd = ["kubectl", "top", "pod", "-n", namespace, "-l", f"app={app_label}", "--no-headers"]
            out = run_capture(cmd, check=False).strip()
            if not out:
                return 0, 0
            
            total_cpu = 0
            total_mem = 0
            for line in out.splitlines():
                parts = line.split()
                if len(parts) >= 3:
                    cpu_str = parts[1]
                    mem_str = parts[2]
                    
                    # Parse CPU
                    if cpu_str.endswith("m"):
                        cpu = int(cpu_str[:-1])
                    else:
                        cpu = int(cpu_str) * 1000
                        
                    # Parse Memory
                    if mem_str.endswith("Mi"):
                        mem = int(mem_str[:-2])
                    elif mem_str.endswith("Gi"):
                        mem = int(mem_str[:-2]) * 1024
                    elif mem_str.endswith("Ki"):
                        mem = int(mem_str[:-2]) // 1024
                    else:
                        mem = int(mem_str) // (1024 * 1024)
                        
                    total_cpu += cpu
                    total_mem += mem
            return total_cpu, total_mem
        except Exception as e:
            self.logger.log("METRICS_WARN", f"Failed to get resource usage for {namespace}/{app_label}: {e}")
            return 0, 0

    def _get_rabbitmq_queues(self) -> tuple[int, int, int, int]:
        try:
            cmd = ["kubectl", "exec", "-n", "rabbitmq", "deploy/rabbitmq", "--", "rabbitmqctl", "list_queues", "--formatter", "json"]
            out = run_capture(cmd, check=False).strip()
            if out:
                data = json.loads(out)
                queues = {q["name"]: q["messages"] for q in data}
                return (
                    queues.get("amocna.graph.updates", 0),
                    queues.get("amocna.action.queue", 0),
                    queues.get("amocna.vulnerability.updates", 0),
                    queues.get("amocna.status.queue", 0)
                )
        except Exception as e:
            self.logger.log("METRICS_WARN", f"Failed to get RabbitMQ queue sizes: {e}")
        return 0, 0, 0, 0

    def _get_graphdb_triple_count(self) -> int:
        query = "SELECT (COUNT(*) as ?count) WHERE { ?s ?p ?o }"
        try:
            res = run_query_local(query)
            if res and "results" in res:
                return int(res["results"]["bindings"][0]["count"]["value"])
        except Exception as e:
            self.logger.log("METRICS_WARN", f"Failed to get GraphDB triple count: {e}")
        return 0

    def _measure_graphdb_latencies(self) -> tuple[float, float]:
        query_latency = -1.0
        update_latency = -1.0
        
        # 1. Measure query latency
        query = "SELECT (COUNT(*) as ?count) WHERE { ?s ?p ?o }"
        t0 = time.time()
        try:
            run_query_local(query)
            query_latency = (time.time() - t0) * 1000.0
        except Exception:
            pass
            
        # 2. Measure update latency
        update = """
        PREFIX ex: <http://example.org/>
        DELETE WHERE { ex:test_latency ex:val ?v } ;
        INSERT DATA { ex:test_latency ex:val 1 }
        """
        t0 = time.time()
        try:
            run_update_local(update)
            update_latency = (time.time() - t0) * 1000.0
        except Exception:
            pass
            
        return query_latency, update_latency

    def _parse_logs_for_latencies(self, namespace: str, app_label: str, since_s: int) -> float:
        try:
            cmd = ["kubectl", "logs", "-n", namespace, "-l", f"app={app_label}", f"--since={since_s}s"]
            logs = run_capture(cmd, check=False)
            durations = []
            for line in logs.splitlines():
                if "succeeded in" in line:
                    match = re.search(r'succeeded in (\d+)ms', line)
                    if match:
                        durations.append(int(match.group(1)))
            if durations:
                return sum(durations) / len(durations)
        except Exception as e:
            self.logger.log("METRICS_WARN", f"Failed to parse logs for {namespace}/{app_label}: {e}")
        return 0.0

    def _clear_anomalies_and_actions(self):
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
            self.logger.log("STRESS_RESET_WARN", f"Failed to clean GraphDB states: {e}")

    def _run_pod_churn(self):
        pod_names = [f"kwok-pod-{i}" for i in range(200)]
        try:
            # Delete 200 pods
            cmd = ["kubectl", "delete", "pod", "-n", "amocna-benchmark", "--grace-period=0", "--force"] + pod_names
            subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            
            # Re-create the pods
            pods_data = []
            for i in range(200):
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
            with concurrent.futures.ThreadPoolExecutor(max_workers=50) as executor:
                futures = [
                    executor.submit(post_resource, pod_url, pod) for pod in pods_data
                ]
                results = [f.result() for f in concurrent.futures.as_completed(futures)]
            success = sum(1 for r in results if r)
            self.logger.log("POD_CHURN", f"Churn complete: Re-created {success}/200 pods")
        except Exception as e:
            self.logger.log("POD_CHURN_ERR", f"Failed to perform pod churn: {e}")
