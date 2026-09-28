import time
import subprocess
from amocna_cli.commands.benchmark.base import Scenario
from amocna_cli.commands.benchmark.registry import ScenarioRegistry
from amocna_cli.utils.ui import run, run_capture
from amocna_cli.utils.shell import (
    k8s_scale,
    k8s_delete_resource,
    k8s_get_jsonpath,
)

@ScenarioRegistry.register
class Scenario6(Scenario):
    id = "6"
    name = "Single-Step Config Remediation (Green Path)"
    allowed_intents = ["ConfigRemediationWorkflow"]

    def initialize(self) -> None:
        from amocna_cli.commands.benchmark import run_sparql, _load_sparql_query
        run(k8s_delete_resource("configmap", "orders-config", namespace="sock-shop"), check=False)
        run(k8s_scale("sock-shop", "orders", 1), check=False)
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "clean-anomalies.sparql"))
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "clean-actions.sparql"))
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "restore-restart.sparql"))

    def setup_baseline(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load
        set_locust_load(200, 10)
        run(k8s_scale("default", "cluster-stress", 1), check=False)
        
        # Deploy dummy ConfigMap
        create_cm = subprocess.run(
            [
                "kubectl", "create", "configmap", "orders-config",
                "-n", "sock-shop", "--from-literal=updated=false",
                "--dry-run=client", "-o", "yaml",
            ],
            capture_output=True, text=True, check=True,
        )
        subprocess.run(
            ["kubectl", "apply", "-f", "-"],
            input=create_cm.stdout, text=True, check=True,
        )

    def trigger_anomaly(self) -> None:
        from amocna_cli.commands.benchmark import run_sparql, _load_sparql_query
        self.logger.log("TRIGGER_ANOMALY", "Injecting ConfigDriftState for Green Path test")
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "inject-drift.sparql"))

    def observe_remediation(self) -> None:
        start_obs = time.time()
        remediation_detected = False
        while time.time() - start_obs < 360:
            val = run_capture(
                k8s_get_jsonpath("sock-shop", "configmap", "orders-config", "{.data.updated}"),
                check=False,
            )
            if val == "true" and not remediation_detected:
                self.logger.log("REMEDIATION_DETECTED", "ConfigMap patched to 'true'")
                remediation_detected = True
                break
            time.sleep(10)

    def cleanup(self) -> None:
        from amocna_cli.commands.benchmark import stop_locust
        stop_locust()
        run(k8s_scale("default", "cluster-stress", 0), check=False)
        run(k8s_delete_resource("configmap", "orders-config", namespace="sock-shop"), check=False)
