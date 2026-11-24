#!/usr/bin/env python3
"""Macht aus den Ansichten des Design-Canvas (.dc.html) die Seiten, die der Launcher anzeigt.

Aufruf:  python3 tools/canvas2ui.py <Ordner mit .dc.html und canvas.json>
Ergebnis in app/src/main/assets/ui/:  s/<Nummer>-<oben|unten>.html, canvas.css, screens.js
"""
import json
import os
import re
import sys

src = sys.argv[1]
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'assets', 'ui')
os.makedirs(os.path.join(out, 's'), exist_ok=True)
boards = json.load(open(os.path.join(src, 'canvas.json'), encoding='utf-8'))['boards']

# In der Zeichnung laufen Dialog, Hinweis, Menü und Boot in Schleife. Am Gerät spielen sie einmal.
ONCE = [
    (re.compile(r'animation:pl-pop [^;"]*'), 'animation:pl-pop .3s cubic-bezier(.2,.8,.2,1) both'),
    (re.compile(r'animation:pl-toast [^;"]*'), 'animation:pl-toast .3s cubic-bezier(.2,.8,.2,1) both'),
    (re.compile(r'animation:pl-side [^;"]*'), 'animation:pl-side .35s cubic-bezier(.2,.8,.2,1) both'),
    (re.compile(r'(animation:pl-bf [^;"]*?)infinite'), r'\1 1 both'),
    (re.compile(r'(animation:pl-word [^;"]*?)infinite'), r'\1 1 both'),
]

# Die vier wählbaren Hintergründe stehen auf Blatt 0.7. In den Ansichten ist "Polarlicht" fest eingezeichnet;
# dort kommt ein Platz hin, den der Launcher mit dem gewählten Hintergrund füllt.
walls = []
wall_file = os.path.join(src, 'v0-7-hintergruende.dc.html')
if os.path.exists(wall_file):
    sheet = open(wall_file, encoding='utf-8').read()
    start = '<div style="width:960px;height:540px;transform:scale(.6042);transform-origin:0 0;position:relative;background:#0A0D14;overflow:hidden">'
    for part in sheet.split(start)[1:5]:
        depth, i = 1, 0
        for m in re.finditer(r'<(/?)div\b', part):
            depth += -1 if m.group(1) else 1
            if depth == 0:
                i = m.start()
                break
        walls.append(part[:i])
    for name, html_ in zip(['polarlicht', 'sterne', 'wellen', 'streifen'], walls):
        open(os.path.join(out, 's', f'bg-{name}.html'), 'w', encoding='utf-8').write(html_)
PLACE = '<div id="pl-wall" style="position:absolute;inset:0;overflow:hidden"></div>'
placed = 0

screens, css, count = {}, '', 0
for name in sorted(os.listdir(src)):
    if not name.endswith('.dc.html') or name not in boards:
        continue
    m = re.match(r'^(\d+\.\d+[a-z]?)\s+(.*?)\s+·\s+(oben|unten)$', boards[name].get('title', ''))
    if not m:
        continue  # Grundlagen und die PC-Seite sind keine Geräte-Ansichten
    key, label, side = m.groups()
    text = open(os.path.join(src, name), encoding='utf-8').read()
    inner = text[text.index('<x-dc>') + 6:text.index('</x-dc>')]
    helmet = re.search(r'<helmet>([\s\S]*?)</helmet>', inner)
    if helmet and not css:
        css = ''.join(re.findall(r'<style>([\s\S]*?)</style>', helmet.group(1)))
    body = re.sub(r'<helmet>[\s\S]*?</helmet>', '', inner).strip()
    if walls and walls[0] in body:
        body = body.replace(walls[0], PLACE)
        placed += 1
    for pattern, replacement in ONCE:
        body = pattern.sub(replacement, body)
    open(os.path.join(out, 's', f'{key}-{side}.html'), 'w', encoding='utf-8').write(body)
    screens.setdefault(key, {'t': label})[side[0]] = 1
    count += 1


def order(k):
    a, b = k.split('.')
    return int(a), int(re.match(r'\d+', b).group(0)), b


ordered = {k: screens[k] for k in sorted(screens, key=order)}
missing = [k for k, v in ordered.items() if 'o' not in v or 'u' not in v]
open(os.path.join(out, 'canvas.css'), 'w', encoding='utf-8').write(css)
open(os.path.join(out, 'screens.js'), 'w', encoding='utf-8').write(
    'window.PL_SCREENS=' + json.dumps({k: v['t'] for k, v in ordered.items()}, ensure_ascii=False) + ';\n')
print(f'{count} Seiten, {len(ordered)} Ansichten, unvollständig: {missing}, Hintergründe: {len(walls)}, eingesetzt in {placed}')
