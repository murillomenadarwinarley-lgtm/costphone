#!/usr/bin/env python3
"""Offline tests for CostPhone's native Windows engine; never interacts with a real desktop."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from windows_engine import EngineError, WindowsDesktopEngine, validate_app_name, validate_control_selector, validate_url  # noqa: E402
from windows_mcp_server import TOOLS, dispatch  # noqa: E402


class FakeAdapter:
    def __init__(self):
        self.calls = []

    def inspect(self):
        self.calls.append(("inspect",))
        return {"title": "Notepad", "process_id": 22, "process_name": "notepad.exe", "screen_width": 1920, "screen_height": 1080}

    def screenshot(self):
        self.calls.append(("screenshot",))
        return {"screen_width": 1920, "screen_height": 1080, "image_base64": "ZmFrZQ=="}

    def launch_app(self, executable):
        self.calls.append(("launch_app", executable))
        return {"app": executable, "process_id": 12, "launched": True}

    def open_url(self, url):
        self.calls.append(("open_url", url))
        return {"url": url, "opened": True}

    def click(self, x, y):
        self.calls.append(("click", x, y))
        return {"x": x, "y": y, "clicked": True}

    def drag(self, start_x, start_y, end_x, end_y, duration_ms):
        self.calls.append(("drag", start_x, start_y, end_x, end_y, duration_ms))
        return {"start_x": start_x, "start_y": start_y, "end_x": end_x, "end_y": end_y, "duration_ms": duration_ms, "dragged": True}

    def scroll(self, x, y, clicks):
        self.calls.append(("scroll", x, y, clicks))
        return {"x": x, "y": y, "clicks": clicks, "scrolled": True}

    def type_text(self, text):
        self.calls.append(("type_text", text))
        return {"characters": len(text), "typed": True}

    def press_key(self, key):
        self.calls.append(("press_key", key))
        return {"key": key, "pressed": True}

    def list_controls(self, limit=100):
        self.calls.append(("list_controls", limit))
        return {"controls": [{"name": "Save", "automation_id": "saveButton", "control_type": "Button", "selector": {"name": "Save", "automation_id": "saveButton", "control_type": "Button", "occurrence": 0}}], "returned": 1, "visible_descendants": 1, "truncated": False}

    def click_control(self, selector):
        self.calls.append(("click_control", selector))
        return {"clicked": True, "control": {"name": "Save", "control_type": "Button"}}

    def double_click_control(self, selector):
        self.calls.append(("double_click_control", selector))
        return {"double_clicked": True, "control": {"name": "Folder", "control_type": "ListItem"}}

    def set_control_text(self, selector, text):
        self.calls.append(("set_control_text", selector, text))
        return {"text_set": True, "characters": len(text), "control": {"name": "Search", "control_type": "Edit"}}


    def set_control_checked(self, selector, checked):
        self.calls.append(("set_control_checked", selector, checked))
        return {"checked": checked, "changed": True, "control": {"name": "Remember me", "control_type": "CheckBox"}}


class ValidationTests(unittest.TestCase):
    def test_validates_http_url(self):
        self.assertEqual(validate_url("https://example.com/path"), "https://example.com/path")

    def test_rejects_non_http_url(self):
        for url in ("file:///C:/secret.txt", "javascript:alert(1)", "https://user:password@example.com"):
            with self.subTest(url=url), self.assertRaises(EngineError):
                validate_url(url)

    def test_app_must_be_exe_without_arguments(self):
        self.assertEqual(validate_app_name("notepad.exe"), "notepad.exe")
        for app in ("notepad.exe --help", "powershell.exe -Command something", "cmd.exe & calc.exe", "https://example.com"):
            with self.subTest(app=app), self.assertRaises(EngineError):
                validate_app_name(app)


class DesktopEngineTests(unittest.TestCase):
    def test_inspection_is_read_only(self):
        adapter = FakeAdapter()
        result = WindowsDesktopEngine(adapter, allow_input=False).inspect()
        self.assertTrue(result["ok"])
        self.assertEqual(result["window"]["process_name"], "notepad.exe")
        self.assertEqual(adapter.calls, [("inspect",)])

    def test_input_disabled_by_default(self):
        adapter = FakeAdapter()
        engine = WindowsDesktopEngine(adapter, allow_input=False)
        with self.assertRaises(EngineError) as ctx:
            engine.type_text("hello")
        self.assertEqual(ctx.exception.code, "input_disabled")
        self.assertEqual(adapter.calls, [])

    def test_click_checks_integer_and_screen_bounds(self):
        adapter = FakeAdapter()
        engine = WindowsDesktopEngine(adapter, allow_input=True)
        self.assertTrue(engine.click(1919, 1079)["ok"])
        with self.assertRaises(EngineError):
            engine.click(1920, 1080)
        with self.assertRaises(EngineError):
            engine.click(True, 2)

    def test_scroll_checks_permission_resolution_and_range(self):
        adapter = FakeAdapter()
        engine = WindowsDesktopEngine(adapter, allow_input=True)
        self.assertTrue(engine.scroll(100, 100, -3)["ok"])
        self.assertEqual(adapter.calls[-1], ("scroll", 100, 100, -3))
        with self.assertRaises(EngineError):
            engine.scroll(1920, 100, 1)
        with self.assertRaises(EngineError):
            engine.scroll(100, 100, 21)
        disabled = WindowsDesktopEngine(FakeAdapter(), allow_input=False)
        with self.assertRaises(EngineError) as ctx:
            disabled.scroll(10, 10, 1)
        self.assertEqual(ctx.exception.code, "input_disabled")

    def test_drag_checks_permission_bounds_and_duration(self):
        adapter = FakeAdapter()
        engine = WindowsDesktopEngine(adapter, allow_input=True)
        self.assertTrue(engine.drag(20, 30, 200, 300, 500)["ok"])
        self.assertEqual(adapter.calls[-1], ("drag", 20, 30, 200, 300, 500))
        with self.assertRaises(EngineError):
            engine.drag(0, 0, 1920, 100, 500)
        with self.assertRaises(EngineError):
            engine.drag(0, 0, 10, 10, 99)
        disabled = WindowsDesktopEngine(FakeAdapter(), allow_input=False)
        with self.assertRaises(EngineError) as ctx:
            disabled.drag(0, 0, 10, 10, 500)
        self.assertEqual(ctx.exception.code, "input_disabled")

    def test_text_and_key_allowlist(self):
        adapter = FakeAdapter()
        engine = WindowsDesktopEngine(adapter, allow_input=True)
        self.assertTrue(engine.type_text("hola ñ🙂")["ok"])
        self.assertTrue(engine.press_key("ctrl+l")["ok"])
        with self.assertRaises(EngineError):
            engine.press_key("alt+f4")

    def test_invalid_url_never_reaches_adapter(self):
        adapter = FakeAdapter()
        with self.assertRaises(EngineError):
            WindowsDesktopEngine(adapter).open_url("file:///C:/secret.txt")
        self.assertEqual(adapter.calls, [])

    def test_screenshot_returns_image_payload(self):
        value = dispatch("tools/call", {"name": "desktop_screenshot", "arguments": {}}, WindowsDesktopEngine(FakeAdapter()))
        contents = value["result"]["content"]
        self.assertEqual(contents[1]["type"], "image")
        self.assertEqual(contents[1]["mimeType"], "image/png")
        self.assertEqual(contents[1]["data"], "ZmFrZQ==")

    def test_ui_automation_selector_requires_stable_identifier(self):
        self.assertEqual(validate_control_selector({"name": "Save", "control_type": "Button", "window_handle": 100, "process_id": 22}), {"name": "Save", "control_type": "Button", "window_handle": 100, "process_id": 22})
        with self.assertRaises(EngineError):
            validate_control_selector({"control_type": "Button", "window_handle": 100, "process_id": 22})
        with self.assertRaises(EngineError):
            validate_control_selector({"name": "Save", "window_handle": 100, "process_id": 22, "occurrence": True})

    def test_controls_are_readable_without_input_permission(self):
        adapter = FakeAdapter()
        result = WindowsDesktopEngine(adapter, allow_input=False).list_controls()
        self.assertTrue(result["ok"])
        self.assertEqual(result["controls"][0]["selector"]["automation_id"], "saveButton")
        self.assertEqual(adapter.calls, [("list_controls", 100)])

    def test_ui_control_click_requires_explicit_permission(self):
        adapter = FakeAdapter()
        with self.assertRaises(EngineError) as ctx:
            WindowsDesktopEngine(adapter, allow_input=False).click_control({"name": "Save", "control_type": "Button"})
        self.assertEqual(ctx.exception.code, "input_disabled")
        self.assertEqual(adapter.calls, [])

    def test_ui_control_operations_with_permission(self):
        adapter = FakeAdapter()
        engine = WindowsDesktopEngine(adapter, allow_input=True)
        self.assertTrue(engine.click_control({"name": "Save", "control_type": "Button", "window_handle": 100, "process_id": 22})["ok"])
        self.assertTrue(engine.set_control_text({"name": "Search", "control_type": "Edit", "window_handle": 100, "process_id": 22}, "café")["ok"])
        self.assertEqual(adapter.calls[0][0], "click_control")
        self.assertEqual(adapter.calls[1][0], "set_control_text")
        self.assertEqual(adapter.calls[1][2], "café")

    def test_set_checkbox_state_requires_permission_and_boolean(self):
        adapter = FakeAdapter()
        engine = WindowsDesktopEngine(adapter, allow_input=True)
        self.assertTrue(engine.set_control_checked({"name": "Remember me", "control_type": "CheckBox", "window_handle": 100, "process_id": 22}, True)["ok"])
        self.assertEqual(adapter.calls[-1], ("set_control_checked", {"name": "Remember me", "control_type": "CheckBox", "window_handle": 100, "process_id": 22}, True))
        with self.assertRaises(EngineError):
            engine.set_control_checked({"name": "Remember me", "window_handle": 100, "process_id": 22}, "true")
        disabled = WindowsDesktopEngine(FakeAdapter(), allow_input=False)
        with self.assertRaises(EngineError) as ctx:
            disabled.set_control_checked({"name": "Remember me", "window_handle": 100, "process_id": 22}, False)
        self.assertEqual(ctx.exception.code, "input_disabled")

    def test_double_click_control_requires_permission_and_window_bound_selector(self):
        adapter = FakeAdapter()
        engine = WindowsDesktopEngine(adapter, allow_input=True)
        selector = {"name": "Folder", "control_type": "ListItem", "window_handle": 100, "process_id": 22}
        self.assertTrue(engine.double_click_control(selector)["ok"])
        self.assertEqual(adapter.calls[-1], ("double_click_control", selector))
        disabled = WindowsDesktopEngine(FakeAdapter(), allow_input=False)
        with self.assertRaises(EngineError) as ctx:
            disabled.double_click_control(selector)
        self.assertEqual(ctx.exception.code, "input_disabled")

    def test_mcp_tools_and_unknown_tool(self):
        names = {tool["name"] for tool in TOOLS}
        self.assertEqual(names, {"desktop_inspect", "desktop_screenshot", "desktop_launch_app", "desktop_open_url", "desktop_click", "desktop_scroll", "desktop_drag", "desktop_type_text", "desktop_press_key", "desktop_inspect_controls", "desktop_click_control", "desktop_double_click_control", "desktop_set_control_text", "desktop_set_control_checked"})
        response = dispatch("tools/call", {"name": "not_a_tool", "arguments": {}}, WindowsDesktopEngine(FakeAdapter()))
        self.assertEqual(response["error"]["code"], -32602)


if __name__ == "__main__":
    unittest.main()
