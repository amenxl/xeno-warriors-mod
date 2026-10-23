"""Battle-worn and torn versions of each character's armor/hoodie atlas (used only for the hoodies)."""
from PIL import Image
import random, sys
T = sys.argv[1]
for char in ('mikiko', 'rhalix'):
    base = Image.open(f'{T}/{char}_armor.png').convert('RGBA')
    W, H = base.size
    for tag, dirt, holes, hole_size, slashes in (('worn', 0.05, 0.006, 1, 3), ('torn', 0.10, 0.018, 2, 14)):
        rng = random.Random(hash((char, tag)) & 0xFFFF)
        im = base.copy(); px = im.load()
        opaque = [(x, y) for x in range(W) for y in range(H) if px[x, y][3] > 0]
        n = len(opaque)
        for _ in range(int(n * dirt)):                       # scuffs and grime
            x, y = rng.choice(opaque)
            r, g, b, a = px[x, y]
            f = rng.uniform(0.45, 0.75)
            px[x, y] = (int(r * f), int(g * f), int(b * f), a)
        for _ in range(int(n * 0.01)):                       # frayed lighter threads
            x, y = rng.choice(opaque)
            r, g, b, a = px[x, y]
            px[x, y] = (min(255, r + 40), min(255, g + 40), min(255, b + 40), a)
        for _ in range(int(n * holes)):                      # holes right through
            x, y = rng.choice(opaque)
            for dx in range(hole_size):
                for dy in range(hole_size):
                    if 0 <= x + dx < W and 0 <= y + dy < H and rng.random() < 0.8:
                        px[x + dx, y + dy] = (0, 0, 0, 0)
        for _ in range(slashes):                             # ragged rips
            x, y = rng.choice(opaque)
            length = rng.randint(3, 6)
            for k in range(length):
                xx, yy = x + k, y + (k // 2) * rng.choice((0, 1))
                if 0 <= xx < W and 0 <= yy < H and px[xx, yy][3] > 0:
                    px[xx, yy] = (0, 0, 0, 0)
                    if 0 <= yy + 1 < H and px[xx, yy + 1][3] > 0:
                        r, g, b, a = px[xx, yy + 1]
                        px[xx, yy + 1] = (int(r * 0.5), int(g * 0.5), int(b * 0.5), a)
        im.save(f'{T}/{char}_armor_{tag}.png')
        print(char, tag, W, H)
