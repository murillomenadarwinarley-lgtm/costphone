#!/usr/bin/env python3
"""Offline tests for CostPhone's native Windows engine; never interacts with a real desktop."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from windows_engine import EngineError, WindowsDesktopEngine, validate_app_name, validate_url  # noqa: E402
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

    def type_text(self, text):
        self.calls.append(("type_text", text))
        return {"characters": len(text), "typed": True}

    def press_key(self, key):
        self.calls.append(("press_key", key))
        return {"key": key, "pressed": True}


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

    def test_mcp_tools_and_unknown_tool(self):
        names = {tool["name"] for tool in TOOLS}
        self.assertEqual(names, {"desktop_inspect", "desktop_screenshot", "desktop_launch_app", "desktop_open_url", "desktop_click", "desktop_type_text", "desktop_press_key"})
        response = dispatch("tools/call", {"name": "not_a_tool", "arguments": {}}, WindowsDesktopEngine(FakeAdapter()))
        self.assertEqual(response["error"]["code"], -32602)


if __name__ == "__main__":
    unittest.main()
