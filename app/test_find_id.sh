#!/usr/bin/env bash
# Verificación estática de regresión para ACTION_FIND_ID
# Comprueba que el case existe y tiene la estructura correcta
set -euo pipefail
cd "$(dirname "$0")"
SRC="AccessibilityServiceImpl.java"
PASS=0
FAIL=0

check() {
    if eval "$2"; then
        echo "PASS: $1"
        PASS=$((PASS+1))
    else
        echo "FAIL: $1"
        FAIL=$((FAIL+1))
    fi
}

check "ACTION_FIND_ID declarado" \
    "grep -q 'ACTION_FIND_ID.*=.*\"com.accessdroid.termux.ACTION_FIND_ID\"' $SRC"

check "ACTION_FIND_ID en IntentFilter" \
    "grep -q 'filter.addAction(ACTION_FIND_ID)' $SRC"

check "case ACTION_FIND_ID existe en handleCommand" \
    "grep -A2 'case ACTION_CLICK_ID' $SRC | grep -q 'case ACTION_FIND_ID'"

check "ACTION_FIND_ID usa findAccessibilityNodeInfosByViewId" \
    "grep -A10 'case ACTION_FIND_ID' $SRC | grep -q 'findAccessibilityNodeInfosByViewId'"

check "ACTION_FIND_ID valida parámetro id" \
    "grep -A10 'case ACTION_FIND_ID' $SRC | grep -q 'getStringExtra.*id'"

check "ACTION_FIND_ID llama a ACTION_CLICK" \
    "grep -A15 'case ACTION_FIND_ID' $SRC | grep -q 'ACTION_CLICK'"

check "secret gate presente" \
    "grep -q 'SECRET' $SRC && grep -q 'rejected command' $SRC"

echo ""
echo "Results: $PASS passed, $FAIL failed"
exit $FAIL
