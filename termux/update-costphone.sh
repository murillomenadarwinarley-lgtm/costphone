#!/data/data/com.termux/files/usr/bin/bash
# Actualiza los comandos CostPhone y la skill de OpenCode.
set -euo pipefail
BASE_URL="https://raw.githubusercontent.com/murillomenadarwinarley-lgtm/costphone/mi-cambio"
BIN="$HOME/bin"
SKILL_DIR="$HOME/.config/opencode/skills/phone-control"
OLD_SKILL_DIR="$HOME/.config/opencode/skills/android/phone-control"
AGENTS="$HOME/.config/opencode/AGENTS.md"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
step() { printf '\n[CostPhone] %s\n' "$*"; }
fail() { printf '[CostPhone] ERROR: %s\n' "$*" >&2; exit 1; }

step "Instalando dependencias"
pkg install -y curl python termux-am >/dev/null || fail "pkg install falló; revisa conexión y reintenta."
step "Descargando versión actual"
curl -fsSL "$BASE_URL/termux/amctl" -o "$TMP/amctl"
curl -fsSL "$BASE_URL/termux/amctl-snap.py" -o "$TMP/amctl-snap.py"
curl -fsSL "$BASE_URL/skill/phone-control/SKILL.md" -o "$TMP/SKILL.md"
curl -fsSL "$BASE_URL/termux/update-costphone.sh" -o "$TMP/update-costphone"
step "Validando archivos"
bash -n "$TMP/amctl" || fail "amctl tiene sintaxis inválida."
bash -n "$TMP/update-costphone" || fail "el actualizador tiene sintaxis inválida."
python3 -m py_compile "$TMP/amctl-snap.py" || fail "amctl-snap.py no compila."

step "Instalando CLI y skill"
mkdir -p "$BIN" "$HOME/.config/amctl" "$SKILL_DIR"
install -m 755 "$TMP/amctl" "$BIN/amctl"
install -m 755 "$TMP/amctl-snap.py" "$BIN/amctl-snap.py"
install -m 755 "$TMP/update-costphone" "$BIN/update-costphone"
install -m 644 "$TMP/SKILL.md" "$SKILL_DIR/SKILL.md"
# Remove the old nested copy so OpenCode cannot discover duplicate skill IDs.
if [[ -f "$OLD_SKILL_DIR/SKILL.md" ]]; then
    rm -f "$OLD_SKILL_DIR/SKILL.md"
    rmdir "$OLD_SKILL_DIR" 2>/dev/null || true
    rmdir "$(dirname "$OLD_SKILL_DIR")" 2>/dev/null || true
fi
if ! grep -qF 'export PATH="$HOME/bin:$PATH"' "$HOME/.bashrc" 2>/dev/null; then
    printf '\nexport PATH="$HOME/bin:$PATH"\n' >> "$HOME/.bashrc"
fi
export PATH="$BIN:$PATH"

step "Activando Termux-AM por socket"
mkdir -p "$HOME/.termux"
PROPS="$HOME/.termux/termux.properties"
touch "$PROPS"
if grep -qE '^[[:space:]]*run-termux-am-socket-server[[:space:]]*=' "$PROPS"; then
    sed -i -E 's/^[[:space:]]*run-termux-am-socket-server[[:space:]]*=.*/run-termux-am-socket-server = true/' "$PROPS"
else
    printf '\nrun-termux-am-socket-server = true\n' >> "$PROPS"
fi
termux-reload-settings >/dev/null 2>&1 || true

step "Actualizando instrucciones del agente"
mkdir -p "$(dirname "$AGENTS")"
TMP_AGENTS="$(mktemp)"
if [ -f "$AGENTS" ]; then
    awk '
        /<!-- phone-control -->/ { skip=1; next }
        /<!-- end phone-control -->/ { skip=0; next }
        !skip { print }
    ' "$AGENTS" > "$TMP_AGENTS"
fi
cat >> "$TMP_AGENTS" <<'AGENT_BLOCK'

<!-- phone-control -->
For every phone-control task, load the `phone-control` skill before the first amctl call. Read ~/.config/opencode/skills/phone-control/SKILL.md.
If a target app is known, call `amctl launch <package>` first; it now confirms foreground focus. Never call `waittext` or `snap` while Termux is foreground, because the anti-Termux guard intentionally blocks it.
If launch reports that the app did not reach foreground, use at most one fresh `amctl snap` to inspect the actual screen; only tap refs from that latest snapshot. Never use sleeps to guess when an app is ready.
For WhatsApp, use the current screen's `com.whatsapp:id/search_bar_inner_layout` when it appears as "Preguntar a Meta AI o buscar"; do not use stale `menuitem_search`. Search the exact chat name, select its row from the current results, and batch known actions.
Use one short final verification. Avoid PNG/screenshots, status/doctor/logs, Python timing and verbose reports for ordinary tasks. Don't claim success without visible confirmation; do not repeat a send on timeout.
Do not disable the guard or bypass Android authorization. If Accessibility is disconnected, ask the owner to re-enable it. If Shizuku is unavailable, ask the owner to start it and grant CostPhone permission.
Skill file: ~/.config/opencode/skills/phone-control/SKILL.md
<!-- end phone-control -->
AGENT_BLOCK
mv "$TMP_AGENTS" "$AGENTS"
step "Activando la ruta rápida (intento único)"
if command -v rish >/dev/null 2>&1; then
    if SESSION_START_WAIT=4 "$BIN/amctl" session-start; then
        echo "     Shizuku/rish listo; la sesión persistente queda abierta para las tareas."
    else
        echo "     Shizuku/rish no respondió. Se conserva el fallback AM; no reintentes en bucle."
    fi
else
    echo "     rish no está en PATH; se conserva el fallback AM."
    echo "     Abre Shizuku, inicia su servidor y verifica la instalación de rish."
fi

step "Comprobación final"
printf '\nActualización instalada.\n'
printf '1) La skill está en la ruta estándar ~/.config/opencode/skills/phone-control/SKILL.md.\n'
printf '2) Reinicia OpenCode y carga la skill phone-control antes de la primera acción del teléfono.\n'
printf '3) "amctl launch <paquete>" confirma ahora que la app llegó al primer plano; si no, devuelve error pronto.\n'
printf '4) No ejecutes status/doctor/logs en tareas normales; úsalos solo si hay un fallo repetible.\n'
