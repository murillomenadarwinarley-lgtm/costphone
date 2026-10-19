#!/data/data/com.termux/files/usr/bin/bash
# check_resources.sh — valida recursos sin SDK
set -u
cd "$(dirname "$0")/.."
FAIL=0

echo "=== 1. XML bien formados (Python ET) ==="
while IFS= read -r f; do
  if ! python3 -c "import xml.etree.ElementTree as ET; ET.parse('$f')" 2>&1; then
    echo "FAIL: XML mal formado: $f"; FAIL=1
  else
    echo "OK: $f"
  fi
done < <(find app/res -name '*.xml' -type f)

echo ""
echo "=== 2. Referencias @drawable/@color/@style/@dimen/@string en XML ==="
# Recursos definidos en XML
python3 - <<'PY'
import os, re, sys

res = 'app/res'
defined = {'drawable': set(), 'color': set(), 'style': set(),
           'dimen': set(), 'string': set(), 'attr': set(), 'mipmap': set(),
           'layout': set(), 'menu': set(), 'anim': set(), 'xml': set(),
           'integer': set(), 'bool': set(), 'array': set(), 'plurals': set()}

IMAGE_EXT = ('.png', '.jpg', '.webp', '.gif', '.9.png')

for root_dir, dirs, files in os.walk(res):
    for fn in files:
        path = os.path.join(root_dir, fn)
        # drawable/mipmap: nombre = nombre de archivo (sin extensión)
        rel = os.path.relpath(root_dir, res)
        top = rel.split(os.sep)[0]
        if top in ('drawable', 'drawable-v24') and (fn.endswith('.xml') or fn.lower().endswith(IMAGE_EXT)):
            name = fn
            for ext in ('.xml', '.png', '.jpg', '.webp', '.gif', '.9.png'):
                if name.lower().endswith(ext):
                    name = name[:-len(ext)]
                    break
            defined['drawable'].add(name)
        elif top.startswith('mipmap') and (fn.endswith('.xml') or fn.lower().endswith(IMAGE_EXT)):
            name = fn
            for ext in ('.xml', '.png', '.jpg', '.webp', '.gif'):
                if name.lower().endswith(ext):
                    name = name[:-len(ext)]
                    break
            defined['mipmap'].add(name)
        # XML con name= (color, string, style, dimen, etc.)
        if fn.endswith('.xml'):
            try:
                import xml.etree.ElementTree as ET
                tree = ET.parse(path)
                for child in tree.getroot():
                    tag = child.tag
                    if tag in defined and child.get('name'):
                        defined[tag].add(child.get('name'))
            except Exception as e:
                print(f"  PARSE FAIL {path}: {e}")

# Referencias @drawable/... @color/... etc en XML
refs = {'drawable': set(), 'color': set(), 'style': set(),
        'dimen': set(), 'string': set(), 'mipmap': set()}
for root_dir, dirs, files in os.walk(res):
    for fn in files:
        if not fn.endswith('.xml'):
            continue
        path = os.path.join(root_dir, fn)
        try:
            import xml.etree.ElementTree as ET
            tree = ET.parse(path)
            for el in tree.iter():
                for v in el.attrib.values():
                    m = re.match(r'@(\w+)/(\w+)', v)
                    if m and m.group(1) in refs:
                        refs[m.group(1)].add(m.group(2))
                if el.text:
                    for m in re.finditer(r'@(\w+)/(\w+)', el.text):
                        if m.group(1) in refs:
                            refs[m.group(1)].add(m.group(2))
        except:
            pass

fails = 0
for kind, names in sorted(refs.items()):
    for n in sorted(names):
        if n in defined[kind]:
            print(f"  OK  @{kind}/{n}")
        else:
            print(f"  FAIL @{kind}/{n} no definido")
            fails += 1

print(f"  XML refs: {'PASS' if fails==0 else f'{fails} FALLOS'}")
sys.exit(1 if fails else 0)
PY
[ $? -ne 0 ] && FAIL=1

echo ""
echo "=== 3. Referencias R.* en .java ==="
python3 - <<'PY'
import os, re, sys

res = 'app/res'
src = 'app/src'

# Recursos definidos
defined = {}
for kind in ('drawable', 'color', 'style', 'dimen', 'string', 'id', 'mipmap'):
    defined[kind] = set()
IMAGE_EXT = ('.png', '.jpg', '.webp', '.gif', '.9.png')
for root_dir, dirs, files in os.walk(res):
    for fn in files:
        path = os.path.join(root_dir, fn)
        rel = os.path.relpath(root_dir, res)
        top = rel.split(os.sep)[0]
        if top in ('drawable', 'drawable-v24') and (fn.endswith('.xml') or fn.lower().endswith(IMAGE_EXT)):
            name = fn
            for ext in ('.xml', '.png', '.jpg', '.webp', '.gif', '.9.png'):
                if name.lower().endswith(ext):
                    name = name[:-len(ext)]
                    break
            defined['drawable'].add(name)
        elif top.startswith('mipmap') and (fn.endswith('.xml') or fn.lower().endswith(IMAGE_EXT)):
            name = fn
            for ext in ('.xml', '.png', '.jpg', '.webp', '.gif'):
                if name.lower().endswith(ext):
                    name = name[:-len(ext)]
                    break
            defined['mipmap'].add(name)
        if fn.endswith('.xml'):
            try:
                import xml.etree.ElementTree as ET
                tree = ET.parse(path)
                for child in tree.getroot():
                    if child.tag in defined and child.get('name'):
                        defined[child.tag].add(child.get('name'))
            except:
                pass

# IDs generados en Java (R.id.xxx)
java_ids = set()
for root_dir, dirs, files in os.walk(src):
    for fn in files:
        if not fn.endswith('.java'):
            continue
        path = os.path.join(root_dir, fn)
        content = open(path, encoding='utf-8', errors='ignore').read()
        # findViewById(R.id.xxx) y setId(R.id.xxx)
        for m in re.finditer(r'(?:findViewById|setId|\.setId)\s*\(\s*R\.id\.(\w+)', content):
            java_ids.add(m.group(1))
        # R.id.xxx en cualquier contexto
        for m in re.finditer(r'R\.id\.(\w+)', content):
            java_ids.add(m.group(1))

defined['id'] |= java_ids

# Referencias R.xxx.yyy en Java
fails = 0
refs_found = set()
for root_dir, dirs, files in os.walk(src):
    for fn in files:
        if not fn.endswith('.java'):
            continue
        path = os.path.join(root_dir, fn)
        content = open(path, encoding='utf-8', errors='ignore').read()
        for m in re.finditer(r'R\.(drawable|color|style|dimen|string|id|mipmap)\.(\w+)', content):
            kind, name = m.group(1), m.group(2)
            refs_found.add((kind, name))
            if name in defined.get(kind, set()):
                pass  # OK
            else:
                print(f"  FAIL R.{kind}.{name} no definido")
                fails += 1

print(f"  Java refs: {'PASS' if fails==0 else f'{fails} FALLOS'}")

# Recursos creados pero sin usar
unused = []
for kind in ('drawable', 'color', 'style', 'dimen'):
    for name in sorted(defined[kind]):
        if name.startswith('ic_launcher'):
            continue
        if (kind, name) not in refs_found:
            # también buscar en XML
            used_in_xml = False
            for root_dir, dirs, files in os.walk(res):
                for fn in files:
                    if not fn.endswith('.xml'):
                        continue
                    p = os.path.join(root_dir, fn)
                    txt = open(p, encoding='utf-8', errors='ignore').read()
                    if f'@{kind}/{name}' in txt:
                        used_in_xml = True
                        break
                if used_in_xml:
                    break
            if not used_in_xml:
                unused.append(f"  UNUSED @{kind}/{name}")

if unused:
    print("  Recursos sin usar:")
    for u in unused:
        print(u)
else:
    print("  Sin recursos sin usar")

sys.exit(1 if fails else 0)
PY
[ $? -ne 0 ] && FAIL=1

echo ""
echo "=== 4. test_find_id.sh ==="
bash app/test_find_id.sh
[ $? -ne 0 ] && FAIL=1

echo ""
echo "=== RESULTADO: $([ $FAIL -eq 0 ] && echo 'PASS' || echo 'FAIL') ==="
exit $FAIL
