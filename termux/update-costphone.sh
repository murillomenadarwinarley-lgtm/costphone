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
For Android phone tasks, use amctl and the phone-control skill.
Fast path: launch the target app; prefer clickid or click by visible text; use snap only to discover unknown controls; use screenshots only when accessibility gives no useful content.
Batch known steps with amctl batch and verify results with amctl waittext. Avoid screenshots between ordinary taps.
If commands fail, run amctl status and amctl doctor; never claim success without a successful tool result or verification.
If accessibility is disconnected, tell the user to open CostPhone, press Repair connection, then re-enable it in Android Accessibility settings. If Shizuku is unavailable, guide the user to start its service and grant CostPhone permission; Android authorization cannot be bypassed.
Skill file: ~/.config/opencode/skills/android/phone-control/SKILL.md
<!-- end phone-control -->
AGENT_BLOCK
mv "$TMP_AGENTS" "$AGENTS"
step "Comprobación final"
printf '\nActualización instalada.\n'
printf '1) Cierra Termux por completo y vuelve a abrirlo para activar el socket rápido.\n'
printf '2) Si Android desactivó Accesibilidad tras actualizar CostPhone, actívala otra vez.\n'
printf '3) Ejecuta: amctl status && amctl doctor\n'
printf '4) Si hay lag/cierre, reproduce una vez y ejecuta: amctl logs\n'
