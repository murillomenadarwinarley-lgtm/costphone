#!/data/data/com.termux/files/usr/bin/bash
# Installs the optional CostPhone Engine without changing the existing updater/config.
set -euo pipefail

BASE_URL="$(printenv COSTPHONE_ENGINE_BASE_URL || true)"
if [ -z "$BASE_URL" ]; then
  BASE_URL="https://raw.githubusercontent.com/murillomenadarwinarley-lgtm/costphone/engine/costphone-engine-v0"
fi
LIB="$HOME/.local/share/costphone-engine"
BIN="$HOME/bin"
SKILL="$HOME/.config/opencode/skills/costphone-engine"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

step() { printf '\n[CostPhone Engine] %s\n' "$*"; }
fail() { printf '[CostPhone Engine] ERROR: %s\n' "$*" >&2; exit 1; }

step "Descargando la versión aislada"
curl -fsSL "$BASE_URL/engine/costphone_engine.py" -o "$TMP/costphone_engine.py"
curl -fsSL "$BASE_URL/engine/mcp_server.py" -o "$TMP/mcp_server.py"
curl -fsSL "$BASE_URL/engine/test_engine.py" -o "$TMP/test_engine.py"
curl -fsSL "$BASE_URL/skill/costphone-engine/SKILL.md" -o "$TMP/SKILL.md"

step "Validando sintaxis y pruebas sin teléfono"
python3 -m py_compile "$TMP/costphone_engine.py" "$TMP/mcp_server.py" "$TMP/test_engine.py" \
  || fail "El código descargado no compila."
PYTHONPATH="$TMP" python3 -m unittest discover -s "$TMP" -p 'test_*.py' -v \
  || fail "Las pruebas locales fallaron; no se instalará."

step "Instalando sin reemplazar archivos existentes"
mkdir -p "$LIB" "$BIN" "$SKILL"
install -m 644 "$TMP/costphone_engine.py" "$LIB/costphone_engine.py"
install -m 644 "$TMP/mcp_server.py" "$LIB/mcp_server.py"
install -m 644 "$TMP/SKILL.md" "$SKILL/SKILL.md"

cat > "$BIN/costphone-engine" <<'WRAPPER'
#!/data/data/com.termux/files/usr/bin/bash
exec python3 "$HOME/.local/share/costphone-engine/costphone_engine.py" "$@"
WRAPPER
cat > "$BIN/costphone-mcp" <<'WRAPPER'
#!/data/data/com.termux/files/usr/bin/bash
exec python3 "$HOME/.local/share/costphone-engine/mcp_server.py" "$@"
WRAPPER
chmod 755 "$BIN/costphone-engine" "$BIN/costphone-mcp"

step "Instalación terminada"
printf 'CLI: %s/costphone-engine\n' "$BIN"
printf 'MCP: %s/costphone-mcp\n' "$BIN"
printf 'Skill nueva: %s/SKILL.md\n' "$SKILL"
printf '\nNo se modificaron el APK, amctl, el actualizador ni la configuración de OpenCode.\n'
printf 'Para registrar MCP, agrega manualmente una entrada en ~/.config/opencode/opencode.json o opencode.jsonc\n'
printf 'sin reemplazar el archivo. Usa estos valores dentro del objeto mcp existente:\n\n'
printf '  "costphone-engine": {\n'
printf '    "type": "local",\n'
printf '    "command": ["%s/costphone-mcp"],\n' "$BIN"
printf '    "enabled": true,\n'
printf '    "timeout": 120000\n'
printf '  }\n\n'
printf 'Timeout recomendado para el cliente MCP: 120000 ms.\n'
printf 'Reinicia OpenCode y prueba primero phone_inspect y phone_validate_plan.\n'
