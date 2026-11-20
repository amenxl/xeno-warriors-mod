"""The big ship (15 aboard) and the Corrupted Souls' ghost ship: one wooden sailing-ship model (a galleon - hull,
main deck, raised quarterdeck with the ship's wheel, three masts with square sails, bowsprit, six cannons, stern
lanterns), painted three ways: oak, dark oak, and corrupted (black rotting wood, torn purple sails, glowing lanterns).

Uses vehicles.py's Model (box-UV atlas, geo export). Model units: 16 = 1 model block; the ship is drawn at x2
(VehicleKind.SHIP scale 2.0), so 8 units = 1 world block. Faces -z (the bow); +x is starboard (its right); y=0 is the
keel. VehicleKind.rawSeats("ship") stands the riders on the deck (deck top y=20, quarterdeck top y=28).

Usage: python3 ship.py   (writes geo/ship.geo.json, textures/entity/vehicle/{ship_oak,ship_dark,corrupt_ship_corrupted}.png
+ glowmasks, animations/ship.animation.json, item icons, and a preview into $XW_PREVIEW_DIR)"""
import json, math, os, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vehicles as V

R = V.R
PREV = V.PREV

PALETTES = {
    'oak': dict(hull=(138, 96, 56), hull2=(104, 70, 40), deck=(176, 136, 86), trim=(70, 46, 26), sail=(236, 228, 206), sail2=(196, 40, 40),
                mast=(96, 66, 38), rope=(150, 128, 92), iron=(38, 38, 42), lamp=(255, 196, 90), flag=(196, 40, 40), torn=False),
    'dark': dict(hull=(70, 48, 30), hull2=(52, 34, 20), deck=(112, 82, 52), trim=(196, 160, 60), sail=(214, 204, 180), sail2=(30, 60, 120),
                 mast=(60, 42, 26), rope=(130, 110, 80), iron=(34, 34, 38), lamp=(255, 196, 90), flag=(30, 60, 120), torn=False),
    'corrupted': dict(hull=(40, 30, 40), hull2=(26, 20, 28), deck=(62, 50, 58), trim=(120, 40, 160), sail=(70, 34, 92), sail2=(150, 60, 200),
                      mast=(34, 26, 34), rope=(80, 60, 90), iron=(20, 18, 24), lamp=(190, 80, 255), flag=(150, 60, 200), torn=True),
}


def build(P):
    m = V.Model('ship', tex=512)
    H, H2, D, T = P['hull'], P['hull2'], P['deck'], P['trim']

    def hull_side(w_units, h_units):
        """Planks running fore-aft (lines every 2 units), a darker strake at the waterline, a trim band at the top."""
        def f(x, y):
            yy = y * h_units
            if yy < 1.0: return T
            if yy > h_units - 4: return V.mul(H2, .85)
            if int(yy) % 2 == 0 and (x * w_units) % 1.0 < .9: return V.mul(H, .9)
            seam = int(x * w_units / 6 + (int(yy) % 4) * .5)
            return V.mul(H, .96 if seam % 2 else 1.04)
        return f

    def deck_top(w_units, d_units):
        def f(x, y):
            xx = x * w_units
            if int(xx) % 3 == 0: return V.mul(D, .82)
            return V.mul(D, .97 if int(y * d_units / 8 + xx / 3) % 2 else 1.04)
        return f

    def sail(w, h, seed):
        def f(x, y):
            if P['torn']:
                # ragged lower edge and a few holes
                edge = .82 + .1 * math.sin(x * 23 + seed) + .06 * math.sin(x * 51 + seed * 3)
                if y > edge: return None
                for hx, hy, r in ((.3 + .1 * seed % .3, .4, .07), (.7, .62 - .05 * seed % .2, .05)):
                    if (x - hx) ** 2 + (y - hy) ** 2 < r * r: return None
            if abs(x - .5) < .02 or abs(y - .5) < .015: return V.mul(P['sail'], .86)   # seams
            if P['torn'] and math.hypot(x - .5, y - .45) < .14: return P['sail2']   # (a glowing soul mark)
            if not P['torn'] and seed == 1 and .4 < y < .62 and abs(x - .5) < .18 - (y - .4) * .5: return P['sail2']   # (an emblem on the main sail)
            return V.mul(P['sail'], 1 - .08 * y)
        return f

    # ---- hull
    m.box('root', [-12, 0, -40], [12, 3, 44], H2)                                                    # keel / bottom
    m.box('root', [-17, 3, -36], [17, 20, 40], H, {'right': hull_side(76, 17), 'left': hull_side(76, 17), 'top': deck_top(34, 76),
                                                   'front': H2, 'back': H2})                        # main hull, deck on top
    m.box('root', [-14, 5, -44], [14, 21, -36], H, {'right': hull_side(8, 16), 'left': hull_side(8, 16), 'top': deck_top(28, 8)})   # bow taper 1
    m.box('root', [-9, 8, -50], [9, 22, -44], H, {'right': hull_side(6, 14), 'left': hull_side(6, 14), 'top': deck_top(18, 6)})     # bow taper 2
    m.box('root', [-4, 11, -55], [4, 23, -50], H, {'right': hull_side(5, 12), 'left': hull_side(5, 12), 'top': deck_top(8, 5)})     # the stem
    m.box('root', [-1, 21, -72], [1, 23, -55], P['mast'])                                            # bowsprit
    m.box('root', [-14, 3, 40], [14, 30, 46], H, {'right': hull_side(6, 27), 'left': hull_side(6, 27), 'back':
          lambda x, y: P['lamp'] if (.2 < x < .3 or .7 < x < .8) and .25 < y < .4 else T if y < .08 or y > .92 else H})   # stern wall + windows
    m.box('root', [-17, 20, 26], [17, 28, 40], H, {'right': hull_side(14, 8), 'left': hull_side(14, 8), 'top': deck_top(34, 14),
                                                   'front': lambda x, y: T if y < .15 else H})       # quarterdeck
    # rails
    m.sym('root', [15.5, 20, -36], [17, 24, 26], T)
    m.sym('root', [15.5, 28, 26], [17, 32, 40], T)
    m.box('root', [-17, 28, 39.5], [17, 32, 41], T)
    m.sym('root', [12.5, 21, -44], [14, 25, -36], T)
    m.box('root', [-14, 27.5, 25.5], [14, 29, 26.5], T)                                                 # quarterdeck front rail
    # ---- the ship's wheel (on the quarterdeck, the captain stands behind it)
    m.box('root', [-1, 28, 29], [1, 34, 31], P['mast'])
    ring = lambda x, y: P['mast'] if .3 < math.hypot(x - .5, y - .5) < .5 or abs(x - .5) < .08 or abs(y - .5) < .08 else None
    m.box('root', [-5, 31, 28.6], [5, 41, 29], P['mast'], {'front': ring, 'back': ring})
    # ---- masts, yards, sails
    def mast(z, top, low_yard, high_yard, sw, seed):
        m.box('root', [-1.5, 20, z - 1.5], [1.5, top, z + 1.5], P['mast'])
        for yv, half in ((low_yard, sw / 2 + 2), (high_yard, sw / 2)):
            m.box('root', [-half, yv, z - 1], [half, yv + 1.5, z + 1], P['mast'])
        lo_h = high_yard - low_yard - 2
        m.box('root', [-(sw / 2 + 1), low_yard - lo_h - 6, z + 1.2], [sw / 2 + 1, low_yard, z + 2.2], P['sail'],
              {'front': sail(sw, lo_h + 6, seed), 'back': sail(sw, lo_h + 6, seed), 'top': None, 'bottom': None, 'left': None, 'right': None})
        m.box('root', [-(sw / 2 - 1), low_yard + 1.5, z + 1.2], [sw / 2 - 1, high_yard, z + 2.2], P['sail'],
              {'front': sail(sw - 2, lo_h, seed + 2), 'back': sail(sw - 2, lo_h, seed + 2), 'top': None, 'bottom': None, 'left': None, 'right': None})
        m.box('root', [-2.5, top - 2, z - 2.5], [2.5, top, z + 2.5], P['mast'])                          # crow's nest
    mast(-30, 92, 58, 84, 34, 0)
    mast(-4, 112, 66, 98, 40, 1)
    mast(34, 82, 52, 74, 24, 3)
    m.box('root', [0, 104, -3], [0.4, 112, -3 + 10], P['flag'], {'right': lambda x, y: P['flag'] if x < .9 - .2 * abs(y - .5) else None,
                                                                 'left': lambda x, y: P['flag'] if x > .1 + .2 * abs(y - .5) else None})   # flag
    # rigging (thin lines from the mast tops down to the rails)
    for z, top in ((-30, 88), (-4, 106), (34, 78)):
        for s in (-1, 1):
            m.box('root', [s * 15.6 - .25, 22, z + 4], [s * 15.6 + .25, top - 10, z + 4.5], P['rope'])
    # ---- cannons: three a side, poking out of the gun ports
    port = lambda x, y: (12, 10, 10) if .2 < x < .8 and .2 < y < .8 else T
    for z in (-24, -6, 12):
        m.sym('root', [17, 11, z - 3], [17.3, 17, z + 3], T, {'right': port})
        m.sym('root', [17.2, 12.5, z - 1.5], [21, 15.5, z + 1.5], P['iron'])
    # deck cannons on the quarterdeck, pointing out
    m.sym('root', [11, 28, 33], [17.5, 31, 36], P['iron'])
    # ---- deck things: hatch, barrels, crates
    m.box('root', [-6, 20, 0], [6, 21, 10], V.mul(D, .7), {'top': lambda x, y: (30, 22, 14) if int(x * 12) % 2 else V.mul(D, .7)})
    m.box('root', [-11, 20, 18], [-6, 26, 23], P['hull2'], {'top': V.mul(D, .8)})
    m.box('root', [7, 20, 17], [12, 25, 22], V.mul(D, .85), {'top': V.mul(D, .7)})
    # ---- stern lanterns (they glow)
    for s in (-1, 1):
        m.box('root', [s * 12 - 1.5, 32, 44], [s * 12 + 1.5, 36, 47], P['lamp'], glow={'front': P['lamp'], 'back': P['lamp'], 'left': P['lamp'], 'right': P['lamp']})
    m.box('root', [-1.5, 22, -58], [1.5, 26, -55], P['lamp'], glow={'front': P['lamp'], 'left': P['lamp'], 'right': P['lamp']})   # bow lantern
    if P['torn']:   # glowing soul marks on the hull
        m.sym('root', [17.05, 6, -20], [17.1, 10, 30], P['hull'], {'right': lambda x, y: P['trim'] if abs(y - .5 - .3 * math.sin(x * 18)) < .12 else None},
              glow={'right': lambda x, y: P['trim'] if abs(y - .5 - .3 * math.sin(x * 18)) < .12 else None})
    else:
        m.sym('root', [17.05, 6, -20], [17.1, 10, 30], P['hull'], {'right': lambda x, y: P['trim'] if abs(y - .5) < .18 else None})
    return m


def preview(m, tex, out, yaw=-35, pitch=22, size=520):
    """A quick flat-shaded preview (each face its texture's average colour, painter's order)."""
    px = tex.load()
    T = m.tex
    faces = []
    cy, sy, cp, sp = math.cos(math.radians(yaw)), math.sin(math.radians(yaw)), math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    def proj(p):
        x, y, z = p
        x2, z2 = x * cy - z * sy, x * sy + z * cy
        y2, z3 = y * cp - z2 * sp, y * sp + z2 * cp
        return x2, y2, z3
    for b in m.order:
        for c in m.bones[b]['cubes']:
            lo, hi = c['lo'], c['hi']
            w, h, d = [max(1, math.ceil(hi[k] - lo[k] - 1e-6)) for k in range(3)]
            u, v = c['uv']
            rects = {'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h),
                     'back': (u + 2 * d + w, v + d, w, h), 'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d)}
            x0, y0, z0 = lo; x1, y1, z1 = hi
            quads = {'right': [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], 'left': [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)],
                     'front': [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], 'back': [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],
                     'top': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], 'bottom': [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)]}
            for f, q in quads.items():
                fx, fy, fw, fh = rects[f]
                cols = [px[fx + i, fy + j] for j in range(fh) for i in range(fw) if px[fx + i, fy + j][3] > 0]
                if not cols: continue
                col = tuple(sum(cc[k] for cc in cols) // len(cols) for k in range(3))
                shade = {'top': 1.1, 'bottom': .6, 'right': .9, 'left': .75, 'front': .85, 'back': .8}[f]
                pts = [proj(p) for p in q]
                faces.append((sum(p[2] for p in pts) / 4, [(p[0], p[1]) for p in pts], V.mul(col, shade)))
    faces.sort(key=lambda f: -f[0])
    im = Image.new('RGB', (size, size), (40, 70, 110)); dr = ImageDraw.Draw(im)
    s = size / 190
    for _, pts, col in faces: dr.polygon([(size / 2 + x * s, size * .7 - y * s) for x, y in pts], fill=col)
    im.save(out)
    return im


def icons():
    A = R
    def icon(name, hull, sail, extra=None):
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
        d.polygon([(1, 10), (15, 10), (13, 14), (3, 14)], fill=hull + (255,))
        d.line((1, 10, 15, 10), fill=V.mul(hull, .6) + (255,))
        d.line((8, 2, 8, 10), fill=(70, 48, 30, 255)); d.line((4, 4, 4, 10), fill=(70, 48, 30, 255)); d.line((12, 5, 12, 10), fill=(70, 48, 30, 255))
        d.rectangle((6, 3, 10, 8), fill=sail + (255,)); d.rectangle((3, 5, 5, 8), fill=sail + (255,)); d.rectangle((11, 6, 13, 8), fill=sail + (255,))
        d.point((8, 1), fill=(196, 40, 40, 255))
        if extra: extra(d)
        im.save(A + f'textures/item/{name}.png')
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'xenowarriors:item/{name}'}}, open(A + f'models/item/{name}.json', 'w'))
    icon('ship', (138, 96, 56), (236, 228, 206))
    icon('corrupt_ship', (40, 30, 40), (110, 50, 150), lambda d: d.point((8, 5), fill=(220, 120, 255, 255)))


if __name__ == '__main__':
    os.makedirs(PREV, exist_ok=True)
    geo = None
    for name, pal in (('ship_oak', 'oak'), ('ship_dark', 'dark'), ('corrupt_ship_corrupted', 'corrupted')):
        m = build(PALETTES[pal])
        tex, glow = m.pack((0, 0, 0))
        tex.save(R + f'textures/entity/vehicle/{name}.png'); glow.save(R + f'textures/entity/vehicle/{name}_glowmask.png')
        g = m.geo()
        if geo is None: geo = g
        else: assert json.dumps(g) == json.dumps(geo), 'the variants must share one layout'
        preview(m, tex, f'{PREV}/{name}_prev.png')
        preview(m, tex, f'{PREV}/{name}_prev2.png', yaw=150, pitch=15)
    geo['minecraft:geometry'][0]['description'].update({'visible_bounds_width': 16, 'visible_bounds_height': 14, 'visible_bounds_offset': [0, 6, 0]})
    json.dump(geo, open(R + 'geo/ship.geo.json', 'w'))
    json.dump({'format_version': '1.8.0', 'animations': {}}, open(R + 'animations/ship.animation.json', 'w'))
    icons()
    print('ok', sum(len(m.bones[b]['cubes']) for b in m.order), 'cubes')
