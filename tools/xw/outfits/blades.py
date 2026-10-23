"""Alternate Hekublade item models (same frame as hekublade_base: drawn along +y, rotated -45 about z)."""
import json
from PIL import Image
RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'

def C(h): h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

class Blade:
    def __init__(self, name, palette):
        self.name = name; self.els = []
        self.img = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); self.pos = {}
        i = 0
        for k, hexc in palette.items():
            c = C(hexc)
            for j, f in enumerate((1.15, 1.0, 0.72)):          # light / mid / dark
                p = i * 3 + j
                self.img.putpixel((p % 16, p // 16), tuple(min(255, int(v * f)) for v in c) + (255,))
            self.pos[k] = i * 3; i += 1

    def uv(self, k, shade):
        p = self.pos[k] + shade
        x, y = p % 16, p // 16
        return [x + .25, y + .25, x + .75, y + .75]

    def el(self, frm, to, k, name='part'):
        faces = {f: {'uv': self.uv(k, s), 'texture': '#0'} for f, s in
                 (('north', 1), ('south', 1), ('east', 2), ('west', 0), ('up', 0), ('down', 2))}
        self.els.append({'name': name, 'from': frm, 'to': to,
                         'rotation': {'angle': -45, 'axis': 'z', 'origin': [8, 8, 8]}, 'faces': faces})

    def save(self):
        self.img.save(RES + f'textures/item/{self.name}.png')
        json.dump({'textures': {'0': f'xenowarriors:item/{self.name}', 'particle': f'xenowarriors:item/{self.name}'},
                   'elements': self.els}, open(RES + f'models/item/{self.name}.json', 'w'), indent=1)
        print(self.name, len(self.els), 'elements')

# ---------------------------------------------------------------- Origins 2: big white broadsword, red-cross guard
b = Blade('hekublade_origins2', {'white': '#e6e0e0', 'steel': '#b8b2b4', 'line': '#3a3638', 'guard': '#5a585c',
                                 'red': '#c81a1a', 'gem': '#f4f6fa', 'orange': '#f07a1e', 'orangeD': '#b8520e', 'dark': '#2a2628'})
b.el([7.1, -1.6, 7.1], [8.9, -0.2, 8.9], 'dark', 'pommel')
b.el([7.3, -0.2, 7.3], [8.7, 4.3, 8.7], 'orange', 'grip')
for y in (0.3, 1.3, 2.3, 3.3):
    b.el([7.2, y, 7.2], [8.8, y + 0.45, 8.8], 'orangeD', 'wrap')
b.el([4.8, 4.3, 7.2], [11.2, 10.0, 8.8], 'guard', 'guard')
b.el([7.4, 4.7, 7.05], [8.6, 9.6, 8.95], 'red', 'cross')
b.el([5.2, 6.55, 7.05], [10.8, 7.75, 8.95], 'red', 'cross')
b.el([5.0, 4.5, 6.95], [6.1, 5.6, 9.05], 'gem', 'gem')
b.el([7.4, 9.9, 7.45], [8.6, 10.9, 8.55], 'red', 'red gem')
b.el([6.0, 10.0, 7.6], [10.0, 25.0, 8.4], 'white', 'blade')
b.el([5.85, 10.0, 7.7], [6.15, 25.0, 8.3], 'steel', 'edge')
b.el([9.85, 10.0, 7.7], [10.15, 25.0, 8.3], 'steel', 'edge')
b.el([7.85, 10.9, 7.55], [8.15, 24.6, 8.45], 'line', 'fuller')
b.el([6.5, 25.0, 7.65], [9.5, 26.4, 8.35], 'white', 'tip')
b.el([7.2, 26.4, 7.7], [8.8, 27.4, 8.3], 'white', 'tip')
b.el([7.7, 27.4, 7.75], [8.3, 28.0, 8.25], 'steel', 'point')
b.save()

# ---------------------------------------------------------------- Recarnet Season 3: hooked grey blade, globe guard
s = Blade('hekublade_s3', {'grey': '#b4b4b8', 'greyD': '#8c8c92', 'hi': '#e2e2e6', 'red': '#c8283a', 'black': '#1e1c20',
                           'silver': '#c4c8cc', 'blue': '#2a56d8', 'globe': '#2e7cd6', 'green': '#3c9a3a', 'rope': '#c2ad86'})
# pommel: blue fin, ring, a short rope end
s.el([7.0, -2.6, 7.3], [9.0, -0.6, 8.7], 'blue', 'fin')
s.el([6.1, -2.3, 7.5], [7.0, -1.5, 8.5], 'blue', 'fin spike')
s.el([9.0, -1.7, 7.5], [9.9, -0.9, 8.5], 'blue', 'fin spike')
s.el([7.4, -3.5, 7.75], [8.6, -3.2, 8.25], 'silver', 'ring')
s.el([7.4, -4.4, 7.75], [8.6, -4.1, 8.25], 'silver', 'ring')
s.el([7.4, -4.4, 7.75], [7.7, -3.2, 8.25], 'silver', 'ring')
s.el([8.3, -4.4, 7.75], [8.6, -3.2, 8.25], 'silver', 'ring')
s.el([7.85, -3.2, 7.85], [8.15, -2.6, 8.15], 'rope', 'rope')
# grip: black with red wraps
s.el([7.35, -0.6, 7.35], [8.65, 4.0, 8.65], 'black', 'grip')
for y in (0.0, 1.2, 2.4):
    s.el([7.25, y, 7.25], [8.75, y + 0.55, 8.75], 'red', 'wrap')
# guard: black disc, silver and red rings, the globe
s.el([5.6, 3.4, 7.0], [10.4, 8.6, 9.0], 'black', 'disc')
s.el([4.8, 4.2, 7.0], [11.2, 7.8, 9.0], 'black', 'disc')
s.el([6.0, 4.0, 6.9], [10.0, 8.0, 9.1], 'silver', 'ring')
s.el([6.4, 4.4, 6.85], [9.6, 7.6, 9.15], 'red', 'ring')
s.el([6.9, 4.9, 6.8], [9.1, 7.1, 9.2], 'globe', 'globe')
s.el([7.2, 5.3, 6.75], [8.2, 6.2, 9.25], 'green', 'land')
s.el([8.3, 6.2, 6.75], [8.9, 6.8, 9.25], 'green', 'land')
s.el([9.6, 7.8, 7.3], [10.6, 10.2, 8.7], 'silver', 'prong')
s.el([5.5, 8.2, 7.3], [6.4, 9.8, 8.7], 'black', 'prong')
s.el([4.1, 3.1, 7.3], [5.2, 4.2, 8.7], 'silver', 'prong')
# blade: segments curving slightly, hooked spikes and red bands, big crescent head
segs = [(8.6, 12.5, 6.3), (13.2, 16.6, 6.6), (17.3, 20.6, 6.9)]
for i, (y0, y1, x0) in enumerate(segs):
    s.el([x0, y0, 7.6], [x0 + 3.4, y1, 8.4], 'grey', 'blade')
    s.el([x0 + 3.2, y0, 7.68], [x0 + 3.55, y1, 8.32], 'hi', 'edge')
    s.el([x0 + 3.4, y1 - 1.1, 7.66], [x0 + 4.5, y1 + 0.5, 8.34], 'grey', 'spike')
    s.el([x0 + 4.0, y1 + 0.2, 7.7], [x0 + 4.6, y1 + 0.9, 8.3], 'hi', 'spike tip')
    s.el([x0 - 0.2, y1, 7.45], [x0 + 3.6, y1 + 0.7, 8.55], 'red', 'band')
s.el([6.8, 21.3, 7.6], [11.6, 24.0, 8.4], 'grey', 'head')
s.el([7.4, 24.0, 7.6], [12.4, 26.0, 8.4], 'grey', 'head')
s.el([8.6, 26.0, 7.62], [12.6, 27.2, 8.38], 'greyD', 'head')
s.el([10.0, 27.2, 7.64], [12.4, 28.0, 8.36], 'greyD', 'head')
s.el([11.6, 21.6, 7.66], [12.0, 26.0, 8.34], 'hi', 'head edge')
s.el([12.4, 24.2, 7.66], [12.8, 27.0, 8.34], 'hi', 'head edge')
s.el([5.4, 22.4, 7.64], [6.9, 23.5, 8.36], 'greyD', 'hook')
s.el([4.7, 21.6, 7.66], [5.6, 22.6, 8.34], 'hi', 'hook tip')
s.save()
