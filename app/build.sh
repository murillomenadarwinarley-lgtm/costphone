#!/data/data/com.termux/files/usr/bin/bash
# Build CostPhone. Only a release signed with the original keystore is updateable.
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$APP_DIR/.." && pwd)"

if ! command -v gradle >/dev/null 2>&1; then
    echo "✗ Gradle no está instalado."
    echo "  En Termux puedes instalarlo con: pkg install gradle"
    exit 1
fi

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

if [[ ! -f "$ANDROID_HOME/platforms/android-36/android.jar" ]]; then
    echo "✗ No encuentro platforms/android-36/android.jar en:"
    echo "  $ANDROID_HOME"
    echo "  Instala Android SDK Platform 36 y Build Tools 36.0.0."
    exit 1
fi

SDK_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
APKSIGNER="$SDK_TOOLS/apksigner"
LEGACY_APK="$(find "$ROOT_DIR/apk" -maxdepth 1 -type f -name 'CostPhone-v*.apk' -print 2>/dev/null | sort -V | tail -n 1)"
OUTPUT_APK="$APP_DIR/build/CostPhone.apk"

mkdir -p "$APP_DIR/build"
# Avoid leaving an old APK at the advertised path after an unsuccessful build.
rm -f "$OUTPUT_APK"

has_signing_key=0
if [[ -n "${COSTPHONE_KEYSTORE_PATH:-}" && -f "${COSTPHONE_KEYSTORE_PATH}" &&
      -n "${COSTPHONE_KEYSTORE_PASSWORD:-}" && -n "${COSTPHONE_KEY_ALIAS:-}" &&
      -n "${COSTPHONE_KEY_PASSWORD:-}" ]]; then
    has_signing_key=1
fi

echo "────────────────────────────────────────────"
echo " CostPhone APK builder"
echo "────────────────────────────────────────────"

if [[ "$has_signing_key" != "1" ]]; then
    gradle --no-daemon -p "$ROOT_DIR" :app:assembleDebug
    DEBUG_APK="$APP_DIR/build/outputs/apk/debug/app-debug.apk"
    [[ -s "$DEBUG_APK" ]] || { echo "✗ Gradle no produjo el APK debug esperado."; exit 1; }
    cp "$DEBUG_APK" "$APP_DIR/build/CostPhone-debug.apk"
    echo
    echo "✓ APK de prueba: $APP_DIR/build/CostPhone-debug.apk"
    echo "⚠ No se creó CostPhone.apk para actualizar la app instalada."
    echo "  Configura el keystore ORIGINAL y sus contraseñas/alias."
    echo "  La clave privada no se puede recuperar de una APK firmada."
    exit 0
fi

if [[ ! -x "$APKSIGNER" ]]; then
    echo "✗ No encuentro apksigner en $SDK_TOOLS"
    exit 1
fi
if [[ -z "$LEGACY_APK" || ! -s "$LEGACY_APK" ]]; then
    echo "✗ Falta la APK heredada que sirve para comprobar el certificado original."
    exit 1
fi

gradle --no-daemon -p "$ROOT_DIR" :app:assembleRelease
RELEASE_APK="$APP_DIR/build/outputs/apk/release/app-release.apk"
[[ -s "$RELEASE_APK" ]] || { echo "✗ Gradle no produjo el APK release esperado."; exit 1; }
"$APKSIGNER" verify "$RELEASE_APK"

legacy_cert="$("$APKSIGNER" verify --print-certs "$LEGACY_APK" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/ {print $2; exit}' | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
release_cert="$("$APKSIGNER" verify --print-certs "$RELEASE_APK" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/ {print $2; exit}' | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
if [[ -z "$legacy_cert" || -z "$release_cert" || "$legacy_cert" != "$release_cert" ]]; then
    echo "✗ La firma NO coincide con la APK CostPhone heredada del repositorio."
    echo "  No se publicará ni copiará como CostPhone.apk."
    exit 1
fi

cp "$RELEASE_APK" "$OUTPUT_APK"
echo
echo "✓ APK de actualización: $OUTPUT_APK"
echo "  El certificado SHA-256 coincide con la APK heredada."
