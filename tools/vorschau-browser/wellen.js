const puppeteer = require('puppeteer-core');
(async () => {
  const b = await puppeteer.launch({ executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox', '--disable-gpu'] });
  for (const [side, key] of [['unten', 'dc::UntenLeistung'], ['oben', '2.2'], ['oben', '5.1'], ['unten', '2.2']]) {
    const p = await b.newPage(); await p.setViewport({ width: side === 'oben' ? 960 : 620, height: 540 });
    p.on('pageerror', e => console.log('ERR', e.message));
    await p.goto('http://localhost:8765/ui/host.html?side=' + side, { waitUntil: 'networkidle0' });
    const r = await p.evaluate(async key => {
      const pos = () => { const e = document.querySelector('.welle'); return e ? Math.round(new DOMMatrix(getComputedStyle(e).transform).m41 * 10) / 10 : null; };
      window.plShow(key); await new Promise(r => setTimeout(r, 3000));
      const a = pos(); window.plShow(key); const b2 = pos(); await new Promise(r => setTimeout(r, 100)); const c = pos();
      return { vorher: a, direktDanach: b2, nach100ms: c };
    }, key);
    console.log(side, key, JSON.stringify(r));
    await p.close();
  }
  await b.close();
})();
