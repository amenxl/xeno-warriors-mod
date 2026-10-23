"""Repaint the base 64x64 skins to match the official art (faces come from paint.py)."""
import subprocess, random
from PIL import Image

subprocess.run(['python3', '/home/claude/xw/paint.py'], cwd='/home/claude/xw', check=True)

def box(u, v, w, h, d):
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
            'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}

HAT = box(32, 0, 8, 8, 8); BODY = box(16, 16, 8, 12, 4); JACKET = box(16, 32, 8, 12, 4)
RARM = box(40, 16, 4, 12, 4); RSLV = box(40, 32, 4, 12, 4); LARM = box(32, 48, 4, 12, 4); LSLV = box(48, 48, 4, 12, 4)
RLEG = box(0, 16, 4, 12, 4); RPNT = box(0, 32, 4, 12, 4); LLEG = box(16, 48, 4, 12, 4); LPNT = box(0, 48, 4, 12, 4)

def hx(h):
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def paint(img, b, fn, seed=1, noise=0.08):
    rnd = random.Random(seed)
    for face, (u, v, w, h) in b.items():
        for y in range(h):
            for x in range(w):
                c = fn(face, x, y, w, h)
                if c is None:
                    img.putpixel((u + x, v + y), (0, 0, 0, 0)); continue
                c = hx(c); f = 1 + (rnd.random() - .5) * noise
                img.putpixel((u + x, v + y), tuple(max(0, min(255, int(k * f))) for k in c) + (255,))

def clear(img, *bs):
    for b in bs:
        paint(img, b, lambda *a: None)

# ------------------------------------------------------------ Mikiko
NAVY, NAVY_D, ORANGE, ORANGE_D, RED = '#1d2034', '#141626', '#f19a22', '#c8741a', '#c81f26'
SKIN, SKIN_D, WHITE, BLACK = '#966240', '#7a4e32', '#ececec', '#17161b'
img = Image.open('/home/claude/xw/tex/mikiko.png').convert('RGBA')
clear(img, HAT, JACKET, RSLV, LSLV, RPNT, LPNT)

def m_body(face, x, y, w, h):
    """Clean orange zip-up shirt under the cropped jacket (no stray pixels)."""
    if face == 'top': return NAVY
    if face == 'bottom': return NAVY_D
    if y == 11: return RED                               # belt row
    if face == 'front':
        if y <= 5 and (x <= 1 or x >= 6): return NAVY    # jacket panels (covered by 3D jacket)
        if x == 4: return '#a7a7ae'                       # zipper
        return ORANGE_D if y == 10 else ORANGE
    if face == 'back':
        return NAVY if y <= 5 else (ORANGE_D if y == 10 else ORANGE)
    return NAVY if y <= 5 else (ORANGE_D if y == 10 else ORANGE)
paint(img, BODY, m_body, 2, noise=0)

def m_arm(glove):
    def f(face, x, y, w, h):
        if face == 'top': return NAVY
        if face == 'bottom': return BLACK if glove else SKIN
        if y <= 4: return NAVY
        if y == 5: return WHITE
        if glove and y >= 8: return BLACK if y < 11 else SKIN_D
        if not glove and y in (8, 9): return RED
        return SKIN if y < 11 else SKIN_D
    return f
paint(img, RARM, m_arm(True), 3)
paint(img, LARM, m_arm(False), 4)

def m_leg(face, x, y, w, h):
    if face == 'top': return NAVY
    if face == 'bottom': return '#9a1218'
    if y <= 5: return NAVY if (x + y) % 5 else NAVY_D
    if y in (6, 7): return SKIN if y == 7 or face != 'front' else SKIN_D
    if y == 8: return NAVY_D
    if y >= 11: return '#9a1218'
    return NAVY_D if (face == 'front' and y == 10) else RED
paint(img, RLEG, m_leg, 5)
paint(img, LLEG, m_leg, 6)
img.save('/home/claude/xw/tex/mikiko.png')

# ------------------------------------------------------------ Rhalix
COAT, COAT_D, MAROON = '#9b2bc0', '#7a1e98', '#6a1f3a'
SKIN, SKIN_D, TANK, TANK_D, SHIRT = '#5a3621', '#462919', '#e8e6ea', '#c4c2c8', '#c8a838'
JEANS, JEANS_L, JEANS_D, BOOT = '#1e2a5a', '#2f47a0', '#141c3e', '#8a2440'
img = Image.open('/home/claude/xw/tex/rhalix.png').convert('RGBA')
clear(img, HAT, JACKET, RSLV, LSLV, RPNT, LPNT)

def r_body(face, x, y, w, h):
    if face == 'top': return COAT
    if face == 'bottom': return JEANS_D
    if face == 'front':
        if x <= 1 or x >= 6: return COAT
        if y == 11: return MAROON
        if y <= 1:
            return TANK if x in (2, 5) else SKIN if y == 0 else SKIN_D
        if y >= 8: return SHIRT
        return TANK if (x + y) % 4 else TANK_D
    return COAT if (x + y) % 6 else COAT_D
paint(img, BODY, r_body, 7)

def r_arm(face, x, y, w, h):
    if face == 'top': return COAT
    if face == 'bottom': return SKIN
    if y <= 9: return COAT if (x + y) % 5 else COAT_D
    if y == 10: return MAROON
    return SKIN
paint(img, RARM, r_arm, 8)
paint(img, LARM, r_arm, 9)

def r_leg(face, x, y, w, h):
    if face == 'top': return JEANS
    if face == 'bottom': return '#5a1428'
    if y <= 4:
        if face == 'front' and x == 1: return JEANS_L
        return JEANS if (x + y) % 3 else JEANS_D
    return BOOT
paint(img, RLEG, r_leg, 10)
paint(img, LLEG, r_leg, 11)
img.save('/home/claude/xw/tex/rhalix.png')
print('skins ok')
