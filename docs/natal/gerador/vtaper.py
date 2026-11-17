"""Noel V-Taper: tall, shoulders twice as wide as the waist, bare chest crossed by bandoliers, dark shades and a
rotary machine gun in each hand, fired from the hip; a sack of ammo hangs from the belt."""

from bbtool import SIDES, Mat, Model, edge_dark, only, ragged, speckle, stripes, trim
from paleta import *

IDENT = "noel_vtaper"
TAN = Mat("#d39a6c", "#c98f62", "#dca779", w=[6, 2, 2], grad=0.08)
TAN_D = (170, 112, 74)
TAN_DD = (146, 94, 60)
TAN_L = (232, 184, 142)
GUN = Mat("#3a3f46", "#30353b", "#454b53", grad=0.12)
GUN_D = Mat("#24282d", "#1d2125")
SHADES = (22, 22, 26)


def pecs(f, cb):
    if f.name == "north":  # 22 x 7
        m = f.w // 2
        for y in range(f.h):
            f.px(m - 1, y, TAN_D)
            f.px(m, y, TAN_DD if y > 1 else TAN_D)
        for x in range(2, f.w - 2):
            f.px(x, f.h - 1, TAN_D)
        for x in (3, 4, 5, f.w - 4, f.w - 5, f.w - 6):
            f.px(x, 2, TAN_L)
        for y in range(1, f.h):
            f.px(1, y, TAN_D)
            f.px(f.w - 2, y, TAN_D)
        for x in range(m - 3, m + 3):
            if f.rng.random() < 0.5:
                f.px(x, 0, BEARD.pick(f.rng))
    if f.name == "south":
        m = f.w // 2
        for y in range(f.h):
            f.px(m, y, TAN_D)
        for x in (4, 5, f.w - 5, f.w - 6):
            f.px(x, 2, TAN_D)


def serratus(f, cb):
    if f.name == "north":  # 18 x 6
        m = f.w // 2
        for y in range(f.h):
            f.px(m - 1, y, TAN_D)
        for x in range(m - 4, m + 3):
            if x != m - 1:
                f.px(x, 2, TAN_D)
        for y in (1, 3, 5):
            f.px(1, y, TAN_DD)
            f.px(2, y + 0 if y < 5 else y, TAN_D)
            f.px(f.w - 2, y, TAN_DD)
            f.px(f.w - 3, y, TAN_D)
    if f.name == "south":
        m = f.w // 2
        for y in range(f.h):
            f.px(m - 1, y, TAN_D)
        for y in range(f.h):
            f.px(2 + y // 2, y, TAN_D)
            f.px(f.w - 3 - y // 2, y, TAN_D)


def abs_paint(f, cb):
    if f.name == "north":  # 14 x 7
        m = f.w // 2
        for y in range(f.h):
            f.px(m - 1, y, TAN_DD)
        for y in (1, 4):
            for x in range(m - 4, m + 3):
                if x != m - 1:
                    f.px(x, y, TAN_D)
        for x, y in [(m - 3, 2), (m + 1, 2), (m - 3, 5), (m + 1, 5)]:
            f.px(x, y, TAN_L)
        for y in range(f.h):
            f.px(1, y, TAN_D)
            f.px(f.w - 2, y, TAN_D)
        f.px(2, f.h - 1, TAN_DD)
        f.px(f.w - 3, f.h - 1, TAN_DD)
    if f.name == "south":
        for y in range(f.h):
            f.px(f.w // 2 - 1, y, TAN_D)


def delt(f, cb):
    if f.name in SIDES:
        for x in range(f.w):
            f.px(x, 0, TAN_L)
            f.px(x, f.h - 1, TAN_D)


def bicep(f, cb):
    if f.name in SIDES:
        for x in range(f.w):
            f.px(x, f.h - 1, TAN_D)
        if f.name in ("east", "west"):
            for y in range(2, f.h - 2):
                f.px(f.w // 2, y, TAN_D)


def head_paint(f, cb):
    if f.name == "north":  # 9 x 10
        f.rect(0, 0, 8, 1, BEARD)
        for x in range(0, 9):
            f.px(x, 3, SHADES)
        f.rect(1, 4, 3, 4, SHADES)
        f.rect(5, 4, 7, 4, SHADES)
        f.px(1, 3, (230, 230, 240))
        f.px(5, 3, (230, 230, 240))
        f.px(4, 2, TAN_D)
        f.px(2, 2, BEARD.cols[3])
        f.px(6, 2, BEARD.cols[3])
        f.px(4, 5, TAN_D)
        f.rect(0, 6, 8, 9, BEARD)
    elif f.name in ("east", "west"):
        f.rect(0, 0, f.w - 1, 1, BEARD)
        tx = range(5, 9) if f.name == "east" else range(0, 4)
        for x in tx:
            f.px(x, 3, SHADES)
        back = range(0, 4) if f.name == "east" else range(5, 9)
        for x in back:
            for y in range(2, f.h):
                f.px(x, y, BEARD.pick(f.rng))
        f.rect(0, 7, f.w - 1, 9, BEARD)
    elif f.name in ("south", "down"):
        f.rect(0, 0, f.w - 1, f.h - 1, BEARD)
        if f.name == "south":
            for x in range(f.w):
                f.px(x, 3, SHADES)


def beard_paint(f, cb):
    for y in range(f.h):
        for x in range(f.w):
            if f.rng.random() < 0.22:
                f.tint(x, y, 0.88)
    if f.name == "north" and cb["size"][1] >= 8:
        for x in range(3, f.w - 3):
            f.px(x, 1, (70, 30, 28))
        f.px(f.w - 4, 1, (236, 230, 214))
    if f.name in SIDES:
        for x in range(f.w):
            for k in range(f.rng.randint(0, 2)):
                f.clear(x, f.h - 1 - k)


def bandolier(f, cb):
    if f.name in ("north", "south"):
        for y in range(1, f.h, 2):
            f.px(1, y, GOLD.cols[1])
            if y + 1 < f.h:
                f.px(1, y + 1, GOLD_D.cols[0])
        for y in range(f.h):
            f.px(0, y, LEATHER.cols[1])
            f.px(f.w - 1, y, LEATHER.cols[1])


def belt_paint(f, cb):
    f.rect(0, 0, f.w - 1, 0, (28, 18, 12))
    if f.name == "north":
        m = f.w // 2
        f.rect(m - 3, 0, m + 2, f.h - 1, GOLD)
        f.rect(m - 1, 1, m, f.h - 2, (40, 26, 16))


def ring(f, cb):
    stripes(CANE_R, CANE_W, 4, 1)(f, cb)


def ammo_box(f, cb):
    if f.name in SIDES:
        f.rect(0, 0, f.w - 1, 0, GOLD_D)
        if f.w >= 4:
            f.rect(1, 2, f.w - 2, 2, GOLD)


def receiver(f, cb):
    if f.name in ("east", "west"):
        for x in range(f.w):
            f.px(x, 0, GOLD.cols[0] if x % 3 else GOLD_D.cols[0])
        for x in range(1, f.w - 1, 4):
            f.px(x, f.h // 2, GUN_D.cols[0])
    if f.name == "north":
        f.rect(1, 1, f.w - 2, f.h - 2, GUN_D)


def sack_paint(f, cb):
    for y in range(f.h):
        for x in range(f.w):
            if (x + y * 3) % 7 == 0:
                f.tint(x, y, 0.88)
    if f.name == "up":
        for y in range(1, f.h - 1):
            for x in range(1, f.w - 1):
                f.px(x, y, GOLD.cols[1] if (x + y) % 2 else GOLD_D.cols[0])


def gun(m, bone, cx, cy, cz, side):
    """A rotary machine gun held at (cx, cy, cz): receiver over the hand, six barrels forward (-Z)."""
    m.cube(bone, (cx - 4, cy + 2.5, cz - 8), (8, 8, 20), GUN, [receiver])
    m.cube(bone, (cx - 1, cy + 11.5, cz - 5), (2, 1, 12), GUN_D, [])
    m.cube(bone, (cx - 1, cy + 10.5, cz - 5), (2, 2, 1), GUN_D, [])
    m.cube(bone, (cx - 1, cy + 10.5, cz + 6), (2, 2, 1), GUN_D, [])
    m.cube(bone, (cx - 3, cy + 3.5, cz + 12), (6, 6, 3), GUN_D, [])  # back plate
    m.cube(bone, (cx - 1.5, cy - 4, cz + 5), (3, 7, 3), GUN_D, [], rot=(-15, 0, 0), origin=(cx, cy + 2.5, cz + 6.5))
    ax, ay = cx, cy + 6.5
    for ox, oy in [(-3, 0), (3, 0), (-1.5, 2.6), (1.5, 2.6), (-1.5, -2.6), (1.5, -2.6)]:
        m.cube(bone, (ax + ox - 1, ay + oy - 1, cz - 32), (2, 2, 24), STEEL, [])
    m.cube(bone, (ax - 1, ay - 1, cz - 30), (2, 2, 22), GUN_D, [])
    m.cube(bone, (ax - 4.5, ay - 4.5, cz - 14), (9, 9, 3), CANE_W, [ring])
    m.cube(bone, (ax - 4.5, ay - 4.5, cz - 31), (9, 9, 2), CANE_W, [ring])
    # ammo box on the outer side, belt drooping from it
    bx = cx + 4 if side > 0 else cx - 10
    m.cube(bone, (bx, cy + 1.5, cz - 4), (6, 8, 9), GREEN, [ammo_box])
    for k in range(5):
        m.cube(bone, (bx + 2.5, cy + 0.5 - k * 2, cz + 1 + k * 1.6), (1, 2, 2), GOLD, [])


def build():
    m = Model(IDENT, seed=2026)
    m.bone("raiz")

    for side, sx in (("d", 1), ("e", -1)):
        leg = m.bone("perna_" + side, "raiz", (5 * sx, 41, 0), (0, 0, 5 * sx))
        X = lambda x, w: x if sx > 0 else -(x + w)
        m.cube(leg, (X(1, 9), 25, -4.5), (9, 16, 9), RED, [edge_dark()])
        m.cube(leg, (X(1.5, 8), 9, -4), (8, 16, 8), RED, [edge_dark()])
        m.cube(leg, (X(1, 9), 0, -5), (9, 9, 9), BOOT, [trim(SOLE, bottom=1, jag=0, cap=True)])
        m.cube(leg, (X(1, 9), 0, -7), (9, 4, 2), BOOT, [trim(SOLE, bottom=1, jag=0, cap=True)])
        m.cube(leg, (X(0.5, 10), 8, -5.5), (10, 3, 10), FUR, [speckle(FUR, 0.2)], inflate=0.25)

    body = m.bone("corpo", "raiz", (0, 44, 0))
    m.cube(body, (-8.5, 40, -5), (17, 6, 10), RED, [edge_dark()])
    m.cube(body, (-8.5, 45, -5.5), (17, 3, 11), LEATHER, [belt_paint], inflate=0.2)
    m.cube(body, (-7, 47, -4.5), (14, 8, 9), TAN, [abs_paint])
    m.cube(body, (-9, 55, -5), (18, 6, 10), TAN, [serratus])
    m.cube(body, (-12, 61, -5.5), (24, 7, 11), TAN, [pecs])
    m.cube(body, (-13, 55, -1), (26, 9, 6), TAN, [only(("east", "west", "south"), serratus)])  # lats
    m.cube(body, (-6, 67, -3), (12, 3, 7), TAN, [])  # traps
    for rz in (34, -34):
        m.cube(body, (-1.5, 42, -6.7), (3, 34, 1), LEATHER, [bandolier], rot=(0, 0, rz), origin=(0, 59, -6.2))
        m.cube(body, (-1.5, 42, 5.7), (3, 34, 1), LEATHER, [bandolier], rot=(0, 0, rz), origin=(0, 59, 6.2))
    # sack of ammo on the left hip
    sack = m.bone("saco", "corpo", (-11, 42, 0), (0, 0, -8))
    m.cube(sack, (-15, 30, -3), (7, 11, 7), BURLAP, [sack_paint, edge_dark()])
    m.cube(sack, (-13.5, 41, -1.5), (4, 2, 4), ROPE, [], inflate=0.2)

    head = m.bone("cabeca", "corpo", (0, 69, -0.5))
    m.cube(head, (-3, 66, -3), (6, 5, 6), TAN, [])
    m.cube(head, (-4.5, 70, -5), (9, 10, 9), TAN, [head_paint])
    m.cube(head, (-5, 63, -6.5), (10, 9, 2), BEARD, [beard_paint])
    m.cube(head, (-3, 60, -6.3), (6, 4, 2), BEARD, [beard_paint])
    m.cube(head, (-3.5, 71.5, -7), (7, 2, 1), BEARD, [])
    hat = m.bone("gorro", "cabeca", (0, 80, -0.5), (-8, 0, 6))
    m.cube(hat, (-5.5, 79, -6), (11, 3, 11), FUR, [speckle(FUR, 0.2)], inflate=0.25)
    m.cube(hat, (-4.5, 82, -5), (9, 4, 9), RED, [edge_dark()])
    hat2 = m.bone("gorro_meio", "gorro", (0, 86, -0.5), (-16, 0, -18))
    m.cube(hat2, (-3.5, 86, -4), (7, 4, 7), RED, [edge_dark()])
    hat3 = m.bone("gorro_ponta", "gorro_meio", (0, 90, -0.5), (-28, 0, -34))
    m.cube(hat3, (-2.5, 90, -3), (5, 3, 5), RED)
    m.cube(hat3, (-2, 93, -2.5), (4, 4, 4), FUR, [speckle(FUR, 0.3)])

    # arms: hip fire, forearms forward, a machine gun in each hand
    for side, sx in (("d", 1), ("e", -1)):
        X = lambda x, w: x if sx > 0 else -(x + w)
        arm = m.bone("braco_" + side, "corpo", (16.5 * sx, 64, 0), (6, 0, 14 * sx))
        m.cube(arm, (X(11.5, 10), 57, -5), (10, 10, 10), TAN, [delt])
        m.cube(arm, (X(12.5, 8), 47, -4.5), (8, 13, 9), TAN, [bicep])
        m.cube(arm, (X(13, 7), 50, -6), (7, 7, 2), TAN, [])
        fore = m.bone("antebraco_" + side, "braco_" + side, (16.5 * sx, 48, 0), (80, 0, 0))
        m.cube(fore, (X(13, 7), 36, -4), (7, 12, 8), TAN, [bicep])
        m.cube(fore, (X(12.5, 8), 34, -4.5), (8, 3, 9), RED, [trim(FUR, top=1, bottom=1, jag=0)], inflate=0.3)
        hand = m.bone("mao_" + side, "antebraco_" + side, (16.5 * sx, 32, 0))
        m.cube(hand, (X(13.5, 6), 29, -3), (6, 6, 6), BLACK, [])
        g = m.bone("metralhadora_" + side, "mao_" + side, (16.5 * sx, 32, 0), (-86, 0, 0))
        gun(m, g, 16.5 * sx, 32, 0, sx)
    return m
