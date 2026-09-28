from __future__ import annotations

import re
import sys
from enum import Enum
from pathlib import Path
from typing import Optional
from typing_extensions import Annotated
import typer

from amocna_cli.config import ProjectConfig
from amocna_cli.utils.ui import console, header, info, warn, error

app = typer.Typer()

class BumpPart(str, Enum):
    major = "major"
    minor = "minor"
    patch = "patch"

# ─── POM version helpers ─────────────────────────────────────────────

_VERSION_RE = re.compile(r"(<version>)(.*?)(</version>)")
_PARENT_BLOCK_RE = re.compile(r"(<parent>.*?</parent>)", re.DOTALL)
_EXCLUDE_BLOCKS_RE = re.compile(
    r"("
    r"<parent\b[^>]*>.*?</parent>|"
    r"<dependencies\b[^>]*>.*?</dependencies>|"
    r"<dependencyManagement\b[^>]*>.*?</dependencyManagement>|"
    r"<build\b[^>]*>.*?</build>|"
    r"<profiles\b[^>]*>.*?</profiles>|"
    r"<profile\b[^>]*>.*?</profile>|"
    r"<plugins\b[^>]*>.*?</plugins>|"
    r"<plugin\b[^>]*>.*?</plugin>"
    r")",
    re.DOTALL
)

def read_pom_version(pom_path: Path) -> str:
    """Read the <version> directly under <project> falling back to parent version."""
    if not pom_path.is_file():
        error(f"Could not find POM file at {pom_path}")
        sys.exit(1)
    content = pom_path.read_text()
    clean_content = _EXCLUDE_BLOCKS_RE.sub("", content)
    
    m = _VERSION_RE.search(clean_content)
    if m:
        return m.group(2)
    m_parent = _PARENT_BLOCK_RE.search(content)
    if m_parent:
        m_ver = _VERSION_RE.search(m_parent.group(1))
        if m_ver:
            return m_ver.group(2)
    error(f"Could not find <version> in {pom_path}")
    sys.exit(1)

def _set_version_in_text(text: str, new_version: str) -> str:
    """Replace the first <version>...</version> NOT inside excluded blocks."""
    parts = _EXCLUDE_BLOCKS_RE.split(text)
    found = False
    result_parts = []
    for i, part in enumerate(parts):
        if i % 2 == 1:
            result_parts.append(part)
        else:
            if not found:
                new_part, count = _VERSION_RE.subn(
                    rf"\g<1>{new_version}\g<3>", part, count=1
                )
                if count > 0:
                    found = True
                result_parts.append(new_part)
            else:
                result_parts.append(part)
    return "".join(result_parts)

def _set_parent_version_in_text(text: str, new_version: str) -> str:
    """Replace <version> inside <parent>...</parent> block."""
    def _replace_in_parent(m: re.Match) -> str:
        parent_block = m.group(1)
        return _VERSION_RE.sub(rf"\g<1>{new_version}\g<3>", parent_block, count=1)

    return _PARENT_BLOCK_RE.sub(_replace_in_parent, text)

def bump_version(current: str, part: BumpPart) -> str:
    """Bump a Maven version string like 1.0-SNAPSHOT or 1.2.3-SNAPSHOT."""
    suffix = ""
    base = current
    if "-" in current:
        base, suffix = current.split("-", 1)
        suffix = f"-{suffix}"

    segments = base.split(".")
    while len(segments) < 3:
        segments.append("0")

    if part == BumpPart.major:
        segments[0] = str(int(segments[0]) + 1)
        segments[1] = "0"
        segments[2] = "0"
    elif part == BumpPart.minor:
        segments[1] = str(int(segments[1]) + 1)
        segments[2] = "0"
    elif part == BumpPart.patch:
        segments[2] = str(int(segments[2]) + 1)
    else:
        error(f"Unknown bump part: {part}")
        sys.exit(1)

    return ".".join(segments) + suffix

def sync_infra_versions(infra_dir: Path, new_version: str, dry_run: bool = False) -> list[tuple[Path, str, str]]:
    """Scan all yaml files in infra_dir and replace ghcr.io/amocna-kr/<app>:<tag> with new_version."""
    updated = []
    pattern = re.compile(r"(image:\s*ghcr\.io/amocna-kr/[\w-]+:)([0-9a-zA-Z.-]+)")
    
    # scan both .yaml and .yml files
    for suffix in ("*.yaml", "*.yml"):
        for path in infra_dir.rglob(suffix):
            if not path.is_file():
                continue
            content = path.read_text()
            matches = pattern.findall(content)
            if not matches:
                continue
                
            needs_update = False
            old_version = None
            for prefix, tag in matches:
                if tag != new_version:
                    needs_update = True
                    old_version = tag
                    break
                    
            if needs_update:
                new_content = pattern.sub(r"\g<1>" + new_version, content)
                if not dry_run:
                    path.write_text(new_content)
                actual_old = old_version or matches[0][1]
                updated.append((path, actual_old, new_version))
                
    return updated

@app.callback(invoke_without_command=True)
def version_cmd(
    ctx: typer.Context,
    bump: Annotated[Optional[BumpPart], typer.Option(help="Bump version component")] = None,
    set_ver: Annotated[Optional[str], typer.Option("--set", help="Set an explicit version string")] = None,
    dry_run: Annotated[bool, typer.Option("--dry-run", help="Show what would change without writing")] = False,
):
    """Manage & sync versions across core POMs and infra manifests."""
    if ctx.invoked_subcommand is not None:
        return

    cfg: ProjectConfig = ctx.obj
    parent_pom = cfg.project_root / cfg.parent_pom
    current = read_pom_version(parent_pom)

    header("Version Synchronization")
    info(f"Current version: [bold]{current}[/bold]")

    # Determine new version
    if set_ver:
        new_version = set_ver
    elif bump:
        new_version = bump_version(current, bump)
    else:
        # Just show current state
        console.print(f"\n  [bold]Parent POM[/bold]: {current}")
        core_apps = [a for a in cfg.apps.values() if a.is_core]
        for app in core_apps:
            pom = cfg.project_root / app.path / "pom.xml"
            if pom.is_file():
                v = read_pom_version(pom)
                match = (
                    "[green]✔ in sync[/green]"
                    if v == current
                    else f"[red]✖ out of sync ({v})[/red]"
                )
                console.print(f"  {app.name:<20}: {match}")
        
        # Show infra manifests status
        console.print(f"\n  [bold]Infra Manifests[/bold]:")
        infra_dir = cfg.project_root / "infra"
        pattern = re.compile(r"image:\s*ghcr\.io/amocna-kr/([\w-]+):([0-9a-zA-Z.-]+)")
        infra_status = {}
        for path in sorted(infra_dir.rglob("*.yaml")) + sorted(infra_dir.rglob("*.yml")):
            if not path.is_file():
                continue
            content = path.read_text()
            matches = pattern.findall(content)
            for img_name, tag in matches:
                infra_status[img_name] = tag
                
        if infra_status:
            for img_name, tag in sorted(infra_status.items()):
                match = (
                    "[green]✔ in sync[/green]"
                    if tag == current
                    else f"[red]✖ out of sync ({tag})[/red]"
                )
                console.print(f"    {img_name:<18}: {match}")
        else:
            console.print("    No ghcr.io/amocna-kr images found in infra")
        console.print()
        return

    info(f"New version: [bold]{new_version}[/bold]")

    # Collect all POM files to update
    poms_to_update: list[tuple[str, Path]] = [("parent", parent_pom)]
    core_apps = [a for a in cfg.apps.values() if a.is_core]
    for app in core_apps:
        pom = cfg.project_root / app.path / "pom.xml"
        if pom.is_file():
            poms_to_update.append((app.name, pom))

    if dry_run:
        warn("DRY RUN — no files will be modified")
        console.print()
        for label, pom in poms_to_update:
            console.print(f"  Would update: [bold]{label}[/bold]:{label:<25} [dim]{pom}[/dim]")
        
        infra_updates = sync_infra_versions(cfg.project_root / "infra", new_version, dry_run=True)
        for path, old, new in infra_updates:
            rel_path = path.relative_to(cfg.project_root)
            console.print(f"  Would update: [bold]infra image tag[/bold]:{label:<25} [dim]{rel_path}[/dim] ({old} → {new})")
        console.print()
        return

    # Apply changes
    for label, pom in poms_to_update:
        text = pom.read_text()

        if label == "parent":
            # Update <version> under <project>
            new_text = _set_version_in_text(text, new_version)
        else:
            # Update <parent><version> and module's own <version>
            new_text = _set_parent_version_in_text(text, new_version)
            new_text = _set_version_in_text(new_text, new_version)

        if new_text != text:
            pom.write_text(new_text)
            info(f"Updated {label}: {pom.name}")
        else:
            warn(f"No change needed for {label}")

    # Apply infra updates
    infra_updates = sync_infra_versions(cfg.project_root / "infra", new_version, dry_run=False)
    for path, old, new in infra_updates:
        rel_path = path.relative_to(cfg.project_root)
        info(f"Updated infra image tag in {rel_path}: {old} → {new}")

    console.print()
    info(f"All core POMs and infra manifests synchronized to [bold]{new_version}[/bold]")
    console.print(
        f"  [dim]Tip: run[/dim] ./amocna.py version [dim]to verify sync state[/dim]"
    )
    console.print()
