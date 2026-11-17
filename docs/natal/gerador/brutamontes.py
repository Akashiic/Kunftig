"""Noel Brutamontes: the brawler. Open coat, huge fists with gold knuckles, a sack of coal on his back."""

from bbtool import SIDES, Model, edge_dark, only, ragged, speckle, trim
from paleta import *


def head_face(f, cb):
    # 10 x 11: row 0 under the brim, beard from row 7 down
    dark = (40, 26, 24)
    brow = Mat("#8e8e96", "#7c7c84", "#a0a0a8")
    f.rect(0, 0, 9, 0, BEARD)  # hair under the brim
    f.px(4, 1, SKIN_D.cols[0])
    f.px(5, 1, SKIN_D.cols[0])
    for x, y in [(0, 2), (1, 2), (2, 2), (1, 3), (2, 3), (3, 3), (4, 3)]:
        f.px(x, y, brow.pick(f.rng))
    for x, y in [(9, 2), (8, 2), (7, 2), (8, 3), (7, 3), (6, 3), (5, 3)]:
        f.px(x, y, brow.pick(f.rng))
    for x in (1, 2, 3, 6, 7, 8):
        f.px(x, 4, (240, 240, 236))
    for x in (2, 3, 6, 7):
        f.px(x, 4, dark)
    f.px(3, 4, (150, 24, 24))
    f.px(6, 4, (150, 24, 24))
    for x in (1, 2, 3, 6, 7, 8):
        f.px(x, 5, SKIN_D.pick(f.rng))
    f.px(0, 6, (226, 140, 120))
    f.px(9, 6, (226, 140, 120))
    f.rect(0, 7, 9, 10, BEARD)


def head_sides(f, cb):
    if f.name in ("east", "west"):
        f.rect(0, 0, f.w - 1, 1, BEARD)
        # hair at the back half, ear in the middle
        back = range(0, 5) if f.name == "east" else range(5, 10)
        for x in back:
            f.rect(x, 2, x, 10, BEARD)
        ex = 5 if f.name == "east" else 3
        f.rect(ex, 4, ex + 1, 6, SKIN_D)
        f.rect(0, 7, f.w - 1, 10, BEARD)
    elif f.name == "south":
        f.rect(0, 0, f.w - 1, f.h - 1, BEARD)
    elif f.name == "down":
        f.rect(0, 0, f.w - 1, f.h - 1, BEARD)


def beard_paint(f, cb):
    for y in range(f.h):
        for x in range(f.w):
            if f.rng.random() < 0.25:
                f.tint(x, y, 0.88)
    if f.name == "north":
        # grimace under the moustache, with teeth
        for x in range(3, 8):
            f.px(x, 2, (70, 30, 28))
        for x in (4, 6):
            f.px(x, 2, (236, 230, 214))
        for x in range(4, 7):
            f.px(x, 3, (110, 50, 46))
    if f.name in SIDES:
        for x in range(f.w):
            for k in range(f.rng.randint(0, 2)):
                f.clear(x, f.h - 1 - k)
        for x in (0, f.w - 1):
            f.clear(x, f.h - 3)


def moustache(f, cb):
    if f.name == "north":
        f.px(0, 1, BEARD.cols[3])
        f.px(f.w - 1, 1, BEARD.cols[3])
        f.clear(0, 0)
        f.clear(f.w - 1, 0)
        f.px(f.w // 2, 0, BEARD.cols[1])


def torso_front(f, cb):
    if f.name != "north":
        if f.name == "south":
            for y in range(f.h):
                f.tint(f.w // 2, y, 0.85)
        return
    W, H = f.w, f.h  # 20 x 14
    m = W // 2
    L0, L1 = m - 4, m + 3  # skin from L0 to L1
    f.rect(L0, 0, L1, H - 1, SKIN)
    # fur along the coat edges, ragged inward
    for y in range(H):
        a = f.rng.randint(0, 1)
        f.rect(L0 - 2, y, L0 - 1 + a, y, FUR)
        b = f.rng.randint(0, 1)
        f.rect(L1 + 1 - b, y, L1 + 2, y, FUR)
    # chest hair
    for y in range(0, 5):
        for x in range(L0 + 1, L1):
            if f.rng.random() < 0.45 - y * 0.07:
                f.px(x, y, BEARD.pick(f.rng))
    # pecs and abs
    for x in range(L0 + 1, L1):
        f.px(x, 5, SKIN_D.cols[0])
    for y in range(3, H):
        f.px(m - 1, y, SKIN_D.cols[1] if y > 5 else SKIN_D.cols[0])
    for y in (8, 11):
        for x in range(L0 + 1, L1):
            if x != m - 1:
                f.px(x, y, SKIN_D.cols[0])
    for x, y in [(m - 3, 6), (m + 1, 6), (m - 3, 9), (m + 1, 9), (m - 3, 12), (m + 1, 12)]:
        f.px(x, y, (236, 200, 166))
    # buttons left on the open coat
    for y in (3, 7, 11):
        f.px(L0 - 4, y, GOLD.cols[0])


def belt_paint(f, cb):
    f.rect(0, 0, f.w - 1, 0, (28, 18, 12))
    if f.name == "north":
        m = f.w // 2
        f.rect(m - 3, 0, m + 2, f.h - 1, GOLD)
        f.rect(m - 1, 1, m, f.h - 2, (40, 26, 16))
        f.px(m - 3, 0, GOLD_D.cols[0])
        f.px(m + 2, f.h - 1, GOLD_D.cols[0])


def skirt_paint(f, cb):
    if f.name == "north":
        m = f.w // 2
        f.rect(m - 2, 0, m + 1, f.h - 1, RED_D)
        for y in range(f.h):
            f.px(m - 3, y, FUR.pick(f.rng))
            f.px(m + 2, y, FUR.pick(f.rng))


def glove_paint(f, cb):
    if f.name == "north":
        # four knuckles, gold duster across them
        for x in range(1, f.w - 1):
            f.px(x, 2, GOLD.pick(f.rng))
            f.px(x, 3, GOLD.pick(f.rng))
        for x in range(1, f.w - 1, 2):
            f.px(x, 1, GOLD_D.cols[0])
        for y in range(4, f.h):
            for x in (3, 6):
                if x < f.w:
                    f.px(x, y, GLOVE.cols[1])
    if f.name in ("east", "west"):
        f.rect(0, f.h - 3, f.w - 1, f.h - 3, GLOVE.cols[1])


def sack_paint(f, cb):
    for y in range(f.h):
        for x in range(f.w):
            if (x + y * 3) % 7 == 0:
                f.tint(x, y, 0.88)
    if f.name in ("south", "east"):
        x0, y0 = f.w // 3, f.h // 3
        f.rect(x0, y0, x0 + 4, y0 + 4, Mat("#6e5a3a", "#5e4d31"))
        for k in range(5):
            f.px(x0 + k, y0, (40, 30, 20))
            f.px(x0 + k, y0 + 4, (40, 30, 20))
    if f.name == "up":
        f.rect(1, 1, f.w - 2, f.h - 2, COAL)


def strap_paint(f, cb):
    if f.name == "north":
        for y in range(2, f.h, 6):
            f.px(1, y, GOLD.cols[1])
        for y in range(f.h):
            f.px(0, y, LEATHER.cols[1])
            f.px(f.w - 1, y, LEATHER.cols[1])


def gift_paint(f, cb):
    m = f.w // 2
    if f.name in ("north", "south", "up", "down"):
        f.rect(m - 1 if f.w > 3 else m, 0, m, f.h - 1, RIBBON)
    if f.name in ("east", "west", "up", "down"):
        mm = f.h // 2
        f.rect(0, mm - 1 if f.h > 3 else mm, f.w - 1, mm, RIBBON)


def build():
    m = Model("noel_brutamontes", seed=25)
    root = m.bone("raiz")
    body = m.bone("corpo", "raiz", (0, 15, 0))

    # legs
    for side, sx in (("d", 1), ("e", -1)):
        leg = m.bone("perna_" + side, "raiz", (4.5 * sx, 15, 0), (0, 0, 0))
        x0 = 1 if sx > 0 else -8
        m.cube(leg, (x0, 6, -3), (7, 9, 6), RED_D, [edge_dark()])
        m.cube(leg, (x0 - 0.5, 0, -4.5), (8, 5, 8), BOOT, [trim(SOLE, bottom=1, jag=0, cap=True)])
        m.cube(leg, (x0 - 0.5, 0, -6.5), (8, 3, 2), BOOT, [trim(SOLE, bottom=1, jag=0, cap=True)])
        m.cube(leg, (x0 - 1, 5, -5), (9, 2, 9), FUR, [speckle(FUR, 0.2)], inflate=0.25)

    # hips, belt, torso
    m.cube(body, (-9.5, 13, -5.5), (19, 6, 11), RED, [trim(FUR, bottom=2), skirt_paint, edge_dark()])
    m.cube(body, (-9.5, 19, -5.5), (19, 3, 11), LEATHER, [belt_paint], inflate=0.25)
    m.cube(body, (-10, 21, -6), (20, 14, 12), RED, [torso_front])
    m.cube(body, (-6, 34, -5), (12, 2, 10), FUR, [], inflate=0.3)  # collar
    m.cube(body, (-12, 14, -3), (4, 4, 4), GREEN, [gift_paint])  # a present grenade on the hip
    m.cube(
        body, (-1.5, 10, -6.9), (3, 30, 1), LEATHER, [strap_paint], rot=(0, 0, 36), origin=(0, 26, -6.4)
    )

    # head
    head = m.bone("cabeca", "corpo", (0, 35, -1))
    m.cube(head, (-5, 35, -6), (10, 11, 10), SKIN, [only(("north",), head_face), head_sides])
    m.cube(head, (-5.5, 30, -7), (11, 9, 3), BEARD, [beard_paint])
    m.cube(head, (-3.5, 27, -6.5), (7, 4, 2), BEARD, [beard_paint])
    m.cube(head, (-4.5, 38, -8), (9, 2, 1), BEARD, [moustache])
    m.cube(head, (-1, 39, -7), (2, 2, 1), SKIN, [only(("up",), lambda f, cb: None)])

    hat = m.bone("gorro", "cabeca", (0, 46, -1), (-6, 0, 4))
    m.cube(hat, (-6, 45, -7), (12, 3, 12), FUR, [speckle(FUR, 0.2)], inflate=0.25)
    m.cube(hat, (-5, 48, -6), (10, 4, 10), RED, [edge_dark()])
    hat2 = m.bone("gorro_meio", "gorro", (0, 52, -1), (-10, 0, -14))
    m.cube(hat2, (-4, 52, -5), (8, 4, 8), RED, [edge_dark()])
    hat3 = m.bone("gorro_ponta", "gorro_meio", (0, 56, -1), (-20, 0, -38))
    m.cube(hat3, (-3, 56, -4), (6, 4, 6), RED, [edge_dark()])
    m.cube(hat3, (-2, 60, -3), (4, 3, 4), RED)
    m.cube(hat3, (-2.5, 62, -3.5), (5, 5, 5), FUR, [speckle(FUR, 0.3)])

    # arms: boxer's guard, right fist high, left fist forward
    for side, sx, pose_up, pose_fore in (("d", 1, (35, 0, 10), (55, 0, 0)), ("e", -1, (12, 0, -12), (28, 0, 0))):
        arm = m.bone("braco_" + side, "corpo", (14 * sx, 31, -0.5), pose_up)
        xa = 9 if sx > 0 else -19
        m.cube(arm, (xa, 26, -6), (10, 10, 11), RED, [edge_dark()])
        m.cube(arm, (xa + 1.5, 18, -4.5), (7, 9, 8), RED, [edge_dark()])
        fore = m.bone("antebraco_" + side, "braco_" + side, (14 * sx, 19, -0.5), pose_fore)
        m.cube(fore, (xa + 1, 11, -4.5), (8, 8, 8), RED, [edge_dark()])
        m.cube(fore, (xa + 0.5, 9, -5), (9, 3, 9), FUR, [speckle(FUR, 0.2)], inflate=0.3)
        fist = m.bone("punho_" + side, "antebraco_" + side, (14 * sx, 9, -0.5))
        m.cube(fist, (xa, 1, -5.5), (10, 8, 10), GLOVE, [glove_paint])

    # the sack on his back, neck over the left shoulder
    sack = m.bone("saco", "corpo", (-3, 26, 9), (8, 0, 24))
    m.cube(sack, (-11, 15, 6), (16, 19, 10), BURLAP, [sack_paint, edge_dark()])
    m.cube(sack, (-6, 34, 8.5), (6, 3, 5), ROPE, [], inflate=0.2)
    m.cube(sack, (-7, 37, 7.5), (8, 3, 7), BURLAP, [ragged(1, SIDES, 0.5)])
    return m
