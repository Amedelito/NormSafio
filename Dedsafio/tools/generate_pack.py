#!/usr/bin/env python3
"""Genera el resource pack de Dedsafío: texturas 16x16, modelos y overrides.

Uso:  python3 tools/generate_pack.py        (requiere Pillow)
Salida: resourcepack/  (listo para zippear)
Cada objeto se liga a su CustomModelData del plugin (ItemRegistry).
"""
import json
import os
import random
import shutil
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "resourcepack")
TEX = os.path.join(ROOT, "assets", "dedsafio", "textures", "item")
MOD = os.path.join(ROOT, "assets", "dedsafio", "models", "item")
VAN = os.path.join(ROOT, "assets", "minecraft", "models", "item")
S = 16


def hx(c):
    c = c.lstrip("#")
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), 255)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def lighten(c, t=0.3):
    return mix(c, (255, 255, 255, 255), t)


def darken(c, t=0.3):
    return mix(c, (0, 0, 0, 255), t)


class Cv:
    """Lienzo 16x16 con relleno, líneas, sombreado automático y contorno."""

    def __init__(self):
        self.px = {}

    def set(self, x, y, c):
        if 0 <= x < S and 0 <= y < S:
            self.px[(x, y)] = c

    def get(self, x, y):
        return self.px.get((x, y))

    def line(self, x0, y0, x1, y1, c):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.set(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def rect(self, x, y, w, h, c):
        for j in range(h):
            for i in range(w):
                self.set(x + i, y + j, c)

    def sprite(self, rows, pal):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in pal:
                    self.set(x, y, pal[ch])

    def shade(self, base, hi=0.35, lo=0.35, only=None):
        """Luz arriba-izquierda, sombra abajo-derecha sobre los píxeles de color base."""
        out = {}
        for (x, y), c in self.px.items():
            if c != base and (only is None or c not in only):
                continue
            empty = lambda a, b: (a, b) not in self.px
            if empty(x - 1, y) or empty(x, y - 1):
                out[(x, y)] = lighten(base, hi)
            elif empty(x + 1, y) or empty(x, y + 1):
                out[(x, y)] = darken(base, lo)
        self.px.update(out)

    def outline(self, color=None, diag=False):
        add = {}
        nb = [(1, 0), (-1, 0), (0, 1), (0, -1)]
        if diag:
            nb += [(1, 1), (-1, -1), (1, -1), (-1, 1)]
        for (x, y), c in self.px.items():
            for dx, dy in nb:
                p = (x + dx, y + dy)
                if p not in self.px and 0 <= p[0] < S and 0 <= p[1] < S:
                    add[p] = color or darken(c, 0.72)
        self.px.update(add)

    def save(self, name):
        im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        for (x, y), c in self.px.items():
            im.putpixel((x, y), c)
        os.makedirs(TEX, exist_ok=True)
        im.save(os.path.join(TEX, name + ".png"))
        return im


# ───────────────────────── Espadas ─────────────────────────

def sword(blade, guard, grip, wide=False, gem=None, glow=None, jag=False, curved=False):
    c = Cv()
    light, mid, dark = lighten(blade, .45), blade, darken(blade, .3)
    # eje x+y = 15; hoja desde (6,9) a (13,2), punta en (14,1)
    for i in range(8):
        x, y = 6 + i, 9 - i
        c.set(x, y, mid)
        c.set(x, y - 1, light)
        if wide:
            c.set(x + 1, y, dark)
    c.set(14, 1, light)
    if jag:  # filo dentado
        for x, y in [(8, 8), (10, 6), (12, 4)]:
            c.set(x + 1, y + 1, dark)
    if curved:
        c.set(13, 1, light)
        c.set(14, 2, mid)
    # guardia perpendicular (dirección 1,1) centrada en (5,10)
    for k in (-2, -1, 0, 1, 2):
        c.set(5 + k, 10 + k, guard if k else lighten(guard, .3))
    c.set(2, 7, guard) if wide else None
    c.set(8, 13, guard) if wide else None
    # empuñadura y pomo
    for x, y in [(4, 11), (3, 12), (2, 13)]:
        c.set(x, y, grip)
    c.set(1, 14, lighten(guard, .2))
    if gem:
        c.set(5, 10, gem)
    c.outline(diag=False)
    if glow:
        for (x, y) in [(11, 1), (15, 3), (9, 4), (14, 6), (12, 8)]:
            if (x, y) not in c.px:
                c.set(x, y, glow)
    return c


# ───────────────────────── Armaduras ─────────────────────────

MASKS = {
    "helmet": [
        "................", "................", "................",
        "....########....", "...##########...", "..############..",
        "..############..", "..############..", "..####....####..",
        "..####....####..", "..###......###..", "..###......###..",
        "................", "................", "................", "................"],
    "chestplate": [
        "................", "..####....####..", ".#####....#####.",
        ".##############.", ".##############.", ".##############.",
        ".###.######.###.", ".###.######.###.", ".###.######.###.",
        "..##.######.##..", "....########....", "....########....",
        "....########....", "................", "................", "................"],
    "leggings": [
        "................", "................", "...##########...",
        "...##########...", "...##########...", "...####..####...",
        "...####..####...", "...####..####...", "...####..####...",
        "...####..####...", "...####..####...", "...####..####...",
        "...####..####...", "................", "................", "................"],
    "boots": [
        "................", "................", "................",
        "................", "................", "................",
        "................", "..####....####..", "..####....####..",
        "..####....####..", "..####....####..", ".#####....#####.",
        "######....######", "######....######", "................", "................"],
}

SETS = {
    # nombre: (base, acento, estilo)
    "dungeon": ("#8d99ae", "#4cc9f0", "chain"),
    "earth": ("#6a994e", "#a98467", "moss"),
    "dimensional": ("#7b2cbf", "#7df9ff", "crystal"),
    "boxing_red": ("#d62839", "#ffffff", "stripe"),
    "boxing_blue": ("#2a6f97", "#ffffff", "stripe"),
}
SET_INDEX = ["dungeon", "earth", "dimensional", "boxing_red", "boxing_blue"]
PIECES = ["helmet", "chestplate", "leggings", "boots"]


def armor(set_name, piece):
    base, accent, style = SETS[set_name]
    base, accent = hx(base), hx(accent)
    rnd = random.Random(f"{set_name}{piece}")
    c = Cv()
    for y, row in enumerate(MASKS[piece]):
        for x, ch in enumerate(row):
            if ch == "#":
                c.set(x, y, base)
    # patrón por estilo
    for (x, y) in list(c.px):
        if style == "chain" and (x + y) % 2 == 0:
            c.px[(x, y)] = darken(base, .18)
        elif style == "moss" and rnd.random() < .22:
            c.px[(x, y)] = hx("#a98467") if rnd.random() < .5 else lighten(base, .25)
        elif style == "crystal" and rnd.random() < .12:
            c.px[(x, y)] = accent
        elif style == "stripe" and y % 6 == 4:
            c.px[(x, y)] = accent
    shade_base = None
    # sombreado de bordes por vecino vacío
    for (x, y), col in list(c.px.items()):
        top_left = (x - 1, y) not in c.px or (x, y - 1) not in c.px
        bot_right = (x + 1, y) not in c.px or (x, y + 1) not in c.px
        if top_left:
            c.px[(x, y)] = accent if style == "crystal" else lighten(col, .35)
        elif bot_right:
            c.px[(x, y)] = darken(col, .35)
    # detalle central del set
    if piece == "chestplate":
        gem = {"dungeon": accent, "earth": hx("#ffd166"), "dimensional": hx("#ffffff"),
               "boxing_red": hx("#ffffff"), "boxing_blue": hx("#ffffff")}[set_name]
        c.set(7, 5, gem); c.set(8, 5, gem); c.set(7, 6, lighten(gem, .3))
    if piece == "helmet" and style == "dimensional":
        c.set(7, 3, accent); c.set(8, 3, accent)
    if piece == "helmet" and style == "crystal":
        c.set(7, 2, accent); c.set(8, 1, accent)
    c.outline()
    return c


# ───────────────────────── Otros objetos ─────────────────────────

def glove():
    c = Cv()
    red = hx("#e63946")
    rows = [
        "................", ".....######.....", "....########....",
        "...##########...", "..###########...", "..###########...",
        "...##########...", "...##########...", "....########....",
        ".....######.....", ".....wwwwww.....", ".....wwwwww.....",
        ".....wwwwww.....", "................", "................", "................"]
    c.sprite(rows, {"#": red, "w": hx("#f1faee")})
    c.shade(red, .4, .35)
    c.set(8, 11, hx("#a8dadc")); c.set(9, 11, hx("#a8dadc"))
    c.set(5, 5, darken(red, .35)); c.set(5, 6, darken(red, .35))  # pulgar
    c.outline()
    return c


def flask(kind, liquid, symbol_col):
    L = hx(liquid)
    glass = hx("#cfe8f0")
    c = Cv()
    shapes = {
        "round": [
            "................", "......mmmm......", "......gggg......", ".......gg.......",
            "......gLLg......", ".....gLLLLg.....", "....gLLLLLLg....", "....gLLLLLLg....",
            "....gLLLLLLg....", "....gLLLLLLg....", ".....gLLLLg.....", "......gggg......",
            "................", "................", "................", "................"],
        "wide": [
            "................", "......mmmm......", ".......gg.......", ".......gg.......",
            "......gLLg......", "....ggLLLLgg....", "...gLLLLLLLLg...", "...gLLLLLLLLg...",
            "...gLLLLLLLLg...", "...gLLLLLLLLg...", "....gLLLLLLg....", ".....gggggg.....",
            "................", "................", "................", "................"],
        "tall": [
            "................", ".......mm.......", ".......gg.......", "......gggg......",
            "......gLLg......", "......gLLg......", ".....gLLLLg.....", ".....gLLLLg.....",
            ".....gLLLLg.....", ".....gLLLLg.....", ".....gLLLLg.....", ".....gLLLLg.....",
            "......gggg......", "................", "................", "................"],
        "jug": [
            "................", ".....mmmmmm.....", ".....GGGGGG.....", "......gLLg......",
            "....ggLLLLgg....", "...gLLLLLLLLg...", "...gLLLLLLLLg...", "...gLLLLLLLLg...",
            "...gLLLLLLLLg...", "...gLLLLLLLLg...", "....gLLLLLLg....", ".....gggggg.....",
            "................", "................", "................", "................"],
    }
    c.sprite(shapes[kind], {"g": glass, "L": L, "m": hx("#a98467"), "G": hx("#ffd166")})
    c.shade(L, .35, .3)
    # brillo del cristal
    for (x, y), col in list(c.px.items()):
        if col == glass and (x - 1, y) not in c.px:
            c.px[(x, y)] = hx("#ffffff")
    # símbolo en el líquido
    sc = hx(symbol_col)
    sym = {
        "round": [(7, 8), (8, 8), (6, 9), (9, 9), (7, 10), (8, 10)],           # corazón
        "wide": [(8, 6), (7, 7), (8, 7), (8, 8), (9, 8), (8, 9)],               # llama
        "tall": [(7, 7), (8, 8), (7, 9), (8, 10)],                              # espiral
        "jug": [(8, 6), (7, 7), (8, 7), (9, 7), (8, 8)],                        # flecha/estrella
    }[kind]
    for p in sym:
        if p in c.px:
            c.px[p] = sc
    c.outline()
    return c


def soul_flower():
    c = Cv()
    pet, core = hx("#9d4edd"), hx("#e0aaff")
    rows = [
        "................", ".....pp..pp.....", "....pPPppPPp....", "....pPccccPp....",
        ".....pcCCcp.....", "....pPccccPp....", "....pPPppPPp....", ".....pp..pp.....",
        "........s.......", "........s.......", ".......ss.......", ".......s........",
        ".......s.ll.....", "......sslll.....", "................", "................"]
    c.sprite(rows, {"p": pet, "P": darken(pet, .25), "c": core, "C": hx("#ffffff"),
                    "s": hx("#2d6a4f"), "l": hx("#40916c")})
    c.outline()
    for p in [(2, 3), (13, 2), (14, 6), (1, 8), (12, 10)]:  # almas flotando
        c.set(*p, hx("#b8f2ff"))
    return c


def spoon():
    c = Cv()
    gold = hx("#ffd166")
    # mango diagonal (x+y=15) y cuenco elíptico arriba a la derecha
    for x, y in [(2, 13), (3, 12), (4, 11), (5, 10), (6, 9), (7, 8)]:
        c.set(x, y, gold)
        c.set(x, y - 1, lighten(gold, .45))
    bowl = ["..aaa.", ".aAAAa", "aAAAAa", "aAAAAa", ".aAAa.", "..aa.."]
    for j, row in enumerate(bowl):
        for i, ch in enumerate(row):
            if ch == "a":
                c.set(8 + i, 0 + j + 1, lighten(gold, .2))
            elif ch == "A":
                c.set(8 + i, 0 + j + 1, mix(gold, hx("#fff3b0"), .6))
    c.set(1, 14, darken(gold, .2))
    c.outline()
    for p in [(13, 7), (6, 3), (3, 6), (14, 10)]:
        c.set(*p, hx("#fff3b0"))
    return c


def dedita(body, rim, shine):
    c = Cv()
    B, R = hx(body), hx(rim)
    for y in range(S):
        for x in range(S):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 6.4 ** 2:
                c.set(x, y, R)
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 5.0 ** 2:
                c.set(x, y, B)
    c.shade(B, .3, .3)
    # dedito grabado
    f = lighten(B, .5)
    for p in [(7, 4), (8, 4), (7, 5), (8, 5), (7, 6), (8, 6), (7, 7), (8, 7), (6, 8), (7, 8), (8, 8), (9, 8),
              (6, 9), (7, 9), (8, 9), (9, 9), (7, 10), (8, 10)]:
        c.set(*p, f)
    c.set(7, 4, hx(shine)); c.set(8, 4, darken(f, .2))
    c.outline()
    return c


def totem(body, accent, kind):
    c = Cv()
    B, A = hx(body), hx(accent)
    rows = [
        "................", ".....######.....", "....########....", "....########....",
        "....########....", ".....######.....", "..############..", ".##############.",
        ".##############.", "..############..", "....########....", "....########....",
        ".....######.....", ".....######.....", "....########....", "................"]
    c.sprite(rows, {"#": B})
    c.shade(B, .35, .35)
    eye = hx("#14213d")
    c.set(6, 3, eye); c.set(9, 3, eye)
    if kind == "eonic":      # aro del tiempo
        for p in [(7, 8), (8, 8), (6, 9), (9, 9), (7, 10), (8, 10)]:
            c.set(*p, A)
        c.set(7, 9, hx("#ffffff")); c.set(8, 9, hx("#ffffff"))
    elif kind == "iron":     # remaches y visera
        for x in range(5, 11):
            c.set(x, 3, darken(B, .45))
        for p in [(3, 7), (12, 7), (5, 11), (10, 11)]:
            c.set(*p, A)
    elif kind == "otter":    # cara de nutria
        c.set(7, 4, hx("#3d2b1f")); c.set(8, 4, hx("#3d2b1f"))
        for p in [(5, 4), (10, 4)]:
            c.set(*p, A)
        c.set(6, 2, A); c.set(9, 2, A)
        for p in [(7, 8), (8, 8), (7, 9), (8, 9), (6, 9), (9, 9)]:
            c.set(*p, A)
    elif kind == "flash":    # estallido
        for p in [(7, 8), (8, 8), (7, 9), (8, 9)]:
            c.set(*p, hx("#ffffff"))
        for p in [(7, 7), (8, 7), (6, 8), (9, 8), (6, 9), (9, 9), (7, 10), (8, 10)]:
            c.set(*p, A)
    c.outline()
    if kind == "flash":
        for p in [(1, 1), (14, 1), (0, 8), (15, 8), (2, 14), (13, 14)]:
            c.set(*p, A)
    return c


def rune_table():
    c = Cv()
    top, wood = hx("#5a189a"), hx("#3c096c")
    rows = [
        "................", "................", "...tttttttttt...", "..tTTTTTTTTTTt..",
        ".tTTTTTTTTTTTTt.", ".tTTTTTTTTTTTTt.", ".tTTTTTTTTTTTTt.", "..tTTTTTTTTTTt..",
        "...wwwwwwwwww...", ".....wwwwww.....", ".....wwwwww.....", ".....wwwwww.....",
        "....wwwwwwww....", "...wwwwwwwwww...", "................", "................"]
    c.sprite(rows, {"t": hx("#9d4edd"), "T": top, "w": wood})
    glyph = hx("#e0aaff")
    for p in [(8, 4), (7, 5), (8, 5), (9, 5), (8, 6), (5, 5), (11, 5), (6, 6), (10, 6)]:
        c.set(*p, glyph)
    c.set(8, 5, hx("#ffffff"))
    c.outline()
    return c


def lighthouse():
    c = Cv()
    rows = [
        "......hhhh......", ".....hLLLLh.....", ".....hLLLLh.....", "......rrrr......",
        ".....wwwwww.....", ".....wRRRRw.....", "....wwwwwwww....", "....wRRRRRRw....",
        "....wwwwwwww....", "...wwwwwwwwww...", "...wRRRRRRRRw...", "...wwwwwwwwww...",
        "..wwwwwwwwwwww..", "..bbbbbbbbbbbb..", "................", "................"]
    c.sprite(rows, {"h": hx("#8d99ae"), "L": hx("#fff3b0"), "r": hx("#e63946"),
                    "w": hx("#f1faee"), "R": hx("#e63946"), "b": hx("#6c757d")})
    c.shade(hx("#f1faee"), .0, .22)
    c.outline()
    for p in [(0, 1), (1, 2), (14, 2), (15, 1), (0, 3), (15, 3)]:  # haz de luz
        c.set(*p, hx("#fff3b0"))
    return c


def ancestral_block():
    c = Cv()
    base = hx("#2b1b3d")
    rnd = random.Random("ancestral")
    for y in range(1, 15):
        for x in range(1, 15):
            col = base
            r = rnd.random()
            if r < .18:
                col = lighten(base, .15)
            elif r < .32:
                col = darken(base, .25)
            c.set(x, y, col)
    gold = hx("#d4a373")
    for p in [(8, 4), (7, 5), (8, 5), (9, 5), (8, 6), (8, 7), (6, 8), (7, 8), (9, 8), (10, 8),
              (7, 9), (9, 9), (7, 10), (9, 10), (6, 11), (10, 11)]:
        c.set(*p, gold)
    c.set(8, 5, lighten(gold, .5))
    for p in [(3, 3), (12, 3), (3, 12), (12, 12)]:
        c.set(*p, hx("#ffd166"))
    # marco
    for i in range(1, 15):
        for p in [(i, 1), (1, i)]:
            c.set(*p, lighten(base, .35))
        for p in [(i, 14), (14, i)]:
            c.set(*p, darken(base, .5))
    c.outline(darken(base, .8))
    return c


def statue():
    c = Cv()
    stone = hx("#adb5bd")
    rows = [
        "................", ".....SSSS.......", "....SSSSSS......", "....SSSSSS......",
        ".....SSSS.......", "...SSSSSSSS.....", "..SSSSSSSSSS....", "..SS.SSSS.SS....",
        "..SS.SSSS.SS....", ".....SSSS.......", ".....S..S.......", ".....S..S.......",
        "..PPPPPPPPPP....", ".PPPPPPPPPPPP...", ".PPPPPPPPPPPP...", "................"]
    c.sprite(rows, {"S": stone, "P": hx("#6c757d")})
    c.shade(stone, .35, .3)
    c.shade(hx("#6c757d"), .3, .3)
    c.set(5, 2, hx("#ffd166")); c.set(6, 1, hx("#ffd166"))  # corona
    c.outline()
    return c


def kart():
    c = Cv()
    red = hx("#e63946")
    rows = [
        "................", "................", "................", "......ssss......",
        "....rrrrrrrr....", "..rrrrrrrrrrrr..", ".rrrrRRRRRRrrrr.", ".rrrrrrrrrrrrrr.",
        "wwrrrrrrrrrrrrww", "wwwwrrrrrrrrwwww", "wwwwwwwwwwwwwwww", ".wwww......wwww.",
        "................", "................", "................", "................"]
    c.sprite(rows, {"r": red, "R": hx("#f1faee"), "s": hx("#14213d"), "w": hx("#343a40")})
    c.shade(red, .3, .3)
    c.set(2, 10, hx("#adb5bd")); c.set(13, 10, hx("#adb5bd"))
    c.set(7, 6, hx("#ffd166")); c.set(8, 6, hx("#ffd166"))
    c.outline()
    return c


# ───────────────────────── Registro de objetos ─────────────────────────
# id -> (CustomModelData, material, tipo de modelo)
ITEMS = {}


def add(item_id, cmd, material, factory):
    ITEMS[item_id] = (cmd, material, factory)


add("ancestral_sword", 2001, "netherite_sword",
    lambda: sword(hx("#d4a373"), hx("#7f5539"), hx("#3d2b1f"), wide=True, gem=hx("#e63946"), glow=hx("#ffd166")))
add("belic_sword", 2002, "diamond_sword",
    lambda: sword(hx("#e63946"), hx("#4a1c1c"), hx("#2b2d42"), wide=True, jag=True, glow=hx("#ff9a3c")))
add("celestial_sword", 2003, "golden_sword",
    lambda: sword(hx("#ffd166"), hx("#fff3b0"), hx("#9d4edd"), gem=hx("#4cc9f0"), curved=True, glow=hx("#ffffff")))
add("space_stellar_sword", 2004, "iron_sword",
    lambda: sword(hx("#90e0ef"), hx("#4361ee"), hx("#14213d"), gem=hx("#ffffff"), glow=hx("#caf0f8")))
add("space_void_sword", 2005, "netherite_sword",
    lambda: sword(hx("#5a189a"), hx("#240046"), hx("#10002b"), wide=True, gem=hx("#e0aaff"), glow=hx("#9d4edd")))
add("boxing_gloves", 2101, "red_dye", glove)
add("potion_vitality", 2201, "potion", lambda: flask("round", "#ff6b6b", "#ffe5ec"))
add("potion_fury", 2202, "potion", lambda: flask("wide", "#c1121f", "#ffb703"))
add("potion_dimensional", 2203, "potion", lambda: flask("tall", "#b388ff", "#e0fbfc"))
add("potion_ancestral", 2204, "potion", lambda: flask("jug", "#d4a373", "#fff3b0"))
add("soul_flower", 2301, "wither_rose", soul_flower)
add("resurrection_spoon", 2401, "stick", spoon)
add("dedita_common", 2501, "copper_ingot", lambda: dedita("#b87333", "#7f4f24", "#ffd8a8"))
add("dedita_rare", 2502, "amethyst_shard", lambda: dedita("#9d4edd", "#5a189a", "#e0aaff"))
add("dedita_legendary", 2503, "echo_shard", lambda: dedita("#ffd60a", "#b8860b", "#ffffff"))
add("totem_eonic", 2601, "totem_of_undying", lambda: totem("#2ec4b6", "#7df9ff", "eonic"))
add("totem_iron", 2602, "totem_of_undying", lambda: totem("#8d99ae", "#ffd166", "iron"))
add("totem_otter", 2603, "totem_of_undying", lambda: totem("#8d6e63", "#d7b99a", "otter"))
add("totem_flashbang", 2604, "totem_of_undying", lambda: totem("#f1faee", "#ffe66d", "flash"))
add("rune_table", 2701, "enchanting_table", rune_table)
add("lighthouse", 2702, "sea_lantern", lighthouse)
add("ancestral_block", 2703, "crying_obsidian", ancestral_block)
add("statue", 2704, "armor_stand", statue)
add("kart", 2705, "saddle", kart)

ARMOR_MAT = {
    "dungeon": "chainmail", "earth": "leather", "dimensional": "diamond",
    "boxing_red": "leather", "boxing_blue": "leather",
}
for si, s in enumerate(SET_INDEX):
    for pi, p in enumerate(PIECES):
        add(f"{s}_{p}", 3000 + si * 10 + pi, f"{ARMOR_MAT[s]}_{p}",
            (lambda s=s, p=p: armor(s, p)))

# Modelos base de vanilla (para no perder la apariencia normal del objeto)
HANDHELD = {"netherite_sword", "diamond_sword", "golden_sword", "iron_sword", "stick"}
BLOCKS = {"enchanting_table", "sea_lantern", "crying_obsidian"}


def base_model(material):
    if material in BLOCKS:
        return {"parent": f"minecraft:block/{material}"}
    if material == "potion":
        return {"parent": "minecraft:item/generated",
                "textures": {"layer0": "minecraft:item/potion_overlay", "layer1": "minecraft:item/potion"}}
    if material.startswith("leather_"):
        return {"parent": "minecraft:item/generated",
                "textures": {"layer0": f"minecraft:item/{material}", "layer1": f"minecraft:item/{material}_overlay"}}
    if material == "wither_rose":
        return {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:block/wither_rose"}}
    parent = "minecraft:item/handheld" if material in HANDHELD else "minecraft:item/generated"
    return {"parent": parent, "textures": {"layer0": f"minecraft:item/{material}"}}


def main():
    shutil.rmtree(ROOT, ignore_errors=True)
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MOD, exist_ok=True)
    os.makedirs(VAN, exist_ok=True)

    overrides = {}
    sheet = []
    for item_id, (cmd, material, factory) in ITEMS.items():
        sheet.append(factory().save(item_id))
        parent = "minecraft:item/handheld" if material in HANDHELD else "minecraft:item/generated"
        with open(os.path.join(MOD, item_id + ".json"), "w", encoding="utf-8") as f:
            json.dump({"parent": parent, "textures": {"layer0": f"dedsafio:item/{item_id}"}}, f, indent=2)
        overrides.setdefault(material, []).append(
            {"predicate": {"custom_model_data": cmd}, "model": f"dedsafio:item/{item_id}"})

    for material, ov in overrides.items():
        model = base_model(material)
        model["overrides"] = sorted(ov, key=lambda o: o["predicate"]["custom_model_data"])
        with open(os.path.join(VAN, material + ".json"), "w", encoding="utf-8") as f:
            json.dump(model, f, indent=2)

    with open(os.path.join(ROOT, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"pack_format": 34,
                            "description": "§6Dedsafío §7· texturas del plugin"}}, f, indent=2, ensure_ascii=False)

    # icono del pack: espada celestial ampliada
    sheet_icon = ITEMS["celestial_sword"][2]().save("_tmp")
    os.remove(os.path.join(TEX, "_tmp.png"))
    sheet_icon.resize((128, 128), Image.NEAREST).save(os.path.join(ROOT, "pack.png"))

    # hoja de contacto para revisar (no va al pack)
    cols = 9
    rows = (len(sheet) + cols - 1) // cols
    cell = 80
    contact = Image.new("RGBA", (cols * cell, rows * cell), (45, 45, 55, 255))
    for i, im in enumerate(sheet):
        big = im.resize((64, 64), Image.NEAREST)
        contact.alpha_composite(big, ((i % cols) * cell + 8, (i // cols) * cell + 8))
    contact.save(os.path.join(os.path.dirname(ROOT), "tools", "contact_sheet.png"))
    print(f"{len(ITEMS)} texturas generadas en {os.path.normpath(ROOT)}")


if __name__ == "__main__":
    main()
