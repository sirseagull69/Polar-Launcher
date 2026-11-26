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
  window.plPref = pref;
  function perfNow() { try { return JSON.parse(J.perf ? J.perf() : '{}'); } catch (e) { return {}; } }
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
      if (name === 'fokus') { var tn = Date.now(); if (tn - (snd.t || 0) < 70) return; snd.t = tn; }
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
      if (el.tagName === 'BUTTON' || (/border-radius:22px/.test(s) && /min-height:\d+px;padding:0 1\dpx/.test(s))) el.dataset.pl = 'row';
      else if (/^border-radius:22px;/.test(s) && c && /height:92px/.test(sty(c))) el.dataset.pl = 'tile';
      else if (/width:46px;height:26px/.test(s)) { el.dataset.pl = 'sw'; el.dataset.on = /--acc/.test(s) ? '1' : '0'; }
      else if (/position:relative;height:(16|28)px/.test(s) && el.children.length === 3) el.dataset.pl = 'sl';
      if (/outline:2px solid/.test(s)) el.dataset.had = '1';
      if (el.dataset.pl === 'row' && el.tagName !== 'BUTTON' && /background:#(22222e|17171f)/i.test(s)) el.dataset.base = '#17171f';
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
    sw.style.background = on ? 'var(--acc)' : '#3a3a48';
    sw.firstElementChild.style.left = on ? '23px' : '3px';
    sw.firstElementChild.style.background = on ? '#0b0b10' : '#A3ABBA';
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
    if (!TOP && cur[0] === '2' && !root.dataset.mix) {
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
    var game = sel ? sel.label : 'Ori OS';
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
    qa('[data-live="time"]').forEach(function (e) { e.textContent = st.time; }); qa('[data-live="bat"]').forEach(function (e) { e.textContent = st.battery + ' %'; });
    var V = values(); live.forEach(function (x) { if (x.n.parentNode) x.n.nodeValue = fillText(x.tpl, V, {}).replace(/\u0001/g, '–'); });
  }
  setInterval(tick, 20000);

  /* ---------- Hintergrund (Canvas 0.7, wählbar in 5.3a) ---------- */
  var WALLS = { 'Farbflächen': 'farben', 'Polarlicht': 'polarlicht', 'Sterne': 'sterne', 'Wellen': 'wellen', 'Streifen': 'streifen', 'Bild des gewählten Spiels': 'bild', 'Aus': 'aus' };
  function wall(name) {
    var el = document.getElementById('pl-wall'); if (!el) return;
    name = name || pref('bg', 'farben');
    if (name === 'farben') { if (el.dataset.w !== 'farben') { el.dataset.w = 'farben'; el.innerHTML = mxBg(sel); } return; }
    if (name === 'bild') { if (sel && sel.img) { el.dataset.w = 'bild'; el.innerHTML = '<img src="' + img(sel, 'image') + '" style="position:absolute;inset:0;width:100%;height:100%;object-fit:cover;opacity:.55">'; return; } name = 'farben'; el.dataset.w = ''; wall('farben'); return; }
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
    if (on && root.dataset.mix) { el.style.outline = '3px solid var(--ringc,#fff)'; el.style.outlineOffset = '4px'; el.classList.add('mx-foc'); return; }
    if (!on && root.dataset.mix) { el.style.outline = 'none'; el.classList.remove('mx-foc'); return; }
    if (on) {
      el.style.outline = '3px solid #fff'; el.style.outlineOffset = '3px'; el.style.animation = 'pl-ring 2.6s ease-in-out infinite';
      if (el.dataset.base) el.style.background = '#30303f';
    } else {
      el.style.outline = 'none'; el.style.animation = 'none';
      if (el.dataset.base) el.style.background = el.dataset.base;
    }
  }
  function setFocus(el, quiet) {
    if (!el || foc === el) return;
    if (foc) ring(foc, false);
    foc = el; ring(el, true);
    if (el.dataset.scroll === 'box') {
      var bx = el.closest('.o-scroll');
      if (bx) {
        var r1 = el.getBoundingClientRect(), r2 = bx.getBoundingClientRect(), pad = 10;
        if (bx.classList.contains('o-grid')) {
          /* Im Raster auf ganze Reihen einrasten, damit oben keine halbe Reihe angeschnitten ist */
          var gcs = getComputedStyle(bx), stp = (parseFloat(gcs.gridAutoRows) || r1.height) + (parseFloat(gcs.rowGap) || 12);
          var ct = r1.top - r2.top + bx.scrollTop - (parseFloat(gcs.paddingTop) || 0), row = Math.round(ct / stp);
          var vis = Math.max(1, Math.floor((bx.clientHeight - (parseFloat(gcs.paddingTop) || 0) + (parseFloat(gcs.rowGap) || 12)) / stp)), top0 = Math.round(bx.scrollTop / stp);
          if (row < top0) top0 = row; else if (row > top0 + vis - 1) top0 = row - vis + 1;
          bx.scrollTop = top0 * stp;
        } else if (r1.top < r2.top + pad) bx.scrollTop -= r2.top + pad - r1.top;
        else if (r1.bottom > r2.bottom - pad) bx.scrollTop += r1.bottom - (r2.bottom - pad);
      }
    }
    else if (el.scrollIntoView && el.dataset.scroll) el.scrollIntoView({ block: 'nearest' });
    if (el._onfoc) el._onfoc();
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
    var a = foc.getBoundingClientRect(), horiz = dir === 'left' || dir === 'right', best = null, bs = 1e9;
    items.forEach(function (el) {
      if (el === foc) return;
      var b = el.getBoundingClientRect(), p, s, o;
      if (dir === 'left') p = a.left - b.right; else if (dir === 'right') p = b.left - a.right; else if (dir === 'up') p = a.top - b.bottom; else p = b.top - a.bottom;
      /* muss wirklich in der Richtung liegen: höchstens ein Viertel Überlappung */
      if (p < -Math.min(horiz ? a.width : a.height, horiz ? b.width : b.height) * 0.25) return;
      if (horiz) { o = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top); s = o > 0 ? 0 : -o + 1; }
      else { o = Math.min(a.right, b.right) - Math.max(a.left, b.left); s = o > 0 ? 0 : -o + 1; }
      var cx = Math.abs((b.left + b.right) - (a.left + a.right)) / 2, cy = Math.abs((b.top + b.bottom) - (a.top + a.bottom)) / 2;
      var score = Math.max(p, 0) + s * 4 + (horiz ? cy : cx) * 0.15;
      if (s > 0 && s > Math.max(p, 0) * 2 + 60) return;
      /* links/rechts: was auf gleicher Höhe liegt, gewinnt immer vor etwas schräg darunter (z. B. der Leiste) */
      if (horiz && s === 0) score -= 100000;
      if (score < bs) { bs = score; best = el; }
    });
    /* Von den Einstellungen nach links landet man beim gerade offenen Bereich, nicht irgendwo in der Liste */
    if (best && dir === 'left' && best.classList.contains('o-cat') && !foc.classList.contains('o-cat')) { var on = root.querySelector('.o-cat.on'); if (on) best = on; }
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
  function launch(a) { if (!a) return; if (a.game && TOP) { var gi0 = ginfo(a); if (gi0.sys && !(gi0.emus || []).length) { emuHol(gi0.sys); return; } } snd('start'); select(a); setTimeout(function () { J.launch(a.key); }, 380); }
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
  function next() { var i = SETUP.indexOf(cur); snd('ok'); if (i >= SETUP.length - 1) return done(); J.go(i >= 0 ? SETUP[i + 1] : 'home'); }
  function done() { J.setPref('setup', '1'); J.go('home'); }
  if (TOP) setTimeout(function () { if (J.bruecke && pref('aleiste', '0') !== '1') J.bruecke('sb statusbar-expansion'); }, 4000);
  function afterBoot() { J.setPref('setup', '1'); J.swap(pref('setup2', '') === '1' ? 'home' : 'o.setup1'); setTimeout(applyRgb, 3000); } /* alte Polarlicht-Einrichtung aus, die neue Ori-Einrichtung kommt später */
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
    '1.7': { l: { 'Los geht’s': next }, k: { A: next } }, '1.8': { k: { A: next } }, '1.11': { l: { 'Weiter': next }, k: { A: next } },
    '1.9': { l: { 'WLAN-Liste öffnen': '@wifi', 'Anderes Netzwerk': '@wifi', 'Ohne WLAN fortfahren': '1.11' }, k: { X: '@wifi' } },
    '1.10': { l: { 'Verbinden': '1.11' }, k: { START: '1.11' } },
    '1.12': { k: { STARTLONG: next, B: null }, bind: bindController },
    '1.13': { l: { 'microSD-Karte': next, 'Interner Speicher': next, 'Später festlegen': next, 'Anderen Ordner wählen': '!Ordner-Wahl kommt mit der Spiele-Bibliothek', 'Übernehmen': next, 'Diesen Ordner nehmen': next } },
    '1.14': { l: { 'Neu suchen': '!Emulator-Suche kommt mit der Spiele-Bibliothek' }, k: { START: '1.15' } },
    '1.15': { k: { A: done }, enter: 'erfolg' },
    '1.16': { l: { 'Entsperren': 'home' }, k: { A: 'home' } }, '1.17': { l: { 'Weiterspielen': 'back' }, k: { A: 'back', B: 'home' } },
    '2.3': { k: { A: '1.13', Y: '3.7', X: null } }, '2.6': { k: { SELECT: '6.2' }, enter: 'hinweis' }, '2.7': { enter: 'warn' },
    '3.1': { k: { B: 'home' } }, '3.2': { k: { B: swap('3.1') } }, '3.4': { k: { B: 'home' } }, '3.7': { k: { B: 'home' } },
    '3.3': { k: { Y: null, START: 'back', L1: null, R1: null }, enter0: function () { } },
    '3.5': { l: { 'Starten': function () { launch(sel); }, 'Emulator wählen': '3.6', 'Favorit': function () { fav(sel); }, 'Bild ändern': '3.10', 'Speicherstände': '4.2', 'Ausblenden': OS },
             k: { X: function () { fav(sel); }, Y: function () { if (sel && sel.game && (ginfo(sel).emus || []).length > 1) { snd('ok'); J.go('3.6'); } }, L1: null, R1: null, START: null } },
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
    '5.3a': { bind: function () { addRow('Polarlicht', 'Farbflächen', true); qa('div,button', root).filter(isRow).forEach(function (r) { var w = WALLS[labelOf(r)]; if (w) r._act = function () { J.setPref('bg', w); snd('ok'); if (TOP) J.back(); else J.sync('reload'); }; }); } },
    '5.3': { bind: function () { addRow('Hintergrund', 'Akzentfarbe', false); }, l: { 'Akzentfarbe': 'acc', 'Hintergrund': '5.3a', 'Oben · Hintergrund': '5.3a', 'Oben · Farbe': '@display', 'Oben · Abschaltzeit': '@display', 'Unten · Farbe': '@display', 'Unten · Abschaltzeit': '@display' } },
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
    'o.wlan': { enter0: function () { sysMsg = ''; wlanZiel = ''; sysDo('wlanSuchen'); } }, 'o.bt': { enter0: function () { sysMsg = ''; } }, 'o.speicher': { enter0: function () { sysMsg = ''; } }, 'o.emu': { enter0: function () { sysMsg = ''; } },
    '6.2': { l: { 'Jetzt laden': updInstall }, k: { A: updInstall } },
    '6.3': { l: { 'Pausieren': '!Pausieren geht noch nicht' }, k: { X: 'back' } },
    '6.4': { l: { 'Jetzt neu starten': OS }, k: { A: OS } }, '6.5': { lock: 1, auto: [3000, '6.6'] },
    '6.6': { l: { 'Fertig': 'home', 'Was ist neu': '!Die Änderungsliste kommt noch' }, k: { A: 'home', X: '!Die Änderungsliste kommt noch' }, enter: 'erfolg' },
    '6.7': { l: { 'Wiederholen': '6.1', 'Details': function () { note((st.upd && st.upd.msg) || 'Keine Meldung'); } }, k: { A: '6.1' }, enter: 'fehler' },
    '7.1': { l: { 'Standby': OS, 'Neu starten': OS, 'Ausschalten': OS }, enter: 'dialog' },
    '7.2': { l: { 'Alle entfernen': 'back', 'Ori OS': '6.2' }, k: { START: 'back', SELECT: 'back' } },
    '7.3': { l: { 'Alle schließen': 'back' } }, '7.4': { k: { START: 'back' } },
    '7.5': { l: { 'Erlauben': 'back', 'Nur dieses Mal': 'back', 'Ablehnen': 'back' }, enter: 'dialog' }, '7.6': { l: { 'Installieren': OS }, enter: 'dialog' },
    '7.7': { l: { 'Neu starten': 'back', 'Schließen': 'home', 'Bericht ansehen': '!Kein Bericht vorhanden' }, enter: 'fehler' },
    '7.8': { l: { 'WLAN öffnen': '5.2a' }, enter: 'warn' }, '7.9': { l: { 'Speicher aufräumen': '5.8' }, enter: 'warn' },
    '8.1': { l: { 'Wo geht es weiter?': '8.2', 'Tipp ohne Spoiler': '8.2', 'Erklär mir diese Stelle': '8.2', 'Spiel anhalten': '4.1' }, k: { B: 'back' } },
    '8.2': { l: { 'Genauer': '!Ori ist noch nicht angeschlossen', 'Kürzer': '!Ori ist noch nicht angeschlossen', 'Danke': 'back' }, enter: 'ori' },
    '8.3': { l: { 'Mehr dazu': '8.2', 'Keine Tipps für dieses Spiel': 'back' }, enter: 'ori' }
  };
  function addRow(like, label, before) {
    var src = qa('div,button', root).filter(isRow).filter(function (r) { return labelOf(r) === like; })[0]; if (!src) return;
    var c = src.cloneNode(true), t = firstText(c), w = document.createTreeWalker(c, NodeFilter.SHOW_TEXT, null), n;
    while ((n = w.nextNode())) if (n !== t) n.nodeValue = '';
    if (t) t.nodeValue = label;
    delete c.dataset.had; c.style.outline = 'none';
    src.parentNode.insertBefore(c, before ? src : src.nextSibling);
    if (!TOP) { src.parentNode.style.overflowY = 'auto'; src.parentNode.style.minHeight = '0'; src.parentNode.style.flex = '1 1 0'; }
  }
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
    if (el.dataset.act) { doAct(el.dataset.act); return; }
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
  /* Echte Leistungswerte für die Anzeige unten; behält die letzten Werte für die Balken */
  var PERF = { now: {}, hist: { cpu: [], temp: [], watt: [] }, at: 0 };
  window.plPerf = function () {
    if (Date.now() - PERF.at > 900) {
      PERF.at = Date.now(); var p = {}; try { p = JSON.parse(J.perf ? J.perf() : '{}'); } catch (e) { p = {}; }
      PERF.now = p; ['cpu', 'temp', 'watt'].forEach(function (k) { var a = PERF.hist[k]; a.push(p[k] == null ? -1 : p[k]); if (a.length > 14) a.shift(); });
    }
    return PERF;
  };
  var lastTouch = 0; document.addEventListener('pointerdown', function () { lastTouch = Date.now(); }, true);
  setInterval(function () { if (!TOP && root && /UntenLeistung/.test(cur || '') && !document.hidden && Date.now() - lastTouch > 2500) { try { show(cur); } catch (e) { /* weiter */ } } }, 2000);
  /* Update-Bildschirm im Ori-Look: dieselben Schritte 6.x, nur neu gezeichnet */
  window.plUpd = function () {
    var u = st.upd || {}, v = st.version || '', r = u.remote ? 'Build ' + u.remote : 'Neue Version';
    var O = function (n) { return 'ori/' + n + '.svg'; };
    var m = {
      '6.1': { kicker: 'UPDATES', title: 'Ich schau mal nach', spruch: 'Kurz Geduld.', sub: 'Frage bei GitHub nach, ob es was Neues für Ori gibt.', pct: '30%', ori: O('websuche-hell'), b: 'Zurück' },
      '6.2': { kicker: 'UPDATE GEFUNDEN', title: 'Ori OS · ' + r, spruch: 'Da liegt was Neues für dich.', sub: 'Lass dein Thor am besten am Strom. Spielstände bleiben, wo sie sind.', pct: '0%', ori: O('winkt-hell'), a: 'Jetzt laden', b: 'Später' },
      '6.3': { kicker: 'UPDATE', title: 'Wird eingespielt', spruch: 'Ich falte mich kurz neu.', sub: u.msg || 'Wird geladen …', pct: '60%', ori: O('stellt-bereit-hell'), b: 'Im Hintergrund' },
      '6.4': { kicker: 'UPDATE', title: 'Fast fertig', spruch: 'Einmal neu starten, dann passt alles.', sub: 'Ori startet danach von selbst wieder.', pct: '90%', ori: O('stellt-bereit-hell'), a: 'Jetzt neu starten' },
      '6.5': { kicker: 'UPDATE', title: 'Startet neu', spruch: 'Bin gleich wieder da.', sub: 'Nicht ausschalten.', pct: '95%', ori: O('stellt-bereit-hell') },
      '6.6': { kicker: 'UPDATES', title: 'Ori ist aktuell', spruch: 'Alles frisch gefaltet.', sub: 'Version ' + v + ' ist installiert.', pct: '100%', ori: O('erledigt-hell'), a: 'Fertig' },
      '6.7': { kicker: 'UPDATE', title: 'Hat nicht geklappt', spruch: 'Passiert. Nochmal?', sub: u.msg || 'Keine Meldung.', pct: '0%', ori: O('ratlos-hell'), a: 'Nochmal', b: 'Zurück' }
    };
    return m[cur] || m['6.6'];
  };
  window.plUpdAct = function (w) {
    var n = NAV[cur] || {}, k = (n.k || {})[w === 'a' ? 'A' : 'B'];
    if (w === 'b' && k === undefined) { run('back'); return; }
    if (k == null && w === 'a') { k = cur === '6.6' ? 'home' : cur === '6.7' ? '6.1' : null; }
    if (k) run(k);
  };
  /* Ori-Sperrbildschirm: kommt beim Aufwachen, A oder Tippen entsperrt und geht zurück, wo man war */
  var lockPrev = null, quietUntil = 0;
  window.plLock = function () { if (!TOP) return; if (!/^dc:Sperr:/.test(cur)) lockPrev = cur; J.go('dc:Sperr:UntenSperr'); };
  window.plLockInfo = function () {
    var p = {}; try { p = JSON.parse(J.perf ? J.perf() : '{}'); } catch (e) { p = {}; }
    var a = Math.max(0, Math.min(100, Math.round(p.akku >= 0 ? p.akku : st.battery) || 0));
    var laedt = !!p.laedt, netz = !!p.strom;
    var unter = laedt ? (a >= 100 ? 'Voll. Kannst abstecken.' : 'Lädt. Voll in ca. ' + Math.max(5, Math.round((100 - a) * 1.2)) + ' min')
      : netz ? 'Am Strom, alles voll.' : p.minuten ? 'Reicht noch ca. ' + (p.minuten >= 60 ? Math.floor(p.minuten / 60) + ' h ' + (p.minuten % 60) + ' min' : p.minuten + ' min')
      : a <= 15 ? 'Bitte bald ans Kabel.' : 'Alles da. Du fehlst noch.';
    var g = null; (apps || []).forEach(function (x) { if (x.game && x.last && (!g || x.last > g.last)) g = x; });
    lockGame = g;
    return { upd: !!(st.upd && st.upd.available), spiel: p.spiel || '', akkuZahl: a, laedt: laedt ? '1' : '',
      akkuWort: laedt ? 'Lädt' : netz ? 'Am Strom' : 'Akku', unterzeile: unter,
      pegel: Math.round(188 - a / 100 * 176), zahlFarbe: 188 - a / 100 * 176 < 100 ? '#fffdf8' : '#1b2340', wortFarbe: 188 - a / 100 * 176 < 128 ? '#fffdf8' : '#4d556e', ringFarbe: a <= 15 ? '#d6452f' : a <= 35 ? '#e08a2c' : '#3f9a63',
      oriPose: laedt ? 'sammelt' : a <= 15 ? 'ratlos' : 'ruhezustand',
      weiter: g ? g.label : '', weiterKopf: p.spiel ? 'LÄUFT GERADE' : 'WEITERSPIELEN', weiterBild: g && g.img ? img(g, 'image') : '', weiterSys: g && !g.img ? String(g.sys || 'Spiel').split(' ').pop().toUpperCase() : '' };
  };
  /* Sperrbildschirm unten: Flüssigkeit schwappt, beim Laden steigen Bläschen. Läuft nur, solange er zu sehen ist. */
  var lockGame = null, lkT = 0, lkBubs = [];
  function lkWave(y0, amp, len, ph, w) { w = w || 620; var d = 'M0 ' + y0; for (var x = 0; x <= w; x += 20) d += ' L' + x + ' ' + (y0 + Math.sin(x / len + ph) * amp).toFixed(1); return d + ' L' + w + ' 540 L0 540 Z'; }
  function lkLiq(y, amp, ph) { var d = 'M0 ' + y; for (var x = 0; x <= 200; x += 8) d += ' L' + x + ' ' + (y + Math.sin(x / 22 + ph) * amp).toFixed(1); return d + ' L200 200 L0 200 Z'; }
  function lkFrame() {
    var liq = document.getElementById('lk-liq'), tw1 = document.getElementById('lkt-w1');
    if ((!liq && !tw1) || document.hidden) { setTimeout(function () { requestAnimationFrame(lkFrame); }, 1000); return; }
    lkT += 0.03;
    if (tw1) {
      tw1.setAttribute('d', lkWave(470, 10, 140, lkT * 0.6, 960));
      var tw2 = document.getElementById('lkt-w2'); if (tw2) tw2.setAttribute('d', lkWave(500, 8, 110, -lkT * 0.8, 960));
      var to = document.getElementById('lkt-ori'); if (to) to.style.transform = 'translateY(' + (Math.sin(lkT) * 3).toFixed(1) + 'px)';
      if (!liq) { requestAnimationFrame(lkFrame); return; }
    }
    var y = +(liq.getAttribute('d').split(' ')[1]) || 100, sub = document.getElementById('lk-sub'), laedt = sub && sub.getAttribute('data-laedt') === '1';
    if (!liq._y) liq._y = y;
    var w1 = document.getElementById('lk-w1'), w2 = document.getElementById('lk-w2');
    if (w1) w1.setAttribute('d', lkWave(470, 10, 90, lkT * 0.6));
    if (w2) w2.setAttribute('d', lkWave(500, 8, 70, -lkT * 0.8));
    var base = liq._y;
    liq.setAttribute('d', lkLiq(base, laedt ? 5 : 3, lkT * 2));
    var l2 = document.getElementById('lk-liq2'); if (l2) l2.setAttribute('d', lkLiq(base, laedt ? 5 : 3, lkT * 2.6 + 1.7));
    var g = document.getElementById('lk-bub');
    if (g) {
      if (laedt && Math.random() < 0.12) lkBubs.push({ x: 50 + Math.random() * 100, y: 192, r: 2 + Math.random() * 4, v: 0.6 + Math.random() * 0.8 });
      lkBubs = lkBubs.filter(function (b) { b.y -= b.v; b.x += Math.sin(b.y / 9) * 0.3; return b.y > base + 4; });
      g.innerHTML = lkBubs.map(function (b) { return '<circle cx="' + b.x.toFixed(1) + '" cy="' + b.y.toFixed(1) + '" r="' + b.r.toFixed(1) + '" fill="none" stroke="#fffdf8" stroke-width="1.5" opacity=".8"/>'; }).join('');
    }
    var o = document.getElementById('lk-ori'); if (o) o.style.transform = 'translateY(' + (Math.sin(lkT) * 3).toFixed(1) + 'px)';
    requestAnimationFrame(lkFrame);
  }
  requestAnimationFrame(lkFrame);
  function unlock() { snd('ok'); var back = lockPrev && !/^dc:Sperr:/.test(lockPrev) ? lockPrev : 'home'; lockPrev = null; if (J.unlock) J.unlock(); quietUntil = Date.now() + 700; setTimeout(function () { J.swap(back); }, 120); }
  window.plLockTap = function () { if (TOP && /^dc:Sperr:/.test(cur)) unlock(); };
  var aynOn = false, botFocus = false;
  /* RGB-Ringe an den Sticks: aus den Einstellungen über die Systembrücke setzen */
  var RGBC = { Orange: '#ff6a1a', Pink: '#ff3c8c', Lila: '#8a4cff', Blau: '#2a5cff', 'Grün': '#22c060', Gold: '#ffb020', 'Weiß': '#ffffff' };
  function applyRgb() { if (!J.rgb) return; var p = function (id, d) { var v = pref('e:rgb:' + id, null); return v === null ? d : v; }; var on = p('an', '1') === '1'; var fx = { Atmen: 'atmen', Regenbogen: 'regenbogen', Welle: 'welle' }[p('effekt', 'Fest')], hex = (RGBC[p('farbe', 'Orange')] || '#ff6a1a').replace('#', ''), hl = Math.round((on ? +p('hell', 60) : 0) * 2.55); if (J.bruecke && J.bruecke('fx ' + (on ? fx || 'fest' : 'aus') + ' ' + hex + ' ' + hl) === 'ok' && fx) return; J.rgb(RGBC[p('farbe', 'Orange')] || '#ff6a1a', on ? +p('hell', 60) : 0); }
  window.plRgb = applyRgb;
  window.plFan = function (n) { var r = J.fan ? J.fan(n) : 'fehler'; note(r === 'ok' ? 'Lüfter: ' + n : 'Lüfter ließ sich nicht stellen.'); };
  window.plProfil = function (n) { var r = J.profil ? J.profil(n) : 'keine-bruecke'; note(r === 'ok' ? 'Profil ' + n + ' ist aktiv.' : r === 'keine-bruecke' ? 'Profil gemerkt. Wirkt ab dem nächsten OS-Update.' : 'Profil ließ sich nicht setzen.'); };
  /* Unten mit dem Controller bedienen (rechter Stick gedrückt): Rahmen zeigen, wo man ist */
  window.plBotFocus = function (on) {
    botFocus = !!on; if (TOP || !root) return;
    if (botFocus) { show(cur); if (items.length) setFocus(items[0], true); }
    else if (foc) { foc.classList.remove('mx-foc'); foc = null; }
  };

  /* Ruhemodus: nach 20 s ohne Eingabe stehen die Wellen still, das spart GPU und Akku */
  var lastInput = Date.now();
  function wach() { lastInput = Date.now(); document.documentElement.classList.remove('ruhig'); }
  ['pointerdown', 'keydown', 'wheel'].forEach(function (ev) { document.addEventListener(ev, wach, true); });
  setInterval(function () { if (Date.now() - lastInput > 20000) document.documentElement.classList.add('ruhig'); }, 5000);
  /* Foto- und Clip-Ansicht in Ori, statt die Android-Galerie zu öffnen */
  var medOffen = null;
  function medZeig(k) {
    if (!TOP) return;
    var all = []; try { all = JSON.parse(J.medien ? J.medien() : '[]'); } catch (e) { all = []; }
    var x = all.filter(function (m) { return String(m.k) === String(k); })[0]; if (!x) return;
    medZu(); snd('ok');
    var d = document.createElement('div'); d.className = 'o-medview';
    d.innerHTML = (x.clip ? '<video src="https://polarlicht.local/fotogross?k=' + x.k + '" autoplay controls style="max-width:100%;max-height:100%"></video>' : '<img src="https://polarlicht.local/fotogross?k=' + x.k + '" alt="">')
      + '<span class="o-medhint">' + esc(x.name) + ' · B Schließen</span>';
    d.addEventListener('click', medZu); document.body.appendChild(d); medOffen = d;
  }
  function medZu() { if (medOffen) { medOffen.remove(); medOffen = null; return true; } return false; }
  /* Beim Wechsel mit L1/R1 kurz groß zeigen, wo man landet */
  function ortZeig(name) {
    if (!name || !TOP) return;
    var n = document.getElementById('o-ort'); if (!n) { n = document.createElement('div'); n.id = 'o-ort'; document.body.appendChild(n); }
    n.textContent = name; n.className = 'zeig'; clearTimeout(ortZeig.t); ortZeig.t = setTimeout(function () { n.className = ''; }, 900);
  }
  window.plKey = function (k) {
    wach();
    if (medOffen) { if (k === 'B' || k === 'A') { medZu(); snd('back'); } return; }
    if ((!TOP && !botFocus) || !root) return;
    if (Date.now() < quietUntil) return;
    if (k === 'INDEX') { toggleIndex(); return; }
    if (k === 'AYN') { aynOn = !aynOn; snd('ok'); var tk = /^dc:/.test(cur) ? cur.split(':')[1] : '@' + cur; J.swap(aynOn ? 'dc:' + tk + ':UntenLeistung' : cur); return; }
    if (idx) { indexKey(k); return; }
    var n = NAV[cur] || {};
    if (root._any) root._any(k);
    if (n.lock) return;
    if (k === 'up' || k === 'down' || k === 'left' || k === 'right') {
      if (k === 'left' || k === 'right') {
        if (root._switch && (!root._switchOn || root._switchOn())) { root._switch(k === 'right' ? 1 : -1); return; }
        if (foc && foc._adj) { foc._adj(k === 'right' ? 5 : -5); snd('fokus'); return; }
        if (foc && foc._hue) { foc._hue(+foc.dataset.hue + (k === 'right' ? 10 : -10)); snd('fokus'); return; }
        var sl = foc && findSlider(foc);
        if (sl) { sliderSet(foc, sl, (+sl.dataset.v || parseFloat(sl.children[1].style.width) || 0) + (k === 'right' ? 5 : -5), true); snd('fokus'); J.sync(''); return; }
      }
      move(k); return;
    }
    var t = lookup('k', k);
    if ((k === 'L2' || k === 'R2') && root._switch) { root._switch(k === 'R2' ? 1 : -1); return; }
    if ((k === 'L1' || k === 'R1') && root._tabs) { root._tabs(k === 'R1' ? 1 : -1); return; }
    if (root.dataset.mix && cur !== '3.3' && cur !== 'acc' && (k === 'L1' || k === 'R1')) {
      /* L1/R1 gehen die Leiste entlang, in derselben Reihenfolge wie die Symbole unten */
      var T = ['home', '3.1', 'dc:Emulatoren:', '3.7', 'dc:Medien:' + DC_PAIR.Medien, '5.1', 'dc:Ausschalten:' + DC_PAIR.Ausschalten], at = cur[0] === '2' ? 0 : cur === '3.1' || cur === '3.2' || cur === '3.4' ? 1 : cur.indexOf('dc:Emulatoren') === 0 ? 2 : cur === '3.7' ? 3 : cur.indexOf('dc:Medien') === 0 ? 4 : /^5\./.test(cur) ? 5 : cur.indexOf('dc:Ausschalten') === 0 ? 6 : -1;
      if (at < 0) { snd('taste'); return; }
      var to = T[(at + (k === 'R1' ? 1 : T.length - 1)) % T.length];
      snd('ok'); ortZeig(['Start', 'Bibliothek', 'Emulatoren', 'Apps', 'Medien', 'Einstellungen', 'Aus'][T.indexOf(to)]); if (to === 'home') J.go('home'); else if (at === 0) J.go(to); else J.swap(to); return;
    }
    if (k === 'A' && root.dataset.mix) { act(foc); return; }
    if (k === 'A') { if (t !== undefined && t !== null) run(t); else act(foc); return; }
    if (k === 'B') { run(t === undefined ? 'back' : t); return; }
    if (t === undefined && k === 'SELECT') t = '7.2';
    run(t);
  };

  /* ---------- Alle Ansichten (Select lang halten) ---------- */
  function toggleIndex() {
    if (idx) { idx.el.parentNode.removeChild(idx.el); idx = null; snd('back'); return; }
    if (!window.PL_SCREENS['dc:Emulatoren:']) { var all = {}; Object.keys(DC_ALL).forEach(function (n) { all[DC_TOP.indexOf(n) >= 0 ? 'dc:' + n + ':' + (DC_PAIR[n] || '') : 'dc::' + n] = DC_ALL[n]; }); Object.keys(window.PL_SCREENS).forEach(function (k) { all[k] = window.PL_SCREENS[k]; }); window.PL_SCREENS = all; }
    var keys = Object.keys(window.PL_SCREENS), el = document.createElement('div');
    el.style.cssText = 'position:absolute;inset:0;background:rgba(10,13,20,.96);padding:28px 44px;box-sizing:border-box;color:#EEF1F6;font-family:\'Space Grotesk\',sans-serif;display:flex;flex-direction:column;gap:12px;z-index:50';
    el.innerHTML = '<div style="font-family:\'JetBrains Mono\',monospace;font-size:11px;color:#A3ABBA;letter-spacing:.04em">ALLE ANSICHTEN · ' + keys.length + '</div><div style="flex:1 1 0;min-height:0;overflow:hidden;display:grid;grid-template-columns:repeat(3,1fr);gap:6px;align-content:start;padding:6px;margin:-6px"></div>';
    var box = el.lastChild;
    keys.forEach(function (k) {
      var r = document.createElement('div');
      r.style.cssText = 'height:34px;border-radius:10px;background:#121724;display:flex;align-items:center;gap:10px;padding:0 12px;font-size:13px;font-weight:500;white-space:nowrap;overflow:hidden';
      r.innerHTML = '<span style="font-family:\'JetBrains Mono\',monospace;font-size:11px;color:#A3ABBA;width:34px;flex:none">' + (k.indexOf('dc:') === 0 ? 'Ori' : k) + '</span>' + window.PL_SCREENS[k];
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

  /* ---------- Neuer Look: Mix aus Kino und Bento (Start, Bibliothek, Bedienfeld) ---------- */
  var LIME = 'var(--acc)', INK = '#0b0b10', TILE = '#17171f', GLASS = 'background:#1e2029;box-shadow:inset 0 0 0 1px rgba(255,255,255,.06)';
  var MXI = {
    play: '<path d="M8 5v14l11-7z" fill="currentColor" stroke="none"></path>',
    grid: '<rect x="4" y="4" width="6" height="6" rx="1.5"></rect><rect x="14" y="4" width="6" height="6" rx="1.5"></rect><rect x="4" y="14" width="6" height="6" rx="1.5"></rect><rect x="14" y="14" width="6" height="6" rx="1.5"></rect>',
    set: '<path d="M4 7h10M18 7h2M4 17h4M12 17h8"></path><circle cx="16" cy="7" r="2"></circle><circle cx="10" cy="17" r="2"></circle>',
    wifi: '<path d="M4 10a12 12 0 0 1 16 0M7 13.5a7.5 7.5 0 0 1 10 0"></path><circle cx="12" cy="17.5" r="1"></circle>',
    bt: '<path d="M7 8l10 8-5 4V4l5 4-10 8"></path>', vol: '<path d="M5 10v4h3l4 4V6l-4 4zM16 9a4 4 0 0 1 0 6"></path>',
    sun: '<circle cx="12" cy="12" r="3.5"></circle><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4"></path>',
    bat: '<rect x="3" y="8" width="16" height="8" rx="2"></rect><path d="M21 11v2"></path>', plane: '<path d="M3 11l18-7-7 18-3-8z"></path>',
    star: '<path d="M12 4l2.4 5 5.6.8-4 3.9.9 5.5-4.9-2.6-4.9 2.6.9-5.5-4-3.9 5.6-.8z"></path>', search: '<circle cx="11" cy="11" r="6"></circle><path d="M16 16l4 4"></path>',
    down: '<path d="M12 4v11M7 11l5 5 5-5M5 20h14"></path>',
    back: '<path d="M10 6l-6 6 6 6M4 12h16"></path>', pad: '<rect x="3" y="8" width="18" height="9" rx="4.5"></rect><path d="M7.5 11v3M6 12.5h3"></path><circle cx="16" cy="12.5" r="1"></circle>'
  };
  function mi(n, s, w) { return '<svg aria-hidden="true" width="' + s + '" height="' + s + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="' + (w || 1.9) + '" stroke-linecap="round" stroke-linejoin="round" style="display:block;flex:none">' + MXI[n] + '</svg>'; }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  function hue(a) { var h = 0, s = a ? a.label : 'Polarlicht'; for (var i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360; return h; }
  function mxBg(a) {
    var h = hue(a);
    return '<div class="mx-bg"><i style="left:-200px;top:-280px;background:radial-gradient(circle,hsla(' + h + ',62%,36%,.95) 0,transparent 68%)"></i>'
      + '<i class="b" style="right:-240px;bottom:-340px;background:radial-gradient(circle,hsla(' + (h + 48) % 360 + ',58%,30%,.9) 0,transparent 68%)"></i>'
      + '<i class="c" style="left:38%;top:12%;background:radial-gradient(circle,hsla(' + (h + 320) % 360 + ',75%,58%,.26) 0,transparent 70%)"></i>'
      + (a && a.img ? '<img class="mx-art" src="' + img(a, 'image') + '">' : '') + '<div class="mx-scrim"></div></div>';
  }
  function mxNav(tab) {
    function p(t, act, on) { return '<button type="button" class="mx-pill' + (on ? ' on' : '') + '" data-act="' + act + '">' + t + '</button>'; }
    var upd = st.upd && st.upd.available ? '<button type="button" class="mx-pill on" data-act="go:6.2" style="margin-left:auto">' + mi('down', 18, 2.2) + 'Update da</button>' : '';
    return '<div class="mx-nav"><span class="mx-pill mx-clock" data-live="time">' + esc(st.time || '') + '</span>' + p('Start', 'home', tab === 0) + p('Spiele', 'swap:3.1', tab === 1) + p('Apps', 'swap:3.7', tab === 3) + p('Favoriten', 'swap:3.4', tab === 2) + p('System', 'go:5.1', false) + upd
      + '<span class="mx-pill mx-stat" style="' + (upd ? '' : 'margin-left:auto;') + '">' + (st.wifi ? mi('wifi', 17) : '') + '<span data-live="bat">' + esc(st.battery) + ' %</span></span></div>';
  }
  function mxKeys(list) {
    return '<div class="mx-keys">' + list.map(function (k) { return '<span><b>' + k[0] + '</b>' + k[1] + '</span>'; }).join('') + '</div>';
  }
  /* ---------- Ori OS: Papier-Look nach Nils' Vorlage (Canvas "Ori OS") ---------- */
  var PAL = [['#fbdbe9', '#b8327a', '#e2609a', '#8f1f55'], ['#f8dcd6', '#b23a28', '#d1604e', '#7c2418'], ['#d9efe0', '#2f7a4d', '#4c9c6c', '#1f5a37'], ['#f6e7c4', '#9a6a12', '#c9973a', '#6e4a08'], ['#dbe5f8', '#2b4a8f', '#3d5fb5', '#1c2f66'], ['#e4dcfb', '#6a4fc7', '#8a72e0', '#4a35a0'], ['#d8eef0', '#1f7480', '#4aa3ae', '#14525b'], ['#e6e8ef', '#3a4466', '#6b7596', '#1b2340']];
  var QUO = ['„Einer geht noch.“', '„Nur kurz reinschauen.“', '„Diesmal klappt es. Bestimmt.“', '„Wo waren wir stehen geblieben?“', '„Fünf Minuten. Höchstens.“', '„Der Akku hält das aus.“', '„Mal sehen, ob das läuft.“', '„Wie früher.“'];
  function pal(a) { return PAL[hue(a) % PAL.length]; }
  /* Ori, der gefaltete Kranich: Nils' eigene Animationen von selbstgfaltet.de. 'abendrot' für Papier, 'hell' für dunkle Flächen. */
  function ori(name, size, dark, cls) { return '<img class="o-ori' + (cls ? ' ' + cls : '') + '" data-ori="' + name + '" alt="" aria-hidden="true" width="' + size + '" height="' + size + '" src="ori/' + name + '-' + (dark ? 'hell' : 'abendrot') + '.svg">'; }
  var greeted = false;
  function quoteOf(a) { return !a ? '' : a.last ? QUO[(hue(a) * 7 + a.label.length) % QUO.length] : '„Noch nie gestartet. Wird Zeit.“'; }
  function fold(p) {
    return '<svg aria-hidden="true" width="100%" height="100%" viewBox="0 0 200 160" preserveAspectRatio="none" style="display:block"><polygon points="0,160 0,96 52,60 100,104 100,160" fill="' + p[1] + '"/><polygon points="100,160 100,104 148,44 200,92 200,160" fill="' + p[2] + '"/><polygon points="52,60 100,104 74,110" fill="' + p[3] + '"/><polygon points="148,44 200,92 166,98" fill="' + p[3] + '"/><polygon points="150,16 166,16 166,32 150,32" fill="#ffe36e"/></svg>';
  }
  /* Bild im Papierrahmen: echtes Cover, sonst gefaltete Berge, bei Apps das App-Symbol */
  function tileIn(a) {
    var p = pal(a);
    if (a.game) return '<span class="o-art" style="background:' + p[0] + '">' + (a.cov ? '<img class="cv" src="' + img(a, 'icon') + '" alt="">' : fold(p) + '<b class="o-sysl">' + esc(a.sysid.toUpperCase()) + '</b>') + '</span>';
    return '<span class="o-art" style="background:' + p[0] + '">' + (a.img ? '<img class="cv" src="' + img(a, 'image') + '" alt="">' : '<img class="ic" src="' + img(a, 'icon') + '" alt="">') + '</span>';
  }
  function tileBg() { return ''; }
  function homeRow() {
    return apps.slice().sort(function (a, b) { return (b.fav ? 1 : 0) - (a.fav ? 1 : 0) || b.last - a.last || (b.game ? 1 : 0) - (a.game ? 1 : 0) || a.label.localeCompare(b.label); }).slice(0, 12);
  }
  function kickOf(a) { return (a.game ? a.sys : 'App') + (a.fav ? ' · Favorit' : ''); }
  function subOf(a) { return kickOf(a); }
  function lastOf(a) { return a.last ? new Date(a.last).toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long' }) : 'noch nie'; }
  function placeOf(a) { return !a.game ? '' : a.key.indexOf('g:/storage/emulated/') === 0 ? 'Intern' : 'microSD'; }
  /* Papierwellen: weiche, runde Bögen in zwei Lagen, die langsam gegeneinander wandern */
  /* Fester Streifen ganz oben: wenn die Wellen nach unten schwingen, blitzt sonst das helle Papier durch */
  var W_TOP = '<i aria-hidden="true" style="position:absolute;left:0;top:0;width:960px;height:16px;background:#fbc19d"></i><svg class="welle2" aria-hidden="true" width="960" height="120" viewBox="0 0 960 120" preserveAspectRatio="none" style="position:absolute;left:0;top:0"><path d="M0,0H960V70C900,96 840,104 770,92S640,62 560,74S420,112 340,104S180,72 110,82S30,98 0,90Z" fill="#fdd3b8"/></svg><svg class="welle" aria-hidden="true" width="960" height="80" viewBox="0 0 960 80" preserveAspectRatio="none" style="position:absolute;left:0;top:0"><path d="M0,0H960V38C890,56 820,64 740,56S600,34 520,42S380,68 300,62S140,40 80,44S20,50 0,48Z" fill="#fbc19d"/></svg>';
  var W_BOT = '<svg class="welle2" aria-hidden="true" width="620" height="100" viewBox="0 0 620 100" preserveAspectRatio="none" style="position:absolute;left:0;bottom:0"><path d="M0,100V36C40,18 80,10 130,16S220,42 280,38S370,10 430,12S530,34 570,30S605,20 620,18V100Z" fill="#fdd3b8"/></svg><svg class="welle" aria-hidden="true" width="620" height="60" viewBox="0 0 620 60" preserveAspectRatio="none" style="position:absolute;left:0;bottom:0"><path d="M0,60V32C50,18 100,12 160,18S260,36 320,32S420,12 480,14S580,28 620,26V60Z" fill="#fbc19d"/></svg>';
  /* Statussymbole: WLAN, Bluetooth, Flugmodus und Akku, je nachdem, was gerade gilt */
  function oriStatus() {
    return (st.airplane ? '<svg role="img" aria-label="Flugmodus an" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"><path d="M3 11l18-7-7 18-3-8z"/></svg>' : '')
      + (st.wifi ? '<svg role="img" aria-label="WLAN verbunden" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M2 9a15 15 0 0 1 20 0M5.5 12.7a10 10 0 0 1 13 0M9 16.3a5 5 0 0 1 6 0M12 19.6h.01"/></svg>' : '')
      + (st.bt ? '<svg role="img" aria-label="Bluetooth an" width="16" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M7 7l10 10-5 4V3l5 4L7 17"/></svg>' : '')
      + (st.upd && st.upd.available ? '<svg role="img" aria-label="Update verfügbar" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4v11M7 11l5 5 5-5M5 20h14"/></svg>' : '')
      + '<span class="o-bat"><svg aria-hidden="true" width="26" height="14" viewBox="0 0 26 14"><rect x="1" y="1" width="21" height="12" rx="3" fill="none" stroke="currentColor" stroke-width="1.8"/><rect x="3.5" y="3.5" width="' + Math.max(1, Math.round(16 * Math.min(100, parseInt(st.battery, 10) || 0) / 100)) + '" height="7" rx="1.2" fill="' + ((parseInt(st.battery, 10) || 100) <= 15 ? '#c2410c' : 'currentColor') + '"/><rect x="23.4" y="4.5" width="2" height="5" rx="1" fill="currentColor"/></svg><b data-live="bat">' + parseInt(st.battery, 10) + ' %</b></span>';
  }
  function oriBar() {
    return '<div class="o-bar"><span class="o-bl"><b data-live="time">' + esc(st.time || '') + '</b><span>' + esc((st.weekday ? st.weekday.slice(0, 2) + '., ' : '') + (st.date || '')) + '</span></span><span class="o-br">' + oriStatus() + '</span></div>';
  }
  var DK = {
    start: 'M4 11l8-7 8 7v8a1 1 0 0 1-1 1h-4v-6h-6v6H5a1 1 0 0 1-1-1z', emu: 'M8 8h8v8H8zM5 5h14v14H5zM9 2v3M15 2v3M9 19v3M15 19v3M2 9h3M2 15h3M19 9h3M19 15h3', med: 'M3 5h18v14H3zM3 16l5-5 4 4 3-3 6 6M15.5 9.5h.01', aus: 'M12 3v8M6.3 6.8a8 8 0 1 0 11.4 0', bib: 'M4 5h4v14H4zM10 5h4v14h-4zM16.5 6l3.5 1-3 12-3.5-1z', apps: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z',
    fav: 'M12 4l2.4 5 5.6.8-4 3.9.9 5.5-4.9-2.6-4.9 2.6.9-5.5-4-3.9 5.6-.8z', einst: 'M4 7h10M18 7h2M4 17h2M10 17h10M16 5v4M8 15v4', upd: 'M12 4v11M7 11l5 5 5-5M5 20h14'
  };
  /* "A Auswählen · B Zurück" wird zu kleinen Tasten-Plättchen, damit man auf einen Blick sieht, was geht */
  function hintChips(h) {
    return String(h).split(' · ').slice(0, 3).map(function (p) {
      var m = p.match(/^(A|B|X|Y|L1\/R1|L1|R1|L\/R|Start|Select|START|↑↓)\s+(.*)$/);
      return m ? '<span class="o-hc"><i>' + m[1] + '</i>' + esc(m[2]) + '</span>' : '<span class="o-hc">' + esc(p) + '</span>';
    }).join('');
  }
  function oriDock(active, hint) {
    var items = [['start', 'Start', 'home'], ['bib', 'Bibliothek', 'swap:3.1'], ['emu', 'Emulatoren', 'dc:Emulatoren'], ['apps', 'Apps', 'swap:3.7'], ['med', 'Medien', 'dc:Medien'], ['einst', 'Einstellungen', 'go:5.1'], ['aus', 'Aus', 'dc:Ausschalten']];
    if (st.upd && st.upd.available) items.push(['upd', 'Update', 'go:6.2']);
    return '<nav class="o-dock" aria-label="Systemleiste"><span class="o-logo">' + ori('ruhezustand', 46) + '<b>Ori OS</b></span><div class="o-dki">'
      + items.map(function (i) { return '<button type="button" class="o-dk' + (i[0] === active ? ' on' : '') + '" data-act="' + i[2] + '"><svg aria-hidden="true" width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="' + DK[i[0]] + '"/></svg><span>' + i[1] + '</span></button>'; }).join('')
      + '</div><span class="o-hint">' + hintChips(hint || 'A Auswählen · B Zurück') + '</span></nav>';
  }
  var W_TOPB = '<svg class="welle2" aria-hidden="true" width="960" height="110" viewBox="0 0 960 110" preserveAspectRatio="none" style="position:absolute;left:0;bottom:0"><path d="M0,110V40C70,20 150,12 240,22S400,52 500,46S660,14 760,16S900,40 960,34V110Z" fill="#fdd3b8"/></svg><svg class="welle" aria-hidden="true" width="960" height="70" viewBox="0 0 960 70" preserveAspectRatio="none" style="position:absolute;left:0;bottom:0"><path d="M0,70V36C80,20 170,14 260,22S430,44 520,40S690,16 780,18S910,34 960,30V70Z" fill="#fbc19d"/></svg>';
  function oriTop(active, hint, main) { return '<div class="mx ori" style="width:960px;height:540px">' + W_TOP + W_TOPB + oriBar() + '<main class="o-main">' + main + '</main>' + oriDock(active, hint) + '</div>'; }
  function mxHome() {
    var list = homeRow(), at = sel ? sel.key : '';
    if (!list.some(function (a) { return a.key === at; })) at = list[0] ? list[0].key : '';
    return oriTop('start', 'A Starten · X Details · Y Bibliothek · L2/R2 blättern',
      '<div class="o-head"><div class="o-tt"><span class="o-kick" id="h-sys"></span><h1 class="o-h1" id="h-title"></h1><span class="o-quote" id="h-quote"></span></div>' + ori(greeted ? 'ruhezustand' : 'winkt', 92, false, 'o-ohome') + '<span style="flex:1"></span>'
      + '<div class="o-btns"><button type="button" class="o-btn" data-act="details">Details</button><button type="button" class="o-go" data-act="launch"><i>A</i>Starten</button></div></div>'
      + '<div class="o-row" id="h-row"><div class="o-track" id="h-track">' + list.map(function (a) { return '<button type="button" class="o-t" data-act="launch:' + esc(a.key) + '" data-key="' + esc(a.key) + '"' + (a.key === at ? ' data-first="1"' : '') + '><span class="o-frame">' + tileIn(a) + '</span><span class="o-tn">' + esc(a.label) + '</span></button>'; }).join('') + '</div></div>'
      + '<div class="o-chips"><span class="o-chip"><b>Zuletzt</b> · <span id="h-last"></span></span><span class="o-chip" id="h-ortc"><b>Liegt auf</b> · <span id="h-ort"></span></span><span style="flex:1"></span><button type="button" class="o-link" data-act="swap:' + (st.games ? '3.1' : '3.7') + '">Alle ' + (st.games ? 'Spiele' : 'Apps') + ' ansehen</button></div>');
  }
  var selT = 0, hOff = 0, txtT = 0;
  function mxHomeBind() {
    var track = document.getElementById('h-track'), tiles = qa('.o-t', track);
    /* Die Reihe bewegt sich nur über transform (läuft auf der Grafikkarte): nichts wird neu umbrochen, darum bleibt schnelles Blättern flüssig.
       Jede Kachel ist in voller Größe angelegt und wird verkleinert gezeigt, nur die gewählte steht auf 1. */
    var BIG = 237, S = 2 / 3, STEP = 158 + 14, PAD = 28, W = 960, max = Math.max(0, PAD * 2 + tiles.length * STEP - 14 + (BIG - 158) - W);
    function place(f) {
      var l = PAD + f * STEP;
      if (l - hOff < PAD) hOff = l - PAD; else if (l + BIG - hOff > W - PAD - 120) hOff = l + BIG - (W - PAD - 120);
      hOff = Math.max(0, Math.min(max, hOff));
      track.style.transform = 'translate3d(' + -hOff + 'px,0,0)';
      for (var i = 0; i < tiles.length; i++) tiles[i].style.transform = 'translate3d(' + (PAD + i * STEP + (i > f ? BIG - 158 : 0)) + 'px,0,0) scale(' + (i === f ? 1 : S) + ')';
    }
    function text(a) {
      if (!a || !document.getElementById('h-sys')) return; /* Ansicht inzwischen gewechselt */
      document.getElementById('h-sys').textContent = kickOf(a).toUpperCase();
      var t = document.getElementById('h-title'); t.textContent = a.label; t.style.fontSize = a.label.length > 26 ? '30px' : a.label.length > 18 ? '38px' : '46px';
      document.getElementById('h-quote').textContent = quoteOf(a);
      document.getElementById('h-last').textContent = lastOf(a);
      document.getElementById('h-ort').textContent = placeOf(a); document.getElementById('h-ortc').style.display = a.game ? '' : 'none';
    }
    var first = 0; tiles.forEach(function (el, i) { if (el.dataset.first) first = i; });
    track.style.transition = 'none'; tiles.forEach(function (el) { el.style.transition = 'none'; });
    place(first); if (tiles[first]) text(appByKey(tiles[first].dataset.key));
    requestAnimationFrame(function () { requestAnimationFrame(function () { track.style.transition = ''; tiles.forEach(function (el) { el.style.transition = ''; }); }); });
    if (!greeted) { greeted = true; var oh = root.querySelector('.o-ohome'); timers.push(setTimeout(function () { if (oh && oh.isConnected) oh.src = 'ori/ruhezustand-abendrot.svg'; }, 4400)); }
    tiles.forEach(function (el, i) {
      var a = appByKey(el.dataset.key || '');
      el._onfoc = function () {
        if (!a) return;
        place(i);
        /* Titel, Spruch und der untere Bildschirm ziehen erst nach, wenn das Blättern kurz steht */
        clearTimeout(txtT); txtT = setTimeout(function () { text(a); }, 70);
        if (!sel || sel.key !== a.key) { sel = a; clearTimeout(selT); selT = setTimeout(function () { J.setPref('sel', a.key); J.sync(''); }, 260); }
      };
    });
    if (!tiles.length) { document.getElementById('h-title').textContent = 'Noch nichts da'; }
    root._switch = function (d) { var i = tiles.indexOf(foc); if (i < 0) { setFocus(tiles[0]); return; } setFocus(tiles[Math.max(0, Math.min(tiles.length - 1, i + d))]); };
    root._switchOn = function () { return tiles.indexOf(foc) >= 0; };
  }
  function mxLib() {
    var favs = cur === '3.4', find = cur === '3.3', gamesTab = cur === '3.1' || cur === '3.2', list = apps.slice(), chips = '';
    if (find) { var ql = query.toLowerCase(); list = list.filter(function (a) { return !ql || a.label.toLowerCase().indexOf(ql) >= 0; }); }
    else if (favs) list = list.filter(function (a) { return a.fav; });
    else if (gamesTab) {
      list = list.filter(function (a) { return a.game; });
      var ids = [], f = pref('sysf', '');
      list.forEach(function (a) { if (ids.indexOf(a.sysid) < 0) ids.push(a.sysid); });
      if (ids.indexOf(f) < 0) f = '';
      if (ids.length > 1) chips = [''].concat(ids).map(function (id) { return '<button type="button" class="o-fc' + (id === f ? ' on' : '') + '" data-act="sys:' + id + '">' + (id ? esc(id.toUpperCase()) : 'Alle') + '</button>'; }).join('');
      rootSys = [''].concat(ids);
      if (f) list = list.filter(function (a) { return a.sysid === f; });
    } else list = list.filter(function (a) { return !a.game; });
    var byLast = pref('sort', 'last') === 'last';
    if (!find && !favs && byLast) list.sort(function (a, b) { return b.last - a.last || a.label.localeCompare(b.label); });
    var cards = list.map(function (a, i) {
      return '<button type="button" class="o-card" data-act="launch:' + esc(a.key) + '" data-key="' + esc(a.key) + '" data-scroll="box"' + (i === 0 ? ' data-first="1"' : '') + ' style="' + (i < 10 ? 'animation-delay:' + i * 26 + 'ms' : 'animation:none') + '">' + tileIn(a)
        + '<span class="o-ct"><b>' + (a.fav ? '★ ' : '') + esc(a.label) + '</b><span>' + esc(a.game ? a.sys : 'App') + '</span></span></button>';
    }).join('');
    if (!list.length) {
      if (gamesTab && !st.files) cards = '<div class="o-empty">' + ori('ratlos', 120) + '<b>Noch keine Spiele</b>Ori braucht einmal die Erlaubnis, deine Spieleordner zu lesen.<button type="button" class="o-go" data-act="and:files" data-first="1">Zugriff erlauben</button></div>';
      else if (gamesTab) cards = '<div class="o-empty">' + ori(st.scanning ? 'sammelt' : 'ratlos', 120) + '<b>' + (st.scanning ? 'Suche läuft …' : 'Keine Spiele gefunden') + '</b>Leg deine Spiele in einen Ordner „ROMs“ mit einem Unterordner je System, zum Beispiel ROMs/nds, ROMs/gba oder ROMs/switch. Bilder holt Ori sich selbst.<button type="button" class="o-go" data-act="rescan" data-first="1">Neu suchen</button></div>';
      else cards = '<div class="o-empty">' + ori('ratlos', 120) + '<b>' + (favs ? 'Noch keine Favoriten' : 'Nichts gefunden') + '</b>' + (favs ? 'Start auf einem Spiel oder einer App setzt einen Favoriten.' : '') + '</div>';
    }
    var name = find ? 'Suche' : favs ? 'Favoriten' : gamesTab ? 'Bibliothek' : 'Apps', count = list.length + (find ? ' Treffer' : favs ? ' Favoriten' : gamesTab ? (list.length === 1 ? ' Spiel' : ' Spiele') : ' Apps');
    var head = find
      ? '<div class="o-lh"><div class="o-find">' + '<svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0M20 20l-4-4"/></svg><span>' + (query ? esc(query) : '<em>Unten tippen …</em>') + '</span><i class="blink"></i></div>' + ori(list.length ? 'websuche' : 'ratlos', 54) + '</div>'
      : '<div class="o-lh"><div style="display:flex;align-items:baseline;gap:12px"><h1 class="o-h2">' + name + '</h1><span class="o-kick">' + (favs ? 'DEINE LIEBLINGE' : gamesTab ? 'ALLE SPIELE' : 'ALLES ANDERE') + '</span></div><span class="o-say">' + '<span class="o-quote" style="font-size:21px">' + (favs ? 'Die üblichen Verdächtigen.' : gamesTab ? 'Und du nimmst eh wieder dasselbe.' : 'Was man halt so braucht.') + '</span>' + ori(favs ? 'triumph' : gamesTab ? 'sassy' : 'ruhezustand-2', 54) + '</span></div>';
    return oriTop(find ? '' : favs ? 'fav' : gamesTab ? 'bib' : 'apps', find ? 'A Öffnen · X Details · B Suche schließen' : 'A Öffnen · X Details · Y Suchen · Start Favorit' + (chips ? ' · L2/R2 Filter' : ''),
      head + '<div class="o-fcs">' + chips + (find || favs ? '' : '<button type="button" class="o-fc" data-act="sort">' + (byLast ? 'Zuletzt zuerst' : 'A bis Z') + '</button>') + (find ? '' : '<button type="button" class="o-fc" data-act="go:3.3">Suchen</button>') + '<span style="flex:1"></span><span class="o-cnt">' + count + (find || favs ? '' : ' · sortiert nach „' + (byLast ? 'Zuletzt gespielt' : 'Name') + '“') + '</span></div>'
      + '<div class="o-grid o-scroll">' + cards + '</div>');
  }
  var rootSys = [''];
  function mxLibBind() {
    if (cur !== '3.1' && cur !== '3.2') return;
    root._switch = function (d) { if (rootSys.length < 2) return; var i = Math.max(0, rootSys.indexOf(pref('sysf', ''))); J.setPref('sysf', rootSys[(i + d + rootSys.length) % rootSys.length]); snd('ok'); show(cur); };
    root._switchOn = function () { return false; };
  }
  function uHead(title, right) { return '<header class="u-head"><b>' + title + '</b><span>' + right + '</span></header>'; }
  function mxPanel() {
    var feld = pref('unten', 'start') === 'feld', a = sel;
    var NIC = { 'Bibliothek': 'bib', 'Apps': 'apps', 'Favoriten': 'fav', 'Einstellungen': 'einst', 'Start': 'start', 'Medien': 'med' };
    function nav(t, act, on) { var ic = NIC[t] && DK[NIC[t]] ? '<svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="' + DK[NIC[t]] + '"/></svg>' : ''; return '<button type="button" class="u-nav' + (ic ? ' u-ic' : '') + (on ? ' on' : '') + '" data-act="' + act + '">' + ic + '<span>' + t + '</span></button>'; }
    if (feld) {
      var tg = function (n, on, act) { return '<button type="button" class="u-tg' + (on ? ' on' : '') + '" data-act="' + act + '"><b>' + n + '</b><span>' + (on ? 'An' : 'Aus') + '</span></button>'; };
      var sl = function (k, n, v) { return '<div class="u-sl"><label for="u-' + k + '">' + n + '</label><input id="u-' + k + '" type="range" min="0" max="100" value="' + v + '" data-hs="' + k + '"></div>'; };
      return '<div class="mx ori" style="width:620px;height:540px;display:flex;flex-direction:column">' + uHead('Bedienfeld', esc(st.time || '') + ' · Akku ' + parseInt(st.battery, 10) + ' %' + (st.wifi ? ' · WLAN' : '') + '<button type="button" class="u-hl" data-act="unten:start">Start</button>') + W_BOT
        + '<div class="u-body"><section class="u-tgs">' + tg('WLAN', st.wifi, 'sys:wlan:' + (st.wifi ? '0' : '1')) + tg('Bluetooth', st.bt, 'sys:bt:' + (st.bt ? '0' : '1')) + tg('Flugmodus', st.airplane, 'sys:flug:' + (st.airplane ? '0' : '1')) + '</section>'
        + '<section class="u-card">' + sl('b', 'Helligkeit', st.brightness || 0) + sl('v', 'Lautstärke', st.volume || 0) + '</section>'
        + '<span class="o-say l">' + ori((st.battery || 100) <= 15 ? 'ungeduldig' : 'ruhezustand-2', 58) + '<span class="o-quote" style="font-size:20px">Akku ' + parseInt(st.battery, 10) + ' %' + (st.temp ? ', ' + esc(st.temp) : '') + '. ' + esc(st.free || '') + ' frei.</span></span>'
        + '<nav class="u-navs s">' + nav('Leistung', 'dc:UntenLeistung') + nav('RGB', 'dc:UntenRgb') + nav('Tasten', 'dc:UntenTasten') + nav('Touchpad', 'dc:UntenTouchpad') + nav('Akku', 'dc:UntenAkku') + nav('Mitteilungen', 'dc:Mitteilungen') + '</nav>'
        + '<div style="flex:1"></div><nav class="u-navs">' + nav('Start', 'home', true) + nav('Bibliothek', st.games ? 'go:3.1' : 'go:3.7') + nav('Einstellungen', 'go:5.1') + nav('Suchen', 'go:3.3') + '</nav></div></div>';
    }
    var favs = homeRow().slice(0, 6);
    return '<div class="mx ori" style="width:620px;height:540px;display:flex;flex-direction:column">' + uHead('Start', 'Tippen startet oben<button type="button" class="u-hl" data-act="unten:feld">Bedienfeld</button>') + W_BOT
      + '<div class="u-body"><button type="button" class="u-search" data-act="go:3.3"><svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0M20 20l-4-4"/></svg>Spiel oder App suchen</button>'
      + '<b class="u-lb">' + (favs.some(function (x) { return x.fav; }) ? 'Favoriten und zuletzt' : 'Zuletzt') + '</b><div class="u-favs">' + favs.map(function (b) { return '<button type="button" class="u-fav' + (a && a.key === b.key ? ' on' : '') + '" data-act="launch:' + esc(b.key) + '"><span class="u-fa">' + tileIn(b) + '</span><span class="u-ft"><b>' + esc(b.label) + '</b><span>' + esc(b.game ? b.sysid.toUpperCase() : 'App') + '</span></span></button>'; }).join('') + '</div>'
      + '<span class="o-say l">' + ori('ruhezustand', 58) + '<span class="o-quote" style="font-size:20px">' + (a ? esc(a.label) + ' ist oben ausgewählt.' : 'Oben ist noch nichts ausgewählt.') + '</span></span>'
      + (a ? '<div class="u-acts"><button type="button" class="o-go" data-act="launch"><i>A</i>' + (a.game ? 'Spielen' : 'Öffnen') + '</button><button type="button" class="o-btn" data-act="fav">' + (a.fav ? '★ Favorit' : '☆ Favorit') + '</button><button type="button" class="o-btn" data-act="details">Details</button></div>' : '')
      + '<div style="flex:1"></div><nav class="u-navs">' + nav('Bibliothek', st.games ? 'go:3.1' : 'go:3.7') + nav('Apps', 'go:3.7') + nav('Favoriten', 'go:3.4') + nav('Einstellungen', 'go:5.1') + '</nav></div></div>';
  }
  function mxPanelBind() {
    qa('input[data-hs]', root).forEach(function (el) {
      el.addEventListener('input', function () { if (el.dataset.hs === 'b') J.setBrightness(+el.value); else J.setVolume(+el.value); });
    });
  }
  var ACC0 = '#c2410c', OLD0 = '#16161a'; /* OLD0 war die Vorgabe im hellen Look und wäre auf Dunkel unsichtbar */
  var ACCS = ['#c2410c', '#5CF2C0', '#4DE8FF', '#6FB2FF', '#B58CFF', '#FF7AC3', '#FF6B5E', '#FF9A3D', '#FFE14D', '#C6FF3D'];
  function accDark(c) { var m = /^#([0-9a-f]{6})$/i.exec(c); if (!m) return false; var n = parseInt(m[1], 16); return (0.299 * (n >> 16) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255)) < 140; }
  function applyAcc() { var c = pref('acc', ACC0), e = document.documentElement.style; if (c === OLD0 || c === '#5CF2C0') c = ACC0; e.setProperty('--acc', c); e.setProperty('--acct', accDark(c) ? '#fff' : '#16161a'); }
  function setAcc(c) { J.setPref('acc', c); applyAcc(); J.sync(''); }
  function mxAcc() {
    var now = pref('acc', ACC0), hueNow = +pref('acch', '80');
    var sw = ACCS.map(function (c, i) { return '<button type="button" class="mx-sw" data-act="acc:' + c + '"' + (c === now || (i === 0 && ACCS.indexOf(now) < 0 && now.indexOf('hsl') < 0) ? ' data-first="1"' : '') + ' style="background:' + c + '" aria-label="Farbe ' + (i + 1) + '"></button>'; }).join('');
    if (!TOP) return '<div class="mx" style="width:620px;height:540px;padding:22px;box-sizing:border-box;display:flex;flex-direction:column;gap:16px"><div class="mx-bg"></div><div class="mx-count" style="position:relative">Akzentfarbe</div><div class="mx-swg" style="position:relative;grid-template-columns:repeat(5,1fr)">' + sw + '</div><div class="mx-hue" data-hue="' + hueNow + '" style="position:relative;height:64px"><i style="left:' + hueNow / 3.6 + '%"></i></div><button type="button" class="mx-pr" data-act="go:back" style="position:relative;flex:none">Fertig</button></div>';
    return '<div class="mx" style="width:960px;height:540px"><div id="mx-bgs">' + mxBg(sel) + '</div><div style="position:absolute;left:28px;top:34px;right:28px"><div class="mx-s" style="letter-spacing:.08em">SYSTEM · ANZEIGE</div><div class="mx-title" style="font-size:44px;margin-top:6px">Akzentfarbe</div><div class="mx-meta" style="margin-top:8px">Gilt für Knöpfe, Fokus, Schalter und Regler. Die Farbe wechselt sofort.</div></div>'
      + '<div class="mx-swg" style="position:absolute;left:28px;top:190px;width:560px;grid-template-columns:repeat(5,1fr)">' + sw + '</div>'
      + '<button type="button" class="mx-hue" data-hue="' + hueNow + '" style="position:absolute;left:28px;top:400px;width:560px;height:44px"><i style="left:' + hueNow / 3.6 + '%"></i></button>'
      + '<div style="position:absolute;right:28px;top:190px;width:300px;border-radius:26px;padding:20px;' + GLASS + ';display:flex;flex-direction:column;gap:14px"><div class="mx-s">Vorschau</div><span class="mx-tag">WEITERSPIELEN</span><span class="mx-play" style="align-self:flex-start">' + mi('play', 20) + 'Starten</span><div class="mx-bar"><i style="width:62%;background:var(--acc)"></i></div></div>'
      + mxKeys([['A', 'Farbe nehmen'], ['← →', 'Auf dem Farbband: eigene Farbe'], ['B', 'Zurück']]) + '</div>';
  }
  function mxAccBind() {
    qa('.mx-hue', root).forEach(function (el) {
      el._hue = function (h) { h = (Math.round(h) + 360) % 360; el.dataset.hue = h; el.firstElementChild.style.left = h / 3.6 + '%'; J.setPref('acch', String(h)); setAcc('hsl(' + h + ',95%,62%)'); };
      var drag = function (e) { var p = e.touches ? e.touches[0] : e, r = el.getBoundingClientRect(); el._hue(Math.max(0, Math.min(359, (p.clientX - r.left) / r.width * 360))); e.preventDefault(); };
      el.addEventListener('touchstart', drag, { passive: false }); el.addEventListener('touchmove', drag, { passive: false });
    });
  }
  /* Die Ansichten aus dem Canvas bekommen Farben, Schrift und Rundungen des neuen Looks. */
  function skin(h) {
    return h.replace(/background:#EEF1F6;color:#0A0D14;border:1\.5px solid #EEF1F6/g, 'background:var(--acc);color:#0b0b10;border:1.5px solid var(--acc)')
      .replace(/font-size:(\d\d)px;font-weight:700;color:#EEF1F6;line-height:1\.25;letter-spacing:-\.02em/g, function (m, n) { return +n < 26 ? m : 'font-family:Unbounded,sans-serif;font-size:' + Math.round(n * 0.8) + 'px;font-weight:800;color:#fff;line-height:1.25;letter-spacing:-.04em'; })
      .replace(/'Space Grotesk',system-ui,sans-serif/g, 'Outfit,system-ui,sans-serif').replace(/'JetBrains Mono',ui-monospace,monospace/g, 'Outfit,system-ui,sans-serif')
      .replace(/#0A0D14/g, '#0b0b10').replace(/#121724/g, '#17171f').replace(/#1A2132/g, '#22222e').replace(/#1F2533/g, '#2a2a36').replace(/#3A4358/g, '#3a3a48')
      .replace(/#5FD38D/g, 'var(--acc)').replace(/border-radius:14px/g, 'border-radius:22px');
  }

  /* ---------- Spielseite (3.5) und Emulator-Auswahl (3.6) ---------- */
  function ginfo(a) { try { return a && a.game && J.gameInfo ? JSON.parse(J.gameInfo(a.key)) : {}; } catch (e) { return {}; } }
  function mb(n) { n = +n || 0; return n >= 1073741824 ? (n / 1073741824).toFixed(1).replace('.', ',') + ' GB' : n >= 1048576 ? Math.round(n / 1048576) + ' MB' : n ? Math.max(1, Math.round(n / 1024)) + ' KB' : '–'; }
  function emuNow(g) { var e = g.emus || []; return e.indexOf(g.chosen) >= 0 ? g.chosen : e[0] || ''; }
  function mxDet() {
    var a = sel; if (!a) return '<div class="mx ori" style="width:' + (TOP ? 960 : 620) + 'px;height:540px"></div>';
    var g = ginfo(a), emu = emuNow(g), n = (g.emus || []).length;
    if (!TOP) {
      var t = function (x, y, act, on) { return '<button type="button" class="u-big' + (on ? ' on' : '') + '" data-act="' + act + '"><b>' + x + '</b><span>' + y + '</span></button>'; };
      return '<div class="mx ori" style="width:620px;height:540px;display:flex;flex-direction:column">' + uHead(esc(a.label), esc(kickOf(a))) + W_BOT
        + '<div class="u-body"><div class="u-bigs">' + t(a.game ? 'Spielen' : 'Öffnen', 'Startet auf dem oberen Bildschirm', 'launch', true)
        + t(a.fav ? 'Favorit entfernen' : 'Als Favorit', a.fav ? 'Steht vorn auf der Startseite' : 'Kommt vorn auf die Startseite', 'fav')
        + (a.game ? t('Emulator', emu ? esc(emu) : 'Keiner installiert', n ? 'go:3.6' : 'go:!Für ' + esc(g.sysFull || a.sys) + ' ist kein passender Emulator installiert.') : t('App-Info', 'Android-Einstellungen', 'appinfo:' + esc(a.key)))
        + t('Zurück', 'Zur Bibliothek', 'go:back') + '</div></div></div>';
    }
    var facts = a.game
      ? [['System', g.sysFull || a.sys], ['Emulator', emu || 'Keiner installiert'], ['Datei', g.file || '–'], ['Größe', mb(g.size)], ['Liegt auf', placeOf(a)], ['Zuletzt', lastOf(a)]]
      : [['Art', 'App'], ['Favorit', a.fav ? 'Ja' : 'Nein'], ['Paket', a.key.split('/')[0]], ['Zuletzt', lastOf(a)]];
    return oriTop('', 'A Auswählen · X Favorit' + (a.game && n > 1 ? ' · Y Emulator' : '') + ' · B Zurück',
      '<div class="o-det"><div class="o-dl"><span class="o-frame big">' + tileIn(a) + '</span><button type="button" class="o-link" data-act="go:back">‹ Zurück</button></div>'
      + '<div class="o-dm"><span class="o-kick">' + esc(kickOf(a).toUpperCase()) + '</span><h1 class="o-h1" style="font-size:' + (a.label.length > 22 ? 30 : a.label.length > 14 ? 36 : 42) + 'px">' + esc(a.label) + '</h1><span class="o-say l">' + ori(a.last ? 'ruhezustand' : 'winkt', 60) + '<span class="o-quote">' + quoteOf(a) + '</span></span>'
      + '<div class="o-btns" style="margin:8px 0 0;flex-wrap:wrap"><button type="button" class="o-go" data-act="launch" data-first="1"><i>A</i>' + (a.last ? (a.game ? 'Weiterspielen' : 'Öffnen') : 'Starten') + '</button>'
      + '<button type="button" class="o-btn" data-act="fav">' + (a.fav ? 'Favorit entfernen' : 'Als Favorit') + '</button>'
      + (a.game ? (n > 1 ? '<button type="button" class="o-btn" data-act="go:3.6">Emulator wählen</button>' : '') : '<button type="button" class="o-btn" data-act="and:app:' + esc(a.key) + '">App-Info</button>') + '</div></div>'
      + '<section class="o-facts"><span class="o-kick" style="padding-bottom:6px">ANGABEN</span>' + facts.map(function (f) { return '<div><b>' + f[0] + '</b><span>' + esc(f[1]) + '</span></div>'; }).join('') + '</section></div>');
  }
  function mxEmu() {
    var a = sel, g = ginfo(a), list = g.emus || [], now = emuNow(g), name = esc(g.sysFull || (a && a.sys) || 'dieses System');
    var rows = list.map(function (e, i) {
      var on = e === now;
      return '<button type="button" class="o-rowb' + (on ? ' on' : '') + '" data-act="emu:' + esc(e) + '" data-scroll="1"' + (on || (!now && i === 0) ? ' data-first="1"' : '') + '><b>' + esc(e) + '</b><span>' + (on ? 'Ausgewählt' : i === 0 ? 'Vorschlag' : '') + '</span></button>';
    }).join('');
    if (!list.length) rows = '<div class="o-empty">' + ori('ratlos', 110) + '<b>Kein Emulator</b>Für ' + name + ' ist kein passender Emulator installiert. Installier einen und öffne diese Seite noch einmal.</div>';
    var inner = '<div class="o-lh"><div style="display:flex;align-items:baseline;gap:12px"><h1 class="o-h2">Emulator</h1><span class="o-kick">' + name.toUpperCase() + '</span></div><span class="o-say"><span class="o-quote" style="font-size:21px">Gilt für alle Spiele dieses Systems.</span>' + ori(list.length ? 'denkt-nach' : 'ratlos', 54) + '</span></div><div class="o-list">' + rows + '</div>';
    if (TOP) return oriTop('', 'A Auswählen · B Zurück', inner);
    return '<div class="mx ori" style="width:620px;height:540px;display:flex;flex-direction:column">' + uHead('Emulator', name) + W_BOT + '<div class="u-body"><div class="o-list">' + rows + '</div></div></div>';
  }
  /* ---------- Systemseiten (7.x): WLAN, Bluetooth, Speicher in Ori statt in Android ---------- */
  var wlanZiel = '', sysMsg = '';
  function sysJ(w) { try { return JSON.parse(J.sysGet ? J.sysGet(w) : '{}'); } catch (e) { return {}; } }
  function sysDo(c, a, b) { var r = J.sysDo ? J.sysDo(c, a || '', b || '') : 'nein'; return r; }
  function gbs(n) { n = +n || 0; return n >= 1e9 ? (n / 1e9).toFixed(n >= 1e11 ? 0 : 1).replace('.', ',') + ' GB' : n >= 1e6 ? Math.round(n / 1e6) + ' MB' : n ? '< 1 MB' : '0 MB'; }
  function sysRow(label, right, act, first, extra) { return '<button type="button" class="o-rowb" data-act="' + act + '" data-scroll="1"' + (first ? ' data-first="1"' : '') + '><b>' + label + '</b><span>' + right + '</span></button>' + (extra || ''); }
  function sysPage(titel, kick, spruch, pose, rows, hint) {
    var inner = '<div class="o-lh"><div style="display:flex;align-items:baseline;gap:12px"><h1 class="o-h2">' + titel + '</h1><span class="o-kick">EINSTELLUNGEN › ' + kick + '</span></div><span class="o-say"><span class="o-quote" style="font-size:21px">' + spruch + '</span>' + ori(pose, 54) + '</span></div>'
      + (sysMsg ? '<div class="o-sysmsg">' + esc(sysMsg) + '</div>' : '') + '<div class="o-list o-scroll">' + rows + '</div>';
    return oriTop('einst', hint || 'A Auswählen · B Zurück · L1/R1 Bereich wechseln', inner);
  }
  function balken(n) { var h = ''; for (var i = 0; i < 4; i++) h += '<i style="display:inline-block;width:5px;margin-left:2px;border-radius:2px;height:' + (5 + i * 4) + 'px;background:' + (i < n + 1 ? '#1b2340' : '#d9cdbd') + '"></i>'; return '<span style="display:inline-flex;align-items:flex-end;height:18px;margin-left:8px">' + h + '</span>'; }
  function mxWlan() {
    var w = sysJ('wlan'), rows = sysRow('WLAN', w.an ? 'An' : 'Aus', 'sys:wlan:' + (w.an ? '0' : '1'), true);
    if (w.an) {
      if (w.verbunden) rows += sysRow(esc(w.verbunden), 'Verbunden', 'sys:wlanMenu:' + esc(w.verbunden));
      if (wlanZiel) rows += '<div class="o-rowb" style="gap:10px"><b style="flex:none">' + esc(wlanZiel) + '</b><input id="o-pw" type="password" placeholder="Passwort" style="flex:1;min-width:0;height:36px;border:1px solid #eadfce;border-radius:10px;padding:0 10px;font:inherit;background:#fff"><button type="button" class="o-go" data-act="sys:wlanPw" style="height:36px"><i>A</i>Verbinden</button></div>';
      var n = (w.netze || []).filter(function (x) { return !x.jetzt; });
      rows += n.map(function (x) { return sysRow(esc(x.name) + balken(x.balken), x.bekannt ? 'Gespeichert' : x.offen ? 'Offen' : 'Passwort', 'sys:wlanWahl:' + esc(x.name) + ':' + (x.bekannt || x.offen ? '1' : '0')); }).join('');
      if (!n.length) rows += '<div class="o-empty" style="min-height:120px">' + ori('denkt-nach', 70) + '<b>Suche Netzwerke …</b>' + (w.ort === false ? 'Für die Suche braucht Android den Standort. Ori schaltet ihn dafür ein.' : 'Dauert ein paar Sekunden.') + '</div>';
      rows += sysRow('Neu suchen', '', 'sys:wlanSuchen');
    }
    return sysPage('WLAN', 'NETZWERK', w.verbunden ? 'Drin. Läuft.' : w.an ? 'Wo bin ich denn hier?' : 'Funkstille.', w.verbunden ? 'fertig' : 'denkt-nach', rows);
  }
  function mxBt() {
    var b = sysJ('bt');
    if (b.da === false) return sysPage('Bluetooth', 'GERÄTE', 'Kein Bluetooth gefunden.', 'ratlos', '');
    var rows = sysRow('Bluetooth', b.an ? 'An' : 'Aus', 'sys:bt:' + (b.an ? '0' : '1'), true);
    var ART = { kopfhoerer: 'Kopfhörer', eingabe: 'Controller', telefon: 'Telefon', computer: 'Computer', geraet: 'Gerät' };
    if (b.an) {
      rows += (b.gekoppelt || []).map(function (d) { return sysRow(esc(d.name), (d.verbunden ? 'Verbunden · ' : 'Gekoppelt · ') + (ART[d.art] || 'Gerät'), 'sys:btMenu:' + d.adr + ':' + esc(d.name)); }).join('');
      rows += (b.gefunden || []).map(function (d) { return sysRow(esc(d.name), 'Koppeln · ' + (ART[d.art] || 'Gerät'), 'sys:btKoppeln:' + d.adr); }).join('');
      rows += sysRow(b.sucht ? 'Suche läuft …' : 'Neue Geräte suchen', b.sucht ? '' : 'Controller, Kopfhörer', 'sys:btSuchen');
    }
    if (b.fehlt) rows += '<div class="o-empty" style="min-height:100px">' + ori('ratlos', 60) + '<b>Kein Zugriff</b>Bluetooth-Rechte fehlen noch. Kommen mit dem nächsten OS-Update.</div>';
    return sysPage('Bluetooth', 'GERÄTE', b.an ? 'Wer will mitspielen?' : 'Alles still.', b.an ? 'sammelt' : 'ruhezustand', rows);
  }
  function mxSpeicher() {
    var sp = sysJ('speicher'), g = sp.gesamt || 0, f = sp.frei || 0, used = g - f;
    function bar(parts, total) { return '<div style="display:flex;height:14px;border-radius:7px;overflow:hidden;background:#eadfce;margin:6px 0 4px">' + parts.map(function (p) { return '<i style="width:' + Math.max(0, Math.min(100, p[0] / (total || 1) * 100)).toFixed(1) + '%;background:' + p[1] + '"></i>'; }).join('') + '</div>'; }
    var sonst = Math.max(0, used - (sp.medien || 0) - (sp.downloads || 0) - (sp.spieleIntern || 0));
    var rows = '<div class="o-rowb" style="flex-direction:column;align-items:stretch;padding:12px 14px;min-height:0"><div style="display:flex;justify-content:space-between"><b>Interner Speicher</b><span>' + gbs(f) + ' frei von ' + gbs(g) + '</span></div>'
      + bar([[sp.spieleIntern || 0, '#c2410c'], [sp.medien || 0, '#8a72e0'], [sp.downloads || 0, '#e8b84a'], [sonst, '#4d556e']], g)
      + '<span style="font-size:12px;color:#4d556e">Spiele ' + gbs(sp.spieleIntern) + ' · Bilder und Videos ' + gbs(sp.medien) + ' · Downloads ' + gbs(sp.downloads) + ' · Apps und System ' + gbs(sonst) + '</span></div>';
    rows += (sp.karten || []).map(function (k) { var u = k.gesamt - k.frei; return '<div class="o-rowb" style="flex-direction:column;align-items:stretch;padding:12px 14px;min-height:0"><div style="display:flex;justify-content:space-between"><b>' + esc(k.name || 'microSD') + '</b><span>' + gbs(k.frei) + ' frei von ' + gbs(k.gesamt) + '</span></div>' + bar([[k.spiele || 0, '#c2410c'], [Math.max(0, u - (k.spiele || 0)), '#4d556e']], k.gesamt) + '<span style="font-size:12px;color:#4d556e">Spiele ' + gbs(k.spiele) + ' · Anderes ' + gbs(Math.max(0, u - (k.spiele || 0))) + '</span></div>'; }).join('');
    rows += sysRow('Spiele-Ordner neu durchsuchen', st.scanning ? 'Sucht …' : '', 'rescan', true) + sysRow('Apps verwalten', '', 'go:3.7');
    return sysPage('Speicher', 'PLATZ', f / (g || 1) < 0.1 ? 'Wird eng. Ehrlich.' : 'Platz ist da. Noch.', f / (g || 1) < 0.1 ? 'ratlos' : 'sortiert', rows);
  }
  var EMUS = {
    nds: ['Nintendo DS', 'WatermelonDS', 'https://github.com/SapphireRhodonite/WatermelonDS', 'Nutzt beide Bildschirme wie ein echter DS.'],
    n3ds: ['Nintendo 3DS', 'Azahar (Dual-Screen)', 'https://github.com/SapphireRhodonite/azahar', 'Oberer und unterer Bildschirm wie beim 3DS.'],
    switch: ['Nintendo Switch', 'Eden', '', 'Für Eden gibt es gerade keinen sicheren Link, den Ori kennt. Hol ihn über den Browser von der offiziellen Seite.'],
    wiiu: ['Wii U', 'Cemu', 'https://github.com/SSimco/Cemu', 'Das Gamepad-Bild kommt nach unten.'],
    gc: ['GameCube und Wii', 'Dolphin', 'https://dolphin-emu.org/download/', 'Dolphin gibt es auf der eigenen Seite, Ori öffnet sie.'],
    pc: ['PC-Spiele', 'GameNative', 'https://github.com/utkarshdalal/GameNative', 'Steam-Spiele direkt auf dem Thor.'],
    ps5: ['PS5', 'chiaki-ng', 'https://github.com/streetpea/chiaki-ng', 'Streamt deine PS5 aus dem Heimnetz aufs Thor.']
  };
  function mxEmuHol() {
    var id = pref('emuHol', 'n3ds'), e = EMUS[id] || EMUS.n3ds, obt = (apps || []).some(function (a) { return String(a.key).indexOf('dev.imranr.obtainium/') === 0; });
    var gh = e[2].indexOf('https://github.com/') === 0, rows = '';
    if (gh && obt) rows += sysRow('Über Obtainium installieren', 'Hält ' + e[1] + ' auch aktuell', 'emuhol:obt:' + id, true);
    if (e[2]) rows += sysRow(gh ? 'Download-Seite öffnen' : 'Seite öffnen', e[2].replace('https://', ''), 'emuhol:web:' + id, !(gh && obt));
    if (gh && !obt) rows += '<div class="o-sysmsg">Tipp: Mit Obtainium bleibt ' + e[1] + ' von selbst aktuell.</div>';
    rows += '<div class="o-rowb" style="flex-direction:column;align-items:flex-start;padding:12px 14px;min-height:0;gap:4px"><b>Danach einmal einrichten</b><span>' + e[1] + ' fragt beim ersten Start nach einem Ordner. Wähl die microSD-Karte und dann den Ordner „ROMs“. Danach startet Ori deine Spiele direkt.</span></div>';
    rows += sysRow('Zurück', '', 'go:back');
    return sysPage(e[1], e[0].toUpperCase(), 'Fehlt nur noch der Emulator.', 'sammelt', '<div class="o-rowb" style="min-height:0;padding:12px 14px"><span style="color:#1b2340;font-size:15px">' + esc(e[3]) + '</span></div>' + rows);
  }
  function emuHol(id) { id = { wii: 'gc', windows: 'pc', steam: 'pc', '3ds': 'n3ds' }[id] || id; J.setPref('emuHol', id); run('o.emu'); }
  window.plEmuFehlt = function (id) { if (TOP) emuHol(id); };
  /* ---------- Ori-Einrichtung (o.setup1 bis o.setup5): fünf kurze Schritte im Ori-Look ---------- */
  function setupPage(n, titel, spruch, pose, body, weiter) {
    var dots = ''; for (var i = 1; i <= 5; i++) dots += '<i style="display:inline-block;width:' + (i === n ? 22 : 8) + 'px;height:8px;border-radius:4px;margin-right:5px;background:' + (i <= n ? '#c2410c' : '#eadfce') + '"></i>';
    return '<div class="mx ori" style="width:960px;height:540px;position:relative;overflow:hidden">' + W_TOP + W_TOPB
      + '<div style="position:absolute;inset:0;box-sizing:border-box;padding:40px 56px 0;display:flex;flex-direction:row;gap:36px;align-items:flex-start;z-index:2">'
      + '<div style="width:230px;flex:none;display:flex;flex-direction:column;align-items:center;gap:12px;padding-top:40px">' + ori(pose, 170) + '<span>' + dots + '</span><span class="o-kick">SCHRITT ' + n + ' VON 5</span></div>'
      + '<div style="flex:1;min-width:0;display:flex;flex-direction:column;gap:12px"><h1 class="o-h1" style="font-size:40px;margin:0">' + titel + '</h1><span class="o-quote" style="font-size:24px">' + spruch + '</span>'
      + (sysMsg ? '<div class="o-sysmsg">' + esc(sysMsg) + '</div>' : '') + '<div class="o-list o-scroll" style="max-height:290px">' + body + '</div>'
      + '<div class="o-btns" style="margin-top:6px"><button type="button" class="o-go" data-act="setup:' + (n + 1) + '"' + (body.indexOf('data-first') < 0 ? ' data-first="1"' : '') + '><i>A</i>' + (weiter || 'Weiter') + '</button>' + (n > 1 ? '<button type="button" class="o-btn" data-act="setup:' + (n - 1) + '">Zurück</button>' : '') + '</div></div></div></div>';
  }
  function mxSetup() {
    var n = +(cur || '').slice(7) || 1;
    if (n === 1) return setupPage(1, 'Hallo, ich bin Ori.', 'Ich falte dir dein Thor zurecht.', 'aufwachen', sysRow('Was ich mache', 'Spiele finden, Emulatoren starten, alles an einem Ort', 'setup:2'), 'Los geht’s');
    if (n === 2) { var w = sysJ('wlan'); return setupPage(2, 'WLAN', w.verbunden ? 'Verbunden mit ' + esc(w.verbunden) + '.' : 'Für Cover und Updates.', w.verbunden ? 'fertig' : 'denkt-nach', sysRow('WLAN', w.an ? 'An' : 'Aus', 'sys:wlan:' + (w.an ? '0' : '1'), true) + (w.an ? (w.netze || []).filter(function (x) { return !x.jetzt; }).slice(0, 6).map(function (x) { return sysRow(esc(x.name) + balken(x.balken), x.bekannt ? 'Gespeichert' : x.offen ? 'Offen' : 'Passwort', 'sys:wlanWahl:' + esc(x.name) + ':' + (x.bekannt || x.offen ? '1' : '0')); }).join('') : '') + (wlanZiel ? '<div class="o-rowb" style="gap:10px"><b style="flex:none">' + esc(wlanZiel) + '</b><input id="o-pw" type="password" placeholder="Passwort" style="flex:1;min-width:0;height:36px;border:1px solid #eadfce;border-radius:10px;padding:0 10px;font:inherit;background:#fff"><button type="button" class="o-go" data-act="sys:wlanPw" style="height:36px"><i>A</i>Verbinden</button></div>' : ''), w.verbunden ? 'Weiter' : 'Später'); }
    if (n === 3) { var nG = (apps || []).filter(function (a) { return a.game; }).length; return setupPage(3, 'Deine Spiele', nG ? nG + ' Spiele gefunden.' : 'Leg sie in den Ordner „ROMs“.', nG ? 'sammelt' : 'sortiert', sysRow('Zugriff auf Spieleordner', st.files ? 'Erlaubt' : 'Jetzt erlauben', st.files ? 'rescan' : 'and:files', !st.files) + sysRow('Ordner neu durchsuchen', st.scanning ? 'Sucht …' : nG + ' Spiele', 'rescan') + '<div class="o-rowb" style="flex-direction:column;align-items:flex-start;min-height:0;padding:12px 14px;gap:4px"><b>So findet Ori deine Spiele</b><span>microSD-Karte → ROMs → ein Ordner je System, zum Beispiel „nds“, „n3ds“, „switch“, „wiiu“, „gc“.</span></div>'); }
    if (n === 4) {
      var have = function (pk) { return (apps || []).some(function (a) { return pk.some(function (p) { return String(a.key).indexOf(p + '/') === 0; }); }); };
      var L = [['nds', 'DS', ['me.magnum.melondualds', 'me.magnum.melonds']], ['n3ds', '3DS', ['org.azahar_emu.azahar', 'io.github.lime3ds.android', 'org.citra.citra_emu']], ['switch', 'Switch', ['dev.eden.eden_emulator']], ['wiiu', 'Wii U', ['info.cemu.cemu']], ['gc', 'GameCube / Wii', ['org.dolphinemu.dolphinemu']], ['pc', 'PC', ['app.gamenative']]];
      return setupPage(4, 'Emulatoren', 'Was fehlt, hol ich dir.', 'sammelt', L.map(function (e, i) { var h = have(e[2]); return sysRow(e[1], h ? 'Bereit · Ordner „ROMs“ einmal wählen' : 'Installieren', h ? 'setup:4' : 'emu:' + e[0], i === 0); }).join(''));
    }
    return setupPage(5, 'Fertig!', 'Viel Spaß. Ich bin da, wenn du mich brauchst.', 'triumph', sysRow('Tipp', 'L1/R1 wechselt die Bereiche, B geht zurück', 'setup:6') + sysRow('Tipp', 'Der AYN-Knopf zeigt unten die Leistung', 'setup:6'), 'Zum Start');
  }
  /* App-Info in Ori (o.app) statt der Android-Seite */
  function mxApp() {
    var k = pref('appInfo', ''), i = {}; try { i = JSON.parse(J.appInfo ? J.appInfo(k) : '{}'); } catch (e) { i = {}; }
    var d = function (t) { return t ? new Date(t).toLocaleDateString('de-DE', { day: 'numeric', month: 'long', year: 'numeric' }) : '–'; };
    var rows = sysRow('Öffnen', '', 'launch:' + esc(k), true)
      + sysRow('Beenden', i.laeuft ? 'Läuft gerade' : 'Falls sie hängt', 'appdo:beenden')
      + (i.system ? '' : sysRow('Deinstallieren', 'Android fragt noch einmal nach', 'appdo:entfernen'))
      + '<div class="o-rowb" style="flex-direction:column;align-items:stretch;min-height:0;padding:12px 14px;gap:6px">'
      + [['Version', i.version || '–'], ['Größe', mb(i.groesse)], ['Installiert', d(i.installiert)], ['Aktualisiert', d(i.aktualisiert)], ['Paket', i.pkg || '–']].map(function (r) { return '<div style="display:flex;justify-content:space-between;gap:12px"><b style="font-weight:600">' + r[0] + '</b><span>' + esc(r[1]) + '</span></div>'; }).join('') + '</div>';
    return sysPage(esc(i.name || 'App'), 'APP', i.laeuft ? 'Läuft noch im Hintergrund.' : 'Alles, was man wissen muss.', 'liest', rows);
  }
  window.plBotChip = function (on) {
    var c = document.getElementById('o-botchip');
    if (!c) { c = document.createElement('div'); c.id = 'o-botchip'; c.innerHTML = '<i>R3</i> Controller steuert unten · rechten Stick drücken für oben'; document.body.appendChild(c); }
    c.className = on ? 'zeig' : '';
  };
  window.plMit = function () { if (/Mitteilungen/.test(cur || '')) show(cur); };
  window.plSys = function () { if (/^o\./.test(cur || '')) { setKeep = foc ? foc.dataset.act || '' : ''; show(cur); } };
  function sysAct(v) {
    var p = v.split(':'), c = p[0];
    sysMsg = '';
    if (c === 'aleiste') { J.setPref('aleiste', p[1]); var ar = J.bruecke ? J.bruecke(p[1] === '1' ? 'sb none' : 'sb statusbar-expansion') : 'nein'; snd(p[1] === '1' ? 'an' : 'aus'); if (ar !== 'ok') note('Kommt mit dem nächsten OS-Update.'); setKeep = foc ? foc.dataset.id || '' : ''; J.sync('reload'); return; }
    if (c === 'layout') { J.setPref('layout', p[1]); var lr = J.bruecke ? J.bruecke('layout ' + p[1].toLowerCase()) : 'nein'; snd('ok'); note(lr === 'ok' ? 'Tastenlayout ' + p[1] + ': gilt nach dem nächsten Neustart.' : 'Kommt mit dem nächsten OS-Update.'); setKeep = foc ? foc.dataset.id || '' : ''; J.sync('reload'); return; }
    if (c === 'hz') { snd('ok'); if (sysDo('hz', p[1].split(' ')[0]) !== 'ok') sysMsg = 'Ließ sich nicht umstellen.'; setKeep = foc ? foc.dataset.id || '' : ''; J.sync('reload'); return; }
    if (c === 'wlan' || c === 'bt' || c === 'flug') { var r = sysDo(c, p[1]); if (r !== 'ok') sysMsg = 'Das darf Ori noch nicht selbst. Kommt mit dem nächsten OS-Update.'; snd(p[1] === '1' ? 'an' : 'aus'); if (c === 'wlan' && p[1] === '1') setTimeout(function () { sysDo('wlanSuchen'); }, 1500); }
    else if (c === 'wlanSuchen' || c === 'btSuchen') { snd('ok'); if (sysDo(c) !== 'ok') sysMsg = 'Suche ließ sich nicht starten.'; }
    else if (c === 'wlanWahl') { if (p[2] === '1') { snd('ok'); sysMsg = sysDo('wlanVerbinden', p[1]) === 'ok' ? 'Verbinde mit ' + p[1] + ' …' : 'Verbinden ging nicht.'; wlanZiel = ''; } else { wlanZiel = p[1]; snd('ok'); show(cur); setTimeout(function () { var i = document.getElementById('o-pw'); if (i) i.focus(); }, 80); return; } }
    else if (c === 'wlanPw') { var i = document.getElementById('o-pw'), pw = i ? i.value : ''; if (pw.length < 8) { sysMsg = 'Das Passwort hat mindestens 8 Zeichen.'; snd('fehler'); } else { snd('ok'); sysMsg = sysDo('wlanVerbinden', wlanZiel, pw) === 'ok' ? 'Verbinde mit ' + wlanZiel + ' …' : 'Verbinden ging nicht.'; wlanZiel = ''; } }
    else if (c === 'wlanMenu') { if (confirmTwice('vergessen:' + p[1])) { sysDo('wlanVergessen', p[1]); sysMsg = p[1] + ' vergessen.'; snd('aus'); } else { sysMsg = 'Nochmal A drücken, um ' + p[1] + ' zu vergessen.'; snd('taste'); } }
    else if (c === 'btKoppeln') { snd('ok'); sysMsg = sysDo('btKoppeln', p[1]) === 'ok' ? 'Koppeln läuft – bestätige am Gerät, falls es fragt.' : 'Koppeln ging nicht.'; }
    else if (c === 'btMenu') { if (confirmTwice('bt:' + p[1])) { sysDo('btEntkoppeln', p[1]); sysMsg = p.slice(2).join(':') + ' entkoppelt.'; snd('aus'); } else { sysMsg = 'Nochmal A drücken, um ' + p.slice(2).join(':') + ' zu entkoppeln.'; snd('taste'); } }
    setKeep = foc ? foc.dataset.act || '' : ''; show(cur);
  }
  var lastConfirm = '', lastConfirmAt = 0;
  function confirmTwice(id) { var ok = lastConfirm === id && Date.now() - lastConfirmAt < 4000; lastConfirm = ok ? '' : id; lastConfirmAt = Date.now(); return ok; }
  /* ---------- Einstellungen (5.x) nach der Vorlage "Ori OS": neun Bereiche, fünf Zeilenarten ---------- */
  var SETC = [['bildschirme', 'Bildschirme'], ['netzwerk', 'WLAN & Netzwerk'], ['controller', 'Controller'], ['rgb', 'RGB-Licht'], ['ton', 'Ton'], ['leistung', 'Leistung & Lüfter'], ['speicher', 'Speicher'], ['emulatoren', 'Emulatoren'], ['system', 'System & Updates']];
  var setKeep = '';
  function mxSet() {
    var seite = pref('einst', 'bildschirme'); if (!SETC.some(function (c) { return c[0] === seite; })) seite = 'bildschirme';
    function g(id, def) { var v = pref('e:' + seite + ':' + id, null); return v === null ? def : v; }
    /* wirkt = true: die Einstellung tut schon etwas. Sonst merkt Ori sie sich nur, bis das eigene OS sie umsetzen kann. */
    function V(label, value, sub, act) { return { k: 'v', label: label, value: value, sub: sub || '', act: act || '', real: !!act }; }
    function T(id, label, def, sub, real) { var on = real ? real.on : g(id, def ? '1' : '0') === '1'; return { k: 't', id: id, label: label, sub: sub || '', on: on, act: real ? real.act : 'eset:' + id + ':' + (on ? '0' : '1'), real: !!real }; }
    function S(id, label, val, real) { return { k: 's', id: id, label: label, val: real ? real.val : +g(id, val), hs: real ? real.hs : '', real: !!real }; }
    function C(id, label, names, def, real) { var c = real ? real.cur : g(id, def); return { k: 'c', id: id, label: label, real: !!real, opts: names.map(function (n) { return { name: n, on: n === c, act: real ? real.act + n : 'eset:' + id + ':' + n }; }) }; }
    function W(id, label, cols, def, real) { var c = real ? real.cur : g(id, def); return { k: 'w', id: id, label: label, real: !!real, opts: cols.map(function (x) { return { name: x[0], color: x[1], on: (real ? x[1] : x[0]) === c, act: real ? real.act + x[1] : 'eset:' + id + ':' + x[0] }; }) }; }
    var b = st.brightness || 0, upd = st.upd || {};
    var P = {
      bildschirme: ['Bildschirme', 'Zwei Bildschirme. Doppelt so viel zu putzen.', [S('oben', 'Helligkeit oben', 70, { val: b, hs: 'b' }), S('unten', 'Helligkeit unten', 55, { val: b, hs: 'b' }), C('hz', 'Bildwiederholrate', ['60 Hz', '120 Hz'], '', { cur: (st.hz || 120) + ' Hz', act: 'sys:hz:' }), C('farbe', 'Farbmodus', ['Natürlich', 'Kräftig', 'Warm'], 'Kräftig'), C('zweit', 'Unterer Bildschirm', ['Start', 'Bedienfeld'], '', { cur: pref('unten', 'start') === 'feld' ? 'Bedienfeld' : 'Start', act: 'untenw:' }), T('still', 'Bewegungen reduzieren', false, 'Wellen, Kacheln und Ori halten still', { on: pref('sw:Bewegungen reduzieren', '0') === '1', act: 'sw:Bewegungen reduzieren' }), T('nacht', 'Nachtlicht', false)]],
      netzwerk: ['WLAN & Netzwerk', 'Ohne WLAN bin ich auch nur ein Gameboy.', [T('wlan', 'WLAN', true, '', { on: !!st.wifi, act: 'sys:wlan:' + (st.wifi ? '0' : '1') }), V('Netzwerke', st.wifi ? 'Verbunden' : 'Nicht verbunden', '', 'go:o.wlan'), T('flug', 'Flugmodus', false, '', { on: !!st.airplane, act: 'sys:flug:' + (st.airplane ? '0' : '1') }), T('hotspot', 'Hotspot', false), V('Streamen im Heimnetz', 'Noch nicht eingerichtet', 'PS5 und PC aufs Thor holen'), V('VPN', 'Aus')]],
      controller: ['Controller & Bluetooth', 'Die Tasten sind unschuldig. Meistens.', [T('bt', 'Bluetooth', true, '', { on: !!st.bt, act: 'sys:bt:' + (st.bt ? '0' : '1') }), V('Geräte', 'Koppeln und verwalten', '', 'go:o.bt'), C('layout', 'Tastenlayout', ['Nintendo', 'Xbox'], '', { cur: pref('layout', 'Xbox'), act: 'sys:layout:' }), T('trigger', 'Trigger tauschen', false, 'L2 und R2 vertauschen'), S('tot', 'Stick-Totzone', 10), S('vib', 'Vibration', 60), T('gyro', 'Bewegungssteuerung', true), V('Externer Controller', 'Koppeln', '', 'go:o.bt')]],
      rgb: ['RGB-Licht', 'Bunt macht schneller. Angeblich.', [T('an', 'Beleuchtung', true, 'Leuchtringe an den Sticks'), C('effekt', 'Effekt', ['Fest', 'Atmen', 'Regenbogen', 'Welle'], 'Atmen'), W('farbe', 'Farbe', [['Orange', '#ff8a4c'], ['Pink', '#e2609a'], ['Lila', '#8a72e0'], ['Blau', '#3d5fb5'], ['Grün', '#4c9c6c'], ['Gold', '#e8b84a'], ['Weiß', '#ffffff']], 'Orange'), S('hell', 'Helligkeit', 60), S('tempo', 'Tempo', 40), T('spiel', 'Farbe vom Spiel übernehmen', true, 'Sticks leuchten in der Farbe des Spiels'), T('laden', 'Beim Laden pulsieren', false)]],
      ton: ['Ton', 'Lauter geht. Aber muss das sein?', [S('laut', 'Lautstärke', 45, { val: st.volume || 0, hs: 'v' }), V('Ausgabe', st.kopfhoerer ? 'Kopfhörer' : 'Lautsprecher'), T('tasten', 'Tastentöne', true, '', { on: pref('sw:Tastentöne', '1') !== '0', act: 'sw:Tastentöne' }), C('klang', 'Systemklänge', ['Papier', 'Sanft', 'Aus'], 'Papier'), T('mikro', 'Mikrofon', false), V('Kopfhörer', 'Koppeln', '', 'go:o.bt')]],
      leistung: ['Leistung & Lüfter', 'Turbo klingt gut. Der Akku sieht das anders.', [C('profil', 'Leistungsprofil', ['Sparsam', 'Ausgewogen', 'Leistung', 'Turbo', 'Eigene'], '', { cur: perfNow().profil || 'Ausgewogen', act: 'prof:' })].concat(perfNow().profil === 'Eigene' ? [S('k1', 'Kleine Kerne (bis 2,0 GHz)', 100), S('k2', 'Mittlere Kerne (bis 2,8 GHz)', 100), S('k3', 'Großer Kern (bis 3,2 GHz)', 100), S('gpu', 'Grafik (bis 680 MHz)', 100)] : []).concat([C('luefter', 'Lüfter', ['Leise', 'Automatisch', 'Stark'], '', { cur: perfNow().fanStufe || 'Automatisch', act: 'fan:' }), T('anzeige', 'Leistungsanzeige', true, 'FPS, Temperatur, Watt und Akku im Spiel'), V('Position der Anzeige', 'Oben links'), V('Bildrate begrenzen', '60 FPS'), T('merken', 'Profil je Spiel merken', true), T('schonen', 'Akku schonen', true, 'Nur bis 80 % laden')])],
      speicher: ['Speicher', 'Platz ist da. Noch.', [V('Interner Speicher', (st.free || '–') + ' frei von ' + (st.total || '–'), '', 'go:o.speicher'), V('Zugriff auf Spieleordner', st.files ? 'Erlaubt' : 'Fehlt noch', '', 'and:files'), C('ziel', 'Neue Spiele speichern auf', ['Intern', 'microSD'], 'microSD'), T('sichern', 'Speicherstände sichern', true, 'Kopie auf die microSD-Karte'), V('Spiele-Ordner', (st.games || 0) + ' Spiele gefunden', 'Ordner „ROMs“ mit einem Unterordner je System', 'rescan'), V('Apps verwalten', '', '', 'and:apps')]],
      emulatoren: ['Emulatoren', 'Alte Spiele, neues Papier.', [V('Emulator je System', 'Auf der Spielseite wählen', 'Spiel öffnen, Details, Emulator wählen'), C('skala', 'Auflösung hochrechnen', ['Original', '2×', '3×'], '2×'), C('ds2', 'DS: zweiter Bildschirm', ['Unten', 'Nebeneinander'], 'Unten'), T('cover', 'Cover automatisch laden', true, 'Aus dem offenen libretro-Archiv', { on: pref('covers', '1') !== '0', act: 'pset:covers' }), V('Spiele-Ordner durchsuchen', st.scanning ? 'Sucht …' : 'Jetzt suchen', '', 'rescan')]],
      system: ['System & Updates', upd.available ? 'Da liegt was Neues für dich.' : 'Alles aktuell. Ich hab nachgesehen.', [V('Version', 'Ori ' + (st.version || '')), V('Updates', upd.available ? 'Update verfügbar' : 'Jetzt suchen', '', upd.available ? 'go:6.2' : 'go:6.1'), V('Gerät', (st.model || '') + ' · ' + (st.android || '')), W('akzent', 'Akzentfarbe', [['Orange', '#c2410c'], ['Pink', '#b8327a'], ['Lila', '#6a4fc7'], ['Blau', '#2b4a8f'], ['Grün', '#2f7a4d'], ['Gold', '#9a6a12'], ['Tinte', '#141b31']], '', { cur: pref('acc', ACC0), act: 'acc:' }), C('design', 'Design', ['Hell', 'Dunkel', 'Automatisch'], 'Hell'), T('aleiste', 'Android-Leiste von oben', false, 'Aus: nur Ori, kein Android-Menü beim Wischen', { on: pref('aleiste', '0') === '1', act: 'sys:aleiste:' + (pref('aleiste', '0') === '1' ? '0' : '1') }), V('Einrichtung erneut starten', 'Fünf kurze Schritte', '', 'go:o.setup1'), V('Datum, Sprache und mehr', 'Android-Einstellungen', '', 'and:settings')]]
    };
    var p = P[seite], any = false, n = 0;
    /* Beim Neuzeichnen bleibt der Rahmen dort, wo er gerade steht, statt nach oben zu springen */
    if (foc && foc.dataset && foc.dataset.id && root && root.contains(foc) && (foc.dataset.id.indexOf('cat:') === 0 || foc.dataset.id.indexOf(seite + ':') === 0)) setKeep = foc.dataset.id;
    if (setKeep && setKeep.indexOf('cat:') !== 0 && setKeep.indexOf(seite + ':') !== 0) setKeep = 'cat:' + seite;
    function first(id) { n++; return (setKeep ? setKeep === id : n === 1) ? ' data-first="1"' : ''; }
    var rows = p[2].map(function (r, i) {
      var id = seite + ':' + (r.id || r.label), dot = '', lab = '<span class="o-sl">' + dot + '<span><b>' + r.label + '</b>' + (r.sub ? '<small>' + r.sub + '</small>' : '') + '</span></span>';
      if (!r.real) any = true;
      if (r.k === 'v') return '<button type="button" class="o-sr" data-scroll="box"' + (r.real ? '' : ' data-later="1"') + ' data-id="' + id + '"' + first(id) + ' data-act="' + (r.act || 'go:!Das merkt sich Ori noch nicht. Kommt mit dem eigenen OS.') + '">' + lab + '<span class="o-sv">' + esc(r.value) + (r.act ? '<svg aria-hidden="true" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M9 5l7 7-7 7"/></svg>' : '') + '</span></button>';
      if (r.k === 't') return '<button type="button" class="o-sr" data-scroll="box"' + (r.real ? '' : ' data-later="1"') + ' data-id="' + id + '"' + first(id) + ' data-act="' + r.act + '" aria-pressed="' + r.on + '">' + lab + '<span class="o-sw' + (r.on ? ' on' : '') + '"><i></i></span></button>';
      if (r.k === 's') return '<button type="button" class="o-sr" data-scroll="box"' + (r.real ? '' : ' data-later="1"') + ' data-id="' + id + '"' + first(id) + ' data-sl="' + (r.hs || 'e:' + seite + ':' + r.id) + '" data-v="' + r.val + '">' + lab + '<span class="o-tr"><i style="width:' + r.val + '%"></i></span><span class="o-sn">' + r.val + '</span></button>';
      if (r.k === 'c') return '<div class="o-sr"' + (r.real ? '' : ' data-later="1"') + '>' + lab + '<span class="o-seg">' + r.opts.map(function (o) { var oid = id + ':' + o.name; return '<button type="button" data-scroll="box" data-id="' + oid + '"' + ((setKeep ? setKeep === oid : false) ? ' data-first="1"' : '') + ' class="' + (o.on ? 'on' : '') + '" data-act="' + o.act + '">' + o.name + '</button>'; }).join('') + '</span></div>';
      return '<div class="o-sr"' + (r.real ? '' : ' data-later="1"') + '>' + lab + '<span class="o-sws">' + r.opts.map(function (o) { var oid = id + ':' + o.name; return '<button type="button" data-scroll="box" data-id="' + oid + '"' + ((setKeep ? setKeep === oid : false) ? ' data-first="1"' : '') + ' class="' + (o.on ? 'on' : '') + '" aria-label="' + o.name + '" data-act="' + o.act + '"><i style="background:' + o.color + '"></i></button>'; }).join('') + '</span></div>';
    }).join('');
    return oriTop('einst', 'A Ändern · ← → Regler · B Zurück',
      '<div class="o-lh"><div style="display:flex;align-items:baseline;gap:12px"><h1 class="o-h2">' + p[0] + '</h1><span class="o-kick">EINSTELLUNGEN</span></div><span class="o-say"><span class="o-quote" style="font-size:21px">' + p[1] + '</span>' + ori(seite === 'system' && !upd.available ? 'fertig' : seite === 'leistung' ? 'sport' : seite === 'emulatoren' ? 'sortiert' : seite === 'netzwerk' && !st.wifi ? 'ratlos' : 'ruhezustand-2', 54) + '</span></div>'
      + '<div class="o-set"><nav class="o-cats o-scroll" aria-label="Einstellungsbereiche">' + SETC.map(function (c) { return '<button type="button" data-scroll="box" class="o-cat' + (c[0] === seite ? ' on' : '') + '" data-id="cat:' + c[0] + '"' + (setKeep === 'cat:' + c[0] ? ' data-first="1"' : '') + ' data-act="ecat:' + c[0] + '">' + c[1] + '</button>'; }).join('') + '</nav>'
      + '<div class="o-scol"><section class="o-sec o-scroll">' + rows + '</section></div></div>');
  }
  function mxSetBind() {
    var seite = pref('einst', 'bildschirme');
    qa('[data-sl]', root).forEach(function (el) {
      el._adj = function (d) {
        var v = Math.max(0, Math.min(100, (+el.dataset.v || 0) + d)), k = el.dataset.sl; el.dataset.v = v;
        el.querySelector('.o-tr i').style.width = v + '%'; el.querySelector('.o-sn').textContent = v;
        if (k === 'b') { J.setBrightness(v); qa('[data-sl="b"]', root).forEach(function (o) { if (o !== el) { o.dataset.v = v; o.querySelector('.o-tr i').style.width = v + '%'; o.querySelector('.o-sn').textContent = v; } }); }
        else if (k === 'v') J.setVolume(v); else { J.setPref(k, String(v)); if (k === 'e:rgb:hell') applyRgb(); if (/^e:leistung:(k1|k2|k3|gpu)$/.test(k) && perfNow().profil === 'Eigene') J.profil('Eigene'); }
      };
      el._act = function () { el._adj(+el.dataset.v >= 100 ? -100 : 10); snd('taste'); };
    });
  }
  /* ---------- Die Ansichten aus dem Canvas "Ori OS" laufen direkt: Vorlage + Logik je Ansicht aus s/dc-<name>.html ---------- */
  var DCC = {}, DCS = {};
  var DC_ALL = { Boot: 'Hochfahren', Sperr: 'Sperrbildschirm', Einrichtung: 'Ersteinrichtung', Emulatoren: 'Emulatoren', Medien: 'Medien', Schnell: 'Schnelleinstellungen', Schnellmenue: 'Im Spiel: Schnellmenü', Ausschalten: 'Ausschalten', Update: 'Systemupdate',
    Bedienfeld: 'Bedienfeld (Vorlage)', BedienfeldSpiel: 'Bedienfeld im Spiel', Tastatur: 'Tastatur', TastaturZahlen: 'Tastatur: Zahlen', Mitteilungen: 'Mitteilungen', UntenSperr: 'Sperrbildschirm unten', UntenStart: 'Start-Begleiter (Vorlage)', UntenLeistung: 'Leistungsmonitor', UntenRgb: 'RGB-Licht', UntenTasten: 'Tastenbelegung', UntenTouchpad: 'Touchpad', UntenDS: 'Zweiter Spielbildschirm (DS)', UntenAkku: 'Akku & Laden', UntenFoto: 'Bildschirmfoto gemacht' };
  var DC_TOP = ['Boot', 'Sperr', 'Einrichtung', 'Emulatoren', 'Medien', 'Schnell', 'Schnellmenue', 'Ausschalten', 'Update'];
  var DC_PAIR = { Sperr: 'UntenSperr', Boot: 'UntenSperr', Einrichtung: 'Tastatur', Schnellmenue: 'BedienfeldSpiel', Medien: 'UntenTouchpad', Update: 'UntenAkku', Ausschalten: 'UntenAkku' };
  var DC_CAT = { Einst: 'bildschirme', EinstNetzwerk: 'netzwerk', EinstController: 'controller', EinstRgb: 'rgb', EinstTon: 'ton', EinstLeistung: 'leistung', EinstSpeicher: 'speicher', EinstEmulatoren: 'emulatoren', EinstSystem: 'system' };
  var DC_DOCK = { start: 'start', bibliothek: 'bib', emulatoren: 'emu', apps: 'apps', medien: 'med', einstellungen: 'einst', aus: 'aus' };
  function dcKey(name) { return DC_TOP.indexOf(name) >= 0 ? 'dc:' + name + ':' + (DC_PAIR[name] || '') : 'dc:' + (cur.indexOf('dc:') === 0 ? cur.split(':')[1] : '@' + cur) + ':' + name; }
  function dcLoad(name) {
    if (DCC[name] !== undefined) return DCC[name];
    var raw = J.html('dc-' + name.toLowerCase()); if (!raw) return (DCC[name] = null);
    try {
      var a = raw.split('\n<!--dc-script-->\n'), b = a[1].split('\n<!--dc-props-->\n'), tpl = document.createElement('template');
      tpl.innerHTML = a[0];
      return (DCC[name] = { tpl: tpl.content, Cls: new Function('DCLogic', b[0] + '\nreturn Component;')(function DCLogic() {}), props: JSON.parse(b[1] || '{}') });
    } catch (e) { J.log('Ansicht ' + name + ': ' + e); return (DCC[name] = null); }
  }
  function dcGet(scope, expr) {
    expr = expr.replace(/^\s*\{\{\s*|\s*\}\}\s*$/g, '').trim();
    if (expr === 'true') return true; if (expr === 'false') return false; if (/^-?\d+(\.\d+)?$/.test(expr)) return +expr;
    var v = scope, p = expr.split('.');
    for (var i = 0; i < p.length; i++) { if (v === null || v === undefined) return undefined; v = v[p[i]]; }
    return v;
  }
  function dcFill(t, scope) { return t.indexOf('{{') < 0 ? t : t.replace(/\{\{\s*([^}]+?)\s*\}\}/g, function (m, e) { var v = dcGet(scope, e); return v === null || v === undefined ? '' : v; }); }
  /* Die Beispielwerte aus dem Canvas für Uhrzeit, Datum und Akku werden durch die echten ersetzt */
  function dcLive(t) {
    if (t.indexOf('12:30') < 0 && t.indexOf('82 %') < 0 && t.indexOf('25. N') < 0) return t;
    var d = new Date(), tag = d.toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long' }), kurz = d.toLocaleDateString('de-DE', { weekday: 'short', day: 'numeric', month: 'long' });
    return t.replace(/12:30/g, st.time || '12:30').replace(/82 %/g, parseInt(st.battery, 10) + ' %').replace(/MITTWOCH, 25\. NOVEMBER/g, tag.toUpperCase()).replace(/Mittwoch, 25\. November/g, tag).replace(/Mi\., 25\. November/g, kurz);
  }
  function dcGo(name) {
    name = name.replace(/\.dc\.html$/, '');
    if (name.indexOf('mit:') === 0) { var mv2 = name.slice(4); if (mv2 === 'alle') { J.mitteilungWeg && J.mitteilungWeg('*'); snd('aus'); } else if (mv2.indexOf('weg:') === 0) { J.mitteilungWeg && J.mitteilungWeg(decodeURIComponent(mv2.slice(4))); snd('aus'); } else if (mv2.indexOf('auf:') === 0) { var mo = J.mitteilungOeffnen ? J.mitteilungOeffnen(decodeURIComponent(mv2.slice(4))) : 'nein'; snd(mo === 'ok' ? 'ok' : 'taste'); if (mo !== 'ok') note('Diese Mitteilung lässt sich nicht öffnen.'); } setTimeout(function () { show(cur); }, 250); return; }
    if (name === 'spiel:beenden') { var rb = J.beenden ? J.beenden() : 'nein'; snd(rb === 'ok' ? 'aus' : 'fehler'); note(rb === 'ok' ? 'Spiel beendet.' : 'Ließ sich nicht beenden.'); setTimeout(function () { show(cur); }, 400); return; }
    if (name.indexOf('emu:auf:') === 0) { snd('start'); J.launch(name.slice(8)); return; }
    if (name.indexOf('emu:hol:') === 0) { emuHol(name.slice(8)); return; }
    if (name.indexOf('swap:') === 0) { snd('ok'); J.swap(name.slice(5)); return; }
    if (name.indexOf('med:') === 0) { var mv = name.slice(4); if (mv === 'fotos' || mv === 'clips') { J.setPref('medien', mv); snd('ok'); show(cur); } else if (mv.indexOf('zeig:') === 0) medZeig(mv.slice(5)); return; }
    if (name === 'lock:open') { if (TOP) unlock(); else J.lockOpen && J.lockOpen(); return; }
    if (name === 'lock:play') { var lg = lockGame; if (TOP) unlock(); else J.lockOpen && J.lockOpen(); if (lg) setTimeout(function () { J.launch(lg.key); }, 700); return; }
    if (name.indexOf('upd:') === 0) { snd('ok'); window.plUpdAct(name.slice(4)); return; }
    if (name.indexOf('power:') === 0) { snd('ok'); if ((J.power ? J.power(name.slice(6)) : 'nein') !== 'ok') note('Geht erst, wenn Ori das System ist.'); return; }
    var nat = { Main: 'home', Bibliothek: '3.1', Spiel: '3.5', Apps: '3.7' };
    if (nat[name]) { run(nat[name]); return; }
    /* Unten zurück zum echten Bedienfeld bzw. Start-Begleiter statt zur Vorlage aus dem Canvas */
    if (!TOP && (name === 'Bedienfeld' || name === 'UntenStart')) { J.setPref('unten', name === 'Bedienfeld' ? 'feld' : 'start'); snd('ok'); var tk = cur.indexOf('dc:') === 0 ? cur.split(':')[1] : '@' + cur; J.swap(tk && tk !== '@' ? 'dc:' + tk + ':' : 'home'); return; }
    if (DC_CAT[name]) { J.setPref('einst', DC_CAT[name]); setKeep = ''; run('5.1'); return; }
    if (!DC_ALL[name]) { note('Diese Ansicht gibt es noch nicht.'); return; }
    snd('ok'); var k = dcKey(name);
    if (cur.indexOf('dc:') === 0 && DC_TOP.indexOf(name) < 0) J.swap(k); else J.go(k);
  }
  function dcBuild(node, scope, out) {
    for (var ch = node.firstChild; ch; ch = ch.nextSibling) {
      if (ch.nodeType === 3) { out.appendChild(document.createTextNode(dcLive(dcFill(ch.nodeValue, scope)))); continue; }
      if (ch.nodeType !== 1) continue;
      var tag = ch.localName;
      if (tag === 'sc-for') { var list = dcGet(scope, ch.getAttribute('list') || '') || [], as = ch.getAttribute('as') || 'item'; for (var i = 0; i < list.length; i++) { var sc = Object.create(scope); sc[as] = list[i]; dcBuild(ch, sc, out); } continue; }
      if (tag === 'sc-if') { if (dcGet(scope, ch.getAttribute('value') || '')) dcBuild(ch, scope, out); continue; }
      if (tag === 'helmet') continue;
      if (tag === 'dc-import') {
        var nm = ch.getAttribute('name'), box = document.createElement('div');
        box.innerHTML = nm === 'Leiste' ? oriBar() : nm === 'Dock' ? oriDock(DC_DOCK[ch.getAttribute('active')] || '', dcFill(ch.getAttribute('hinweis') || '', scope)) : '';
        while (box.firstChild) out.appendChild(box.firstChild);
        continue;
      }
      var link = tag === 'a', el = link ? document.createElement('button') : document.createElementNS(ch.namespaceURI, ch.tagName.toLowerCase() === tag ? tag : ch.tagName);
      if (link) { el.type = 'button'; el.className = 'dc-a'; }
      for (var n = 0; n < ch.attributes.length; n++) {
        var at = ch.attributes[n], an = at.name, lo = an.toLowerCase();
        if (lo.indexOf('hint-') === 0) continue;
        if (lo === 'onclick') { (function (fn) { if (typeof fn === 'function') el._act = function () { snd('ok'); fn(); }; })(dcGet(scope, at.value)); continue; }
        if (link && lo === 'href') { (function (h) { if (h && !/^https?:/.test(h)) el._act = function () { dcGo(h); }; })(dcFill(at.value, scope)); continue; }
        try { el.setAttribute(lo === 'defaultvalue' ? 'value' : an, dcFill(at.value, scope)); } catch (e) { /* unbekanntes Attribut */ }
      }
      var into = el;
      if (link && /flex|grid/.test(el.style.display)) {
        into = document.createElement('span'); var es = el.style, is = into.style;
        ['flexDirection', 'justifyContent', 'alignItems', 'gap', 'flexWrap', 'padding', 'minHeight'].forEach(function (pn) { if (es[pn]) is[pn] = es[pn]; });
        is.display = es.display.replace('inline-', ''); is.boxSizing = 'border-box'; is.width = '100%'; is.height = '100%';
        es.padding = '0'; es.display = es.display.indexOf('inline') === 0 ? 'inline-block' : 'block'; el.appendChild(into);
      }
      dcBuild(ch, scope, into); out.appendChild(el);
    }
  }
  function dcView(name) {
    var d = dcLoad(name), W = DC_TOP.indexOf(name) >= 0 ? 960 : 620;
    if (!d) return [function () { return '<div class="mx ori" style="width:' + (TOP ? 960 : 620) + 'px;height:540px"><div class="o-empty" style="margin:60px">' + ori('ratlos', 120) + '<b>Diese Ansicht fehlt</b></div></div>'; }, null];
    return [function () { return '<div class="mx ori dc" style="width:' + W + 'px;height:540px"></div>'; }, function () {
      var inst = new d.Cls(); inst.props = d.props; inst.state = DCS[name] || {};
      inst.setState = function (s) {
        DCS[name] = Object.assign({}, inst.state, s);
        var i = items.indexOf(foc); show(cur); if (i >= 0 && items[i]) setFocus(items[i], true);
      };
      var vals; try { vals = inst.renderVals() || {}; } catch (e) { J.log('Ansicht ' + name + ': ' + e); vals = {}; }
      var frag = document.createDocumentFragment(); dcBuild(d.tpl, vals, frag);
      var inner = null; for (var c = frag.firstChild; c; c = c.nextSibling) if (c.nodeType === 1) { inner = c; break; }
      if (inner && /background:\s*(#f8e9dc|rgb\(248, 233, 220\))/i.test(inner.getAttribute('style') || '')) {
        inner.style.background = 'transparent'; root.insertAdjacentHTML('beforeend', W === 960 ? W_TOP + W_TOPB : W_BOT);
      }
      if (inner) {
        /* Wo die Vorlage den Status als Text schreibt ("Akku 82 % · WLAN"), stehen am Gerät die echten Symbole */
        qa('span,div', inner).forEach(function (el) { if (!el.children.length && /^(\d\d:\d\d · )?Akku \d+ %( · WLAN)?$/.test(el.textContent.trim())) { el.className = 'o-br'; el.innerHTML = oriStatus(); } });
        inner.style.position = 'relative'; root.appendChild(inner);
      }
    }];
  }
  function mixFor(key) {
    if (TOP && /^6\.[1-7]$/.test(key) && dcLoad('Update')) return dcView('Update');
    if (!TOP && /^6\.[1-7]$/.test(key)) return [mxPanel, mxPanelBind];
    if (key === 'acc') return [mxAcc, mxAccBind];
    if (key.indexOf('dc:') === 0) { var dk = key.split(':'), dn = TOP ? dk[1] : dk[2]; if (dn && dn[0] === '@') return mixFor(dn.slice(1)); return dn ? dcView(dn) : TOP ? [mxHome, mxHomeBind] : [mxPanel, mxPanelBind]; }
    if (/^5\./.test(key)) { var sc = { rgb: 'UntenRgb', leistung: 'UntenLeistung', controller: 'UntenTasten' }[pref('einst', '')]; return TOP ? [mxSet, mxSetBind] : sc ? dcView(sc) : [mxPanel, mxPanelBind]; }
    if (/^o\.setup[1-5]$/.test(key)) return TOP ? [mxSetup, null] : [mxPanel, mxPanelBind];
    if (/^o\.(wlan|bt|speicher|emu|app)$/.test(key)) return TOP ? [{ 'o.wlan': mxWlan, 'o.bt': mxBt, 'o.speicher': mxSpeicher, 'o.emu': mxEmuHol, 'o.app': mxApp }[key], null] : [mxPanel, mxPanelBind];
    if (key === '3.5') return [mxDet, null];
    if (key === '3.6') return [mxEmu, null];
    var home = /^2\.(1|2|4|5|6|7)$/.test(key), lib = key === '3.1' || key === '3.2' || key === '3.4' || key === '3.7' || (TOP && key === '3.3');
    if (TOP) return home ? [mxHome, mxHomeBind] : lib ? [mxLib, mxLibBind] : null;
    return home || lib || key === '2.3' ? [mxPanel, mxPanelBind] : null;
  }
  function doAct(a) {
    var i = a.indexOf(':'), k = i < 0 ? a : a.slice(0, i), v = i < 0 ? '' : a.slice(i + 1);
    if (k === 'launch') launch(v ? appByKey(v) : sel);
    else if (k === 'details') details();
    else if (k === 'fav') fav(sel);
    else if (k === 'dc') dcGo(v);
    else if (k === 'unten') { J.setPref('unten', v); snd('ok'); show(cur); }
    else if (k === 'ecat') { setKeep = 'cat:' + v; J.setPref('einst', v); snd('ok'); J.sync('reload'); }
    else if (k === 'eset') { var q = v.indexOf(':'), eid = v.slice(0, q), ev = v.slice(q + 1), es = pref('einst', 'bildschirme'); J.setPref('e:' + es + ':' + eid, ev); if (es === 'rgb') applyRgb(); setKeep = foc && foc.dataset.id ? (foc.parentNode.className === 'o-seg' || foc.parentNode.className === 'o-sws' ? es + ':' + eid + ':' + ev : foc.dataset.id) : ''; snd(ev === '0' ? 'aus' : 'an'); var later = foc && foc.closest && foc.closest('[data-later]'); show(cur); if (later && es !== 'rgb') note('Merkt sich Ori. Wirkt im eigenen OS.'); }
    else if (k === 'sw' || k === 'pset') { var pk = k === 'sw' ? 'sw:' + v : v, was = pref(pk, k === 'sw' && v === 'Bewegungen reduzieren' ? '0' : '1') !== '0'; J.setPref(pk, was ? '0' : '1'); setKeep = foc ? foc.dataset.id || '' : ''; snd(was ? 'aus' : 'an'); show(cur); J.sync(''); }
    else if (k === 'untenw') { J.setPref('unten', v === 'Bedienfeld' ? 'feld' : 'start'); setKeep = pref('einst', '') + ':zweit:' + v; snd('ok'); show(cur); J.sync('reload'); }
    else if (k === 'emu') { var gi = ginfo(sel); if (gi.sys) J.setPref('emu:' + gi.sys, v); snd('an'); J.sync(''); J.back(); }
    else if (k === 'home') { snd('ok'); J.go('home'); }
    else if (k === 'swap') { snd('ok'); if (cur[0] === '2') J.go(v); else J.swap(v); }
    else if (k === 'go') { sysMsg = ''; run(v); }
    else if (k === 'sys') sysAct(v);
    else if (k === 'appinfo') { J.setPref('appInfo', v); sysMsg = ''; run('o.app'); }
    else if (k === 'appdo') { var ak = pref('appInfo', ''), ar2 = J.appTun ? J.appTun(v, ak) : 'nein'; snd(ar2 === 'ok' ? 'ok' : 'fehler'); sysMsg = v === 'beenden' ? (ar2 === 'ok' ? 'Beendet.' : 'Ließ sich nicht beenden.') : ''; if (v === 'beenden') show(cur); }
    else if (k === 'setup') { var sn = +v; sysMsg = ''; if (sn >= 6) { J.setPref('setup2', '1'); snd('ok'); J.go('home'); } else { snd('ok'); if (sn === 2) sysDo('wlanSuchen'); J.swap('o.setup' + Math.max(1, sn)); } }
    else if (k === 'emu') emuHol(v);
    else if (k === 'prof') { if (window.plProfil) window.plProfil(v); else J.profil(v); snd('ok'); setKeep = foc ? foc.dataset.id || '' : ''; setTimeout(function () { show(cur); }, 300); }
    else if (k === 'fan') { if (window.plFan) window.plFan(v); snd('ok'); setKeep = foc ? foc.dataset.id || '' : ''; setTimeout(function () { show(cur); }, 300); }
    else if (k === 'emuhol') { var ep = v.split(':'), ee = EMUS[ep[1]]; if (ee) { var r = J.oeffne ? J.oeffne(ep[0] === 'obt' ? 'obtainium://add/' + ee[2] : ee[2]) : 'nein'; snd(r === 'ok' ? 'ok' : 'fehler'); if (r !== 'ok') note('Ließ sich nicht öffnen.'); } }
    else if (k === 'acc') { snd('ok'); setAcc(v); if (/^5\./.test(cur)) { var an = ''; qa('.o-sws button', root).forEach(function (bn) { if (bn.dataset.act === 'acc:' + v) an = bn.dataset.id; }); setKeep = an; show(cur); } }
    else if (k === 'sys') { J.setPref('sysf', v); snd('ok'); J.sync('reload'); }
    else if (k === 'rescan') { snd('ok'); J.rescan(); note('Suche läuft …'); }
    else if (k === 'and') run('@' + v);
    else if (k === 'sort') { J.setPref('sort', pref('sort', 'last') === 'last' ? 'name' : 'last'); snd('ok'); J.sync('reload'); }
  }

  /* ---------- Anzeigen ---------- */
  function show(key) {
    /* "dc:@3.1:UntenRgb": unten wechselt, oben bleibt die Ansicht 3.1 stehen, wie sie ist */
    if (TOP && key.indexOf('dc:@') === 0) { key = key.split(':')[1].slice(1); if (key === cur && root && root.firstChild) return; }
    var mix = mixFor(key), html = mix ? '' : J.html(key + '-' + side);
    if (!mix && !html) return;
    timers.forEach(clearTimeout); timers = []; idx = null;
    cur = key;
    try { st = JSON.parse(J.state()); apps = JSON.parse(J.apps()); } catch (e) { st = {}; apps = []; }
    sel = appByKey(pref('sel', '')) || homeList()[0] || null;
    applyAcc();
    html = mix ? mix[0]() : skin(html);
    stage.innerHTML = html; root = stage.firstElementChild; foc = null;
    if (mix) root.dataset.mix = '1';
    if (!mix && !/^1\.([1-6]|18)$/.test(key)) { root.classList.add('pl-inv'); if (pref('acc', ACC0) === ACC0) root.style.setProperty('--acc', '#f3713d'); }
    if (mix && mix[1]) mix[1]();
    tag();
    if (mix) {
      qa('[data-key]', root).forEach(function (el) { el._app = appByKey(el.dataset.key); });
    }
    if (pref('sw:Bewegungen reduzieren', '0') === '1') root.classList.add('pl-still');
    var n = NAV[key] || {};
    if (!mix && TOP && key[0] === '2') bindHome();
    if (!mix && TOP && key[0] === '3') bindGrid();
    if (n.bind) n.bind();
    if (key === '5.13') qa('div', root).filter(isRow).forEach(function (r) { if (labelOf(r) === 'Android') qa('div', r).forEach(function (d) { if (d.textContent === '[Version]') d.textContent = st.android || '–'; }); });
    if (TOP && key === '3.5') {
      var art = root.querySelector(':scope > svg');
      if (art && sel && sel.img) { var pic = document.createElement('img'); pic.src = img(sel, 'image'); pic.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;object-fit:cover'; root.replaceChild(pic, art); }
      else swapArt();
    }
    fill();
    if (key === '7.2') {
      if (!(st.upd && st.upd.available)) qa('div', root).filter(isRow).forEach(function (r) { if (labelOf(r) === 'Ori OS') r.parentNode.removeChild(r); });
      var left = qa('div', root).filter(isRow);
      if (TOP && !left.length) { var none = document.createElement('div'); none.style.cssText = 'position:absolute;right:44px;top:120px;width:500px;font:600 17px Outfit,sans-serif;color:rgba(255,255,255,.6)'; none.textContent = 'Keine Benachrichtigungen.'; root.appendChild(none); }
    }
    qa('[aria-label="Farbstreifen"] > div', root).forEach(function (d) { d.style.background = 'var(--acc)'; });
    var oldNote = document.getElementById('pl-note'); if (oldNote) oldNote.style.display = 'none';
    wall(); collect(); applyStates(); fit();
    if (!TOP) { bindTouch(); return; }
    items.forEach(function (el) { el.addEventListener('click', function () { setFocus(el, true); act(el); }); });
    if (n.enter) snd(n.enter);
    if (n.enter0) n.enter0();
    if (n.note) later(600, function () { note(typeof n.note === 'function' ? n.note() : n.note); });
    if (n.auto) later(n.auto[0], function () { var t = n.auto[1]; if (typeof t === 'function') t(); else if (t === 'back') J.back(); else J.swap(t); });

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

  window.plSnd = snd;
  window.plNote = function (t) { if (TOP) note(t); };
  window.plShow = function (key) { try { show(key); } catch (e) { J.log('show ' + key + ': ' + e + ' ' + (e.stack || '')); } };
  window.plSync = function (what) {
    if (!root) return;
    if (what === 'reload') { var keep = foc && foc._app ? foc._app.key : null; show(cur); if (keep) items.forEach(function (el) { if (el._app && el._app.key === keep) setFocus(el, true); }); return; }
    applyAcc();
    if (!TOP && root.dataset.mix && pref('sel', '') !== (sel ? sel.key : '')) { show(cur); return; }
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
    var P = {}, stack = [], now = null, qa0 = /acc=([^&]+)/.exec(location.search); if (qa0) P.acc = decodeURIComponent(qa0[1]);
    var A = ['Dateien', 'Kamera', 'Dolphin', 'RetroArch', 'Steam Link', 'YouTube', 'Einstellungen', 'Browser', 'Galerie', 'Musik', 'Uhr', 'Rechner'].map(function (l, i) { return { label: l, key: 'p' + i + '/c', fav: i === 2 || i === 3, last: i < 4 ? 1795000000000 - i * 86400000 : 0, img: false }; });
    [['Pokémon SoulSilver', 'nds', 'Nintendo DS', 1], ['Emerald Rogue', 'gba', 'Nintendo Game Boy Advance', 0], ['Wind Waker', 'gc', 'Nintendo GameCube', 0], ['Tears of the Kingdom', 'switch', 'Nintendo Switch', 1]].forEach(function (g, i) { if (/nogames/.test(location.search)) return; A.push({ label: g[0], key: 'g:/roms/' + g[1] + '/' + i, fav: i === 0, last: g[3] ? 1795400000000 + i : 0, img: false, cov: false, game: true, sys: g[2], sysid: g[1] }); });
    function to(k) { if (k === 'home') { stack = []; k = '2.2'; } now = k; window.plShow(k); if (window.parent !== window) window.parent.postMessage({ pl: k }, '*'); }
    window.addEventListener('message', function (e) { if (e.data && e.data.pl && e.data.pl !== now) { now = e.data.pl; window.plShow(now); } });
    window.addEventListener('keydown', function (e) {
      var m = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right', Enter: 'A', Escape: 'B', x: 'X', y: 'Y', q: 'L1', e: 'R1', s: 'START', Tab: 'SELECT', i: 'INDEX' }[e.key];
      if (m) { e.preventDefault(); window.plKey(m); }
    });
    setTimeout(function () { to((/s=([\d.a-c]+)/.exec(location.search) || [0, '2.2'])[1]); }, 0);
    return {
      html: function (n) { var x = new XMLHttpRequest(); x.open('GET', 's/' + n + '.html', false); x.send(); return x.status === 200 || x.status === 0 ? x.responseText : ''; },
      state: function () { var d = new Date(); return JSON.stringify({ time: d.toTimeString().slice(0, 5), date: d.toLocaleDateString('de-DE'), weekday: 'Dienstag', battery: 87, version: '0.4.0 · Build 6', tz: 'Europe/Berlin', model: 'AYN Thor', region: 'DE', used: '41 GB', total: '256 GB', free: '215 GB', temp: '31 °C', wifi: true, bt: false, airplane: false, brightness: 65, volume: 40, upd: {}, files: true, games: A.filter(function (a) { return a.game; }).length }); },
      apps: function () { return JSON.stringify(A); }, rescan: function () {}, pref: function (k) { return P[k]; }, setPref: function (k, v) { P[k] = v; },
      go: function (k) { if (now && k !== 'home') stack.push(now); to(k); }, swap: function (k) { to(k); }, back: function () { to(stack.pop() || 'home'); },
      gameInfo: function (k) { var sy = k.split('/')[2]; return JSON.stringify({ sys: sy, sysFull: { nds: 'Nintendo DS', gba: 'Nintendo Game Boy Advance', gc: 'Nintendo GameCube', switch: 'Nintendo Switch' }[sy] || sy, file: 'spiel.' + sy, folder: '/storage/emulated/0/ROMs/' + sy, size: 268435456, chosen: P['emu:' + sy] || '', emus: sy === 'gc' ? ['Dolphin'] : sy === 'switch' ? [] : ['melonDS', 'DraStic', 'RetroArch (melonDS DS)'] }); },
      launch: function (k) { note('Starte ' + k); }, fav: function (k) { A.forEach(function (a) { if (a.key === k) a.fav = !a.fav; }); },
      android: function (w) { note('Android: ' + w); }, setBrightness: function () {}, setVolume: function () {}, updateCheck: function () {}, updateInstall: function () {}, perf: function () { var t = Date.now() / 2000 | 0; return JSON.stringify({ temp: 47 + t % 6, gpu: 20 + t % 30, cpu: 25 + t * 7 % 50, watt: 4.8 + t % 4 * 0.7, laedt: false, akku: 83, minuten: 197, fan: 3000 + t % 5 * 120, cpuGhz: 1.8 + t % 4 * 0.3, gpuMhz: 480 + t % 3 * 90, spiel: 'Majora’s Mask 3D' }); },
      type: function (c) { query = c === '\b' ? query.slice(0, -1) : query + c; window.plEvent('q', query); }, sync: function (w) { window.plSync(w); }, log: function (m) { console.log(m); }
    };
  }
  fit();
  if (window.PL) J.ready(side);
})();
