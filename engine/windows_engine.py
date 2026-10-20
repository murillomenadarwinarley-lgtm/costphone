#!/usr/bin/env python3
"""Native Windows desktop adapter for CostPhone; independent of the Android engine."""
from __future__ import annotations

import base64
import ctypes
import io
import os
import shutil
import subprocess
import sys
import time
import webbrowser
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

VERSION = "0.2.0"
MAX_TYPED_TEXT = 4000
MAX_APP_NAME = 260
MAX_UI_CONTROLS = 200
ALLOWED_KEYS = {
    "enter": "enter", "tab": "tab", "escape": "esc", "backspace": "backspace",
    "delete": "delete", "space": "space", "up": "up", "down": "down",
    "left": "left", "right": "right", "home": "home", "end": "end",
    "pageup": "pageup", "pagedown": "pagedown", "f5": "f5",
    "ctrl+l": ("ctrl", "l"), "ctrl+a": ("ctrl", "a"),
    "ctrl+c": ("ctrl", "c"), "ctrl+v": ("ctrl", "v"),
}


class EngineError(Exception):
    """Expected and user-safe error returned to MCP clients."""

    def __init__(self, code: str, message: str, details: dict[str, Any] | None = None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.details = details or {}

    def as_dict(self) -> dict[str, Any]:
        return {"ok": False, "error": self.code, "message": self.message, **self.details}


def validate_url(value: Any) -> str:
    if not isinstance(value, str) or not 1 <= len(value) <= 2048 or any(ord(c) < 32 for c in value):
        raise EngineError("invalid_url", "La URL debe ser texto válido de hasta 2048 caracteres.")
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password:
        raise EngineError("invalid_url", "Solo se permiten URL http/https completas, sin credenciales incrustadas.")
    try:
        if parsed.port is not None and not 1 <= parsed.port <= 65535:
            raise ValueError("port")
    except ValueError as exc:
        raise EngineError("invalid_url", "El puerto de la URL no es válido.") from exc
    return value


def validate_app_name(value: Any) -> str:
    if not isinstance(value, str) or not value.strip() or len(value) > MAX_APP_NAME:
        raise EngineError("invalid_app", "Indica el nombre o la ruta de un ejecutable .exe, sin argumentos.")
    candidate = value.strip()
    if candidate != value or any(c in candidate for c in "\r\n\0"):
        raise EngineError("invalid_app", "Nombre de aplicación no válido.")
    if not candidate.casefold().endswith(".exe"):
        raise EngineError("invalid_app", "Por seguridad, solo se abren ejecutables .exe concretos; no se admiten comandos con argumentos.")
    if '"' in candidate or "'" in candidate or "&" in candidate or "|" in candidate or ";" in candidate:
        raise EngineError("invalid_app", "La ruta contiene caracteres no permitidos.")
    return candidate


def validate_text(value: Any) -> str:
    if not isinstance(value, str) or not value or len(value) > MAX_TYPED_TEXT:
        raise EngineError("invalid_text", f"El texto debe tener entre 1 y {MAX_TYPED_TEXT} caracteres.")
    if "\0" in value:
        raise EngineError("invalid_text", "El texto contiene un carácter no permitido.")
    return value


def validate_coordinate(value: Any, label: str) -> int:
    if type(value) is not int or value < 0 or value > 100000:
        raise EngineError("invalid_coordinates", f"{label} debe ser un entero entre 0 y 100000.")
    return value


def validate_control_selector(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise EngineError("invalid_selector", "selector debe ser un objeto con name y/o automation_id.")
    allowed = {"name", "automation_id", "control_type", "occurrence"}
    extra = set(value) - allowed
    if extra:
        raise EngineError("invalid_selector", "El selector contiene propiedades no permitidas.", {"extra": sorted(extra)})
    selector: dict[str, Any] = {}
    for field in ("name", "automation_id", "control_type"):
        item = value.get(field)
        if item is None or item == "":
            continue
        if not isinstance(item, str) or len(item) > 300 or any(ord(char) < 32 for char in item):
            raise EngineError("invalid_selector", f"selector.{field} debe ser texto válido de hasta 300 caracteres.")
        selector[field] = item
    if not selector.get("name") and not selector.get("automation_id"):
        raise EngineError("invalid_selector", "El selector debe incluir name o automation_id.")
    occurrence = value.get("occurrence")
    if occurrence is not None:
        if type(occurrence) is not int or not 0 <= occurrence < MAX_UI_CONTROLS:
            raise EngineError("invalid_selector", f"selector.occurrence debe ser un entero de 0 a {MAX_UI_CONTROLS - 1}.")
        selector["occurrence"] = occurrence
    return selector


class WindowsDesktopAdapter:
    """Windows API for window metadata and PyAutoGUI for visible desktop input/capture."""

    @staticmethod
    def _require_windows() -> None:
        if sys.platform != "win32":
            raise EngineError("windows_only", "El motor PC debe ejecutarse en una sesión de Windows interactiva.")

    @staticmethod
    def _pyautogui():
        try:
            import pyautogui
        except ImportError as exc:
            raise EngineError("dependency_missing", "Faltan dependencias. Ejecuta install-costphone-pc.ps1 para instalarlas.") from exc
        pyautogui.FAILSAFE = True
        pyautogui.PAUSE = 0.15
        return pyautogui

    def inspect(self) -> dict[str, Any]:
        self._require_windows()
        from ctypes import wintypes

        user32 = ctypes.windll.user32
        user32.GetForegroundWindow.restype = wintypes.HWND
        user32.GetWindowTextLengthW.argtypes = [wintypes.HWND]
        user32.GetWindowTextLengthW.restype = ctypes.c_int
        user32.GetWindowTextW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
        user32.GetWindowTextW.restype = ctypes.c_int
        user32.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
        user32.GetWindowThreadProcessId.restype = wintypes.DWORD
        user32.GetSystemMetrics.argtypes = [ctypes.c_int]
        user32.GetSystemMetrics.restype = ctypes.c_int
        kernel32 = ctypes.windll.kernel32
        kernel32.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        kernel32.OpenProcess.restype = wintypes.HANDLE
        kernel32.QueryFullProcessImageNameW.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
        kernel32.QueryFullProcessImageNameW.restype = wintypes.BOOL
        kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
        kernel32.CloseHandle.restype = wintypes.BOOL

        hwnd = user32.GetForegroundWindow()
        title_len = user32.GetWindowTextLengthW(hwnd) if hwnd else 0
        title_buffer = ctypes.create_unicode_buffer(max(1, min(title_len + 1, 4096)))
        if hwnd:
            user32.GetWindowTextW(hwnd, title_buffer, len(title_buffer))
        process_id = wintypes.DWORD(0)
        if hwnd:
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(process_id))

        process_path = ""
        handle = None
        try:
            if process_id.value:
                handle = kernel32.OpenProcess(0x1000, False, process_id.value)
                if handle:
                    capacity = wintypes.DWORD(32768)
                    buffer = ctypes.create_unicode_buffer(capacity.value)
                    if kernel32.QueryFullProcessImageNameW(handle, 0, buffer, ctypes.byref(capacity)):
                        process_path = buffer.value[:capacity.value]
        except (AttributeError, OSError):
            process_path = ""
        finally:
            if handle:
                try:
                    kernel32.CloseHandle(handle)
                except (AttributeError, OSError):
                    pass

        return {
            "title": title_buffer.value[:1000],
            "window_handle": int(hwnd or 0),
            "process_id": int(process_id.value),
            "process_name": Path(process_path).name if process_path else "",
            "process_path": process_path,
            "screen_width": int(user32.GetSystemMetrics(0)),
            "screen_height": int(user32.GetSystemMetrics(1)),
            "captured_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        }

    @staticmethod
    def _control_metadata(control: Any) -> dict[str, Any]:
        info = control.element_info
        try:
            rect = control.rectangle()
            bounds = {"left": int(rect.left), "top": int(rect.top), "right": int(rect.right), "bottom": int(rect.bottom)}
        except Exception:
            bounds = {"left": None, "top": None, "right": None, "bottom": None}
        try:
            enabled = bool(control.is_enabled())
        except Exception:
            enabled = False
        try:
            visible = bool(control.is_visible())
        except Exception:
            visible = False
        return {
            "name": str(getattr(info, "name", "") or "")[:300],
            "automation_id": str(getattr(info, "automation_id", "") or "")[:300],
            "control_type": str(getattr(info, "control_type", "") or "")[:100],
            "class_name": str(getattr(info, "class_name", "") or "")[:160],
            "enabled": enabled,
            "visible": visible,
            "bounds": bounds,
        }

    def _active_ui_controls(self) -> list[Any]:
        self._require_windows()
        window = self.inspect()
        hwnd = window.get("window_handle")
        if not hwnd:
            raise EngineError("window_not_found", "No pude identificar una ventana activa para consultar sus controles.")
        try:
            from pywinauto import Desktop
        except ImportError as exc:
            raise EngineError("dependency_missing", "Falta pywinauto. Vuelve a ejecutar install-costphone-pc.ps1 para actualizar las dependencias.") from exc
        try:
            root = Desktop(backend="uia").window(handle=hwnd)
            if not root.exists(timeout=2):
                raise EngineError("window_not_found", "La ventana activa ya no está disponible; inspecciona de nuevo.")
            return root.descendants()
        except EngineError:
            raise
        except Exception as exc:
            raise EngineError("uia_unavailable", "Windows UI Automation no pudo leer los controles de la ventana activa.", {"reason": str(exc)[:400]}) from exc

    def list_controls(self, limit: int = 100) -> dict[str, Any]:
        if type(limit) is not int or not 1 <= limit <= MAX_UI_CONTROLS:
            raise EngineError("invalid_limit", f"limit debe ser un entero entre 1 y {MAX_UI_CONTROLS}.")
        controls = self._active_ui_controls()
        items: list[dict[str, Any]] = []
        occurrences: dict[tuple[str, str, str], int] = {}
        visible_count = 0
        for control in controls:
            try:
                details = self._control_metadata(control)
            except Exception:
                continue
            if not details["visible"]:
                continue
            visible_count += 1
            if not details["name"] and not details["automation_id"]:
                continue
            signature = (
                details["name"].casefold(),
                details["automation_id"].casefold(),
                details["control_type"].casefold(),
            )
            occurrence = occurrences.get(signature, 0)
            occurrences[signature] = occurrence + 1
            details["selector"] = {
                "name": details["name"],
                "automation_id": details["automation_id"],
                "control_type": details["control_type"],
                "occurrence": occurrence,
            }
            items.append(details)
            if len(items) >= limit:
                break
        return {
            "controls": items,
            "returned": len(items),
            "visible_descendants": visible_count,
            "truncated": len(items) < visible_count and len(items) >= limit,
            "selection_scope": "solo la ventana que estaba activa al iniciar esta consulta",
        }

    def _resolve_control(self, selector: dict[str, Any]) -> tuple[Any, dict[str, Any]]:
        controls = self._active_ui_controls()
        matches: list[tuple[Any, dict[str, Any]]] = []
        for control in controls:
            try:
                details = self._control_metadata(control)
            except Exception:
                continue
            if not details["visible"]:
                continue
            ok = True
            for key in ("name", "automation_id", "control_type"):
                wanted = selector.get(key)
                if wanted is not None and details[key].casefold() != wanted.casefold():
                    ok = False
                    break
            if ok:
                matches.append((control, details))
        if not matches:
            raise EngineError("control_not_found", "No encontré el control indicado en la ventana activa. Vuelve a listar los controles antes de actuar.", {"selector": selector})
        occurrence = selector.get("occurrence")
        if occurrence is None and len(matches) > 1:
            raise EngineError(
                "ambiguous_control",
                "El selector coincide con varios controles; lista los controles y especifica occurrence para distinguirlos.",
                {"match_count": len(matches), "candidates": [item[1] for item in matches[:10]],
            )
        index = occurrence if occurrence is not None else 0
        if index >= len(matches):
            raise EngineError("control_not_found", "No existe esa occurrence del control en la ventana activa.", {"selector": selector, "match_count": len(matches)})
        return matches[index]

    def click_control(self, selector: dict[str, Any]) -> dict[str, Any]:
        self._require_windows()
        control, details = self._resolve_control(selector)
        if not details["enabled"]:
            raise EngineError("control_disabled", "Windows indica que el control está deshabilitado.", {"control": details})
        try:
            control.click_input()
        except Exception as exc:
            raise EngineError("control_click_failed", "No se pudo hacer clic en el control. Inspecciona la ventana de nuevo; no se repetirá la acción.", {"control": details, "reason": str(exc)[:300]}) from exc
        return {"clicked": True, "control": details}

    def set_control_text(self, selector: dict[str, Any], text: str) -> dict[str, Any]:
        self._require_windows()
        control, details = self._resolve_control(selector)
        if details["control_type"].casefold() != "edit":
            raise EngineError("not_edit_control", "Por seguridad, esta herramienta solo escribe en campos identificados por Windows como Edit.", {"control": details})
        if not details["enabled"]:
            raise EngineError("control_disabled", "Windows indica que el campo está deshabilitado.", {"control": details})
        try:
            try:
                if bool(getattr(control.element_info, "is_password", False)):
                    raise EngineError("password_field_blocked", "No escribiré en un campo identificado como contraseña mediante esta herramienta. Usa el flujo de autenticación de la aplicación.")
            except EngineError:
                raise
            except Exception:
                pass
            control.set_edit_text(text)
        except EngineError:
            raise
        except Exception as exc:
            raise EngineError("control_text_failed", "Windows UI Automation no pudo establecer el texto del campo. El contenido puede no haberse modificado.", {"control": details, "reason": str(exc)[:300]}) from exc
        return {"text_set": True, "characters": len(text), "control": details}

    def screenshot(self) -> dict[str, Any]:
        self._require_windows()
        image = self._pyautogui().screenshot()
        width, height = image.size
        buffer = io.BytesIO()
        image.save(buffer, format="PNG", optimize=True)
        data = buffer.getvalue()
        if len(data) > 12 * 1024 * 1024:
            raise EngineError("screenshot_too_large", "La captura supera el límite de 12 MiB; reduce la resolución o los monitores.")
        return {"screen_width": width, "screen_height": height, "image_base64": base64.b64encode(data).decode("ascii")}

    def launch_app(self, executable: str) -> dict[str, Any]:
        self._require_windows()
        candidate = Path(executable).expanduser()
        resolved = str(candidate) if candidate.is_absolute() and candidate.is_file() else shutil.which(executable)
        if not resolved or not Path(resolved).is_file() or Path(resolved).suffix.casefold() != ".exe":
            raise EngineError("app_not_found", "No encuentro ese .exe. Usa una ruta existente o el nombre de un .exe presente en PATH.", {"app": executable})
        try:
            process = subprocess.Popen([resolved], shell=False, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, close_fds=True)
        except OSError as exc:
            raise EngineError("app_launch_failed", "Windows no pudo abrir la aplicación.", {"reason": str(exc)}) from exc
        return {"app": Path(resolved).name, "process_id": int(process.pid), "launched": True}

    def open_url(self, url: str) -> dict[str, Any]:
        self._require_windows()
        try:
            opened = webbrowser.open(url, new=2, autoraise=True)
        except Exception as exc:
            raise EngineError("open_url_failed", "No se pudo abrir la URL en el navegador predeterminado.", {"reason": str(exc)}) from exc
        if not opened:
            raise EngineError("open_url_failed", "Windows no confirmó la apertura de la URL.")
        return {"url": url, "opened": True}

    def click(self, x: int, y: int) -> dict[str, Any]:
        self._require_windows()
        self._pyautogui().click(x=x, y=y, clicks=1, button="left")
        return {"x": x, "y": y, "clicked": True}

    def type_text(self, text: str) -> dict[str, Any]:
        self._require_windows()
        pyautogui = self._pyautogui()
        try:
            import pyperclip
        except ImportError as exc:
            raise EngineError("dependency_missing", "Falta pyperclip. Ejecuta install-costphone-pc.ps1 para reparar las dependencias.") from exc
        try:
            old_clipboard = pyperclip.paste()
        except Exception:
            old_clipboard = None
        try:
            pyperclip.copy(text)
            pyautogui.hotkey("ctrl", "v")
            time.sleep(0.25)
        except Exception as exc:
            raise EngineError("typing_failed", "No se pudo pegar el texto en la ventana activa.", {"reason": str(exc)}) from exc
        finally:
            if old_clipboard is not None:
                try:
                    pyperclip.copy(old_clipboard)
                except Exception:
                    pass
        return {"characters": len(text), "typed": True, "clipboard_restored": old_clipboard is not None}

    def press_key(self, key: str) -> dict[str, Any]:
        self._require_windows()
        pyautogui = self._pyautogui()
        mapped = ALLOWED_KEYS[key]
        if isinstance(mapped, tuple):
            pyautogui.hotkey(*mapped)
        else:
            pyautogui.press(mapped)
        return {"key": key, "pressed": True}


class WindowsDesktopEngine:
    def __init__(self, adapter: Any | None = None, allow_input: bool | None = None):
        self.adapter = adapter or WindowsDesktopAdapter()
        if allow_input is None:
            allow_input = os.environ.get("COSTPHONE_WINDOWS_ALLOW_INPUT") == "1"
        self.allow_input = bool(allow_input)

    def inspect(self) -> dict[str, Any]:
        return {"ok": True, "version": VERSION, "mode": "windows-local", "window": self.adapter.inspect()}

    def screenshot(self) -> dict[str, Any]:
        return {"ok": True, "version": VERSION, **self.adapter.screenshot()}

    def list_controls(self, limit: Any = 100) -> dict[str, Any]:
        return {"ok": True, "version": VERSION, **self.adapter.list_controls(limit=limit)}

    def click_control(self, selector: Any) -> dict[str, Any]:
        self._require_input()
        return {"ok": True, **self.adapter.click_control(validate_control_selector(selector))}

    def set_control_text(self, selector: Any, text: Any) -> dict[str, Any]:
        self._require_input()
        return {"ok": True, **self.adapter.set_control_text(validate_control_selector(selector), validate_text(text))}

    def launch_app(self, executable: Any) -> dict[str, Any]:
        return {"ok": True, **self.adapter.launch_app(validate_app_name(executable))}

    def open_url(self, url: Any) -> dict[str, Any]:
        return {"ok": True, **self.adapter.open_url(validate_url(url))}

    def _require_input(self) -> None:
        if not self.allow_input:
            raise EngineError(
                "input_disabled",
                "El control de teclado/ratón está desactivado. Reinstala con permiso explícito o inicia el MCP con --allow-input.",
            )

    def click(self, x: Any, y: Any) -> dict[str, Any]:
        self._require_input()
        x = validate_coordinate(x, "x")
        y = validate_coordinate(y, "y")
        window = self.adapter.inspect()
        width, height = window.get("screen_width"), window.get("screen_height")
        if not isinstance(width, int) or not isinstance(height, int) or width <= 0 or height <= 0:
            raise EngineError("screen_unknown", "No pude verificar la resolución de la pantalla.")
        if x >= width or y >= height:
            raise EngineError("invalid_coordinates", "Las coordenadas quedan fuera de la pantalla activa.", {"screen_width": width, "screen_height": height})
        return {"ok": True, **self.adapter.click(x, y)}

    def type_text(self, text: Any) -> dict[str, Any]:
        self._require_input()
        return {"ok": True, **self.adapter.type_text(validate_text(text))}

    def press_key(self, key: Any) -> dict[str, Any]:
        self._require_input()
        if not isinstance(key, str) or key not in ALLOWED_KEYS:
            raise EngineError("invalid_key", "Tecla no permitida. Usa una tecla de la lista admitida.", {"allowed_keys": sorted(ALLOWED_KEYS)})
        return {"ok": True, **self.adapter.press_key(key)}
