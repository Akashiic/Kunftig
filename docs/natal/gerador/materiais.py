"""Ramps and materials for the HD engine (pintura.py)."""

import numpy as np

from pintura import Layer, Material, fbm, ramp, smooth, vnoise, worley

# ------------------------------------------------------------------------------------------------- ramps
# dark to light; shadows lean cool/purple, lights lean warm

SKIN = ramp("#1e0e0e", "#3d1d19", "#5e3024", "#7c4632", "#985d43", "#b07656", "#c4906e", "#d6ab8b", "#e7c7ac", "#f4e0cd", n=16)
SCAR = ramp("#4a2023", "#7a3a3b", "#a65d5a", "#c9837b", "#e2aaa0", "#f2cdc4", n=10)
INK = ramp("#0b0d16", "#141a2b", "#1f2a43", "#2c3c5c", "#3b4f73", n=8)
INK_RED = ramp("#2a0507", "#4f0b10", "#7a1218", "#a0201f", "#bd3a2c", n=8)
VEIN = ramp("#2e1818", "#4b2a28", "#6a3f38", "#86564a", "#a06e5c", "#b98a76", n=10)
CLOTH = ramp("#120204", "#24050a", "#3d0a10", "#590f17", "#76161e", "#922026", "#ab2d2e", "#c2443d", "#d76552", n=14)
FUR = ramp("#3b3a40", "#5d5a5e", "#807b7b", "#a29c98", "#bfb9b2", "#d6d1c9", "#e7e3dc", "#f3f1ec", "#fcfbf8", n=16)
SOOT = ramp("#0e0d0f", "#1d1b1e", "#2e2b2e", "#423e3f", "#58534f", n=8)
LEATHER = ramp("#060508", "#0f0d12", "#19161e", "#24202b", "#322c3b", "#433b4f", "#585068", "#746b86", n=12)
GUNMETAL = ramp("#07090c", "#10141a", "#1a2028", "#262e38", "#35404c", "#485563", "#5f6e7d", "#7e8d9b", "#a6b3be", "#d7e0e6", n=14)
STEEL = ramp("#1a1e24", "#2f363e", "#48525c", "#65707b", "#86919b", "#a9b3bb", "#ccd4da", "#eef2f4", n=12)
GOLD = ramp("#2a1503", "#4f2c06", "#7a4a0c", "#a06c12", "#c38f1d", "#ddb032", "#efcd57", "#f9e796", "#fff8d6", n=14)
PAINT_RED = ramp("#1a0406", "#33080c", "#520d13", "#71141b", "#8f1d22", "#aa2b2b", "#c2423a", "#d6624f", n=12)
GREEN = ramp("#030a06", "#08170d", "#0f2616", "#173720", "#20492b", "#2c5e37", "#3b7546", "#508e58", "#6eaa6d", n=12)
OLIVE = ramp("#0d1006", "#1a200c", "#2a3315", "#3b471f", "#4e5c2b", "#64733a", "#7e8d50", "#9eac72", n=12)
BEARD = ramp("#2e2e36", "#4c4c56", "#6c6c76", "#8d8d96", "#acacb4", "#c7c7cd", "#dcdce1", "#ececf0", "#f9f9fb", n=14)
RUBBER = ramp("#050505", "#0d0d0e", "#171718", "#232325", "#303033", "#3f3f43", n=8)
CIGAR = ramp("#120804", "#24120a", "#3a1f10", "#55301a", "#704426", "#8c5a34", "#a8744a", n=10)
ASH = ramp("#3a3836", "#5e5a56", "#85807a", "#aaa59e", "#cfcbc4", n=8)
WHITE_ENAMEL = ramp("#5e5c62", "#86848a", "#aeacb1", "#cfcdd1", "#e8e6e9", "#f8f7f8", n=10)
RED_ENAMEL = ramp("#2a0508", "#520a10", "#7e1018", "#a61a20", "#c62a2c", "#dd4a42", "#ee7563", n=10)
LENS = ramp("#020203", "#06070a", "#0c0e14", "#151a24", "#222a3a", "#34405a", n=8)
HEAT = ramp("#121419", "#20242e", "#323848", "#4a4c5a", "#6e6452", "#94825a", "#b39d6a", n=10)

GLOW_RED = (255, 40, 24)
GLOW_RED_HOT = (255, 196, 160)
GLOW_RED_DIM = (176, 18, 12)
EMBER = (255, 120, 30)
EMBER_HOT = (255, 226, 140)


def near_edge(ctx, d):
    """Distance in units from the face's border."""
    return np.minimum(np.minimum(ctx.u, ctx.fw - ctx.u), np.minimum(ctx.v, ctx.fh - ctx.v)) < d


def edge_dist(ctx):
    return np.minimum(np.minimum(ctx.u, ctx.fw - ctx.u), np.minimum(ctx.v, ctx.fh - ctx.v))


# --------------------------------------------------------------------------------------------- anatomy
# Height maps (units) for the torso and limbs, as functions of the rest-pose point; the model sets where.


def dome(x, y, cx, cy, rx, ry, amp, p=2.0, shape=None):
    """A muscle mass. shape=None: soft edges (smoothstep); a number < 1 gives a hard rim (a shelf)."""
    d = (np.abs((x - cx) / rx) ** p + np.abs((y - cy) / ry) ** p) ** (2 / p)
    t = np.clip(1 - d, 0, 1)
    if shape is None:
        return amp * t * t * (3 - 2 * t)
    return amp * t ** shape


def groove(x, y, x0, y0, x1, y1, width, depth):
    """A straight furrow from (x0,y0) to (x1,y1)."""
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy
    t = np.clip(((x - x0) * dx + (y - y0) * dy) / L2, 0, 1)
    d = np.hypot(x - (x0 + t * dx), y - (y0 + t * dy))
    return -depth * np.clip(1 - d / width, 0, 1) ** 1.5


def seg_dist(P2, a, b):
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    ab = b - a
    t = np.clip(((P2 - a) * ab).sum(-1) / (ab @ ab), 0, 1)
    return np.linalg.norm(P2 - (a + t[..., None] * ab), axis=-1), t


def torso_front(x, y):
    h = np.zeros_like(x)
    # pecs: two heavy squared domes with a hard lower shelf
    for s in (1, -1):
        h += dome(x, y, s * 7.0, 84.2, 7.4, 4.9, 1.25, p=2.6, shape=0.45)
        h += dome(x, y, s * 10.5, 86.0, 3.5, 2.6, 0.35, p=2.0)  # upper outer pec into the delt
    h += groove(x, y, 0, 89, 0, 61, 0.9, 0.55)  # linea alba
    # abs: four rows
    for row, cy in enumerate((76.0, 72.4, 68.8, 65.0)):
        for s in (1, -1):
            h += dome(x, y, s * 2.75, cy, 2.45 - row * 0.08, 1.55, 0.7, p=2.8, shape=0.5)
    # serratus fingers on the ribs
    for s in (1, -1):
        for k, cy in enumerate((78.4, 76.2, 74.0)):
            h += dome(x, y, s * (8.8 + k * 0.35), cy, 1.5, 0.9, 0.45, p=2.0, shape=0.7)
        # obliques, and the V into the belt
        h += dome(x, y, s * 6.9, 66.5, 1.6, 4.8, 0.55, p=2.2)
        h += groove(x, y, s * 6.0, 63.5, s * 2.2, 59.0, 0.8, 0.5)
    return h


def torso_back(x, y):
    h = np.zeros_like(x)
    h += groove(x, y, 0, 96, 0, 58, 1.0, 0.65)  # spine
    for s in (1, -1):
        h += dome(x, y, s * 2.6, 66.0, 1.8, 6.5, 0.7, p=2.2)  # erectors
        h += dome(x, y, s * 9.5, 78.5, 6.5, 8.5, 1.3, p=2.0)  # lats
        h += dome(x, y, s * 6.0, 84.0, 3.6, 3.0, 0.55)  # teres / rhomboids
        h += dome(x, y, s * 11.0, 86.5, 3.5, 3.0, 0.45)  # rear delt tie-in
    h += dome(x, y, 0, 89.0, 7.5, 5.5, 0.75, p=1.6)  # traps diamond
    return h


def torso_height(ctx):
    x, y, z = ctx.P[..., 0], ctx.P[..., 1], ctx.P[..., 2]
    if ctx.face == "north":
        return torso_front(x, y)
    if ctx.face == "south":
        return torso_back(x, y)
    if ctx.face in ("east", "west"):
        # lats flaring on the sides, serratus at the front edge
        h = dome(z, y, 1.0, 78.0, 5.0, 8.0, 0.8)
        for k, cy in enumerate((78.4, 76.2, 74.0)):
            h += dome(z, y, -4.0, cy, 1.2, 0.9, 0.35)
        return h
    return np.zeros_like(x)


def limb_height(kind):
    """Bicep/tricep/delt/forearm relief, expressed on the cube's own faces."""

    def f(ctx):
        u, v, fw, fh = ctx.u, ctx.v, ctx.fw, ctx.fh
        h = np.zeros_like(u)
        cu = fw / 2
        if kind == "delt":
            # three heads: front, side, rear, with striations
            h += dome(u, v, cu, fh * 0.45, fw * 0.48, fh * 0.55, 0.9, p=2.0)
            h += 0.05 * np.sin(u * 3.1) * smooth(0, fh * 0.3, v) * (1 - smooth(fh * 0.6, fh, v))
            if ctx.face in ("east", "west", "north", "south"):
                h += groove(u, v, cu * 0.5, fh * 0.15, cu * 0.8, fh * 0.95, 0.7, 0.3)
        elif kind == "bicep":
            if ctx.face == "north":
                h += dome(u, v, cu, fh * 0.45, fw * 0.46, fh * 0.48, 1.3, p=2.0)
                h += groove(u, v, cu, fh * 0.1, cu, fh * 0.8, 0.6, 0.35)
            elif ctx.face == "south":
                # horseshoe tricep
                h += dome(u, v, cu, fh * 0.35, fw * 0.45, fh * 0.4, 1.0)
                h += groove(u, v, cu * 0.55, fh * 0.6, cu, fh * 0.95, 0.8, 0.4)
                h += groove(u, v, cu * 1.45, fh * 0.6, cu, fh * 0.95, 0.8, 0.4)
            else:
                h += dome(u, v, fw * 0.4, fh * 0.45, fw * 0.5, fh * 0.5, 0.6)
                h += groove(u, v, fw * 0.55, fh * 0.05, fw * 0.5, fh, 0.6, 0.35)
        elif kind == "forearm":
            h += dome(u, v, cu, fh * 0.3, fw * 0.5, fh * 0.45, 0.6)
        return h

    return f


# ------------------------------------------------------------------------------------------- materials


def skin(height=None, veins=0.0, scars=(), freckle=0.6, **kw):
    """height: fn(ctx) -> relief. scars: list of (face, (x0,y0,z0), (x1,y1,z1), width) in rest coords."""

    def p(ctx):
        L = Layer(ctx.P.shape[:2], [SKIN])
        P = ctx.P
        if height is not None:
            L.H += height(ctx)
        L.alb += (fbm(P, 0.22, 3, ctx.seed) - 0.5) * 1.0
        L.alb += (vnoise(P, 1.6, ctx.seed + 9) - 0.5) * 0.35 * freckle
        L.H += (vnoise(P, 2.0, ctx.seed + 3) - 0.5) * 0.04
        if veins:
            f = fbm(P, (0.3, 0.14, 0.3), 2, ctx.seed + 17)
            r = np.clip(1 - np.abs(f - 0.5) / 0.022, 0, 1)
            g = fbm(P, (0.35, 0.2, 0.35), 2, ctx.seed + 21)
            r = r * smooth(0.55, 0.65, g)
            L.H += r * 0.35 * veins
            L.alb -= r * 0.8 * veins
        for face, a, b, wdt in scars:
            if face != ctx.face:
                continue
            ax = (0, 1) if face in ("north", "south") else (2, 1)
            d, t = seg_dist(P[..., list(ax)], [a[ax[0]], a[ax[1]]], [b[ax[0]], b[ax[1]]])
            core = d < wdt
            L.rid[core] = L.use(SCAR)
            L.alb[core] = 1.0
            L.H += np.where(core, 0.25, 0) - np.clip(1 - d / (wdt * 2.5), 0, 1) * 0.15
            # stitch marks across the scar
            st = core & (np.abs(((t * 40) % 3) - 1.5) < 0.35)
            L.alb[st] = -1.0
        return L

    return Material(p, **kw)


def cloth(base=None, weave=0.07, folds=0.6, dirt=0.8, stripe=None, seams=True, holes=0, **kw):
    """stripe: fn(ctx) -> bool mask painted as fur trim (Santa's white band)."""
    base = CLOTH if base is None else base

    def p(ctx):
        L = Layer(ctx.P.shape[:2], [base])
        P = ctx.P
        # twill weave
        tw = ((ctx.ii + ctx.jj) % 4 < 2).astype(float) - 0.5
        L.H += tw * weave * 0.5
        L.alb += tw * 0.12
        # folds: horizontal wrinkles, stronger at the bottom of the cube (bunching)
        f = fbm(P, (0.12, 0.55, 0.12), 3, ctx.seed + 5)
        bunch = 0.5 + 0.8 * (1 - ctx.v / max(ctx.fh, 1e-6)) if ctx.face not in ("up", "down") else 1
        L.H += (f - 0.5) * folds * 2.2 * (1.6 - bunch * 0.6)
        L.alb += (fbm(P, 0.15, 3, ctx.seed + 8) - 0.5) * 1.0
        # wear: lighter fraying along edges, grime toward the ground
        e = edge_dist(ctx)
        fray = (e < 0.3) & (vnoise(P, 2.0, ctx.seed + 4) > 0.55)
        L.alb[fray] += 1.2
        y = P[..., 1]
        L.alb -= dirt * np.clip((30 - y) / 30, 0, 1) * (1.0 + 2 * fbm(P, 0.4, 2, ctx.seed + 11))
        if seams and ctx.face in ("east", "west"):
            s = np.abs(ctx.u - ctx.fw / 2) < 0.28
            L.H[s] -= 0.3
            L.alb[s & ((ctx.jj % 3) == 0)] += 2.0
        if holes:
            w1, _, _ = worley(P, 0.25, ctx.seed + 30)
            hole = (w1 < 0.12) & (fbm(P, 0.3, 1, ctx.seed + 31) > 1 - holes)
            ring = (w1 < 0.2) & ~hole & (fbm(P, 0.3, 1, ctx.seed + 31) > 1 - holes)
            L.rid[hole] = L.use(SOOT)
            L.alb[hole] = -3
            L.H[hole] -= 0.5
            L.rid[ring] = L.use(SOOT)
            L.alb[ring] = 1
        if stripe is not None:
            m = stripe(ctx)
            if m.any():
                fl = fur_layer(ctx, 0.5)
                for k in ("H", "alb"):
                    getattr(L, k)[m] = getattr(fl, k)[m]
                L.rid[m] = L.use(FUR)
        return L

    return Material(p, **kw)


def fur_layer(ctx, dirt):
    L = Layer(ctx.P.shape[:2], [FUR])
    P = ctx.P
    f1, f2, cid = worley(P, (0.6, 0.42, 0.6), ctx.seed + 40)
    clump = np.clip(1 - f1, 0, 1)
    L.H += clump ** 1.3 * 1.1
    L.alb += (cid - 0.5) * 0.6
    edge = (f2 - f1) < 0.1
    L.alb[edge] -= 1.0
    L.H += (vnoise(P, (2.2, 0.7, 2.2), ctx.seed + 41) - 0.5) * 0.18
    L.alb += (vnoise(P, (1.4, 0.6, 1.4), ctx.seed + 42) - 0.5) * 0.5
    g = fbm(P, 0.22, 3, ctx.seed + 43)
    L.alb -= dirt * np.clip(g - 0.5, 0, 1) * 7
    if dirt > 0.2:
        soot = g > 0.8
        L.rid[soot] = L.use(SOOT)
    return L


def fur(dirt=0.6, **kw):
    def p(ctx):
        return fur_layer(ctx, dirt)

    kw.setdefault("round", 1.4)
    kw.setdefault("cav", 0.5)
    kw.setdefault("dither", 0.3)
    return Material(p, **kw)


def leather(base=None, stitch=True, creases=0.5, scuff=1.0, laces=False, **kw):
    base = LEATHER if base is None else base

    def p(ctx):
        L = Layer(ctx.P.shape[:2], [base])
        P = ctx.P
        cr = fbm(P, (0.5, 0.25, 0.5), 3, ctx.seed + 50)
        L.H += (1 - np.abs(cr - 0.5) * 2) ** 6 * 0.35 * creases
        L.alb += (fbm(P, 0.25, 2, ctx.seed + 51) - 0.5) * 1.4
        sc = vnoise(P, 0.7, ctx.seed + 52)
        L.alb += np.clip(sc - 0.7, 0, 1) * scuff * 4
        if stitch and ctx.face not in ("up", "down"):
            e = edge_dist(ctx)
            s = (np.abs(e - 0.6) < 0.25) & (((ctx.ii + ctx.jj) % 3) == 0)
            L.alb[s] += 3.0
            L.H[s] += 0.1
        if laces and ctx.face == "north":
            u, v, fw = ctx.u, ctx.v, ctx.fw
            band = np.abs(u - fw / 2) < fw * 0.28
            zig = np.abs(((v * 1.4) % 2) - 1) * fw * 0.28
            lace = band & (np.abs(np.abs(u - fw / 2) - zig) < 0.35)
            L.rid[lace] = L.use(STEEL)
            L.alb[lace] = -2
            L.H[lace] += 0.25
            eyelet = (np.abs(np.abs(u - fw / 2) - fw * 0.3) < 0.3) & ((np.floor(v * 1.4) % 2) == 0) & (
                ((v * 1.4) % 1) < 0.35)
            L.rid[eyelet] = L.use(GOLD)
            L.alb[eyelet] = 0
        return L

    kw.setdefault("round", 0.9)
    kw.setdefault("spec", 0.18)
    kw.setdefault("spec_p", 16)
    return Material(p, **kw)


def metal(base=None, rivets=0.0, panels=None, scratches=1.0, chip=None, heat=None, grime=0.6, engrave=None, **kw):
    """panels: list of ('u'|'v', position in units) grooves; chip: ramp of the paint over the metal (paint chips
    off at the edges, showing `base`); heat: fn(ctx) -> 0..1, tints toward blue/straw and soot."""
    base = GUNMETAL if base is None else base

    def p(ctx):
        top = chip if chip is not None else base
        L = Layer(ctx.P.shape[:2], [top])
        P = ctx.P
        L.alb += (fbm(P, 0.3, 3, ctx.seed + 60) - 0.5) * 1.2
        # brushed streaks
        L.alb += (vnoise(P, (0.15, 1.6, 0.15) if ctx.face in ("east", "west", "north", "south") else (1.6, 0.15, 0.15),
                         ctx.seed + 61) - 0.5) * 0.5
        if scratches:
            s = fbm(P, (2.5, 0.35, 2.5), 2, ctx.seed + 62)
            line = (np.abs(s - 0.5) < 0.006 * scratches) & (vnoise(P, 0.4, ctx.seed + 65) > 0.55)
            L.alb[line] += 1.3
        if panels:
            for axis, pos in panels:
                c = ctx.u if axis == "u" else ctx.v
                g = np.abs(c - pos) < 0.3
                L.H[g] -= 0.35
                L.alb[g] -= 1.0
        if rivets and ctx.face not in ("down",) and min(ctx.fw, ctx.fh) >= 3:
            u, v, fw, fh = ctx.u, ctx.v, ctx.fw, ctx.fh
            ins = 0.8
            near_u = (np.abs(u - ins) < 0.3) | (np.abs(fw - ins - u) < 0.3)
            near_v = (np.abs(v - ins) < 0.3) | (np.abs(fh - ins - v) < 0.3)
            on_v = near_u & (np.abs(((v - ins) % rivets) - 0) < 0.3) & (v > ins - 0.3) & (v < fh - ins + 0.3)
            on_u = near_v & (np.abs(((u - ins) % rivets) - 0) < 0.3) & (u > ins - 0.3) & (u < fw - ins + 0.3)
            rv = on_u | on_v
            L.H[rv] += 0.3
            L.alb[rv] += 2.0
        if chip is not None:
            e = edge_dist(ctx)
            n = fbm(P, 0.6, 3, ctx.seed + 63)
            score = n + np.clip(1 - e / 0.7, 0, 1) * 0.35
            chipped = score > 0.86
            L.rid[chipped] = L.use(base)
            L.alb[chipped] -= 3.0
            rim = (score > 0.76) & ~chipped
            L.alb[rim] -= 1.5
        L.alb -= grime * np.clip(fbm(P, 0.45, 2, ctx.seed + 64) - 0.55, 0, 1) * 6
        if heat is not None:
            hv = heat(ctx)
            hsel = hv > 0.25
            L.rid[hsel] = L.use(HEAT)
            L.alb[hsel] += (0.6 - hv[hsel]) * 2.5
            soot = hv > 0.9
            L.rid[soot] = L.use(SOOT)
        if engrave is not None:
            engrave(ctx, L)
        return L

    kw.setdefault("round", 0.45)
    kw.setdefault("spec", 0.55)
    kw.setdefault("spec_p", 28)
    kw.setdefault("cav", 0.6)
    kw.setdefault("dither", 0.12)
    return Material(p, **kw)


def hair(base=None, braid=False, roots=True, flow=0.0, **kw):
    """Combed hair: ridges of strands about 1.2 units apart, curving outward by `flow` as they go down, broken
    into a few big clumps."""
    base = BEARD if base is None else base

    def p(ctx):
        L = Layer(ctx.P.shape[:2], [base])
        P = ctx.P
        x, y, z = P[..., 0], P[..., 1], P[..., 2]
        across = x if ctx.face in ("north", "south", "up", "down") else z
        top = P[..., 1].max()
        dy = np.clip(top - y, 0, None)
        s_ = across + flow * np.sign(across) * dy * dy * 0.006 + (fbm(P, 0.2, 2, ctx.seed + 73) - 0.5) * 1.0
        ridge = 0.5 + 0.5 * np.cos(s_ * 2 * np.pi / 1.8)
        L.H += ridge * 0.35
        L.alb += (ridge - 0.5) * 1.2
        f1, f2, cid = worley(P, (0.35, 0.18, 0.35), ctx.seed + 70)
        L.H += np.clip(1 - f1, 0, 1) * 0.9
        L.alb += (cid - 0.5) * 0.7
        L.alb[(f2 - f1) < 0.06] -= 1.4
        if roots and ctx.face not in ("up", "down"):
            L.alb -= np.clip(1.2 - ctx.v, 0, 1.2) * 1.5
        if braid:
            u, v, fw = ctx.u, ctx.v, ctx.fw
            k = (v * 1.15 + np.abs(u - fw / 2) * 0.9) % 1.0
            L.H += np.abs(k - 0.5) * 1.4
            L.alb += (np.abs(k - 0.5) - 0.25) * 4
        return L

    kw.setdefault("round", 1.0)
    kw.setdefault("cav", 0.55)
    kw.setdefault("dither", 0.08)
    return Material(p, **kw)


def rubber(tread=True, **kw):
    def p(ctx):
        L = Layer(ctx.P.shape[:2], [RUBBER])
        P = ctx.P
        L.alb += (fbm(P, 0.6, 2, ctx.seed + 80) - 0.5) * 1.2
        if ctx.face == "down" and tread:
            k = ((ctx.u + ctx.v * 0.6) % 2.0) < 0.9
            L.H[k] += 0.4
        elif ctx.face != "up":
            g = (ctx.v % 1.0) < 0.3
            L.H[g] -= 0.2
            L.alb[(ctx.ii % 4 == 0) & (ctx.v > ctx.fh * 0.4)] -= 1
        return L

    kw.setdefault("round", 0.4)
    return Material(p, **kw)


def flat(rp, alb_noise=1.0, **kw):
    def p(ctx):
        L = Layer(ctx.P.shape[:2], [rp])
        L.alb += (fbm(ctx.P, 0.5, 2, ctx.seed) - 0.5) * alb_noise
        return L

    return Material(p, **kw)


def glow(rgb_core, rgb_rim=None):
    def p(ctx):
        L = Layer(ctx.P.shape[:2], [STEEL])
        L.emask[:] = True
        L.emit[:] = rgb_core
        if rgb_rim is not None:
            e = edge_dist(ctx) < 0.5
            L.emit[e] = rgb_rim
        return L

    return Material(p, ao=0, round=0, cav=0)


def wrap_paper(base=None, ribbon_x=None, ribbon_z=None, ribbon_y=None, rw=1.2, **kw):
    """Present wrapping with a gold ribbon band through the given planes (rest coords)."""
    base = GREEN if base is None else base

    def p(ctx):
        L = Layer(ctx.P.shape[:2], [base])
        P = ctx.P
        L.alb += (fbm(P, 0.3, 2, ctx.seed + 90) - 0.5) * 1.0
        # a diamond pattern printed on the paper
        dm = ((np.abs((P[..., 0] + P[..., 2]) % 3 - 1.5) + np.abs(P[..., 1] % 3 - 1.5)) < 0.55)
        L.alb[dm] += 1.5
        band = np.zeros(L.H.shape, dtype=bool)
        for axis, c in ((0, ribbon_x), (2, ribbon_z), (1, ribbon_y)):
            if c is not None:
                band |= np.abs(P[..., axis] - c) < rw
        L.rid[band] = L.use(GOLD)
        L.alb[band] = (vnoise(P, (0.3, 2, 0.3), ctx.seed + 91)[band] - 0.5) * 2
        L.spec[band] = 0.5
        L.H[band] += 0.25
        return L

    kw.setdefault("round", 0.6)
    kw.setdefault("spec", 0.15)
    return Material(p, **kw)


def candy(period=2.4, **kw):
    """Red and white enamel stripes running diagonally, chipped to steel at the edges."""

    def p(ctx):
        L = Layer(ctx.P.shape[:2], [WHITE_ENAMEL])
        P = ctx.P
        k = ((P[..., 0] * 0.7 + P[..., 1] + P[..., 2] * 0.7) % period) < period / 2
        L.rid[k] = L.use(RED_ENAMEL)
        L.alb += (fbm(P, 0.5, 2, ctx.seed + 95) - 0.5) * 1.0
        e = edge_dist(ctx)
        chipped = (fbm(P, 0.8, 2, ctx.seed + 96) + np.clip(1 - e / 1.2, 0, 1) * 0.4) > 0.8
        L.rid[chipped] = L.use(STEEL)
        return L

    kw.setdefault("round", 0.5)
    kw.setdefault("spec", 0.45)
    kw.setdefault("spec_p", 30)
    return Material(p, **kw)
