"""Noel do Arsenal: the classic brawny Santa (open red coat with white fur, bare chest, grey beard, big brown gloves)
at about eight blocks, dark sunglasses, a rotary machine gun in his right hand and, over his left shoulder, a big
sack of presents holding his arsenal: a rocket launcher, a flamethrower, a proton laser and another machine gun.

Textures at the usual Minecraft density (1 pixel per unit), shaded and occluded by the HD engine, kept clean.
"""

import numpy as np

import vtaper
from materiais import *
from pintura import HDModel, Material, bitmap, euler, ramp, to_euler

IDENT = "noel_arsenal"
X = vtaper.X

# ------------------------------------------------------------------------------------------------- ramps

COAT = ramp("#2a050b", "#4d0913", "#740f1b", "#981722", "#b81e29", "#cf2c32", "#e14741", "#ee6a59", n=12)
BROWN = ramp("#140a05", "#28160c", "#3d2313", "#54321c", "#6b4226", "#845532", "#9d6a42", n=10)
BELT_BROWN = ramp("#0b0605", "#1a0f0a", "#2a1810", "#3b2318", "#4e3022", "#653f2d", n=8)
BURLAP = ramp("#2a1b0e", "#433019", "#5d4524", "#775b31", "#91723f", "#a88a52", "#bea169", "#d2b984", n=10)
CYAN_RING = (90, 240, 255)
CYAN_CORE = (220, 255, 255)
FLAME = (255, 150, 40)
FLAME_HOT = (255, 236, 170)

# ------------------------------------------------------------------------------------------------- materials
# at one pixel per unit the weave would read as stripes, so it is off; the noise is a gentle per-pixel variation


def coat(**kw):
    kw.setdefault("round", 2.2)
    kw.setdefault("round_y", 0.8)
    kw.setdefault("dither", 0.0)
    return cloth(base=COAT, weave=0.0, folds=0.25, dirt=0.0, seams=False, **kw)


M_COAT = coat()
M_COAT_SLEEVE = coat(round=3.5, round_y=0.8)
M_COAT_SHOULDER = coat(round=4.5, round_y=3.0)
M_PANTS = coat(round=3.0, round_y=0.6)
M_FUR = fur(dirt=0.08, round=1.4, dither=0.0)
M_FUR_SOFT = fur(dirt=0.06, round=2.2, round_y=1.2, dither=0.0)
M_GLOVE = leather(base=BROWN, stitch=False, creases=0.3, scuff=0.2, round=2.4, round_y=1.8)
M_BOOT = leather(base=BROWN, stitch=False, creases=0.4, scuff=0.2, round=2.0, round_y=1.0)
M_SOLE = flat(BELT_BROWN, round=0.6)
M_BELT = leather(base=BELT_BROWN, stitch=False, creases=0.2, scuff=0.1, round=0.8, round_y=0.5)
M_GOLD = metal(base=GOLD, round=0.6, spec=0.7, grime=0.0, scratches=0.0)
M_HEAD = skin(round=2.6, round_y=1.2, freckle=0.4)
M_NOSE = skin(round=1.3, round_y=1.0)
M_BEARD = fur(dirt=0.0, round=1.6, round_y=0.8, dither=0.0)
M_BROW = fur(dirt=0.0, round=0.6, round_y=0.5, dither=0.0, lo=0.0, hi=0.75)
M_SHADES = metal(base=LENS, round=0.3, spec=0.9, spec_p=30, scratches=0.0, grime=0.0)
M_SHADES_FRAME = metal(base=GUNMETAL, round=0.3, spec=0.5, scratches=0.0, grime=0.0)
M_SACK = cloth(base=BURLAP, weave=0.0, folds=0.7, dirt=0.0, seams=False, round=4.0, round_y=3.0, dither=0.0)
M_SACK_EDGE = cloth(base=BURLAP, weave=0.0, folds=0.5, dirt=0.0, seams=False, round=1.5, round_y=1.0, dither=0.0)
M_ROPE = metal(base=GOLD, round=0.6, spec=0.3, grime=0.0, scratches=0.0)
M_OLIVE = metal(base=OLIVE, round=1.0, spec=0.25, grime=0.0, scratches=0.0)
M_STEEL = metal(base=STEEL, round=0.6, spec=0.6, grime=0.0, scratches=0.0)
M_DARK = metal(base=LEATHER, round=0.5, spec=0.3, grime=0.0, scratches=0.0)
M_RED_ENAMEL = metal(base=RED_ENAMEL, round=1.0, spec=0.6, grime=0.0, scratches=0.0)
M_WHITE_ENAMEL = metal(base=WHITE_ENAMEL, round=1.0, spec=0.5, grime=0.0, scratches=0.0)
M_CANDY = candy(period=4.0)


def torso_coat():
    """Coat on the sides and back; open at the front over the bare chest, the opening widening to the chest."""
    sk = skin(height=torso_height, freckle=0.3)
    cl = coat()

    def half_width(y):
        return np.interp(y, [58, 70, 89], [3.5, 5.0, 7.5])

    def p(ctx):
        L = cl.painter(ctx)
        if ctx.face != "north":
            return L
        x, y = ctx.P[..., 0], ctx.P[..., 1]
        m = np.abs(x) < half_width(y)
        if m.any():
            S = sk.painter(ctx)
            L.H[m] = S.H[m]
            L.alb[m] = S.alb[m]
            L.rid[m] = L.use(SKIN)
            hairm = m & (y > 83) & (np.abs(x) < 4.5) & (vnoise(ctx.P, 0.9, ctx.seed + 5) > 0.42 + (89 - y) * 0.05)
            L.rid[hairm] = L.use(BEARD)
            L.alb[hairm] = 0
        return L

    return Material(p, round=2.2, round_y=0.6, bump=1.8, cav=0.7, dither=0.0)


M_TORSO = torso_coat()

# --------------------------------------------------------------------------------------------- decorations

MOUTH = [  # a frown under the moustache, beard front (13 wide)
    ".............",
    "....ddddd....",
    "...d.....d...",
]
MOUTH_PAL = {"d": (BEARD, -5.0)}

SHADES = [  # 13 x 2: two dark lenses, a glint on each
    "fLwLLLfLwLLLf",
    "fLLLLffLLLLLf",
]
SHADES_PAL = {"L": (LENS, 0.0), "w": (LENS, 7.0), "f": (GUNMETAL, 3.0)}


def face_paint(ctx, L):
    """Rosy cheeks, frown lines, white hair at the back and sides under the hat."""
    x, y, z = ctx.P[..., 0], ctx.P[..., 1], ctx.P[..., 2]
    if ctx.face == "north":
        for cx in (-3.5, 3.5):
            ch = (np.abs(x - cx) < 1.0) & (np.abs(y - 102.5) < 0.6)
            L.rid[ch] = L.use(SCAR)
            L.alb[ch] = 1.0
        w = (np.abs(x) < 0.6) & (y > 106) & (y < 107)
        L.alb[w] -= 1.5
    elif ctx.face in ("east", "west"):
        m = (z > 0.5 + (y - 104) * 0.3) | (y < 103.0)
        L.rid[m] = L.use(BEARD)
        L.alb[m] += 1.0
    elif ctx.face == "south":
        L.rid[:] = L.use(BEARD)
        L.alb += 1.0


def ragged1(ctx, L):
    """Beard tips: at one pixel per unit, at most one pixel cut from the bottom edge."""
    if ctx.face not in ("north", "south", "east", "west"):
        return
    th, tw = L.H.shape
    rng = np.random.default_rng(ctx.seed)
    cut = rng.random(tw) < 0.45
    L.alpha[th - 1, cut] = False


def buckle(ctx, L):
    if ctx.face != "north":
        return
    hole = (np.abs(ctx.u - ctx.fw / 2) < 1.0) & (np.abs(ctx.v - ctx.fh / 2) < 1.0)
    L.rid[hole] = L.use(BELT_BROWN)
    L.alb[hole] = 0


def glove_fingers(ctx, L):
    if ctx.face == "north":
        k = np.floor(ctx.u) % 3
        gap = (k == 2) & (ctx.v > ctx.fh * 0.3)
        L.alb[gap] -= 2.0


def gauge(ctx, L):
    if ctx.face != "north":
        return
    d = np.hypot(ctx.u - ctx.fw / 2, ctx.v - ctx.fh * 0.3)
    L.rid[d < 1.3] = L.use(WHITE_ENAMEL)
    L.alb[d < 1.3] = 3
    L.rid[(d < 0.6)] = L.use(RED_ENAMEL)


def glow_rings(spacing, offset=0.0):
    def d(ctx, L):
        if ctx.face in ("up", "down"):
            return
        ring = ((ctx.v + offset) % spacing) < 1.0
        L.emask[ring] = True
        L.emit[ring] = CYAN_RING

    return d


def glow_face(rgb, face="up"):
    def d(ctx, L):
        if ctx.face != face:
            return
        L.emask[:] = True
        L.emit[:] = rgb

    return d


def sack_patch(ctx, L):
    if ctx.face != "south":
        return
    cu, cv = ctx.fw * 0.68, ctx.fh * 0.55
    inside = (np.abs(ctx.u - cu) < 3.0) & (np.abs(ctx.v - cv) < 2.5)
    L.alb[inside] -= 2.0
    rim = inside & ((np.abs(np.abs(ctx.u - cu) - 2.5) < 0.5) | (np.abs(np.abs(ctx.v - cv) - 2.0) < 0.5))
    st = rim & (((ctx.ii + ctx.jj) % 2) == 0)
    L.rid[st] = L.use(GOLD)
    L.alb[st] = 1


def mouth_dark(ctx, L):
    if ctx.face == "up":
        inner = edge_dist(ctx) > 1.0
        L.alb[inner] = -6


# --------------------------------------------------------------------------------------------- weapons
# each one along +Y from its base inside the sack, in its own bone, tilted out of the mouth


def rpg(m, bone, bx, by, bz):
    m.cube(bone, (bx - 1.5, by, bz - 1.5), (3, 30, 3), M_OLIVE, name="rpg_tubo")
    m.cube(bone, (bx - 2, by + 4, bz - 2), (4, 2, 4), M_OLIVE, name="rpg_anel_a")
    m.cube(bone, (bx - 2, by + 22, bz - 2), (4, 2, 4), M_OLIVE, name="rpg_anel_b")
    m.cube(bone, (bx + 1.5, by + 16, bz - 0.5), (2, 3, 1), M_DARK, name="rpg_mira")
    wy = by + 30
    m.cube(bone, (bx - 1, wy, bz - 1), (2, 2, 2), M_STEEL, name="rpg_haste")
    m.cube(bone, (bx - 3, wy + 2, bz - 3), (6, 5, 6), M_RED_ENAMEL, name="rpg_ogiva_a")
    m.cube(bone, (bx - 3, wy + 4, bz - 3), (6, 1, 6), M_GOLD, inflate=0.1, name="rpg_ogiva_faixa")
    m.cube(bone, (bx - 2, wy + 7, bz - 2), (4, 3, 4), M_RED_ENAMEL, name="rpg_ogiva_b")
    m.cube(bone, (bx - 1, wy + 10, bz - 1), (2, 2, 2), M_RED_ENAMEL, name="rpg_ogiva_c")
    m.cube(bone, (bx - 0.5, wy + 12, bz - 0.5), (1, 2, 1), M_GOLD, name="rpg_ponta")
    for k, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        m.cube(bone, (bx - 0.5 + dx * 3, wy + 1, bz - 0.5 + dz * 3), (1, 3, 1), M_STEEL, name="rpg_aleta_%d" % k)


def flamethrower(m, bone, bx, by, bz):
    m.cube(bone, (bx - 1, by, bz - 1), (2, 28, 2), M_DARK, name="lanca_tubo")
    m.cube(bone, (bx - 1.5, by + 26, bz - 1.5), (3, 3, 3), M_STEEL, [glow_face(FLAME)], name="lanca_bocal")
    m.cube(bone, (bx - 0.5, by + 29, bz - 0.5), (1, 2, 1), glow(FLAME_HOT, FLAME), name="lanca_chama")
    m.cube(bone, (bx + 1, by + 6, bz - 3), (6, 13, 6), M_RED_ENAMEL, [gauge], name="lanca_tanque")
    m.cube(bone, (bx + 2, by + 19, bz - 2), (4, 1, 4), M_STEEL, name="lanca_tampa")
    m.cube(bone, (bx + 3, by + 20, bz - 1), (2, 2, 2), M_STEEL, name="lanca_valvula")
    for yy in (9, 15):
        m.cube(bone, (bx - 1.2, by + yy, bz - 3.2), (8, 1, 6), M_BELT, inflate=0.1, name="lanca_cinta_%d" % yy)


def proton_laser(m, bone, bx, by, bz):
    m.cube(bone, (bx - 2, by, bz - 2), (4, 22, 4), M_WHITE_ENAMEL, [glow_rings(5.0, 2.0)], name="laser_corpo")
    m.cube(bone, (bx - 3, by + 22, bz - 3), (6, 4, 6), M_WHITE_ENAMEL, name="laser_cabeca")
    m.cube(bone, (bx - 2, by + 26, bz - 2), (4, 2, 4), M_DARK, [glow_face(CYAN_CORE)], name="laser_emissor")
    m.cube(bone, (bx - 1, by + 28, bz - 1), (2, 2, 2), glow(CYAN_CORE, CYAN_RING), name="laser_nucleo")
    m.cube(bone, (bx + 2, by + 10, bz - 1), (3, 8, 2), M_DARK, [glow_rings(3.0)], name="laser_bobina")


def spare_minigun(m, bone, bx, by, bz):
    m.cube(bone, (bx - 4, by, bz - 4), (8, 7, 8), M_CANDY, name="reserva_luva")
    for k in range(6):
        a = np.radians(30 + k * 60)
        ox, oz = 2.6 * np.cos(a), 2.6 * np.sin(a)
        m.cube(bone, (bx + ox - 0.5, by + 6, bz + oz - 0.5), (1, 18, 1), M_STEEL, name="reserva_cano_%d" % k)
    m.cube(bone, (bx - 3.5, by + 21, bz - 3.5), (7, 2, 7), M_STEEL, name="reserva_boca")


def present(m, bone, bx, by, bz, size, wrap_mat):
    w, h, d = size
    m.cube(bone, (bx - w / 2, by, bz - d / 2), size, wrap_mat, name=bone + "_caixa")
    m.cube(bone, (bx - 1.5, by + h, bz - 1), (3, 2, 2), M_GOLD, rot=(0, 0, 25), origin=(bx, by + h, bz), name=bone + "_laco_a")
    m.cube(bone, (bx - 1.5, by + h, bz - 1), (3, 2, 2), M_GOLD, rot=(0, 0, -25), origin=(bx, by + h, bz), name=bone + "_laco_b")


def rope(m, bone, pts, mat, size=2, name="corda"):
    """Square links along a Catmull-Rom curve through world-like points (in the bone's rest frame)."""
    pts = np.array(pts, float)
    dense = []
    for i in range(len(pts) - 1):
        p0, p1, p2, p3 = pts[max(i - 1, 0)], pts[i], pts[i + 1], pts[min(i + 2, len(pts) - 1)]
        for t in np.linspace(0, 1, 16, endpoint=False):
            dense.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t
                                + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    dense.append(pts[-1])
    dense = np.array(dense)
    seg = np.linalg.norm(np.diff(dense, axis=0), axis=1)
    cum = np.r_[0, np.cumsum(seg)]
    step = size * 0.9
    for k, dist in enumerate(np.arange(0, cum[-1], step)):
        i = min(np.searchsorted(cum, dist, side="right") - 1, len(seg) - 1)
        f = (dist - cum[i]) / max(seg[i], 1e-6)
        p = dense[i] * (1 - f) + dense[i + 1] * f
        t = dense[i + 1] - dense[i]
        t /= np.linalg.norm(t)
        xa = np.array([1.0, 0, 0]) - t[0] * t
        if np.linalg.norm(xa) < 1e-3:
            xa = np.array([0, 0, 1.0]) - t[2] * t
        xa /= np.linalg.norm(xa)
        R = np.c_[xa, np.cross(t, xa), t]
        m.cube(bone, (p[0] - size / 2, p[1] - size / 2, p[2] - size / 2), (size, size, size), mat,
               rot=to_euler(R), origin=tuple(p), name="%s_%d" % (name, k))


# --------------------------------------------------------------------------------------------- model


def build():
    m = HDModel(IDENT, seed=1225, tex_w=256, tex_scale=1, ao_rays=40, ao_dist=7.0, ao_strength=0.5)
    m.bone("raiz")

    for side, sx in (("d", 1), ("e", -1)):
        leg = m.bone("perna_" + side, "raiz", (7 * sx, 54, 0), (2, 0, 5 * sx))
        m.cube(leg, (X(1.5, 12, sx), 32, -6), (12, 22, 12), M_PANTS, name="coxa_" + side)
        shin = m.bone("canela_" + side, "perna_" + side, (7 * sx, 32, 0), (-2, 0, -5 * sx))
        m.cube(shin, (X(2, 10, sx), 13, -5), (10, 19, 10), M_PANTS, name="perna_baixa_" + side)
        m.cube(shin, (X(1.3, 11, sx), 11, -5.7), (11, 4, 11), M_FUR_SOFT, inflate=0.35, name="pelo_calca_" + side)
        m.cube(shin, (X(1.5, 11, sx), 1.5, -6), (11, 10, 12), M_BOOT, name="bota_" + side)
        m.cube(shin, (X(1.5, 11, sx), 1.5, -9), (11, 6, 3), M_BOOT.with_(round=2.4, round_y=2.0), name="bico_" + side)
        m.cube(shin, (X(1, 12, sx), 0, -9.5), (12, 2, 16), M_SOLE, name="sola_" + side)

    body = m.bone("corpo", "raiz", (0, 56, 0), (-3, 0, 0))
    for sx in (1, -1):
        m.cube(body, (X(3.5, 9, sx), 38, -7.5), (9, 20, 15), M_COAT, name="aba_" + str(sx))
        m.cube(body, (X(3, 10, sx), 36.5, -8), (10, 3, 16), M_FUR, inflate=0.3, name="pelo_aba_" + str(sx))
        m.cube(body, (X(2.5, 2, sx), 38, -8.3), (2, 20, 2), M_FUR, inflate=0.2, name="pelo_frente_aba_" + str(sx))
    m.cube(body, (-3.5, 38, 2), (7, 20, 5), M_COAT, name="aba_costas")
    m.cube(body, (-3.5, 36.5, 2.5), (7, 3, 5), M_FUR, inflate=0.3, name="pelo_aba_costas")
    m.cube(body, (-10.5, 56, -7), (21, 4, 14), M_BELT, inflate=0.15, name="cinto")
    m.cube(body, (-3.5, 55.5, -8), (7, 5, 1), M_GOLD, [buckle], name="fivela")
    gift = m.bone("presente_cinto", "corpo", (-12, 56, -1), (0, 0, 8))
    present(m, gift, -12.0, 50.5, -1.0, (5, 5, 5), wrap_paper(ribbon_x=-12.0, ribbon_z=-1.0, rw=0.6, dither=0.0))

    m.cube(body, (-9, 59, -5.5), (18, 10, 11), M_TORSO, name="cintura")
    m.cube(body, (-12.5, 69, -6.5), (25, 9, 13), M_TORSO, name="costelas")
    m.cube(body, (-15.5, 78, -7.5), (31, 11, 15), M_TORSO, name="peito")
    m.cube(body, (-17, 67, -2.5), (34, 15, 9), M_COAT, name="dorsais")
    m.cube(body, (-8, 87, -4), (16, 5, 9), M_COAT_SHOULDER, name="gola")
    for sx in (1, -1):
        m.cube(body, (X(3.5, 3, sx), 58, -8.3), (3, 32, 2), M_FUR, rot=(-3, 0, -5 * sx),
               origin=(4.8 * sx, 58, -7.3), name="lapela_" + str(sx))
    m.cube(body, (-8.5, 87.5, -5.6), (17, 3, 3), M_FUR, inflate=0.2, name="pelo_gola")

    # head: sunglasses, angry brows, grey moustache and beard like the reference
    head = m.bone("cabeca", "corpo", (0, 98, -1), (-5, 0, 0))
    m.cube(head, (-4.5, 89, -4), (9, 10, 8), skin(round=3.0, round_y=0.6), name="pescoco")
    m.cube(head, (-6, 98, -7), (12, 13, 12), M_HEAD, [face_paint], name="cabeca")
    for sx in (1, -1):
        m.cube(head, (X(0.5, 6, sx), 106, -7.8), (6, 2, 2), M_BROW, rot=(0, 0, 14 * sx), origin=(0.5 * sx, 107, -6.8),
               name="sobrancelha_" + ("d" if sx > 0 else "e"))
    m.cube(head, (-6.5, 104, -8.2), (13, 2, 1), M_SHADES, [bitmap(SHADES, SHADES_PAL, face="north")], name="oculos")
    for sx in (1, -1):
        m.cube(head, (X(6, 1, sx), 104.5, -7.5), (1, 1, 8), M_SHADES_FRAME, name="haste_oculos_" + str(sx))
    m.cube(head, (-1.5, 101, -8.8), (3, 3, 2), M_NOSE, name="nariz")
    m.cube(head, (-6, 99, -9.2), (12, 2, 2), M_BEARD.with_(hi=0.88), name="bigode")
    for sx in (1, -1):
        m.cube(head, (X(5.5, 2, sx), 96, -9), (2, 4, 2), M_BEARD.with_(hi=0.88), name="bigode_ponta_" + str(sx))
        m.cube(head, (X(5.5, 2, sx), 94, -7), (2, 10, 8), M_BEARD, name="costeleta_" + str(sx))
    m.cube(head, (-6.5, 91, -8.3), (13, 9, 2), M_BEARD, [bitmap(MOUTH, MOUTH_PAL, face="north"), ragged1],
           name="barba")
    m.cube(head, (-4.5, 87, -8), (9, 4, 2), M_BEARD, [ragged1], name="barba_ponta")

    hat = m.bone("gorro", "cabeca", (0, 110, -1), (8, 0, -6))
    m.cube(hat, (-7, 109, -8), (14, 4, 14), M_FUR_SOFT, inflate=0.3, name="aba_gorro")
    m.cube(hat, (-6, 112.5, -7), (12, 6, 12), M_COAT.with_(round=3.0, round_y=1.5), name="gorro_1")
    h2 = m.bone("gorro_2", "gorro", (0, 118, -1), (12, 0, -8))
    m.cube(h2, (-5, 117, -6), (10, 6, 10), M_COAT.with_(round=2.6, round_y=1.5), name="gorro_2")
    h3 = m.bone("gorro_3", "gorro_2", (0, 122.5, -1), (18, 0, -14))
    m.cube(h3, (-4, 121.5, -5), (8, 5, 8), M_COAT.with_(round=2.2, round_y=1.3), name="gorro_3")
    h4 = m.bone("gorro_4", "gorro_3", (0, 126, -1), (24, 0, -18))
    m.cube(h4, (-3, 125, -4), (6, 5, 6), M_COAT.with_(round=1.8, round_y=1.2), name="gorro_4")
    h5 = m.bone("gorro_ponta", "gorro_4", (0, 129.5, -1), (26, 0, -18))
    m.cube(h5, (-2, 128.5, -3), (4, 4, 4), M_COAT.with_(round=1.4, round_y=1.0), name="gorro_ponta")
    m.cube(h5, (-3, 131.5, -4), (6, 6, 6), M_FUR_SOFT.with_(round=2.6, round_y=2.6), name="pompom")

    # arms: the right fires from the hip, the left fist raised in front of the shoulder holding the sack's rope
    arms = {"d": ((6, 0, 10), (80, 0, 0)), "e": ((22, 0, -12), (132, 0, 0))}
    for side, sx in (("d", 1), ("e", -1)):
        up, fore_r = arms[side]
        arm = m.bone("braco_" + side, "corpo", (21 * sx, 87, 0), up)
        m.cube(arm, (X(14, 14, sx), 78.5, -7), (14, 13, 14), M_COAT_SHOULDER, name="ombro_" + side)
        m.cube(arm, (X(15.5, 11, sx), 64, -6), (11, 16, 12), M_COAT_SLEEVE, name="manga_" + side)
        fore = m.bone("antebraco_" + side, "braco_" + side, (21 * sx, 65, 0), fore_r)
        m.cube(fore, (X(16, 10, sx), 50, -5.5), (10, 15, 11), M_COAT_SLEEVE, name="antebraco_" + side)
        m.cube(fore, (X(15, 12, sx), 47.5, -6.5), (12, 4, 13), M_FUR_SOFT, inflate=0.35, name="punho_pelo_" + side)
        hand = m.bone("mao_" + side, "antebraco_" + side, (21 * sx, 43, 0))
        m.cube(hand, (X(15.5, 11, sx), 37.5, -5.5), (11, 11, 11), M_GLOVE, [glove_fingers], name="luva_" + side)

    rig = m.rig
    rig.invalidate()

    # the machine gun in the right hand, from the V-Taper with clean materials and no engraving at this density
    vtaper.M_GUN = metal(panels=[("v", 3.0)], rivets=3.0, round=0.7, grime=0.0, scratches=0.0)
    vtaper.M_GUN_PLAIN = metal(round=0.5, grime=0.0, scratches=0.0)
    vtaper.M_GUN_DARK = metal(base=LEATHER, round=0.4, spec=0.3, grime=0.0, scratches=0.0)
    vtaper.M_STEEL = M_STEEL
    vtaper.M_CANDY = M_CANDY
    vtaper.engraving = lambda text, face: (lambda ctx, L: None)
    m.bone("metralhadora_d", "mao_d", (21, 43, 0))
    m.bones["metralhadora_d"]["rot"] = to_euler(rig.world_rot("mao_d").T @ euler((0, -4, 0)))
    rig.invalidate()
    vtaper.gun(m, "metralhadora_d", 21, 43, 0, 1)

    # the big sack on his back, a little to the left: a round belly narrowing to a gathered, tied neck, the mouth
    # flaring open again with the arsenal bursting out
    cx, cy, cz = -4, 74, 18
    sack = m.bone("saco", "corpo", (cx, cy, cz), (-4, 0, 8))
    layers = [  # (y0, height, width, depth, material, name)
        (50, 3, 24, 14, M_SACK_EDGE, "saco_fundo"),
        (53, 5, 32, 18, M_SACK, "saco_baixo"),
        (58, 22, 38, 22, M_SACK, "saco_barriga"),
        (80, 6, 32, 18, M_SACK, "saco_alto"),
        (86, 4, 24, 14, M_SACK_EDGE, "saco_ombro"),
        (90, 3, 14, 10, M_SACK_EDGE, "saco_pescoco"),
        (93, 3, 18, 13, M_SACK_EDGE, "saco_boca"),
    ]
    for y0, h, w, d, mat, name in layers:
        deco = [sack_patch] if name == "saco_barriga" else ([mouth_dark] if name == "saco_boca" else [])
        m.cube(sack, (cx - w / 2, y0, cz - d / 2), (w, h, d), mat.with_(round=min(w, d) * 0.22, round_y=h * 0.45),
               deco, name=name)
    m.cube(sack, (cx - 7.5, 90.8, cz - 5.5), (15, 1, 11), M_ROPE, inflate=0.2, name="saco_corda")
    m.cube(sack, (cx + 6.5, 87.5, cz - 6.2), (2, 4, 1), M_ROPE, name="saco_corda_ponta")

    # the arsenal fanned out of the mouth
    by = 84
    weapons = [
        ("rpg", rpg, (cx - 5, by, cz - 1), (4, 0, 30)),
        ("lanca_chamas", flamethrower, (cx + 4, by, cz + 2), (22, 0, -24)),
        ("laser", proton_laser, (cx, by, cz + 2), (24, 0, 4)),
        ("metralhadora_reserva", spare_minigun, (cx + 1, by, cz - 3), (2, 0, -8)),
    ]
    for name, fn, (bx, wy, bz), rot in weapons:
        b = m.bone(name, "saco", (bx, wy, bz), rot)
        fn(m, b, bx, wy, bz)
    pres = m.bone("presente_saco", "saco", (cx + 5, 94, cz - 2), (10, 0, -18))
    present(m, pres, cx + 5, 94, cz - 2, (6, 5, 6),
            wrap_paper(base=RED_ENAMEL, ribbon_x=cx + 5, ribbon_z=cz - 2, rw=0.6, dither=0.0))
    pres2 = m.bone("presente_saco_2", "saco", (cx - 6, 94, cz + 3), (-14, 0, 12))
    present(m, pres2, cx - 6, 94, cz + 3, (5, 5, 5), wrap_paper(ribbon_x=cx - 6, ribbon_z=cz + 3, rw=0.6, dither=0.0))

    # the rope: from the sack's mouth over the left shoulder into the raised fist
    rig.invalidate()
    fist = rig.to_rest("corpo", rig.point("mao_e", (-21, 43, 0)))
    mouth = rig.to_rest("corpo", rig.point("saco", (cx - 7.5, 91.3, cz - 3)))
    over = np.array([-19.0, 97.0, 1.0])
    front = np.array([fist[0] + 0.5, fist[1] + 5.0, fist[2] + 1.5])
    rope(m, "corpo", [mouth, over, front, fist], M_ROPE, size=2, name="corda_saco")
    return m
