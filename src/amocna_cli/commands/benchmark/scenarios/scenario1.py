import time
from amocna_cli.commands.benchmark.base import Scenario
from amocna_cli.commands.benchmark.registry import ScenarioRegistry
from amocna_cli.utils.ui import run, run_capture
from amocna_cli.utils.shell import (
    k8s_scale,
    k8s_rollout_restart,
    k8s_get_jsonpath,
)


@ScenarioRegistry.register
class Scenario1(Scenario):
    id = "1"
    name = "Horizontal Scaling Remediation (Green Path)"
    allowed_intents = ["HorizontalScalingUpIntent", "HorizontalScalingDownIntent"]

    def initialize(self) -> None:
        from amocna_cli.commands.benchmark import run_sparql, _load_sparql_query

        run_sparql(self.cfg, _load_sparql_query(self.cfg, "clean-anomalies.sparql"))
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "clean-actions.sparql"))

    def setup_baseline(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load

        set_locust_load(200, 10)
        run(k8s_scale("default", "cluster-stress", 1), check=False)
        run(k8s_scale("sock-shop", "front-end", 1), check=False)

    def trigger_anomaly(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load

        self.logger.log("TRIGGER_ANOMALY", "Spiking Locust to 1800 users")
        set_locust_load(1800, 50)

    def observe_remediation(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load

        start_obs = time.time()
        remediation_detected = False
        while time.time() - start_obs < 360:
            replicas = run_capture(
                k8s_get_jsonpath(
                    "sock-shop", "deployment", "front-end", "{.status.readyReplicas}"
                ),
                check=False,
            )
            if replicas == "3" and not remediation_detected:
                self.logger.log(
                    "REMEDIATION_DETECTED", "Frontend successfully scaled to 3 replicas"
                )
                self.logger.log(
                    "TRAFFIC_REBALANCE",
                    "Restarting Locust workers to balance traffic...",
                )
                run(
                    k8s_rollout_restart(
                        "deployment/locust-worker", namespace="sock-shop"
                    ),
                    check=False,
                )
                time.sleep(30)
                self.logger.log("RESUME_TRAFFIC", "Re-triggering Locust swarm")
                set_locust_load(1800, 50)
                remediation_detected = True
                break
            time.sleep(10)

        # Scale Down Part
        self.logger.log("SCALE_DOWN_TRIGGER", "Reducing Locust back to 200 users")
        set_locust_load(200, 10)

        self.logger.log("SCALE_DOWN_OBSERVATION", "Monitoring scale down to 1 replica")
        start_sd = time.time()
        while time.time() - start_sd < 360:
            replicas = run_capture(
                k8s_get_jsonpath(
                    "sock-shop", "deployment", "front-end", "{.status.readyReplicas}"
                ),
                check=False,
            )
            if replicas == "1":
                self.logger.log(
                    "SCALE_DOWN_DETECTED",
                    "Frontend successfully scaled back to 1 replica",
                )
                break
            time.sleep(10)

    def cleanup(self) -> None:
        from amocna_cli.commands.benchmark import stop_locust

        stop_locust()
        run(k8s_scale("default", "cluster-stress", 0), check=False)
        run(k8s_scale("sock-shop", "front-end", 1), check=False)
