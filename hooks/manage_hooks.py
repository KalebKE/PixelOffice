#!/usr/bin/env python3
"""Install, inspect, or remove Pixel Office user-level agent hooks."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
import tempfile
import time
from pathlib import Path
from typing import Any

MARKER = "pixel_office_hook.py"
COMMON_EVENTS = (
    "SessionStart",
    "SessionEnd",
    "UserPromptSubmit",
    "PreToolUse",
    "PermissionRequest",
    "PostToolUse",
    "Stop",
    "SubagentStart",
    "SubagentStop",
)
PROVIDER_EVENTS = {
    "claude": COMMON_EVENTS + ("PostToolUseFailure",),
    "codex": COMMON_EVENTS,
}


def load_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return value


def atomic_write(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        backup = path.with_name(f"{path.name}.bak-{time.time_ns()}")
        shutil.copy2(path, backup)
    rendered = json.dumps(value, indent=2, sort_keys=False) + "\n"
    with tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", dir=path.parent, delete=False
    ) as handle:
        handle.write(rendered)
        temporary = Path(handle.name)
    os.replace(temporary, path)


def owned(handler: Any, provider: str | None = None) -> bool:
    if not isinstance(handler, dict):
        return False
    command = handler.get("command")
    if not isinstance(command, str) or MARKER not in command:
        return False
    return provider is None or f"--provider {provider}" in command


def install_into(config: dict[str, Any], provider: str, command: str) -> bool:
    hooks = config.setdefault("hooks", {})
    if not isinstance(hooks, dict):
        raise ValueError("The existing hooks setting must be a JSON object")
    changed = False
    for event in PROVIDER_EVENTS[provider]:
        groups = hooks.setdefault(event, [])
        if not isinstance(groups, list):
            raise ValueError(f"hooks.{event} must be an array")
        if any(
            owned(handler, provider)
            for group in groups
            if isinstance(group, dict)
            for handler in group.get("hooks", [])
        ):
            continue
        groups.append(
            {
                "hooks": [
                    {
                        "type": "command",
                        "command": command,
                        "timeout": 1,
                    }
                ]
            }
        )
        changed = True
    return changed


def uninstall_from(config: dict[str, Any], provider: str) -> bool:
    hooks = config.get("hooks")
    if not isinstance(hooks, dict):
        return False
    changed = False
    for event in list(hooks):
        groups = hooks[event]
        if not isinstance(groups, list):
            continue
        retained_groups = []
        for group in groups:
            if not isinstance(group, dict):
                retained_groups.append(group)
                continue
            handlers = group.get("hooks")
            if not isinstance(handlers, list):
                retained_groups.append(group)
                continue
            retained_handlers = [handler for handler in handlers if not owned(handler, provider)]
            if len(retained_handlers) != len(handlers):
                changed = True
            if retained_handlers:
                copied = dict(group)
                copied["hooks"] = retained_handlers
                retained_groups.append(copied)
        if retained_groups:
            hooks[event] = retained_groups
        elif event in hooks:
            del hooks[event]
    if not hooks:
        config.pop("hooks", None)
    return changed


def count_owned(config: dict[str, Any], provider: str) -> int:
    hooks = config.get("hooks")
    if not isinstance(hooks, dict):
        return 0
    return sum(
        1
        for groups in hooks.values()
        if isinstance(groups, list)
        for group in groups
        if isinstance(group, dict)
        for handler in group.get("hooks", [])
        if owned(handler, provider)
    )


def config_paths(home: Path) -> dict[str, Path]:
    return {
        "claude": home / ".claude" / "settings.json",
        "codex": home / ".codex" / "hooks.json",
    }


def emitter_destination(home: Path) -> Path:
    return home / ".local" / "share" / "pixel-office" / "pixel_office_hook.py"


def command_for(destination: Path, provider: str, host: str, port: int) -> str:
    quoted = "'" + str(destination).replace("'", "'\"'\"'") + "'"
    return (
        f"/usr/bin/env python3 {quoted} --provider {provider} "
        f"--host {host} --port {port}"
    )


def install(home: Path, host: str, port: int) -> None:
    paths = config_paths(home)
    configs = {provider: load_json(path) for provider, path in paths.items()}
    destination = emitter_destination(home)
    source = Path(__file__).with_name("pixel_office_hook.py")

    changed_configs: list[tuple[Path, dict[str, Any]]] = []
    for provider, config in configs.items():
        command = command_for(destination, provider, host, port)
        if install_into(config, provider, command):
            changed_configs.append((paths[provider], config))

    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    destination.chmod(0o755)
    for path, config in changed_configs:
        atomic_write(path, config)

    print(f"Installed emitter: {destination}")
    print("Claude Code hooks: installed")
    print("Codex hooks: installed; open /hooks in Codex and trust the new definitions")


def uninstall(home: Path) -> None:
    paths = config_paths(home)
    for provider, path in paths.items():
        config = load_json(path)
        if uninstall_from(config, provider):
            atomic_write(path, config)
            print(f"{provider}: removed Pixel Office hooks")
        else:
            print(f"{provider}: no Pixel Office hooks found")
    destination = emitter_destination(home)
    if destination.exists():
        destination.unlink()
        print(f"Removed emitter: {destination}")


def status(home: Path) -> None:
    for provider, path in config_paths(home).items():
        config = load_json(path)
        found = count_owned(config, provider)
        expected = len(PROVIDER_EVENTS[provider])
        print(f"{provider}: {found}/{expected} hooks installed ({path})")
    destination = emitter_destination(home)
    print(f"emitter: {'installed' if destination.exists() else 'missing'} ({destination})")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("install", "uninstall", "status"))
    parser.add_argument("--home", type=Path, default=Path.home(), help=argparse.SUPPRESS)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9997)
    args = parser.parse_args()

    try:
        if args.action == "install":
            install(args.home, args.host, args.port)
        elif args.action == "uninstall":
            uninstall(args.home)
        else:
            status(args.home)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"Pixel Office hook setup failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
