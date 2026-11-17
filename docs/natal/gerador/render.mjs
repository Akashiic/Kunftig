// node render.mjs <three-root-dir> <preview.json> <out-prefix> [steve.json]
import { createRequire } from 'node:module';
const { chromium } = createRequire(import.meta.url)('playwright');
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
const [threeRoot, modelPath, outPrefix, stevePath] = process.argv.slice(2);
const here = path.dirname(new URL(import.meta.url).pathname);
const server = http.createServer((req, res) => {
  const p = req.url.startsWith('/node_modules/') ? path.join(threeRoot, req.url) : path.join(here, req.url);
  try {
    const body = fs.readFileSync(p);
    res.writeHead(200, { 'Content-Type': p.endsWith('.js') ? 'text/javascript' : 'text/html' });
    res.end(body);
  } catch { res.writeHead(404); res.end(); }
}).listen(0);
const port = server.address().port;
const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage();
page.on('console', m => console.log('[page]', m.text()));
await page.goto(`http://localhost:${port}/render.html`);
await page.waitForFunction(() => window.ready);
const model = JSON.parse(fs.readFileSync(modelPath));
const steve = stevePath ? JSON.parse(fs.readFileSync(stevePath)) : null;
const views = [
  { name: 'frente34', yaw: 35, pitch: 8 },
  { name: 'frente', yaw: 0, pitch: 4 },
  { name: 'lado', yaw: 90, pitch: 4 },
  { name: 'costas34', yaw: 215, pitch: 8 },
  { name: 'escala', yaw: 25, pitch: 6, steve: -34 },
];
const shots = await page.evaluate(([m, v, s]) => window.renderViews(m, v, [700, 900], s), [model, views, steve]);
shots.forEach((d, i) => fs.writeFileSync(`${outPrefix}_${views[i].name}.png`, Buffer.from(d.split(',')[1], 'base64')));
await browser.close();
server.close();
