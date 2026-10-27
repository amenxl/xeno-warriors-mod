"""The girls' skins go in the slim (3px arm) layout, to match the slim player model the mod gives them (see
client/FemaleBody.java). Converts a wide-arm skin by dropping one middle column from each arm's front, back, top and
bottom. Safe to run again: skins already in the slim layout are left alone. Run convert_all() after any script that
writes a girl's skin (sp_assets, sr_named, npcs, kaeru)."""
import os, glob, sys
from PIL import Image

RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/'
GIRLS = ['kaeru', 'kyrie', 'lexy', 'technica', 'aioni', 'yuri']
GIRL_OUTFITS = {'max': ['royal'], 'sam': ['royal'], 'yuri': ['c1pjs']}
GIRL_NPCS = ['woman', 'girl', 'sky_lexy', 'sr_ava', 'sr_ava_demon', 'sr_persephone', 'sr_persephone_demon', 'sr_maddie']
SKIN_SUFFIXES = ['', '_damage1', '_damage2', '_torn1', '_torn2', '_torn3']
ARMS = [(40, 16), (32, 48), (40, 32), (48, 48)]      # right arm, left arm, right sleeve, left sleeve
KEEP = (0, 1, 3)                                     # the columns kept from each 4-wide face (both edges stay)

def is_slim(im):
    px = im.load()
    return px[40 + 15, 16 + 8][3] == 0 and px[40 + 14, 16 + 8][3] == 0 and px[40 + 12, 16 + 8][3] != 0

def to_slim(im):
    im = im.convert('RGBA'); src = im.copy(); s = src.load(); d = im.load()
    for u, v in ARMS:
        for y in range(v, v + 16):
            for x in range(u, u + 16): d[x, y] = (0, 0, 0, 0)
        def face(sx, sy, w, h, dx, dy, keep):
            for yy in range(h):
                for i, c in enumerate(keep): d[u + dx + i, v + dy + yy] = s[u + sx + c, v + sy + yy]
        face(4, 0, 4, 4, 4, 0, KEEP)                  # top
        face(8, 0, 4, 4, 7, 0, KEEP)                  # bottom
        face(0, 4, 4, 12, 0, 4, (0, 1, 2, 3))         # right side (4 deep, as is)
        face(4, 4, 4, 12, 4, 4, KEEP)                 # front
        face(8, 4, 4, 12, 7, 4, (0, 1, 2, 3))         # left side
        face(12, 4, 4, 12, 11, 4, KEEP)               # back
    return im

def convert(path):
    if not os.path.exists(path): return False
    im = Image.open(path).convert('RGBA')
    if im.size != (64, 64) or is_slim(im): return False
    to_slim(im).save(path); return True

def files():
    out = []
    ch = RES + 'character/'
    for g in GIRLS:
        keys = [''] + ['_' + k for k in GIRL_OUTFITS.get(g, [])]
        for k in keys:
            for s in SKIN_SUFFIXES: out += [ch + g + k + s + '.png', ch + g + k + s + '_cel.png']
    for g, ks in GIRL_OUTFITS.items():
        if g in GIRLS: continue
        for k in ks: out += [ch + f'{g}_{k}.png', ch + f'{g}_{k}_cel.png']
    for n in GIRL_NPCS:
        for s in ('', '_dark'): out += [RES + f'npc/{n}{s}.png', RES + f'npc/{n}{s}_cel.png']
    return out

def convert_all(verbose=True):
    n = 0
    for f in files():
        if convert(f):
            n += 1
            if verbose: print('slim:', os.path.basename(f))
    return n

if __name__ == '__main__':
    print(convert_all(), 'converted')
