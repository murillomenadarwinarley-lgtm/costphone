#!/usr/bin/env python3
"""CostPhone Engine: deterministic, fail-closed orchestration above amctl.

This module intentionally contains no shell evaluation and no third-party dependencies.
It orchestrates only allow-listed amctl commands. It is not an LLM or a replacement
for the existing CostPhone app/CLI.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import uuid
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

VERSION = "0.2.0"
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

    def __init__(self, executable: str = "amctl", timeout_s: float = 30.0):
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

        output, rc, elapsed = self._run(args, timeout_s=float(action.get("command_timeout_s", 30)))
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


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _plan_digest(validated_plan: dict[str, Any]) -> str:
    canonical = json.dumps(validated_plan, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


class RunJournal:
    """Private, atomic task journal. It stores progress only, never the plan or screen text."""

    def __init__(self, directory: str | os.PathLike[str] | None = None):
        configured = directory or os.environ.get("COSTPHONE_RUNS_DIR")
        self.directory = (
            Path(configured).expanduser()
            if configured
            else Path.home() / ".local" / "state" / "costphone-engine" / "runs"
        )

    @staticmethod
    def validate_run_id(run_id: Any) -> str:
        if not isinstance(run_id, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", run_id):
            raise EngineError("invalid_run_id", "run_id debe contener de 1 a 64 letras, números, guiones o guiones bajos.")
        return run_id

    def _path(self, run_id: str) -> Path:
        return self.directory / (self.validate_run_id(run_id) + ".json")

    def save(self, record: dict[str, Any]) -> None:
        path = self._path(record.get("run_id"))
        record["updated_at"] = _utc_now()
        try:
            self.directory.mkdir(parents=True, exist_ok=True)
            try:
                os.chmod(self.directory, 0o700)
            except OSError:
                pass
            temp = path.with_name(path.name + "." + uuid.uuid4().hex + ".tmp")
            try:
                with temp.open("x", encoding="utf-8") as fh:
                    json.dump(record, fh, ensure_ascii=False, separators=(",", ":"))
                    fh.write("\n")
                try:
                    os.chmod(temp, 0o600)
                except OSError:
                    pass
                os.replace(temp, path)
            finally:
                try:
                    temp.unlink()
                except FileNotFoundError:
                    pass
        except OSError as exc:
            raise EngineError(
                "journal_write_failed",
                "No pude guardar el punto de control local. La ejecución no continuará sin un registro fiable.",
                {"run_id": record.get("run_id"), "reason": str(exc)},
            ) from exc

    def create(self, validated_plan: dict[str, Any]) -> dict[str, Any]:
        run_id = "cp_" + uuid.uuid4().hex
        record: dict[str, Any] = {
            "schema_version": 1,
            "run_id": run_id,
            "name": validated_plan["name"],
            "plan_sha256": _plan_digest(validated_plan),
            "status": "running",
            "created_at": _utc_now(),
            "updated_at": _utc_now(),
            "next_stage": 1,
            "completed_stages": [],
            "current_stage": None,
            "last_error": None,
        }
        self.save(record)
        return record

    def load(self, run_id: str) -> dict[str, Any]:
        path = self._path(run_id)
        try:
            with path.open("r", encoding="utf-8") as fh:
                record = json.load(fh)
        except FileNotFoundError as exc:
            raise EngineError("run_not_found", "No existe un registro local para ese run_id.", {"run_id": run_id}) from exc
        except (OSError, json.JSONDecodeError) as exc:
            raise EngineError("journal_corrupt", "No pude leer el registro local de la tarea.", {"run_id": run_id}) from exc
        if not isinstance(record, dict) or record.get("schema_version") != 1 or record.get("run_id") != run_id:
            raise EngineError("journal_corrupt", "El registro local no tiene un formato válido.", {"run_id": run_id})
        return record


class CostPhoneEngine:
    def __init__(
        self,
        adapter: Any | None = None,
        allow_external_effects: bool | None = None,
        journal_dir: str | os.PathLike[str] | None = None,
    ):
        self.adapter = adapter or AmctlAdapter()
        if allow_external_effects is None:
            allow_external_effects = os.environ.get("COSTPHONE_ALLOW_EXTERNAL_EFFECTS") == "1"
        self.allow_external_effects = bool(allow_external_effects)
        self.journal = RunJournal(journal_dir)

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

    @staticmethod
    def _stage_summary(index: int, package: str, actions_completed: int, reconciled: bool = False) -> dict[str, Any]:
        return {
            "stage": index + 1,
            "package": package,
            "actions_completed": actions_completed,
            "postconditions_matched": True,
            "reconciled_after_interruption": reconciled,
        }

    def _mark_attention(
        self,
        record: dict[str, Any],
        code: str,
        message: str,
        details: dict[str, Any] | None = None,
    ) -> None:
        record["status"] = "needs_attention"
        record["last_error"] = {"code": code, "message": message}
        self.journal.save(record)
        extra = {
            "run_id": record["run_id"],
            "run_status": record["status"],
            "completed_stages": len(record.get("completed_stages", [])),
            "next_stage": record.get("next_stage", 1),
            "resume_available": False,
        }
        extra.update(details or {})
        raise EngineError(code, message, extra)

    def get_run(self, run_id: str) -> dict[str, Any]:
        record = self.journal.load(run_id)
        # Journal schema intentionally excludes task text, action values and screen snapshots.
        return {"ok": True, "run": record}

    def run_plan(self, plan: Any, dry_run: bool = False) -> dict[str, Any]:
        if not isinstance(dry_run, bool):
            raise EngineError("invalid_argument", "dry_run debe ser un booleano.")

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
                "note": "Simulación únicamente: no se inspeccionó ni modificó el teléfono ni se creó un registro de ejecución.",
            }

        record = self.journal.create(valid)
        return self._execute_validated_plan(valid, record, start_index=0, action_offset=0, resume_current=False)

    def resume_plan(self, plan: Any, run_id: str) -> dict[str, Any]:
        """Resume only from journaled, freshly revalidated states; never blindly replays an uncertain action."""
        valid = validate_plan(plan, self.allow_external_effects)
        record = self.journal.load(run_id)
        if record.get("plan_sha256") != _plan_digest(valid):
            raise EngineError(
                "run_plan_mismatch",
                "El plan no coincide con el que creó este run_id. No ejecutaré un plan distinto sobre el registro.",
                {"run_id": run_id, "resume_available": False},
            )
        if record.get("status") == "completed":
            return {
                "ok": True,
                "run_id": run_id,
                "resumed": False,
                "already_completed": True,
                "steps_completed": len(record.get("completed_stages", [])),
                "results": record.get("completed_stages", []),
                "note": "La tarea ya figura completada; no se repitió ninguna acción.",
            }

        current_stage = record.get("current_stage")
        start_index = max(0, int(record.get("next_stage", 1)) - 1)
        action_offset = 0
        resume_current = False

        if current_stage is not None:
            index = int(current_stage.get("stage", 0)) - 1
            if index < 0 or index >= len(valid["steps"]):
                self._mark_attention(record, "journal_corrupt", "El punto de control indica una etapa fuera del plan.")
            step = valid["steps"][index]
            phase = current_stage.get("phase")
            actions_completed = int(current_stage.get("actions_completed", 0))
            total_actions = len(step["actions"])

            # If the process died while an action was in flight, inspect before deciding anything.
            if phase == "action_in_progress":
                snapshot = self._resume_snapshot(record, "No pude inspeccionar la tarea de forma segura al reanudar.")
                try:
                    _check_snapshot(snapshot, step["after"], "reanudar: resultado visible de la etapa")
                except EngineError:
                    self._mark_attention(
                        record,
                        "resume_ambiguous",
                        "Una acción pudo haber surtido efecto, pero la condición final no coincide. No repetiré la acción.",
                        {"run_id": run_id, "failed_stage": index + 1, "action_may_have_taken_effect": True},
                    )
                if current_stage.get("action_external_effect"):
                    self._mark_attention(
                        record,
                        "external_outcome_uncertain",
                        "La pantalla coincide con la condición final, pero una acción externa pudo ejecutarse antes de la interrupción. Revisa el resultado antes de continuar.",
                        {
                            "run_id": run_id,
                            "failed_stage": index + 1,
                            "visible_postconditions_matched": True,
                            "action_may_have_taken_effect": True,
                        },
                    )
                if int(current_stage.get("action_index", -1)) != total_actions - 1:
                    self._mark_attention(
                        record,
                        "resume_ambiguous",
                        "La pantalla parece coincidir con el final, pero quedaban acciones en esta etapa. No omitiré pasos sin evidencia.",
                        {"run_id": run_id, "failed_stage": index + 1},
                    )
                record["completed_stages"].append(
                    self._stage_summary(index, snapshot.package, total_actions, reconciled=True)
                )
                record["next_stage"] = index + 2
                record["current_stage"] = None
                record["status"] = "running"
                record["last_error"] = None
                self.journal.save(record)
                start_index = index + 1
            elif phase == "verifying" or actions_completed == total_actions:
                snapshot = self._resume_snapshot(record, "No pude verificar el resultado de la etapa al reanudar.")
                try:
                    _check_snapshot(snapshot, step["after"], "reanudar: resultado visible de la etapa")
                except EngineError:
                    self._mark_attention(
                        record,
                        "resume_result_mismatch",
                        "Las acciones registradas terminaron, pero la condición final no coincide. No repetiré la etapa automáticamente.",
                        {"run_id": run_id, "failed_stage": index + 1},
                    )
                record["completed_stages"].append(
                    self._stage_summary(index, snapshot.package, total_actions, reconciled=True)
                )
                record["next_stage"] = index + 2
                record["current_stage"] = None
                record["status"] = "running"
                record["last_error"] = None
                self.journal.save(record)
                start_index = index + 1
            elif actions_completed == 0:
                snapshot = self._resume_snapshot(record, "No pude revalidar el estado previo a las acciones.")
                try:
                    _check_snapshot(snapshot, step["expect"], "reanudar: estado previo")
                except EngineError:
                    self._mark_attention(
                        record,
                        "resume_context_mismatch",
                        "La pantalla actual no coincide con el estado esperado. Necesita un nuevo plan basado en el estado real.",
                        {"run_id": run_id, "failed_stage": index + 1},
                    )
                current_stage["phase"] = "preparing_resume"
                current_stage["action_index"] = None
                current_stage["action_kind"] = None
                current_stage["action_external_effect"] = False
                self.journal.save(record)
                start_index = index
                action_offset = 0
                resume_current = True
            else:
                # A prior action returned successfully; continue only if the next action's
                # explicitly declared context is still visible on a fresh Android snapshot.
                snapshot = self._resume_snapshot(record, "No pude revalidar el contexto de la siguiente acción.")
                next_action = step["actions"][actions_completed]
                required = next_action.get("requires", [])
                try:
                    _check_snapshot(
                        snapshot,
                        {"package": step["expect"]["package"], "contains": required, "not_contains": []},
                        "reanudar: contexto de la siguiente acción",
                    )
                except EngineError:
                    self._mark_attention(
                        record,
                        "resume_context_mismatch",
                        "El contexto requerido para continuar ya no está visible. No repetiré las acciones anteriores.",
                        {"run_id": run_id, "failed_stage": index + 1, "actions_completed": actions_completed},
                    )
                current_stage["phase"] = "preparing_resume"
                current_stage["action_index"] = None
                current_stage["action_kind"] = None
                current_stage["action_external_effect"] = False
                self.journal.save(record)
                start_index = index
                action_offset = actions_completed
                resume_current = True
        elif record.get("status") == "needs_attention":
            raise EngineError(
                "resume_needs_review",
                "Este registro necesita revisión y no contiene un punto seguro para continuar.",
                {"run_id": run_id, "resume_available": False},
            )

        return self._execute_validated_plan(
            valid,
            record,
            start_index=start_index,
            action_offset=action_offset,
            resume_current=resume_current,
            resumed=True,
        )

    def _resume_snapshot(self, record: dict[str, Any], message: str) -> Snapshot:
        try:
            return self.adapter.inspect()
        except EngineError as exc:
            self._mark_attention(
                record,
                "resume_inspect_failed",
                message,
                {"run_id": record["run_id"], "underlying_error": exc.code},
            )
        raise AssertionError("unreachable")

    def _execute_validated_plan(
        self,
        valid: dict[str, Any],
        record: dict[str, Any],
        start_index: int,
        action_offset: int,
        resume_current: bool,
        resumed: bool = False,
    ) -> dict[str, Any]:
        started = time.monotonic()
        stage_timings: list[dict[str, int]] = []
        steps = valid["steps"]

        for index in range(start_index, len(steps)):
            step = steps[index]
            continuing_current = resume_current and index == start_index
            stage_started = time.monotonic()
            prepare_started = time.monotonic()

            if not continuing_current:
                record["current_stage"] = {
                    "stage": index + 1,
                    "phase": "preparing",
                    "actions_completed": 0,
                    "actions_total": len(step["actions"]),
                    "action_index": None,
                    "action_kind": None,
                    "action_external_effect": False,
                }
                record["status"] = "running"
                record["last_error"] = None
                self.journal.save(record)

            try:
                current = self.adapter.inspect()
                if continuing_current and action_offset > 0:
                    required = step["actions"][action_offset].get("requires", [])
                    _check_snapshot(
                        current,
                        {"package": step["expect"]["package"], "contains": required, "not_contains": []},
                        f"etapa {index + 1}, contexto al reanudar",
                    )
                else:
                    if not continuing_current and step["launch"]:
                        current = self.adapter.launch(step["launch"])
                    _check_snapshot(current, step["expect"], f"etapa {index + 1}, antes de actuar")

                prepare_ms = int((time.monotonic() - prepare_started) * 1000)
                current_state = record["current_stage"]
                current_state["phase"] = "ready_for_action"
                self.journal.save(record)

                action_results = []
                actions_started = time.monotonic()
                first_action = action_offset if continuing_current else 0
                for action_index in range(first_action, len(step["actions"])):
                    action = step["actions"][action_index]
                    if action.get("requires") or is_external_effect(action):
                        fresh = self.adapter.inspect()
                        live_assertion = {
                            "package": step["expect"]["package"],
                            "contains": action.get("requires", []),
                            "not_contains": [],
                        }
                        _check_snapshot(fresh, live_assertion, f"etapa {index + 1}, antes de {action['kind']}")

                    current_state = record["current_stage"]
                    current_state["phase"] = "action_in_progress"
                    current_state["action_index"] = action_index
                    current_state["action_kind"] = action["kind"]
                    current_state["action_external_effect"] = is_external_effect(action)
                    self.journal.save(record)

                    action_results.append(self.adapter.perform(action))
                    current_state["actions_completed"] = action_index + 1
                    current_state["phase"] = "action_completed"
                    current_state["action_index"] = None
                    current_state["action_kind"] = None
                    current_state["action_external_effect"] = False
                    self.journal.save(record)

                actions_ms = int((time.monotonic() - actions_started) * 1000)
                current_state = record["current_stage"]
                current_state["phase"] = "verifying"
                self.journal.save(record)

                verify_started = time.monotonic()
                after = self.adapter.inspect()
                verify_ms = int((time.monotonic() - verify_started) * 1000)
                _check_snapshot(after, step["after"], f"etapa {index + 1}, resultado")

                stage_elapsed = int((time.monotonic() - stage_started) * 1000)
                stage_timings.append({
                    "stage": index + 1,
                    "prepare_ms": prepare_ms,
                    "actions_ms": actions_ms,
                    "verify_ms": verify_ms,
                    "total_ms": stage_elapsed,
                })
                record["completed_stages"].append(
                    self._stage_summary(index, after.package, len(step["actions"]))
                )
                record["next_stage"] = index + 2
                record["current_stage"] = None
                record["status"] = "running"
                record["last_error"] = None
                self.journal.save(record)
                action_offset = 0
                resume_current = False
            except EngineError as exc:
                current_state = record.get("current_stage") or {}
                uncertain_action = current_state.get("phase") == "action_in_progress"
                record["status"] = "needs_attention" if uncertain_action else "paused"
                record["last_error"] = {"code": exc.code, "message": exc.message}
                self.journal.save(record)
                exc.details.update({
                    "run_id": record["run_id"],
                    "run_status": record["status"],
                    "completed_stages": len(record.get("completed_stages", [])),
                    "next_stage": record.get("next_stage", 1),
                    "failed_stage": index + 1,
                    "resume_available": True,
                })
                raise

        total_ms = int((time.monotonic() - started) * 1000)
        record["status"] = "completed"
        record["current_stage"] = None
        record["next_stage"] = len(steps) + 1
        record["last_error"] = None
        self.journal.save(record)
        completed = record["completed_stages"]
        return {
            "ok": True,
            "run_id": record["run_id"],
            "resumed": resumed,
            "dry_run": False,
            "name": valid["name"],
            "steps_completed": len(completed),
            "actions_executed": sum(int(item.get("actions_completed", 0)) for item in completed),
            "elapsed_ms": total_ms,
            "postconditions_matched": True,
            "verification_scope": "foreground_package_and_visible_accessibility_text",
            "external_effect_outcome": "not_inferred",
            "timings": {"total_ms": total_ms, "stages": stage_timings},
            "results": completed,
            "run_status": record["status"],
            "note": (
                "Las condiciones visibles configuradas coincidieron. El registro conserva el progreso sin guardar el plan, "
                "el texto de las acciones ni capturas. Esto no demuestra por sí solo un resultado externo."
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
    run_p = sub.add_parser("run", help="Valida y ejecuta un plan JSON por etapas con punto de control local.")
    run_p.add_argument("plan", help="Ruta JSON o - para stdin.")
    run_p.add_argument("--dry-run", action="store_true", help="Solo valida; no toca el teléfono.")
    resume_p = sub.add_parser("resume", help="Revalida y continúa una ejecución registrada.")
    resume_p.add_argument("run_id", help="ID devuelto por el comando run.")
    resume_p.add_argument("plan", help="El mismo plan JSON usado al iniciar la tarea.")
    status_p = sub.add_parser("status", help="Lee el estado local de una ejecución, sin tocar el teléfono.")
    status_p.add_argument("run_id", help="ID devuelto por el comando run.")
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
        elif args.command == "resume":
            plan = _load_json(args.plan)
            _print(engine.resume_plan(plan, args.run_id))
        elif args.command == "status":
            _print(engine.get_run(args.run_id))
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
