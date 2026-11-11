#!/usr/bin/env python3
"""
Karma Scanner visors (4 tiers): one geometry table -> everything that has to agree with it.

  python3 tools/xw/scanner/scanners.py

Writes
  - textures/models/armor/<id>_visor.png          worn model, tinted pass (lens glass + LEDs, grey so the dye colours it)
  - textures/models/armor/<id>_visor_overlay.png  worn model, untinted pass (the tier's frame)
  - client/render/ScannerVisorGeo.java            the same cubes for the worn ModelPart model (texOffs + boxes)
  - models/item/3d/<id>.json + models/item/<id>.json   held / ground / item-frame 3D model (inventory stays flat)
  - textures/item/<id>.png (frame) + <id>_lens.png (tinted layer0)  the flat inventory icon
  - textures/item/scanner3d/*.png                 the 3D item model's textures

Head space: the head box is x -4..4, y -8..0 (y grows DOWN), z -4..4, the face looks to -z. Wearer's right = -x.
"""
import json
import os
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
RES = os.path.join(ROOT, 'src/main/resources/assets/xenowarriors')
JAVA = os.path.join(ROOT, 'src/main/java/com/xenowarriors/client/render/ScannerVisorGeo.java')

# frame colours per tier: main, dark (edges), trim (rims)
TIERS = {
    'karma_scanner':          dict(main=(84, 92, 104), dark=(48, 52, 60), trim=(150, 160, 172)),
    'advanced_karma_scanner': dict(main=(205, 212, 224), dark=(104, 116, 136), trim=(70, 84, 110)),
    'elite_karma_scanner':    dict(main=(44, 44, 52), dark=(22, 22, 26), trim=(205, 40, 44)),
    'omni_karma_scanner':     dict(main=(226, 182, 64), dark=(140, 100, 28), trim=(255, 244, 200)),
}

# (name, x, y, z, w, h, d, kind)   kind: frame | trim | lens | earR | earL | ant
COMMON = [
    ('strapTop', -5, -9, -1, 10, 1, 2, 'frame'),
    ('strapR', -6, -9, -1, 1, 2, 2, 'frame'),
    ('earR', -6.5, -7, -2.5, 1, 4, 4, 'earR'),
    ('armR', -6, -5, -6.5, 1, 1, 5, 'frame'),
]
ONE_EYE = [
    ('strapL', 5, -9, -1, 1, 5, 2, 'frame'),
    ('padL', 5, -5, -1.5, 1, 2, 3, 'frame'),
    ('lens', -5, -5, -6.5, 5, 3, 1, 'lens'),
    ('rim', -5, -6, -6.5, 5, 1, 1, 'trim'),
]
TWO_EYE = [
    ('strapL', 5, -9, -1, 1, 2, 2, 'frame'),
    ('earL', 5.5, -7, -2.5, 1, 4, 4, 'earL'),
    ('armL', 5, -5, -6.5, 1, 1, 5, 'frame'),
    ('lens', -5, -5, -6.5, 10, 3, 1, 'lens'),
    ('rim', -5, -6, -6.5, 10, 1, 1, 'trim'),
    ('rimLow', -5, -2, -6.5, 10, 1, 1, 'trim'),
]
ANT_R = ('antR', -6.5, -11, 0, 1, 4, 1, 'ant')
ANT_L = ('antL', 5.5, -11, 0, 1, 4, 1, 'ant')
GEO = {
    'karma_scanner': COMMON + ONE_EYE,
    'advanced_karma_scanner': COMMON + ONE_EYE + [('rimLow', -5, -2, -6.5, 5, 1, 1, 'trim'), ANT_R],
    'elite_karma_scanner': COMMON + TWO_EYE + [ANT_R],
    'omni_karma_scanner': COMMON + TWO_EYE + [ANT_R, ANT_L, ('crest', -0.5, -11, -2.5, 1, 2, 4, 'trim')],
}
TW, TH = 64, 32


def pack(cubes):
    """Shelf-pack each cube's UV box (2*(w+d) x (d+h)) into 64x32."""
    order = sorted(range(len(cubes)), key=lambda i: -(cubes[i][6] + cubes[i][5]))
    pos = [None] * len(cubes)
    x = y = shelf = 0
    for i in order:
        c = cubes[i]
        w, h, d = c[4], c[5], c[6]
        bw, bh = 2 * (w + d), d + h
        if x + bw > TW:
            x, y, shelf = 0, y + shelf, 0
        assert y + bh <= TH, 'atlas full'
        pos[i] = (x, y)
        x += bw
        shelf = max(shelf, bh)
    return pos


def faces(u, v, w, h, d):
    """Cube UV rects like ModelPart's Cube: name -> (x0, y0, w, h)."""
    return {
        'down': (u + d, v, w, d), 'up': (u + d + w, v, w, d),
        'west': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'east': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h),
    }


def fill(img, r, col):
    x0, y0, w, h = r
    for yy in range(y0, y0 + h):
        for xx in range(x0, x0 + w):
            img.putpixel((xx, yy), col + (255,) if len(col) == 3 else col)


def frame_face(img, r, pal, kind):
    x0, y0, w, h = r
    main = pal['trim'] if kind == 'trim' else pal['main']
    for yy in range(y0, y0 + h):
        for xx in range(x0, x0 + w):
            edge = (w >= 3 and h >= 3) and (xx in (x0, x0 + w - 1) or yy in (y0, y0 + h - 1))
            img.putpixel((xx, yy), (pal['dark'] if edge else main) + (255,))


def glass(img, r):
    x0, y0, w, h = r
    for yy in range(y0, y0 + h):
        for xx in range(x0, x0 + w):
            t = (yy - y0) / max(1, h - 1)
            g = int(245 - 70 * t)
            if xx == x0 and yy == y0:
                g = 255
            img.putpixel((xx, yy), (g, g, g, 255))


def led(img, x, y):
    img.putpixel((x, y), (255, 255, 255, 255))


def armor_textures(tid):
    pal = TIERS[tid]
    cubes = GEO[tid]
    pos = pack(cubes)
    base = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))
    over = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))
    for c, (u, v) in zip(cubes, pos):
        name, x, y, z, w, h, d, kind = c
        fs = faces(u, v, w, h, d)
        for fname, r in fs.items():
            if kind == 'lens' and fname in ('north', 'south'):
                glass(base, r)
                continue
            frame_face(over, r, pal, kind)
        if kind == 'earR':            # a light on the outside of the earpiece
            x0, y0, fw, fh = fs['west']
            for p in ((x0 + 1, y0 + 1), (x0 + 2, y0 + 1), (x0 + 1, y0 + 2), (x0 + 2, y0 + 2)):
                led(base, *p); over.putpixel(p, (0, 0, 0, 0))
        if kind == 'earL':
            x0, y0, fw, fh = fs['east']
            for p in ((x0 + 1, y0 + 1), (x0 + 2, y0 + 1), (x0 + 1, y0 + 2), (x0 + 2, y0 + 2)):
                led(base, *p); over.putpixel(p, (0, 0, 0, 0))
        if kind == 'ant':             # the antenna's glowing tip
            pts = [(fs['up'][0], fs['up'][1])] + [(fs[f][0], fs[f][1]) for f in ('west', 'north', 'east', 'south')]
            for p in pts:
                led(base, *p); over.putpixel(p, (0, 0, 0, 0))
    d = os.path.join(RES, 'textures/models/armor')
    base.save(os.path.join(d, tid + '_visor.png'))
    over.save(os.path.join(d, tid + '_visor_overlay.png'))
    return pos


def java(all_pos):
    out = ['package com.xenowarriors.client.render;', '',
           '/** Generated by tools/xw/scanner/scanners.py - the Karma Scanner visors\' cubes (do not edit by hand). */',
           'final class ScannerVisorGeo {', '    private ScannerVisorGeo() {}', '',
           '    /** Per tier (KarmaScanner.Tier order): {u, v, x, y, z, w, h, d} per cube, all on the head. */',
           '    static final float[][][] CUBES = {']
    for tid in TIERS:
        out.append('        { // ' + tid)
        for c, (u, v) in zip(GEO[tid], all_pos[tid]):
            name, x, y, z, w, h, d, kind = c
            out.append('            {%s}, // %s' % (', '.join('%gF' % f for f in (u, v, x, y, z, w, h, d)), name))
        out.append('        },')
    out += ['    };', '}', '']
    with open(JAVA, 'w') as f:
        f.write('\n'.join(out))


def item_textures():
    d = os.path.join(RES, 'textures/item/scanner3d')
    os.makedirs(d, exist_ok=True)
    lens = Image.new('RGBA', (16, 16))
    glass(lens, (0, 0, 16, 16))
    for i in range(3):
        lens.putpixel((1 + i, 1), (255, 255, 255, 255))
    lens.save(os.path.join(d, 'lens.png'))
    for tid, pal in TIERS.items():
        for k in ('frame', 'trim'):
            im = Image.new('RGBA', (16, 16))
            frame_face(im, (0, 0, 16, 16), pal, k)
            im.save(os.path.join(d, tid + '_' + k + '.png'))


def item_models():
    for tid in TIERS:
        els = []
        for name, x, y, z, w, h, d, kind in GEO[tid]:
            fr = [8 - (x + w), 1 - (y + h), 9 - (z + d)]
            to = [8 - x, 1 - y, 9 - z]
            tex = '#trim' if kind == 'trim' else '#frame'
            fc = {}
            for fn, (fw, fh) in {'north': (w, h), 'south': (w, h), 'east': (d, h), 'west': (d, h), 'up': (w, d), 'down': (w, d)}.items():
                fc[fn] = {'uv': [0, 0, min(16, fw), min(16, fh)], 'texture': tex}
            if kind == 'lens':
                for fn in ('north', 'south'):
                    fc[fn] = {'uv': [0, 0, min(16, w * 3), min(16, h * 3)], 'texture': '#lens', 'tintindex': 0}
            if kind == 'ant':
                fc['up'] = {'uv': [0, 0, 1, 1], 'texture': '#lens', 'tintindex': 0}
            els.append({'name': name, 'from': fr, 'to': to, 'faces': fc})
        m3 = {
            'credit': 'tools/xw/scanner/scanners.py',
            'textures': {'frame': 'xenowarriors:item/scanner3d/%s_frame' % tid, 'trim': 'xenowarriors:item/scanner3d/%s_trim' % tid,
                         'lens': 'xenowarriors:item/scanner3d/lens', 'particle': 'xenowarriors:item/scanner3d/%s_frame' % tid},
            'elements': els,
            'display': {
                'thirdperson_righthand': {'rotation': [10, -30, 0], 'translation': [0, 2.5, 1], 'scale': [0.6, 0.6, 0.6]},
                'thirdperson_lefthand': {'rotation': [10, 30, 0], 'translation': [0, 2.5, 1], 'scale': [0.6, 0.6, 0.6]},
                'firstperson_righthand': {'rotation': [0, -40, 0], 'translation': [1.5, 3, 0], 'scale': [0.7, 0.7, 0.7]},
                'firstperson_lefthand': {'rotation': [0, 40, 0], 'translation': [1.5, 3, 0], 'scale': [0.7, 0.7, 0.7]},
                'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
                'fixed': {'rotation': [0, 180, 0], 'scale': [0.9, 0.9, 0.9]},
                'head': {'rotation': [0, 180, 0], 'translation': [0, 4, 0]},
            },
        }
        d3 = os.path.join(RES, 'models/item/3d')
        os.makedirs(d3, exist_ok=True)
        with open(os.path.join(d3, tid + '.json'), 'w') as f:
            json.dump(m3, f, indent=1)
        top = {
            'loader': 'forge:separate_transforms', 'gui_light': 'front', 'render_type': 'minecraft:cutout',
            'textures': {'particle': 'xenowarriors:item/' + tid},
            'base': {'parent': 'xenowarriors:item/3d/' + tid},
            'perspectives': {'gui': {'parent': 'minecraft:item/generated',
                                     'textures': {'layer0': 'xenowarriors:item/%s_lens' % tid, 'layer1': 'xenowarriors:item/' + tid}}},
        }
        with open(os.path.join(RES, 'models/item', tid + '.json'), 'w') as f:
            json.dump(top, f, indent=1)


def icons():
    """Flat inventory icons: layer0 = the lens (grey, dyed by tint), layer1 = the frame."""
    for tid, pal in TIERS.items():
        fr = Image.new('RGBA', (16, 16))
        ln = Image.new('RGBA', (16, 16))
        M, D, T = pal['main'] + (255,), pal['dark'] + (255,), pal['trim'] + (255,)
        two = tid in ('elite_karma_scanner', 'omni_karma_scanner')
        lx0, lx1 = (1, 14) if two else (1, 9)            # lens box (frame included)
        for x in range(lx0, lx1 + 1):
            fr.putpixel((x, 3), T); fr.putpixel((x, 9), D)
        for y in range(3, 10):
            fr.putpixel((lx0, y), D); fr.putpixel((lx1, y), D)
        for y in range(4, 9):
            for x in range(lx0 + 1, lx1):
                if two and x in (7, 8):
                    fr.putpixel((x, y), M)               # the bridge between the eyes
                    continue
                g = int(245 - 70 * (y - 4) / 4)
                ln.putpixel((x, y), (255, 255, 255, 255) if (x, y) == (lx0 + 1, 4) else (g, g, g, 255))
        # earpiece box and the arm
        ex = 10 if not two else 11
        if not two:
            for y in range(6, 12):
                for x in range(ex, ex + 4):
                    edge = x in (ex, ex + 3) or y in (6, 11)
                    fr.putpixel((x, y), D if edge else M)
            fr.putpixel((ex + 1, 8), (0, 0, 0, 0)); ln.putpixel((ex + 1, 8), (255, 255, 255, 255))
            fr.putpixel((9, 7), D)
        else:
            for x in (0, 15):
                for y in range(5, 11):
                    fr.putpixel((x, y), D if y in (5, 10) else M)
        if tid != 'karma_scanner':                       # antenna
            ax = 12 if not two else 15
            for y in range(1, 6):
                fr.putpixel((ax, y), D)
            fr.putpixel((ax, 0), (0, 0, 0, 0)); ln.putpixel((ax, 0), (255, 255, 255, 255))
            if tid == 'omni_karma_scanner':
                for y in range(1, 5):
                    fr.putpixel((0, y), D)
                ln.putpixel((0, 0), (255, 255, 255, 255))
                for x in range(6, 10):
                    fr.putpixel((x, 1), T); fr.putpixel((x, 2), M)
        fr.save(os.path.join(RES, 'textures/item', tid + '.png'))
        ln.save(os.path.join(RES, 'textures/item', tid + '_lens.png'))


if __name__ == '__main__':
    allpos = {t: armor_textures(t) for t in TIERS}
    java(allpos)
    item_textures()
    item_models()
    icons()
    print('ok')
