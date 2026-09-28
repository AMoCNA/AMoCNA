from __future__ import annotations

import os
import sys
from typing import Optional
from typing_extensions import Annotated
import typer

from amocna_cli.config import ProjectConfig, AppDef
from amocna_cli.commands.version import read_pom_version
from amocna_cli.commands.login import check_pat
from amocna_cli.utils.ui import header, info, warn, error, run
from amocna_cli.utils.shell import docker_build, docker_push

app = typer.Typer()

def resolve_registry(registry_opt: str | None, cfg: ProjectConfig) -> str:
    """Registry resolution: --registry flag > AMOCNA_REGISTRY env > amocna.yaml."""
    if registry_opt:
        return registry_opt
    env = os.environ.get("AMOCNA_REGISTRY")
    if env:
        return env
    return cfg.registry

def resolve_apps(cfg: ProjectConfig, app_name: str | None, all_apps: bool) -> list[AppDef]:
    """Resolve which apps to operate on from --app / --all flags."""
    if all_apps:
        return list(cfg.apps.values())

    if not app_name:
        error("Specify --app NAME or --all")
        error(f"Available apps: {', '.join(cfg.apps.keys())}")
        sys.exit(1)

    if app_name not in cfg.apps:
        error(f"Unknown app: {app_name}")
        error(f"Available: {', '.join(cfg.apps.keys())}")
        sys.exit(1)

    return [cfg.apps[app_name]]

def docker_build_image(
    cfg: ProjectConfig,
    app_def: AppDef,
    registry: str,
    tag: str,
    *,
    push: bool = False,
) -> str:
    """Build (and optionally push) a Docker image for an app. Returns the image ref."""
    if not app_def.dockerfile:
        error(f"No dockerfile configured for {app_def.name}")
        sys.exit(1)

    dockerfile_path = cfg.project_root / app_def.dockerfile
    if not dockerfile_path.is_file():
        error(f"Dockerfile not found: {dockerfile_path}")
        sys.exit(1)

    image = f"{registry}/{app_def.image_name}:{tag}"
    info(f"Image: {image}")
    info(f"Dockerfile: {app_def.dockerfile}")

    run(docker_build(image, str(dockerfile_path), str(cfg.project_root)))

    if push:
        info(f"Pushing {image}...")
        run(docker_push(image))

    return image

@app.callback(invoke_without_command=True)
def build_cmd(
    ctx: typer.Context,
    app_name: Annotated[Optional[str], typer.Option("--app", help="App name to build")] = None,
    all_apps: Annotated[bool, typer.Option("--all", help="Build all apps")] = False,
    push: Annotated[bool, typer.Option("--push", help="Push image after build")] = False,
    registry: Annotated[Optional[str], typer.Option(help="Docker registry (overrides config & env)")] = None,
    tag: Annotated[Optional[str], typer.Option(help="Image tag (default: version from pom.xml)")] = None,
):
    """Build Docker images for specified applications."""
    if ctx.invoked_subcommand is not None:
        return

    cfg: ProjectConfig = ctx.obj
    resolved_registry = resolve_registry(registry, cfg)
    resolved_tag = tag
    if not resolved_tag:
        resolved_tag = read_pom_version(cfg.project_root / cfg.parent_pom)
    
    apps_to_build = resolve_apps(cfg, app_name, all_apps)

    if push and "ghcr.io" in resolved_registry:
        check_pat()

    for app_def in apps_to_build:
        if not app_def.dockerfile:
            if app_def.app_type in ("maven-lib", "angular"):
                info(f"Skipping {app_def.name} ({app_def.app_type}, no Docker image)")
            else:
                warn(f"Skipping {app_def.name} (no dockerfile configured)")
            continue

        header(f"Building {app_def.name}")
        docker_build_image(cfg, app_def, resolved_registry, resolved_tag, push=push)
        info(f"{app_def.name} built successfully")
