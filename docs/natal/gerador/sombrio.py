"""Noel Sombrio: the corrupted one. Gaunt and hunched, horns through a torn hat, glowing eyes, chains with bells,
a candy-cane scythe in one hand and a smouldering sack of coal dragged on a chain in the other."""

from bbtool import SIDES, Mat, Model, edge_dark, only, ragged, speckle, stripes, trim
from paleta import *

IDENT = "noel_sombrio"
SOCKET = (34, 24, 26)


def head_paint(f, cb):
    if f.name == "north":  # 9 x 11
        f.rect(0, 0, 8, 1, ASH)
        f.rect(0, 2, 8, 2, GRAY_SKIN.cols[1])
        for x in (1, 2, 3, 5, 6, 7):
            f.px(x, 2, (70, 58, 56))
        f.rect(1, 3, 3, 4, SOCKET)
        f.rect(5, 3, 7, 4, SOCKET)
        f.px(2, 3, EYE_GLOW2)
        f.px(6, 3, EYE_GLOW2)
        f.px(3, 3, EYE_GLOW)
        f.px(5, 3, EYE_GLOW)
        f.px(2, 4, (40, 120, 110))
        f.px(6, 4, (40, 120, 110))
        for x in (1, 2, 6, 7):
            f.px(x, 5, (110, 98, 92))
        f.px(0, 6, (96, 84, 80))
        f.px(8, 6, (96, 84, 80))
        f.rect(0, 8, 8, 10, ASH)
    elif f.name in ("east", "west"):
        f.rect(0, 0, f.w - 1, 2, ASH)
        back = range(0, 5) if f.name == "east" else range(4, 9)
        for x in back:
            for y in range(3, f.h):
                f.px(x, y, ASH.pick(f.rng))
        # pointed ear
        ex = 5 if f.name == "east" else 2
        f.rect(ex, 4, ex + 1, 6, GRAY_SKIN.cols[1])
        f.px(ex + (1 if f.name == "east" else 0), 3, GRAY_SKIN.cols[1])
    elif f.name in ("south", "down"):
        f.rect(0, 0, f.w - 1, f.h - 1, ASH)


def beard_paint(f, cb):
    for y in range(f.h):
        for x in range(f.w):
            if (x * 7 + y) % 5 == 0:
                f.tint(x, y, 0.8)
    if f.name == "north" and cb["size"][1] > 10:
        # a wide grin full of teeth, high on the beard
        for x in range(1, f.w - 1):
            f.px(x, 1, (30, 10, 12))
            f.px(x, 2, (30, 10, 12))
        for x in range(1, f.w - 1, 2):
            f.px(x, 1, BONE.cols[0])
        for x in range(2, f.w - 1, 2):
            f.px(x, 2, BONE.cols[1])
    if f.name in SIDES:
        for x in range(f.w):
            n = f.rng.choice((0, 1, 2, 3, 4)) if f.name == "north" else f.rng.choice((0, 1, 2))
            for k in range(n):
                f.clear(x, f.h - 1 - k)


def torso_paint(f, cb):
    if f.name == "north":  # 14 x 15
        m = f.w // 2
        for y in range(f.h):
            f.px(m - 1, y, FUR_DIRTY.pick(f.rng))
            f.px(m, y, FUR_DIRTY.pick(f.rng))
        # two chains crossing the chest
        for y in range(f.h):
            for x in (y - 1, f.w - y):
                if 0 <= x < f.w:
                    col = IRON.cols[2] if (y % 2 == 0) else IRON.cols[1]
                    f.px(x, y, col)
        # rips
        for x, y in [(2, 9), (3, 10), (11, 6), (12, 7)]:
            f.px(x, y, ROBE_D.cols[1])
    if f.name == "south":
        for x, y in [(3, 4), (4, 5), (5, 6), (9, 10), (10, 11)]:
            f.px(x, y, ROBE_D.cols[0])


def chain_belt(f, cb):
    for x in range(f.w):
        for y in range(f.h):
            f.px(x, y, IRON.cols[2] if (x + y) % 3 == 0 else (IRON.cols[1] if (x + y) % 3 == 1 else (40, 40, 44)))


def bell(f, cb):
    if f.name in SIDES:
        f.px(f.w // 2, f.h - 1, (60, 40, 10))


def sack_paint(f, cb):
    for y in range(f.h):
        for x in range(f.w):
            if f.rng.random() < 0.05:
                f.px(x, y, EMBER.pick(f.rng))
    if f.name in SIDES:
        # glowing cracks
        x = f.rng.randint(2, max(2, f.w - 3))
        for y in range(2, f.h - 2):
            x += f.rng.choice((-1, 0, 0, 1))
            f.px(x, y, EMBER.pick(f.rng))
    if f.name == "up":
        f.rect(1, 1, f.w - 2, f.h - 2, COAL)
        for _ in range(4):
            f.px(f.rng.randint(1, f.w - 2), f.rng.randint(1, f.h - 2), EMBER.cols[0])


def blade_paint(f, cb):
    if f.name in ("north", "south"):
        for x in range(f.w):
            f.px(x, f.h - 1, EYE_GLOW2)
            f.px(x, f.h - 2, EYE_GLOW)
        for x in range(0, f.w, 3):
            f.px(x, 1, STEEL.cols[2])
    if f.name == "down":
        f.rect(0, 0, f.w - 1, f.h - 1, EYE_GLOW2)


def hoof_paint(f, cb):
    if f.name == "north":
        for y in range(f.h):
            f.px(f.w // 2, y, (8, 6, 6))


def build():
    m = Model(IDENT, seed=666)
    m.bone("raiz")

    # goat legs and hooves under the robe
    for side, sx in (("d", 1), ("e", -1)):
        leg = m.bone("perna_" + side, "raiz", (3.5 * sx, 12, 0))
        x0 = 1.5 if sx > 0 else -5.5
        m.cube(leg, (x0, 3, -2), (4, 10, 4), Mat("#2a221e", "#1e1814", "#352b25"), [speckle(ASH, 0.05)])
        m.cube(leg, (x0 - 0.5, 0, -3.5), (5, 3, 6), BLACK, [hoof_paint])

    saia = m.bone("saia", "raiz", (0, 22, 0))
    m.cube(saia, (-9, 6, -6), (18, 16, 13), ROBE, [ragged(4), trim(FUR_DIRTY, bottom=2, jag=1), edge_dark()])
    m.cube(saia, (-1.5, 6, -6.3), (3, 16, 1), ROBE_D, [ragged(5)])

    body = m.bone("corpo", "raiz", (0, 22, 0), (-14, 0, 0))
    m.cube(body, (-7.5, 21, -5.5), (15, 2, 11), IRON, [chain_belt], inflate=0.2)
    m.cube(body, (-7, 22, -5), (14, 15, 10), ROBE, [torso_paint, edge_dark()])
    m.cube(body, (-8, 34, -6), (16, 3, 12), FUR_DIRTY, [ragged(1)], inflate=0.2)  # mantle collar
    for i, bx in enumerate((-5, -1, 3)):
        m.cube(body, (bx, 18 - (i % 2) * 2, -6.5), (2, 3, 2), GOLD, [bell])
    cape = m.bone("capa", "corpo", (0, 36, 5), (12, 0, 0))
    m.cube(cape, (-8, 8, 5), (16, 28, 1), ROBE_D, [ragged(6, chance=0.7), speckle(ROBE, 0.15)])

    head = m.bone("cabeca", "corpo", (0, 37, -3), (16, 0, 0))
    m.cube(head, (-4.5, 37, -8), (9, 11, 9), GRAY_SKIN, [head_paint])
    m.cube(head, (-0.5, 39, -10), (1, 4, 2), GRAY_SKIN, [], rot=(-15, 0, 0), origin=(0, 42, -9))  # hooked nose
    m.cube(head, (-5, 24, -10), (10, 16, 3), ASH, [beard_paint])
    m.cube(head, (-3, 18, -9.5), (6, 7, 2), ASH, [beard_paint])
    m.cube(head, (-4, 39, -11), (8, 2, 1), ASH, [])
    m.cube(head, (-5, 31, -11), (1, 9, 1), ASH, [ragged(2)])
    m.cube(head, (4, 31, -11), (1, 9, 1), ASH, [ragged(2)])

    hat = m.bone("gorro", "cabeca", (0, 48, -3.5), (-4, 0, 0))
    m.cube(hat, (-5.5, 47, -9), (11, 3, 11), FUR_DIRTY, [ragged(1), speckle(ASH, 0.2)], inflate=0.25)
    m.cube(hat, (-4.5, 50, -8), (9, 4, 9), ROBE, [edge_dark()])
    hat2 = m.bone("gorro_2", "gorro", (0, 54, -3.5), (42, 0, 6))
    m.cube(hat2, (-3.5, 53, -7), (7, 5, 7), ROBE, [edge_dark(), ragged(1)])
    hat3 = m.bone("gorro_3", "gorro_2", (0, 58, -3.5), (48, 0, 4))
    m.cube(hat3, (-2.5, 57, -6), (5, 6, 5), ROBE, [edge_dark()])
    hat4 = m.bone("gorro_4", "gorro_3", (0, 63, -3.5), (40, 0, 0))
    m.cube(hat4, (-1.5, 62, -5), (3, 5, 3), ROBE)
    m.cube(hat4, (-2, 67, -5.5), (4, 4, 4), GOLD, [bell])

    # horns breaking through the hat, curling up and out
    segs = [((5, 4, 4), HORN, (0, -20, 16)), ((5, 3, 3), HORN, (0, 0, 34)), ((4, 2, 2), HORN, (0, 10, 38)),
            ((3, 1, 1), BONE, (0, 0, 30))]
    for side, sx in (("d", 1), ("e", -1)):
        parent, px = "cabeca", 4.0
        for k, (size, mat, rot) in enumerate(segs):
            name = "chifre%d_%s" % (k + 1, side)
            m.bone(name, parent, (px * sx, 49, -3.5), (rot[0], rot[1] * sx, rot[2] * sx))
            w, h, d = size
            x0 = px - 0.5 if sx > 0 else -(px - 0.5) - w
            m.cube(name, (x0, 49 - h / 2, -3.5 - d / 2), size, mat, [edge_dark()] if k < 3 else [])
            parent, px = name, px - 0.5 + w

    # arms: long sleeves, grey hands with claws
    for side, sx, up, fore in (("d", 1, (35, 0, 6), (25, 0, 0)), ("e", -1, (-6, 0, -14), (10, 0, 0))):
        arm = m.bone("braco_" + side, "corpo", (9 * sx, 34, -0.5), up)
        xa = 6.5 if sx > 0 else -12.5
        m.cube(arm, (xa, 26, -3.5), (6, 10, 6), ROBE, [edge_dark()])
        m.cube(arm, (xa - 0.5, 33, -4), (7, 4, 7), FUR_DIRTY, [ragged(1)], inflate=0.2)
        fore_b = m.bone("antebraco_" + side, "braco_" + side, (9.5 * sx, 26, -0.5), fore)
        m.cube(fore_b, (xa - 0.5, 16, -4), (7, 10, 7), ROBE, [ragged(3), edge_dark()])
        hand = m.bone("mao_" + side, "antebraco_" + side, (9.5 * sx, 17, -0.5))
        m.cube(hand, (xa + 0.5, 11, -2.5), (5, 6, 5), GRAY_SKIN, [])
        for k, cz in enumerate((-2.5, -0.5, 1.5)):
            m.cube(hand, (xa + (4.2 if sx > 0 else 0.3) + 0.0, 6, cz), (1, 5, 1), BONE, [],
                   rot=(-12 + k * 4, 0, 0), origin=(xa + 2.5, 11, cz))

    # candy-cane scythe in the right hand, kept upright against the arm's pose, blade sweeping outward
    sc = m.bone("foice", "mao_d", (9.5, 13, -0.5), (-46, 0, -4))
    m.cube(sc, (8.5, -3, -1.5), (2, 45, 2), CANE_W, [stripes(CANE_R, CANE_W, 6, 1)])
    m.cube(sc, (8.5, 42, -8.5), (2, 2, 9), CANE_W, [stripes(CANE_R, CANE_W, 6, 1)])
    m.cube(sc, (8.5, 36, -8.5), (2, 6, 2), CANE_W, [stripes(CANE_R, CANE_W, 6, 1)])
    m.cube(sc, (8, 38, -2), (3, 4, 3), IRON, [])
    blade = m.bone("lamina", "foice", (10.5, 40, -0.5), (0, 8, -18))
    m.cube(blade, (10.5, 37, -1), (17, 5, 1), STEEL, [blade_paint])
    blade2 = m.bone("lamina_ponta", "lamina", (27.5, 39, -0.5), (0, 0, -32))
    m.cube(blade2, (27.5, 36, -1), (8, 4, 1), STEEL, [blade_paint])
    blade3 = m.bone("lamina_bico", "lamina_ponta", (35.5, 38, -0.5), (0, 0, -30))
    m.cube(blade3, (35.5, 36, -1), (4, 2, 1), STEEL, [blade_paint])

    # the sack lies on the ground beside him, chained to the left hand
    ch = m.bone("corrente", "mao_e", (-9.5, 11, -0.5), (0, 0, -55))
    for k in range(3):
        m.cube(ch, (-10, 8 - k * 3, -1), (1, 3, 1), IRON, [], inflate=0.1 if k % 2 else 0)
    sack = m.bone("saco", "raiz", (-18, 0, 3), (0, 25, 6))
    m.cube(sack, (-24, 0, -2), (13, 12, 11), Mat("#2b2522", "#221d1a", "#342c28", "#191513"), [sack_paint])
    m.cube(sack, (-20, 12, 1), (5, 2, 5), ROPE, [], inflate=0.2)
    m.cube(sack, (-21, 14, 0), (7, 2, 7), Mat("#2b2522", "#221d1a"), [ragged(1)])
    return m
