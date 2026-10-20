#!/usr/bin/env python3
"""MCP stdio server for native Windows CostPhone PC tools."""
from __future__ import annotations

import argparse
import json
import os
import sys
from typing import Any

from windows_engine import EngineError, VERSION, WindowsDesktopEngine

TOOLS: list[dict[str, Any]] = [
    {"name": "desktop_inspect", "description": "Read-only: report the active Windows window title, process, and screen size. Does not read the text inside windows; use desktop_screenshot for visual context.", "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False}},
    {"name": "desktop_screenshot", "description": "Read-only screenshot of the current interactive Windows desktop, returned as an MCP image for visual inspection. The screen may contain private information; inspect only what is needed.", "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False}},
    {"name": "desktop_launch_app", "description": "Open a specific .exe by executable name or absolute path, without arguments or shell commands.", "inputSchema": {"type": "object", "properties": {"executable": {"type": "string", "description": "An executable such as notepad.exe, calc.exe, or an absolute path to an .exe."}}, "required": ["executable"], "additionalProperties": False}},
    {"name": "desktop_open_url", "description": "Open a complete HTTP or HTTPS URL in the Windows default browser. URLs containing embedded credentials are rejected.", "inputSchema": {"type": "object", "properties": {"url": {"type": "string", "description": "Complete http:// or https:// URL."}}, "required": ["url"], "additionalProperties": False}},
    {"name": "desktop_click", "description": "Click once at an absolute screen coordinate. Requires --allow-input to be explicitly enabled at setup. Inspect the screenshot first and never repeat a click blindly.", "inputSchema": {"type": "object", "properties": {"x": {"type": "integer", "minimum": 0}, "y": {"type": "integer", "minimum": 0}}, "required": ["x", "y"], "additionalProperties": False}},
    {"name": "desktop_type_text", "description": "Paste text into the currently focused Windows control using the clipboard, then restore the previous text clipboard when possible. Requires explicit input permission. Verify the focused field before using it.", "inputSchema": {"type": "object", "properties": {"text": {"type": "string", "minLength": 1, "maxLength": 4000}}, "required": ["text"], "additionalProperties": False}},
    {"name": "desktop_press_key", "description": "Press one allow-listed key or shortcut in the focused Windows app. Requires explicit input permission; Enter may submit a form or message, so inspect context first.", "inputSchema": {"type": "object", "properties": {"key": {"type": "string", "enum": ["enter", "tab", "escape", "backspace", "delete", "space", "up", "down", "left", "right", "home", "end", "pageup", "pagedown", "f5", "ctrl+l", "ctrl+a", "ctrl+c", "ctrl+v"]}}, "required": ["key"], "additionalProperties": False}},
    {"name": "desktop_inspect_controls", "description": "Read visible Windows UI Automation controls in the currently active window, including text/name, automation ID, role, enabled state and bounds. Read-only; returned selectors can be used for an exact follow-up action. Some applications expose few or no controls; use desktop_screenshot as fallback.", "inputSchema": {"type": "object", "properties": {"limit": {"type": "integer", "minimum": 1, "maximum": 200, "default": 100}}, "additionalProperties": False}},
    {"name": "desktop_click_control", "description": "Click an exact control from Windows UI Automation in the current foreground window. Requires explicit input permission. The selector must specify name and/or automation_id; if duplicates exist, occurrence must disambiguate. Inspect again after each action. This may activate buttons that submit or send things.", "inputSchema": {"type": "object", "properties": {"selector": {"type": "object", "properties": {"name": {"type": "string", "maxLength": 300}, "automation_id": {"type": "string", "maxLength": 300}, "control_type": {"type": "string", "maxLength": 100}, "occurrence": {"type": "integer", "minimum": 0, "maximum": 199}}, "additionalProperties": False, "description": "Use the selector returned by desktop_inspect_controls. Must include name or automation_id."}}, "required": ["selector"], "additionalProperties": False}},
    {"name": "desktop_set_control_text", "description": "Set the text of a specific Windows UI Automation Edit field, without sending Enter. Requires explicit input permission and an exact selector. Refuses elements identified as password fields. Read fresh control metadata and verify the outcome afterward.", "inputSchema": {"type": "object", "properties": {"selector": {"type": "object", "properties": {"name": {"type": "string", "maxLength": 300}, "automation_id": {"type": "string", "maxLength": 300}, "control_type": {"type": "string", "maxLength": 100}, "occurrence": {"type": "integer", "minimum": 0, "maximum": 199}}, "additionalProperties": False, "description": "Use the selector returned by desktop_inspect_controls. Must include name or automation_id."}, "text": {"type": "string", "minLength": 1, "maxLength": 4000}}, "required": ["selector", "text"], "additionalProperties": False}},
]


def _tool_result(value: dict[str, Any], *, screenshot: bool = False) -> dict[str, Any]:
    if screenshot and value.get("ok") and isinstance(value.get("image_base64"), str):
        image_data = value["image_base64"]
        text_value = {k: v for k, v in value.items() if k != "image_base64"}
        return {"content": [
            {"type": "text", "text": json.dumps(text_value, ensure_ascii=False, separators=(",", ":"))},
            {"type": "image", "data": image_data, "mimeType": "image/png"},
        ], "isError": False}
    return {"content": [{"type": "text", "text": json.dumps(value, ensure_ascii=False, separators=(",", ":"))}], "isError": not bool(value.get("ok"))}


def dispatch(method: str, params: dict[str, Any] | None, engine: WindowsDesktopEngine) -> dict[str, Any] | None:
    params = params or {}
    if method in {"notifications/initialized", "notifications/cancelled", "notifications/progress"}:
        return None
    if method == "ping":
        return {"jsonrpc": "2.0", "result": {}, "_notification": False}
    if method == "initialize":
        requested = params.get("protocolVersion")
        version = requested if isinstance(requested, str) else "2024-11-05"
        return {"jsonrpc": "2.0", "result": {"protocolVersion": version, "capabilities": {"tools": {"listChanged": False}}, "serverInfo": {"name": "costphone-pc", "version": VERSION}, "instructions": "Este motor se ejecuta localmente en Windows y no necesita el móvil Android. Usa desktop_screenshot o desktop_inspect antes de actuar; tras cada acción comprueba de nuevo el estado. El control de teclado y ratón puede estar desactivado. No repitas a ciegas acciones ni infieras que una acción externa tuvo éxito sin confirmación visible."}}
    if method == "tools/list":
        return {"jsonrpc": "2.0", "result": {"tools": TOOLS}}
    if method == "tools/call":
        name = params.get("name")
        args = params.get("arguments") or {}
        if not isinstance(args, dict):
            return {"jsonrpc": "2.0", "error": {"code": -32602, "message": "arguments debe ser un objeto."}}
        try:
            is_screenshot = False
            if name == "desktop_inspect":
                result = engine.inspect()
            elif name == "desktop_screenshot":
                result = engine.screenshot()
                is_screenshot = True
            elif name == "desktop_launch_app":
                result = engine.launch_app(args.get("executable"))
            elif name == "desktop_open_url":
                result = engine.open_url(args.get("url"))
            elif name == "desktop_click":
                result = engine.click(args.get("x"), args.get("y"))
            elif name == "desktop_type_text":
                result = engine.type_text(args.get("text"))
            elif name == "desktop_press_key":
                result = engine.press_key(args.get("key"))
            elif name == "desktop_inspect_controls":
                result = engine.list_controls(args.get("limit", 100))
            elif name == "desktop_click_control":
                result = engine.click_control(args.get("selector"))
            elif name == "desktop_set_control_text":
                result = engine.set_control_text(args.get("selector"), args.get("text"))
            else:
                return {"jsonrpc": "2.0", "error": {"code": -32602, "message": "Herramienta desconocida."}}
            return {"jsonrpc": "2.0", "result": _tool_result(result, screenshot=is_screenshot)}
        except EngineError as exc:
            return {"jsonrpc": "2.0", "result": _tool_result(exc.as_dict())}
        except Exception:
            return {"jsonrpc": "2.0", "result": _tool_result({"ok": False, "error": "internal_error", "message": "Error interno; no se repetirá ninguna acción."})}
    return {"jsonrpc": "2.0", "error": {"code": -32601, "message": "Método no soportado."}}


def serve(engine: WindowsDesktopEngine | None = None) -> int:
    parser = argparse.ArgumentParser(description="CostPhone PC - MCP local para Windows")
    parser.add_argument("--allow-input", action="store_true", help="Habilita expresamente teclado/ratón para esta instancia MCP.")
    args = parser.parse_args()
    engine = engine or WindowsDesktopEngine(allow_input=args.allow_input or os.environ.get("COSTPHONE_WINDOWS_ALLOW_INPUT") == "1")
    for line in sys.stdin:
        try:
            request = json.loads(line)
            if not isinstance(request, dict) or request.get("jsonrpc") != "2.0":
                response = {"jsonrpc": "2.0", "id": None, "error": {"code": -32600, "message": "Petición inválida."}}
            else:
                method = request.get("method")
                params = request.get("params") if isinstance(request.get("params"), dict) else {}
                response = dispatch(str(method), params, engine)
                if response is None:
                    continue
                if response.pop("_notification", False):
                    continue
                response["id"] = request.get("id")
        except json.JSONDecodeError:
            response = {"jsonrpc": "2.0", "id": None, "error": {"code": -32700, "message": "JSON inválido."}}
        except Exception:
            response = {"jsonrpc": "2.0", "id": None, "error": {"code": -32603, "message": "Error interno."}}
        sys.stdout.write(json.dumps(response, ensure_ascii=False, separators=(",", ":")) + "\n")
        sys.stdout.flush()
    return 0


if __name__ == "__main__":
    raise SystemExit(serve())
