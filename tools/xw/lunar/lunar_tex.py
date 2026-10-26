"""Lunar Rhalix textures (from the user's art): vivid purple hair fading to lavender tips, white crescent marks
under the eyes, brighter glowing red eyes."""
import json, glob, os
from PIL import Image
A = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
CH = A + 'textures/entity/character/'
LAV = (222, 190, 245)

def cloth(c):
    """Lunar clothes (from the art): purple coat -> white/lavender, white shirt -> dark, gold -> silver."""
    r, g, b, a = c
    if a == 0: return c
    lum = (r + g + b) / 3
    if b > g + 30 and r > g + 20:                                    # purple
        t = min(1.0, lum / 130.0)
        return (int(196 + 46 * t), int(182 + 44 * t), int(222 + 30 * t), a)
    if r > 140 and g > 100 and b < 100 and r > b + 60:               # gold trim -> silver
        t = min(1.0, lum / 200.0)
        return (int(150 + 80 * t), int(155 + 80 * t), int(170 + 75 * t), a)
    if min(r, g, b) > 140 and max(r, g, b) - min(r, g, b) < 30:      # white / gray shirt -> dark collar and shirt
        t = (lum - 140) / 115.0
        return (int(34 + 26 * t), int(28 + 20 * t), int(44 + 30 * t), a)
    if r > g + 40 and r > b + 20 and r < 170 and g < 60:             # dark red / maroon -> deep plum
        return (int(r * .55 + 20), int(g * .6 + 10), int(b * .8 + 40), a)
    return c

def skin():
    im = Image.open(CH + 'rhalix.png').convert('RGBA'); px = im.load()
    for y in range(64):
        for x in range(64):
            if y < 16: continue                                      # head / hat rows untouched
            px[x, y] = cloth(px[x, y])
    im.save(CH + 'rhalix_lunar.png')

def walk(n, off, acc):
    for c in n['cubes']: acc.append((c, off))
    for ch in n.get('children', []):
        walk(ch, [off[i] + ch['pivot'][i] for i in range(3)], acc)
    return acc

def details():
    d = Image.open(CH + 'rhalix_details.png').convert('RGBA'); m = Image.open(CH + 'rhalix_details_hairmask.png').convert('RGBA')
    px, mp = d.load(), m.load()
    out = d.copy(); op = out.load()
    # 1) all hair: more vivid violet
    for y in range(256):
        for x in range(256):
            if mp[x, y][3] == 0:
                op[x, y] = cloth(px[x, y]); continue
            r, g, b, a = px[x, y]
            if r + g + b < 130:                                   # the black part of his hair -> very light purple / white
                k = (r + g + b) / 130.0
                op[x, y] = (int(228 + 14 * k), int(212 + 14 * k), int(244 + 8 * k), a)
                continue
            if b > r and b > 90:
                op[x, y] = (min(255, int(r * 1.12)), min(255, int(g * 1.5 + 12)), min(255, int(b * 1.22)), a)
    # 2) tips: lower parts of hanging locks fade to lavender
    model = json.load(open(A + 'models/detail/rhalix.json'))
    for c, off in walk(model['bones']['head'], [0, 0, 0], []):
        (fx, fy, fz), (w, h, dd), (u, v) = c['from'], c['size'], c['uv']
        y0 = fy + off[1]; y1 = y0 + h
        if y1 < -4.5: continue
        W, H, D = max(1, round(w)), max(1, round(h)), max(1, round(dd))
        for r in range(H):
            wy = y0 + (r + .5) / H * h
            t = max(0.0, min(1.0, (wy + 4.5) / 3.5))
            if t <= 0: continue
            for xx in range(u, u + 2 * D + 2 * W):
                yy = v + D + r
                if not (0 <= xx < 256 and 0 <= yy < 256) or mp[xx, yy][3] == 0: continue
                R, G, B, a = op[xx, yy]
                k = t * (0.8 if R + G + B < 120 else 0.92)
                op[xx, yy] = (int(R + (LAV[0] - R) * k), int(G + (LAV[1] - G) * k), int(B + (LAV[2] - B) * k), a)
    out.save(CH + 'rhalix_lunar_details.png')

L_CRES = [(3, 20), (3, 21), (3, 22), (4, 23), (5, 24), (6, 24), (7, 24), (8, 23), (4, 21), (4, 22), (5, 23), (6, 23)]
def face(src, dst):
    im = Image.open(src).convert('RGBA'); px = im.load()
    for (x, y) in L_CRES:
        for xx in (x, 31 - x):
            if px[xx, y][3] > 0: px[xx, y] = (238, 236, 248, 255)
    for (x, y) in [(4, 20), (7, 23), (27, 20), (24, 23)]:            # soft edge
        if px[x, y][3] > 0:
            r, g, b, a = px[x, y]; px[x, y] = ((r + 238) // 2, (g + 236) // 2, (b + 248) // 2, a)
    for y in range(12, 20):                                          # eyes glow brighter red
        for x in range(32):
            r, g, b, a = px[x, y]
            if a and r > 150 and g < 90: px[x, y] = (255, min(255, g + 30), min(255, b + 30), a)
    im.save(dst)

if __name__ == '__main__':
    details(); skin()
    import lunar_wings; lunar_wings.main()
    n = 0
    for f in sorted(glob.glob(CH + 'rhalix_face*.png')):
        face(f, f.replace('rhalix_face', 'rhalix_lunar_face')); n += 1
    print('faces', n)
