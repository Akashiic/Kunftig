"""python3 gerar.py [boss ...]: writes the models to ../modelos and the previews to ../previas."""
import json
import os
import subprocess
import sys

import brutamontes
import steve

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "modelos")
PREV = os.path.join(HERE, "..", "previas")
THREE = os.environ.get("THREE_ROOT", os.path.join(HERE, "node_modules", ".."))
BOSSES = {"noel_brutamontes": brutamontes}
for name in ("sombrio", "gelo", "vtaper"):
    try:
        mod = __import__(name)
        BOSSES[mod.IDENT] = mod
    except ImportError:
        pass

os.makedirs(PREV, exist_ok=True)
tmp = os.path.join(HERE, ".tmp")
os.makedirs(tmp, exist_ok=True)
sm = steve.build()
sm.paint()
with open(os.path.join(tmp, "steve.json"), "w") as f:
    json.dump(sm.preview_json(), f)
want = sys.argv[1:] or list(BOSSES)
for ident in want:
    model = BOSSES[ident].build()
    pj = model.write(OUT, ident)
    with open(os.path.join(tmp, ident + ".json"), "w") as f:
        json.dump(pj, f)
    lo, hi = model.bounds()
    print(ident, "cubes", len(model.cubes), "tex", model.tex_w, "x", model.tex_h, "height", hi[1] - lo[1])
    if os.environ.get("NORENDER"):
        continue
    subprocess.run(
        ["node", os.path.join(HERE, "render.mjs"), THREE, os.path.join(tmp, ident + ".json"),
         os.path.join(PREV, ident), os.path.join(tmp, "steve.json")],
        check=True,
    )
