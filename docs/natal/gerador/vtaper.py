"""Noel V-Taper: about eight blocks of bodybuilder Santa, shoulders more than three times his waist.

Head lowered, a cracked tactical visor with a red cybernetic eye behind it, a cigar in a snarl, braided beard.
Scarred, tattooed chest (a skull in a Santa hat; the naughty list down his back, names struck out), bandoliers,
an armoured pauldron with a kill tally, and a rotary cannon in each hand fed from a drum wrapped as a present.

Painted with the HD engine (pintura.py, materiais.py).
"""

import numpy as np

from materiais import *
from pintura import HDModel, Rig, bitmap, cut, emit, euler, to_euler

IDENT = "noel_vtaper"

# 3x5 font for engravings and tattoos
FONT = {
    "H": ["x.x", "x.x", "xxx", "x.x", "x.x"],
    "O": ["xxx", "x.x", "x.x", "x.x", "xxx"],
    " ": ["...", "...", "...", "...", "..."],
}


def text_rows(s):
    rows = [""] * 5
    for ch in s:
        g = FONT[ch]
        for r in range(5):
            rows[r] += g[r] + "."
    return [r[:-1] for r in rows]


def X(x, w, sx):
    """Mirror a right-side box (x, width) to the left side."""
    return x if sx > 0 else -(x + w)


# --------------------------------------------------------------------------------------------- materials

M_TORSO = skin(
    height=torso_height,
    veins=0.0,
    scars=[
        ("north", (12.5, 88.5, 0), (2.0, 74.0, 0), 0.32),  # the slash across the right pec down the ribs
        ("south", (-3.0, 83.0, 0), (6.5, 75.0, 0), 0.28),
    ],
    round=2.2,
    round_y=0.6,
    bump=1.9,
    cav=0.7,
)
M_TRAPS = skin(height=lambda c: dome(c.u, c.v, c.fw / 2, c.fh * 0.6, c.fw * 0.55, c.fh * 0.7, 0.6), round=2.5,
               round_y=1.0)
M_NECK = skin(height=lambda c: groove(c.u, c.v, c.fw * 0.3, 0, c.fw * 0.5, c.fh, 0.8, 0.3)
              + groove(c.u, c.v, c.fw * 0.7, 0, c.fw * 0.5, c.fh, 0.8, 0.3), round=3.0, round_y=0.6)
M_DELT = skin(height=limb_height("delt"), veins=0.0, round=5.0, round_y=3.0, bump=1.5, cav=0.6)
M_BICEP = skin(height=limb_height("bicep"), veins=0.8, round=4.0, round_y=0.8, bump=1.5, cav=0.6)
M_PEAK = skin(height=lambda c: dome(c.u, c.v, c.fw / 2, c.fh / 2, c.fw * 0.5, c.fh * 0.5, 0.6), veins=0.6,
              round=3.5, round_y=3.0)
M_FOREARM = skin(height=limb_height("forearm"), veins=1.2, round=3.5, round_y=0.6)
M_HEAD = skin(round=2.5, round_y=1.0, freckle=0.9)
M_NOSE = skin(round=1.2, round_y=0.6)
M_SKIN_SMALL = skin(round=0.6)


def pants_stripe(outer_sign):
    """The white fur band down the outer side of the trousers."""

    def f(ctx):
        if (ctx.face == "east" and outer_sign > 0) or (ctx.face == "west" and outer_sign < 0):
            return np.abs(ctx.P[..., 2] - 0.0) < 1.6
        return np.zeros(ctx.P.shape[:2], dtype=bool)

    return f


M_PANTS_R = cloth(stripe=pants_stripe(1), round=3.0, round_y=0.6, holes=0.0)
M_PANTS_L = cloth(stripe=pants_stripe(-1), round=3.0, round_y=0.6)
M_PELVIS = cloth(round=2.5, round_y=0.8, dirt=0.3)
M_HAT = cloth(round=2.4, round_y=1.4, folds=0.9, dirt=0.0, holes=0.0, seams=False)
M_FUR = fur(dirt=0.7)
M_FUR_CLEAN = fur(dirt=0.35)
M_LEATHER = leather()
M_BOOT = leather(laces=True, round=1.6, round_y=0.8)
M_BOOT_PLAIN = leather(round=1.6, round_y=0.8, stitch=True)
M_SOLE = rubber()
M_STEEL_TOE = metal(base=STEEL, round=1.5, scratches=2.0, grime=1.0)
M_GUN = metal(panels=[("v", 3.0)], rivets=2.0, round=0.7)
M_GUN_PLAIN = metal(round=0.5)
M_GUN_DARK = metal(base=LEATHER, round=0.4, spec=0.3)
M_STEEL = metal(base=STEEL, round=0.6, scratches=1.5)
M_BRACER = metal(base=STEEL, chip=PAINT_RED, rivets=2.5, round=2.0, round_y=0.6)
M_PAULDRON = metal(base=STEEL, chip=PAINT_RED, rivets=2.2, round=1.2, round_y=0.8, grime=1.0)
M_SPIKE = metal(base=STEEL, round=0.8, scratches=2.5, grime=1.2)
M_GOLD = metal(base=GOLD, round=0.5, spec=0.8, grime=0.3, scratches=0.5)
M_GOLD_SOFT = metal(base=GOLD, round=0.8, spec=0.6, grime=0.4)
M_KNEE = metal(base=LEATHER, round=1.8, round_y=1.2, spec=0.35, panels=[("v", 2.0)])
M_CANDY = candy()
M_OLIVE = metal(base=OLIVE, round=0.8, spec=0.2, scratches=0.5)
M_GLOVE = leather(round=1.4, stitch=True)
M_HAIR = hair(round=1.2, round_y=0.6)
M_BRAID = hair(braid=True, roots=False, round=1.4, round_y=0.5)
M_BEARD = hair(round=1.6, round_y=0.6, flow=1.0, hi=0.93, bump=1.4)
M_CIGAR = flat(CIGAR, round=0.5, spec=0.1)
M_ASH = flat(ASH, round=0.4)
M_LENS = metal(base=LENS, round=0.4, spec=1.0, spec_p=40, scratches=0.3, grime=0.0)


def barrel_heat(z_cold, z_hot):
    """0 at z_cold (rest coords), 1 at the muzzle."""

    def f(ctx):
        return np.clip((ctx.P[..., 2] - z_cold) / (z_hot - z_cold), 0, 1) ** 1.6

    return f


# ------------------------------------------------------------------------------------------------ decals

INK_K = (INK, -1.0)
INK_M = (INK, 1.0)
INK_R = (INK_RED, 0.5)
INK_RD = (INK_RED, -1.5)
SKIN_HI = (SKIN, 2.5)

SKULL_HAT = [  # 22 x 20: a skull in a Santa hat
    "..............RRRRww..",
    "...........RRRRRRRww..",
    "........RRRRRRRRRR....",
    "......RRRRRrrRRRR.....",
    ".....RRRRrrrrrRR......",
    "....wwwwwwwwwwwwww....",
    "...wwwwwwwwwwwwwwww...",
    "....kkkkkkkkkkkkkk....",
    "...kssssssssssssssk...",
    "..kssssssssssssssssk..",
    "..ksskkkkssssskkkksk..",
    "..kskkkkkksssskkkkkk..",
    "..ksskkkkssssskkkksk..",
    "..kssssssskkssssssssk.",
    "...ksssssskksssssssk..",
    "....kssssssssssssk....",
    ".....kskkskkskkskk....",
    "......kskkskkskk......",
    ".......kkkkkkkkk......",
    "......................",
]
SKULL_PAL = {
    "k": INK_K,
    "s": (SKIN, 1.5),
    "R": INK_R,
    "r": INK_RD,
    "w": (SKIN, 3.0),
}

BROWS = [  # brow ridge front, 24 x 4
    "bbbb..............bbbb..",
    ".bbbbbb........bbbbbb...",
    "...bbbbbbb..bbbbbbb.....",
    ".....bbbbb..bbbbb.......",
]

VISOR = [  # 26 x 6: right lens (screen left) cracked over a glowing eye, left lens with a hard reflection
    "mmmmmmmmmmmmmmmmmmmmmmmmmm",
    "mLcLLgGgLLcLmmLLLLLLLLwLLm",
    "mLLcgGEEGgLLmmLLLLLLLwLLLm",
    "mLLcgGEEGgcLmmLLLLLLwLLLLm",
    "mLcLLgGgLLLcmmLLLLLwLLLLLm",
    "mmLLLLcLLLLmmmmLLLLLLLLLmm",
]
VISOR_PAL = {
    "m": (GUNMETAL, 4.0),
    "L": (LENS, 0.0),
    "c": emit((200, 70, 60)),
    "g": emit(GLOW_RED_DIM),
    "G": emit(GLOW_RED),
    "E": emit(GLOW_RED_HOT),
    "w": (LENS, 6.0),
}

MOUTH = [  # on the beard front (28 wide), under the moustache: a snarl, raised on the cigar side
    "..........ddddddddd.........",
    ".........dTTdTTdTTdd........",
    "..........dtdtdtddd.........",
    "...........ddddd............",
]
MOUTH_PAL = {"d": (SOOT, -2.0), "t": (FUR, 3.0), "T": (FUR, 5.0)}


def barbed_band(v0):
    """Barbed-wire tattoo round the bicep, a zigzag 2 units tall starting v0 units below the top."""

    def d(ctx, L):
        if ctx.face not in ("north", "south", "east", "west"):
            return
        v = ctx.v - v0
        inband = (v >= 0) & (v < 2.0)
        zig = np.abs(((ctx.u * 1.1) % 2.0) - 1.0) * 1.6 + 0.2
        wire = inband & (np.abs(v - zig) < 0.32)
        wire2 = inband & (np.abs(v - (1.8 - zig)) < 0.32)
        barb = inband & ((np.abs((ctx.u * 1.1) % 2.0 - 1.0) < 0.18) & (np.abs(v - 1.0) < 0.7))
        m = wire | wire2 | barb
        L.rid[m] = L.use(INK)
        L.alb[m] = -0.5

    return d


def naughty_list(x0, x1, y0, y1):
    """The naughty list tattooed on the back (south faces), in rest coords: a scroll, lines, names struck out."""

    def d(ctx, L):
        if ctx.face != "south":
            return
        x, y = ctx.P[..., 0], ctx.P[..., 1]
        inside = (x > x0) & (x < x1) & (y > y0) & (y < y1)
        if not inside.any():
            return
        border = inside & ((np.abs(x - x0) < 0.45) | (np.abs(x - x1) < 0.45))
        rolls = ((np.abs(y - y1) < 1.0) | (np.abs(y - y0) < 1.0)) & (x > x0 - 0.8) & (x < x1 + 0.8)
        roll_edge = rolls & ((np.abs(np.abs(y - y1) - 1.0) < 0.3) | (np.abs(np.abs(y - y0) - 1.0) < 0.3))
        L.rid[rolls] = L.use(INK)
        L.alb[rolls] = 2.5
        L.alb[roll_edge] = -1
        L.rid[border] = L.use(INK)
        L.alb[border] = -1
        # lines of names: short dashes in rows one unit apart, some struck through in red
        row = np.floor((y1 - 1.6 - y) / 1.0)
        in_text = inside & (y < y1 - 1.6) & (y > y0 + 1.6) & (x > x0 + 0.9) & (x < x1 - 0.9)
        frac = (y1 - 1.6 - y) % 1.0
        rng = np.random.default_rng(abs(int(x0 * 7 + y0)) + 11)
        lens = rng.uniform(0.45, 0.95, 40)
        struck = rng.random(40) < 0.6
        r_i = np.clip(row, 0, 39).astype(int)
        glyph = in_text & (frac < 0.38) & ((x - x0 - 0.9) < (x1 - x0 - 1.8) * lens[r_i]) & (
            ((np.floor((x - x0) * 2.2) % 3) != 2))
        L.rid[glyph] = L.use(INK)
        L.alb[glyph] = 0
        strike = in_text & (np.abs(frac - 0.2) < 0.16) & struck[r_i] & (
            (x - x0 - 0.6) < (x1 - x0 - 1.2) * lens[r_i] + 0.4)
        L.rid[strike] = L.use(INK_RED)
        L.alb[strike] = 1.0

    return d


def bullet_scar(face, cx, cy, r=0.7):
    def d(ctx, L):
        if ctx.face != face:
            return
        if face in ("north", "south"):
            dd = np.hypot(ctx.P[..., 0] - cx, ctx.P[..., 1] - cy)
        else:
            dd = np.hypot(ctx.P[..., 2] - cx, ctx.P[..., 1] - cy)
        ang = np.arctan2(ctx.P[..., 1] - cy, ctx.P[..., 0] - cx)
        star = dd < r * (1 + 0.45 * np.cos(ang * 5))
        L.rid[star] = L.use(SCAR)
        L.alb[star] = 0.5 - (dd[star] < r * 0.4) * 2
        L.H += np.where(star, -0.2, 0)

    return d


def kill_tally(face):
    def d(ctx, L):
        if ctx.face != face:
            return
        u, v = ctx.u, ctx.v
        groups = (u > 1.0) & (u < ctx.fw - 1.0) & (v > 0.8) & (v < ctx.fh - 0.8)
        k = (u - 1.0) % 3.0
        mark = groups & (((k % 0.6) < 0.22) & (k < 2.3))
        slash = groups & (np.abs((k / 2.3) * (ctx.fh - 1.6) - (v - 0.8)) < 0.3) & (k < 2.3)
        m = mark | slash
        L.rid[m] = L.use(STEEL)
        L.alb[m] = 5
        L.H[m] -= 0.25

    return d


def engraving(text, face):
    rows = text_rows(text)

    def d(ctx, L):
        if ctx.face != face:
            return
        th, tw = L.H.shape
        w = len(rows[0])
        c0 = (tw - w) // 2
        r0 = (th - 5) // 2
        for r, line in enumerate(rows):
            for c, ch in enumerate(line):
                if ch == "x":
                    j, i = r0 + r, c0 + c
                    L.rid[j, i] = L.use(GOLD)
                    L.alb[j, i] = 2
                    L.H[j, i] -= 0.15
        # a gold frame round the text
        for i in range(c0 - 3, c0 + w + 3):
            for j in (r0 - 2, r0 + 6):
                if 0 <= i < tw and 0 <= j < th:
                    L.rid[j, i] = L.use(GOLD)
                    L.alb[j, i] = 0

    return d


def buckle_skull(ctx, L):
    if ctx.face != "north":
        return
    art = [
        "............",
        "...kkkkkk...",
        "..kssssssk..",
        "..kskksksk..",
        "..ksssssskk.",
        "...ksksksk..",
        "....kkkkk...",
        "............",
    ]
    th, tw = L.H.shape
    c0, r0 = (tw - 12) // 2, (th - 8) // 2
    for r, line in enumerate(art):
        for c, ch in enumerate(line):
            j, i = r0 + r, c0 + c
            if not (0 <= j < th and 0 <= i < tw):
                continue
            if ch == "k":
                L.H[j, i] -= 0.3
                L.alb[j, i] = -3
            elif ch == "s":
                L.H[j, i] += 0.25
                L.alb[j, i] = 2


def bullets_on_strap(ctx, L):
    """Cartridges in loops along a bandolier (north/south faces of a long strap)."""
    if ctx.face not in ("north", "south"):
        L.alb += 1.5
        return
    L.alb += 1.5
    k = ctx.v % 2.0
    brass = (k < 1.3) & (np.abs(ctx.u - ctx.fw / 2) < ctx.fw * 0.3)
    tipm = brass & (k < 0.4)
    L.rid[brass] = L.use(GOLD)
    L.alb[brass] = -0.5
    L.alb[tipm] = -2.5
    L.H += np.where(brass, 0.5 * np.sin(np.clip(k / 1.3, 0, 1) * np.pi) * np.cos((ctx.u - ctx.fw / 2) / ctx.fw * 2.6), 0)
    L.spec[brass] = 0.4
    loop = (~brass) & (k < 1.5) & (k > 0.2)
    L.alb[loop] -= 1.0


def link_paint(ctx, L):
    """One link of an ammo belt: brass case, copper tip, dark link band."""
    u = ctx.u
    if ctx.face in ("north", "south", "up", "down"):
        L.rid[:] = L.use(GOLD)
        tip = u < 0.9 if ctx.face in ("north", "up", "down") else u > ctx.fw - 0.9
        L.alb[tip] = -2.0
        band = np.abs(u - ctx.fw / 2) < 0.4
        L.rid[band] = L.use(GUNMETAL)
        L.alb[band] = 1


# ------------------------------------------------------------------------------------------------- model


def build():
    m = HDModel(IDENT, seed=2512, tex_w=256, tex_scale=2, ao_rays=40, ao_dist=7.0, ao_strength=0.72)
    m.bone("raiz")

    # ------------------------------------------------------------------ legs
    for side, sx in (("d", 1), ("e", -1)):
        leg = m.bone("perna_" + side, "raiz", (7 * sx, 54, 0), (2, 0, 6 * sx))
        pants = M_PANTS_R if sx > 0 else M_PANTS_L
        m.cube(leg, (X(1.5, 12, sx), 34, -6), (12, 20, 12), pants, name="coxa_" + side)
        shin = m.bone("canela_" + side, "perna_" + side, (7 * sx, 32, 0), (-2, 0, -6 * sx))
        m.cube(shin, (X(2, 10, sx), 16, -5), (10, 17, 10), pants, name="perna_baixa_" + side)
        m.cube(shin, (X(2, 10, sx), 28, -7.1), (10, 8, 2), M_KNEE, name="joelheira_" + side)
        m.cube(shin, (X(1.5, 11, sx), 30, -5.9), (11, 2, 11), M_LEATHER, inflate=0.05, name="tira_joelho_" + side)
        m.cube(shin, (X(2, 10, sx), 2, -6), (10, 10, 12), M_BOOT_PLAIN, name="bota_" + side)
        m.cube(shin, (X(2.5, 9, sx), 11, -5), (9, 5, 10), M_BOOT, name="cano_" + side)
        m.cube(shin, (X(2, 10, sx), 2, -9), (10, 6, 3), M_STEEL_TOE, name="biqueira_" + side)
        m.cube(shin, (X(1.5, 11, sx), 0, -9.5), (11, 2, 16), M_SOLE, name="sola_" + side)
        m.cube(shin, (X(1.5, 11, sx), 15, -5.5), (11, 3, 11), M_FUR, inflate=0.3, name="pelo_bota_" + side)
        m.cube(shin, (X(5.5, 3, sx), 6, -6.4), (3, 2, 1), M_STEEL, name="fivela_bota_" + side)
        if sx > 0:  # candy-cane knife on the right thigh
            m.cube(leg, (13, 35, -2), (2, 11, 4), M_LEATHER, name="bainha")
            m.cube(leg, (12.6, 46, -2.6), (3, 1, 5), M_STEEL, name="guarda_faca")
            m.cube(leg, (13, 47, -1.5), (2, 6, 3), M_CANDY, name="cabo_faca")
        else:  # cargo pocket on the left
            m.cube(leg, (-15, 37, -3.5), (2, 8, 7), M_PELVIS, name="bolso")
            m.cube(leg, (-15.3, 43.5, -4), (2, 2, 8), M_PANTS_L, name="aba_bolso")

    # ------------------------------------------------------------------ hips and torso
    body = m.bone("corpo", "raiz", (0, 56, 0), (-4, 0, 0))
    m.cube(body, (-10, 52, -6), (20, 8, 12), M_PELVIS, name="quadril")
    m.cube(body, (-9.5, 58, -6.5), (19, 4, 13), M_LEATHER, inflate=0.15, name="cinto")
    m.cube(body, (-3.5, 57.8, -7.4), (7, 5, 1), M_GOLD.with_(), [buckle_skull], name="fivela")
    for px in (-9, 5):
        m.cube(body, (px, 55, -8.6), (4, 5, 2), M_LEATHER, name="bolsa_%d" % px)
        m.cube(body, (px - 0.2, 59, -8.8), (4, 2, 2), M_LEATHER, inflate=0.1, name="aba_bolsa_%d" % px)
    gren = m.bone("granada_cinto", "corpo", (-11, 56, 0), (0, 0, 6))
    m.cube(gren, (-12.5, 52, -2), (3, 4, 3), M_OLIVE, [pineapple], name="granada_cinto")
    m.cube(gren, (-12, 56, -1.5), (2, 1, 2), M_STEEL, name="espoleta_cinto")

    m.cube(body, (-8, 61, -5), (16, 10, 10), M_TORSO, name="cintura")
    m.cube(body, (-11, 71, -5.5), (22, 8, 11), M_TORSO, [bullet_scar("north", -7.5, 74.5)], name="costelas")
    m.cube(body, (-14, 79, -6.5), (28, 10, 13), M_TORSO,
           [bullet_scar("north", 4.0, 81.5), bullet_scar("north", -9.5, 83.0, 0.55),
            naughty_list(-12.5, -4.5, 64, 87.5)], name="peito")
    m.cube(body, (-16, 69, -2), (32, 15, 8), M_TORSO, [naughty_list(-12.5, -4.5, 64, 87.5)], name="dorsais")
    m.cube(body, (-13, 63, -1.5), (26, 6, 7), M_TORSO, [naughty_list(-12.5, -4.5, 64, 87.5)], name="dorsais_baixo")
    m.cube(body, (-7, 86, -3), (14, 7, 8), M_TRAPS, name="trapezio")
    for sx in (1, -1):
        m.cube(body, (X(3, 13, sx), 85, -3.5), (13, 7, 8), M_TRAPS, rot=(0, 0, -20 * sx),
               origin=(3 * sx, 90, 0), name="trapezio_" + ("d" if sx > 0 else "e"))
    # bandoliers, front and back
    for rz, nm in ((33, "a"), (-33, "b")):
        m.cube(body, (-2, 50, -7.9), (4, 44, 1), M_LEATHER, [bullets_on_strap], rot=(-6, 0, rz),
               origin=(0, 72, -7.4), name="cartucheira_frente_" + nm)
        m.cube(body, (-2, 50, 6.6), (4, 44, 1), M_LEATHER, [bullets_on_strap], rot=(6, 0, rz),
               origin=(0, 72, 7.1), name="cartucheira_costas_" + nm)

    # ------------------------------------------------------------------ head
    head = m.bone("cabeca", "corpo", (0, 96, -1), (-9, 0, 0))
    m.cube(head, (-5, 88, -4), (10, 9, 8), M_NECK, name="pescoco")
    m.cube(head, (-6, 96, -7), (12, 13, 11), M_HEAD, [head_hair], name="cabeca")
    m.cube(head, (-6, 104, -8.4), (12, 2, 1.4 if False else 1), M_HEAD.with_(round=0.8, round_y=0.6), [brow_scar],
           name="testa_baixa")
    for sx in (1, -1):
        bx = X(0.3, 6.5, sx)
        m.cube(head, (bx, 104, -9.6), (6.5 if False else 7, 2, 2), M_HAIR.with_(round=0.7, round_y=0.6, lo=0.0, hi=0.8),
               rot=(0, 0, 13 * sx), origin=(0.3 * sx, 104.6, -8.6), name="sobrancelha_" + ("d" if sx > 0 else "e"))
    m.cube(head, (-6.5, 101, -8.5), (13, 3, 1), M_LENS, [bitmap(VISOR, VISOR_PAL, face="north")], name="visor")
    for sx in (1, -1):
        m.cube(head, (X(6, 1, sx), 102, -7.5), (1, 2, 9), M_GUN_PLAIN, name="haste_visor_" + str(sx))
    m.cube(head, (-1.5, 98, -9), (3, 3, 2), M_NOSE, name="nariz")
    for sx in (1, -1):
        m.cube(head, (X(6, 1, sx), 99, -2.5), (1, 4, 2), M_SKIN_SMALL, name="orelha_" + str(sx))
    m.cube(head, (-7, 88, -9.5), (14, 12, 3), M_BEARD, [bitmap(MOUTH, MOUTH_PAL, face="north", col=0, row=6),
                                                        beard_ragged], name="barba")
    m.cube(head, (-5.5, 84, -9.2), (11, 4, 2), M_BEARD, [beard_ragged], name="barba_baixo")
    M_STACHE = M_HAIR.with_(round=1.0, round_y=0.9, lo=0.0, hi=0.78)
    m.cube(head, (-6.5, 97, -11.2), (13, 3, 2), M_STACHE, name="bigode")
    for sx in (1, -1):
        m.cube(head, (X(5.5, 2, sx), 91, -11), (2, 7, 2), M_STACHE, rot=(0, 0, -8 * sx), origin=(6.5 * sx, 97, -10),
               name="bigode_ponta_" + str(sx))
        m.cube(head, (X(5.5, 2, sx), 89, -8), (2, 10, 7), M_BEARD, [beard_ragged], name="barba_lado_" + str(sx))
        bx = X(2, 3, sx)
        m.cube(head, (bx, 74, -9), (3, 14, 3), M_BRAID, name="tranca_" + str(sx))
        m.cube(head, (bx - 0.5, 80, -9.5), (4, 2, 4), M_GUN_PLAIN, name="anel_a_" + str(sx))
        m.cube(head, (bx - 0.5, 75.5, -9.5), (4, 2, 4), M_GUN_PLAIN, name="anel_b_" + str(sx))
        m.cube(head, (bx + 0.5, 72, -8.5), (2, 2, 2), M_HAIR, name="ponta_tranca_" + str(sx))
    # the cigar, from the left corner of the snarl
    cig_o = (-3.5, 96.7, -9.5)
    m.cube(head, (-4, 96.2, -15), (1, 1, 5), M_CIGAR, rot=(8, 26, 0), origin=cig_o, name="charuto")
    m.cube(head, (-4, 96.2, -11.6), (1, 1, 1), M_GOLD, inflate=0.08, rot=(8, 26, 0), origin=cig_o, name="anel_charuto")
    m.cube(head, (-4, 96.2, -16), (1, 1, 1), M_ASH, [ember_tip], rot=(8, 26, 0), origin=cig_o, name="brasa")

    hat = m.bone("gorro", "cabeca", (0, 108, -1.5), (10, 0, 6))
    m.cube(hat, (-7, 107, -8.5), (14, 4, 13), M_FUR, inflate=0.3, name="aba_gorro")
    m.cube(hat, (-6, 111, -7.5), (12, 5, 11), M_HAT, [bullet_holes([(3.5, 2.5), (8.8, 3.6)])], name="gorro_1")
    hat2 = m.bone("gorro_2", "gorro", (0, 116, -2), (18, 0, -12))
    m.cube(hat2, (-5, 114.5, -6.5), (10, 6, 9), M_HAT, name="gorro_2")
    hat3 = m.bone("gorro_3", "gorro_2", (0, 121, -2), (26, 0, -18))
    m.cube(hat3, (-4, 119.5, -5.5), (8, 5, 7), M_HAT, [bullet_holes([(5.0, 2.0)])], name="gorro_3")
    m.cube(hat3, (-3, 123.5, -4.5), (6, 5, 5), M_HAT, name="gorro_4")
    hat4 = m.bone("gorro_ponta", "gorro_3", (0, 129, -2), (30, 0, -20))
    m.cube(hat4, (-2, 127.5, -3.5), (4, 4, 3), M_HAT, name="gorro_ponta")
    m.cube(hat4, (-2.5, 131.5, -4), (5, 2, 4), M_FUR, name="pompom_queimado")
    # the grenade hangs from the tip straight down: its bone undoes the hat's rotations
    m.bone("granada", "gorro_ponta", (0, 132.5, -2))

    # ------------------------------------------------------------------ arms
    for side, sx in (("d", 1), ("e", -1)):
        arm = m.bone("braco_" + side, "corpo", (21 * sx, 87, 0), (6, 0, 10 * sx))
        deco_delt = [] if sx > 0 else [bitmap(SKULL_HAT, SKULL_PAL, face="west", col=2, row=2)]
        m.cube(arm, (X(14.5, 13, sx), 79, -6.5), (13, 12, 13), M_DELT, deco_delt, name="deltoide_" + side)
        m.cube(arm, (X(16, 10, sx), 64, -5.5), (10, 16, 11), M_BICEP,
               [] if sx > 0 else [barbed_band(5.0)], name="braco_" + side)
        m.cube(arm, (X(17, 8, sx), 67, -7.5), (8, 9, 2), M_PEAK, name="biceps_" + side)
        m.cube(arm, (X(17, 8, sx), 67, 5.5), (8, 10, 2), M_PEAK, name="triceps_" + side)
        fore = m.bone("antebraco_" + side, "braco_" + side, (21 * sx, 65, 0), (80, 0, 0))
        m.cube(fore, (X(16.5, 9, sx), 49, -5), (9, 16, 10), M_FOREARM, name="antebraco_" + side)
        m.cube(fore, (X(16, 10, sx), 52, -5.5), (10, 10, 11), M_BRACER, inflate=0.25, name="bracadeira_" + side)
        m.cube(fore, (X(15.5, 11, sx), 48.5, -6), (11, 3, 12), M_FUR, inflate=0.3, name="pelo_pulso_" + side)
        hand = m.bone("mao_" + side, "antebraco_" + side, (21 * sx, 45, 0))
        m.cube(hand, (X(16.5, 9, sx), 40.5, -4.5), (9, 9, 9), M_GLOVE, [knuckles], name="luva_" + side)
        m.bone("metralhadora_" + side, "mao_" + side, (21 * sx, 45, 0))

    # the right pauldron: three lames, spikes, a kill tally
    pa = m.bone("ombreira", "braco_d", (21, 89, 0), (0, 0, -14))
    m.cube(pa, (12.5, 88, -8), (17, 4, 16), M_PAULDRON, name="lamina_1")
    m.cube(pa, (15.5, 85, -7.5), (15, 4, 15), M_PAULDRON, [kill_tally("east")], rot=(0, 0, -10),
           origin=(15.5, 89, 0), name="lamina_2")
    m.cube(pa, (18, 82, -7), (13, 4, 14), M_PAULDRON, rot=(0, 0, -20), origin=(18, 86, 0), name="lamina_3")
    m.cube(pa, (19, 80.6, -7), (12, 2, 14), M_FUR, inflate=0.2, rot=(0, 0, -20), origin=(18, 86, 0),
           name="pelo_ombreira")
    for k, z in enumerate((-5, 0, 5)):
        sp = m.bone("espinho_%d" % k, "ombreira", (21, 92, z), (0, 0, -22 - k * 4))
        m.cube(sp, (19.5, 92, z - 1.5), (3, 3, 3), M_SPIKE, name="espinho_base_%d" % k)
        m.cube(sp, (20, 95, z - 1), (2, 3, 2), M_SPIKE, name="espinho_meio_%d" % k)
        m.cube(sp, (20.5, 98, z - 0.5), (1, 3, 1), M_SPIKE, [spike_tip], name="espinho_ponta_%d" % k)

    # ------------------------------------------------------------------ pose the attached props
    rig = m.rig
    rig.invalidate()
    # the grenade hangs straight down
    gb = m.bones["granada"]
    gb["rot"] = to_euler(rig.world_rot("gorro_ponta").T)
    m.cube("granada", (-0.5, 129.5, -2.5), (1, 3, 1), M_LEATHER, name="cordao_granada")
    m.cube("granada", (-2, 124.5, -4), (4, 5, 4), M_OLIVE, [pineapple], name="granada")
    m.cube("granada", (-1, 129.5, -3), (2, 1, 2), M_STEEL, name="espoleta")
    m.cube("granada", (1, 125.5, -2.5), (1, 4, 1), M_STEEL, name="alavanca")
    m.cube("granada", (-1.5, 130, -3.5), (1, 2, 1), M_GOLD, name="argola")

    # the guns: level with the world, toed in a little
    rig.invalidate()
    for side, sx in (("d", 1), ("e", -1)):
        name = "metralhadora_" + side
        Rw = rig.world_rot("mao_" + side)
        m.bones[name]["rot"] = to_euler(Rw.T @ euler((0, -3 * sx, 0)))
        rig.invalidate()
        gun(m, name, 21 * sx, 45, 0, sx)
    return m


# ------------------------------------------------------------------------------------- part decorations


def pineapple(ctx, L):
    """Fragmentation grid of a grenade."""
    g = ((ctx.u % 1.0) < 0.22) | ((ctx.v % 1.0) < 0.22)
    if ctx.face in ("up", "down"):
        return
    L.H[g] -= 0.35
    L.alb[g] -= 1.5


def head_hair(ctx, L):
    """Shaved head: white hair only on the back and low behind the ears, stubble elsewhere; frown lines."""
    if ctx.face == "north":
        x, y = ctx.P[..., 0], ctx.P[..., 1]
        for cx in (-0.9, 0.9):
            w = (np.abs(x - cx - (y - 106) * 0.25 * np.sign(cx)) < 0.25) & (y > 106) & (y < 107.8)
            L.H[w] -= 0.3
            L.alb[w] -= 1.5
        for cy in (107.2, 108.0):
            w = (np.abs(y - cy - np.abs(x) * 0.05) < 0.22) & (np.abs(x) > 1.6) & (np.abs(x) < 4.5)
            L.H[w] -= 0.2
            L.alb[w] -= 1.0
    if ctx.face == "south":
        L.rid[:] = L.use(BEARD)
        crop = np.cos(ctx.P[..., 0] * 2 * np.pi / 2.0 + np.sin(ctx.P[..., 1] * 0.9) * 0.8)
        L.alb += 0.5 + crop * 0.9 - np.clip(ctx.P[..., 1] - 104, 0, 4) * 0.4
        L.H += crop * 0.12
        scar = np.abs(ctx.P[..., 0] + 2 - (ctx.P[..., 1] - 100) * 0.6) < 0.3
        L.rid[scar] = L.use(SCAR)
        L.alb[scar] = 1
    elif ctx.face in ("east", "west"):
        z, y = ctx.P[..., 2], ctx.P[..., 1]
        m = (z > 1.0 + (y - 99) * 0.4) | ((y < 99.5) & (z > -4))
        L.rid[m] = L.use(BEARD)
        L.alb[m] += 0.5
        stub = (~m) & (y > 103.5) & (((ctx.ii * 3 + ctx.jj * 5) % 7) == 0)
        L.alb[stub] -= 1.2


def brow_scar(ctx, L):
    if ctx.face != "north":
        return
    # the scar crosses the right brow (screen left) diagonally
    for j in range(L.H.shape[0]):
        for i in (6 + j, 7 + j):
            if 0 <= i < L.H.shape[1]:
                L.rid[j, i] = L.use(SCAR)
                L.alb[j, i] = 1.5
                L.H[j, i] += 0.2


def beard_ragged(ctx, L):
    if ctx.face not in ("north", "south", "east", "west"):
        return
    th, tw = L.H.shape
    rng = np.random.default_rng(ctx.seed)
    for i in range(tw):
        n = rng.integers(0, 4)
        for k in range(n):
            L.alpha[th - 1 - k, i] = False


def ember_tip(ctx, L):
    if ctx.face in ("north",):
        L.emask[:] = True
        L.emit[:] = EMBER
        L.emit[1:-1, 1:-1] = EMBER_HOT
    elif ctx.face in ("east", "west", "up", "down"):
        third = ctx.u < ctx.fw * 0.5 if ctx.face in ("east",) else ctx.u > ctx.fw * 0.5
        L.emask[third] = True
        L.emit[third] = EMBER


def knuckles(ctx, L):
    """Fingerless gloves: the fingers (front face, lower half) show skin, knuckle studs on top."""
    if ctx.face == "north":
        fingers = ctx.v > ctx.fh * 0.35
        L.rid[fingers] = L.use(SKIN)
        L.alb[fingers] = 0
        gaps = fingers & ((ctx.u % (ctx.fw / 4)) < 0.3)
        L.alb[gaps] = -3
        L.H += np.where(fingers, 0.3 * np.sin((ctx.u % (ctx.fw / 4)) / (ctx.fw / 4) * np.pi), 0)
        stud = (~fingers) & (np.abs(ctx.v - ctx.fh * 0.2) < 0.5) & ((ctx.u % (ctx.fw / 4)) > 0.7) & (
            (ctx.u % (ctx.fw / 4)) < 1.6)
        L.rid[stud] = L.use(STEEL)
        L.alb[stud] = 3
        L.H[stud] += 0.4


def spike_tip(ctx, L):
    L.alb -= np.clip((1.5 - ctx.v), 0, 1.5) * 0  # keep the shine, the soot goes on the very top
    if ctx.face == "up":
        L.rid[:] = L.use(SOOT)


def bullet_holes(spots):
    """Bullet holes in the hat's front: a black hole, a scorched rim, frayed threads."""

    def d(ctx, L):
        if ctx.face != "north":
            return
        for cu, cv in spots:
            dd = np.hypot(ctx.u - cu, ctx.v - cv)
            ring = dd < 1.1
            L.rid[ring] = L.use(SOOT)
            L.alb[ring] = 2
            hole = dd < 0.55
            L.alb[hole] = -4
            L.H += np.where(ring, -0.3, 0)
            fray = (dd > 1.0) & (dd < 1.4) & (((ctx.ii + 2 * ctx.jj) % 3) == 0)
            L.alb[fray] += 2

    return d


def patch(k):
    """A square patch stitched on the hat."""

    def d(ctx, L):
        if ctx.face not in ("north", "east"):
            return
        cu, cv = ctx.fw * (0.35 if k == 1 else 0.6), ctx.fh * 0.5
        inside = (np.abs(ctx.u - cu) < 1.6) & (np.abs(ctx.v - cv) < 1.4)
        L.rid[inside] = L.use(PAINT_RED)
        L.alb[inside] = -1
        rim = inside & ((np.abs(np.abs(ctx.u - cu) - 1.4) < 0.25) | (np.abs(np.abs(ctx.v - cv) - 1.2) < 0.25))
        st = rim & (((ctx.ii + ctx.jj) % 2) == 0)
        L.rid[st] = L.use(FUR)
        L.alb[st] = 2

    return d


# ----------------------------------------------------------------------------------------------- the gun


def gun(m, bone, gx, gy, gz, sx):
    """A seven-barrel rotary cannon gripped at (gx, gy, gz); barrels toward -Z, drum on the outer side."""
    G = lambda dx, dy, dz: (gx + dx, gy + dy, gz + dz)
    outer = "east" if sx > 0 else "west"
    m.cube(bone, G(-1.5, -4, -1.5), (3, 9, 3), M_GUN_DARK, name=bone + "_punho")
    m.cube(bone, G(-5, 5, -14), (10, 10, 26), M_GUN, [engraving("HO HO HO", outer)], name=bone + "_corpo")
    m.cube(bone, G(-4, 6, 12), (8, 8, 5), M_GUN.with_(), name=bone + "_motor")
    for k in range(3):
        m.cube(bone, G(-4.5, 7 + k * 2.2, 12.5), (9, 1, 4), M_GUN_DARK, name=bone + "_aleta_%d" % k)
    m.cube(bone, G(-1, 15, -9), (2, 3, 2), M_GUN_PLAIN, name=bone + "_alca_a")
    m.cube(bone, G(-1, 15, 5), (2, 3, 2), M_GUN_PLAIN, name=bone + "_alca_b")
    m.cube(bone, G(-1, 18, -10), (2, 2, 17), M_GUN_DARK, name=bone + "_alca")
    m.cube(bone, G(-1.5, 20, -6), (3, 3, 4), M_GUN_PLAIN, name=bone + "_mira")
    m.cube(bone, G(-1, 20.5, -6.4), (2, 2, 1), glow(GLOW_RED, GLOW_RED_DIM), name=bone + "_mira_lente")
    m.cube(bone, G(-6, 4, -20), (12, 12, 6), M_CANDY, name=bone + "_luva_canos")
    m.cube(bone, G(-1.5, -4, -19), (3, 8, 3), M_GUN_DARK, name=bone + "_empunhadura")
    ax, ay = 0.0, 10.0
    heat = barrel_heat(gz - 34, gz - 52)
    M_BARREL = metal(base=STEEL, round=1.0, scratches=0.8, heat=heat, grime=0.3)
    for k in range(6):
        a = np.radians(30 + k * 60)
        ox, oy = ax + 3.6 * np.cos(a), ay + 3.6 * np.sin(a)
        m.cube(bone, G(ox - 1, oy - 1, -52), (2, 2, 34), M_BARREL, [muzzle_glow], name=bone + "_cano_%d" % k)
    m.cube(bone, G(-1, 9, -50), (2, 2, 32), M_GUN_DARK, name=bone + "_eixo")
    m.cube(bone, G(-5.5, 4.5, -36), (11, 11, 3), M_STEEL, name=bone + "_braco_canos")
    m.cube(bone, G(-5.5, 4.5, -50), (11, 11, 2), metal(base=STEEL, round=0.8, grime=1.6), name=bone + "_boca")
    # feed port and the present drum on the outer side
    m.cube(bone, G(5 if sx > 0 else -7, 5, 3), (2, 6, 7), M_STEEL, name=bone + "_entrada")
    dx0 = 5 if sx > 0 else -15
    M_DRUM = wrap_paper(ribbon_x=gx + (10 if sx > 0 else -10), ribbon_z=gz - 5.5, rw=1.1)
    m.cube(bone, G(dx0, 3, -12), (10, 13, 13), M_DRUM, name=bone + "_tambor")
    bow_x = gx + (10 if sx > 0 else -10)
    m.cube(bone, (bow_x - 3.5, gy + 16, gz - 7), (3, 3, 3), M_GOLD_SOFT, rot=(0, 0, 25), origin=(bow_x, gy + 16, gz - 5.5),
           name=bone + "_laco_a")
    m.cube(bone, (bow_x + 0.5, gy + 16, gz - 7), (3, 3, 3), M_GOLD_SOFT, rot=(0, 0, -25), origin=(bow_x, gy + 16, gz - 5.5),
           name=bone + "_laco_b")
    m.cube(bone, (bow_x - 1, gy + 16, gz - 6.5), (2, 2, 2), M_GOLD_SOFT, name=bone + "_laco_no")
    # the belt from the drum into the feed port, hanging in a U
    s = 1 if sx > 0 else -1
    pts = [G(10 * s, 3, -2), G(9.5 * s, -2, -0.5), G(9 * s, -5, 3), G(8 * s, -3, 7), G(6.5 * s, 3, 7.5)]
    belt(m, bone, pts, s)


def muzzle_glow(ctx, L):
    """Barrel ends: a soot-black bore with a dull red heat deep inside."""
    if ctx.face == "north":
        th, tw = L.H.shape
        L.rid[:] = L.use(SOOT)
        L.alb[:] = 1
        L.rid[1:-1, 1:-1] = L.use(SOOT)
        L.alb[1:-1, 1:-1] = -4
        L.emask[th // 2, tw // 2 - 1] = True
        L.emit[th // 2, tw // 2 - 1] = (150, 36, 14)


def belt(m, bone, pts, s):
    pts = np.array(pts, float)
    # Catmull-Rom through the points, links every 1.7 units
    dense = []
    for i in range(len(pts) - 1):
        p0 = pts[max(i - 1, 0)]
        p1, p2 = pts[i], pts[i + 1]
        p3 = pts[min(i + 2, len(pts) - 1)]
        for t in np.linspace(0, 1, 20, endpoint=False):
            t2, t3 = t * t, t * t * t
            dense.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    dense.append(pts[-1])
    dense = np.array(dense)
    seg = np.linalg.norm(np.diff(dense, axis=0), axis=1)
    cum = np.r_[0, np.cumsum(seg)]
    k = 0
    for dist in np.arange(0.8, cum[-1] - 0.5, 1.7):
        i = np.searchsorted(cum, dist) - 1
        f = (dist - cum[i]) / max(seg[i], 1e-6)
        p = dense[i] * (1 - f) + dense[i + 1] * f
        t = dense[i + 1] - dense[i]
        t /= np.linalg.norm(t)
        xa = np.array([1.0, 0, 0])
        xa = xa - (xa @ t) * t
        xa /= np.linalg.norm(xa)
        ya = np.cross(t, xa)
        R = np.c_[xa, ya, t]
        rot = to_euler(R)
        m.cube(bone, (p[0] - 2, p[1] - 0.5, p[2] - 0.75), (4, 1, 2), Material_link(), [link_paint], rot=rot,
               origin=tuple(p), name="%s_elo_%d" % (bone, k))
        k += 1


def Material_link():
    return metal(base=GOLD, round=0.4, spec=0.7, scratches=0.0, grime=0.2)
