"""Fusion looks (original designs): the leader's own model and textures, recoloured with the partner's palette.
Writes textures/entity/character/fusion/<key>.png (skin) and <key>_details.png (detail atlas) for each variant.
Rules: (hue_lo, hue_hi, sat_min, sat_max, val_min, val_max) -> (hue or None, sat mul/abs, val mul/abs)."""
import colorsys, json, sys
from PIL import Image
T = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'

def recolor(img, rules):
    img = img.convert('RGBA'); px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            hd = h * 360
            for (lo, hi, smin, smax, vmin, vmax), (nh, ns, nv) in rules:
                inh = (lo <= hd <= hi) if lo <= hi else (hd >= lo or hd <= hi)
                if inh and smin <= s <= smax and vmin <= v <= vmax:
                    h2 = h if nh is None else nh / 360
                    s2 = min(1, s * ns[1]) if ns[0] == 'mul' else ns[1]
                    v2 = min(1, v * nv[1]) if nv[0] == 'mul' else min(1, nv[1] * (0.55 + 0.45 * v))
                    rr, gg, bb = colorsys.hsv_to_rgb(h2, s2, v2)
                    px[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), a)
                    break
    return img

M1 = ('mul', 1.0)
def m(x): return ('mul', x)
def a(x): return ('abs', x)

# Rhalix: purple coat/hair ~265-320, yellow shirt ~40-65 (sat>.55), jeans blue ~200-245, red-violet pants ~320-350
# Mikiko: orange jacket ~25-45 (sat>.7, bright), navy ~200-250, reds ~345-15
VARIANTS = {
    # Rhalix leading, Mikiko's colours
    'rhalix_mikiko_a': ('rhalix', 0x9A1C26, [((262, 322, .25, 1, 0, 1), (355, m(1.05), m(1.0))),        # coat -> crimson
                                              ((38, 70, .5, 1, .5, 1), (30, a(.9), m(1.0))),            # shirt -> Mikiko orange
                                              ((322, 352, .3, 1, 0, 1), (225, m(.8), m(.6)))]),          # pants -> navy
    'rhalix_mikiko_b': ('rhalix', 0x2B2F6E, [((262, 322, .25, 1, 0, 1), (228, m(.85), m(.55))),         # coat -> Mikiko navy
                                              ((38, 70, .5, 1, .5, 1), (28, a(.92), m(1.0))),            # shirt -> orange
                                              ((322, 352, .3, 1, 0, 1), (2, m(1.1), m(1.0)))]),          # pants -> red
    'rhalix_mikiko_c': ('rhalix', 0xE0641E, [((38, 70, .5, 1, .5, 1), (8, a(.85), m(.95))),             # shirt -> fire red
                                              ((322, 352, .3, 1, 0, 1), (28, m(1.0), m(1.1)))]),         # pants -> orange; coat stays purple, hair orange
    # Mikiko leading, Rhalix's colours
    'mikiko_rhalix_a': ('mikiko', 0x6A2AA8, [((22, 48, .7, 1, .6, 1), (285, a(.75), m(.8))),            # jacket -> Rhalix purple
                                              ((195, 255, .2, 1, 0, 1), (270, m(.9), m(1.0)))]),         # navy -> deep violet
    'mikiko_rhalix_b': ('mikiko', 0x8E2A6E, [((22, 48, .7, 1, .6, 1), (50, a(.85), m(1.0))),            # jacket -> Rhalix's yellow
                                              ((195, 255, .2, 1, 0, 1), (285, m(1.1), m(1.2)))]),        # navy -> purple
    # Mikiko leading, Yuri's colours (black and white, green)
    'mikiko_yuri_a': ('mikiko', 0x1E1E22, [((22, 48, .7, 1, .6, 1), (30, a(.06), a(.95))),             # jacket -> white
                                            ((195, 255, .2, 1, 0, 1), (0, a(0), m(.45))),               # navy -> black
                                            ((340, 15, .5, 1, .3, 1), (135, m(.9), m(.8)))]),            # red -> Yuri's green
    'mikiko_yuri_b': ('mikiko', 0x2E5A34, [((22, 48, .7, 1, .6, 1), (138, a(.7), m(.75))),             # jacket -> green
                                            ((195, 255, .2, 1, 0, 1), (0, a(0), m(.4)))]),              # navy -> black
    # Yuri leading, Mikiko's colours
    'yuri_mikiko_a': ('yuri', 0xB02020, [((0, 360, 0, .18, 0, .35), (225, a(.55), m(1.6))),            # black -> navy
                                          ((0, 360, 0, .12, .75, 1), (30, a(.9), m(1.0))),              # white -> orange
                                          ((100, 170, .3, 1, 0, 1), (2, m(1.1), m(1.1)))]),             # green -> red
}

if __name__ == '__main__':
    out = T + 'fusion/'
    meta = {}
    for key, (base, hair, rules) in VARIANTS.items():
        recolor(Image.open(T + base + '.png'), rules).save(out + key + '.png')
        recolor(Image.open(T + base + '_details.png'), rules).save(out + key + '_details.png')
        meta[key] = {'base': base, 'hair': '#%06X' % hair}
    print(json.dumps(meta, indent=1))
