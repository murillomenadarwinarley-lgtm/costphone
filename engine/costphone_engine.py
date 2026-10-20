#!/usr/bin/env python3
"""CostPhone Engine: deterministic, fail-closed orchestration above amctl.

This module intentionally contains no shell evaluation and no third-party dependencies.
It orchestrates only allow-listed amctl commands. It is not an LLM or a replacement
for the existing CostPhone app/CLI.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from dataclasses import asdict, dataclass
from typing import Any
from urllib.parse import urlparse

VERSION = "0.1.1"
PACKAGE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+$")
HEADER_RE = re.compile(r"^Pantalla(?:\s+—\s+display\s+\d+)?:\s*([^\s—]+)\s+—", re.MULTILINE)
RISK_RE = re.compile(
    r"(?i)(?:^|[^a-z0-9])"
    r"(?:send|enviar|mandar|submit|delete|remove|borrar|eliminar|suprimir|"
    r"pay|pagar|purchase|buy|comprar|transfer|transferir|publish|publicar|post|"
    r"checkout|confirm|confirmar|call|llamar|share|compartir|accept|aceptar|"
    r"place[_ -]?order|erase|clear|uninstall|cancel|cancelar|pedido|order)"
    r"(?:$|[^a-z0-9])"
)
MAX_PLAN_STEPS = 30
MAX_ACTIONS_PER_STEP = 10
MAX_TEXT = 4000
MAX_CHECKS = 8
MAX_SNAPSHOT_CHARS = 16000

KEYCODES = {
    "back": "4",
    "home": "3",
    "recents": "187",
    "enter": "66",
    "tab": "61",
    "escape": "111",
    "delete": "67",
    "space": "62",
    "dpad_up": "19",
    "dpad_down": "20",
    "dpad_left": "21",
    "dpad_right": "22",
}
ACTION_KINDS = {
    "click_text", "click_id", "type_text", "press_key", "scroll",
    "swipe", "open_url", "wait_text",
}


class EngineError(Exception):
    """Expected, user-safe error with a stable machine-readable code."""

    def __init__(self, code: str, message: str, details: dict[str, Any] | None = None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.details = details or {}

    def as_dict(self) -> dict[str, Any]:
        return {"ok": False, "error": self.code, "message": self.message, **self.details}


@dataclass(frozen=True)
class Snapshot:
    package: str
    text: str
    elapsed_ms: int

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)


class AmctlAdapter:
    """A narrow, argv-only adapter for the already-installed amctl command."""

    def __init__(self, executable: str = "amctl", timeout_s: float = 15.0):
        self.executable = executable
        self.timeout_s = timeout_s

    def _run(self, args: list[str], timeout_s: float | None = None) -> tuple[str, int, int]:
        resolved = shutil.which(self.executable) if os.path.sep not in self.executable else self.executable
        if not resolved or not os.path.isfile(resolved):
            raise EngineError(
                "amctl_missing",
                "No encuentro amctl. Instala CostPhone o añade ~/bin al PATH.",
            )
        started = time.monotonic()
        try:
            proc = subprocess.run(
                [resolved, *args],
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=timeout_s or self.timeout_s,
                check=False,
                shell=False,
            )
        except subprocess.TimeoutExpired as exc:
            elapsed = int((time.monotonic() - started) * 1000)
            raise EngineError(
                "command_timeout",
                "amctl tardó demasiado. No repetiré la acción automáticamente.",
                {"command": args[0] if args else "", "elapsed_ms": elapsed},
            ) from exc
        except OSError as exc:
            raise EngineError("command_failed", "No se pudo ejecutar amctl.", {"reason": str(exc)}) from exc
        elapsed = int((time.monotonic() - started) * 1000)
        output = (proc.stdout or "")[:MAX_SNAPSHOT_CHARS]
        return output, proc.returncode, elapsed

    @staticmethod
    def parse_package(output: str) -> str:
        match = HEADER_RE.search(output)
        if not match:
            raise EngineError(
                "unknown_screen",
                "No pude identificar de forma fiable la aplicación visible. No ejecutaré toques.",
                {"output": output[:3000]},
            )
        package = match.group(1).strip()
        if package.casefold() in {"termux", "com.termux"} or "bloqueada" in output.casefold():
            raise EngineError(
                "screen_blocked",
                "La pantalla actual está bloqueada para el motor (por ejemplo, Termux en primer plano).",
                {"package": package},
            )
        if not PACKAGE_RE.fullmatch(package):
            raise EngineError(
                "unknown_screen",
                "La cabecera no contiene un identificador de paquete Android verificable.",
                {"package": package},
            )
        return package

    def inspect(self) -> Snapshot:
        output, rc, elapsed = self._run(["snap", "-a"])
        if rc != 0:
            raise EngineError(
                "inspect_failed",
                "CostPhone no pudo leer una pantalla utilizable. No ejecutaré acciones a ciegas.",
                {"exit_code": rc, "output": output[:3000], "elapsed_ms": elapsed},
            )
        package = self.parse_package(output)
        return Snapshot(package=package, text=output, elapsed_ms=elapsed)

    def launch(self, package: str) -> Snapshot:
        validate_package(package)
        output, rc, elapsed = self._run(["launch", package])
        if rc != 0:
            raise EngineError(
                "launch_failed",
                "amctl no confirmó la apertura. No continuaré con referencias de otra pantalla.",
                {"package": package, "exit_code": rc, "output": output[:3000], "elapsed_ms": elapsed},
            )
        # A zero exit code is not enough: verify the real foreground application.
        snapshot = self.inspect()
        if snapshot.package != package:
            raise EngineError(
                "foreground_mismatch",
                "Android no dejó la aplicación solicitada en primer plano.",
                {"expected_package": package, "actual_package": snapshot.package},
            )
        return snapshot

    def perform(self, action: dict[str, Any]) -> dict[str, Any]:
        kind = action["kind"]
        value = action.get("value")
        if kind == "click_text":
            args = ["click", value]
        elif kind == "click_id":
            args = ["clickid", value]
        elif kind == "type_text":
            args = ["type", value]
        elif kind == "press_key":
            args = ["input", "keyevent", KEYCODES[value]]
        elif kind == "scroll":
            args = ["scroll", value]
        elif kind == "swipe":
            args = ["swipe", *(str(n) for n in value)]
        elif kind == "open_url":
            args = ["openurl", value]
        elif kind == "wait_text":
            args = ["waittext", value, str(action.get("timeout_s", 5))]
        else:
            raise EngineError("unsupported_action", "La acción no está permitida.", {"kind": kind})

        output, rc, elapsed = self._run(args, timeout_s=float(action.get("command_timeout_s", 15)))
        if rc != 0:
            # Never auto-retry a UI action: a timeout can happen after an action took effect.
            raise EngineError(
                "action_failed",
                "La acción falló o no pudo confirmarse. No la repetiré automáticamente.",
                {"kind": kind, "exit_code": rc, "output": output[:2000], "elapsed_ms": elapsed},
            )
        return {"kind": kind, "elapsed_ms": elapsed, "output": output[:1000]}


def validate_package(package: Any) -> str:
    if not isinstance(package, str) or not PACKAGE_RE.fullmatch(package):
        raise EngineError("invalid_package", "El paquete debe ser un identificador Android válido.")
    return package


def _safe_text(value: Any, field: str, maximum: int = MAX_TEXT) -> str:
    if not isinstance(value, str) or not value or len(value) > maximum:
        raise EngineError("invalid_plan", f"{field} debe ser texto no vacío de hasta {maximum} caracteres.")
    if any(ord(char) < 32 and char not in "\t" for char in value):
        raise EngineError("invalid_plan", f"{field} contiene caracteres de control no permitidos.")
    return value


def _checks(value: Any, field: str, allow_empty: bool = False) -> list[str]:
    if not isinstance(value, list) or len(value) > MAX_CHECKS:
        raise EngineError("invalid_plan", f"{field} debe ser una lista de hasta {MAX_CHECKS} textos.")
    if not value and not allow_empty:
        raise EngineError("invalid_plan", f"{field} no puede estar vacío.")
    return [_safe_text(item, field, 500) for item in value]


def _assertion(value: Any, field: str, default_package: str | None = None) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise EngineError("invalid_plan", f"{field} debe ser un objeto.")
    package = value.get("package", default_package)
    validate_package(package)
    contains = _checks(value.get("contains"), f"{field}.contains")
    not_contains = _checks(value.get("not_contains", []), f"{field}.not_contains", allow_empty=True)
    return {"package": package, "contains": contains, "not_contains": not_contains}


def is_external_effect(action: dict[str, Any]) -> bool:
    kind = action.get("kind")
    value = str(action.get("value", ""))
    if kind == "press_key" and value == "enter":
        return True
    return kind in {"click_text", "click_id"} and bool(RISK_RE.search(value))


def validate_action(raw: Any, allow_external_effects: bool) -> dict[str, Any]:
    if not isinstance(raw, dict):
        raise EngineError("invalid_plan", "Cada acción debe ser un objeto.")
    kind = raw.get("kind")
    if kind not in ACTION_KINDS:
        raise EngineError("unsupported_action", "Tipo de acción no permitido.", {"kind": kind})
    action: dict[str, Any] = {"kind": kind}
    if kind in {"click_text", "click_id", "type_text", "press_key", "scroll", "open_url", "wait_text"}:
        value = _safe_text(raw.get("value"), f"action.{kind}")
        if kind == "press_key" and value not in KEYCODES:
            raise EngineError("invalid_action", "Tecla no permitida.", {"key": value})
        if kind == "scroll" and value not in {"up", "down"}:
            raise EngineError("invalid_action", "scroll solo acepta up o down.")
        if kind == "open_url":
            parsed = urlparse(value)
            if parsed.scheme not in {"http", "https"} or not parsed.netloc:
                raise EngineError("invalid_action", "open_url solo acepta una URL http o https completa.")
        if kind == "wait_text":
            timeout_s = raw.get("timeout_s", 5)
            if not isinstance(timeout_s, int) or not 1 <= timeout_s <= 8:
                raise EngineError("invalid_action", "wait_text.timeout_s debe estar entre 1 y 8 segundos.")
            action["timeout_s"] = timeout_s
        action["value"] = value
    elif kind == "swipe":
        value = raw.get("value")
        if not isinstance(value, list) or len(value) != 5 or any(type(n) is not int for n in value):
            raise EngineError("invalid_action", "swipe.value debe contener x1, y1, x2, y2 y milisegundos enteros.")
        if any(n < 0 or n > 10000 for n in value[:4]) or not 50 <= value[4] <= 2000:
            raise EngineError("invalid_action", "Coordenadas fuera de rango o duración de swipe inválida.")
        action["value"] = value

    requires = _checks(raw.get("requires", []), "action.requires", allow_empty=True)
    if requires:
        action["requires"] = requires

    if is_external_effect(action):
        if not allow_external_effects:
            raise EngineError(
                "external_effect_blocked",
                "El motor bloqueó una acción que podría enviar, publicar, borrar, llamar o confirmar algo. "
                "Habilita los efectos externos localmente solo cuando sean parte explícita de la tarea.",
                {"kind": kind, "value": action.get("value")},
            )
        if not requires:
            raise EngineError(
                "external_effect_needs_context",
                "Las acciones con efectos externos necesitan requisitos de contexto visibles justo antes de ejecutarse.",
                {"kind": kind},
            )
    return action


def validate_plan(plan: Any, allow_external_effects: bool = False) -> dict[str, Any]:
    """Validate all stages before allowing any device command to run."""
    if not isinstance(plan, dict):
        raise EngineError("invalid_plan", "El plan debe ser un objeto JSON.")
    if plan.get("version") != 1:
        raise EngineError("invalid_plan", "version debe ser 1.")
    name = plan.get("name", "untitled")
    if not isinstance(name, str) or len(name) > 120:
        raise EngineError("invalid_plan", "name debe ser un texto de hasta 120 caracteres.")
    steps = plan.get("steps")
    if not isinstance(steps, list) or not steps or len(steps) > MAX_PLAN_STEPS:
        raise EngineError("invalid_plan", f"steps debe tener entre 1 y {MAX_PLAN_STEPS} etapas.")

    validated_steps = []
    action_count = 0
    for index, raw_step in enumerate(steps):
        prefix = f"steps[{index}]"
        if not isinstance(raw_step, dict):
            raise EngineError("invalid_plan", f"{prefix} debe ser un objeto.")
        launch = raw_step.get("launch")
        if launch is not None:
            validate_package(launch)
        expect = _assertion(raw_step.get("expect"), f"{prefix}.expect")
        raw_actions = raw_step.get("actions")
        if not isinstance(raw_actions, list) or not raw_actions or len(raw_actions) > MAX_ACTIONS_PER_STEP:
            raise EngineError("invalid_plan", f"{prefix}.actions debe contener de 1 a {MAX_ACTIONS_PER_STEP} acciones.")
        after = _assertion(raw_step.get("after"), f"{prefix}.after", expect["package"])
        actions = [validate_action(item, allow_external_effects) for item in raw_actions]
        for action_index, action in enumerate(actions[1:], start=2):
            if not action.get("requires"):
                raise EngineError(
                    "invalid_plan",
                    f"{prefix}: la acción {action_index} necesita requires para volver a comprobar la pantalla tras la acción anterior.",
                )
        action_count += len(actions)
        validated_steps.append(
            {"launch": launch, "expect": expect, "actions": actions, "after": after}
        )
    return {"version": 1, "name": name, "steps": validated_steps, "action_count": action_count}


def _check_snapshot(snapshot: Snapshot, assertion: dict[str, Any], stage: str) -> None:
    expected = assertion["package"]
    if snapshot.package != expected:
        raise EngineError(
            "package_mismatch",
            f"{stage}: la aplicación visible no coincide con el destino esperado.",
            {"expected_package": expected, "actual_package": snapshot.package},
        )
    haystack = snapshot.text.casefold()
    missing = [needle for needle in assertion["contains"] if needle.casefold() not in haystack]
    if missing:
        raise EngineError(
            "context_mismatch",
            f"{stage}: no se encontraron todas las señales de contexto requeridas. No continuaré a ciegas.",
            {"package": snapshot.package, "missing": missing, "snapshot": snapshot.text[:5000]},
        )
    present = [needle for needle in assertion["not_contains"] if needle.casefold() in haystack]
    if present:
        raise EngineError(
            "context_forbidden",
            f"{stage}: la pantalla contiene texto que el plan prohíbe.",
            {"package": snapshot.package, "unexpected": present},
        )


class CostPhoneEngine:
    def __init__(self, adapter: Any | None = None, allow_external_effects: bool | None = None):
        self.adapter = adapter or AmctlAdapter()
        if allow_external_effects is None:
            allow_external_effects = os.environ.get("COSTPHONE_ALLOW_EXTERNAL_EFFECTS") == "1"
        self.allow_external_effects = bool(allow_external_effects)

    def inspect(self) -> dict[str, Any]:
        snap = self.adapter.inspect()
        return {"ok": True, "version": VERSION, "snapshot": snap.as_dict()}

    def launch(self, package: str) -> dict[str, Any]:
        snap = self.adapter.launch(validate_package(package))
        return {
            "ok": True,
            "verified_foreground": True,
            "package": snap.package,
            "snapshot": snap.as_dict(),
        }

    def validate(self, plan: Any) -> dict[str, Any]:
        valid = validate_plan(plan, self.allow_external_effects)
        return {
            "ok": True,
            "valid": True,
            "name": valid["name"],
            "steps": len(valid["steps"]),
            "actions": valid["action_count"],
            "external_effects_enabled": self.allow_external_effects,
            "dry_run": True,
        }

    def run_plan(self, plan: Any, dry_run: bool = False) -> dict[str, Any]:
        if not isinstance(dry_run, bool):
            raise EngineError("invalid_argument", "dry_run debe ser un booleano.")

        # Validate the entire plan before doing any device I/O.
        valid = validate_plan(plan, self.allow_external_effects)
        if dry_run:
            return {
                "ok": True,
                "dry_run": True,
                "executed": False,
                "name": valid["name"],
                "steps_would_run": len(valid["steps"]),
                "actions_would_run": valid["action_count"],
                "actions_executed": 0,
                "plan": valid,
                "note": "Simulación únicamente: no se inspeccionó ni modificó el teléfono.",
            }

        started = time.monotonic()
        completed = []
        stage_timings = []
        for index, step in enumerate(valid["steps"]):
            stage_started = time.monotonic()
            prepare_started = time.monotonic()
            if step["launch"]:
                current = self.adapter.launch(step["launch"])
            else:
                current = self.adapter.inspect()
            prepare_ms = int((time.monotonic() - prepare_started) * 1000)
            _check_snapshot(current, step["expect"], f"etapa {index + 1}, antes de actuar")

            action_results = []
            actions_started = time.monotonic()
            for action in step["actions"]:
                # Sensitive operations and explicitly guarded actions always get a fresh read.
                if action.get("requires") or is_external_effect(action):
                    current = self.adapter.inspect()
                    live_assertion = {
                        "package": step["expect"]["package"],
                        "contains": action.get("requires", []),
                        "not_contains": [],
                    }
                    _check_snapshot(current, live_assertion, f"etapa {index + 1}, antes de {action['kind']}")
                action_results.append(self.adapter.perform(action))
            actions_ms = int((time.monotonic() - actions_started) * 1000)

            # A screen assertion is evidence about visible state, not automatically proof of a
            # real-world outcome (for example, a matching draft is not proof a message was sent).
            verify_started = time.monotonic()
            after = self.adapter.inspect()
            verify_wall_ms = int((time.monotonic() - verify_started) * 1000)
            _check_snapshot(after, step["after"], f"etapa {index + 1}, resultado")
            stage_elapsed = int((time.monotonic() - stage_started) * 1000)
            stage_timings.append({
                "stage": index + 1,
                "prepare_ms": prepare_ms,
                "actions_ms": actions_ms,
                "verify_ms": verify_wall_ms,
                "total_ms": stage_elapsed,
            })
            completed.append({
                "stage": index + 1,
                "package": after.package,
                "actions": action_results,
                "postconditions_matched": True,
            })

        total_ms = int((time.monotonic() - started) * 1000)
        return {
            "ok": True,
            "dry_run": False,
            "name": valid["name"],
            "steps_completed": len(completed),
            "actions_executed": valid["action_count"],
            "elapsed_ms": total_ms,
            "postconditions_matched": True,
            "verification_scope": "foreground_package_and_visible_accessibility_text",
            "external_effect_outcome": "not_inferred",
            "timings": {"total_ms": total_ms, "stages": stage_timings},
            "results": completed,
            "note": (
                "Las condiciones visibles configuradas coincidieron. Esto no demuestra por sí solo "
                "un resultado externo; por ejemplo, texto visible no prueba que un mensaje se enviara."
            ),
        }


def _load_json(path: str) -> Any:
    try:
        if path == "-":
            raw = sys.stdin.read()
        else:
            with open(path, "r", encoding="utf-8") as fh:
                raw = fh.read()
        return json.loads(raw)
    except (OSError, json.JSONDecodeError) as exc:
        raise EngineError("invalid_json", "No se pudo leer el plan JSON.", {"reason": str(exc)}) from exc


def _print(value: dict[str, Any]) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="CostPhone Engine: control Android con comprobaciones.")
    parser.add_argument("--version", action="version", version=f"CostPhone Engine {VERSION}")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("inspect", help="Lee la pantalla actual; no ejecuta acciones.")
    launch_p = sub.add_parser("launch", help="Abre una app y confirma que quedó en primer plano.")
    launch_p.add_argument("package")
    validate_p = sub.add_parser("validate", help="Valida un plan JSON sin tocar el teléfono.")
    validate_p.add_argument("plan", help="Ruta JSON o - para stdin.")
    run_p = sub.add_parser("run", help="Valida y ejecuta un plan JSON por etapas.")
    run_p.add_argument("plan", help="Ruta JSON o - para stdin.")
    run_p.add_argument("--dry-run", action="store_true", help="Solo valida; no toca el teléfono.")
    sub.add_parser("mcp", help="Inicia el servidor MCP local por stdio.")
    args = parser.parse_args(argv)
    engine = CostPhoneEngine()
    try:
        if args.command == "inspect":
            _print(engine.inspect())
        elif args.command == "launch":
            _print(engine.launch(args.package))
        elif args.command == "validate":
            _print(engine.validate(_load_json(args.plan)))
        elif args.command == "run":
            plan = _load_json(args.plan)
            _print(engine.run_plan(plan, dry_run=args.dry_run))
        elif args.command == "mcp":
            from mcp_server import serve
            return serve(engine)
        return 0
    except EngineError as exc:
        _print(exc.as_dict())
        return 2
    except KeyboardInterrupt:
        _print({"ok": False, "error": "interrupted", "message": "Interrumpido; no se repetirá ninguna acción."})
        return 130


if __name__ == "__main__":
    sys.exit(main())
