"""Noel do Gelo Eterno: the frozen giant. Frost-bitten coat, ice crystals on the shoulders, an ice crown on the hat,
an icicle beard, a hammer whose head is a frozen present resting on his shoulder and an ice orb in the other hand."""

from bbtool import SIDES, Mat, Model, edge_dark, only, ragged, speckle, trim
from paleta import *

IDENT = "noel_gelo_eterno"
GLOVE_B = Mat("#2c3f57", "#243549", "#354c68", grad=0.12)
BELT = Mat("#1f2530", "#161b24", "#29303c")
EYE = (210, 250, 255)
EYE2 = (90, 200, 255)


def frost(amount=0.12):
    return speckle(SNOW, amount, SIDES + ("up",))


def head_paint(f, cb):
    if f.name == "north":  # 11 x 11
        f.rect(0, 0, 10, 0, SNOW)
        brow = ICE_D
        for x, y in [(1, 2), (2, 2), (3, 2), (2, 3), (3, 3), (4, 3), (9, 2), (8, 2), (7, 2), (8, 3), (7, 3), (6, 3)]:
            f.px(x, y, brow.pick(f.rng))
        f.rect(1, 4, 9, 4, (44, 66, 92))
        f.px(5, 4, PALE_SKIN.cols[1])
        for x in (2, 3, 7, 8):
            f.px(x, 4, EYE)
        f.px(3, 4, EYE2)
        f.px(7, 4, EYE2)
        for x in (2, 3, 7, 8):
            f.px(x, 5, (150, 170, 190))
        f.rect(0, 7, 10, 10, SNOW)
    elif f.name in ("east", "west"):
        f.rect(0, 0, f.w - 1, 1, SNOW)
        back = range(0, 6) if f.name == "east" else range(5, 11)
        for x in back:
            for y in range(2, f.h):
                f.px(x, y, SNOW.pick(f.rng))
        f.rect(0, 8, f.w - 1, 10, SNOW)
    elif f.name in ("south", "down"):
        f.rect(0, 0, f.w - 1, f.h - 1, SNOW)


def beard_paint(f, cb):
    for y in range(f.h):
        for x in range(f.w):
            if f.rng.random() < 0.18:
                f.px(x, y, ICE.pick(f.rng))
    if f.name == "north" and cb["size"][1] >= 9:
        for x in range(3, f.w - 3):
            f.px(x, 2, (40, 70, 100))
        for x in range(4, f.w - 4, 2):
            f.px(x, 2, ICE_CORE.cols[0])


def icicle(f, cb):
    if f.name in SIDES:
        for y in range(f.h):
            f.px(0, y, ICE_CORE.cols[0])
        if f.w > 1:
            f.clear(f.w - 1, f.h - 1)


def crystal(f, cb):
    if f.name in SIDES:
        for y in range(f.h):
            f.px(0, y, ICE_CORE.cols[0])
            if f.w > 2:
                f.px(f.w - 1, y, ICE_D.cols[0])


def coat_front(f, cb):
    if f.name == "north":
        m = f.w // 2
        for y in range(f.h):
            for x in (m - 2, m - 1, m, m + 1):
                f.px(x, y, SNOW.pick(f.rng))
        for y in range(2, f.h, 4):
            f.px(m - 4, y, ICE.cols[2])
    if f.name in SIDES + ("up",):
        # frost creeping up from the bottom and down from the shoulders
        for x in range(f.w):
            n = f.rng.randint(0, 3)
            for k in range(n):
                f.px(x, f.h - 1 - k, SNOW.pick(f.rng) if k == 0 else ICE.pick(f.rng))


def belt_paint(f, cb):
    if f.name == "north":
        m = f.w // 2
        f.rect(m - 3, 0, m + 2, f.h - 1, ICE)
        f.rect(m - 1, 1, m, f.h - 2, ICE_CORE)
        f.px(m - 3, 0, ICE_D.cols[0])
        f.px(m + 2, f.h - 1, ICE_D.cols[0])


def gift_paint(f, cb):
    w, h = f.w, f.h
    if f.name in ("north", "south", "up", "down"):
        m = w // 2
        f.rect(m - 1, 0, m, h - 1, RIBBON)
    if f.name in ("east", "west", "up", "down"):
        mm = h // 2 if f.name in ("east", "west") else f.h // 2
        f.rect(0, mm - 1, w - 1, mm, RIBBON)
    # ice crust on the edges
    for x in range(w):
        if f.rng.random() < 0.6:
            f.px(x, 0, ICE.pick(f.rng))
        if f.rng.random() < 0.4:
            f.px(x, h - 1, ICE.pick(f.rng))
    for y in range(h):
        if f.rng.random() < 0.5:
            f.px(0, y, ICE.pick(f.rng))
        if f.rng.random() < 0.5:
            f.px(w - 1, y, ICE.pick(f.rng))


def handle_paint(f, cb):
    if f.name in SIDES:
        for y in range(0, f.h, 5):
            f.px(0, y, RIBBON.cols[0])
            f.px(f.w - 1, y, RIBBON.cols[0])
            if y + 1 < f.h:
                f.px(f.w - 1, y + 1, RIBBON.cols[2])


def orb_paint(f, cb):
    m = f.w // 2
    for k in range(f.w):
        f.px(k, m, (255, 255, 255))
        f.px(m, k, (255, 255, 255))
    for k in range(1, f.w - 1):
        f.px(k, k, (200, 245, 255))
        f.px(f.w - 1 - k, k, (200, 245, 255))
    f.px(0, 0, ICE_D.cols[0])
    f.px(f.w - 1, 0, ICE_D.cols[0])
    f.px(0, f.h - 1, ICE_D.cols[0])
    f.px(f.w - 1, f.h - 1, ICE_D.cols[0])


def frame_only(f, cb):
    """Keeps the edges of each face: a cage of ice around the orb."""
    for y in range(f.h):
        for x in range(f.w):
            if 0 < x < f.w - 1 and 0 < y < f.h - 1:
                f.clear(x, y)


def build():
    m = Model(IDENT, seed=1225)
    m.bone("raiz")

    for side, sx in (("d", 1), ("e", -1)):
        leg = m.bone("perna_" + side, "raiz", (5.5 * sx, 18, 0))
        x0 = 1.5 if sx > 0 else -9.5
        m.cube(leg, (x0, 9, -4), (8, 10, 8), FROST_RED, [frost(0.04), edge_dark()])
        m.cube(leg, (x0 - 0.5, 0, -5), (9, 8, 9), GLOVE_B, [trim(Mat("#151b24"), bottom=1, jag=0, cap=True)])
        m.cube(leg, (x0 - 0.5, 0, -7), (9, 3, 2), GLOVE_B, [trim(Mat("#151b24"), bottom=1, jag=0, cap=True)])
        m.cube(leg, (x0 - 1, 7, -5.5), (10, 3, 10), SNOW, [], inflate=0.25)
        m.cube(leg, (x0 + 1, 3, -7.5), (2, 3, 1), ICE, [crystal])  # ice on the boot

    body = m.bone("corpo", "raiz", (0, 20, 0))
    m.cube(body, (-12, 12, -7), (24, 9, 14), FROST_RED, [trim(SNOW, bottom=2), coat_front, edge_dark()])
    m.cube(body, (-12, 20, -7), (24, 4, 14), BELT, [belt_paint], inflate=0.25)
    m.cube(body, (-12, 23, -7), (24, 16, 14), FROST_RED, [coat_front])
    m.cube(body, (-7, 38, -6), (14, 2, 12), SNOW, [], inflate=0.3)
    # icicles hanging from the hem
    for k, (ix, ln) in enumerate([(-10, 3), (-7, 2), (-4, 4), (3, 3), (6, 2), (9, 4)]):
        m.cube(body, (ix, 12 - ln, -7.4), (1, ln, 1), ICE, [icicle])
        m.cube(body, (ix - 1, 12 - ln, 6.4), (1, max(1, ln - 1), 1), ICE, [icicle])

    head = m.bone("cabeca", "corpo", (0, 39, -2))
    m.cube(head, (-5.5, 39, -8), (11, 11, 11), PALE_SKIN, [head_paint])
    m.cube(head, (-6.5, 32, -9.5), (13, 10, 3), SNOW, [beard_paint, ragged(2)])
    m.cube(head, (-5, 42, -10.5), (10, 2, 1), ICE, [])
    m.cube(head, (-1, 43, -9.5), (2, 2, 1), Mat("#b8c8da"), [])
    # icicle beard
    for ix, ln, wd in [(-6, 6, 2), (-3.5, 9, 2), (-1, 11, 2), (1.5, 8, 2), (4, 5, 2), (-5, 3, 1), (3, 4, 1)]:
        m.cube(head, (ix, 32 - ln, -9), (wd, ln, 2), ICE, [icicle])
        m.cube(head, (ix + (0.5 if wd == 2 else 0), 32 - ln - 2, -8.5), (1, 2, 1), ICE_CORE, [])

    hat = m.bone("gorro", "cabeca", (0, 50, -2.5), (-6, 0, -3))
    m.cube(hat, (-6.5, 49, -9), (13, 3, 13), SNOW, [speckle(ICE, 0.15)], inflate=0.25)
    # crown of ice on the brim
    spikes = [(-1, -9.6, 6), (-4.5, -9.6, 4), (2.5, -9.6, 4), (-6.6, -6, 3), (5.6, -6, 3), (-6.6, -1, 4),
              (5.6, -1, 4), (-6.6, 3, 3), (5.6, 3, 3), (-3, 3.6, 3), (1, 3.6, 3)]
    for sxp, szp, h in spikes:
        m.cube(hat, (sxp, 52, szp), (2 if abs(sxp) < 6 else 1, h, 1 if abs(sxp) < 6 else 2), ICE, [crystal])
    m.cube(hat, (-1, 58, -9.8), (2, 2, 1), ICE_CORE, [])
    m.cube(hat, (-5.5, 52, -8), (11, 4, 11), FROST_RED, [edge_dark(), frost(0.05)])
    hat2 = m.bone("gorro_2", "gorro", (0, 56, -2.5), (-14, 0, 10))
    m.cube(hat2, (-4.5, 56, -7), (9, 4, 9), FROST_RED, [edge_dark(), frost(0.05)])
    hat3 = m.bone("gorro_3", "gorro_2", (0, 60, -2.5), (-24, 0, 22))
    m.cube(hat3, (-3, 60, -5.5), (6, 3, 6), FROST_RED, [frost(0.06)])
    m.cube(hat3, (-2.5, 63, -5), (5, 5, 5), ICE_CORE, [crystal])

    arms = (("d", 1, (55, 0, 18), (70, 0, 0)), ("e", -1, (32, 0, -10), (55, 0, 0)))
    for side, sx, up, fore in arms:
        arm = m.bone("braco_" + side, "corpo", (16.5 * sx, 36, -0.5), up)
        xa = 12 if sx > 0 else -21
        m.cube(arm, (xa, 30, -7), (9, 10, 13), FROST_RED, [edge_dark(), frost(0.06)])
        m.cube(arm, (xa + 1, 21, -5), (7, 10, 9), FROST_RED, [edge_dark()])
        for cx, cz, h, w, rot in [(3, -3, 9, 3, (0, 0, -14)), (6, 1, 7, 2, (16, 0, -32)), (1, -6.5, 6, 2, (-24, 0, -8)),
                                  (5, -5, 5, 2, (-10, 20, -40))]:
            x = xa + cx if sx > 0 else xa + 9 - cx - w
            m.cube(arm, (x, 39, cz), (w, h, w), ICE, [crystal], rot=(rot[0], rot[1] * sx, rot[2] * sx),
                   origin=(x + w / 2, 39, cz + w / 2))
        fore_b = m.bone("antebraco_" + side, "braco_" + side, (16.5 * sx, 22, -0.5), fore)
        m.cube(fore_b, (xa + 0.5, 13, -5), (8, 9, 9), FROST_RED, [edge_dark(), frost(0.05)])
        m.cube(fore_b, (xa, 11, -5.5), (9, 3, 10), SNOW, [], inflate=0.3)
        hand = m.bone("mao_" + side, "antebraco_" + side, (16.5 * sx, 8, -0.5))
        m.cube(hand, (xa + 0.5, 3, -5), (8, 8, 9), GLOVE_B, [frost(0.06)])

    # the present hammer, resting on his right shoulder
    hm = m.bone("martelo", "mao_d", (16.5, 7, -0.5), (-60, 0, -10))
    m.cube(hm, (15.5, 0, -1.5), (2, 34, 2), WOOD, [handle_paint])
    m.cube(hm, (14.5, -2, -2.5), (4, 3, 4), RIBBON, [])
    head_b = m.bone("cabeca_martelo", "martelo", (16.5, 38, -0.5))
    m.cube(head_b, (9.5, 32, -7), (14, 12, 13), GIFT_BLUE, [gift_paint])
    m.cube(head_b, (13.5, 44, -2.5), (2, 3, 4), RIBBON, [], rot=(0, 0, 25), origin=(15.5, 44, -0.5))
    m.cube(head_b, (17.5, 44, -2.5), (2, 3, 4), RIBBON, [], rot=(0, 0, -25), origin=(17.5, 44, -0.5))
    for ex, ez, h in [(9, -7.5, 4), (22, -7.5, 5), (9, 5.5, 5), (22, 5.5, 3), (15, -7.8, 3)]:
        m.cube(head_b, (ex, 30 - h + 2, ez), (2, h, 2), ICE, [crystal])

    # an ice orb above the left palm
    orb = m.bone("orbe", "mao_e", (-16.5, 7, -0.5))
    m.cube(orb, (-20, 3.5, -18), (7, 7, 7), ICE_CORE, [orb_paint])
    m.cube(orb, (-21, 2.5, -19), (9, 9, 9), ICE, [frame_only], inflate=0.3)
    return m
