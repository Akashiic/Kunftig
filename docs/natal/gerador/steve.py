"""A plain grey player (32 px tall), only to show the bosses' size in the previews."""
from bbtool import Mat, Model, edge_dark

GREY = Mat("#9aa3ad", "#8f98a2", "#a5aeb8", grad=0.1)
GREY_D = Mat("#6f7883", "#68717b", grad=0.1)


def build():
    m = Model("jogador", seed=3, tex_w=128)
    m.bone("raiz")
    m.cube("raiz", (-4, 24, -4), (8, 8, 8), GREY, [edge_dark()])
    m.cube("raiz", (-4, 12, -2), (8, 12, 4), GREY_D, [edge_dark()])
    m.cube("raiz", (4, 12, -2), (4, 12, 4), GREY, [edge_dark()])
    m.cube("raiz", (-8, 12, -2), (4, 12, 4), GREY, [edge_dark()])
    m.cube("raiz", (0, 0, -2), (4, 12, 4), GREY_D, [edge_dark()])
    m.cube("raiz", (-4, 0, -2), (4, 12, 4), GREY_D, [edge_dark()])
    return m
