const puppeteer = require('puppeteer-core');
(async () => {
  const [side, key, wait, out, ...keys] = process.argv.slice(2);
  const b = await puppeteer.launch({ executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox', '--disable-gpu'] });
  const p = await b.newPage();
  await p.setViewport({ width: side === 'oben' ? 960 : 620, height: 540 });
  p.on('pageerror', e => console.log('ERR', e.message));
  await p.setRequestInterception(true); p.on('request', q => { if (/polarlicht\.local/.test(q.url())) q.respond({ status: 200, contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64') }); else q.continue(); });
  await p.goto('http://localhost:8765/ui/host.html?side=' + side, { waitUntil: 'networkidle0' });
  await new Promise(r => setTimeout(r, 600));
  await p.evaluate(k => window.plShow(k), key);
  for (const k of keys) { await new Promise(r => setTimeout(r, 250)); await p.evaluate(k => window.plKey(k), k); }
  await new Promise(r => setTimeout(r, +wait));
  await p.screenshot({ path: out });
  console.log(await p.evaluate(() => document.getElementById('stage').innerText.replace(/\s+/g, ' ').slice(0, 300)));
  await b.close();
})();
