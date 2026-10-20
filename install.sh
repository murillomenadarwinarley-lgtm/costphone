#!/data/data/com.termux/files/usr/bin/bash
#==============================================================================
# CostPhone — instalador todo-en-uno para Termux
#
# Instala:
#   1. Dependencias: termux-am (transporte "am") + python
#   2. Modificación del "am" de Termux (socket server + PATH)
#   3. amctl + amctl-snap.py  ->  ~/bin
#   4. Skill "phone-control"  ->  ~/.config/opencode/skills/android/
#   5. Bloque de registro    ->  ~/.config/opencode/AGENTS.md
#   6. APK CostPhone          ->  abre el instalador en el teléfono
#
# Uso:  bash install.sh
#==============================================================================
set -u

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN="$HOME/bin"
SKILL_DIR="$HOME/.config/opencode/skills/android/phone-control"
AGENTS="$HOME/.config/opencode/AGENTS.md"
APK_SRC=""

G='\033[0;32m'; Y='\033[1;33m'; R='\033[0;31m'; N='\033[0m'
step() { echo -e "${G}[+]${N} $*"; }
warn() { echo -e "${Y}[!]${N} $*"; }

echo "══════════════════════════════════════════════════"
echo "  CostPhone installer — control del teléfono desde Termux"
echo "══════════════════════════════════════════════════"

# --- 1. Dependencias -------------------------------------------------------
step "1/6  Instalando dependencias (termux-am, python)..."
pkg install -y termux-am python >/dev/null 2>&1 || warn "pkg install falló — revisa tu conexión y re-ejecuta"

# --- 2. Modificar el "am" de Termux ---------------------------------------
step "2/6  Configurando el transporte am (socket server + PATH)..."
mkdir -p "$HOME/.termux"
TP="$HOME/.termux/termux.properties"
touch "$TP"
if ! grep -q 'run-termux-am-socket-server' "$TP"; then
    echo 'run-termux-am-socket-server = true' >> "$TP"
fi
termux-reload-settings >/dev/null 2>&1 || true
if ! grep -q 'HOME/bin' "$HOME/.bashrc" 2>/dev/null; then
    echo 'export PATH="$HOME/bin:$PATH"' >> "$HOME/.bashrc"
fi
export PATH="$BIN:$PATH"
echo "     am --version  =>  $(am --version 2>/dev/null || echo 'NO INSTALADO')"

# --- 3. amctl --------------------------------------------------------------
step "3/6  Instalando amctl + amctl-snap.py en ~/bin..."
mkdir -p "$BIN"
cp "$REPO_DIR/termux/amctl" "$BIN/amctl"
cp "$REPO_DIR/termux/amctl-snap.py" "$BIN/amctl-snap.py"
chmod +x "$BIN/amctl" "$BIN/amctl-snap.py"
echo "     listo: $BIN/amctl"

# --- 4. Skill para OpenCode ------------------------------------------------
step "4/6  Instalando la skill 'phone-control' para OpenCode..."
if [ -d "$HOME/.config/opencode" ]; then
    mkdir -p "$SKILL_DIR"
    cp "$REPO_DIR/skill/phone-control/SKILL.md" "$SKILL_DIR/SKILL.md"
    echo "     listo: $SKILL_DIR/SKILL.md"
else
    warn "No hay ~/.config/opencode (¿OpenCode no instalado?) — skill omitida"
fi

# --- 5. AGENTS.md ----------------------------------------------------------
step "5/6  Registrando la skill en AGENTS.md..."
if [ -d "$HOME/.config/opencode" ]; then
    touch "$AGENTS"
    if ! grep -q 'phone-control' "$AGENTS" 2>/dev/null; then
        cat >> "$AGENTS" <<'EOF'

<!-- phone-control -->
Phone tasks (control the user's Android apps): use `amctl` + skill `phone-control`.
Loop: `amctl launch <pkg>` → `amctl snap` (refs @pN) → `amctl tapref @pN` / `type` / `click` → re-snap.
Read screen as text: `amctl tree` (full) or `amctl snap -a`. Screenshot: `amctl see`.
Guide: ~/.config/opencode/skills/android/phone-control/SKILL.md
<!-- end phone-control -->
EOF
        echo "     bloque añadido a $AGENTS"
    else
        echo "     ya estaba registrado en $AGENTS"
    fi
fi

# --- 6. APK ----------------------------------------------------------------
step "6/6  Instalando la app CostPhone (servicio de accesibilidad)..."

# Nunca prefieras la APK heredada solo porque su nombre de versión es mayor.
# El instalador debe usar primero el build actual (v1.3 + Shizuku + handshake).
CURRENT_APK="$REPO_DIR/app/build/CostPhone.apk"
if [[ -s "$CURRENT_APK" ]]; then
    APK_SRC="$CURRENT_APK"
elif command -v gradle >/dev/null 2>&1 && [[ -s "${ANDROID_HOME:-$HOME/android-sdk}/platforms/android-36/android.jar" ]]; then
    step "No hay APK actual; compilando CostPhone v1.3 desde el código..."
    if (cd "$REPO_DIR/app" && bash build.sh); then
        [[ -s "$CURRENT_APK" ]] && APK_SRC="$CURRENT_APK"
    else
        warn "La compilación falló; no se instalará silenciosamente una APK antigua."
    fi
fi

if [[ -z "$APK_SRC" ]]; then
    LEGACY_APK="$(ls -1 "$REPO_DIR"/apk/CostPhone-v*.apk 2>/dev/null | sort -V | tail -1 || true)"
    if [[ -n "$LEGACY_APK" && -s "$LEGACY_APK" ]]; then
        warn "Solo está disponible la APK heredada $(basename "$LEGACY_APK"); no contiene la nueva integración Shizuku ni el diagnóstico de conexión."
        if [[ -t 0 ]]; then
            read -r -p "¿Instalar esa versión antigua para usar el modo clásico de Accesibilidad/Termux? [s/N] " legacy_choice
            [[ "$legacy_choice" =~ ^[sS]$ ]] && APK_SRC="$LEGACY_APK"
        elif [[ "${COSTPHONE_ALLOW_LEGACY_APK:-0}" == "1" ]]; then
            APK_SRC="$LEGACY_APK"
        else
            warn "No se instala una APK antigua sin confirmación. Compila primero con 'cd app && bash build.sh' o descarga el APK actual desde la ejecución de GitHub Actions."
        fi
    else
        warn "No se encontró CostPhone.apk. Compila con 'cd app && bash build.sh' o descarga el APK actual desde GitHub Actions."
    fi
fi

if [[ -n "$APK_SRC" && -s "$APK_SRC" ]]; then
    APK_DEST="/sdcard/Download/$(basename "$APK_SRC")"
    cp "$APK_SRC" "$APK_DEST"
    echo "     APK seleccionada: $APK_SRC"
    termux-open "$APK_DEST" 2>/dev/null || warn "Abre manualmente: $APK_DEST"
    echo ""
    echo "  ┌─────────────────────────────────────────────────┐"
    echo "  │  EN EL TELÉFONO:                                │"
    echo "  │   1. Toca 'Instalar'                            │"
    echo "  │   2. Ajustes → Accesibilidad → CostPhone        │"
    echo "  │      → Activar y confirmar                      │"
    echo "  │   3. Abre CostPhone → Conexión → Doctor         │"
    echo "  │      y ejecuta 'amctl doctor' en Termux         │"
    echo "  └─────────────────────────────────────────────────┘"
fi

echo ""
echo "══════════════════════════════════════════════════"
echo "  PRUEBA DE CONEXIÓN: amctl doctor"
echo "  Después: amctl snap"
echo "  Si doctor no responde, revisa Accesibilidad y vuelve a instalar"
echo "  la APK actual (no uses una APK heredada si buscas Shizuku)."
echo "══════════════════════════════════════════════════"
