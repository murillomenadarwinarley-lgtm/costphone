#!/data/data/com.termux/files/usr/bin/bash
# Actualiza los comandos CostPhone y la skill de OpenCode.
set -euo pipefail
BASE_URL="https://raw.githubusercontent.com/murillomenadarwinarley-lgtm/costphone/mi-cambio"
BIN="$HOME/bin"
SKILL_DIR="$HOME/.config/opencode/skills/android/phone-control"
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
For Android tasks, prioritize completion over narration. Read ~/.config/opencode/skills/android/phone-control/SKILL.md.
At the start of a new agent session, if rish is installed, try SESSION_START_WAIT=4 amctl session-start once; reuse the session if active. If it fails, do not retry each task and use the AM fallback.
Use only selectors confirmed by the current screen. For WhatsApp search, if present in the snapshot, clickid com.whatsapp:id/search_bar_inner_layout (label: "Preguntar a Meta AI o buscar"), not stale menuitem_search.
Batch dependent actions; use one short wait for the target state, at most one textual snapshot to recover, then verify the final result once. Avoid PNG/screenshots, status/doctor/logs, Python timing, repeated waits and narration in ordinary tasks.
Never claim success without a result check. If the same action fails twice, stop and explain the blocker. Do not disable the app guard or bypass Android authorization.
If Accessibility is disconnected, ask the owner to re-enable CostPhone in Android Accessibility settings. If Shizuku is unavailable, ask the owner to start its service and grant permission; do not attempt to bypass authorization.
Skill file: ~/.config/opencode/skills/android/phone-control/SKILL.md
<!-- end phone-control -->
AGENT_BLOCK
mv "$TMP_AGENTS" "$AGENTS"
step "Comprobación final"
printf '\nActualización instalada.\n'
printf '1) Reinicia OpenCode para que cargue la skill actualizada.\n'
printf '2) En una sesión nueva del agente, si Shizuku/rish está disponible, ejecutar una vez: SESSION_START_WAIT=4 amctl session-start\n'
printf '3) Si Android desactivó Accesibilidad tras actualizar CostPhone, actívala otra vez en Ajustes → Accesibilidad → CostPhone.\n'
printf '4) No ejecutes status/doctor/logs en tareas normales; úsalos solo si hay un fallo repetible.\n'
