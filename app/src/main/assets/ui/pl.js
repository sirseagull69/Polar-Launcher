/* Polarlicht: zeigt die Ansichten aus dem Canvas und macht sie bedienbar.
   Läuft zweimal: oben (Controller) und unten (Touch). Die Brücke zu Android heißt PL. */
(function () {
  'use strict';
  var TOP = !/unten/.test(location.search);
  var side = TOP ? 'oben' : 'unten';
  var W = TOP ? 960 : 620, H = 540;
  var stage = document.getElementById('stage');
  var J = window.PL || stub();
  var cur = '', root = null, foc = null, items = [], st = {}, apps = [], sel = null;
  var hidx = 0, timers = [], live = [], query = '', seen = {}, idx = null;

  /* ---------- Hilfen ---------- */
  function sty(el) { return (el.getAttribute && el.getAttribute('style')) || ''; }
  function qa(s, el) { return Array.prototype.slice.call((el || stage).querySelectorAll(s)); }
  function later(ms, fn) { timers.push(setTimeout(fn, ms)); }
  function pref(k, d) { var v = J.pref(k); return v === null || v === undefined || v === '' ? d : v; }
  function fit() {
    var s = Math.min(window.innerWidth / W, window.innerHeight / H);
    stage.style.width = W + 'px'; stage.style.height = H + 'px';
    stage.style.transform = 'translate(' + (window.innerWidth - W * s) / 2 + 'px,' + (window.innerHeight - H * s) / 2 + 'px) scale(' + s + ')';
  }
  window.addEventListener('resize', fit);

  /* ---------- Klang (Canvas 0.6) ---------- */
  var S = {"fokus": [[880, 0, 0.04, "sine", 0.08]], "ok": [[440, 0, 0.07, "triangle", 0.18], [587.33, 0.06, 0.11, "triangle", 0.18]], "back": [[587.33, 0, 0.07, "triangle", 0.18], [440, 0.06, 0.11, "triangle", 0.18]], "an": [[739.99, 0, 0.09, "sine", 0.16]], "aus": [[369.99, 0, 0.09, "sine", 0.16]], "taste": [[659.25, 0, 0.025, "triangle", 0.07]], "dialog": [[293.66, 0, 0.28, "sine", 0.12], [440, 0, 0.28, "sine", 0.1]], "menu": [[440, 0, 0.14, "sine", 0.12, 293.66]], "hinweis": [[739.99, 0, 0.45, "sine", 0.16]], "warn": [[293.66, 0, 0.14, "triangle", 0.2], [293.66, 0.2, 0.2, "triangle", 0.2]], "fehler": [[329.63, 0, 0.14, "sawtooth", 0.09], [277.18, 0.14, 0.26, "sawtooth", 0.09]], "erfolg": [[293.66, 0, 0.1, "triangle", 0.18], [369.99, 0.1, 0.1, "triangle", 0.18], [440, 0.2, 0.1, "triangle", 0.18], [587.33, 0.3, 0.32, "triangle", 0.18]], "start": [[293.66, 0, 0.35, "triangle", 0.16, 587.33], [880, 0.3, 0.25, "sine", 0.1]], "foto": [[1800, 0, 0.03, "square", 0.05], [1200, 0.05, 0.04, "square", 0.05]], "ori": [[1174.66, 0, 0.07, "sine", 0.14, 1567.98], [1318.51, 0.11, 0.09, "sine", 0.14, 1760]], "boot": [[293.66, 0.0, 0.3, "sine", 0.14], [329.63, 0.35, 0.3, "sine", 0.14], [369.99, 0.7, 0.3, "sine", 0.14], [440, 1.05, 0.3, "sine", 0.14], [493.88, 1.4, 0.3, "sine", 0.14], [293.66, 1.9, 0.9, "sine", 0.1], [440, 1.9, 0.9, "sine", 0.1], [587.33, 1.9, 0.9, "sine", 0.1]]};
  var QUIET = { fokus: 1, ok: 1, back: 1, an: 1, aus: 1, taste: 1 };
  var ac = null;
  function snd(name) {
    try {
      if (QUIET[name] && pref('sw:Tastentöne', '1') === '0') return;
      var AC = window.AudioContext || window.webkitAudioContext; if (!AC || !S[name]) return;
      if (!ac) ac = new AC(); if (ac.state === 'suspended') ac.resume();
      var t0 = ac.currentTime + 0.02;
      S[name].forEach(function (n) {
        var o = ac.createOscillator(), g = ac.createGain(), s = t0 + n[1];
        o.type = n[3] || 'sine'; o.frequency.setValueAtTime(n[0], s);
        if (n[5]) o.frequency.exponentialRampToValueAtTime(n[5], s + n[2]);
        g.gain.setValueAtTime(0.0001, s); g.gain.exponentialRampToValueAtTime(n[4] || 0.15, s + 0.012);
        g.gain.exponentialRampToValueAtTime(0.0001, s + n[2]);
        o.connect(g); g.connect(ac.destination); o.start(s); o.stop(s + n[2] + 0.05);
      });
    } catch (e) { /* kein Ton ist kein Fehler */ }
  }

  /* ---------- Hinweis-Zeile ---------- */
  function note(text) {
    var n = document.getElementById('pl-note');
    if (!n) { n = document.createElement('div'); n.id = 'pl-note'; stage.parentNode.appendChild(n); }
    n.textContent = text; n.style.display = 'block';
    clearTimeout(note.t); note.t = setTimeout(function () { n.style.display = 'none'; }, 2600);
  }
  var OS = '!Kommt mit dem eigenen Android';

  /* ---------- Bausteine erkennen ---------- */
  /* Einmal direkt nach dem Laden markieren: sobald ein Stil geändert wird, schreibt der Browser ihn um. */
  function tag() {
    qa('div,button', root).forEach(function (el) {
      var s = sty(el), c = el.firstElementChild;
      if (el.tagName === 'BUTTON' || (/border-radius:14px/.test(s) && /min-height:\d+px;padding:0 1\dpx/.test(s))) el.dataset.pl = 'row';
      else if (/^border-radius:14px;/.test(s) && c && /height:92px/.test(sty(c))) el.dataset.pl = 'tile';
      else if (/width:46px;height:26px/.test(s)) { el.dataset.pl = 'sw'; el.dataset.on = /5FD38D/i.test(s) ? '1' : '0'; }
      else if (/position:relative;height:(16|28)px/.test(s) && el.children.length === 3) el.dataset.pl = 'sl';
      if (/outline:2px solid/.test(s)) el.dataset.had = '1';
      if (el.dataset.pl === 'row' && el.tagName !== 'BUTTON' && /background:#1(A2132|21724)/i.test(s)) el.dataset.base = '#121724';
    });
  }
  function isRow(el) { return el.dataset && el.dataset.pl === 'row'; }
  function isTile(el) { return el.dataset && el.dataset.pl === 'tile'; }
  function owner(el) { return isTile(el) ? el.parentElement : el; }
  function labelOf(el) {
    var w = document.createTreeWalker(owner(el), NodeFilter.SHOW_TEXT, null), n;
    while ((n = w.nextNode())) { var t = n.nodeValue.replace(/\s+/g, ' ').trim(); if (t) return t; }
    return '';
  }
  function findSwitch(el) { return el.querySelector('[data-pl="sw"]'); }
  function findSlider(el) { return el.querySelector('[data-pl="sl"]'); }
  function setSwitch(sw, on) {
    sw.style.background = on ? '#5FD38D' : '#3A4358';
    sw.firstElementChild.style.left = on ? '23px' : '3px';
    sw.firstElementChild.style.background = on ? '#0A0D14' : '#A3ABBA';
    sw.dataset.on = on ? '1' : '0';
  }
  function setSlider(sl, pct) {
    pct = Math.max(0, Math.min(100, Math.round(pct)));
    var knob = sl.children[2], half = parseFloat(knob.style.width) / 2;
    sl.children[1].style.width = pct + '%'; knob.style.left = 'calc(' + pct + '% - ' + half + 'px)';
    sl.dataset.v = pct;
  }
  function sliderKind(label) { return /Stick|Schrift/.test(cur === '5.5b' ? 'Stick' : label) ? '' : /Helligkeit/.test(label) ? 'b' : /Lautst/.test(label) ? 'v' : ''; }
  function sliderSet(row, sl, pct, fromUser) {
    setSlider(sl, pct);
    if (!fromUser) return;
    var L = labelOf(row), k = sliderKind(L);
    if (k === 'b') J.setBrightness(+sl.dataset.v); else if (k === 'v') J.setVolume(+sl.dataset.v); else J.setPref('sl:' + L, sl.dataset.v);
  }
  function applyStates() {
    qa('div,button', root).filter(isRow).forEach(function (row) {
      var L = labelOf(row), sw = findSwitch(row), sl = findSlider(row);
      if (sw) {
        var v = L === 'WLAN' ? (st.wifi ? '1' : '0') : L === 'Bluetooth' ? (st.bt ? '1' : '0') : pref('sw:' + L, null);
        if (v !== null) setSwitch(sw, v === '1');
      }
      if (sl) {
        var k = sliderKind(L), p = k === 'b' ? st.brightness : k === 'v' ? st.volume : pref('sl:' + L, null);
        if (p !== null && p !== undefined) setSlider(sl, +p);
      }
    });
    if (!TOP && cur[0] === '2') {
      qa('button', root).forEach(function (b) {
        var L = labelOf(b), on = L === 'WLAN' ? st.wifi : L === 'Bluetooth' ? st.bt : L === 'Flugmodus' ? st.airplane : null;
        if (on === null) return;
        b.style.background = on ? '#1A2132' : '#121724';
        var sub = qa('span span', b)[1]; if (sub) sub.textContent = on ? 'An' : 'Aus';
      });
    }
  }

  /* ---------- Platzhalter füllen ---------- */
  function values() {
    return {
      'uhrzeit': st.time, 'akku': st.battery + ' %', 'datum': st.date, 'wochentag': st.weekday, 'version': st.version,
      'zeitzone': st.tz, 'gerätename': st.model, 'region': st.region, 'belegt': st.used, 'gesamt': st.total, 'frei': st.free,
      'temperatur': st.temp, 'netzwerk': st.wifi ? 'An' : 'Aus', 'anzahl': String(apps.length), 'größe': '–',
      'app-name': sel ? sel.label : '–', 'spielzeit': '–', 'ip-adresse': '–', 'seriennummer': '–',
      'netzwerk 1': 'WLAN-Liste öffnen', 'antwort von ori · zwei bis drei kurze sätze': 'Ori ist noch nicht angeschlossen. Das kommt als Nächstes.', 'pfad': st.images || '–', 'fehlermeldung': (st.upd && st.upd.msg) || '–'
    };
  }
  function fillText(tpl, V, out) {
    return tpl.replace(/\[([^\]\[]{2,60})\]/g, function (m, name) {
      var key = name.toLowerCase(), v = name === 'SYSTEM 1' ? 'App' : V[key];
      if (v === undefined || v === null) { out.miss = true; return '\u0001'; }
      return name === name.toUpperCase() && /[A-ZÄÖÜ]/.test(name) ? String(v).toUpperCase() : String(v);
    });
  }
  function fill() {
    var V = values(), w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null), n, kill = [];
    live = [];
    var game = sel ? sel.label : 'Polarlicht';
    while ((n = w.nextNode())) {
      var t = n.nodeValue;
      if (/Platzhalter\]/.test(t)) { kill.push(n.parentNode); continue; }
      if (t.indexOf('Nebelwacht') >= 0 || t.indexOf('NEBELWACHT') >= 0) {
        t = t.replace(/Nebelwacht/g, game).replace(/NEBELWACHT/g, game.toUpperCase()); n.nodeValue = t;
      }
      if (t.indexOf('[') < 0) continue;
      var out = {}, r = fillText(t, V, out);
      if (/\[(Uhrzeit|UHRZEIT|Akku|Datum|DATUM)\]/.test(t)) live.push({ n: n, tpl: t });
      if (out.miss) {
        var row = n.parentNode; while (row && row !== root && !isRow(row)) row = row.parentNode;
        if (row && row !== root && r.replace(/\s/g, '') === '\u0001' && firstText(row) === n) { kill.push(row); continue; }
        r = r.replace(/\u0001/g, '–');
      }
      n.nodeValue = r;
    }
    kill.forEach(function (el) { if (el && el.parentNode) el.parentNode.removeChild(el); });
  }
  function firstText(el) {
    var w = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null), n;
    while ((n = w.nextNode())) if (n.nodeValue.trim()) return n;
    return null;
  }
  function tick() {
    if (!root) return;
    try { st = JSON.parse(J.state()); } catch (e) { return; }
    var V = values(); live.forEach(function (x) { if (x.n.parentNode) x.n.nodeValue = fillText(x.tpl, V, {}).replace(/\u0001/g, '–'); });
  }
  setInterval(tick, 20000);

  /* ---------- Hintergrund (Canvas 0.7, wählbar in 5.3a) ---------- */
  var WALLS = { 'Polarlicht': 'polarlicht', 'Sterne': 'sterne', 'Wellen': 'wellen', 'Streifen': 'streifen', 'Bild des gewählten Spiels': 'bild', 'Aus': 'aus' };
  function wall(name) {
    var el = document.getElementById('pl-wall'); if (!el) return;
    name = name || pref('bg', 'polarlicht');
    if (name === 'bild') { if (sel && sel.img) { el.innerHTML = '<img src="' + img(sel, 'image') + '" style="position:absolute;inset:0;width:100%;height:100%;object-fit:cover;opacity:.55">'; return; } name = 'polarlicht'; }
    if (el.dataset.w === name) return;
    el.dataset.w = name; el.innerHTML = name === 'aus' ? '' : J.html('bg-' + name);
  }
  /* Beispiel-Spielbild aus der Zeichnung raus, gewählten Hintergrund rein. */
  function swapArt() {
    var art = root.querySelector(':scope > svg'); if (!art) return;
    var scrim = art.nextElementSibling;
    if (scrim && /gradient/.test(sty(scrim))) scrim.style.display = 'none';
    var w = document.createElement('div'); w.id = 'pl-wall'; w.style.cssText = 'position:absolute;inset:0;overflow:hidden';
    root.replaceChild(w, art);
  }

  /* ---------- Fokus ---------- */
  function ring(el, on) {
    if (on) {
      el.style.outline = '2px solid #EEF1F6'; el.style.outlineOffset = '3px'; el.style.animation = 'pl-ring 1.6s ease-in-out infinite';
      if (el.dataset.base) el.style.background = '#1A2132';
    } else {
      el.style.outline = 'none'; el.style.animation = 'none';
      if (el.dataset.base) el.style.background = el.dataset.base;
    }
  }
  function setFocus(el, quiet) {
    if (!el || foc === el) return;
    if (foc) ring(foc, false);
    foc = el; ring(el, true);
    if (el.scrollIntoView && el.dataset.scroll) el.scrollIntoView({ block: 'nearest' });
    if (cur === '5.3a' && WALLS[labelOf(el)]) wall(WALLS[labelOf(el)]);
    if (!quiet) snd('fokus');
  }
  function collect() {
    var first = null;
    items = qa('div,button', root).filter(function (el) {
      var had = el.dataset.had === '1';
      if (!(isRow(el) || isTile(el) || had)) return false;
      if (had && !first) first = el;
      if (TOP) ring(el, false);
      return el.getBoundingClientRect().width > 0;
    });
    items = items.filter(function (el) { return !items.some(function (o) { return o !== el && el.contains(o); }); });
    foc = null;
    first = root.querySelector('[data-first]') || first;
    if (TOP) setFocus(first && items.indexOf(first) >= 0 ? first : items[0], true);
  }
  function move(dir) {
    if (!foc) { setFocus(items[0]); return; }
    var a = foc.getBoundingClientRect(), ax = a.left + a.width / 2, ay = a.top + a.height / 2, best = null, bs = 1e9;
    items.forEach(function (el) {
      if (el === foc) return;
      var b = el.getBoundingClientRect(), dx = b.left + b.width / 2 - ax, dy = b.top + b.height / 2 - ay, p, s;
      if (dir === 'left') { p = -dx; s = Math.abs(dy); } else if (dir === 'right') { p = dx; s = Math.abs(dy); }
      else if (dir === 'up') { p = -dy; s = Math.abs(dx); } else { p = dy; s = Math.abs(dx); }
      if (p < 4) return;
      var score = p + s * 2.5; if (score < bs) { bs = score; best = el; }
    });
    if (best) setFocus(best);
  }

  /* ---------- Apps ---------- */
  function appByKey(k) { for (var i = 0; i < apps.length; i++) if (apps[i].key === k) return apps[i]; return null; }
  function homeList() {
    var favs = apps.filter(function (a) { return a.fav; }), l = (favs.length ? favs : apps).slice();
    l.sort(function (a, b) { return b.last - a.last || a.label.localeCompare(b.label); });
    return l;
  }
  function select(a) { sel = a; if (a) J.setPref('sel', a.key); }
  function launch(a) { if (!a) return; snd('start'); select(a); setTimeout(function () { J.launch(a.key); }, 380); }
  function fav(a) { if (!a) return; J.fav(a.key); snd(a.fav ? 'aus' : 'an'); J.sync('reload'); }
  function img(a, kind) { return 'https://polarlicht.local/' + kind + '?k=' + encodeURIComponent(a.key); }

  /* Startbildschirm oben: echte Apps statt Beispielspiele */
  function bindHome() {
    var title = qa('div', root).filter(function (d) { return /font-size:88px/.test(sty(d)); })[0];
    if (!title) return;
    var list = homeList().slice(0, 5), label = title.previousElementSibling, meta = title.nextElementSibling;
    var strip = qa('[aria-label="Farbstreifen"]', root)[0];
    var thumbs = qa('div', root).filter(function (d) { return /height:62px/.test(sty(d)); });
    hidx = Math.max(0, Math.min(hidx, list.length - 1));
    function paint() {
      var a = list[hidx]; if (!a) return;
      select(a);
      title.textContent = a.label;
      title.style.fontSize = a.label.length > 22 ? '48px' : a.label.length > 13 ? '64px' : '88px';
      if (label) label.textContent = (a.fav ? 'FAVORIT' : 'APP') + ' · ZULETZT GEÖFFNET';
      if (meta) meta.textContent = a.last ? 'Zuletzt geöffnet am ' + new Date(a.last).toLocaleDateString('de-DE') : 'Noch nicht geöffnet';
      if (strip) qa('div', strip).forEach(function (f, i) { f.style.display = i < list.length ? '' : 'none'; f.style.opacity = i === hidx ? '1' : '0.22'; });
      thumbs.forEach(function (t, i) {
        var b = list[(hidx + 1 + i) % list.length], box = t.parentElement;
        if (!b || list.length < 2 || i >= list.length - 1) { box.style.display = 'none'; return; }
        box.style.display = '';
        t.innerHTML = '<div style="position:absolute;inset:0;background:#121724;display:flex;align-items:center;justify-content:center"><img src="' + img(b, 'icon') + '" style="width:36px;height:36px"></div>';
        if (t.nextElementSibling) t.nextElementSibling.textContent = b.label;
      });
      var old = document.getElementById('pl-bg'); if (old) old.parentNode.removeChild(old);
      if (a.img) {
        var bg = document.createElement('div'); bg.id = 'pl-bg';
        bg.style.cssText = 'position:absolute;inset:0;animation:pl-rise .45s both';
        bg.innerHTML = '<img src="' + img(a, 'image') + '" style="position:absolute;inset:0;width:100%;height:100%;object-fit:cover;animation:pl-drift 18s ease-in-out infinite;transform-origin:center;scale:1.08"><div style="position:absolute;inset:0;background:linear-gradient(90deg,#0A0D14 0%,#0A0D14 26%,rgba(10,13,20,.74) 54%,rgba(10,13,20,.12) 100%)"></div>';
        root.insertBefore(bg, root.firstChild);
      }
    }
    swapArt(); paint();
    root._switch = function (d) { if (list.length < 2) return; hidx = (hidx + d + list.length) % list.length; paint(); snd('fokus'); };
    var more = qa('div', root).filter(function (d) { return /ALS NÄCHSTES/.test(d.textContent) && d.children.length === 0; })[0];
    if (more) more.textContent = 'ALS NÄCHSTES · ' + Math.max(0, homeList().length - 1) + ' APPS';
  }

  /* Bibliothek oben: Raster aus echten Apps */
  function bindGrid() {
    var grid = qa('div', root).filter(function (d) { return /grid-template-columns:repeat\(5/.test(sty(d)) && d.querySelector('div') && /height:92px/.test(d.innerHTML); })[0];
    if (!grid) return;
    var tpl = grid.firstElementChild.cloneNode(true), list;
    if (cur === '3.4') list = apps.filter(function (a) { return a.fav; });
    else if (cur === '3.7') list = apps.slice();
    else { list = apps.slice(); if (pref('sort', 'last') === 'last') list.sort(function (a, b) { return b.last - a.last || a.label.localeCompare(b.label); }); }
    if (cur === '3.3') { var ql = query.toLowerCase(); list = ql ? list.filter(function (a) { return a.label.toLowerCase().indexOf(ql) >= 0; }) : list; }
    grid.innerHTML = '';
    var spacer = grid.nextElementSibling;
    if (spacer && !spacer.children.length && /flex:1 1 0/.test(sty(spacer))) spacer.style.display = 'none';
    var top = (grid.getBoundingClientRect().top - root.getBoundingClientRect().top) / (root.getBoundingClientRect().width / W);
    grid.style.cssText += ';flex:none;height:' + Math.max(140, 468 - top) + 'px;overflow:hidden;align-content:start;padding:6px;margin:-6px;grid-auto-rows:max-content;box-sizing:border-box';
    list.forEach(function (a, i) {
      var t = tpl.cloneNode(true), wrap = t.firstElementChild, pic = wrap.firstElementChild, texts = t.children[1], sub = t.children[2];
      wrap.style.outline = 'none'; wrap.style.animation = 'none'; wrap.dataset.scroll = '1'; delete wrap.dataset.had; if (i === 0) wrap.dataset.first = '1';
      pic.style.background = '#121724'; pic.style.display = 'flex'; pic.style.alignItems = 'center'; pic.style.justifyContent = 'center';
      pic.innerHTML = a.img ? '<img src="' + img(a, 'image') + '" style="width:100%;height:100%;object-fit:cover">' : '<img src="' + img(a, 'icon') + '" style="width:48px;height:48px">';
      if (texts) {
        texts.firstElementChild.textContent = a.label;
        var star = texts.querySelector('svg'); if (star) star.style.display = a.fav ? '' : 'none';
      }
      if (sub) sub.textContent = a.fav ? 'Favorit' : 'App';
      t.style.animation = 'pl-rise .35s cubic-bezier(.2,.8,.2,1) ' + Math.min(i, 14) * 30 + 'ms both';
      wrap._app = a; wrap._act = function () { launch(a); };
      grid.appendChild(t);
    });
    var count = qa('div', root).filter(function (d) { return d.children.length === 0 && /TREFFER/.test(d.textContent); })[0];
    if (count) count.textContent = list.length + ' TREFFER' + (query ? ' FÜR „' + query.toUpperCase() + '“' : '');
    if (cur === '3.3') { var cursor = qa('div', root).filter(function (d) { return /pl-blink/.test(sty(d)); })[0]; if (cursor && cursor.previousElementSibling) cursor.previousElementSibling.textContent = query; }
    if (!list.length) {
      var empty = document.createElement('div');
      empty.style.cssText = 'grid-column:1/-1;font-size:15px;font-weight:500;color:#A3ABBA;padding:24px 0';
      empty.textContent = cur === '3.4' ? 'Noch keine Favoriten. Start auf einer App setzt einen.' : 'Nichts gefunden.';
      grid.appendChild(empty);
    }
  }
  function bindAppRows() {
    var list = homeList(), i = 0;
    qa('div', root).filter(isRow).forEach(function (row) {
      var m = /^\[App (\d)\]$/.exec(labelOf(row)); if (!m) return;
      var a = list[i++]; if (!a) return;
      firstText(row).nodeValue = a.label;
      row._act = function () { snd('ok'); J.android('app:' + a.key); };
      if (!TOP) row.addEventListener('click', row._act);
    });
  }
  function bindController() {
    var cells = {}, total = 0;
    qa('div', root).forEach(function (d) { if (d.children.length === 0 && d.textContent === 'OFFEN') { var name = d.previousElementSibling ? d.previousElementSibling.textContent : ''; cells[name] = d; } });
    var sum = qa('div', root).filter(function (d) { return d.children.length === 0 && /ERKANNT/.test(d.textContent); })[0];
    seen = {};
    root._any = function (k) {
      var map = { A: 'A', B: 'B', X: 'X', Y: 'Y', L1: 'L1', R1: 'R1', L2: 'L2', R2: 'R2', START: 'Start', SELECT: 'Select', up: '↑', down: '↓', left: '←', right: '→' };
      var name = map[k]; if (!name || seen[name]) return;
      seen[name] = 1; total++; snd('an');
      if (cells[name]) { cells[name].textContent = 'OK'; cells[name].style.color = '#5FD38D'; }
      if (sum) sum.textContent = total + ' VON 14 ERKANNT';
      if (total >= 14) later(500, function () { run(next); });
    };
  }

  /* ---------- Wege zwischen den Ansichten (Notizen im Canvas) ---------- */
  var SETUP = ['1.7', '1.8', '1.9', '1.11', '1.12', '1.13', '1.14', '1.15'];
  function next() { var i = SETUP.indexOf(cur); snd('ok'); J.go(i >= 0 && i < SETUP.length - 1 ? SETUP[i + 1] : 'home'); }
  function done() { J.setPref('setup', '1'); J.go('home'); }
  function afterBoot() { J.swap(pref('setup', '') === '1' ? 'home' : '1.7'); }
  function swap(k) { return function () { snd('ok'); J.swap(k); }; }
  function tab(d) { return function () { var T = ['3.1', '3.4', '3.7'], i = T.indexOf(cur); snd('ok'); J.swap(T[(Math.max(i, 0) + d + 3) % 3]); }; }
  function focApp() { return foc && foc._app ? foc._app : null; }
  function details() { var a = focApp() || sel; if (!a) return; select(a); snd('ok'); J.go('3.5'); }
  function updInstall() { snd('ok'); J.go('6.3'); J.updateInstall(); }
  var GL = {
    'Zurück': 'back', 'Abbrechen': 'back', 'Später': 'back', 'Schließen': 'back', 'Startbildschirm': 'home', 'Zum Startbildschirm': done,
    'Bibliothek': '3.1', 'Einstellungen': '5.1', 'Weiter': next, 'Überspringen': next, 'Übernehmen': 'back', 'Fertig': 'back',
    'Schnellmenü': '4.1', 'Ori fragen': '8.1', 'Bildschirmfoto': '4.8', 'Schnell speichern': OS, 'Schnell laden': OS, 'Bildschirme tauschen': OS
  };
  var PG = {
    '1': { k: { START: next } },
    '2': { l: { 'Starten': function () { launch(sel); }, 'Details': details, 'Ordner wählen': '1.13', 'Alle Apps': '3.7', 'WLAN': '@wifi', 'Bluetooth': '@bt', 'Flugmodus': '@airplane', 'Leistung': '5.6', 'Update verfügbar': '6.2' },
           k: { A: function () { launch(sel); }, X: details, Y: '3.1', START: '5.1', B: null } },
    '3': { l: { 'Alle Spiele': swap('3.1'), 'Favoriten': swap('3.4'), 'Alle Apps': swap('3.7'), 'Alle Systeme': swap('3.1'), 'Zuletzt gespielt': function () { J.setPref('sort', 'last'); swap('3.1')(); },
                'Sortieren': function () { J.setPref('sort', pref('sort', 'last') === 'last' ? 'name' : 'last'); snd('ok'); J.sync('reload'); }, 'Bilder laden': '3.8',
                'Alle': null, 'Spiele': null, 'Emulatoren': null, 'System': null },
           k: { X: details, Y: '3.3', L1: tab(-1), R1: tab(1), START: function () { fav(focApp()); } } },
    '5': { k: { START: '@settings' } }
  };
  var NAV = {
    '1.1': { lock: 1, auto: [350, '1.2'] }, '1.2': { lock: 1, auto: [350, '1.3'] }, '1.3': { lock: 1, auto: [350, '1.4'] }, '1.4': { lock: 1, auto: [350, '1.5'] },
    '1.5': { lock: 1, auto: [350, '1.6'] }, '1.6': { lock: 1, auto: [1400, afterBoot] }, '1.18': { lock: 1, auto: [3700, afterBoot], enter: 'boot' },
    '1.7': { l: { 'Los geht’s': next }, k: { A: next } }, '1.8': { k: { A: next } }, '1.11': { k: {} },
    '1.9': { l: { 'WLAN-Liste öffnen': '@wifi', 'Anderes Netzwerk': '@wifi', 'Ohne WLAN fortfahren': '1.11' }, k: { X: '@wifi' } },
    '1.10': { l: { 'Verbinden': '1.11' }, k: { START: '1.11' } },
    '1.12': { k: { STARTLONG: next, B: null }, bind: bindController },
    '1.13': { l: { 'microSD-Karte': next, 'Interner Speicher': next, 'Später festlegen': next, 'Anderen Ordner wählen': '!Ordner-Wahl kommt mit der Spiele-Bibliothek', 'Übernehmen': next, 'Diesen Ordner nehmen': next } },
    '1.14': { l: { 'Neu suchen': '!Emulator-Suche kommt mit der Spiele-Bibliothek' }, k: { START: '1.15' } },
    '1.15': { k: { A: done }, enter: 'erfolg' },
    '1.16': { l: { 'Entsperren': 'home' }, k: { A: 'home' } }, '1.17': { l: { 'Weiterspielen': 'back' }, k: { A: 'back', B: 'home' } },
    '2.3': { k: { A: '1.13', Y: '3.7', X: null } }, '2.6': { k: { SELECT: '6.2' }, enter: 'hinweis' }, '2.7': { enter: 'warn' },
    '3.1': { k: { B: 'home' } }, '3.2': { k: { B: swap('3.1') } }, '3.4': { k: { B: 'home' } }, '3.7': { k: { B: 'home' } },
    '3.3': { k: { Y: null, START: 'back' } },
    '3.5': { l: { 'Starten': function () { launch(sel); }, 'Emulator wählen': '3.6', 'Favorit': function () { fav(sel); }, 'Bild ändern': '3.10', 'Speicherstände': '4.2', 'Ausblenden': OS },
             k: { X: function () { fav(sel); }, Y: null, L1: null, R1: null, START: null } },
    '3.6': { k: { START: 'back', X: null, Y: null, L1: null, R1: null } },
    '3.8': { l: { 'Im Hintergrund weiter': 'back' }, k: { X: 'back', Y: null, L1: null, R1: null, START: null }, note: 'Bilder aus dem Netz kommen noch. Eigene Bilder gehen schon: siehe 3.10.' },
    '3.9': { l: { 'Eigenes Bild wählen': '3.10', 'Erneut suchen': '3.8', 'Ohne Bild': 'back' }, k: { X: null, Y: null, L1: null, R1: null, START: null } },
    '3.10': { k: { A: 'back', X: null, Y: null, L1: null, R1: null, START: null }, note: function () { return 'Bild als <Paketname>.jpg ablegen in: ' + (st.images || 'Bilder'); } },
    '4.1': { l: { 'Fortsetzen': 'back', 'Speicherstand': '4.2', 'Leistung': '4.3', 'Beenden': '4.10' }, enter: 'menu' },
    '4.2': { l: { 'Platz 1': OS, 'Platz 2': OS, 'Platz 3': OS } }, '4.3': { l: { 'Lüfter': '5.6', 'Sparsam': OS, 'Ausgewogen': OS, 'Leistung': OS } },
    '4.4': { k: { B: 'home' } },
    '4.5': { auto: [2600, 'back'] }, '4.6': { auto: [2600, 'back'] }, '4.7': { auto: [2600, 'back'], enter: 'warn' },
    '4.8': { auto: [2600, 'back'], enter: 'foto' }, '4.9': { auto: [2600, 'back'], enter: 'hinweis' },
    '4.10': { l: { 'Weiter spielen': 'back', 'Speichern und beenden': 'home', 'Beenden': 'home' }, enter: 'dialog' },
    '5.1': { l: { 'Netzwerk': '5.2a', 'Anzeige': '5.3', 'Ton': '5.4', 'Controller': '5.5', 'Leistung und Lüfter': '5.6', 'Akku': '5.7', 'Speicher': '5.8', 'Apps und Berechtigungen': '5.9',
                  'Emulatoren': '5.10', 'Datum und Sprache': '5.11', 'Bedienungshilfen': '5.12', 'Über das Gerät': '5.13' }, k: { B: 'home' } },
    '5.2a': { l: { 'WLAN': '@wifi', 'WLAN-Liste öffnen': '@wifi', 'Netzwerk hinzufügen': '@wifi', 'Erweitert': '@wifisettings' }, k: { R1: swap('5.2b'), X: '@wifi' } },
    '5.2b': { l: { 'Bluetooth': '@bt', 'Sichtbar als': '@bt', 'Neues Gerät koppeln': '@bt' }, k: { L1: swap('5.2a'), X: '@bt' } },
    '5.3a': { bind: function () { qa('div,button', root).filter(isRow).forEach(function (r) { var w = WALLS[labelOf(r)]; if (w) r._act = function () { J.setPref('bg', w); snd('ok'); if (TOP) J.back(); else J.sync('reload'); }; }); } },
    '5.3': { l: { 'Hintergrund': '5.3a', 'Oben · Hintergrund': '5.3a', 'Oben · Farbe': '@display', 'Oben · Abschaltzeit': '@display', 'Unten · Farbe': '@display', 'Unten · Abschaltzeit': '@display' } },
    '5.4': { l: { 'Ausgabe': '@sound' } },
    '5.5': { l: { 'Tastenbelegung': '5.5a', 'Stick-Beleuchtung': '5.5b', 'Stick-Kalibrierung': '5.5c', 'Controller-Test': '1.12' } },
    '5.5a': { l: { 'Auf Standard zurücksetzen': OS } }, '5.5c': { l: { 'Speichern': 'back', 'Zurücksetzen': OS }, k: { A: 'back' } },
    '5.6': { l: { 'Sparsam': OS, 'Ausgewogen': OS, 'Leistung': OS, 'Lüfterkurve anpassen': OS } },
    '5.7': { l: { 'Verbrauch nach App': '@battery', 'Ladestand': '@battery', 'Restlaufzeit': '@battery' } },
    '5.8': { l: { 'Interner Speicher': '@storage', 'microSD-Karte': '@storage', 'Spiele': '@storage', 'Apps': '@apps', 'Bilder und Bildschirmfotos': '@storage', 'Zwischenspeicher leeren': '@storage', 'microSD-Karte auswerfen': '@storage' } },
    '5.9': { l: { 'Alle Apps anzeigen': '3.7', 'Berechtigungen': '5.9a', 'Standard-Apps': '@defaultapps', 'Apps aus unbekannten Quellen': '@unknown' }, bind: bindAppRows },
    '5.9a': { l: { 'Dateien und Speicher': '@apps', 'Mikrofon': '@apps', 'Standort': '@location', 'Benachrichtigungen': '@notifications', 'Geräte in der Nähe': '@apps', 'Über anderen Apps anzeigen': '@overlay' } },
    '5.10': { l: { 'Neu suchen': '!Emulator-Suche kommt mit der Spiele-Bibliothek' } },
    '5.11': { l: { 'Sprache': '@locale', 'Region': '@locale', 'Automatische Zeit': '@date', 'Zeitzone': '@date', '24-Stunden-Format': '@date', 'Tastatur': '@ime' } },
    '5.12': { l: { 'Hoher Kontrast': '@a11y' } },
    '5.13': { l: { 'Nach Updates suchen': '6.1', 'Android': '@about', 'Gerät': '@about', 'Lizenzen': '!Schriften: Space Grotesk und JetBrains Mono (OFL)', 'Auf Werkseinstellungen zurücksetzen': OS, 'Polarlicht': '6.1' } },
    '6.1': { enter0: function () { J.updateCheck(); } },
    '6.2': { l: { 'Jetzt laden': updInstall }, k: { A: updInstall } },
    '6.3': { l: { 'Pausieren': '!Pausieren geht noch nicht' }, k: { X: 'back' } },
    '6.4': { l: { 'Jetzt neu starten': OS }, k: { A: OS } }, '6.5': { lock: 1, auto: [3000, '6.6'] },
    '6.6': { l: { 'Fertig': 'home', 'Was ist neu': '!Die Änderungsliste kommt noch' }, k: { A: 'home', X: '!Die Änderungsliste kommt noch' }, enter: 'erfolg' },
    '6.7': { l: { 'Wiederholen': '6.1', 'Details': function () { note((st.upd && st.upd.msg) || 'Keine Meldung'); } }, k: { A: '6.1' }, enter: 'fehler' },
    '7.1': { l: { 'Standby': OS, 'Neu starten': OS, 'Ausschalten': OS }, enter: 'dialog' },
    '7.2': { l: { 'Alle entfernen': 'back', 'Polarlicht': '6.2' }, k: { START: 'back', SELECT: 'back' } },
    '7.3': { l: { 'Alle schließen': 'back' } }, '7.4': { k: { START: 'back' } },
    '7.5': { l: { 'Erlauben': 'back', 'Nur dieses Mal': 'back', 'Ablehnen': 'back' }, enter: 'dialog' }, '7.6': { l: { 'Installieren': OS }, enter: 'dialog' },
    '7.7': { l: { 'Neu starten': 'back', 'Schließen': 'home', 'Bericht ansehen': '!Kein Bericht vorhanden' }, enter: 'fehler' },
    '7.8': { l: { 'WLAN öffnen': '5.2a' }, enter: 'warn' }, '7.9': { l: { 'Speicher aufräumen': '5.8' }, enter: 'warn' },
    '8.1': { l: { 'Wo geht es weiter?': '8.2', 'Tipp ohne Spoiler': '8.2', 'Erklär mir diese Stelle': '8.2', 'Spiel anhalten': '4.1' }, k: { B: 'back' } },
    '8.2': { l: { 'Genauer': '!Ori ist noch nicht angeschlossen', 'Kürzer': '!Ori ist noch nicht angeschlossen', 'Danke': 'back' }, enter: 'ori' },
    '8.3': { l: { 'Mehr dazu': '8.2', 'Keine Tipps für dieses Spiel': 'back' }, enter: 'ori' }
  };
  function lookup(kind, name) {
    var n = NAV[cur] || {}, g = PG[cur.split('.')[0]] || {};
    if (n[kind] && name in n[kind]) return n[kind][name];
    if (g[kind] && name in g[kind]) return g[kind][name];
    if (kind === 'l' && name in GL) return GL[name];
    return undefined;
  }
  function run(t) {
    if (t === null || t === undefined) return;
    if (typeof t === 'function') { t(); return; }
    if (t === 'back') { snd('back'); J.back(); }
    else if (t === 'home') { snd('ok'); J.go('home'); }
    else if (t[0] === '@') { snd('ok'); J.android(t.slice(1)); }
    else if (t[0] === '!') { snd('taste'); note(t.slice(1)); }
    else { snd('ok'); J.go(t); }
  }
  function act(el) {
    if (!el) return;
    if (el._act) { el._act(); return; }
    var L = labelOf(el), t = lookup('l', L);
    var isKey = !TOP && el.tagName === 'BUTTON' && t === undefined;
    if (isKey && (L.length === 1 || L === 'Leerzeichen')) { snd('taste'); J.type(L === 'Leerzeichen' ? ' ' : L); return; }
    if (isKey && L === '') { snd('taste'); if (!el.nextElementSibling || labelOf(el.nextElementSibling) !== 'y') J.type('\b'); return; }
    var sw = findSwitch(el);
    if (t === undefined && sw) {
      var on = sw.dataset.on !== '1'; setSwitch(sw, on); snd(on ? 'an' : 'aus'); J.setPref('sw:' + L, on ? '1' : '0'); J.sync('');
      return;
    }
    if (t === undefined) { snd('ok'); return; }
    run(t);
  }

  /* ---------- Tasten (nur oben) ---------- */
  window.plKey = function (k) {
    if (!TOP || !root) return;
    if (k === 'INDEX') { toggleIndex(); return; }
    if (idx) { indexKey(k); return; }
    var n = NAV[cur] || {};
    if (root._any) root._any(k);
    if (n.lock) return;
    if (k === 'up' || k === 'down' || k === 'left' || k === 'right') {
      if (k === 'left' || k === 'right') {
        if (root._switch) { root._switch(k === 'right' ? 1 : -1); return; }
        var sl = foc && findSlider(foc);
        if (sl) { sliderSet(foc, sl, (+sl.dataset.v || parseFloat(sl.children[1].style.width) || 0) + (k === 'right' ? 5 : -5), true); snd('fokus'); J.sync(''); return; }
      }
      move(k); return;
    }
    var t = lookup('k', k);
    if (k === 'A') { if (t !== undefined && t !== null) run(t); else act(foc); return; }
    if (k === 'B') { run(t === undefined ? 'back' : t); return; }
    if (t === undefined && k === 'SELECT') t = '7.2';
    run(t);
  };

  /* ---------- Alle Ansichten (Select lang halten) ---------- */
  function toggleIndex() {
    if (idx) { idx.el.parentNode.removeChild(idx.el); idx = null; snd('back'); return; }
    var keys = Object.keys(window.PL_SCREENS), el = document.createElement('div');
    el.style.cssText = 'position:absolute;inset:0;background:rgba(10,13,20,.96);padding:28px 44px;box-sizing:border-box;color:#EEF1F6;font-family:\'Space Grotesk\',sans-serif;display:flex;flex-direction:column;gap:12px;z-index:50';
    el.innerHTML = '<div style="font-family:\'JetBrains Mono\',monospace;font-size:11px;color:#A3ABBA;letter-spacing:.04em">ALLE ANSICHTEN · ' + keys.length + '</div><div style="flex:1 1 0;min-height:0;overflow:hidden;display:grid;grid-template-columns:repeat(3,1fr);gap:6px;align-content:start;padding:6px;margin:-6px"></div>';
    var box = el.lastChild;
    keys.forEach(function (k) {
      var r = document.createElement('div');
      r.style.cssText = 'height:34px;border-radius:10px;background:#121724;display:flex;align-items:center;gap:10px;padding:0 12px;font-size:13px;font-weight:500;white-space:nowrap;overflow:hidden';
      r.innerHTML = '<span style="font-family:\'JetBrains Mono\',monospace;font-size:11px;color:#A3ABBA;width:34px;flex:none">' + k + '</span>' + window.PL_SCREENS[k];
      box.appendChild(r);
    });
    stage.appendChild(el); idx = { el: el, rows: qa('div', box), i: Math.max(0, keys.indexOf(cur)), keys: keys };
    indexPaint(); snd('dialog');
  }
  function indexPaint() {
    idx.rows.forEach(function (r, i) { r.style.outline = i === idx.i ? '2px solid #EEF1F6' : 'none'; r.style.background = i === idx.i ? '#1A2132' : '#121724'; });
    idx.rows[idx.i].scrollIntoView({ block: 'nearest' });
  }
  function indexKey(k) {
    var n = idx.keys.length, d = k === 'down' ? 3 : k === 'up' ? -3 : k === 'right' ? 1 : k === 'left' ? -1 : 0;
    if (d) { idx.i = Math.max(0, Math.min(n - 1, idx.i + d)); indexPaint(); snd('fokus'); return; }
    if (k === 'A') { var key = idx.keys[idx.i]; toggleIndex(); J.go(key); }
    if (k === 'B') toggleIndex();
  }

  /* ---------- Anzeigen ---------- */
  function show(key) {
    var html = J.html(key + '-' + side);
    if (!html) return;
    timers.forEach(clearTimeout); timers = []; idx = null;
    cur = key;
    try { st = JSON.parse(J.state()); apps = JSON.parse(J.apps()); } catch (e) { st = {}; apps = []; }
    sel = appByKey(pref('sel', '')) || homeList()[0] || null;
    stage.innerHTML = html; root = stage.firstElementChild; foc = null; tag();
    if (pref('sw:Bewegungen reduzieren', '0') === '1') root.classList.add('pl-still');
    var n = NAV[key] || {};
    if (TOP && key[0] === '2') bindHome();
    if (TOP && key[0] === '3') bindGrid();
    if (n.bind) n.bind();
    if (key === '5.13') qa('div', root).filter(isRow).forEach(function (r) { if (labelOf(r) === 'Android') qa('div', r).forEach(function (d) { if (d.textContent === '[Version]') d.textContent = st.android || '–'; }); });
    if (TOP && key === '3.5') {
      var art = root.querySelector(':scope > svg');
      if (art && sel && sel.img) { var pic = document.createElement('img'); pic.src = img(sel, 'image'); pic.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;object-fit:cover'; root.replaceChild(pic, art); }
      else swapArt();
    }
    fill(); wall(); collect(); applyStates(); fit();
    if (!TOP) { bindTouch(); return; }
    items.forEach(function (el) { el.addEventListener('click', function () { setFocus(el, true); act(el); }); });
    if (n.enter) snd(n.enter);
    if (n.enter0) n.enter0();
    if (n.note) later(600, function () { note(typeof n.note === 'function' ? n.note() : n.note); });
    if (n.auto) later(n.auto[0], function () { var t = n.auto[1]; if (typeof t === 'function') t(); else if (t === 'back') J.back(); else J.swap(t); });
    if (key === '7.2' && !(st.upd && st.upd.available)) qa('div', root).filter(isRow).forEach(function (r) { if (labelOf(r) === 'Polarlicht') r.parentNode.removeChild(r); });
  }
  function bindTouch() {
    items.forEach(function (el) {
      var sl = findSlider(el);
      if (sl && !findSwitch(el)) {
        var drag = function (e) {
          var p = e.touches ? e.touches[0] : e, r = sl.getBoundingClientRect();
          sliderSet(el, sl, (p.clientX - r.left) / r.width * 100, true); e.preventDefault();
        };
        el.addEventListener('touchstart', drag, { passive: false }); el.addEventListener('touchmove', drag, { passive: false });
        el.addEventListener('touchend', function () { J.sync(''); }); el.addEventListener('mousedown', drag);
        return;
      }
      if (el._bound) return; el._bound = 1;
      el.addEventListener('click', function () {
        el.style.transform = 'scale(.96)'; setTimeout(function () { el.style.transform = ''; }, 120);
        act(el);
      });
    });
    qa('div', root).filter(function (d) { return /height:64px/.test(sty(d)) && findSlider(d) && items.indexOf(d) < 0; }).forEach(function (row) {
      var sl = findSlider(row), L = labelOf(row), k = sliderKind(L);
      setSlider(sl, k === 'b' ? st.brightness : k === 'v' ? st.volume : 50);
      var drag = function (e) { var p = e.touches ? e.touches[0] : e, r = sl.getBoundingClientRect(); sliderSet(row, sl, (p.clientX - r.left) / r.width * 100, true); e.preventDefault(); };
      row.addEventListener('touchstart', drag, { passive: false }); row.addEventListener('touchmove', drag, { passive: false }); row.addEventListener('mousedown', drag);
    });
  }

  window.plShow = function (key) { try { show(key); } catch (e) { J.log('show ' + key + ': ' + e + ' ' + (e.stack || '')); } };
  window.plSync = function (what) {
    if (!root) return;
    if (what === 'reload') { var keep = foc && foc._app ? foc._app.key : null; show(cur); if (keep) items.forEach(function (el) { if (el._app && el._app.key === keep) setFocus(el, true); }); return; }
    try { st = JSON.parse(J.state()); } catch (e) { return; }
    applyStates();
  };
  window.plEvent = function (name, arg) {
    if (name === 'q') { query = arg || ''; if (TOP && cur === '3.3') { show(cur); } return; }
    if (name === 'update' && TOP) {
      try { st = JSON.parse(J.state()); } catch (e) { return; }
      var u = st.upd || {};
      if (cur === '6.1' && !u.busy) J.swap(u.error ? '6.7' : u.available ? '6.2' : '6.6');
      else if (cur === '6.3' && u.error) J.swap('6.7');
      else if (cur === '6.3' && u.msg) note(u.msg);
      else if (cur[0] === '2' && u.available && cur !== '2.6') J.go('home');
    }
  };

  /* Ohne Android (am PC im Browser): Beispieldaten, damit man die Ansichten prüfen kann. */
  function stub() {
    var P = {}, stack = [], now = null;
    var A = ['Dateien', 'Kamera', 'Dolphin', 'RetroArch', 'Steam Link', 'YouTube', 'Einstellungen', 'Browser', 'Galerie', 'Musik', 'Uhr', 'Rechner'].map(function (l, i) { return { label: l, key: 'p' + i + '/c', fav: i === 2 || i === 3, last: i < 4 ? 1795000000000 - i * 86400000 : 0, img: false }; });
    function to(k) { if (k === 'home') { stack = []; k = '2.2'; } now = k; window.plShow(k); if (window.parent !== window) window.parent.postMessage({ pl: k }, '*'); }
    window.addEventListener('message', function (e) { if (e.data && e.data.pl && e.data.pl !== now) { now = e.data.pl; window.plShow(now); } });
    window.addEventListener('keydown', function (e) {
      var m = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right', Enter: 'A', Escape: 'B', x: 'X', y: 'Y', q: 'L1', e: 'R1', s: 'START', Tab: 'SELECT', i: 'INDEX' }[e.key];
      if (m) { e.preventDefault(); window.plKey(m); }
    });
    setTimeout(function () { to((/s=([\d.a-c]+)/.exec(location.search) || [0, '2.2'])[1]); }, 0);
    return {
      html: function (n) { var x = new XMLHttpRequest(); x.open('GET', 's/' + n + '.html', false); x.send(); return x.status === 200 || x.status === 0 ? x.responseText : ''; },
      state: function () { var d = new Date(); return JSON.stringify({ time: d.toTimeString().slice(0, 5), date: d.toLocaleDateString('de-DE'), weekday: 'Dienstag', battery: 87, version: '0.4.0 · Build 6', tz: 'Europe/Berlin', model: 'AYN Thor', region: 'DE', used: '41 GB', total: '256 GB', free: '215 GB', temp: '31 °C', wifi: true, bt: false, airplane: false, brightness: 65, volume: 40, upd: {} }); },
      apps: function () { return JSON.stringify(A); }, pref: function (k) { return P[k]; }, setPref: function (k, v) { P[k] = v; },
      go: function (k) { if (now && k !== 'home') stack.push(now); to(k); }, swap: function (k) { to(k); }, back: function () { to(stack.pop() || 'home'); },
      launch: function (k) { note('Starte ' + k); }, fav: function (k) { A.forEach(function (a) { if (a.key === k) a.fav = !a.fav; }); },
      android: function (w) { note('Android: ' + w); }, setBrightness: function () {}, setVolume: function () {}, updateCheck: function () {}, updateInstall: function () {},
      type: function (c) { query = c === '\b' ? query.slice(0, -1) : query + c; window.plEvent('q', query); }, sync: function (w) { window.plSync(w); }, log: function (m) { console.log(m); }
    };
  }
  fit();
  if (window.PL) J.ready(side);
})();
