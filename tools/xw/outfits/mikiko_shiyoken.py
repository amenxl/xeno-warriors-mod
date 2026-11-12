"""Mikiko's Shiyoken outfit (worn while he's in DragonMineZ's Shiyoken forms, over whatever outfit he had on):
from the user's reference picture - a black hoodie with an orange fire swirl on the chest and flames licking up from the hem
and cuffs, dark trousers with flame tongues up the shins, black-and-orange shoes; his hair and goggles stay his own.
Writes mikiko_shiyoken.png (skin), mikiko_shiyoken_details.png + models/detail/mikiko_shiyoken.json (his detail model with the
clothes repainted, plus a rolled-down hood at his neck and a raised swirl emblem)."""
import json, math, colorsys, sys, copy
from PIL import Image

RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
CH = RES + 'textures/entity/character/'
BLACK = (22, 18, 24); BLACK2 = (34, 27, 34); CHAR = (44, 36, 42)
ORANGE = (255, 122, 30); ORANGE2 = (255, 170, 60); EMBER = (214, 66, 20); YELLOW = (255, 214, 100)

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
def lum(c): return (0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]) / 255

# ------------------------------------------------------------------ the skin
def faces(u0, v0, w, h, d):
    return {'top': (u0 + d, v0, w, d), 'bottom': (u0 + d + w, v0, w, d), 'right': (u0, v0 + d, d, h),
            'front': (u0 + d, v0 + d, w, h), 'left': (u0 + d + w, v0 + d, d, h), 'back': (u0 + 2 * d + w, v0 + d, w, h)}

def flame(x, y, h, seed, height):
    """True where a flame tongue reaches at column x, row y (0 = top) of an h-tall face; flames rise from the bottom."""
    k = (x * 37 + seed * 11) % 7
    reach = height + (2 if k in (1, 4) else 1 if k in (2, 6) else 0)
    return y >= h - reach

def skin():
    s = Image.open(CH + 'mikiko.png').convert('RGBA'); px = s.load()
    # clear the outer layer on the body, arms and legs (the details carry the clothes' bulk)
    for (u, v, w, h) in ((16, 32, 24, 16), (40, 32, 16, 16), (0, 32, 16, 16), (0, 48, 16, 16), (48, 48, 16, 16)):
        for y in range(v, v + h):
            for x in range(u, u + w): px[x, y] = (0, 0, 0, 0)
    def paint(box, kind):
        for fname, (fu, fv, fw, fh) in faces(*box).items():
            for y in range(fh):
                for x in range(fw):
                    shade = 1.0 + ((x * 3 + y * 5) % 4 - 1.5) * 0.03
                    c = BLACK if kind != 'leg' else CHAR
                    if fname in ('top', 'bottom') and kind == 'leg' and fname == 'bottom': c = BLACK
                    if kind == 'body':
                        if fname in ('front', 'back', 'left', 'right') and flame(x, y, fh, fu + (1 if fname == 'back' else 0), 3): c = lerp(EMBER, ORANGE, (y - (fh - 4)) / 4)
                        if fname == 'front' and y == 1: c = ORANGE2                      # (the hood's drawstring line)
                    if kind == 'arm':
                        if fname in ('front', 'back', 'left', 'right') and flame(x, y, fh, fu + 3, 2): c = lerp(EMBER, ORANGE, 0.6)
                        if fname in ('front', 'back', 'left', 'right') and y == fh - 1: c = ORANGE   # cuff
                    if kind == 'leg':
                        if fname in ('front', 'back', 'left', 'right'):
                            if y >= fh - 2: c = BLACK if y == fh - 2 else ORANGE                 # shoes: black, orange sole
                            elif flame(x, y + 2, fh, fu + 5, 5) and y >= 4: c = lerp(EMBER, ORANGE, max(0, (y - 5)) / 5)
                        if fname == 'bottom': c = ORANGE
                    px[fu + x, fv + y] = tuple(max(0, min(255, int(ch * shade))) for ch in c) + (255,)
    paint((16, 16, 8, 12, 4), 'body')
    paint((40, 16, 4, 12, 4), 'arm'); paint((32, 48, 4, 12, 4), 'arm')
    paint((0, 16, 4, 12, 4), 'leg'); paint((16, 48, 4, 12, 4), 'leg')
    # the fire swirl on the chest (front of the body: 20..27 x 20..31)
    sw = [(3, 3), (4, 3), (5, 4), (5, 5), (4, 6), (3, 6), (2, 5), (2, 4), (3, 4), (4, 4), (4, 5)]
    for (x, y) in sw: px[20 + x - 0, 20 + y] = ORANGE + (255,)
    px[20 + 3, 20 + 5] = YELLOW + (255,)
    s.save(CH + 'mikiko_shiyoken.png')
    return s

# ------------------------------------------------------------------ the details
def cube_rects(c):
    w, h, d = [max(1, math.ceil(v - 1e-6)) for v in c['size']]
    u, v = c['uv']
    return (u, v, 2 * d + 2 * w, d + h)

def walk(node, out):
    for c in node.get('cubes', []): out.append(c)
    for ch in node.get('children', []): walk(ch, out)

def recolor(c, a):
    """Clothing colours onto the Shiyoken palette: almost all black, a warm ember in what was red, charcoal for metal."""
    r, g, b = c
    hh, ll, ss = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    L = lum(c)
    hue = hh * 360
    if ss > 0.35 and (hue < 20 or hue > 340) and L > 0.12:          # reds (belt, wraps) -> dark ember
        return lerp((58, 20, 16), (120, 36, 18), min(1, L * 1.4))
    if ss > 0.35 and 20 <= hue < 55 and L > 0.25:                   # the orange shirt -> black
        return lerp(BLACK, BLACK2, min(1, L))
    if L > 0.72:                                                    # white / cream -> pale charcoal
        return lerp((62, 52, 58), (88, 74, 80), (L - 0.72) / 0.28)
    if ss < 0.18 and L > 0.35:                                      # greys (the shoulder plate, buckles) -> charcoal
        return lerp(CHAR, (66, 54, 62), (L - 0.35) / 0.4)
    return lerp(BLACK, BLACK2, min(1, L * 2.2))                     # navy cloth -> black

def flames_on(px, c, seed, height, glow=True):
    """Flame tongues licking up the side faces of a cube (its box UV), from its bottom edge."""
    w, h, d = [max(1, math.ceil(v - 1e-6)) for v in c['size']]
    u, v = c['uv']
    if h < 3: return
    for fname, (fu, fv, fw, fh) in faces(u, v, w, h, d).items():
        if fname in ('top', 'bottom'): continue
        for y in range(fh):
            for x in range(fw):
                if not flame(x + fu, y, fh, seed, height): continue
                t = (y - (fh - height - 2)) / (height + 2)
                q = (fu + x, fv + y)
                if px[q][3] == 0: continue
                px[q] = (lerp(EMBER, ORANGE, max(0, min(1, t))) if glow else EMBER) + (255,)

def details():
    mj = json.load(open(RES + 'models/detail/mikiko.json'))
    atlas = Image.open(CH + 'mikiko_details.png').convert('RGBA'); px = atlas.load()
    head_rects = []
    hc = []; walk(mj['bones']['head'], hc)
    for c in hc: head_rects.append(cube_rects(c))
    def in_head(x, y): return any(u <= x < u + w and v <= y < v + h for (u, v, w, h) in head_rects)
    done = set()
    for bone in ('body', 'right_arm', 'left_arm', 'right_leg', 'left_leg'):
        cs = []; walk(mj['bones'][bone], cs)
        for c in cs:
            u, v, w, h = cube_rects(c)
            for y in range(v, v + h):
                for x in range(u, u + w):
                    if (x, y) in done or x >= atlas.width or y >= atlas.height or in_head(x, y): continue
                    r, g, b, a = px[x, y]
                    if a == 0: continue
                    done.add((x, y))
                    px[x, y] = recolor((r, g, b), a) + (a,)
    # flames licking up: the jacket's hem, the sleeve cuffs, and up the trousers from the shoes
    for bone, height, tall in (('body', 3, 8), ('right_arm', 2, 6), ('left_arm', 2, 6), ('right_leg', 4, 8), ('left_leg', 4, 8)):
        cs = []; walk(mj['bones'][bone], cs)
        for i, c in enumerate(cs):                                  # (only the big panels: the trims stay black)
            if c['size'][1] >= tall and c['size'][0] >= 3: flames_on(px, c, i, height)
    # new pieces: a rolled-down hood at the back of his neck and a raised fire swirl on his chest
    def swatch(u, v, base, accent=None):
        for y in range(v, v + 16):
            for x in range(u, u + 16):
                k = 1.0 + ((x * 7 + y * 13) % 5 - 2) * 0.04
                c = base
                if accent and (y - v) in (0, 15): c = accent
                px[x, y] = tuple(max(0, min(255, int(ch * k))) for ch in c) + (255,)
    swatch(240, 0, BLACK2, ORANGE)                                   # hood
    swatch(0, 80, ORANGE)                                            # emblem
    swatch(16, 80, YELLOW)
    hood = [{'name': 'shiyo_hood', 'pivot': [0, 0, 0], 'rot': [0, 0, 0], 'children': [], 'cubes': [
        {'uv': [240, 0], 'from': [-3.6, -0.6, 2.3], 'size': [7.2, 2.4, 2.2]},
        {'uv': [240, 0], 'from': [-3.0, 1.6, 2.6], 'size': [6.0, 1.4, 1.6]},
        {'uv': [240, 0], 'from': [-4.4, -0.2, -1.2], 'size': [1.2, 1.4, 3.8]},
        {'uv': [240, 0], 'from': [3.2, -0.2, -1.2], 'size': [1.2, 1.4, 3.8]}]},
        {'name': 'shiyo_swirl', 'pivot': [0, 0, 0], 'rot': [0, 0, 0], 'children': [], 'cubes': [
        {'uv': [0, 80], 'from': [-1.6, 3.2, -2.75], 'size': [3.2, 0.6, 0.5]},
        {'uv': [0, 80], 'from': [1.0, 3.8, -2.75], 'size': [0.6, 2.4, 0.5]},
        {'uv': [0, 80], 'from': [-1.6, 5.6, -2.75], 'size': [2.6, 0.6, 0.5]},
        {'uv': [0, 80], 'from': [-1.6, 4.4, -2.75], 'size': [0.6, 1.2, 0.5]},
        {'uv': [16, 80], 'from': [-1.0, 4.4, -2.85], 'size': [1.2, 0.6, 0.5]}]}]
    mj['bones']['body']['children'] = mj['bones']['body'].get('children', []) + hood
    json.dump(mj, open(RES + 'models/detail/mikiko_shiyoken.json', 'w'), separators=(',', ':'))
    atlas.save(CH + 'mikiko_shiyoken_details.png')
    return mj, atlas

if __name__ == '__main__':
    s = skin()
    mj, atlas = details()
    if len(sys.argv) > 1:
        sys.path.insert(0, '/home/claude/xw/ultima')
        import ultima as U
        im = U.preview(mj, s, atlas, Image.open(CH + 'mikiko_face.png').convert('RGBA'), yaws=(0, 35, 180), scale=7)
        im.save(sys.argv[1]); print(im.size)
