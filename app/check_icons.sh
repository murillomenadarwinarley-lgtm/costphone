#!/data/data/com.termux/files/usr/bin/bash
# check_icons.sh — falla si dos iconos tienen el mismo pathData
set -u
cd "$(dirname "$0")/.."
FAIL=0
declare -A seen

for f in app/res/drawable/ic_*.xml; do
  name=$(basename "$f" .xml)
  # extraer todos los pathData concatenados por icono
  paths=$(grep -oE 'android:pathData="[^"]*"' "$f" | sed 's/android:pathData="//;s/"$//' | tr '\n' '|')
  if [ -z "$paths" ]; then
    echo "FAIL: $name sin pathData"; FAIL=1; continue
  fi
  if [ -n "${seen[$paths]:-}" ]; then
    echo "FAIL: pathData duplicado entre $name y ${seen[$paths]}"
    FAIL=1
  else
    seen[$paths]="$name"
    echo "OK: $name"
  fi
done

echo ""
echo "=== RESULTADO: $([ $FAIL -eq 0 ] && echo 'PASS' || echo 'FAIL') ==="
exit $FAIL
