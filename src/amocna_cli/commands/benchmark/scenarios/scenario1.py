import os
import time
from amocna_cli.commands.benchmark.base import Scenario
from amocna_cli.commands.benchmark.registry import ScenarioRegistry
from amocna_cli.commands.paper_eval.targets import SCALE_TARGETS
from amocna_cli.utils.ui import run, run_capture
from amocna_cli.utils.shell import (
    k8s_scale,
    k8s_rollout_restart,
    k8s_get_jsonpath,
)


def _scale_target_from_env() -> dict:
    ns = os.environ.get("AMOCNA_SCALE_NAMESPACE", "sock-shop")
    for target in SCALE_TARGETS:
        if target["namespace"] == ns:
            override = dict(target)
            if os.environ.get("AMOCNA_SCALE_DEPLOYMENT"):
                override["deployment"] = os.environ["AMOCNA_SCALE_DEPLOYMENT"]
            if os.environ.get("AMOCNA_LOCUST_HOST"):
                override["locust_host"] = os.environ["AMOCNA_LOCUST_HOST"]
            if os.environ.get("AMOCNA_SPIKE_USERS"):
                override["spike_users"] = int(os.environ["AMOCNA_SPIKE_USERS"])
            if os.environ.get("AMOCNA_BASELINE_USERS"):
                override["baseline_users"] = int(os.environ["AMOCNA_BASELINE_USERS"])
            return override
    return SCALE_TARGETS[0]


@ScenarioRegistry.register
class Scenario1(Scenario):
    id = "1"
    name = "Horizontal Scaling Remediation (Green Path)"
    allowed_intents = ["HorizontalScalingUpIntent", "HorizontalScalingDownIntent"]

    def __init__(self, cfg):
        super().__init__(cfg)
        self.target = _scale_target_from_env()

    def initialize(self) -> None:
        from amocna_cli.commands.benchmark import run_sparql, _load_sparql_query

        run_sparql(self.cfg, _load_sparql_query(self.cfg, "clean-anomalies.sparql"))
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "clean-actions.sparql"))

    def setup_baseline(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load

        t = self.target
        set_locust_load(
            t["baseline_users"],
            10,
            host=t["locust_host"],
            locust_namespace=t["locust_namespace"],
        )
        run(k8s_scale("default", "cluster-stress", 1), check=False)
        run(k8s_scale(t["namespace"], t["deployment"], 1), check=False)

    def trigger_anomaly(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load

        t = self.target
        self.logger.log(
            "TRIGGER_ANOMALY",
            f"Spiking Locust to {t['spike_users']} users on {t['locust_host']}",
        )
        set_locust_load(
            t["spike_users"],
            t["spawn_rate"],
            host=t["locust_host"],
            locust_namespace=t["locust_namespace"],
        )

    def observe_remediation(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load

        t = self.target
        ns, dep = t["namespace"], t["deployment"]
        start_obs = time.time()
        remediation_detected = False
        while time.time() - start_obs < 360:
            replicas = run_capture(
                k8s_get_jsonpath(ns, "deployment", dep, "{.status.readyReplicas}"),
                check=False,
            )
            if replicas == str(t["target_replicas"]) and not remediation_detected:
                self.logger.log(
                    "REMEDIATION_DETECTED",
                    f"{ns}/{dep} successfully scaled to {t['target_replicas']} replicas",
                )
                self.logger.log(
                    "TRAFFIC_REBALANCE",
                    "Restarting Locust workers to balance traffic...",
                )
                run(
                    k8s_rollout_restart(
                        "deployment/locust-worker", namespace=t["locust_namespace"]
                    ),
                    check=False,
                )
                time.sleep(30)
                self.logger.log("RESUME_TRAFFIC", "Re-triggering Locust swarm")
                set_locust_load(
                    t["spike_users"],
                    t["spawn_rate"],
                    host=t["locust_host"],
                    locust_namespace=t["locust_namespace"],
                )
                remediation_detected = True
                break
            time.sleep(10)

        self.logger.log("SCALE_DOWN_TRIGGER", "Reducing Locust back to baseline users")
        set_locust_load(
            t["baseline_users"],
            10,
            host=t["locust_host"],
            locust_namespace=t["locust_namespace"],
        )

        self.logger.log("SCALE_DOWN_OBSERVATION", "Monitoring scale down to 1 replica")
        start_sd = time.time()
        while time.time() - start_sd < 360:
            replicas = run_capture(
                k8s_get_jsonpath(ns, "deployment", dep, "{.status.readyReplicas}"),
                check=False,
            )
            if replicas == "1":
                self.logger.log(
                    "SCALE_DOWN_DETECTED",
                    f"{ns}/{dep} successfully scaled back to 1 replica",
                )
                break
            time.sleep(10)

    def cleanup(self) -> None:
        from amocna_cli.commands.benchmark import stop_locust

        t = self.target
        stop_locust(t["locust_namespace"])
        run(k8s_scale("default", "cluster-stress", 0), check=False)
        run(k8s_scale(t["namespace"], t["deployment"], 1), check=False)
