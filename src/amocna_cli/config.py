from __future__ import annotations

import sys
from pathlib import Path
from typing import Any, Optional
import yaml
from pydantic import BaseModel, Field, ValidationError

from amocna_cli.utils.ui import error

class ProjectInfo(BaseModel):
    name: str = "amocna"
    group_id: str = "com.kubiki"
    parent_pom: str = "pom.xml"
    registry: str = "ghcr.io/amocna-kr"

class AppDef(BaseModel):
    name: str
    path: str
    app_type: str = Field(..., alias="type")
    dockerfile: Optional[str] = None
    image_name: Optional[str] = None
    ports: dict[str, int] = Field(default_factory=dict)
    description: str = ""
    is_core: bool = False

    model_config = {
        "populate_by_name": True
    }

class ForwardDef(BaseModel):
    name: str
    namespace: str = "default"
    service: Optional[str] = None
    pod_label: Optional[str] = None
    local_port: int = 8080
    remote_port: int = 8080

class K8sClusterResource(BaseModel):
    kind: str
    name: str

class GraphDBStorage(BaseModel):
    host_path: str = "/data/graphdb"
    node_hostname: str = "kube-worker-0"

class K8sConfig(BaseModel):
    deploy_order: list[str] = Field(default_factory=list)
    undeploy_namespaces: list[str] = Field(default_factory=list)
    cluster_resources: list[K8sClusterResource] = Field(default_factory=list)
    graphdb_storage: GraphDBStorage = Field(default_factory=GraphDBStorage)

class ProjectConfig(BaseModel):
    project: ProjectInfo = Field(default_factory=ProjectInfo)
    core_apps: dict[str, dict[str, Any]] = Field(default_factory=dict)
    standalone_apps: dict[str, dict[str, Any]] = Field(default_factory=dict)
    forward: dict[str, dict[str, Any]] = Field(default_factory=dict)
    k8s: K8sConfig = Field(default_factory=K8sConfig)

    # Processed fields (excluded from dump and model fields)
    apps: dict[str, AppDef] = Field(default_factory=dict, exclude=True)
    forwards: dict[str, ForwardDef] = Field(default_factory=dict, exclude=True)
    project_root: Optional[Path] = Field(None, exclude=True)

    def model_post_init(self, __context: Any) -> None:
        # Populate processed apps and forwards fields after loading raw dicts
        for name, data in self.core_apps.items():
            if not isinstance(data, dict):
                continue
            image_name = data.get("image_name") or name
            app_data = data.copy()
            app_data["image_name"] = image_name
            self.apps[name] = AppDef(name=name, is_core=True, **app_data)

        for name, data in self.standalone_apps.items():
            if not isinstance(data, dict):
                continue
            image_name = data.get("image_name") or name
            app_data = data.copy()
            app_data["image_name"] = image_name
            self.apps[name] = AppDef(name=name, is_core=False, **app_data)

        for name, data in self.forward.items():
            if not isinstance(data, dict):
                continue
            fwd_data = data.copy()
            if "service" not in fwd_data and "pod_label" not in fwd_data:
                fwd_data["service"] = name
            self.forwards[name] = ForwardDef(name=name, **fwd_data)

    @property
    def name(self) -> str:
        return self.project.name

    @property
    def group_id(self) -> str:
        return self.project.group_id

    @property
    def parent_pom(self) -> str:
        return self.project.parent_pom

    @property
    def registry(self) -> str:
        return self.project.registry

    @property
    def k8s_deploy_order(self) -> list[str]:
        return self.k8s.deploy_order

    @property
    def k8s_undeploy_namespaces(self) -> list[str]:
        return self.k8s.undeploy_namespaces

    @property
    def k8s_cluster_resources(self) -> list[dict[str, str]]:
        return [{"kind": r.kind, "name": r.name} for r in self.k8s.cluster_resources]

    @property
    def graphdb_host_path(self) -> str:
        return self.k8s.graphdb_storage.host_path

    @property
    def graphdb_node_hostname(self) -> str:
        return self.k8s.graphdb_storage.node_hostname

def find_project_root() -> Path:
    """Walk up from CWD or script dir to find amocna.yaml."""
    candidates = [Path.cwd(), Path(__file__).resolve().parent]
    for start in candidates:
        p = start
        while p != p.parent:
            if (p / "amocna.yaml").is_file():
                return p
            p = p.parent
    error("Cannot find amocna.yaml — run from the project root or its subdirectory.")
    sys.exit(1)

def load_config(root: Path) -> ProjectConfig:
    yaml_path = root / "amocna.yaml"
    if not yaml_path.is_file():
        error(f"amocna.yaml not found at {yaml_path}")
        sys.exit(1)

    try:
        raw_data = yaml.safe_load(yaml_path.read_text()) or {}
    except Exception as e:
        error(f"Error parsing amocna.yaml: {e}")
        sys.exit(1)

    try:
        cfg = ProjectConfig(**raw_data)
        cfg.project_root = root
        return cfg
    except ValidationError as e:
        error("Configuration validation failed in amocna.yaml:")
        for err in e.errors():
            loc = " -> ".join(str(x) for x in err["loc"])
            error(f"  [{loc}]: {err['msg']} (input: {err.get('input')})")
        sys.exit(1)
