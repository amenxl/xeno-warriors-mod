"""Renders the Government facility layout (same plan as GovFacilityFeature) as isometric voxels: outside, and cut open."""
from PIL import Image, ImageDraw
HX, HZ, H = 11, 8, 7
COL = {'wall': (196, 198, 200), 'trim': (110, 112, 118), 'inner': (110, 112, 118), 'dark': (24, 26, 32), 'floor': (70, 72, 80), 'lamp': (200, 235, 230),
       'glass': (120, 180, 230), 'band': (236, 238, 240), 'arch': (40, 150, 160), 'roof': (170, 170, 170), 'quartz': (232, 228, 222), 'barrel': (140, 100, 60),
       'iron': (210, 212, 216), 'chest': (160, 110, 50), 'black': (40, 40, 46), 'rlamp': (240, 180, 90), 'pod': (238, 242, 248), 'podg': (90, 235, 140), 'det': (200, 190, 160)}
B = {}
def s(x, y, z, k): B[(x, y, z)] = k
def a(x, y, z): B.pop((x, y, z), None)
for dx in range(-HX, HX + 1):
    for dz in range(-HZ, HZ + 1):
        s(dx, -1, dz, 'floor')
        edge = abs(dx) == HX or abs(dz) == HZ
        for y in range(H):
            if edge:
                k = 'trim' if y == H - 1 else ('glass' if y == 3 and (((dx + dz) % 2 == 0 and abs(dx) < HX - 1) or abs(dz) < HZ - 1) else 'trim' if y == 1 else 'wall')
                s(dx, y, dz, k)
        s(dx, H, dz, 'trim' if edge else 'roof')
for dx in (-1, 0, 1):
    for y in range(3): a(dx, y, HZ)
for y in range(4): s(-2, y, HZ, 'dark'); s(2, y, HZ, 'dark')
for dx in range(-2, 3): s(dx, 3, HZ, 'dark')
s(0, 3, HZ, 'lamp')
for dz in range(1, HZ):
    for sx in (-2, 2):
        for y in range(4): s(sx, y, dz, 'band' if y == 2 else 'inner')
    for dx in (-1, 0, 1): s(dx, 4, dz, 'dark'); s(dx, -1, dz, 'lamp' if dx == 0 and dz % 2 == 0 else 'dark')
    s(0, 4, dz, 'lamp' if dz % 2 == 1 else 'dark')
for dx in range(-HX + 1, -2):
    for dz in range(1, HZ): s(dx, 4, dz, 'dark')
a(-2, 0, 4); a(-2, 1, 4)
for dx in range(-HX + 1, -3, 2): s(dx, 0, HZ - 1, 'barrel'); s(dx, 1, HZ - 1, 'iron')
for i in range(3): s(-HX + 2 + i * 3, 0, 1, 'chest')
for dx in range(3, HX):
    for dz in range(1, HZ): s(dx, 4, dz, 'dark')
a(2, 0, 4); a(2, 1, 4)
for dz in range(2, 6): s(HX - 1, 0, dz, 'black'); s(HX - 1, 1, dz, 'rlamp' if dz % 2 == 0 else 'glass')
s(4, 0, HZ - 1, 'chest')
for dx in range(-HX + 1, HX):
    for dz in range(-HZ + 1, 1):
        for y in range(H): a(dx, y, dz)
        s(dx, -1, dz, 'lamp' if abs(dz + 4) == 3 and dx % 2 == 0 else 'quartz')
for dx in range(-HX, HX + 1):
    ax = abs(dx); y = H + (3 if ax < 3 else 2 if ax < 6 else 1 if ax < 9 else 0)
    for dz in range(-HZ, 1):
        s(dx, y, dz, 'arch' if dz % 3 == 0 else 'glass')
        for yy in range(H, y):
            if abs(dz) == HZ or dz == 0: s(dx, yy, dz, 'arch')
for dz in range(-HZ + 1, 1, 3):
    for sx in (-HX + 1, HX - 1):
        for y in range(H): s(sx, y, dz, 'arch')
for dx in (-1, 0, 1):
    for y in (0, 1): a(dx, y, 0)
for dx in range(-8, 9, 4):
    s(dx, 0, -HZ + 2, 'pod'); s(dx, 1, -HZ + 2, 'podg'); s(dx, 0, -HZ + 4, 'iron'); s(dx, 1, -HZ + 4, 'det')
for dx in range(-3, 4): s(dx, 0, -2, 'roof')
s(0, 1, -2, 'chest')

def render(cut=False, yaw_flip=False, size=16):
    blocks = {k: v for k, v in B.items() if not (cut and (k[1] >= 4 or (k[2] == HZ and k[1] >= 0) or (k[0] == HX and k[1] >= 0)))}
    if cut:      # roofless and with the front walls cut low, to see in
        blocks = {k: v for k, v in blocks.items() if not (k[1] >= H)}
    W, Hh = 1100, 760
    img = Image.new('RGB', (W, Hh), (150, 195, 235)); d = ImageDraw.Draw(img)
    d.rectangle((0, Hh * 0.62, W, Hh), fill=(96, 150, 66))
    cx, cy = W // 2, int(Hh * 0.55)
    def iso(x, y, z):
        if yaw_flip: x, z = -x, -z
        return (cx + (x - z) * size * 0.87, cy + (x + z) * size * 0.5 - y * size)
    order = sorted(blocks, key=lambda k: ((k[0] + k[2]) * (-1 if yaw_flip else 1), k[1]))
    for (x, y, z) in order:
        c = COL[blocks[(x, y, z)]]
        if yaw_flip: pass
        top = [iso(x, y + 1, z), iso(x + 1, y + 1, z), iso(x + 1, y + 1, z + 1), iso(x, y + 1, z + 1)]
        left = [iso(x, y + 1, z + 1), iso(x + 1, y + 1, z + 1), iso(x + 1, y, z + 1), iso(x, y, z + 1)]
        right = [iso(x + 1, y + 1, z), iso(x + 1, y + 1, z + 1), iso(x + 1, y, z + 1), iso(x + 1, y, z)]
        d.polygon(top, fill=c, outline=tuple(max(0, v - 30) for v in c))
        d.polygon(left, fill=tuple(int(v * .78) for v in c), outline=tuple(max(0, v - 40) for v in c))
        d.polygon(right, fill=tuple(int(v * .62) for v in c), outline=tuple(max(0, v - 50) for v in c))
    return img
render().save('/tmp/fac_out.png'); render(cut=True).save('/tmp/fac_cut.png')
a_ = Image.open('/tmp/fac_out.png'); b_ = Image.open('/tmp/fac_cut.png')
sheet = Image.new('RGB', (1100, 1520)); sheet.paste(a_, (0, 0)); sheet.paste(b_, (0, 760))
ImageDraw.Draw(sheet).text((12, 10), 'Government facility - outside', fill=(20, 20, 30)); ImageDraw.Draw(sheet).text((12, 770), 'Cut open: storage (left), corridor with floor lights, security office (right), blue-arched lab with healing pods (back)', fill=(20, 20, 30))
sheet.save('/mnt/user-data/outputs/gov_facility.png'); print('ok')
