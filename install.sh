#!/data/data/com.termux/files/usr/bin/bash
#==============================================================================
# CostPhone — instalador para Termux. No instala builds con una firma distinta.
#==============================================================================
set -u

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN="$HOME/bin"
SKILL_DIR="$HOME/.config/opencode/skills/android/phone-control"
AGENTS="$HOME/.config/opencode/AGENTS.md"
APK_SRC=""

G='\033[0;32m'; Y='\033[1;33m'; N='\033[0m'
step() { echo -e "${G}[+] ${N}$*"; }
warn() { echo -e "${Y}[!] ${N}$*"; }

echo "══════════════════════════════════════════════════"
echo "  CostPhone installer — control del teléfono desde Termux"
echo "══════════════════════════════════════════════════"

# --- 1. Dependencias -------------------------------------------------------
step "1/6  Instalando dependencias (termux-am, python)..."
pkg install -y termux-am python >/dev/null 2>&1 || warn "pkg install falló — revisa tu conexión y re-ejecuta"

# --- 2. Configurar termux-am ------------------------------------------------
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
echo "     am --version => $(am --version 2>/dev/null || echo 'NO INSTALADO')"

# --- 3. amctl ----------------------------------------------------------------
step "3/6  Instalando amctl + amctl-snap.py en ~/bin..."
mkdir -p "$BIN"
cp "$REPO_DIR/termux/amctl" "$BIN/amctl"
cp "$REPO_DIR/termux/amctl-snap.py" "$BIN/amctl-snap.py"
chmod +x "$BIN/amctl" "$BIN/amctl-snap.py"
echo "     listo: $BIN/amctl"

# --- 4. Skill para OpenCode -------------------------------------------------
step "4/6  Instalando la skill 'phone-control' para OpenCode..."
if [ -d "$HOME/.config/opencode" ]; then
    mkdir -p "$SKILL_DIR"
    cp "$REPO_DIR/skill/phone-control/SKILL.md" "$SKILL_DIR/SKILL.md"
    echo "     listo: $SKILL_DIR/SKILL.md"
else
    warn "No hay ~/.config/opencode (¿OpenCode no instalado?) — skill omitida"
fi

# --- 5. AGENTS.md ------------------------------------------------------------
step "5/6  Registrando la skill en AGENTS.md..."
if [ -d "$HOME/.config/opencode" ]; then
    touch "$AGENTS"
    python3 - "$AGENTS" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8") if path.exists() else ""
start = "<!-- phone-control -->"
end = "<!-- end phone-control -->"
block = """<!-- phone-control -->
Phone tasks (control the user's Android apps): accept natural-language goals and use the phone-control skill. For multi-step work, create and run a task-specific adaptive Bash script; do not make the user type amctl commands.
Use amctl doctor only after install/update or when the Termux-to-CostPhone transport fails. Then use amctl snap for the current interactive screen, amctl tapref with fresh refs, and re-snapshot after screen transitions. Use tree/see only when snap is insufficient.
Verify the requested outcome rather than reporting success because a command returned.
Guide: ~/.config/opencode/skills/android/phone-control/SKILL.md
<!-- end phone-control -->"""
if start in text and end in text:
    before = text.split(start, 1)[0]
    after = text.split(end, 1)[1]
    text = before + block + after
else:
    text = text.rstrip() + "\n\n" + block + "\n"
path.write_text(text, encoding="utf-8")
PY
    echo "     bloque de agente actualizado en $AGENTS"
fi

# --- 6. APK ------------------------------------------------------------------
step "6/6  Preparando CostPhone v1.3..."

CURRENT_APK="$REPO_DIR/app/build/CostPhone.apk"
SDK_ROOT="${ANDROID_HOME:-$HOME/android-sdk}"
AAPT2="$SDK_ROOT/build-tools/36.0.0/aapt2"
APKSIGNER="$SDK_ROOT/build-tools/36.0.0/apksigner"
LEGACY_APK="$(ls -1 "$REPO_DIR"/apk/CostPhone-v*.apk 2>/dev/null | sort -V | tail -1 || true)"

certificate_sha256() {
    local candidate="$1"
    [[ -x "$APKSIGNER" && -s "$candidate" ]] || return 1
    "$APKSIGNER" verify --print-certs "$candidate" 2>/dev/null |
        awk -F': ' '/Signer #1 certificate SHA-256 digest:/ {print $2; exit}' |
        tr -d '[:space:]' | tr '[:upper:]' '[:lower:]'
}

is_current_apk() {
    local candidate="$1"
    [[ -s "$candidate" && -x "$AAPT2" && -n "$LEGACY_APK" && -s "$LEGACY_APK" ]] || return 1
    "$AAPT2" dump badging "$candidate" 2>/dev/null |
        grep -q "package: name='com.accessdroid.termux'.*versionName='1.3'" || return 1
    local expected actual
    expected="$(certificate_sha256 "$LEGACY_APK" || true)"
    actual="$(certificate_sha256 "$candidate" || true)"
    [[ -n "$expected" && "$expected" == "$actual" ]]
}

if is_current_apk "$CURRENT_APK"; then
    APK_SRC="$CURRENT_APK"
elif command -v gradle >/dev/null 2>&1 && [[ -s "$SDK_ROOT/platforms/android-36/android.jar" ]]; then
    step "Intentando compilar CostPhone..."
    if (cd "$REPO_DIR/app" && bash build.sh) && is_current_apk "$CURRENT_APK"; then
        APK_SRC="$CURRENT_APK"
    else
        warn "No hay una APK v1.3 con la firma original. No se abrirá la APK debug como actualización."
    fi
else
    warn "No hay compilación local ni SDK suficiente para generar una APK firmada con la clave original."
fi

if [[ -z "$APK_SRC" ]]; then
    warn "No se encontró una APK nueva cuya firma coincida con la APK heredada del repositorio."
    warn "No desinstales CostPhone ni instales una APK debug: necesitamos la clave privada original."
    if [[ -n "$LEGACY_APK" && -s "$LEGACY_APK" && "${COSTPHONE_ALLOW_LEGACY_APK:-0}" == "1" ]]; then
        APK_SRC="$LEGACY_APK"
        warn "COSTPHONE_ALLOW_LEGACY_APK=1: se abrirá la APK heredada v1.2, no la versión nueva."
    else
        warn "Configura la clave original antes de instalar una versión actualizable. Véase docs/firma-original.md."
    fi
fi

if [[ -n "$APK_SRC" && -s "$APK_SRC" ]]; then
    APK_DEST="/sdcard/Download/$(basename "$APK_SRC")"
    cp "$APK_SRC" "$APK_DEST"
    echo "     APK seleccionada: $APK_SRC"
    termux-open "$APK_DEST" 2>/dev/null || warn "Abre manualmente: $APK_DEST"
    echo
    echo "  ┌─────────────────────────────────────────────────┐"
    echo "  │  EN EL TELÉFONO:                                │"
    echo "  │   1. Toca 'Instalar'                            │"
    echo "  │   2. Si Android lo pide, reactiva Accesibilidad │"
    echo "  │   3. En Termux ejecuta: amctl doctor             │"
    echo "  └─────────────────────────────────────────────────┘"
fi

echo
echo "══════════════════════════════════════════════════"
echo "  Diagnóstico: amctl doctor"
echo "  Snapshot: amctl snap"
echo "  La APK no se abre como actualización sin validar la firma original."
echo "══════════════════════════════════════════════════"
