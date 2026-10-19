#!/usr/bin/env bash
# check_contrast.sh — verifica contraste WCAG >= 4.5:1 de los pares de
# colores usados en la app (tokens de colors.xml). Fase 2c, Etapa 6.
set -u
cd "$(dirname "$0")/.."

python3 - <<'EOF'
import re, sys, xml.etree.ElementTree as ET

tree = ET.parse('app/res/values/colors.xml')
colors = {}
for c in tree.getroot().findall('color'):
    colors[c.get('name')] = c.text.strip()

def hex2rgb(h):
    h = h.lstrip('#')
    if len(h) == 8:  # AARRGGBB
        h = h[2:]
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

def lum(h):
    def ch(v):
        v /= 255.0
        return v/12.92 if v <= 0.03928 else ((v+0.055)/1.055) ** 2.4
    r, g, b = hex2rgb(h)
    return 0.2126*ch(r) + 0.7152*ch(g) + 0.0722*ch(b)

def ratio(fg, bg):
    l1, l2 = lum(fg), lum(bg)
    if l1 < l2: l1, l2 = l2, l1
    return (l1 + 0.05) / (l2 + 0.05)

def tok(name):
    if name not in colors:
        print('  FALTA token: ' + name)
        sys.exit(1)
    return colors[name]

# (foreground, background, descripcion, minimo)
PAIRS = [
    ('text',       'bg',        'texto principal sobre fondo',        4.5),
    ('text',       'surface',   'texto principal sobre tarjeta',      4.5),
    ('text',       'surface2',  'texto principal sobre surface2',     4.5),
    ('text2',      'bg',        'texto secundario sobre fondo',       4.5),
    ('text2',      'surface',   'texto secundario sobre tarjeta',     4.5),
    ('text2',      'surface2',  'texto secundario sobre surface2',    4.5),
    ('text3',      'surface',   'texto terciario sobre tarjeta',      4.5),
    ('text3',      'surface2',  'texto terciario sobre surface2',     4.5),
    ('text3',      'bg',        'texto terciario sobre fondo',        4.5),
    ('accent',     'bg',        'acentos sobre fondo',                4.5),
    ('accent',     'surface',   'acentos sobre tarjeta',              4.5),
    ('accent',     'surface2',  'acentos sobre surface2',             4.5),
    ('on_accent',  'accent',    'texto del botón primario',           4.5),
    ('accent2_text','surface',  'violeta de texto sobre tarjeta',     4.5),
    ('accent2_text','bg',       'violeta de texto sobre fondo',       4.5),
    ('info',       'surface',   'info sobre tarjeta',                 4.5),
    ('info',       'bg',        'info sobre fondo',                   4.5),
    ('aviso',      'surface',   'aviso sobre tarjeta',                4.5),
    ('aviso',      'bg',        'aviso sobre fondo',                  4.5),
    ('error',      'surface',   'error sobre tarjeta',                4.5),
    ('error',      'bg',        'error sobre fondo',                  4.5),
    ('ok',         'surface',   'ok sobre tarjeta',                   4.5),
    ('ok',         'bg',        'ok sobre fondo',                     4.5),
    ('text2',      'surface2',  'placeholder sobre input',            4.5),
    # legacy aliases usadas en XML/estilos
    ('text_muted', 'bg_card',   'legacy text_muted sobre bg_card',    4.5),
    ('text_dim',   'bg_card',   'legacy text_dim sobre bg_card',      4.5),
    ('text_main',  'bg_main',   'legacy text_main sobre bg_main',     4.5),
    ('text_log',   'log_bg',    'legacy text_log sobre log_bg',       4.5),
    ('badge_text', 'badge_bg',  'badge sobre badge_bg',               4.5),
]

fails = 0
print('=== Contraste WCAG (min 4.5:1) ===')
for fg, bg, desc, minimum in PAIRS:
    r = ratio(tok(fg), tok(bg))
    ok = r >= minimum
    if not ok:
        fails += 1
    mark = 'OK  ' if ok else 'FAIL'
    print('  %s  %-42s %-14s on %-10s %5.2f:1' % (mark, desc, fg, bg, r))

print()
# píldora de pestaña activa: accent al 18% sobre surface (bg_tab_active)
base = hex2rgb(tok('surface'))
acc = hex2rgb(tok('accent'))
blend = '#%02X%02X%02X' % tuple(int(base[i]*0.82 + acc[i]*0.18) for i in range(3))
r = ratio(tok('accent'), blend)
ok = r >= 4.5
if not ok:
    fails += 1
print('  %s  %-42s %-14s on %-10s %5.2f:1' % (
    'OK  ' if ok else 'FAIL', 'etiqueta activa sobre píldora translúcida',
    'accent', blend, r))

print()
if fails:
    print('=== RESULTADO: FAIL (%d pares < 4.5:1) ===' % fails)
    sys.exit(1)
print('=== RESULTADO: PASS ===')
EOF
