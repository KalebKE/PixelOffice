from __future__ import annotations

import json
import socket
import unittest

from hooks.pixel_office_hook import classify_command, emit, map_hook


class PixelOfficeHookTest(unittest.TestCase):
    def payload(self, event: str, **extra: object) -> dict[str, object]:
        value: dict[str, object] = {
            "hook_event_name": event,
            "session_id": "session-1",
            "cwd": "/tmp/example",
            "permission_mode": "default",
        }
        value.update(extra)
        return value

    def test_lifecycle_mapping(self) -> None:
        cases = (
            (self.payload("SessionStart", source="startup"), "upsert", "idle"),
            (self.payload("SessionStart", source="compact"), "touch", None),
            (self.payload("UserPromptSubmit"), "upsert", "thinking"),
            (
                self.payload("UserPromptSubmit", permission_mode="plan"),
                "upsert",
                "planning",
            ),
            (self.payload("PermissionRequest"), "upsert", "waiting"),
            (self.payload("Stop"), "upsert", "idle"),
            (self.payload("SessionEnd"), "end", None),
            (
                self.payload("SubagentStart", agent_id="worker-1"),
                "upsert",
                "thinking",
            ),
            (self.payload("SubagentStop", agent_id="worker-1"), "end", None),
        )
        for payload, expected_kind, expected_state in cases:
            with self.subTest(payload=payload):
                event = map_hook(payload, "codex", occurred_at=123)
                self.assertIsNotNone(event)
                assert event is not None
                self.assertEqual(expected_kind, event["kind"])
                self.assertEqual(expected_state, event.get("state"))
                self.assertNotIn("role", event)

    def test_tool_mapping_and_structured_results(self) -> None:
        edit = map_hook(
            self.payload(
                "PreToolUse",
                tool_name="apply_patch",
                tool_input={"command": "secret patch body"},
            ),
            "codex",
            occurred_at=1,
        )
        self.assertEqual("coding", edit["state"])
        self.assertEqual("edit", edit["activity"])

        read = map_hook(
            self.payload("PreToolUse", tool_name="Read", tool_input={"file_path": "secret"}),
            "claude",
            occurred_at=2,
        )
        self.assertEqual("researching", read["state"])

        read_finished = map_hook(
            self.payload("PostToolUse", tool_name="Read", tool_input={"file_path": "secret"}),
            "claude",
            occurred_at=3,
        )
        self.assertEqual(("thinking", "post_tool"), (read_finished["state"], read_finished["activity"]))

        test_start = self.payload(
            "PreToolUse",
            tool_name="Bash",
            tool_input={"command": "./gradlew test --password secret"},
        )
        running = map_hook(test_start, "claude", occurred_at=4)
        self.assertEqual(("running", "test"), (running["state"], running["activity"]))

        test_finished = dict(test_start)
        test_finished["hook_event_name"] = "PostToolUse"
        test_finished["tool_response"] = {"exit_code": 0, "output": "secret output"}
        success = map_hook(test_finished, "codex", occurred_at=5)
        self.assertEqual("success", success["state"])

        test_finished["tool_response"] = {"exit_code": 1}
        failure = map_hook(test_finished, "codex", occurred_at=6)
        self.assertEqual("failure", failure["state"])

    def test_packets_do_not_forward_sensitive_hook_fields(self) -> None:
        event = map_hook(
            self.payload(
                "PreToolUse",
                prompt="private prompt",
                tool_name="Bash",
                tool_input={"command": "echo private-command"},
                tool_response={"output": "private-output"},
            ),
            "claude",
            occurred_at=6,
        )
        rendered = json.dumps(event)
        self.assertNotIn("private prompt", rendered)
        self.assertNotIn("private-command", rendered)
        self.assertNotIn("private-output", rendered)

    def test_udp_emit(self) -> None:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as receiver:
            receiver.bind(("127.0.0.1", 0))
            receiver.settimeout(1)
            event = map_hook(self.payload("Stop"), "codex", occurred_at=7)
            assert event is not None
            emit(event, "127.0.0.1", receiver.getsockname()[1])
            packet, _ = receiver.recvfrom(8192)
        self.assertEqual(event, json.loads(packet))

    def test_command_classification_uses_command_metadata(self) -> None:
        self.assertEqual("test", classify_command("./gradlew test"))
        self.assertEqual("build", classify_command("xcodebuild build"))
        self.assertEqual("commit", classify_command("git commit -m message"))
        self.assertEqual("install", classify_command("npm install"))
        self.assertEqual("command", classify_command("ls -la"))


if __name__ == "__main__":
    unittest.main()
