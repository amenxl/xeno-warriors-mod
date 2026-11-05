"""The 'starving' faces (and 'starving2', its second frame) for every face set, and the HUD portraits.

Made from each set's own exhausted faces (droopy eyes, open mouth): the skin goes pale and sallow and dark shadows
sink in under the eyes. Pixel sets (64x64, the face in the top-left 32x32) and the drawn sets (256x256, face in the
top-left 128x128) both work. HUD portraits get a pale, drained version of their exhausted portrait.

usage: python3 tools/xw/faces/starving.py [preview.png]
"""
import glob, os, sys
from collections import Counter
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', '..', 'src', 'main', 'resources', 'assets', 'xenowarriors', 'textures')
CH = os.path.join(ROOT, 'entity', 'character')
HUD = os.path.join(ROOT, 'gui', 'hud')


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def sallow(c, amt):
    """Paler, a little grey-green: drained of colour."""
    r, g, b, a = c
    l = lum(c)
    tr, tg, tb = l * 1.02 + 6, l * 1.0 + 6, l * 0.86
    return (int(r + (tr - r) * amt), int(g + (tg - g) * amt), int(b + (tb - b) * amt), a)


def starve(im):
    im = im.convert('RGBA')
    W = im.size[0]
    s = max(1, W // 64)
    R = 32 * s
    px = im.load()
    cols = Counter(px[x, y] for y in range(int(R * 0.3), int(R * 0.95)) for x in range(R) if px[x, y][3] > 200)
    if not cols:
        return im
    skin = cols.most_common(1)[0][0]
    sl = lum(skin)
    out = im.copy()
    op = out.load()
    # sunken eyes: a shadow under each eye (the lowest dark row of the eye, one face pixel below it)
    for half in (0, 1):
        xs = range(0, R // 2) if half == 0 else range(R // 2, R)
        dark = [(x, y) for y in range(int(R * 0.36), int(R * 0.68)) for x in xs
                if px[x, y][3] > 200 and lum(px[x, y]) < sl * 0.5]
        if not dark:
            continue
        top = min(y for _, y in dark)
        eye = [(x, y) for x, y in dark if y <= top + 4 * s]               # (the eye, not the mouth below it)
        yb = max(y for _, y in eye)
        row = [x for x, y in eye if y >= yb - s]
        x0, x1 = min(row), max(row)
        for y in range(yb + s, yb + 2 * s):
            for x in range(x0 + s, x1 - s + 1):
                c = op[x, y]
                if c[3] > 200 and abs(lum(c) - sl) < sl * 0.25:
                    op[x, y] = (int(c[0] * 0.60), int(c[1] * 0.50), int(c[2] * 0.66), c[3])
        for y in range(yb + 2 * s, yb + 3 * s):                          # (a fainter second line: hollow)
            for x in range(x0 + 2 * s, x1 - 2 * s + 1):
                c = op[x, y]
                if c[3] > 200 and abs(lum(c) - sl) < sl * 0.25:
                    op[x, y] = (int(c[0] * 0.86), int(c[1] * 0.82), int(c[2] * 0.88), c[3])
    # pale, sallow skin over the whole face
    for y in range(R):
        for x in range(R):
            c = op[x, y]
            if c[3] > 0:
                op[x, y] = sallow(c, 0.38)
    return out


def portrait(im):
    im = im.convert('RGBA')
    out = im.copy()
    op = out.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            c = op[x, y]
            if c[3] > 0:
                d = sallow(c, 0.38)
                op[x, y] = (int(d[0] * 0.9), int(d[1] * 0.9), int(d[2] * 0.92), d[3])
    return out


def main():
    made = []
    for p in sorted(glob.glob(os.path.join(CH, '*_face_exhausted.png'))):
        key = os.path.basename(p)[:-len('_face_exhausted.png')]
        two = os.path.join(CH, key + '_face_exhausted2.png')
        a = starve(Image.open(p))
        b = starve(Image.open(two if os.path.exists(two) else p))
        a.save(os.path.join(CH, key + '_face_starving.png'))
        b.save(os.path.join(CH, key + '_face_starving2.png'))
        made.append((key, Image.open(p).convert('RGBA'), a, b))
    for p in sorted(glob.glob(os.path.join(HUD, 'portrait_*_exhausted*.png'))):
        portrait(Image.open(p)).save(p.replace('_exhausted', '_starving'))
    print(len(made), 'face sets')
    if len(sys.argv) > 1:                                                  # a preview sheet: exhausted | starving | starving2
        cell = 128
        sheet = Image.new('RGBA', (cell * 3 * 4, cell * ((len(made) + 3) // 4)), (70, 70, 78, 255))
        for i, (k, e, a, b) in enumerate(made):
            R = 32 * max(1, e.size[0] // 64)
            for j, im in enumerate((e, a, b)):
                sheet.alpha_composite(im.crop((0, 0, R, R)).resize((cell, cell), Image.NEAREST), ((i % 4) * cell * 3 + j * cell, (i // 4) * cell))
        sheet.save(sys.argv[1])


if __name__ == '__main__':
    main()
