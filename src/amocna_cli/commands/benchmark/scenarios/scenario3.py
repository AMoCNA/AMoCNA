import time
from amocna_cli.commands.benchmark.base import Scenario
from amocna_cli.commands.benchmark.registry import ScenarioRegistry
from amocna_cli.utils.ui import run, run_capture, error
from amocna_cli.utils.shell import (
    k8s_scale,
    k8s_get_jsonpath,
    k8s_get_pods_jsonpath,
)
from amocna_cli.commands.benchmark import SOCK_SHOP_FRONTEND_PATCHED_TAG

@ScenarioRegistry.register
class Scenario3(Scenario):
    id = "3"
    name = "Security Vulnerability Remediation (Red Path)"
    allowed_intents = ["ImageUpdateIntent"]

    def initialize(self) -> None:
        from amocna_cli.commands.benchmark import run_sparql, _load_sparql_query
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "clean-anomalies.sparql"))
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "clean-actions.sparql"))

    def setup_baseline(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load
        set_locust_load(50, 10)
        run(k8s_scale("default", "cluster-stress", 1), check=False)

    def trigger_anomaly(self) -> None:
        from amocna_cli.commands.benchmark import run_sparql, _load_sparql_query
        pod_name = run_capture(
            k8s_get_pods_jsonpath("sock-shop", "name=front-end", "{.items[0].metadata.name}")
        )
        if not pod_name:
            raise RuntimeError("Active front-end pod not found")

        self.logger.log("TRIGGER_ANOMALY", f"Injecting Security Vulnerability for pod {pod_name}")
        vulnerability_query = _load_sparql_query(self.cfg, "inject-vulnerability.sparql").replace("{pod_name}", pod_name)
        run_sparql(self.cfg, vulnerability_query)

    def observe_remediation(self) -> None:
        start_obs = time.time()
        remediation_detected = False
        while time.time() - start_obs < 360:
            image = run_capture(
                k8s_get_jsonpath("sock-shop", "deployment", "front-end", "{.spec.template.spec.containers[0].image}"),
                check=False,
            )
            if SOCK_SHOP_FRONTEND_PATCHED_TAG in image and not remediation_detected:
                self.logger.log("REMEDIATION_DETECTED", f"Frontend updated to {SOCK_SHOP_FRONTEND_PATCHED_TAG}")
                remediation_detected = True
                break
            time.sleep(10)

    def cleanup(self) -> None:
        run(k8s_scale("default", "cluster-stress", 0), check=False)
