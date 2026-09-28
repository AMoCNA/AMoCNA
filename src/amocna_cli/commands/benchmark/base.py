from __future__ import annotations
import time
import datetime
import json
import abc
from amocna_cli.config import ProjectConfig
from amocna_cli.utils.ui import console, info

class EventLogger:
    def __init__(self, scenario_id: str):
        self.scenario_id = scenario_id
        self.start_time = time.time()
        self.start_iso = datetime.datetime.now().isoformat()
        self.events = []
        self.log_file = f"benchmark_log_{scenario_id}_{int(self.start_time)}.json"

    def log(self, event_type: str, description: str):
        elapsed = time.time() - self.start_time
        timestamp = datetime.datetime.now().strftime("%H:%M:%S")
        self.events.append(
            {
                "timestamp_s": round(elapsed, 2),
                "wall_clock": timestamp,
                "type": event_type,
                "description": description,
            }
        )
        console.print(
            f"[[bold cyan]{elapsed:06.1f}s[/bold cyan]] [bold]{event_type}[/bold]: {description}"
        )

    def save(self):
        data = {
            "scenario_id": self.scenario_id,
            "start_iso": self.start_iso,
            "total_duration": round(time.time() - self.start_time, 2),
            "events": self.events,
        }
        with open(self.log_file, "w") as f:
            json.dump(data, f, indent=2)
        info(f"Event log saved to {self.log_file}")

class Scenario(abc.ABC):
    def __init__(self, cfg: ProjectConfig):
        self.cfg = cfg
        self.logger = EventLogger(self.id)

    @property
    @abc.abstractmethod
    def id(self) -> str:
        """The scenario ID (e.g. '1', '2')."""
        pass

    @property
    @abc.abstractmethod
    def name(self) -> str:
        """The descriptive name of the scenario."""
        pass

    @property
    @abc.abstractmethod
    def allowed_intents(self) -> list[str]:
        """List of intents allowed for this scenario in Palamedes."""
        pass

    def run(self, keep_on_failure: bool = False) -> None:
        """Template method defining the scenario lifecycle."""
        try:
            from amocna_cli.commands.benchmark import set_palamedes_filter
            from amocna_cli.utils.ui import header

            header(f"Scenario {self.id}: {self.name}")
            
            self.logger.log("INIT", "Cleaning up stale resources and states...")
            set_palamedes_filter(self.allowed_intents, logger=self.logger)
            self.initialize()
            time.sleep(15) # Sync wait

            self.setup_baseline()
            self.logger.log("BASELINE", "Monitoring baseline for 120s")
            time.sleep(120)

            self.trigger_anomaly()
            
            self.observe_remediation()

            self.logger.log("STABILIZATION", "Monitoring post-fix stability for 120s")
            time.sleep(120)

            self.logger.log("END_SCENARIO", f"Scenario {self.id} completed")
            self.logger.save()
            self.cleanup()
        except Exception as e:
            self.logger.log("FAILURE", f"Scenario failed: {str(e)}")
            self.logger.save()
            if not keep_on_failure:
                self.cleanup()
            raise

    @abc.abstractmethod
    def initialize(self) -> None:
        """Clean up and prepare for the scenario."""
        pass

    @abc.abstractmethod
    def setup_baseline(self) -> None:
        """Establish the initial stable state."""
        pass

    @abc.abstractmethod
    def trigger_anomaly(self) -> None:
        """Inject the fault or condition to be remediated."""
        pass

    @abc.abstractmethod
    def observe_remediation(self) -> None:
        """Poll and wait for the expected remediation to occur."""
        pass

    @abc.abstractmethod
    def cleanup(self) -> None:
        """Return the system to a clean state."""
        pass
