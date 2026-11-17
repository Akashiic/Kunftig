"""Second painting engine: shaded pixel art baked from geometry.

Every texel knows where it sits on the model (rest pose) and in the world (posed). A material turns that into
layers: a height map (relief: muscles, weave, fur clumps, rivets), an albedo offset (in ramp steps), which colour
ramp to use, emission and cut-outs. The engine then lights it the same way for every part:

* a normal from the face, tilted near the cube's edges as if they were rounded (radius per cube) and bent by the
  height map;
* soft light from the upper front left (half-Lambert), cavity darkening in the grooves of the height map,
  specular for metals;
* ambient occlusion traced against every other cube of the posed model (contact shadows under the beard, the
  arms, the hat brim, between the legs);
* the result picks a step of a hand-made colour ramp (shadows cool and saturated, lights warm), with a light
  ordered dither between steps.

The texture is painted at `tex_scale` pixels per UV unit.
"""

import math

import numpy as np
from PIL import Image

from bbtool import Model

# ---------------------------------------------------------------------------------------------- colour ramps


def _rgb(h):
    h = h.lstrip("#")
    return [int(h[i : i + 2], 16) for i in (0, 2, 4)]


def _lin(c):
    c = np.asarray(c, float) / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def _srgb(lin):
    lin = np.clip(lin, 0, 1)
    return np.where(lin <= 0.0031308, lin * 12.92, 1.055 * np.power(lin, 1 / 2.4) - 0.055) * 255


def ramp(*keys, n=12):
    """n colours spread evenly through the key colours (dark to light), interpolated in linear light."""
    k = np.array([_lin(_rgb(x)) for x in keys])
    t = np.linspace(0, len(keys) - 1, n)
    out = []
    for x in t:
        i = min(int(x), len(keys) - 2)
        f = x - i
        out.append(_srgb(k[i] * (1 - f) + k[i + 1] * f))
    return np.round(np.array(out)).astype(np.uint8)


# ---------------------------------------------------------------------------------------------------- noise


def _hash(ix, iy, iz, seed):
    h = (ix.astype(np.int64) * 73856093) ^ (iy.astype(np.int64) * 19349663) ^ (iz.astype(np.int64) * 83492791)
    h = (h ^ (seed * 2654435761)) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF) / float(0xFFFFFF)


def vnoise(P, freq=1.0, seed=0):
    """Smooth value noise in [0, 1]; freq may be a scalar or a 3-vector (anisotropic)."""
    Q = P * np.asarray(freq, float)
    i = np.floor(Q)
    f = Q - i
    w = f * f * (3 - 2 * f)
    i = i.astype(np.int64)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                h = _hash(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz, seed)
                wx = w[..., 0] if dx else 1 - w[..., 0]
                wy = w[..., 1] if dy else 1 - w[..., 1]
                wz = w[..., 2] if dz else 1 - w[..., 2]
                out = out + h * wx * wy * wz
    return out


def fbm(P, freq=1.0, octaves=3, seed=0, gain=0.5):
    tot, amp, norm = 0, 1.0, 0
    fr = np.asarray(freq, float)
    for o in range(octaves):
        tot = tot + amp * vnoise(P, fr, seed + o * 101)
        norm += amp
        amp *= gain
        fr = fr * 2.03
    return tot / norm


def worley(P, freq=1.0, seed=0):
    """Distance to the nearest and second nearest feature point (cells of size 1/freq), and the cell's hash."""
    Q = P * np.asarray(freq, float)
    i = np.floor(Q).astype(np.int64)
    f1 = np.full(Q.shape[:-1], 9.0)
    f2 = np.full(Q.shape[:-1], 9.0)
    cid = np.zeros(Q.shape[:-1])
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cx, cy, cz = i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz
                fx = cx + _hash(cx, cy, cz, seed)
                fy = cy + _hash(cx, cy, cz, seed + 1)
                fz = cz + _hash(cx, cy, cz, seed + 2)
                d = np.sqrt((Q[..., 0] - fx) ** 2 + (Q[..., 1] - fy) ** 2 + (Q[..., 2] - fz) ** 2)
                closer = d < f1
                f2 = np.where(closer, f1, np.minimum(f2, d))
                cid = np.where(closer, _hash(cx, cy, cz, seed + 3), cid)
                f1 = np.where(closer, d, f1)
    return f1, f2, cid


BAYER = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) + 0.5) / 16 - 0.5


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


# ------------------------------------------------------------------------------------------------ transforms


def euler(r):
    """Blockbench/three.js rotation (degrees, order ZYX: X applied first)."""
    rx, ry, rz = np.radians(r)
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def to_euler(R):
    """Inverse of euler(): degrees (x, y, z) for R = Rz Ry Rx."""
    y = math.asin(max(-1.0, min(1.0, -R[2, 0])))
    x = math.atan2(R[2, 1], R[2, 2])
    z = math.atan2(R[1, 0], R[0, 0])
    return [round(math.degrees(a), 3) for a in (x, y, z)]


def T(v):
    M = np.eye(4)
    M[:3, 3] = v
    return M


def R4(R):
    M = np.eye(4)
    M[:3, :3] = R
    return M


class Rig:
    """Forward kinematics of a Model: where rest-pose points end up once the bones are posed."""

    def __init__(self, model):
        self.m = model
        self.cache = {}

    def bone(self, name):
        """Matrix of the bone's frame (origin at its pivot)."""
        if name in self.cache:
            return self.cache[name]
        b = self.m.bones[name]
        if b["parent"]:
            pp = self.m.bones[b["parent"]]["pivot"]
            M = self.bone(b["parent"]) @ T(np.subtract(b["pivot"], pp)) @ R4(euler(b["rot"]))
        else:
            M = T(b["pivot"]) @ R4(euler(b["rot"]))
        self.cache[name] = M
        return M

    def rest_to_world(self, name):
        return self.bone(name) @ T(-np.asarray(self.m.bones[name]["pivot"], float))

    def cube(self, cb):
        b = self.m.bones[cb["bone"]]
        if cb["rot"]:
            o = np.asarray(cb["origin"] or b["pivot"], float)
            return self.bone(cb["bone"]) @ T(o - b["pivot"]) @ R4(euler(cb["rot"])) @ T(-o)
        return self.rest_to_world(cb["bone"])

    def point(self, name, p):
        return (self.rest_to_world(name) @ np.append(np.asarray(p, float), 1))[:3]

    def to_rest(self, name, pw):
        return (np.linalg.inv(self.rest_to_world(name)) @ np.append(np.asarray(pw, float), 1))[:3]

    def world_rot(self, name):
        return self.bone(name)[:3, :3]

    def invalidate(self):
        self.cache = {}


# -------------------------------------------------------------------------------------------------- faces

# face: normal, u direction, v direction (in the cube's rest frame); u runs left to right, v top to bottom of
# the face as seen from outside
FACES = {
    "north": ((0, 0, -1), (-1, 0, 0), (0, -1, 0)),
    "south": ((0, 0, 1), (1, 0, 0), (0, -1, 0)),
    "east": ((1, 0, 0), (0, 0, -1), (0, -1, 0)),
    "west": ((-1, 0, 0), (0, 0, 1), (0, -1, 0)),
    "up": ((0, 1, 0), (-1, 0, 0), (0, 0, -1)),
    "down": ((0, -1, 0), (-1, 0, 0), (0, 0, 1)),
}


def face_points(cb, fname, nu, nv):
    """Rest-pose points of an nv x nv grid on a face of the (inflated) cube, sampled at cell centres."""
    w, h, d = cb["size"]
    inf = cb["inflate"]
    x1, y1, z1 = np.asarray(cb["frm"], float) - inf
    x2, y2, z2 = np.asarray(cb["frm"], float) + np.asarray(cb["size"], float) + inf
    jj, ii = np.mgrid[0:nv, 0:nu]
    uu = (ii + 0.5) / nu
    vv = (jj + 0.5) / nv
    one = np.ones_like(uu)
    if fname == "north":
        P = (x2 - uu * (x2 - x1), y2 - vv * (y2 - y1), z1 * one)
    elif fname == "south":
        P = (x1 + uu * (x2 - x1), y2 - vv * (y2 - y1), z2 * one)
    elif fname == "east":
        P = (x2 * one, y2 - vv * (y2 - y1), z2 - uu * (z2 - z1))
    elif fname == "west":
        P = (x1 * one, y2 - vv * (y2 - y1), z1 + uu * (z2 - z1))
    elif fname == "up":
        P = (x2 - uu * (x2 - x1), y2 * one, z2 - vv * (z2 - z1))
    else:
        P = (x2 - uu * (x2 - x1), y1 * one, z1 + vv * (z2 - z1))
    return np.stack(P, -1), uu, vv


def face_size(cb, fname):
    w, h, d = cb["size"]
    return {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}[fname]


class Ctx:
    pass


class Layer:
    def __init__(self, shape, ramps):
        self.H = np.zeros(shape)
        self.alb = np.zeros(shape)
        self.rid = np.zeros(shape, dtype=int)
        self.emit = np.zeros(shape + (3,))
        self.emask = np.zeros(shape, dtype=bool)
        self.alpha = np.ones(shape, dtype=bool)
        self.spec = np.zeros(shape)
        self.ramps = list(ramps)

    def use(self, r):
        """Index of ramp r in this layer, adding it if needed."""
        for i, x in enumerate(self.ramps):
            if x is r:
                return i
        self.ramps.append(r)
        return len(self.ramps) - 1


# ------------------------------------------------------------------------------------------------- the engine

LIGHT = np.array([0.5, 0.8, -0.62])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
VIEW = np.array([0.15, 0.25, -1.0])
VIEW = VIEW / np.linalg.norm(VIEW)
HALF = (LIGHT + VIEW) / np.linalg.norm(LIGHT + VIEW)


def _hemisphere(k, seed=7):
    rng = np.random.default_rng(seed)
    pts = []
    golden = math.pi * (3 - math.sqrt(5))
    for i in range(k):
        r = math.sqrt((i + 0.5) / k)
        th = i * golden + rng.random() * 0.3
        x, y = r * math.cos(th), r * math.sin(th)
        pts.append((x, y, math.sqrt(max(0, 1 - x * x - y * y))))
    return np.array(pts)


class Material:
    """painter(ctx) -> Layer, plus how the cube's edges are rounded and how the light lands."""

    def __init__(self, painter, round=1.0, round_y=None, bump=1.0, cav=0.35, spec=0.0, spec_p=24, dither=0.12,
                 lo=0.0, hi=1.0, ao=1.0, amb=0.3, wrap=0.55):
        self.painter = painter
        self.round = round
        self.round_y = round if round_y is None else round_y
        self.bump = bump
        self.cav = cav
        self.spec = spec
        self.spec_p = spec_p
        self.dither = dither
        self.lo, self.hi = lo, hi
        self.ao = ao
        self.amb = amb
        self.wrap = wrap

    def with_(self, **kw):
        m = Material(self.painter)
        m.__dict__.update(self.__dict__)
        m.__dict__.update(kw)
        if "round" in kw and "round_y" not in kw:
            m.round_y = kw["round"]
        return m


class HDModel(Model):
    def __init__(self, name, seed=1, tex_w=256, tex_scale=2, ao_rays=40, ao_dist=7.0, ao_strength=0.7):
        super().__init__(name, seed, tex_w, tex_scale)
        self.ao_rays = ao_rays
        self.ao_dist = ao_dist
        self.ao_strength = ao_strength
        self.rig = Rig(self)

    # boxes of the posed model, for occlusion
    def _boxes(self):
        C, A, Hh = [], [], []
        for cb in self.cubes:
            M = self.rig.cube(cb)
            lo = np.asarray(cb["frm"], float) - cb["inflate"]
            hi = lo + np.asarray(cb["size"], float) + 2 * cb["inflate"]
            c = (M @ np.append((lo + hi) / 2, 1))[:3]
            C.append(c)
            A.append(M[:3, :3])
            Hh.append((hi - lo) / 2)
        return np.array(C), np.array(A), np.array(Hh)

    def _ao(self, origins, normals_t, own):
        """origins (N,3), frames (3,3) = (u_w, v_w, n_w) columns; returns occlusion in [0, 1] per origin."""
        dirs_local = _hemisphere(self.ao_rays)
        u_w, v_w, n_w = normals_t
        D = dirs_local[:, 0:1] * u_w + dirs_local[:, 1:2] * v_w + dirs_local[:, 2:3] * n_w  # (K,3)
        N = origins.shape[0]
        K = D.shape[0]
        o = np.repeat(origins + n_w * 0.04, K, axis=0)
        d = np.tile(D, (N, 1))
        occ = np.zeros(N * K)
        C, A, Hh = self.boxes
        keep = np.ones(len(C), dtype=bool)
        keep[own] = False
        # only boxes that can be reached at all
        reach = np.linalg.norm(C[None, :, :] - origins.mean(0)[None, None, :], axis=-1)[0] < (
            self.ao_dist + np.linalg.norm(Hh, axis=1) + np.ptp(origins, axis=0).max()
        )
        keep &= reach
        C, A, Hh = C[keep], A[keep], Hh[keep]
        if len(C) == 0:
            return np.zeros(N)
        B = 2048
        for s in range(0, N * K, B):
            oo, dd = o[s : s + B], d[s : s + B]
            rel = oo[:, None, :] - C[None]
            ol = np.einsum("nbk,bkj->nbj", rel, A)
            dl = np.einsum("nk,bkj->nbj", dd, A)
            with np.errstate(divide="ignore", invalid="ignore"):
                inv = 1.0 / np.where(np.abs(dl) < 1e-9, 1e-9, dl)
                t1 = (-Hh[None] - ol) * inv
                t2 = (Hh[None] - ol) * inv
            tmin = np.minimum(t1, t2).max(-1)
            tmax = np.maximum(t1, t2).min(-1)
            hit = (tmax >= np.maximum(tmin, 0)) & (tmin < self.ao_dist)
            tt = np.where(hit, np.maximum(tmin, 0), np.inf).min(-1)
            occ[s : s + B] = np.where(np.isfinite(tt), 1 - np.clip(tt / self.ao_dist, 0, 1) ** 0.7, 0)
        return occ.reshape(N, K).mean(1)

    def paint(self):
        self._pack()
        S = self.tex_scale
        self.rig.invalidate()
        self.boxes = self._boxes()
        tex = np.zeros((self.tex_h * S, self.tex_w * S, 4), dtype=np.uint8)
        for ci, cb in enumerate(self.cubes):
            mat = cb["mat"]
            M = self.rig.cube(cb)
            Rw = M[:3, :3]
            for fname, (x, y, w, h) in self.face_rects(cb).items():
                tw, th = w * S, h * S
                img = tex[y * S : y * S + th, x * S : x * S + tw]
                n0, a0, b0 = (np.array(v, float) for v in FACES[fname])
                P, uu, vv = face_points(cb, fname, tw, th)
                ctx = Ctx()
                ctx.face, ctx.cube, ctx.model, ctx.S = fname, cb, self, S
                ctx.P, ctx.n, ctx.a, ctx.b = P, n0, a0, b0
                ctx.fw, ctx.fh = face_size(cb, fname)
                ctx.u, ctx.v = uu * ctx.fw, vv * ctx.fh
                ctx.ii, ctx.jj = np.mgrid[0:th, 0:tw][1], np.mgrid[0:th, 0:tw][0]
                ctx.seed = ci * 31 + 7
                ctx.Pw = (np.c_[P.reshape(-1, 3), np.ones(tw * th)] @ M.T)[:, :3].reshape(th, tw, 3)
                L = mat.painter(ctx)
                for dfn in cb["deco"]:
                    dfn(ctx, L)
                img[...] = self._shade(ctx, L, mat, cb, ci, fname, Rw)
        self.tex = tex
        return tex

    def _shade(self, ctx, L, mat, cb, ci, fname, Rw):
        S = ctx.S
        th, tw = L.H.shape
        n0, a0, b0 = ctx.n, ctx.a, ctx.b
        u, v, fw, fh = ctx.u, ctx.v, ctx.fw, ctx.fh
        N = np.broadcast_to(n0, (th, tw, 3)).astype(float).copy()
        # rounded edges: left/right edges use `round`, top/bottom use `round_y` on side faces
        side = fname in ("north", "south", "east", "west")
        Ru = mat.round
        Rv = mat.round_y if side else mat.round
        for dist, R, dirv in ((u, Ru, -a0), (fw - u, Ru, a0), (v, Rv, -b0), (fh - v, Rv, b0)):
            if R <= 0:
                continue
            t = np.clip(1 - dist / R, 0, 0.96)
            tilt = t / np.sqrt(1 - t * t)
            N += tilt[..., None] * dirv
        # relief
        Hu = np.gradient(L.H, axis=1) * S if tw > 1 else np.zeros_like(L.H)
        Hv = np.gradient(L.H, axis=0) * S if th > 1 else np.zeros_like(L.H)
        N -= mat.bump * (Hu[..., None] * a0 + Hv[..., None] * b0)
        N /= np.linalg.norm(N, axis=-1, keepdims=True)
        ndl = N @ LIGHT
        dif = np.clip((ndl + mat.wrap) / (1 + mat.wrap), 0, 1) ** 1.25
        light = mat.amb + (1 - mat.amb) * dif
        # occlusion, traced at one sample per unit and upsampled
        if mat.ao > 0:
            nu, nv = max(1, int(round(fw))), max(1, int(round(fh)))
            Ps, _, _ = face_points(cb, fname, nu, nv)
            M = self.rig.cube(cb)
            Pws = (np.c_[Ps.reshape(-1, 3), np.ones(nu * nv)] @ M.T)[:, :3]
            frame = (Rw @ a0, Rw @ b0, Rw @ n0)
            occ = self._ao(Pws, frame, ci).reshape(nv, nu)
            if (nu, nv) != (tw, th):
                occ = np.asarray(Image.fromarray(occ.astype(np.float32), "F").resize((tw, th), Image.BILINEAR))
            light = light * (1 - self.ao_strength * mat.ao * occ)
        # cavities of the relief
        if mat.cav and th > 2 and tw > 2:
            lap = (np.roll(L.H, 1, 0) + np.roll(L.H, -1, 0) + np.roll(L.H, 1, 1) + np.roll(L.H, -1, 1) - 4 * L.H) * S * S
            lap[0, :] = lap[-1, :] = 0
            lap[:, 0] = lap[:, -1] = 0
            light = light - mat.cav * np.clip(lap * 0.25, 0, 0.6) + mat.cav * 0.35 * np.clip(-lap * 0.25, 0, 0.4)
        spec = (mat.spec + L.spec) * np.clip(N @ HALF, 0, 1) ** mat.spec_p
        out = np.zeros((th, tw, 4), dtype=np.uint8)
        bay = BAYER[np.arange(th)[:, None] % 4, np.arange(tw)[None, :] % 4]
        for r_i, rp in enumerate(L.ramps):
            sel = L.rid == r_i
            if not sel.any():
                continue
            n = len(rp)
            lo, hi = mat.lo * (n - 1), mat.hi * (n - 1)
            f = lo + np.clip(light, 0, 1.15) * (hi - lo) + L.alb + spec * n * 1.4 + bay * mat.dither
            idx = np.clip(np.floor(f + 0.5), 0, n - 1).astype(int)
            out[sel, :3] = rp[idx[sel]]
        out[..., 3] = np.where(L.alpha, 255, 0)
        if L.emask.any():
            out[L.emask, :3] = np.clip(L.emit[L.emask], 0, 255).astype(np.uint8)
        return out


# ------------------------------------------------------------------------------------------------ decals


def bitmap(rows, palette, face=None, col=0, row=0, flip=False):
    """Pixel art stamped on a face at texel (col, row). palette: char -> fn(L, j, i) or (ramp, alb[, H])."""

    def d(ctx, L):
        if face and ctx.face != face:
            return
        th, tw = L.H.shape
        for r, line in enumerate(rows):
            if flip:
                line = line[::-1]
            for c, ch in enumerate(line):
                if ch in (" ", "."):
                    continue
                j, i = row + r, col + c
                if not (0 <= j < th and 0 <= i < tw):
                    continue
                p = palette[ch]
                if callable(p):
                    p(L, j, i)
                else:
                    L.rid[j, i] = L.use(p[0])
                    L.alb[j, i] = p[1]
                    if len(p) > 2:
                        L.H[j, i] += p[2]

    return d


def emit(rgb):
    def f(L, j, i):
        L.emask[j, i] = True
        L.emit[j, i] = rgb

    return f


def cut(L, j, i):
    L.alpha[j, i] = False
