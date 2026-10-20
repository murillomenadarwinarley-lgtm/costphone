#!/data/data/com.termux/files/usr/bin/bash
# Build CostPhone with the Android Gradle Plugin and the official Shizuku API.
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

echo "────────────────────────────────────────────"
echo " CostPhone APK builder (Gradle + Shizuku)"
echo "────────────────────────────────────────────"
gradle --no-daemon -p "$ROOT_DIR" :app:assembleDebug

APK="$APP_DIR/build/outputs/apk/debug/app-debug.apk"
[[ -s "$APK" ]] || { echo "✗ Gradle terminó sin producir el APK esperado."; exit 1; }

# Keep the historic output path so scripts that expect build/CostPhone.apk continue working.
cp "$APK" "$APP_DIR/build/CostPhone.apk"
echo ""
echo "✅ APK: $APP_DIR/build/CostPhone.apk"
echo "   APK Gradle: $APK"
echo "   Puedes abrirlo desde Termux con: termux-open $APP_DIR/build/CostPhone.apk"
