"""Possessed-person skins (64x64, classic humanoid layout, right limbs only - left limbs mirror)."""
from PIL import Image
import random, sys
OUT = sys.argv[1]

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)

def box(px, u, v, w, h, d, col, noise=0.06, rng=None):
    """Fill all six faces of a cube's UV region with a color (light noise)."""
    regions = [(u + d, v, w, d), (u + d + w, v, d and w, d), (u, v + d, d, h), (u + d, v + d, w, h),
               (u + d + w, v + d, d, h), (u + d + w + d, v + d, w, h)]
    for (x0, y0, ww, hh) in regions:
        for x in range(x0, x0 + ww):
            for y in range(y0, y0 + hh):
                px[x, y] = shade(col, 1 + rng.uniform(-noise, noise))

def faces(u, v, w, h, d):
    return {'top': (u + d, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h)}

def rect(px, x0, y0, ww, hh, col, rng, noise=0.05):
    for x in range(x0, x0 + ww):
        for y in range(y0, y0 + hh):
            px[x, y] = shade(col, 1 + rng.uniform(-noise, noise))

PEOPLE = [
    # skin, hair, hair style, shirt, pants, shoes, extra
    ((236, 196, 164), (70, 45, 25), 'short', (60, 90, 150), (50, 50, 60), (40, 30, 25), 'collar'),
    ((198, 140, 100), (25, 20, 18), 'long', (170, 50, 60), (60, 70, 100), (30, 30, 30), 'none'),
    ((120, 80, 55), (20, 16, 14), 'buzz', (230, 230, 225), (40, 45, 55), (200, 200, 200), 'stripe'),
    ((245, 210, 180), (215, 175, 90), 'long', (90, 140, 90), (100, 80, 60), (60, 40, 30), 'none'),
    ((160, 110, 75), (45, 30, 20), 'short', (60, 60, 65), (30, 35, 45), (25, 25, 25), 'hoodie'),
    ((228, 180, 150), (150, 60, 30), 'spiky', (220, 150, 50), (55, 60, 70), (90, 60, 40), 'stripe'),
    ((90, 60, 45), (15, 12, 10), 'long', (120, 70, 150), (35, 35, 40), (70, 30, 30), 'collar'),
    ((210, 165, 130), (110, 110, 115), 'short', (80, 110, 110), (90, 85, 75), (50, 40, 35), 'hoodie'),
]

for i, (skin, hair, style, shirt, pants, shoes, extra) in enumerate(PEOPLE):
    rng = random.Random(100 + i)
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = img.load()
    pale = tuple(int(c * 0.82 + 40 * 0.18) for c in skin)   # possessed: a little grey and drained
    # head
    box(px, 0, 0, 8, 8, 8, pale, rng=rng)
    f = faces(0, 0, 8, 8, 8)
    hx, hy, _, _ = f['top']; rect(px, hx, hy, 8, 8, hair, rng)
    rows = {'buzz': 1, 'short': 2, 'spiky': 3, 'long': 3}[style]
    for side in ('right', 'left', 'back', 'front'):
        x0, y0, ww, hh = f[side]
        n = rows if side == 'front' else (8 if style == 'long' and side == 'back' else rows + (2 if style == 'long' else 1))
        if side == 'front' and style == 'spiky':
            for x in range(ww):
                for y in range(rows + (x % 2)):
                    px[x0 + x, y0 + y] = shade(hair, 1 + rng.uniform(-0.08, 0.08))
            continue
        rect(px, x0, y0, ww, min(n, hh), hair, rng)
        if style == 'long' and side in ('right', 'left'):
            rect(px, x0 + (0 if side == 'right' else ww - 3) if False else x0, y0, ww, 7, hair, rng)
    # face (front at 8..16, 8..16)
    fx, fy = 8, 8
    px[fx + 1, fy + 4] = (235, 230, 240, 255); px[fx + 2, fy + 4] = (150, 40, 210, 255)
    px[fx + 5, fy + 4] = (150, 40, 210, 255); px[fx + 6, fy + 4] = (235, 230, 240, 255)
    for x in (1, 2, 5, 6):
        px[fx + x, fy + 5] = shade(pale, 0.72)          # dark rings under the eyes
    px[fx + 1, fy + 3] = shade(hair, 0.9); px[fx + 2, fy + 3] = shade(hair, 0.9)
    px[fx + 5, fy + 3] = shade(hair, 0.9); px[fx + 6, fy + 3] = shade(hair, 0.9)
    for x in range(3, 5): px[fx + x, fy + 6] = shade(pale, 0.85)   # nose shadow
    for x in range(2, 6): px[fx + x, fy + 7] = (90, 40, 60, 255)   # grim mouth
    # body (16,16) 8x12x4
    box(px, 16, 16, 8, 12, 4, shirt, rng=rng)
    b = faces(16, 16, 8, 12, 4)
    x0, y0, ww, hh = b['front']
    if extra == 'collar':
        for x in range(2, 6): px[x0 + x, y0] = shade(pale, 1)
        px[x0 + 3, y0 + 1] = shade(pale, 1); px[x0 + 4, y0 + 1] = shade(pale, 1)
        for y in range(2, 12): px[x0 + 4, y0 + y] = shade(shirt, 0.75)
    elif extra == 'stripe':
        for x in range(ww): px[x0 + x, y0 + 5] = shade(shirt, 0.6); px[x0 + x, y0 + 6] = shade(shirt, 0.6)
    elif extra == 'hoodie':
        for x in range(1, 7): px[x0 + x, y0] = shade(shirt, 0.7)
        for y in range(7, 10):
            for x in range(1, 7): px[x0 + x, y0 + y] = shade(shirt, 0.85)
        px[x0 + 3, y0 + 1] = (220, 220, 220, 255); px[x0 + 4, y0 + 1] = (220, 220, 220, 255)
        px[x0 + 3, y0 + 2] = (220, 220, 220, 255); px[x0 + 4, y0 + 2] = (220, 220, 220, 255)
    for side in ('front', 'back', 'right', 'left'):
        x0, y0, ww, hh = b[side]
        rect(px, x0, y0 + 11, ww, 1, shade(pants, 0.7), rng)    # belt line
    # right arm (40,16) 4x12x4: sleeve then skin (hoodies have long sleeves)
    box(px, 40, 16, 4, 12, 4, pale, rng=rng)
    a = faces(40, 16, 4, 12, 4)
    sleeve = 11 if extra == 'hoodie' else 5
    for side in ('front', 'back', 'right', 'left'):
        x0, y0, ww, hh = a[side]
        rect(px, x0, y0, ww, sleeve, shirt, rng)
    x0, y0, ww, hh = a['top']; rect(px, x0, y0, ww, hh, shirt, rng)
    # right leg (0,16): pants, shoes
    box(px, 0, 16, 4, 12, 4, pants, rng=rng)
    l = faces(0, 16, 4, 12, 4)
    for side in ('front', 'back', 'right', 'left'):
        x0, y0, ww, hh = l[side]
        rect(px, x0, y0 + 10, ww, 2, shoes, rng)
    rect(px, 8, 16, 4, 4, shoes, rng)    # sole
    # corruption: a few purple veins creeping over the skin
    placed = 0
    for _ in range(200):
        if placed >= 5: break
        x, y = rng.randrange(0, 32), rng.randrange(12, 16)
        c = px[x, y]
        if c[3] and sum(abs(c[k] - pale[k]) for k in range(3)) < 45 and not (8 <= x < 16 and y in (12, 13)):
            px[x, y] = (120, 50, 160, 255); placed += 1
    # left arm/leg (the player model has its own UVs for them): mirror the right ones like vanilla
    for (x, y, dx, dy, w, h) in [(4,16,16,32,4,4),(8,16,16,32,4,4),(0,20,24,32,4,12),(4,20,16,32,4,12),(8,20,8,32,4,12),
                                 (12,20,16,32,4,12),(44,16,-8,32,4,4),(48,16,-8,32,4,4),(40,20,0,32,4,12),(44,20,-8,32,4,12),
                                 (48,20,-16,32,4,12),(52,20,-8,32,4,12)]:
        img.paste(img.crop((x, y, x + w, y + h)).transpose(Image.FLIP_LEFT_RIGHT), (x + dx, y + dy))
    img.save(f'{OUT}/possessed_{i}.png')

# glowing eyes for all of them
eyes = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); e = eyes.load()
e[10, 12] = (220, 90, 255, 255); e[13, 12] = (220, 90, 255, 255)
e[9, 12] = (120, 40, 160, 255); e[14, 12] = (120, 40, 160, 255)
eyes.save(f'{OUT}/possessed_eyes.png')
print(len(PEOPLE))
