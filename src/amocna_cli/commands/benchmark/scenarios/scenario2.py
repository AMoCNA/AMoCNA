import time
import json
from amocna_cli.commands.benchmark.base import Scenario
from amocna_cli.commands.benchmark.registry import ScenarioRegistry
from amocna_cli.utils.ui import run, run_capture, info
from amocna_cli.utils.shell import (
    k8s_scale,
    k8s_patch,
    k8s_rollout_restart,
    k8s_get_jsonpath,
    ORDERS_CPU_RESET_PATCH,
)


@ScenarioRegistry.register
class Scenario2(Scenario):
    id = "2"
    name = "Vertical Scaling Remediation (Green Path)"
    allowed_intents = ["VerticalScalingUpIntent"]

    def initialize(self) -> None:
        from amocna_cli.commands.benchmark import run_sparql, _load_sparql_query

        run_sparql(self.cfg, _load_sparql_query(self.cfg, "clean-anomalies.sparql"))
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "clean-actions.sparql"))

    def setup_baseline(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load

        set_locust_load(200, 10)
        run(k8s_scale("sock-shop", "orders", 1), check=False)
        run(k8s_patch("sock-shop", "orders", ORDERS_CPU_RESET_PATCH), check=False)

    def trigger_anomaly(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load

        # We trigger the anomaly by strictly throttling the orders service
        # instead of using external cluster-stress.
        throttle_patch = json.dumps(
            {
                "spec": {
                    "template": {
                        "spec": {
                            "containers": [
                                {
                                    "name": "orders",
                                    "resources": {
                                        "requests": {"cpu": "100m"},
                                        "limits": {"cpu": "100m"},
                                    },
                                }
                            ]
                        }
                    }
                }
            }
        )

        self.logger.log("TRIGGER_ANOMALY", "Throttling orders service CPU to 100m")
        run(k8s_patch("sock-shop", "orders", throttle_patch), check=False)
        set_locust_load(1500, 30)

    def observe_remediation(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load

        start_obs = time.time()
        remediation_detected = False
        while time.time() - start_obs < 360:
            cpu = run_capture(
                k8s_get_jsonpath(
                    "sock-shop",
                    "deployment",
                    "orders",
                    "{.spec.template.spec.containers[0].resources.requests.cpu}",
                ),
                check=False,
            )
            if cpu == "1" and not remediation_detected:
                self.logger.log(
                    "REMEDIATION_DETECTED", "Orders CPU requests patched to 1"
                )
                self.logger.log("TRAFFIC_REBALANCE", "Restarting Locust workers...")
                run(
                    k8s_rollout_restart(
                        "deployment/locust-worker", namespace="sock-shop"
                    ),
                    check=False,
                )
                time.sleep(30)
                self.logger.log("RESUME_TRAFFIC", "Re-triggering Locust swarm")
                set_locust_load(1500, 30)
                remediation_detected = True
                break
            time.sleep(10)

    def cleanup(self) -> None:
        from amocna_cli.commands.benchmark import stop_locust

        stop_locust()
        run(k8s_scale("sock-shop", "orders", 1), check=False)
        run(k8s_patch("sock-shop", "orders", ORDERS_CPU_RESET_PATCH), check=False)
