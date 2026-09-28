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
class Scenario4(Scenario):
    id = "4"
    name = "Multi-Step Saga Remediation (Red Path Rollback)"
    allowed_intents = ["SagaRemediationIntent"]

    def initialize(self) -> None:
        from amocna_cli.commands.benchmark import run_sparql, _load_sparql_query

        self.logger.log(
            "INITIALIZE",
            "Cleaning up stale configmaps and restoring default GraphDB state",
        )
        run(
            k8s_delete_resource("configmap", "orders-config", namespace="sock-shop"),
            check=False,
        )
        run(k8s_scale("sock-shop", "orders", 1), check=False)
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "clean-anomalies.sparql"))
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "clean-actions.sparql"))
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "restore-restart.sparql"))

    def setup_baseline(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load

        self.logger.log(
            "SETUP_BASELINE",
            "Setting baseline traffic (50 users), scaling stress workloads, and deploying dummy ConfigMap",
        )
        set_locust_load(50, 5)
        run(k8s_scale("default", "cluster-stress", 1), check=False)

        # Deploy dummy ConfigMap
        create_cm = subprocess.run(
            [
                "kubectl",
                "create",
                "configmap",
                "orders-config",
                "-n",
                "sock-shop",
                "--from-literal=updated=false",
                "--dry-run=client",
                "-o",
                "yaml",
            ],
            capture_output=True,
            text=True,
            check=True,
        )
        subprocess.run(
            ["kubectl", "apply", "-f", "-"],
            input=create_cm.stdout,
            text=True,
            check=True,
        )

    def trigger_anomaly(self) -> None:
        from amocna_cli.commands.benchmark import run_sparql, _load_sparql_query

        self.logger.log("TRIGGER_ANOMALY", "Injecting FAIL_NOW and ConfigDriftState")
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "fail-restart.sparql"))
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "inject-drift.sparql"))

    def observe_remediation(self) -> None:
        self.logger.log(
            "OBSERVE_START",
            "Polling orders-config ConfigMap for Saga progression and rollback",
        )
        start_obs = time.time()
        patch_detected = False
        rollback_detected = False
        last_logged_val = None
        while time.time() - start_obs < 360:
            val = run_capture(
                k8s_get_jsonpath(
                    "sock-shop", "configmap", "orders-config", "{.data.updated}"
                ),
                check=False,
            )

            if val != last_logged_val:
                self.logger.log(
                    "CONFIGMAP_STATE",
                    f"orders-config 'updated' key is currently: '{val}'",
                )
                last_logged_val = val

            if val == "true" and not patch_detected:
                self.logger.log(
                    "PATCH_DETECTED",
                    "Saga executed PatchConfigAction. ConfigMap 'updated' is now 'true'. Waiting for RestartPodAction to fail...",
                )
                patch_detected = True

            if val == "false" and patch_detected and not rollback_detected:
                self.logger.log(
                    "ROLLBACK_DETECTED",
                    "Saga compensation triggered. ConfigMap successfully rolled back to 'false' (Red Path rollback)",
                )
                self.logger.log(
                    "REMEDIATION_DETECTED",
                    "Multi-Step Saga Remediation successfully completed with rollback",
                )
                rollback_detected = True
                break
            time.sleep(10)

    def cleanup(self) -> None:
        from amocna_cli.commands.benchmark import run_sparql, _load_sparql_query

        self.logger.log(
            "CLEANUP",
            "Scaling down stress workload, deleting orders-config, and restoring normal restart configuration in GraphDB",
        )
        run(k8s_scale("default", "cluster-stress", 0), check=False)
        run(
            k8s_delete_resource("configmap", "orders-config", namespace="sock-shop"),
            check=False,
        )
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "restore-restart.sparql"))
