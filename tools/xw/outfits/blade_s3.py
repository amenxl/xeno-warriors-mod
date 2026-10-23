"""Detailed Recarnet Season 3 Hekublade + animated galaxy globe."""
import json, math, random
from PIL import Image
RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
def C(h): h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

PAL = {'grey': '#b8b8bc', 'greyM': '#a2a2a8', 'greyD': '#86868e', 'hi': '#e8e8ec', 'rim': '#5e5e66', 'red': '#c42a3e', 'redL': '#e8607a',
       'redD': '#8a1626', 'black': '#1e1c20', 'blackL': '#38363c', 'silver': '#c8ccd2', 'silverD': '#8e9298', 'blue': '#2a56d8',
       'blueD': '#1a3696', 'blueL': '#5a86ff', 'rope': '#c6b08a', 'ropeD': '#9a845e'}
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); POS = {}
for i, (k, h) in enumerate(PAL.items()):
    c = C(h)
    for j, f in enumerate((1.12, 1.0, 0.74)):
        p = i * 3 + j; img.putpixel((p % 16, p // 16), tuple(min(255, int(v * f)) for v in c) + (255,))
    POS[k] = i * 3
img.save(RES + 'textures/item/hekublade_s3.png')

def uv(k, s):
    p = POS[k] + s; x, y = p % 16, p // 16
    return [x + .25, y + .25, x + .75, y + .75]

blade, globe = [], []
def el(frm, to, k, name='part', target=None):
    faces = {f: {'uv': uv(k, s), 'texture': '#0'} for f, s in (('north', 1), ('south', 1), ('east', 2), ('west', 0), ('up', 0), ('down', 2))}
    (target if target is not None else blade).append({'name': name, 'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to],
        'rotation': {'angle': -45, 'axis': 'z', 'origin': [8, 8, 8]}, 'faces': faces})

def gel(frm, to):
    # galaxy: front/back show the whole swirl, the rim a slice of it
    w, h = to[0] - frm[0], to[1] - frm[1]
    def fuv(a0, a1, b0, b1): return [round(a0, 2), round(b0, 2), round(a1, 2), round(b1, 2)]
    x0 = (frm[0] - 6.6) / 2.8 * 16; x1 = (to[0] - 6.6) / 2.8 * 16
    y0 = (7.4 - to[1]) / 2.8 * 16; y1 = (7.4 - frm[1]) / 2.8 * 16
    faces = {'north': {'uv': fuv(x0, x1, y0, y1), 'texture': '#1'}, 'south': {'uv': fuv(16 - x1, 16 - x0, y0, y1), 'texture': '#1'}}
    for f in ('east', 'west', 'up', 'down'): faces[f] = {'uv': [7, 7, 9, 9], 'texture': '#1'}
    globe.append({'name': 'galaxy', 'from': frm, 'to': to, 'rotation': {'angle': -45, 'axis': 'z', 'origin': [8, 8, 8]}, 'faces': faces})

# ---- pommel: blue fin, ring, twisted rope with a ring on the end
el([7.0, -2.4, 7.3], [9.0, -0.6, 8.7], 'blue', 'fin')
el([7.3, -2.2, 7.25], [8.0, -0.8, 8.75], 'blueL', 'fin shine')
el([6.0, -2.0, 7.45], [7.0, -1.3, 8.55], 'blue', 'fin spike'); el([5.4, -1.85, 7.55], [6.0, -1.5, 8.45], 'blueD', 'fin spike')
el([9.0, -1.4, 7.45], [9.9, -0.8, 8.55], 'blue', 'fin spike'); el([9.9, -1.25, 7.55], [10.5, -0.95, 8.45], 'blueD', 'fin spike')
el([7.55, -3.0, 7.45], [8.45, -2.4, 8.55], 'blueD', 'fin point')
for a, b in (([7.3, -4.3], [8.7, -4.0]), ([7.3, -3.25], [8.7, -2.95]), ([7.3, -4.3], [7.6, -2.95]), ([8.4, -4.3], [8.7, -2.95])):
    el([a[0], a[1], 7.75], [b[0], b[1], 8.25], 'silver', 'ring')
# (the rope stays wound inside the ring until he lets it out - drawn by the mod while it's extended)
# ---- grip: black, red wrap spiralling up it
el([7.35, -0.6, 7.35], [8.65, 4.0, 8.65], 'black', 'grip')
for i in range(6):
    yy = -0.35 + 0.72 * i
    xs = (7.25, 8.05) if i % 2 == 0 else (7.95, 8.75)
    el([xs[0], yy, 7.25], [xs[1], yy + 0.38, 8.75], 'red', 'wrap')
    el([7.25, yy + 0.3, 7.25], [8.75, yy + 0.42, 8.75], 'redD', 'wrap edge')
el([7.15, 3.8, 7.15], [8.85, 4.35, 8.85], 'blackL', 'collar')
# ---- guard: black disc with spikes, silver and red rings, the galaxy globe
for a, b in (([5.6, 3.4], [10.4, 8.6]), ([4.8, 4.2], [11.2, 7.8]), ([5.1, 3.8], [10.9, 8.2])):
    el([a[0], a[1], 7.0], [b[0], b[1], 9.0], 'black', 'disc')
for a, b in (([6.0, 4.0], [10.0, 8.0]), ([6.35, 3.7], [9.65, 8.3]), ([5.7, 4.35], [10.3, 7.65])):
    el([a[0], a[1], 6.9], [b[0], b[1], 9.1], 'silver', 'silver ring')
el([6.15, 4.15, 6.88], [6.45, 7.85, 9.12], 'silverD', 'ring shade')
for a, b in (([6.4, 4.4], [9.6, 7.6]), ([6.7, 4.15], [9.3, 7.85]), ([6.15, 4.7], [9.85, 7.3])):
    el([a[0], a[1], 6.85], [b[0], b[1], 9.15], 'red', 'red ring')
el([6.7, 7.2, 6.83], [9.3, 7.45, 9.17], 'redL', 'red ring shine')
gel([6.9, 4.9, 6.8], [9.1, 7.1, 9.2]); gel([7.2, 4.65, 6.79], [8.8, 7.35, 9.21]); gel([6.65, 5.2, 6.79], [9.35, 6.8, 9.21])
el([9.4, 8.0, 7.25], [10.4, 10.6, 8.75], 'black', 'spike'); el([10.4, 8.2, 7.3], [10.7, 10.2, 8.7], 'silver', 'spike edge')
el([9.7, 10.6, 7.35], [10.3, 11.3, 8.65], 'black', 'spike tip')
el([10.7, 7.4, 7.3], [11.6, 9.6, 8.7], 'silver', 'spike'); el([11.0, 9.6, 7.4], [11.5, 10.3, 8.6], 'silverD', 'spike tip')
el([8.6, 8.4, 7.35], [9.4, 9.9, 8.65], 'black', 'spike')
el([3.9, 3.0, 7.3], [5.0, 4.0, 8.7], 'black', 'spike'); el([3.3, 2.7, 7.4], [4.0, 3.3, 8.6], 'blackL', 'spike tip')
el([4.3, 2.4, 7.35], [5.4, 3.0, 8.65], 'silver', 'spike')
el([4.1, 4.6, 7.2], [4.9, 6.8, 8.8], 'silver', 'plate')
el([5.9, 8.0, 7.4], [7.0, 9.3, 8.6], 'silver', 'claw'); el([5.6, 8.9, 7.45], [6.2, 9.8, 8.55], 'silverD', 'claw tip')
# ---- blade: three widening segments, hooked spike on the back of each, red bands
segs = [(8.6, 12.3, 6.4, 9.6), (13.0, 16.3, 6.8, 10.2), (17.0, 20.3, 7.2, 10.7)]
for y0, y1, xl, xr in segs:
    el([xl, y0, 7.6], [xr, y1, 8.4], 'grey', 'blade')
    el([xl + 0.35, y0, 7.56], [xl + 1.0, y1, 8.44], 'greyM', 'shade')
    el([xl - 0.2, y0, 7.68], [xl + 0.1, y1, 8.32], 'hi', 'edge')
    el([xr - 0.1, y0, 7.68], [xr + 0.2, y1, 8.32], 'hi', 'edge')
    el([xr, y1 - 1.7, 7.64], [xr + 1.0, y1 - 0.15, 8.36], 'grey', 'hook')
    el([xr + 0.75, y1 - 0.7, 7.67], [xr + 1.45, y1 + 0.45, 8.33], 'greyM', 'hook')
    el([xr + 1.2, y1 + 0.3, 7.7], [xr + 1.7, y1 + 0.85, 8.3], 'hi', 'hook point')
    el([xl - 0.6, y0 + 0.6, 7.66], [xl, y0 + 1.5, 8.34], 'greyM', 'notch')
    el([xl - 0.25, y1, 7.42], [xr + 0.3, y1 + 0.7, 8.58], 'red', 'band')
    el([xl - 0.25, y1 + 0.5, 7.4], [xr + 0.3, y1 + 0.7, 8.6], 'redL', 'band shine')
# ---- the big crescent head
rows = [(21.0, 22.0, 7.4, 11.2), (22.0, 23.0, 7.6, 12.0), (23.0, 24.0, 7.9, 12.6), (24.0, 25.0, 8.3, 13.0),
        (25.0, 26.0, 8.8, 13.2), (26.0, 27.0, 9.4, 13.2), (27.0, 27.8, 10.2, 12.9), (27.8, 28.5, 11.0, 12.4)]
for i, (y0, y1, xl, xr) in enumerate(rows):
    el([xl, y0, 7.6], [xr, y1, 8.4], 'grey' if i < 3 else 'greyM' if i < 6 else 'greyD', 'head')
    el([xr - 0.32, y0, 7.66], [xr, y1, 8.34], 'hi', 'head rim')
    el([xl, y0, 7.57], [xl + 0.5, y1, 8.43], 'rim', 'head edge')
for y0, y1, xl, xr in ((22.6, 23.6, 6.2, 7.9), (21.8, 22.8, 5.4, 6.6), (21.2, 22.0, 4.9, 5.8)):
    el([xl, y0, 7.64], [xr, y1, 8.36], 'greyM', 'hook')
el([4.6, 21.0, 7.7], [5.2, 21.5, 8.3], 'hi', 'hook point')
el([7.6, 24.4, 7.65], [8.3, 25.1, 8.35], 'greyM', 'notch')
el([11.2, 21.4, 7.65], [12.2, 22.6, 8.35], 'grey', 'head spike'); el([11.9, 22.3, 7.7], [12.6, 23.1, 8.3], 'hi', 'head spike')

json.dump({'textures': {'0': 'xenowarriors:item/hekublade_s3', 'particle': 'xenowarriors:item/hekublade_s3'}, 'elements': blade},
          open(RES + 'models/item/hekublade_s3.json', 'w'), indent=1)
json.dump({'textures': {'1': 'xenowarriors:item/hekublade_galaxy', 'particle': 'xenowarriors:item/hekublade_galaxy'}, 'elements': globe},
          open(RES + 'models/item/hekublade_s3_globe.json', 'w'), indent=1)
print('blade', len(blade), 'globe', len(globe))

# ---- animated galaxy: a slow spiral of violet/blue nebula with twinkling stars (32 frames of 16x16)
N, S = 32, 16
rnd = random.Random(4)
stars = [(rnd.random(), rnd.random() * 2 * math.pi, rnd.random()) for _ in range(26)]
sheet = Image.new('RGBA', (S, S * N))
for f in range(N):
    rot = f / N * 2 * math.pi
    for y in range(S):
        for x in range(S):
            dx, dy = (x + .5) / S * 2 - 1, (y + .5) / S * 2 - 1
            r = math.hypot(dx, dy); a = math.atan2(dy, dx)
            arm = 0.5 + 0.5 * math.cos(2 * (a - rot) - r * 5.0)            # two spiral arms
            neb = max(0, 1 - r) ** 0.8 * (0.35 + 0.65 * arm)
            core = max(0, 1 - r * 3.2) ** 2
            col = (int(14 + 120 * neb * (0.6 + 0.4 * math.sin(a * 2 + rot)) + 230 * core),
                   int(8 + 40 * neb + 200 * core),
                   int(40 + 190 * neb + 230 * core))
            sheet.putpixel((x, f * S + y), tuple(min(255, c) for c in col) + (255,))
    for rr, aa, ph in stars:
        a2 = aa + rot * (1.6 - rr)                                      # inner stars orbit faster
        sx, sy = int((rr * math.cos(a2) * 0.95 + 1) / 2 * S), int((rr * math.sin(a2) * 0.95 + 1) / 2 * S)
        if 0 <= sx < S and 0 <= sy < S:
            tw = 0.55 + 0.45 * math.sin(f / N * 2 * math.pi * 3 + ph * 6)
            c = sheet.getpixel((sx, f * S + sy))
            sheet.putpixel((sx, f * S + sy), tuple(min(255, int(c[i] + (255 - c[i]) * tw)) for i in range(3)) + (255,))
sheet.save(RES + 'textures/item/hekublade_galaxy.png')
json.dump({'animation': {'frametime': 3, 'interpolate': True}}, open(RES + 'textures/item/hekublade_galaxy.png.mcmeta', 'w'))
sheet.crop((0, 0, S, S * 4)).resize((S * 8, S * 32), Image.NEAREST).save('/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/outfit/galaxy.png')
