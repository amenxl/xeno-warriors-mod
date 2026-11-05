"""Stat Boost item textures + models.

Each boost item is two layers: layer0 = the tier's crystal (colour per tier: +1 green, +2 aqua, +5 purple, +10 gold,
+100 red with a rainbow rim), layer1 = a small icon for the stat in the bottom-right corner. Writes
  textures/item/stat_boost/tier_<n>.png, textures/item/stat_boost/icon_<stat>.png, models/item/stat_boost_<stat>_<n>.json
Run from the repo root: python3 tools/xw/statboost/make.py   (PREVIEW=<png> also saves a preview sheet)
"""
import json, os
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'src', 'main', 'resources', 'assets', 'xenowarriors')
TEX = os.path.join(ROOT, 'textures', 'item', 'stat_boost')
MOD = os.path.join(ROOT, 'models', 'item')
os.makedirs(TEX, exist_ok=True)

STATS = ['speed', 'attack', 'defense', 'mining', 'crafting', 'smelting', 'appetite', 'building', 'hekublade', 'wires', 'guns']
TIERS = [1, 2, 5, 10, 100]


def hexc(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


# tier crystals: (dark, mid, light, highlight)
TIER_COL = {
    1: (0x1E6B2A, 0x3FBF4F, 0x86F08A, 0xE6FFE6),
    2: (0x175E7A, 0x2FB3D9, 0x8BE6FF, 0xEFFFFF),
    5: (0x4E1E7A, 0x9B47D9, 0xD39BFF, 0xFAEEFF),
    10: (0x7A4A0A, 0xE0A020, 0xFFE070, 0xFFFFE0),
    100: (0x6E0A12, 0xE0283A, 0xFF8A70, 0xFFFFFF),
}

# a gem shape (16x16): y -> (x0, x1) filled span
GEM = {
    2: (6, 9), 3: (5, 10), 4: (4, 11), 5: (3, 12), 6: (2, 13), 7: (2, 13), 8: (2, 13),
    9: (3, 12), 10: (4, 11), 11: (5, 10), 12: (6, 9), 13: (7, 8),
}


def tier_png(n):
    dark, mid, light, hi = (hexc(c) for c in TIER_COL[n])
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, (x0, x1) in GEM.items():
        for x in range(x0, x1 + 1):
            above = GEM.get(y - 1)
            below = GEM.get(y + 1)
            edge = x in (x0, x1) or above is None or below is None or x < above[0] or x > above[1] or x < below[0] or x > below[1]
            if edge:
                c = dark
            elif x + y < 12:          # upper-left facet lit
                c = light
            elif x > 8 and y > 7:
                c = shade(mid, 0.78)
            else:
                c = mid
            px[x, y] = c
    for x in range(4, 12):            # the girdle line
        if px[x, 7][3] and px[x, 7] != dark:
            px[x, 7] = shade(px[x, 7], 1.15)
    px[5, 4] = hi; px[4, 5] = hi; px[5, 5] = hi
    if n == 100:                      # rainbow rim + star sparkles for the legendary one
        rainbow = [0xFF4040, 0xFF9A30, 0xFFE040, 0x50E060, 0x40B0FF, 0xA060FF]
        i = 0
        for y, (x0, x1) in GEM.items():
            for x in (x0, x1):
                px[x, y] = hexc(rainbow[i % len(rainbow)]); i += 1
        for (x, y) in ((1, 1), (14, 2), (1, 13)):
            px[x, y] = hexc(0xFFFFFF)
        for (x, y) in ((0, 1), (2, 1), (1, 0), (1, 2), (14, 1), (14, 3), (13, 2), (15, 2), (0, 13), (2, 13), (1, 12), (1, 14)):
            px[x, y] = hexc(0xFFE680, 200)
    elif n == 10:
        for (x, y) in ((1, 2), (14, 3), (1, 13)):
            px[x, y] = hexc(0xFFF4B0)
    elif n == 5:
        px[14, 3] = hexc(0xF0D8FF)
    return im


# stat icons: 6x6 pixel art in the bottom-right corner (x/y 10..15) on a dark plate
ICONS = {
    'speed':     ["#..#..", ".#..#.", "..#..#", "..#..#", ".#..#.", "#..#.."],
    'attack':    ["....##", "...###", "#.###.", ".###..", "###...", "##...."],
    'defense':   ["######", "##..##", "######", "######", ".####.", "..##.."],
    'mining':    [".####.", "#.##.#", "..##..", "..##..", "..##..", "..##.."],
    'crafting':  ["#####.", "#####.", "..#...", "..#...", "..#...", "..#..."],
    'smelting':  ["..#...", ".##.#.", ".####.", "######", "######", ".####."],
    'appetite':  ["..###.", ".#####", ".#####", "..###.", ".#....", "#....."],
    'building':  ["######", "#..#..", "######", "..#..#", "######", "#..#.."],
    'hekublade': [".....#", "....#.", "...#..", "..#...", "##....", "#....."],
    'wires':     ["#.....", ".#...#", "..#.#.", "...#..", "..#.#.", ".#...#"],
    'guns':      ["######", "######", "##.#..", "##....", "##....", "......"],
}
ICON_COL = {'speed': 0x55DDFF, 'attack': 0xFF7744, 'defense': 0x88DD66, 'mining': 0xE0C070, 'crafting': 0xC08A50,
            'smelting': 0xFF8A3A, 'appetite': 0xE8D020, 'building': 0x7FD46A, 'hekublade': 0xFF7A1A,
            'wires': 0xB05CFF, 'guns': 0xB0B8C8}


def icon_png(stat):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(9, 16):
        for x in range(9, 16):
            px[x, y] = (20, 16, 28, 235)
    col = hexc(ICON_COL[stat])
    for j, row in enumerate(ICONS[stat]):
        for i, ch in enumerate(row):
            if ch == '#':
                px[10 + i, 10 + j] = shade(col, 1.15) if j == 0 else col
    return im


def main():
    for n in TIERS:
        tier_png(n).save(os.path.join(TEX, f'tier_{n}.png'))
    for s in STATS:
        icon_png(s).save(os.path.join(TEX, f'icon_{s}.png'))
        for n in TIERS:
            with open(os.path.join(MOD, f'stat_boost_{s}_{n}.json'), 'w') as f:
                json.dump({"parent": "minecraft:item/generated", "textures": {
                    "layer0": f"xenowarriors:item/stat_boost/tier_{n}",
                    "layer1": f"xenowarriors:item/stat_boost/icon_{s}"}}, f)
    out = os.environ.get('PREVIEW')
    if out:
        sheet = Image.new('RGBA', (64 * len(TIERS), 64 * len(STATS)), (60, 60, 70, 255))
        for r, s in enumerate(STATS):
            for c, n in enumerate(TIERS):
                im = Image.alpha_composite(tier_png(n), icon_png(s)).resize((64, 64), Image.NEAREST)
                sheet.alpha_composite(im, (c * 64, r * 64))
        sheet.save(out)
    print('wrote', len(TIERS), 'tiers,', len(STATS), 'icons,', len(TIERS) * len(STATS), 'models')


if __name__ == '__main__':
    main()
