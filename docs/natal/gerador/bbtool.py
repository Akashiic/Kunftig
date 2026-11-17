"""Small toolkit that builds Blockbench (Bedrock entity / GeckoLib) models from code.

Coordinates are Blockbench's own: +X is the character's right (east), +Y up, the front faces -Z (north), feet at
y = 0, 16 units per block. Every cube gets its own box-UV island, painted face by face with numpy; each face is
handed to the painter as the picture seen from outside the cube (left to right, top to bottom), whatever its side.

Writes a .bbmodel (texture embedded), a .geo.json + .png pair for GeckoLib (the X mirror Blockbench applies on
export) and a .json the preview renderer reads.
"""

import base64
import io
import json
import math
import random
import uuid

import numpy as np
from PIL import Image

SIDES = ("north", "east", "south", "west")
ALL = SIDES + ("up", "down")


def c(hexstr):
    hexstr = hexstr.lstrip("#")
    return tuple(int(hexstr[i : i + 2], 16) for i in (0, 2, 4))


class Mat:
    """A palette: the first colour is the base, the others are sprinkled in with their weights."""

    def __init__(self, *cols, w=None, grad=0.10):
        self.cols = [c(x) if isinstance(x, str) else x for x in cols]
        self.w = w or ([5] + [2] * (len(self.cols) - 1))
        self.grad = grad

    def pick(self, rng):
        return rng.choices(self.cols, self.w)[0]


def shade(col, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in col[:3])


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))


class Face:
    """One face of one cube, as a (h, w, 4) view into the atlas, drawn in face coordinates."""

    def __init__(self, arr, name, rng):
        self.a = arr
        self.name = name
        self.rng = rng
        self.h, self.w = arr.shape[0], arr.shape[1]

    def px(self, x, y, col, alpha=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x, :3] = col[:3]
            self.a[y, x, 3] = alpha

    def get(self, x, y):
        return tuple(int(v) for v in self.a[y, x, :3])

    def rect(self, x0, y0, x1, y1, col_or_mat, alpha=255):
        """Inclusive rectangle; a Mat paints with its noise."""
        for y in range(max(0, y0), min(self.h, y1 + 1)):
            for x in range(max(0, x0), min(self.w, x1 + 1)):
                col = col_or_mat.pick(self.rng) if isinstance(col_or_mat, Mat) else col_or_mat
                self.px(x, y, col, alpha)

    def clear(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x, 3] = 0

    def tint(self, x, y, f):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x, :3] = shade(self.get(x, y), f)

    def fill(self, mat):
        for y in range(self.h):
            g = 1.0
            if self.name in SIDES and self.h > 1:
                g = 1 + mat.grad * 0.5 - mat.grad * (y / (self.h - 1)) * 1.5
            elif self.name == "down":
                g = 1 - mat.grad
            for x in range(self.w):
                self.px(x, y, shade(mat.pick(self.rng), g))


class Model:
    def __init__(self, name, seed=1, tex_w=256, tex_scale=1):
        self.tex_scale = tex_scale  # texture pixels per UV unit (2 = twice the usual Minecraft density)
        self.name = name
        self.rng = random.Random(seed)
        self.bones = {}
        self.order = []
        self.cubes = []
        self.tex_w = tex_w

    def bone(self, name, parent=None, pivot=(0, 0, 0), rot=(0, 0, 0)):
        assert name not in self.bones, name
        self.bones[name] = dict(name=name, parent=parent, pivot=list(pivot), rot=list(rot), cubes=[], children=[])
        self.order.append(name)
        if parent:
            self.bones[parent]["children"].append(name)
        return name

    def cube(self, bone, frm, size, mat, deco=None, inflate=0.0, rot=None, origin=None, name=None):
        size = [int(s) for s in size]
        assert all(s >= 1 for s in size), (bone, size)
        cb = dict(
            bone=bone,
            frm=list(frm),
            size=size,
            mat=mat,
            deco=deco if isinstance(deco, (list, tuple)) else ([deco] if deco else []),
            inflate=inflate,
            rot=list(rot) if rot else None,
            origin=list(origin) if origin else None,
            name=name or "%s_%d" % (bone, len(self.bones[bone]["cubes"])),
        )
        self.bones[bone]["cubes"].append(cb)
        self.cubes.append(cb)
        return cb

    # ------------------------------------------------------------------ texture

    def _pack(self):
        items = sorted(self.cubes, key=lambda k: -(k["size"][2] + k["size"][1]))
        x = y = row_h = 0
        W = self.tex_w
        for cb in items:
            w, h, d = cb["size"]
            iw, ih = 2 * d + 2 * w, d + h
            assert iw <= W, (cb["name"], iw)
            if x + iw > W:
                x, y, row_h = 0, y + row_h, 0
            cb["uv"] = [x, y]
            x += iw
            row_h = max(row_h, ih)
        H = y + row_h
        self.tex_h = 1 << max(4, math.ceil(math.log2(max(H, W // 4))))
        self.tex_h = max(self.tex_h, 16)

    @staticmethod
    def face_rects(cb):
        u, v = cb["uv"]
        w, h, d = cb["size"]
        return {
            "north": (u + d, v + d, w, h),
            "east": (u, v + d, d, h),
            "south": (u + 2 * d + w, v + d, w, h),
            "west": (u + d + w, v + d, d, h),
            "up": (u + d, v, w, d),
            "down": (u + d + w, v, w, d),
        }

    def paint(self):
        self._pack()
        tex = np.zeros((self.tex_h, self.tex_w, 4), dtype=np.uint8)
        for cb in self.cubes:
            for fname, (x, y, w, h) in self.face_rects(cb).items():
                f = Face(tex[y : y + h, x : x + w], fname, self.rng)
                f.fill(cb["mat"])
                for dfn in cb["deco"]:
                    dfn(f, cb)
        self.tex = tex
        return tex

    # ------------------------------------------------------------------ output

    def png_bytes(self):
        buf = io.BytesIO()
        Image.fromarray(self.tex, "RGBA").save(buf, "PNG")
        return buf.getvalue()

    def _uid(self):
        return str(uuid.UUID(int=self.rng.getrandbits(128)))

    def bbmodel(self, ident):
        elements = []
        cube_uuid = {}
        for cb in self.cubes:
            u, v = cb["uv"]
            w, h, d = cb["size"]
            fr = cb["frm"]
            to = [fr[0] + w, fr[1] + h, fr[2] + d]
            uid = self._uid()
            cube_uuid[id(cb)] = uid
            faces = {
                "north": [u + d, v + d, u + d + w, v + d + h],
                "east": [u, v + d, u + d, v + d + h],
                "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
                "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
                "up": [u + d + w, v + d, u + d, v],
                "down": [u + d + 2 * w, v, u + d + w, v + d],
            }
            bone = self.bones[cb["bone"]]
            el = {
                "name": cb["name"],
                "box_uv": True,
                "rescale": False,
                "locked": False,
                "render_order": "default",
                "allow_mirror_modeling": True,
                "from": fr,
                "to": to,
                "autouv": 0,
                "color": abs(hash(cb["bone"])) % 8,
                "inflate": cb["inflate"],
                "origin": cb["origin"] or bone["pivot"],
                "uv_offset": [u, v],
                "faces": {k: {"uv": val, "texture": 0} for k, val in faces.items()},
                "type": "cube",
                "uuid": uid,
            }
            if cb["rot"]:
                el["rotation"] = cb["rot"]
            elements.append(el)

        def group(name):
            b = self.bones[name]
            g = {
                "name": name,
                "origin": b["pivot"],
                "color": 0,
                "uuid": self._uid(),
                "export": True,
                "mirror_uv": False,
                "isOpen": False,
                "locked": False,
                "visibility": True,
                "autouv": 0,
                "children": [cube_uuid[id(cb)] for cb in b["cubes"]] + [group(ch) for ch in b["children"]],
            }
            if any(b["rot"]):
                g["rotation"] = b["rot"]
            return g

        roots = [group(n) for n in self.order if not self.bones[n]["parent"]]
        src = "data:image/png;base64," + base64.b64encode(self.png_bytes()).decode()
        lo, hi = self.bounds()
        return {
            "meta": {"format_version": "4.10", "model_format": "bedrock", "box_uv": True},
            "name": self.name,
            "model_identifier": ident,
            "visible_box": [
                math.ceil(max(abs(lo[0]), abs(hi[0]), abs(lo[2]), abs(hi[2])) * 2 / 16) + 1,
                math.ceil((hi[1] - lo[1]) / 16) + 1,
                0,
            ],
            "variable_placeholders": "",
            "variable_placeholder_buttons": [],
            "timeline_setups": [],
            "unhandled_root_fields": {},
            "resolution": {"width": self.tex_w, "height": self.tex_h},
            "elements": elements,
            "outliner": roots,
            "textures": [
                {
                    "path": "",
                    "name": self.name + ".png",
                    "folder": "",
                    "namespace": "",
                    "id": "0",
                    "width": self.tex_w * self.tex_scale,
                    "height": self.tex_h * self.tex_scale,
                    "uv_width": self.tex_w,
                    "uv_height": self.tex_h,
                    "particle": False,
                    "use_as_default": False,
                    "layers_enabled": False,
                    "sync_to_project": "",
                    "render_mode": "default",
                    "render_sides": "auto",
                    "frame_time": 1,
                    "frame_order_type": "loop",
                    "frame_order": "",
                    "frame_interpolate": False,
                    "visible": True,
                    "internal": True,
                    "saved": False,
                    "uuid": self._uid(),
                    "source": src,
                }
            ],
        }

    def geo(self, ident):
        bones = []
        for name in self.order:
            b = self.bones[name]
            gb = {"name": name, "pivot": [-b["pivot"][0], b["pivot"][1], b["pivot"][2]]}
            if b["parent"]:
                gb["parent"] = b["parent"]
            if any(b["rot"]):
                gb["rotation"] = [-b["rot"][0], -b["rot"][1], b["rot"][2]]
            cubes = []
            for cb in b["cubes"]:
                w, h, d = cb["size"]
                fr = cb["frm"]
                gc = {"origin": [-(fr[0] + w), fr[1], fr[2]], "size": [w, h, d], "uv": cb["uv"]}
                if cb["inflate"]:
                    gc["inflate"] = cb["inflate"]
                if cb["rot"]:
                    o = cb["origin"] or b["pivot"]
                    gc["pivot"] = [-o[0], o[1], o[2]]
                    gc["rotation"] = [-cb["rot"][0], -cb["rot"][1], cb["rot"][2]]
                cubes.append(gc)
            if cubes:
                gb["cubes"] = cubes
            bones.append(gb)
        lo, hi = self.bounds()
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [
                {
                    "description": {
                        "identifier": "geometry." + ident,
                        "texture_width": self.tex_w,
                        "texture_height": self.tex_h,
                        "visible_bounds_width": math.ceil(max(hi[0] - lo[0], hi[2] - lo[2]) / 16) + 2,
                        "visible_bounds_height": math.ceil(hi[1] / 16) + 2,
                        "visible_bounds_offset": [0, round(hi[1] / 32, 2), 0],
                    },
                    "bones": bones,
                }
            ],
        }

    def bounds(self):
        lo = [1e9] * 3
        hi = [-1e9] * 3
        for cb in self.cubes:
            for i in range(3):
                lo[i] = min(lo[i], cb["frm"][i])
                hi[i] = max(hi[i], cb["frm"][i] + cb["size"][i])
        return lo, hi

    def preview_json(self):
        return {
            "name": self.name,
            "tex_w": self.tex_w,
            "tex_h": self.tex_h,
            "texture": "data:image/png;base64," + base64.b64encode(self.png_bytes()).decode(),
            "bones": [
                dict(
                    name=n,
                    parent=self.bones[n]["parent"],
                    pivot=self.bones[n]["pivot"],
                    rot=self.bones[n]["rot"],
                    cubes=[
                        dict(
                            frm=cb["frm"],
                            size=cb["size"],
                            uv=cb["uv"],
                            inflate=cb["inflate"],
                            rot=cb["rot"],
                            origin=cb["origin"] or self.bones[n]["pivot"],
                        )
                        for cb in self.bones[n]["cubes"]
                    ],
                )
                for n in self.order
            ],
        }

    def write(self, outdir, ident):
        import os

        os.makedirs(outdir, exist_ok=True)
        self.paint()
        with open(os.path.join(outdir, ident + ".bbmodel"), "w") as f:
            json.dump(self.bbmodel(ident), f)
        with open(os.path.join(outdir, ident + ".geo.json"), "w") as f:
            json.dump(self.geo(ident), f, indent=1)
        with open(os.path.join(outdir, ident + ".png"), "wb") as f:
            f.write(self.png_bytes())
        return self.preview_json()


# ---------------------------------------------------------------------- decorators


def only(faces, fn):
    faces = set(faces)

    def d(f, cb):
        if f.name in faces:
            fn(f, cb)

    return d


def trim(mat, bottom=0, top=0, faces=SIDES, jag=1, cap=None):
    """Fur (or any) band on the bottom/top rows of the side faces, with a ragged inner edge.
    cap paints the whole down (bottom) / up (top) face too."""

    def d(f, cb):
        if f.name in faces:
            for x in range(f.w):
                if bottom:
                    n = bottom + (f.rng.randint(0, jag) if jag else 0)
                    f.rect(x, f.h - n, x, f.h - 1, mat)
                if top:
                    n = top + (f.rng.randint(0, jag) if jag else 0)
                    f.rect(x, 0, x, n - 1, mat)
        if cap and ((f.name == "down" and bottom) or (f.name == "up" and top)):
            f.rect(0, 0, f.w - 1, f.h - 1, mat)

    return d


def speckle(mat, amount=0.08, faces=ALL):
    def d(f, cb):
        if f.name in faces:
            for y in range(f.h):
                for x in range(f.w):
                    if f.rng.random() < amount:
                        f.px(x, y, mat.pick(f.rng))

    return d


def ragged(bottom=2, faces=SIDES, chance=0.55):
    """Cuts the bottom rows out (alpha) in a torn pattern."""

    def d(f, cb):
        if f.name in faces:
            for x in range(f.w):
                n = 0
                while n < bottom and f.rng.random() < chance:
                    n += 1
                for k in range(n):
                    f.clear(x, f.h - 1 - k)

    return d


def edge_dark(f_amt=0.82, faces=SIDES):
    """Darkens the outline of a face, so stacked cubes read apart."""

    def d(f, cb):
        if f.name in faces:
            for x in range(f.w):
                f.tint(x, f.h - 1, f_amt)
            for y in range(f.h):
                f.tint(0, y, 0.92)
                f.tint(f.w - 1, y, 0.92)

    return d


def stripes(mat_a, mat_b, period=4, slope=1, faces=ALL):
    """Diagonal candy-cane stripes."""

    def d(f, cb):
        if f.name in faces:
            for y in range(f.h):
                for x in range(f.w):
                    k = (x + slope * y) % period
                    f.px(x, y, (mat_a if k < period // 2 else mat_b).pick(f.rng))

    return d


def glow_rect(col, x0, y0, x1, y1, faces=("north",)):
    def d(f, cb):
        if f.name in faces:
            f.rect(x0, y0, x1, y1, col)

    return d
