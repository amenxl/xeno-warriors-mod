import sys, math, numpy as np
sys.path.insert(0, '/home/claude/xw/recarnet')
from mca import World
from PIL import Image, ImageDraw
Y0, Y1 = 40, 176
COL = {
 'air': None, 'cave_air': None, 'void_air': None,
 'grass_block': (96, 150, 60), 'dirt': (130, 96, 64), 'stone': (125, 125, 125), 'gravel': (140, 132, 128), 'water': (50, 90, 200),
 'gray_concrete': (60, 64, 70), 'light_gray_concrete': (150, 150, 145), 'white_concrete': (225, 228, 230), 'black_concrete': (20, 22, 26),
 'yellow_concrete': (240, 180, 30), 'cyan_concrete': (20, 130, 140), 'green_concrete': (70, 110, 30), 'lime_concrete': (110, 180, 40),
 'smooth_stone': (165, 165, 165), 'smooth_stone_slab': (165, 165, 165), 'polished_andesite': (135, 138, 136),
 'light_blue_stained_glass': (110, 170, 230), 'blue_stained_glass': (55, 80, 190), 'glass_pane': (190, 220, 235), 'glass': (200, 225, 240),
 'sea_lantern': (210, 235, 230), 'iron_bars': (110, 112, 115), 'observer': (90, 90, 90), 'redstone_lamp': (230, 170, 90),
 'smooth_sandstone': (222, 210, 160), 'cut_sandstone': (215, 200, 150), 'sandstone_slab': (215, 200, 150), 'birch_planks': (200, 180, 120),
 'white_terracotta': (210, 178, 160), 'terracotta': (152, 94, 68), 'brown_terracotta': (78, 52, 36), 'cyan_terracotta': (86, 91, 91),
 'stripped_birch_wood': (196, 176, 120), 'oak_planks': (162, 130, 78), 'glowstone': (250, 210, 120), 'iron_block': (220, 220, 220),
 'spruce_slab': (110, 80, 48), 'spruce_fence': (110, 80, 48), 'spruce_stairs': (110, 80, 48),
 'oak_log': (100, 78, 46), 'oak_leaves': (60, 120, 40), 'poppy': (200, 30, 30), 'cornflower': (70, 90, 200), 'grass': (100, 160, 60),
 'smooth_quartz': (236, 230, 224), 'quartz_pillar': (236, 230, 224), 'quartz_slab': (236, 230, 224), 'flowering_azalea_leaves': (110, 140, 70),
 'blue_carpet': (50, 60, 160), 'polished_deepslate': (70, 70, 74), 'sand': (220, 210, 160), 'snow': (250, 250, 250),
 'deepslate_bricks': (70, 70, 74), 'cracked_deepslate_bricks': (64, 64, 68), 'polished_blackstone': (50, 46, 54), 'dark_oak_planks': (66, 44, 22),
}
def color(name):
    if name is None: return None
    n = name.split(':')[1]
    if n in COL: return COL[n]
    if 'leaves' in n: return (60, 120, 40)
    if 'log' in n or 'wood' in n: return (100, 78, 46)
    if 'concrete' in n: return (150, 150, 150)
    if 'glass' in n: return (180, 210, 230)
    if 'water' in n: return (50, 90, 200)
    if 'grass' in n or 'fern' in n or 'flower' in n or 'tulip' in n or 'dandelion' in n: return None
    if 'slab' in n or 'stairs' in n or 'wall' in n: return (150, 150, 150)
    return (140, 140, 140)

def _indices(pal, data):
    if data is None or len(pal) == 1: return np.zeros(4096, np.int64)
    bits = max(4, math.ceil(math.log2(len(pal)))); per = 64 // bits
    arr = np.array(data, dtype=np.int64).view(np.uint64)
    idx = np.arange(4096); word = arr[idx // per]; shift = ((idx % per) * bits).astype(np.uint64)
    return ((word >> shift) & np.uint64((1 << bits) - 1)).astype(np.int64)

def dense(w, cx, cz):
    """(Y1-Y0, 16, 16, 4) RGBA colors of a chunk."""
    c = w.chunk(cx, cz)
    if c is None: return None
    out = np.zeros((Y1 - Y0, 16, 16, 4), np.uint8)
    for sy in range(Y0 >> 4, (Y1 - 1 >> 4) + 1):
        sec = c.get(sy)
        if sec is None: continue
        pal, data = sec
        lut = np.zeros((len(pal), 4), np.uint8)
        for i, n in enumerate(pal):
            col = color(n)
            if col: lut[i] = (*col, 255)
        rgba = lut[_indices(pal, data)].reshape(16, 16, 16, 4)      # y, z, x
        for ly in range(16):
            y = sy * 16 + ly
            if Y0 <= y < Y1: out[y - Y0] = rgba[ly]
    return out

def topdown(w, r, out):
    n = (2 * r + 1) * 16
    img = np.zeros((n, n, 3), np.uint8); img[:] = (30, 34, 40)
    for cx in range(-r, r + 1):
        for cz in range(-r, r + 1):
            d = dense(w, cx, cz)
            if d is None: continue
            alpha = d[..., 3] > 0
            has = alpha.any(0)
            top = (Y1 - Y0 - 1) - np.argmax(alpha[::-1], axis=0)
            zz, xx = np.indices((16, 16))
            cols = d[top, zz, xx, :3].astype(float)
            shade = 0.55 + 0.45 * (top / (Y1 - Y0 - 1))
            cols = (cols * shade[..., None]).clip(0, 255).astype(np.uint8)
            cols[~has] = (30, 34, 40)
            ox, oz = (cx + r) * 16, (cz + r) * 16
            img[oz:oz + 16, ox:ox + 16] = cols
    Image.fromarray(img).save(out)

def iso(w, x0, z0, size, out, scale=4, yaw=0):
    """Isometric view of blocks x0..x0+size, z0..z0+size, drawing only visible faces."""
    # gather into a dense volume
    vol = np.zeros((Y1 - Y0, size, size, 3), np.uint8); solid = np.zeros((Y1 - Y0, size, size), bool)
    for cx in range(x0 >> 4, (x0 + size - 1 >> 4) + 1):
        for cz in range(z0 >> 4, (z0 + size - 1 >> 4) + 1):
            d = dense(w, cx, cz)
            if d is None: continue
            for lx in range(16):
                for lz in range(16):
                    X, Z = cx * 16 + lx - x0, cz * 16 + lz - z0
                    if 0 <= X < size and 0 <= Z < size:
                        vol[:, Z, X] = d[:, lz, lx, :3]; solid[:, Z, X] = d[:, lz, lx, 3] > 0
    solid[:max(0, 70 - Y0)] = False
    return iso_draw(vol, solid, size, out, scale)

def iso_draw(vol, solid, size, out, scale):
    polys = []
    def P(x, y, z): return ((x - z) * scale, (x + z) * scale * 0.5 - y * scale * 1.15)
    for s_ in range(2 * size):                     # back to front
        for X in range(max(0, s_ - size + 1), min(size, s_ + 1)):
            Z = s_ - X
            col = solid[:, Z, X]
            if not col.any(): continue
            for y in np.where(col)[0]:
                up = y + 1 < solid.shape[0] and solid[y + 1, Z, X]
                fr = Z + 1 < size and solid[y, Z + 1, X]
                rt = X + 1 < size and solid[y, Z, X + 1]
                if up and fr and rt: continue
                c = vol[y, Z, X].astype(int)
                if not fr: polys.append(([P(X, y + 1, Z + 1), P(X + 1, y + 1, Z + 1), P(X + 1, y, Z + 1), P(X, y, Z + 1)], tuple((c * .78).astype(int))))
                if not rt: polys.append(([P(X + 1, y + 1, Z), P(X + 1, y + 1, Z + 1), P(X + 1, y, Z + 1), P(X + 1, y, Z)], tuple((c * .62).astype(int))))
                if not up: polys.append(([P(X, y + 1, Z), P(X + 1, y + 1, Z), P(X + 1, y + 1, Z + 1), P(X, y + 1, Z + 1)], tuple(c)))
    if not polys: return
    xs = [p[0] for pl, _ in polys for p in pl]; ys = [p[1] for pl, _ in polys for p in pl]
    ox, oy = -min(xs) + 10, -min(ys) + 10
    im = Image.new('RGB', (int(max(xs) - min(xs)) + 20, int(max(ys) - min(ys)) + 20), (26, 30, 38)); dr = ImageDraw.Draw(im)
    for pl, c in polys: dr.polygon([(x + ox, y + oy) for x, y in pl], fill=c)
    im.save(out)
