import time
from amocna_cli.commands.benchmark.base import Scenario
from amocna_cli.commands.benchmark.registry import ScenarioRegistry
from amocna_cli.utils.ui import run, run_capture, info, console
from amocna_cli.utils.shell import (
    k8s_scale,
    k8s_get_jsonpath,
    k8s_get_pods_jsonpath,
)
from amocna_cli.commands.benchmark import (
    S6_NAMESPACE, S6_SECRET_NAME, S6_FAILING_DEPLOY, S6_SIBLING_DEPLOY,
    S6_WORKLOADS_MANIFEST, _cleanup_s7_benchmark, _require_registry_credentials,
    _s7_private_image, _s7_check_runtime_prerequisites, _load_k8s_manifest,
    _apply_manifest_stdin, _s7_failing_pod_snapshots, _s7_wait_for_sibling_running,
    _create_docker_registry_secret, _s7_print_timeout_diagnostics
)

@ScenarioRegistry.register
class Scenario7(Scenario):
    id = "7"
    name = "Registry Credential Remediation (Red Path)"
    allowed_intents = ["AddImagePullSecretIntent"]

    def run(self, keep_on_failure: bool = False) -> None:
        """Override run for Scenario 7 as it has a very different flow."""
        try:
            from amocna_cli.commands.benchmark import set_palamedes_filter
            from amocna_cli.utils.ui import header
            header(f"Scenario {self.id}: {self.name}")

            self.logger.log("PRECHECK", "Checking service versions...")
            if not _s7_check_runtime_prerequisites(self.cfg):
                return

            self.logger.log("INIT", "Cleaning up previous resources...")
            set_palamedes_filter(self.allowed_intents, logger=self.logger)
            _cleanup_s7_benchmark(self.cfg)

            self.initialize()
            self.setup_baseline()
            self.trigger_anomaly()
            self.observe_remediation()
            
            self.logger.log("END_SCENARIO", f"Scenario {self.id} completed")
            self.logger.save()
            _cleanup_s7_benchmark(self.cfg)
        except Exception as e:
            self.logger.log("FAILURE", f"Scenario failed: {str(e)}")
            self.logger.save()
            if not keep_on_failure:
                _cleanup_s7_benchmark(self.cfg)
            raise

    def initialize(self) -> None:
        user, pat = _require_registry_credentials()
        self.logger.log("STEP_2", f"Creating docker-registry secret '{S6_SECRET_NAME}'")
        _create_docker_registry_secret(S6_NAMESPACE, S6_SECRET_NAME, user, pat)
        
        secret_type = run_capture(
            ["kubectl", "get", "secret", S6_SECRET_NAME, "-n", S6_NAMESPACE, "-o", "jsonpath={.type}"],
            check=False
        )
        if secret_type != "kubernetes.io/dockerconfigjson":
            raise RuntimeError(f"Secret verification failed: {secret_type}")
        self.logger.log("STEP_2_DONE", "Secret created and verified")

    def setup_baseline(self) -> None:
        private_image = _s7_private_image(self.cfg)
        self.logger.log("STEP_3", "Deploying workloads...")
        manifest = _load_k8s_manifest(self.cfg, S6_WORKLOADS_MANIFEST, private_image=private_image)
        _apply_manifest_stdin(manifest)

    def trigger_anomaly(self) -> None:
        self.logger.log("STEP_4", "Waiting for failing pod ImagePullBackOff...")
        pull_backoff = False
        for i in range(18):
            time.sleep(5)
            snapshots = _s7_failing_pod_snapshots()
            active = [s for s in snapshots if not s[3]]
            pull_failure = next((s for s in active if s[2] in ("ImagePullBackOff", "ErrImagePull")), None)
            if pull_failure:
                self.logger.log("PULL_FAILURE_DETECTED", f"Failing pod pull error: {pull_failure[2]}")
                pull_backoff = True
                break
            if active and all(s[1] == "Running" for s in active):
                self.logger.log("NO_PULL_FAILURE", "Pods reached Running (likely cache hit)")
                return

        if not pull_backoff:
            raise RuntimeError("Failing workload did not reach ImagePullBackOff within 90s")

        self.logger.log("STEP_5", "Scaling sibling to provide valid pull secret source...")
        run(k8s_scale(S6_NAMESPACE, S6_SIBLING_DEPLOY, 1), check=False)
        if not _s7_wait_for_sibling_running(self.logger):
            _s7_print_timeout_diagnostics()
            raise RuntimeError("Sibling workload did not become Running within 240s")

    def observe_remediation(self) -> None:
        self.logger.log("STEP_6", "Polling for autonomic patch and recovery...")
        start_time = time.time()
        patched = healed = False
        for i in range(24):
            time.sleep(5)
            secret = run_capture(
                k8s_get_jsonpath(S6_NAMESPACE, "deployment", S6_FAILING_DEPLOY, "{.spec.template.spec.imagePullSecrets[0].name}"),
                check=False
            )
            phase = run_capture(
                k8s_get_pods_jsonpath(S6_NAMESPACE, "app=s6-failing", "{.items[0].status.phase}"),
                check=False
            )
            if secret == S6_SECRET_NAME: patched = True
            if phase == "Running": healed = True
            if patched and healed:
                self.logger.log("REMEDIATION_DETECTED", f"Deployment patched and pod is Running in {time.time()-start_time:.1f}s")
                return
            console.print(f"    secret={secret or '(none)'}, phase={phase or 'unknown'} ({i*5}s)")

        _s7_print_timeout_diagnostics()
        if not patched: raise RuntimeError("Autonomic loop did not patch imagePullSecrets")
        if not healed: raise RuntimeError("Deployment patched but pod not Running")

    def cleanup(self) -> None:
        # Managed by run override
        pass
