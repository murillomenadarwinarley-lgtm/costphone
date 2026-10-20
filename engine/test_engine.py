#!/usr/bin/env python3
"""Unit tests for the isolated CostPhone Engine. No phone or network required."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from costphone_engine import (  # noqa: E402
    CostPhoneEngine,
    EngineError,
    Snapshot,
    is_external_effect,
    validate_plan,
)
from mcp_server import dispatch  # noqa: E402


def snap(package: str, *words: str) -> Snapshot:
    return Snapshot(package, f"Pantalla: {package} — elementos (todos)\n" + "\n".join(words), 1)


def simple_plan() -> dict:
    return {
        "version": 1,
        "name": "test navigation",
        "steps": [
            {
                "expect": {"package": "com.example.app", "contains": ["Home"]},
                "actions": [{"kind": "click_text", "value": "Settings"}],
                "after": {"package": "com.example.app", "contains": ["Settings"]},
            }
        ],
    }


class FakeAdapter:
    def __init__(self, snapshots: list[Snapshot]):
        self.snapshots = list(snapshots)
        self.last = self.snapshots[-1] if self.snapshots else snap("com.example.app", "Home")
        self.actions: list[dict] = []
        self.inspect_calls = 0
        self.launch_calls: list[str] = []
        self.fail_action = False

    def inspect(self) -> Snapshot:
        self.inspect_calls += 1
        if self.snapshots:
            self.last = self.snapshots.pop(0)
        return self.last

    def launch(self, package: str) -> Snapshot:
        self.launch_calls.append(package)
        return self.inspect()

    def perform(self, action: dict) -> dict:
        self.actions.append(action)
        if self.fail_action:
            raise EngineError("action_failed", "simulated failure")
        return {"kind": action["kind"], "elapsed_ms": 1, "output": "ok"}


class EngineValidationTests(unittest.TestCase):
    def test_accepts_basic_staged_plan(self):
        validated = validate_plan(simple_plan())
        self.assertEqual(validated["action_count"], 1)
        self.assertEqual(validated["steps"][0]["expect"]["package"], "com.example.app")

    def test_rejects_missing_after_condition(self):
        plan = simple_plan()
        del plan["steps"][0]["after"]
        with self.assertRaises(EngineError) as ctx:
            validate_plan(plan)
        self.assertEqual(ctx.exception.code, "invalid_plan")

    def test_rejects_newlines_in_action_text(self):
        plan = simple_plan()
        plan["steps"][0]["actions"][0]["value"] = "Settings\nclick_id send"
        with self.assertRaises(EngineError):
            validate_plan(plan)

    def test_rejects_arbitrary_shell_action(self):
        plan = simple_plan()
        plan["steps"][0]["actions"] = [{"kind": "shell", "value": "rm -rf /"}]
        with self.assertRaises(EngineError) as ctx:
            validate_plan(plan)
        self.assertIn(ctx.exception.code, {"unsupported_action", "invalid_plan"})

    def test_sensitive_actions_are_detected(self):
        self.assertTrue(is_external_effect({"kind": "click_id", "value": "com.whatsapp:id/send"}))
        self.assertTrue(is_external_effect({"kind": "click_text", "value": "Delete account"}))
        self.assertFalse(is_external_effect({"kind": "click_text", "value": "Settings"}))

    def test_sensitive_plan_is_blocked_before_any_device_read(self):
        plan = simple_plan()
        plan["steps"][0]["actions"] = [{"kind": "click_id", "value": "com.example.app:id/send"}]
        adapter = FakeAdapter([snap("com.example.app", "Home", "Message")])
        engine = CostPhoneEngine(adapter=adapter, allow_external_effects=False)
        with self.assertRaises(EngineError) as ctx:
            engine.run_plan(plan)
        self.assertEqual(ctx.exception.code, "external_effect_blocked")
        self.assertEqual(adapter.inspect_calls, 0)
        self.assertEqual(adapter.actions, [])

    def test_sensitive_action_requires_fresh_context(self):
        plan = simple_plan()
        plan["steps"][0]["actions"] = [{"kind": "click_id", "value": "com.example.app:id/send"}]
        with self.assertRaises(EngineError) as ctx:
            validate_plan(plan, allow_external_effects=True)
        self.assertEqual(ctx.exception.code, "external_effect_needs_context")


class EngineExecutionTests(unittest.TestCase):
    def test_executes_and_verifies_a_stage(self):
        adapter = FakeAdapter([
            snap("com.example.app", "Home", "Settings"),
            snap("com.example.app", "Settings", "Saved"),
        ])
        result = CostPhoneEngine(adapter=adapter, allow_external_effects=False).run_plan(simple_plan())
        self.assertTrue(result["verified"])
        self.assertEqual(result["steps_completed"], 1)
        self.assertEqual(len(adapter.actions), 1)
        self.assertEqual(adapter.actions[0]["value"], "Settings")

    def test_does_not_act_if_package_mismatch(self):
        adapter = FakeAdapter([
            snap("com.other.app", "Home", "Settings"),
        ])
        with self.assertRaises(EngineError) as ctx:
            CostPhoneEngine(adapter=adapter).run_plan(simple_plan())
        self.assertEqual(ctx.exception.code, "package_mismatch")
        self.assertEqual(adapter.actions, [])

    def test_does_not_act_if_context_is_ambiguous(self):
        adapter = FakeAdapter([snap("com.example.app", "Meta AI", "Search")])
        with self.assertRaises(EngineError) as ctx:
            CostPhoneEngine(adapter=adapter).run_plan(simple_plan())
        self.assertEqual(ctx.exception.code, "context_mismatch")
        self.assertEqual(adapter.actions, [])

    def test_sensitive_action_checks_context_again_before_action(self):
        plan = simple_plan()
        plan["steps"][0]["actions"] = [
            {
                "kind": "click_id",
                "value": "com.example.app:id/send",
                "requires": ["Target group", "Message draft"],
            }
        ]
        plan["steps"][0]["expect"]["contains"] = ["Home"]
        plan["steps"][0]["after"]["contains"] = ["Message sent"]
        adapter = FakeAdapter([
            snap("com.example.app", "Home", "Target group", "Message draft"),
            snap("com.example.app", "Target group", "Message draft"),
            snap("com.example.app", "Message sent"),
        ])
        result = CostPhoneEngine(adapter=adapter, allow_external_effects=True).run_plan(plan)
        self.assertTrue(result["verified"])
        self.assertEqual(adapter.inspect_calls, 3)
        self.assertEqual(len(adapter.actions), 1)

    def test_failed_action_is_not_retried(self):
        adapter = FakeAdapter([snap("com.example.app", "Home", "Settings")])
        adapter.fail_action = True
        with self.assertRaises(EngineError):
            CostPhoneEngine(adapter=adapter).run_plan(simple_plan())
        self.assertEqual(len(adapter.actions), 1)

    def test_launch_stage_verifies_package(self):
        plan = {
            "version": 1,
            "name": "open settings",
            "steps": [
                {
                    "launch": "com.android.settings",
                    "expect": {"package": "com.android.settings", "contains": ["Settings"]},
                    "actions": [{"kind": "click_text", "value": "Network"}],
                    "after": {"package": "com.android.settings", "contains": ["Network"]},
                }
            ],
        }
        adapter = FakeAdapter([
            snap("com.android.settings", "Settings", "Network"),
            snap("com.android.settings", "Settings", "Network"),
        ])
        result = CostPhoneEngine(adapter=adapter).run_plan(plan)
        self.assertTrue(result["verified"])
        self.assertEqual(adapter.launch_calls, ["com.android.settings"])


class MCPProtocolTests(unittest.TestCase):
    def test_initialize_and_tools_list(self):
        initialized = dispatch("initialize", {"protocolVersion": "2024-11-05"}, CostPhoneEngine(adapter=FakeAdapter([])))
        self.assertEqual(initialized["result"]["serverInfo"]["name"], "costphone-engine")
        tools = dispatch("tools/list", {}, CostPhoneEngine(adapter=FakeAdapter([])))
        names = {item["name"] for item in tools["result"]["tools"]}
        self.assertEqual(names, {"phone_inspect", "phone_launch", "phone_validate_plan", "phone_run_plan"})

    def test_unknown_tool_is_rejected(self):
        result = dispatch("tools/call", {"name": "phone_shell", "arguments": {}}, CostPhoneEngine(adapter=FakeAdapter([])))
        self.assertEqual(result["error"]["code"], -32602)


if __name__ == "__main__":
    unittest.main(verbosity=2)
