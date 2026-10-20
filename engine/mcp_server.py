#!/usr/bin/env python3
"""Minimal MCP stdio adapter for CostPhone Engine; standard library only."""
from __future__ import annotations

import json
import sys
from typing import Any

from costphone_engine import CostPhoneEngine, EngineError, VERSION


TOOLS: list[dict[str, Any]] = [
    {
        "name": "phone_inspect",
        "description": (
            "Read the current Android accessibility snapshot. This is read-only. "
            "Fails closed when the foreground app cannot be identified or is blocked."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {},
            "additionalProperties": False,
        },
    },
    {
        "name": "phone_launch",
        "description": (
            "Launch an Android package and verify that it is actually in the foreground. "
            "A zero exit code from Android alone is not treated as success."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "package": {
                    "type": "string",
                    "pattern": "^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$",
                    "description": "Exact Android package identifier, for example com.android.settings.",
                }
            },
            "required": ["package"],
            "additionalProperties": False,
        },
    },
    {
        "name": "phone_validate_plan",
        "description": (
            "Validate a staged Android plan without touching the phone. "
            "Use exact package and visible-context checks in every stage."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "plan": {"type": "object", "description": "Version 1 CostPhone Engine plan."}
            },
            "required": ["plan"],
            "additionalProperties": False,
        },
    },
    {
        "name": "phone_run_plan",
        "description": (
            "Run a staged Android plan, or set dry_run=true to validate and preview it without "
            "reading or modifying the phone. A successful live run means the configured foreground-package "
            "and visible-text postconditions matched; it does not independently prove an external outcome "
            "(for example, text presence alone does not prove a message was sent). Sensitive external effects "
            "are disabled unless COSTPHONE_ALLOW_EXTERNAL_EFFECTS=1 is set locally; those actions require "
            "fresh context checks and are never automatically retried."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "plan": {"type": "object", "description": "Version 1 CostPhone Engine plan."},
                "dry_run": {
                    "type": "boolean",
                    "description": "If true, validate and preview the plan only; do not call amctl or touch the phone.",
                    "default": False,
                },
            },
            "required": ["plan"],
            "additionalProperties": False,
        },
    },
]


def _tool_result(result: dict[str, Any]) -> dict[str, Any]:
    return {
        "content": [
            {
                "type": "text",
                "text": json.dumps(result, ensure_ascii=False, separators=(",", ":")),
            }
        ],
        "isError": not bool(result.get("ok")),
    }


def dispatch(method: str, params: dict[str, Any] | None, engine: CostPhoneEngine) -> dict[str, Any] | None:
    params = params or {}
    if method == "notifications/initialized":
        return None
    if method == "ping":
        return {"jsonrpc": "2.0", "result": {}, "_notification": False}
    if method == "initialize":
        requested = params.get("protocolVersion")
        version = requested if isinstance(requested, str) else "2024-11-05"
        return {
            "jsonrpc": "2.0",
            "result": {
                "protocolVersion": version,
                "capabilities": {"tools": {"listChanged": False}},
                "serverInfo": {"name": "costphone-engine", "version": VERSION},
                "instructions": (
                    "Use phone_inspect to observe state, phone_launch to open and verify an app, "
                    "and phone_run_plan for a short plan with package and visible-text assertions. "
                    "Never retry a failed action blindly."
                ),
            },
        }
    if method == "tools/list":
        return {"jsonrpc": "2.0", "result": {"tools": TOOLS}}
    if method == "tools/call":
        name = params.get("name")
        args = params.get("arguments") or {}
        if not isinstance(args, dict):
            return {"jsonrpc": "2.0", "error": {"code": -32602, "message": "arguments debe ser un objeto."}}
        try:
            if name == "phone_inspect":
                result = engine.inspect()
            elif name == "phone_launch":
                result = engine.launch(args.get("package"))
            elif name == "phone_validate_plan":
                result = engine.validate(args.get("plan"))
            elif name == "phone_run_plan":
                dry_run = args.get("dry_run", False)
                if not isinstance(dry_run, bool):
                    raise EngineError("invalid_argument", "dry_run debe ser un booleano.")
                result = engine.run_plan(args.get("plan"), dry_run=dry_run)
            else:
                return {"jsonrpc": "2.0", "error": {"code": -32602, "message": "Herramienta desconocida."}}
            return {"jsonrpc": "2.0", "result": _tool_result(result)}
        except EngineError as exc:
            return {"jsonrpc": "2.0", "result": _tool_result(exc.as_dict())}
        except Exception as exc:
            # Do not leak tracebacks or implementation detail through an agent tool.
            return {
                "jsonrpc": "2.0",
                "result": _tool_result(
                    {"ok": False, "error": "internal_error", "message": "Error interno; no se repetirá ninguna acción."}
                ),
            }
    return {"jsonrpc": "2.0", "error": {"code": -32601, "message": "Método no soportado."}}


def serve(engine: CostPhoneEngine | None = None) -> int:
    engine = engine or CostPhoneEngine()
    # MCP stdio must remain machine-readable: diagnostics go to stderr only.
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
