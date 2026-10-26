"""Cel-shade test: flattens texture colors into clean bands, adds a hard shadow band low on side faces, a bright top,
and dark ink lines around every face of every cube (skin + 3D detail atlas). Doesn't touch faces."""
import json, math, sys
from PIL import Image

def lum(c): return c[0] * .3 + c[1] * .59 + c[2] * .11

def box_faces(u, v, w, h, d):
    w, h, d = max(1, math.floor(w + .001)), max(1, math.floor(h + .001)), max(1, math.floor(d + .001))
    return {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}

def quantize(im, n=64):
    rgb = im.convert('RGB').quantize(colors=n, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert('RGB')
    out = im.copy(); po, pr = out.load(), rgb.load()
    for y in range(im.height):
        for x in range(im.width):
            if po[x, y][3]: po[x, y] = pr[x, y] + (po[x, y][3],)
    return out

def shade_face(px, rect, kind, W, H, done):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if not (0 <= x < W and 0 <= y < H) or (x, y) in done: continue
            r, g, b, a = px[x, y]
            if not a: continue
            done.add((x, y))
            edge = (x == x0 or x == x0 + w - 1 or y == y0 or y == y0 + h - 1) and w >= 3 and h >= 3
            f = 1.0
            if kind == 'up': f = 1.12
            elif kind == 'down': f = 0.7
            elif h >= 4 and (y - y0) >= h * 0.72: f = 0.78                 # hard shadow band low on the sides
            if edge: f = min(f, 0.42)                                     # ink line
            px[x, y] = (min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f)), a)

# standard 64x64 player skin boxes (u, v, w, h, d) - base + overlay layers
SKIN_BOXES = [(0, 0, 8, 8, 8), (32, 0, 8, 8, 8), (16, 16, 8, 12, 4), (16, 32, 8, 12, 4), (40, 16, 4, 12, 4), (40, 32, 4, 12, 4),
              (32, 48, 4, 12, 4), (48, 48, 4, 12, 4), (0, 16, 4, 12, 4), (0, 32, 4, 12, 4), (16, 48, 4, 12, 4), (0, 48, 4, 12, 4)]

def cel_skin(src, dst):
    im = quantize(Image.open(src).convert('RGBA')); px = im.load(); done = set()
    for i, (u, v, w, h, d) in enumerate(SKIN_BOXES):
        for k, r in box_faces(u, v, w, h, d).items():
            if i in (0, 1) and k == 'front': continue                     # leave the face area alone
            shade_face(px, r, k, im.width, im.height, done)
    im.save(dst)

def cubes(node):
    for c in node.get('cubes', []): yield c
    for ch in node.get('children', []): yield from cubes(ch)

def cel_details(model_path, src, dst):
    mj = json.load(open(model_path))
    im = quantize(Image.open(src).convert('RGBA')); px = im.load(); done = set()
    for b in mj['bones'].values():
        for c in cubes(b):
            u, v = int(c['uv'][0]), int(c['uv'][1]); w, h, d = c['size']
            for k, r in box_faces(u, v, w, h, d).items(): shade_face(px, r, k, im.width, im.height, done)
    im.save(dst)

if __name__ == '__main__':
    pass
