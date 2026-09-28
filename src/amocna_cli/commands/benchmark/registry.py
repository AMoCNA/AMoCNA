from __future__ import annotations
from typing import Dict, Type
from amocna_cli.config import ProjectConfig
from amocna_cli.commands.benchmark.base import Scenario

class ScenarioRegistry:
    _scenarios: Dict[str, Type[Scenario]] = {}

    @classmethod
    def register(cls, scenario_class: Type[Scenario]):
        cls._scenarios[scenario_class.id] = scenario_class
        return scenario_class

    @classmethod
    def get(cls, scenario_id: str, cfg: ProjectConfig) -> Scenario:
        if scenario_id not in cls._scenarios:
            raise ValueError(f"Scenario {scenario_id} not found in registry.")
        return cls._scenarios[scenario_id](cfg)

    @classmethod
    def list_ids(cls) -> list[str]:
        return sorted(list(cls._scenarios.keys()))
