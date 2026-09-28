import time
from amocna_cli.commands.benchmark.base import Scenario
from amocna_cli.commands.benchmark.registry import ScenarioRegistry
from amocna_cli.utils.ui import run, run_capture
from amocna_cli.utils.shell import (
    k8s_scale,
    k8s_set_image,
    k8s_get_jsonpath,
)
from amocna_cli.commands.benchmark import SOCK_SHOP_FRONTEND_IMAGE, SOCK_SHOP_FRONTEND_VULNERABLE_TAG, SOCK_SHOP_FRONTEND_PATCHED_TAG

@ScenarioRegistry.register
class Scenario5(Scenario):
    id = "5"
    name = "Vulnerability Remediation (Blue Path - Image Change)"
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
        self.logger.log("TRIGGER_ANOMALY", f"Resetting front-end to vulnerable image {SOCK_SHOP_FRONTEND_VULNERABLE_TAG}")
        run(
            k8s_set_image(
                "sock-shop",
                "front-end",
                f"front-end={SOCK_SHOP_FRONTEND_IMAGE}:{SOCK_SHOP_FRONTEND_VULNERABLE_TAG}",
            ),
            check=False,
        )

    def observe_remediation(self) -> None:
        start_obs = time.time()
        remediation_detected = False
        while time.time() - start_obs < 360:
            image = run_capture(
                k8s_get_jsonpath("sock-shop", "deployment", "front-end", "{.spec.template.spec.containers[0].image}"),
                check=False,
            )
            if SOCK_SHOP_FRONTEND_PATCHED_TAG in image and not remediation_detected:
                self.logger.log("REMEDIATION_DETECTED", f"Frontend patched to {SOCK_SHOP_FRONTEND_PATCHED_TAG}")
                remediation_detected = True
                break
            time.sleep(10)

    def cleanup(self) -> None:
        run(k8s_scale("default", "cluster-stress", 0), check=False)
