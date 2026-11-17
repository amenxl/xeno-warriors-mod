"""Super Saiyan 4 fur (our own, so it doesn't depend on DragonMineZ's texture names): a 64x64 skin-layout decal in light
greys (tinted red in game, CharacterDetailLayer.FUR) - the back, sides and shoulders of the body and the arms down to the
wrists furred, the chest and stomach bare (with a ragged edge), hands bare.
Run: python3 ssj4_fur.py -> textures/entity/character/ssj4_fur.png"""
import random
from PIL import Image
OUT = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/ssj4_fur.png'
random.seed(4)
im = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); px = im.load()

def fur(x, y):
    v = 214 + random.randint(-14, 14) + (10 if (x + (y // 3)) % 2 == 0 else -6)   # (strands: light and dark streaks)
    v = max(150, min(255, v))
    px[x, y] = (v, v, v, 255)

def box(x0, y0, x1, y1):
    for y in range(y0, y1):
        for x in range(x0, x1): fur(x, y)

# body: top, sides, back fully; front only round the edges (bare chest and stomach)
box(20, 16, 36, 20)                      # top / bottom of the torso
box(16, 20, 20, 32); box(28, 20, 32, 32) # sides
box(32, 20, 40, 32)                      # back
for y in range(20, 32):                  # front: a collar across the top, the sides down, a ragged edge into the bare chest
    edge = 2 if y < 23 else 1
    for x in range(20, 28):
        inner = min(x - 20, 27 - x)
        if y == 20 or inner < edge or (inner == edge and random.random() < 0.45): fur(x, y)
# arms (right: 40-56 x 16-32; left: 32-48 x 48-64): all round, down to the wrist - the last row bare and ragged
for (ax, ay) in ((40, 16), (32, 48)):
    box(ax + 4, ay, ax + 12, ay + 4)     # top of the shoulder (the bottom face of the hand left bare below)
    for x in range(ax, ax + 16):
        for y in range(ay + 4, ay + 16):
            if y == ay + 15: continue
            if y == ay + 14 and random.random() < 0.5: continue
            fur(x, y)
    for x in range(ax + 8, ax + 12):     # (the hand's underside face: bare)
        for y in range(ay, ay + 4): px[x, y] = (0, 0, 0, 0)
im.save(OUT)
print('saved', OUT)
