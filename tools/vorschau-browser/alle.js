const puppeteer = require('puppeteer-core');
const fs = require('fs');
(async () => {
  const b = await puppeteer.launch({ executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox', '--disable-gpu'] });
  const out = {}; fs.mkdirSync('shots', { recursive: true });
  for (const side of ['oben', 'unten']) {
    const p = await b.newPage();
    await p.setViewport({ width: side === 'oben' ? 960 : 620, height: 540 });
    const errs = []; let curKey = '';
    p.on('pageerror', e => errs.push([curKey, 'ERR ' + e.message.split('\n')[0]]));
    p.on('console', m => { if (m.type() === 'error') errs.push([curKey, 'CON ' + m.text().slice(0, 160)]); });
    p.on('requestfailed', r => errs.push([curKey, 'REQ ' + r.url().slice(-60)]));
    p.on('response', r => { if (r.status() >= 400) errs.push([curKey, 'HTTP' + r.status() + ' ' + r.url().slice(-60)]); });
    await p.setRequestInterception(true); p.on('request', q => { if (/polarlicht\.local/.test(q.url())) q.respond({ status: 200, contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64') }); else q.continue(); });
    await p.goto('http://localhost:8765/ui/host.html?side=' + side, { waitUntil: 'networkidle0' });
    await new Promise(r => setTimeout(r, 800));
    await p.evaluate(() => { window.plKey && window.plKey('INDEX'); });
    await new Promise(r => setTimeout(r, 300));
    let keys = await p.evaluate(() => Object.keys(window.PL_SCREENS || {}));
    await p.evaluate(() => { window.plKey && window.plKey('INDEX'); });
    keys = keys.concat(['o.wlan', 'o.bt', 'o.speicher', 'o.emu', 'o.app', 'o.setup1', 'o.setup2', 'o.setup3', 'o.setup4', 'o.setup5', 'acc']);
    const only = process.argv[2] ? new RegExp(process.argv[2]) : null;
    for (const k of keys) {
      if (only && !only.test(k)) continue;
      curKey = k;
      const r = await p.evaluate(k => { try { window.plShow(k); return 'ok'; } catch (e) { return 'THROW ' + e.message; } }, k);
      if (r !== 'ok') errs.push([k, r]);
      await new Promise(r => setTimeout(r, 450));
      const info = await p.evaluate(() => { const s = document.getElementById('stage'); return { kids: s.children.length, text: s.innerText.length, raw: (s.innerText.match(/\{\{[^}]*\}\}|\[[A-ZÄÖÜ][a-zäöü]+\]|undefined|NaN|null/g) || []).slice(0, 5) }; });
      if (!info.kids || info.raw.length) errs.push([k, 'INHALT ' + JSON.stringify(info)]);
      await p.screenshot({ path: 'shots/' + side + '_' + k.replace(/[:@]/g, '_') + '.png' });
    }
    out[side] = { n: keys.length, errs };
    await p.close();
  }
  await b.close();
  for (const s in out) { console.log('== ' + s + ' · ' + out[s].n + ' Ansichten'); const seen = new Set(); out[s].errs.forEach(e => { const t = e.join(' | '); if (!seen.has(t)) { seen.add(t); console.log(t); } }); }
})();
