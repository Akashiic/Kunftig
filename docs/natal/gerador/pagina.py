"""Writes ../visualizador.html: the three bosses in a 3D viewer (three.js from cdnjs), models embedded."""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
tmp = os.path.join(HERE, ".tmp")
INFO = [
    ("noel_vtaper", "Noel V-Taper", "Alto, em V, com metralhadoras",
     "Oito blocos de altura, ombros 3,4 vezes mais largos que a cintura. Sobrancelhas em V sobre um visor tático rachado, com um olho cibernético vermelho brilhando atrás; charuto aceso num rosnado; barba trançada com anéis de aço. Cicatrizes, uma caveira de gorro tatuada no ombro e a lista dos malvados nas costas, com nomes riscados. Ombreira com marcas de abates, cartucheiras em X e um canhão giratório de sete canos em cada mão, alimentado por um tambor embrulhado de presente.",
     ["Rajada contínua girando a cintura: varre a arena em leque", "Para, aquece os canos (as bocas acendem) e solta uma rajada longa e reta", "Arranca a granada do gorro e joga", "Pose de fisiculturista: onda de choque que empurra quem está perto"]),
    ("noel_brutamontes", "Noel Brutamontes", "O lutador",
     "Casaco aberto, barba enorme e punhos do tamanho de um baú, com socos-ingleses de ouro. Carrega um saco de carvão nas costas e uma granada-presente no cinto.",
     ["Sequência de socos com avanço curto", "Pancada no chão que levanta neve em anel", "Arremessa a granada-presente (explode em fitas)"]),
    ("noel_sombrio", "Noel Sombrio", "O corrompido",
     "Curvado e magro, com chifres furando o gorro rasgado e olhos que brilham. Correntes com sinos no peito, foice de bengala doce numa mão e um saco de carvão em brasa preso por corrente na outra.",
     ["Ceifada larga com a lâmina que brilha", "Puxa o jogador com a corrente", "Abre o saco: carvões em brasa caem pela arena"]),
    ("noel_gelo_eterno", "Noel do Gelo Eterno", "O gigante congelado",
     "Um Noel tomado pelo gelo: coroa de cristais no gorro, barba de pingentes, cristais nos ombros. Leva um martelo cuja cabeça é um presente congelado e um orbe de gelo na outra mão.",
     ["Martelada que congela o chão em volta", "Orbe que dispara estilhaços de gelo", "Chuva de pingentes do teto da arena"]),
]
models = {}
import sys
SHOW = sys.argv[1:] or ["noel_vtaper"]
INFO = [i for i in INFO if i[0] in SHOW]
for ident, *_ in INFO:
    with open(os.path.join(tmp, ident + ".json")) as f:
        models[ident] = json.load(f)
with open(os.path.join(tmp, "steve.json")) as f:
    steve = json.load(f)


def stats(m, ident):
    from PIL import Image as _I

    pw, ph = _I.open(os.path.join(HERE, "..", "modelos", ident + ".png")).size
    scale = pw // m["tex_w"]
    cubes = sum(len(b["cubes"]) for b in m["bones"])
    lo = min(c["frm"][1] for b in m["bones"] for c in b["cubes"])
    hi = max(c["frm"][1] + c["size"][1] for b in m["bones"] for c in b["cubes"])
    return dict(cubes=cubes, bones=len(m["bones"]), tex="%dx%d" % (pw, ph) + (" (%d px/un.)" % scale if scale > 1 else ""),
                height=round((hi - lo) / 16, 1))


data = [dict(id=i, name=n, role=r, desc=d, ideas=ideas, stats=stats(models[i], i)) for i, n, r, d, ideas in INFO]
viewer_js = open(os.path.join(HERE, "viewer.js")).read().replace("export function", "function")

html = """<title>Bosses de Natal</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,600;12..96,800&family=Inter:wght@400;500;600&family=JetBrains+Mono:wght@500&display=swap">
<style>
/* layout: one stage (3D viewer) beside a quiet spec column; the boss picker is a row of tabs over both */
:root {
  --bg: #eef2f4; --panel: #ffffff; --fg: #1c2328; --muted: #5d6a72; --line: #d5dde2;
  --holly: #b3202c; --pine: #2e5c47; --stage: #dfe7eb; --stage2: #cdd8de;
  --display: "Bricolage Grotesque", "Inter", system-ui, sans-serif;
  --body: "Inter", system-ui, -apple-system, sans-serif;
  --mono: "JetBrains Mono", ui-monospace, Menlo, monospace;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #11161a; --panel: #192026; --fg: #e5ebee; --muted: #93a1a9; --line: #2a343b;
  --holly: #e2535d; --pine: #6fb393; --stage: #1a2228; --stage2: #141a1f; color-scheme: dark } }
:root[data-theme="dark"] {
  --bg: #11161a; --panel: #192026; --fg: #e5ebee; --muted: #93a1a9; --line: #2a343b;
  --holly: #e2535d; --pine: #6fb393; --stage: #1a2228; --stage2: #141a1f; color-scheme: dark }
html, body { background: var(--bg); color: var(--fg); }
body { font-family: var(--body); font-size: 15px; line-height: 1.55;
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px));
  padding-block: 28px 40px; }
.wrap { max-width: 1120px; margin: 0 auto; display: grid; gap: 20px; }
header { display: grid; gap: 4px; }
.eyebrow { font: 500 12px/1 var(--mono); letter-spacing: .08em; text-transform: uppercase; color: var(--holly); }
h1 { font: 800 clamp(30px, 5vw, 46px)/1.05 var(--display); margin: 0; text-wrap: balance; letter-spacing: -.01em; }
.lede { color: var(--muted); margin: 6px 0 0; max-width: 64ch; }
.tabs { display: flex; flex-wrap: wrap; gap: 8px; }
.tab { font: 600 14px/1 var(--body); padding: 11px 16px; border-radius: 999px; border: 1px solid var(--line);
  background: var(--panel); color: var(--fg); cursor: pointer; }
.tab[aria-selected="true"] { background: var(--holly); border-color: var(--holly); color: #fff; }
.tab:focus-visible, .ctl:focus-visible { outline: 2px solid var(--pine); outline-offset: 2px; }
.main { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr); gap: 20px; align-items: start; }
@media (max-width: 820px) { .main { grid-template-columns: minmax(0, 1fr); } }
.stage { position: relative; border-radius: 14px; overflow: hidden; aspect-ratio: 4 / 4.3; max-width: 100%;
  background: radial-gradient(120% 90% at 50% 30%, var(--stage), var(--stage2)); border: 1px solid var(--line); touch-action: none; cursor: grab; }
.stage:active { cursor: grabbing; }
.stage canvas { display: block; width: 100%; height: 100%; }
.hint { position: absolute; left: 12px; bottom: 10px; font: 500 11px/1.3 var(--mono); color: var(--muted); pointer-events: none; }
.ctls { position: absolute; right: 10px; top: 10px; display: flex; flex-wrap: wrap; gap: 6px; justify-content: flex-end; max-width: 70%; }
.ctl { font: 600 12px/1 var(--body); padding: 8px 11px; border-radius: 8px; border: 1px solid var(--line);
  background: color-mix(in srgb, var(--panel) 88%, transparent); color: var(--fg); cursor: pointer; }
.ctl[aria-pressed="true"] { border-color: var(--pine); color: var(--pine); }
.spec { display: grid; gap: 18px; min-width: 0; }
.spec h2 { font: 800 30px/1.1 var(--display); margin: 0; text-wrap: balance; }
.role { font: 500 12px/1 var(--mono); letter-spacing: .08em; text-transform: uppercase; color: var(--pine); }
.spec p { margin: 0; max-width: 60ch; }
dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1px; margin: 0; background: var(--line);
  border: 1px solid var(--line); border-radius: 10px; overflow: hidden; }
dl div { background: var(--panel); padding: 10px 12px; display: grid; gap: 2px; }
dt { font: 500 11px/1.2 var(--mono); letter-spacing: .06em; text-transform: uppercase; color: var(--muted); }
dd { margin: 0; font: 600 18px/1.2 var(--body); font-variant-numeric: tabular-nums; }
h3 { font: 600 13px/1 var(--body); margin: 0 0 8px; color: var(--muted); }
ul { margin: 0; padding-left: 18px; display: grid; gap: 4px; }
.files { font-size: 13px; color: var(--muted); }
.files code { font-family: var(--mono); font-size: 12px; color: var(--fg); }
@media (prefers-reduced-motion: reduce) { .tab { transition: none; } }
</style>
<div class="wrap">
  <header>
    <span class="eyebrow">TEOF · Natal 2026 · esboços</span>
    <h1>Bosses de Natal</h1>
    <p class="lede">Papai Noel chefe, montado em cubos no formato do Blockbench (entidade Bedrock, lida pelo GeckoLib das exibições do Kunftig). Arraste para girar, use a roda ou pinça para aproximar.</p>
  </header>
  <div class="tabs" role="tablist" id="tabs"></div>
  <div class="main">
    <div class="stage" id="stage">
      <div class="ctls">
        <button class="ctl" id="v-front" type="button">Frente</button>
        <button class="ctl" id="v-side" type="button">Lado</button>
        <button class="ctl" id="v-back" type="button">Costas</button>
        <button class="ctl" id="v-steve" type="button" aria-pressed="false">Jogador ao lado</button>
      </div>
      <span class="hint" id="hint">girando sozinho · arraste para parar</span>
    </div>
    <section class="spec" aria-live="polite">
      <div><div class="role" id="role"></div><h2 id="name"></h2></div>
      <p id="desc"></p>
      <dl>
        <div><dt>Altura (escala 1)</dt><dd id="s-h"></dd></div>
        <div><dt>Cubos</dt><dd id="s-c"></dd></div>
        <div><dt>Ossos</dt><dd id="s-b"></dd></div>
        <div><dt>Textura</dt><dd id="s-t"></dd></div>
      </dl>
      <div><h3>Ideias de ataque (rascunho)</h3><ul id="ideas"></ul></div>
      <p class="files">Arquivos no repositório Kunftig, pasta <code>docs/natal/modelos</code>: <code id="f-id"></code> em <code>.bbmodel</code> (abre no Blockbench), <code>.geo.json</code> e <code>.png</code>.</p>
    </section>
  </div>
</div>
<script type="module">
import * as THREE from 'https://cdnjs.cloudflare.com/ajax/libs/three.js/0.160.0/three.module.min.js';
const MODELS = __MODELS__;
const STEVE = __STEVE__;
const INFO = __INFO__;
__VIEWER__
const stage = document.getElementById('stage');
const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
renderer.outputColorSpace = THREE.SRGBColorSpace;
stage.prepend(renderer.domElement);
const scene = new THREE.Scene();
const pivot = new THREE.Group();
scene.add(pivot);
const cam = new THREE.OrthographicCamera(-1, 1, 1, -1, -1000, 1000);
let current = null, steveOn = false, yaw = 35, pitch = 8, zoom = 1, auto = true, frame = { cy: 36, hh: 40, cx: 0 };
const built = {};
function load(id) {
  pivot.clear();
  const m = buildModel(THREE, MODELS[id]).root;
  pivot.add(m);
  if (steveOn) {
    const s = buildModel(THREE, STEVE).root;
    const mb = new THREE.Box3().setFromObject(m);
    s.position.x = mb.min.x - 12;
    pivot.add(s);
  }
  pivot.rotation.set(0, 0, 0);
  pivot.updateMatrixWorld(true);
  const box = new THREE.Box3().setFromObject(pivot);
  const r = Math.max(box.max.x - box.min.x, box.max.z - box.min.z, box.max.y - box.min.y) / 2;
  frame = { cy: (box.min.y + box.max.y) / 2, hh: Math.max((box.max.y - box.min.y) / 2, r * 1.1) * 1.08 };
  current = id;
  const info = INFO.find(i => i.id === id);
  document.getElementById('name').textContent = info.name;
  document.getElementById('role').textContent = info.role;
  document.getElementById('desc').textContent = info.desc;
  document.getElementById('s-h').textContent = info.stats.height.toString().replace('.', ',') + ' blocos';
  document.getElementById('s-c').textContent = info.stats.cubes;
  document.getElementById('s-b').textContent = info.stats.bones;
  document.getElementById('s-t').textContent = info.stats.tex.replace('x', ' × ');
  document.getElementById('f-id').textContent = info.id;
  const ul = document.getElementById('ideas'); ul.replaceChildren();
  for (const t of info.ideas) { const li = document.createElement('li'); li.textContent = t; ul.append(li); }
  document.querySelectorAll('.tab').forEach(t => t.setAttribute('aria-selected', t.dataset.id === id ? 'true' : 'false'));
}
const tabs = document.getElementById('tabs');
if (INFO.length < 2) tabs.hidden = true;
for (const i of INFO) {
  const b = document.createElement('button');
  b.className = 'tab'; b.type = 'button'; b.setAttribute('role', 'tab'); b.dataset.id = i.id; b.textContent = i.name; b.id = 'tab-' + i.id;
  b.onclick = () => load(i.id);
  tabs.append(b);
}
function resize() {
  const w = stage.clientWidth, h = stage.clientHeight;
  renderer.setSize(w, h, false);
}
new ResizeObserver(resize).observe(stage);
const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
if (reduce) auto = false;
const stopAuto = () => { auto = false; document.getElementById('hint').textContent = 'arraste para girar · roda/pinça para aproximar'; };
const setView = (y) => { stopAuto(); yaw = y; pitch = 6; };
document.getElementById('v-front').onclick = () => setView(0);
document.getElementById('v-side').onclick = () => setView(90);
document.getElementById('v-back').onclick = () => setView(180);
document.getElementById('v-steve').onclick = (e) => { steveOn = !steveOn; e.currentTarget.setAttribute('aria-pressed', String(steveOn)); load(current); };
const pts = new Map(); let pinch0 = 0, zoom0 = 1;
stage.addEventListener('pointerdown', e => { if (e.target.closest('.ctl')) return; stage.setPointerCapture(e.pointerId); pts.set(e.pointerId, [e.clientX, e.clientY]); stopAuto();
  if (pts.size === 2) { const [a, b] = [...pts.values()]; pinch0 = Math.hypot(a[0] - b[0], a[1] - b[1]); zoom0 = zoom; } });
stage.addEventListener('pointermove', e => { if (!pts.has(e.pointerId)) return; const p = pts.get(e.pointerId);
  if (pts.size === 1) { yaw += (e.clientX - p[0]) * 0.5; pitch = Math.max(-30, Math.min(60, pitch + (e.clientY - p[1]) * 0.3)); }
  pts.set(e.pointerId, [e.clientX, e.clientY]);
  if (pts.size === 2) { const [a, b] = [...pts.values()]; const d = Math.hypot(a[0] - b[0], a[1] - b[1]); zoom = Math.max(0.6, Math.min(4, zoom0 * d / pinch0)); } });
const up = e => pts.delete(e.pointerId);
stage.addEventListener('pointerup', up); stage.addEventListener('pointercancel', up);
stage.addEventListener('wheel', e => { e.preventDefault(); zoom = Math.max(0.6, Math.min(4, zoom * Math.exp(-e.deltaY * 0.0015))); }, { passive: false });
let last = performance.now();
function tick(t) {
  const dt = Math.min(0.05, (t - last) / 1000); last = t;
  if (auto) yaw += dt * 22;
  pivot.rotation.y = yaw * Math.PI / 180;
  shadeScene(THREE, pivot);
  const w = stage.clientWidth || 1, h = stage.clientHeight || 1, aspect = w / h;
  const hh = frame.hh / zoom;
  cam.left = -hh * aspect; cam.right = hh * aspect; cam.top = hh; cam.bottom = -hh; cam.updateProjectionMatrix();
  const el = pitch * Math.PI / 180;
  cam.position.set(0, frame.cy + Math.sin(el) * 200, -Math.cos(el) * 200);
  cam.lookAt(0, frame.cy, 0);
  renderer.render(scene, cam);
  requestAnimationFrame(tick);
}
const start = (location.hash || '').slice(1);
load(INFO.some(i => i.id === start) ? start : INFO[0].id);
resize();
requestAnimationFrame(tick);
</script>
"""
html = (html.replace("__MODELS__", json.dumps(models)).replace("__STEVE__", json.dumps(steve))
        .replace("__INFO__", json.dumps(data, ensure_ascii=False)).replace("__VIEWER__", viewer_js))
out = os.path.join(HERE, "..", "visualizador.html")
with open(out, "w") as f:
    f.write(html)
print(out, len(html))
