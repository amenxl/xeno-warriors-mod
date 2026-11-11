"""Textures for Mikiko's Shiyoken fox aura (client/mikiko/FoxAuraClient).

  fox_spirit.png  4 frames x 64x64  a running flame fox (side view, facing right), flickering
  fox_head.png    2 frames x 128x128 a huge flame fox head (front), for the second level
  void_orb.png    32x32              a black orb with a glowing rim (second level)
  fox_ribbon.png  64x16              a flame streak / ribbon
  fox_trim1.png / fox_trim2.png      64x64 skin-layout glowing lines over the outfit (level 1 / level 2)

Run: python3 tools/xw/aura/fox_aura.py  (writes into src/main/resources/assets/xenowarriors/textures/entity/aura)
"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), '../../../src/main/resources/assets/xenowarriors/textures/entity/aura')
random.seed(7)
np.random.seed(7)


def fire_colour(v):
    """v 0..1 heat -> RGBA (dark red edge, orange, yellow, white-hot core)."""
    v = np.clip(v, 0, 1)
    r = np.clip(v * 3.0, 0, 1)
    g = np.clip(v * 2.2 - 0.55, 0, 1) * 0.92
    b = np.clip(v * 3.0 - 2.15, 0, 1) * 0.85
    a = np.clip(v * 1.8, 0, 1)
    return np.stack([r, g, b, a], -1)


def noise(w, h, scale, seed):
    rng = np.random.RandomState(seed)
    small = rng.rand(max(2, h // scale + 2), max(2, w // scale + 2))
    img = Image.fromarray((small * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.asarray(img).astype(np.float32) / 255.0


def flame_warp(mask, frame, strength=6, up=True):
    """Pushes a soft mask into licking flames: rows shifted by noise, heat rising up."""
    h, w = mask.shape
    n1 = noise(w, h, 6, 100 + frame * 13)
    n2 = noise(w, h, 3, 200 + frame * 29)
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    dx = (n1 - 0.5) * strength * 2
    dy = (n2 - 0.2) * strength * (1.6 if up else 0.6)
    sx = np.clip(xs + dx, 0, w - 1).astype(int)
    sy = np.clip(ys + dy, 0, h - 1).astype(int)
    out = mask[sy, sx]
    flick = noise(w, h, 4, 300 + frame * 7)
    return np.clip(out * (0.75 + 0.5 * flick), 0, 1)


def to_img(heat):
    return Image.fromarray((fire_colour(heat) * 255).astype(np.uint8), 'RGBA')


def soft(draw_fn, size, blur, ss=4):
    big = Image.new('L', (size[0] * ss, size[1] * ss), 0)
    draw_fn(ImageDraw.Draw(big), ss)
    big = big.filter(ImageFilter.GaussianBlur(blur * ss))
    return np.asarray(big.resize(size, Image.LANCZOS)).astype(np.float32) / 255.0


def fox_spirit():
    W = 64
    sheet = Image.new('RGBA', (W * 4, W), (0, 0, 0, 0))
    for f in range(4):
        leg = math.sin(f * math.pi / 2)

        def shape(d, s, leg=leg):
            S = lambda pts: [(x * s, y * s) for x, y in pts]
            d.ellipse(S([(18, 26), (44, 40)]), fill=200)                       # body
            d.ellipse(S([(38, 18), (54, 32)]), fill=230)                       # head
            d.polygon(S([(52, 22), (61, 27), (52, 29)]), fill=230)             # snout
            d.polygon(S([(41, 21), (42, 9), (47, 19)]), fill=240)              # ears
            d.polygon(S([(46, 20), (50, 9), (52, 21)]), fill=240)
            # legs, swinging
            for x0, ph in ((22, 1), (27, -1), (36, -1), (41, 1)):
                k = leg * ph * 4
                d.polygon(S([(x0, 36), (x0 + 4, 36), (x0 + 2 + k, 48), (x0 - 1 + k, 48)]), fill=170)
            # the big flame tail curling up behind
            d.polygon(S([(20, 30), (6, 24), (2, 12), (8, 4), (10, 14), (16, 22), (24, 28)]), fill=210)
        m = soft(shape, (W, W), 0.9)
        heat = flame_warp(m, f, strength=3)
        # a white-hot core along the body and head
        core = soft(lambda d, s: (d.ellipse([(24 * s, 28 * s), (42 * s, 36 * s)], fill=255),
                                  d.ellipse([(42 * s, 22 * s), (51 * s, 29 * s)], fill=255)), (W, W), 2.0)
        heat = np.clip(heat * 0.62 + core * 0.3, 0, 1)
        # glowing eye
        eye = soft(lambda d, s: d.ellipse([(47 * s, 23 * s), (50 * s, 25 * s)], fill=255), (W, W), 0.3)
        img = to_img(heat)
        arr = np.asarray(img).astype(np.float32)
        arr[..., :3] = np.clip(arr[..., :3] + eye[..., None] * 255, 0, 255)
        sheet.paste(Image.fromarray(arr.astype(np.uint8), 'RGBA'), (f * W, 0))
    sheet.save(os.path.join(OUT, 'fox_spirit.png'))


def fox_head():
    W = 128
    sheet = Image.new('RGBA', (W * 2, W), (0, 0, 0, 0))
    for f in range(2):
        def shape(d, s):
            S = lambda pts: [(x * s, y * s) for x, y in pts]
            d.polygon(S([(64, 112), (24, 64), (22, 36), (40, 50), (64, 44), (88, 50), (106, 36), (104, 64)]), fill=210)
            d.polygon(S([(24, 52), (16, 6), (46, 44)]), fill=235)              # ears
            d.polygon(S([(104, 52), (112, 6), (82, 44)]), fill=235)
            d.polygon(S([(40, 56), (64, 40), (88, 56), (64, 104)]), fill=150)  # face
        m = soft(shape, (W, W), 1.6)
        heat = flame_warp(m, f + 5, strength=6)
        eyes = soft(lambda d, s: (d.polygon([(42 * s, 62 * s), (58 * s, 70 * s), (44 * s, 70 * s)], fill=255),
                                  d.polygon([(86 * s, 62 * s), (70 * s, 70 * s), (84 * s, 70 * s)], fill=255)), (W, W), 0.6)
        heat = np.clip(heat * 0.66 + eyes, 0, 1)
        sheet.paste(to_img(heat), (f * W, 0))
    sheet.save(os.path.join(OUT, 'fox_head.png'))


def void_orb():
    W = 32
    ys, xs = np.mgrid[0:W, 0:W].astype(np.float32)
    r = np.hypot(xs - 15.5, ys - 15.5) / 15.5
    rim = np.exp(-((r - 0.78) / 0.09) ** 2)
    arr = np.zeros((W, W, 4), np.float32)
    inside = r < 0.8
    arr[..., 3] = np.where(inside, 0.93, 0) + rim * 0.9
    arr[..., 0] = rim * 1.0
    arr[..., 1] = rim * 0.55
    arr[..., 2] = rim * 0.12
    arr[..., 3] = np.clip(arr[..., 3], 0, 1) * (r < 1.0)
    Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8), 'RGBA').save(os.path.join(OUT, 'void_orb.png'))


def ribbon():
    W, H = 64, 16
    ys, xs = np.mgrid[0:H, 0:W].astype(np.float32)
    along = xs / (W - 1)
    width = 0.25 + 0.75 * np.sin(along * math.pi)
    d = np.abs(ys - 7.5) / 7.5
    heat = np.clip(1 - d / np.maximum(width, 0.05), 0, 1) ** 1.3
    heat *= 0.6 + 0.4 * noise(W, H, 4, 77)
    to_img(heat).save(os.path.join(OUT, 'fox_ribbon.png'))


# skin layout boxes for the body, arms and legs (x, y, w, h, d) with the outer layers too
BOXES = [(16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (32, 48, 4, 12, 4), (0, 16, 4, 12, 4), (16, 48, 4, 12, 4),
         (16, 32, 8, 12, 4), (40, 32, 4, 12, 4), (48, 48, 4, 12, 4), (0, 32, 4, 12, 4), (0, 48, 4, 12, 4)]
SPIRAL = ["..####..",
          ".#....#.",
          "#..##..#",
          "#.#..#.#",
          "#.#.##.#",
          "#..#...#",
          ".#....#.",
          "..####.."]


def trim(level):
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = img.load()
    hot, warm = (255, 214, 120, 255), (255, 110, 30, 220)
    for (x, y, w, h, d) in BOXES:
        sides = [(x, d), (x + d, w), (x + d + w, d), (x + d + w + d, w)]
        top = y + d
        for sx, sw in sides:
            for i in range(sw):
                px[sx + i, top + h - 1] = hot                       # cuffs / hem
                if level > 1: px[sx + i, top + h - 3] = warm
            # flame lines licking up each side
            for i in range(0, sw, 2 if level > 1 else 3):
                n = 3 + (i * 7 + x) % (5 if level == 1 else 9)
                for k in range(n):
                    xx = sx + i + (1 if (k // 2) % 2 and i + 1 < sw else 0)
                    px[xx, top + h - 2 - k] = warm if k > 1 else hot
            if level > 1:
                for i in range(sw):
                    px[sx + i, top] = warm                          # shoulders / waistband
    # the spiral on the jacket front (body and its outer layer)
    for bx, by in ((20, 22), (20, 38)):
        for j, row in enumerate(SPIRAL):
            for i, c in enumerate(row):
                if c == '#': px[bx + i, by + j] = hot
    img.save(os.path.join(OUT, 'fox_trim%d.png' % level))


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    fox_spirit(); fox_head(); void_orb(); ribbon(); trim(1); trim(2)
    print('ok')
