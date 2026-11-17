"""Builds the presentation sheets from the renders in ../previas."""
from PIL import Image, ImageDraw, ImageFont

P = "../previas/"
FONT = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
FONT_R = "/usr/share/fonts/opentype/inter/Inter-Regular.otf"
BOSSES = [
    ("noel_vtaper", "Noel V-Taper", "Alto, ombros duas vezes mais largos que a cintura, peito nu com cartucheiras, óculos escuros e uma metralhadora giratória em cada mão."),
    ("noel_brutamontes", "Noel Brutamontes", "O lutador: casaco aberto, socos-ingleses de ouro, saco de carvão nas costas e granada-presente no cinto."),
    ("noel_sombrio", "Noel Sombrio", "O corrompido: chifres furando o gorro, olhos que brilham, correntes com sinos, foice de bengala doce e saco em brasa."),
    ("noel_gelo_eterno", "Noel do Gelo Eterno", "O gigante congelado: coroa de gelo, barba de pingentes, cristais nos ombros, martelo-presente e orbe de gelo."),
]
VIEWS = ["frente34", "frente", "lado", "costas34", "escala"]
LABELS = ["3/4 frente", "frente", "lado", "3/4 costas", "tamanho (jogador em cinza)"]
BG = (246, 243, 238)
INK = (34, 30, 28)
MUTED = (110, 100, 94)
ACCENT = (178, 24, 34)


def font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def trimmed(path, scale):
    im = Image.open(path).convert("RGBA")
    im = im.crop(im.getbbox())
    return im.resize((int(im.width * scale), int(im.height * scale)), Image.LANCZOS)


import sys
WANT = sys.argv[1:]
for ident, title, desc in BOSSES:
    if WANT and ident not in WANT:
        continue
    cell_w, cell_h = 360, 470
    W, H = cell_w * 5 + 40, cell_h + 170
    s = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(s)
    d.rectangle((0, 0, W, 8), fill=ACCENT)
    d.text((28, 28), title, font=font(FONT, 44), fill=INK)
    d.text((30, 86), desc, font=font(FONT_R, 21), fill=MUTED)
    for k, v in enumerate(VIEWS):
        im = Image.open(P + f"{ident}_{v}.png").convert("RGBA")
        im = im.crop(im.getbbox())
        sc = min((cell_w - 30) / im.width, (cell_h - 50) / im.height)
        im = im.resize((int(im.width * sc), int(im.height * sc)), Image.LANCZOS)
        x0 = 20 + k * cell_w + (cell_w - im.width) // 2
        y0 = 140 + (cell_h - 50 - im.height)
        s.paste(im, (x0, y0), im)
        lab = LABELS[k]
        tw = d.textlength(lab, font=font(FONT_R, 18))
        d.text((20 + k * cell_w + (cell_w - tw) / 2, 140 + cell_h - 34), lab, font=font(FONT_R, 18), fill=MUTED)
    s.save(P + f"{ident}_folha.png", optimize=True)

if WANT:
    raise SystemExit
# overview: the three side by side
cells = []
for ident, title, _ in BOSSES[1:]:
    im = Image.open(P + f"{ident}_frente34.png").convert("RGBA")
    cells.append((im.crop(im.getbbox()), title))
h = 620
W = 40 + sum(int(im.width * h / im.height) + 60 for im, _ in cells)
s = Image.new("RGB", (W, h + 200), BG)
d = ImageDraw.Draw(s)
d.rectangle((0, 0, W, 8), fill=ACCENT)
d.text((30, 26), "Bosses de Natal: esboços", font=font(FONT, 46), fill=INK)
x = 40
for im, title in cells:
    w = int(im.width * h / im.height)
    im = im.resize((w, h), Image.LANCZOS)
    s.paste(im, (x, 110), im)
    tw = d.textlength(title, font=font(FONT, 26))
    d.text((x + (w - tw) / 2, 110 + h + 20), title, font=font(FONT, 26), fill=INK)
    x += w + 60
s.save(P + "natal_bosses.png", optimize=True)
print("ok")
