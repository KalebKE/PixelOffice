from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from hooks.manage_hooks import (
    PROVIDER_EVENTS,
    config_paths,
    count_owned,
    install,
    load_json,
    uninstall,
)


class ManageHooksTest(unittest.TestCase):
    def test_install_is_idempotent_and_uninstall_preserves_existing_hooks(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            paths = config_paths(home)
            existing_handler = {
                "hooks": {
                    "Stop": [
                        {
                            "hooks": [
                                {
                                    "type": "command",
                                    "command": "python3 existing_hook.py",
                                }
                            ]
                        }
                    ]
                },
                "unrelated": True,
            }
            for path in paths.values():
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(json.dumps(existing_handler), encoding="utf-8")

            install(home, "127.0.0.1", 9997)
            first_contents = {provider: path.read_text() for provider, path in paths.items()}
            for provider, path in paths.items():
                config = load_json(path)
                self.assertEqual(len(PROVIDER_EVENTS[provider]), count_owned(config, provider))
                self.assertTrue(config["unrelated"])
                self.assertTrue(
                    any(
                        handler.get("command") == "python3 existing_hook.py"
                        for group in config["hooks"]["Stop"]
                        for handler in group["hooks"]
                    )
                )
                self.assertTrue(list(path.parent.glob(f"{path.name}.bak-*")))

            install(home, "127.0.0.1", 9997)
            self.assertEqual(
                first_contents,
                {provider: path.read_text() for provider, path in paths.items()},
            )

            uninstall(home)
            for provider, path in paths.items():
                config = load_json(path)
                self.assertEqual(0, count_owned(config, provider))
                self.assertTrue(config["unrelated"])
                self.assertEqual(
                    "python3 existing_hook.py",
                    config["hooks"]["Stop"][0]["hooks"][0]["command"],
                )
            self.assertFalse(
                (home / ".local/share/pixel-office/pixel_office_hook.py").exists()
            )

    def test_invalid_existing_json_fails_before_any_configuration_write(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            paths = config_paths(home)
            paths["claude"].parent.mkdir(parents=True, exist_ok=True)
            paths["claude"].write_text('{"existing": true}', encoding="utf-8")
            paths["codex"].parent.mkdir(parents=True, exist_ok=True)
            paths["codex"].write_text("not json", encoding="utf-8")

            with self.assertRaises(json.JSONDecodeError):
                install(home, "127.0.0.1", 9997)

            self.assertEqual('{"existing": true}', paths["claude"].read_text())


if __name__ == "__main__":
    unittest.main()
