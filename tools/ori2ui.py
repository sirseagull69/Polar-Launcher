#!/usr/bin/env python3
"""Übernimmt die Ansichten aus Nils' Design-Canvas "Ori OS" in den Launcher.

Aufruf: tools/ori2ui.py <Ordner mit den .dc.html-Dateien>
Schreibt je Ansicht app/src/main/assets/ui/s/dc-<name>.html: Vorlage, Logik und Vorgaben, getrennt durch Kommentarzeilen.
Die Vorlagen bleiben, wie sie im Canvas stehen. Angepasst wird nur, was am Gerät anders sein soll:
eckige Wellenflächen raus (der Launcher legt runde, animierte Wellen darunter) und helle statt dunkler Kopfzeilen.
"""
import glob, json, os, re, sys

def rund(m):
    """Macht aus einem eckigen Wellenband einen weichen Bogen. Gefaltete Motive (Berge, Logo, Ori) bleiben eckig."""
    pts = [tuple(float(v) for v in q.split(',')) for q in m.group(1).split()]
    xs = [q[0] for q in pts]; ys = [q[1] for q in pts]
    if len(pts) < 6 or (max(xs) - min(xs)) < 3 * (max(ys) - min(ys)):
        return m.group(0)
    hart = [q[0] in (min(xs), max(xs)) for q in pts]
    if all(hart) or not any(hart):
        return m.group(0)
    n = len(pts); i0 = next(i for i in range(n) if hart[i] and hart[i - 1])
    f = lambda q: ('%g,%g' % q)
    d = 'M' + f(pts[i0]); i = 1
    while i <= n:
        q = pts[(i0 + i) % n]
        if hart[(i0 + i) % n]:
            d += 'L' + f(q); i += 1; continue
        lauf = []
        while not hart[(i0 + i) % n]:
            lauf.append(pts[(i0 + i) % n]); i += 1
        ende = pts[(i0 + i) % n]
        for k, c in enumerate(lauf):
            e = ende if k == len(lauf) - 1 else ((c[0] + lauf[k + 1][0]) / 2, (c[1] + lauf[k + 1][1]) / 2)
            d += 'Q' + f(c) + ' ' + f(e)
        i += 1
    return '<path d="' + d + 'Z" fill="' + m.group(2) + '"/>'


src = sys.argv[1]
out = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'ui', 's')
# Ausschalten ist von Hand als großes Fenster gebaut (s/dc-ausschalten.html), nicht mehr aus dem Canvas erzeugt
skip = {'Ausschalten', 'UntenLeistung', 'Main', 'Bibliothek', 'Spiel', 'Apps', 'Leiste', 'Dock', 'Einst', 'EinstNetzwerk', 'EinstController', 'EinstRgb', 'EinstTon', 'EinstLeistung', 'EinstSpeicher', 'EinstEmulatoren', 'EinstSystem'}
names = []
for f in sorted(glob.glob(os.path.join(src, '*.dc.html'))):
    name = os.path.basename(f)[:-8]
    if name in skip:
        continue
    s = open(f, encoding='utf-8').read()
    body = s[s.index('<x-dc>') + 6:s.index('</x-dc>')]
    body = re.sub(r'<helmet>.*?</helmet>', '', body, flags=re.S)
    body = re.sub(r'<svg class="welle2?"[^>]*>\s*<polygon[^>]*fill="#(?:fdd3b8|fbc19d)"[^>]*>(?:</polygon>)?\s*</svg>', '', body, flags=re.S)
    body = re.sub(r'<polygon points="([^"]+)" fill="([^"]+)"\s*/?>(?:</polygon>)?', rund, body)
    # Platzhalter-Logo (gefaltetes Quadrat) wird die echte Ori; auf dunklem Grund die helle Variante
    dunkel = bool(re.search(r'^\s*<[a-z]+ style="[^"]*background: #1[0-9a-f]{5}', body.strip()))
    def logo(m):
        g = int(m.group(1)); n = max(44, int(g * 1.7))
        return '<img alt="" aria-hidden="true" class="o-ori' + (' wippen' if 'wippen' in m.group(0) else '') + '" width="%d" height="%d" style="margin:%dpx 2px %dpx -6px" src="ori/%s-%s.svg">' % (n, n, -(n - g) // 2, -(n - g) // 2, 'aufwachen' if name == 'Boot' else 'ruhezustand', 'hell' if dunkel else 'abendrot')
    body = re.sub(r'<svg[^>]*width="(\d+)"[^>]*viewBox="0 0 40 40"[^>]*>.*?</svg>', logo, body, flags=re.S)
    farbe = re.search(r'<style>.*?\ba\{color:(#[0-9a-fA-F]{3,8})', s, flags=re.S)
    if farbe and farbe.group(1).lower() != '#1b2340':
        body = re.sub(r'(<[a-z]+ style=")', r'\1--dca: ' + farbe.group(1) + '; ', body.strip(), count=1)
    def head(m):
        h = m.group(0).replace('background: #141b31', 'background: transparent').replace('color: #ffffff', 'color: #1b2340').replace('color: #e6e9f5', 'color: #4d556e').replace('color: #ff9a62', 'color: #9a3a0c').replace('color: #c9d0e6', 'color: #4d556e')
        return h
    body = re.sub(r'<header\b.*?</header>', head, body, flags=re.S)
    m = re.search(r"<script type=\"text/x-dc\" data-dc-script data-props='(.*?)'>(.*?)</script>", s, flags=re.S)
    props = {k: v.get('default') for k, v in json.loads(m.group(1)).items() if isinstance(v, dict) and 'default' in v}
    w = re.search(r'^\s*<[a-z]+ style="width: (\d+)px', body.strip())
    open(os.path.join(out, 'dc-' + name.lower() + '.html'), 'w', encoding='utf-8').write(
        body.strip() + '\n<!--dc-script-->\n' + m.group(2).strip() + '\n<!--dc-props-->\n' + json.dumps(props, ensure_ascii=False))
    names.append((name, int(w.group(1)) if w else 0))
print(len(names), 'Ansichten:', ', '.join(n + ('' if w == 960 else ' (unten)') for n, w in names))
