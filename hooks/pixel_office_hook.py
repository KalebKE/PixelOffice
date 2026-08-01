#!/usr/bin/env python3
"""Fail-open Claude Code and Codex hook bridge for Pixel Office."""

from __future__ import annotations

import argparse
import json
import os
import socket
import sys
import time
from typing import Any

PROTOCOL_VERSION = 1
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 9997

CODE_TOOLS = {"apply_patch", "edit", "write", "notebookedit"}
PLAN_TOOLS = {"update_plan", "enterplanmode", "plan"}
WAIT_TOOLS = {"request_user_input", "askuserquestion"}
READ_TOOLS = {
    "read",
    "glob",
    "grep",
    "websearch",
    "webfetch",
    "find",
    "open",
    "screenshot",
    "view_image",
}

TEST_MARKERS = (
    " test",
    "test ",
    "pytest",
    "jest",
    "vitest",
    "rspec",
    "xctest",
    "gradlew test",
    "gradle test",
)
BUILD_MARKERS = (
    " build",
    "build ",
    "compile",
    "assemble",
    "xcodebuild",
    "cargo build",
)
COMMIT_MARKERS = ("git commit", "git push", "gh pr create", "gh pr merge")
INSTALL_MARKERS = (
    "npm install",
    "yarn add",
    "pnpm add",
    "pip install",
    "cargo add",
    "go get",
    "bundle install",
)


def classify_command(command: str) -> str:
    normalized = f" {command.lower()} "
    if any(marker in normalized for marker in TEST_MARKERS):
        return "test"
    if any(marker in normalized for marker in BUILD_MARKERS):
        return "build"
    if any(marker in normalized for marker in COMMIT_MARKERS):
        return "commit"
    if any(marker in normalized for marker in INSTALL_MARKERS):
        return "install"
    return "command"


def classify_tool(tool_name: str, tool_input: Any, permission_mode: str) -> tuple[str, str]:
    name = tool_name.lower()
    if name in WAIT_TOOLS or any(name.endswith(value) for value in WAIT_TOOLS):
        return "waiting", "user_input"
    if permission_mode == "plan" or name in PLAN_TOOLS or any(
        name.endswith(value) for value in PLAN_TOOLS
    ):
        return "planning", "plan"
    if name in CODE_TOOLS or any(name.endswith(value) for value in CODE_TOOLS):
        return "coding", "edit"
    if name in READ_TOOLS or any(name.endswith(value) for value in READ_TOOLS):
        return "researching", "read"
    if name == "bash" or name.endswith("exec_command") or name.endswith("shell"):
        command = ""
        if isinstance(tool_input, dict):
            candidate = tool_input.get("command", tool_input.get("cmd", ""))
            if isinstance(candidate, str):
                command = candidate
        return "running", classify_command(command)
    if "read" in name or "search" in name or "list" in name:
        return "researching", "read"
    return "running", "tool"


def structured_success(response: Any) -> bool | None:
    if not isinstance(response, dict):
        return None
    for key in ("exit_code", "exitCode", "status_code", "statusCode"):
        value = response.get(key)
        if isinstance(value, int):
            return value == 0
    for key in ("success", "ok"):
        value = response.get(key)
        if isinstance(value, bool):
            return value
    for key in ("is_error", "isError"):
        value = response.get(key)
        if isinstance(value, bool):
            return not value
    for key in ("metadata", "_meta"):
        nested = structured_success(response.get(key))
        if nested is not None:
            return nested
    return None


def map_hook(payload: dict[str, Any], provider: str, occurred_at: int | None = None) -> dict[str, Any] | None:
    event_name = payload.get("hook_event_name")
    session_id = payload.get("session_id")
    if not isinstance(event_name, str) or not isinstance(session_id, str) or not session_id:
        return None

    cwd_value = payload.get("cwd")
    cwd = cwd_value if isinstance(cwd_value, str) and cwd_value else os.getcwd()
    project_id = os.path.realpath(cwd)
    project_label = os.path.basename(project_id.rstrip(os.sep)) or project_id
    agent_value = payload.get("agent_id")
    agent_id = agent_value if isinstance(agent_value, str) and agent_value else "main"
    permission_value = payload.get("permission_mode")
    permission_mode = permission_value if isinstance(permission_value, str) else "default"

    kind = "touch"
    state: str | None = None
    activity: str | None = None

    if event_name == "SessionStart":
        if payload.get("source") != "compact":
            kind, state = "upsert", "idle"
    elif event_name == "SessionEnd":
        kind = "end"
    elif event_name == "UserPromptSubmit":
        kind = "upsert"
        state = "planning" if permission_mode == "plan" else "thinking"
    elif event_name == "PermissionRequest":
        kind, state, activity = "upsert", "waiting", "permission"
    elif event_name == "PreToolUse":
        tool_name = str(payload.get("tool_name", ""))
        state, activity = classify_tool(tool_name, payload.get("tool_input"), permission_mode)
        kind = "upsert"
    elif event_name == "PostToolUse":
        tool_name = str(payload.get("tool_name", ""))
        _, tool_activity = classify_tool(tool_name, payload.get("tool_input"), permission_mode)
        # Claude resumes model reasoning after a tool completes. Without this
        # transition Pixel Office remains stuck in the preceding tool state
        # because the terminal spinner itself does not produce a hook event.
        kind, state, activity = "upsert", "thinking", "post_tool"
        if tool_activity in {"test", "build"}:
            success = structured_success(payload.get("tool_response"))
            if success is not None:
                state = "success" if success else "failure"
                activity = tool_activity
    elif event_name == "PostToolUseFailure":
        kind, state = "upsert", "failure"
        tool_name = str(payload.get("tool_name", ""))
        _, activity = classify_tool(tool_name, payload.get("tool_input"), permission_mode)
    elif event_name == "Stop":
        kind, state = "upsert", "idle"
    elif event_name == "SubagentStart":
        kind, state, activity = "upsert", "thinking", "subagent"
    elif event_name == "SubagentStop":
        kind, activity = "end", "subagent"
    else:
        return None

    event: dict[str, Any] = {
        "version": PROTOCOL_VERSION,
        "provider": provider,
        "sessionId": session_id,
        "agentId": agent_id,
        "projectId": project_id,
        "projectLabel": project_label,
        "kind": kind,
        "occurredAt": occurred_at if occurred_at is not None else time.time_ns() // 1_000_000,
    }
    if state is not None:
        event["state"] = state
    if activity is not None:
        event["activity"] = activity
    return event


def emit(event: dict[str, Any], host: str, port: int) -> None:
    packet = json.dumps(event, separators=(",", ":")).encode("utf-8")
    if len(packet) > 8192:
        return
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sender:
        sender.sendto(packet, (host, port))


def main() -> int:
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--provider", required=True, choices=("claude", "codex"))
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    args = parser.parse_args()

    try:
        payload = json.load(sys.stdin)
        if isinstance(payload, dict):
            event = map_hook(payload, args.provider)
            if event is not None:
                emit(event, args.host, args.port)
    except Exception:
        # Pixel Office is ambient UI. It must never interrupt the agent loop.
        pass
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
