"""Outfit kit: skin painting, box-UV cube atlas painting (float-accurate), mod JSON export, textured preview."""
import json, math, random
import numpy as np
from PIL import Image, ImageDraw

RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
CHAR = RES + 'textures/entity/character/'

def C(h):
    if isinstance(h, tuple): return tuple(h[:3])
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def mul(c, f): return tuple(max(0, min(255, int(v * f))) for v in C(c)[:3])

# ---------------------------------------------------------------- skin
# part -> (u, v, w, h, d, outer side)
PARTS = {'body': (16, 16, 8, 12, 4, None), 'rarm': (40, 16, 4, 12, 4, 'right'), 'larm': (32, 48, 4, 12, 4, 'left'),
         'rleg': (0, 16, 4, 12, 4, 'right'), 'lleg': (16, 48, 4, 12, 4, 'left')}

def face_rects(u, v, w, h, d):
    return {'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h),
            'back': (u + 2 * d + w, v + d, w, h), 'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d)}

def paint_skin(fn, seed=5, char='mikiko'):
    """fn(part, face, x, y, W, H, outer) -> rgb. Head/hat copied from his normal skin."""
    rnd = random.Random(seed)
    base = Image.open(CHAR + char + '.png').convert('RGBA')
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    im.paste(base.crop((0, 0, 64, 16)), (0, 0))
    px = im.load()
    for part, (u, v, w, h, d, outer) in PARTS.items():
        for face, (x0, y0, W, H) in face_rects(u, v, w, h, d).items():
            for y in range(H):
                for x in range(W):
                    c = fn(part, face, x, y, W, H, outer)
                    if c is None: continue
                    c = C(c)
                    c = mul(c, 1 + (rnd.random() - .5) * .07)
                    px[x0 + x, y0 + y] = c + (255,)
    return im

# ---------------------------------------------------------------- cubes (Blockbench space: y up, feet 0, front -z, his right +x)
BB_PIV = {'head': [0, 24, 0], 'body': [0, 24, 0], 'right_arm': [5, 22, 0], 'left_arm': [-5, 22, 0],
          'right_leg': [1.9, 12, 0], 'left_leg': [-1.9, 12, 0]}
MIRROR = {'right_arm': 'left_arm', 'right_leg': 'left_leg', 'left_arm': 'right_arm', 'left_leg': 'right_leg'}

class Fit:
    def __init__(self):
        self.cubes = []

    def add(self, bone, frm, to, col, faces=None, rot=None, origin=None, shade=True, group=None):
        """group=(name, pivot) puts the cube in a named child part (e.g. coat_back, which the mod swings with the legs)."""
        lo = [min(a, b) for a, b in zip(frm, to)]; hi = [max(a, b) for a, b in zip(frm, to)]
        self.cubes.append(dict(bone=bone, lo=lo, hi=hi, col=col, faces=faces or {}, rot=rot, shade=shade, group=group,
                               origin=origin or [(a + b) / 2 for a, b in zip(lo, hi)]))

    def shell(self, bone, lo, hi, inf, col, faces=None, **k):
        self.add(bone, [a - inf for a in lo], [b + inf for b in hi], col, faces, **k)

    def both(self, bone, frm, to, col, faces=None, **k):
        """A cube on a right limb and its mirror on the left one (x flipped around the body center)."""
        self.add(bone, frm, to, col, faces, **k)
        mf = {('left' if f == 'right' else 'right' if f == 'left' else f): v for f, v in (faces or {}).items()}
        mfaces = {}
        for f, v in mf.items():
            if callable(v) and f in ('front', 'back', 'top', 'bottom'):
                mfaces[f] = (lambda g: (lambda x, y: g(1 - x, y)))(v)
            else:
                mfaces[f] = v
        self.add(MIRROR[bone], [-to[0], frm[1], frm[2]], [-frm[0], to[1], to[2]], col, mfaces, **k)

def tex_of(spec, fx, fy):
    if spec is None: return None
    return spec(fx, fy) if callable(spec) else C(spec)

def pack(fit, atlas, region=(0, 132, 256, 256), seed=9):
    """Paints every cube's box UV into `atlas` inside `region`; sets cube['uv']."""
    rnd = random.Random(seed)
    px = atlas.load()
    x0r, y0r, x1r, y1r = region
    for yy in range(y0r, y1r):
        for xx in range(x0r, x1r):
            px[xx, yy] = (0, 0, 0, 0)
    order = sorted(range(len(fit.cubes)), key=lambda i: -(math.ceil(fit.cubes[i]['hi'][2] - fit.cubes[i]['lo'][2]) + math.ceil(fit.cubes[i]['hi'][1] - fit.cubes[i]['lo'][1])))
    x, y, shelf = x0r, y0r, 0
    for i in order:
        c = fit.cubes[i]
        w, h, d = [c['hi'][k] - c['lo'][k] for k in range(3)]
        rw = math.ceil(2 * d + 2 * w) + 1; rh = math.ceil(d + h) + 1
        if x + rw > x1r: x = x0r; y += shelf + 1; shelf = 0
        if y + rh > y1r: raise SystemExit('atlas full')
        c['uv'] = [x, y]
        u, v = x, y
        rects = {'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h),
                 'back': (u + 2 * d + w, v + d, w, h), 'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d)}
        for ty in range(v, v + rh):
            for tx in range(u, u + rw):
                cx, cy = tx + .5, ty + .5
                best = None
                for f, (fx0, fy0, fw, fh) in rects.items():
                    if fw <= 0 or fh <= 0: continue
                    if fx0 - .5 <= cx <= fx0 + fw + .5 and fy0 - .5 <= cy <= fy0 + fh + .5:
                        dist = max(fx0 - cx, cx - fx0 - fw, fy0 - cy, cy - fy0 - fh)
                        if best is None or dist < best[0]: best = (dist, f, fx0, fy0, fw, fh)
                if best is None: continue
                _, f, fx0, fy0, fw, fh = best
                lx = min(.999, max(0, (cx - fx0) / fw)); ly = min(.999, max(0, (cy - fy0) / fh))
                col = tex_of(c['faces'].get(f, c['col']), lx, ly)
                if col is None: continue
                fac = 1 + (rnd.random() - .5) * .08
                if c['shade']:
                    if f == 'top': fac *= 1.08
                    elif f == 'bottom': fac *= .8
                    else: fac *= 1.05 - .12 * ly
                px[tx, ty] = mul(col, fac) + (255,)
        x += rw + 1; shelf = max(shelf, rh)
    return atlas

def r4(v): return [round(a, 4) for a in v]

def export(fit, path, size=256, char='mikiko', keep=('right_wing', 'left_wing')):
    src = json.load(open(RES + f'models/detail/{char}.json'))
    out = {'texture_size': size, 'bones': {}, 'face': src['face']}
    for b, node in src['bones'].items():
        out['bones'][b] = {'pivot': node['pivot'], 'cubes': [], 'children': [ch for ch in node.get('children', []) if ch.get('name') in keep]}
    out['bones']['head'] = src['bones']['head']           # hair + goggles, unchanged
    n = 0
    for c in fit.cubes:
        P = BB_PIV[c['bone']]; node = out['bones'][c['bone']]
        lo, hi = c['lo'], c['hi']
        size3 = r4([hi[k] - lo[k] for k in range(3)])
        if c.get('group'):
            gname, gp = c['group']
            g = next((ch for ch in node['children'] if ch.get('name') == gname), None)
            if g is None:
                g = {'name': gname, 'pivot': r4([P[0] - gp[0], P[1] - gp[1], gp[2] - P[2]]), 'rot': [0, 0, 0], 'cubes': [], 'children': []}
                node['children'].append(g)
            g['cubes'].append({'uv': c['uv'], 'from': r4([gp[0] - hi[0], gp[1] - hi[1], lo[2] - gp[2]]), 'size': size3})
        elif c['rot']:
            o = c['origin']
            frm = r4([o[0] - hi[0], o[1] - hi[1], lo[2] - o[2]])
            r = c['rot']
            node['children'].append({'name': f'o{n}', 'pivot': r4([P[0] - o[0], P[1] - o[1], o[2] - P[2]]),
                                     'rot': r4([-math.radians(r[0]), -math.radians(r[1]), math.radians(r[2])]),
                                     'cubes': [{'uv': c['uv'], 'from': frm, 'size': size3}], 'children': []})
            n += 1
        else:
            frm = r4([P[0] - hi[0], P[1] - hi[1], lo[2] - P[2]])
            node['cubes'].append({'uv': c['uv'], 'from': frm, 'size': size3})
    json.dump(out, open(path, 'w'), separators=(',', ':'))
    return out

# ---------------------------------------------------------------- preview (mod space: y down, front -z, his right -x)
def Rzyx(r):
    x, y, z = r
    X = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    Y = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    Z = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return Z @ Y @ X

VBONES = {'head': ([0, 0, 0], [-4, -8, -4], (0, 0)), 'body': ([0, 0, 0], [-4, 0, -2], (16, 16)),
          'right_arm': ([-5, 2, 0], [-3, -2, -2], (40, 16)), 'left_arm': ([5, 2, 0], [-1, -2, -2], (32, 48)),
          'right_leg': ([-1.9, 12, 0], [-2, 0, -2], (0, 16)), 'left_leg': ([1.9, 12, 0], [-2, 0, -2], (16, 48))}

def box_quads(frm, size, uv, tex, M, T):
    """Textured box (vanilla box UV) -> list of (depth, pts2d, color) small quads after transform M (3x3), T."""
    x0, y0, z0 = frm; w, h, d = size; u, v = uv
    X1, Y1, Z1 = x0 + w, y0 + h, z0 + d
    # face: (origin corner, axis along tex x, axis along tex y, tex rect, normal)
    F = [((x0, y0, z0), (w, 0, 0), (0, h, 0), (u + d, v + d, w, h), (0, 0, -1)),          # north/front: tex x -> +x
         ((X1, y0, Z1), (-w, 0, 0), (0, h, 0), (u + 2 * d + w, v + d, w, h), (0, 0, 1)),   # south/back
         ((x0, y0, Z1), (0, 0, -d), (0, h, 0), (u, v + d, d, h), (-1, 0, 0)),              # his right (-x)
         ((X1, y0, z0), (0, 0, d), (0, h, 0), (u + d + w, v + d, d, h), (1, 0, 0)),        # his left (+x)
         ((x0, y0, Z1), (w, 0, 0), (0, 0, -d), (u + d, v, w, d), (0, -1, 0)),              # top
         ((x0, Y1, z0), (w, 0, 0), (0, 0, d), (u + d + w, v, w, d), (0, 1, 0))]            # bottom
    out = []
    light = np.array([-0.3, -0.8, -0.5]); light /= np.linalg.norm(light)
    for o, ax, ay, (tu, tv, tw, th), nrm in F:
        n = M @ np.array(nrm, float)
        if n[2] > 0.01: continue
        nx, ny = max(1, int(round(tw))), max(1, int(round(th)))
        nx = min(nx, 16); ny = min(ny, 16)
        sh = 0.6 + 0.4 * max(0, n @ light)
        for j in range(ny):
            for i in range(nx):
                sx = tu + (i + .5) * tw / nx; sy = tv + (j + .5) * th / ny
                if not (0 <= sx < tex.width and 0 <= sy < tex.height): continue
                c = tex.getpixel((int(sx), int(sy)))
                if c[3] < 10: continue
                pts = []
                for a, b in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    p = np.array(o, float) + np.array(ax) * (a / nx) + np.array(ay) * (b / ny)
                    pts.append(M @ p + T)
                dz = np.mean([p[2] for p in pts])
                out.append((dz, [(p[0], p[1]) for p in pts], mul(c[:3], sh)))
    return out

def preview(model_json, skin, detail, face, yaws=(0, 35, 160, 270), scale=11, pose=None):
    pose = pose or {}
    ims = []
    for yaw in yaws:
        V = Rzyx([0, math.radians(yaw), 0])
        quads = []
        for b, (piv, frm, uv) in VBONES.items():
            R = Rzyx(pose.get(b, [0, 0, 0]))
            M = V @ R; T = V @ np.array(piv, float)
            quads += box_quads(frm, [8, 8, 8] if b == 'head' else [8, 12, 4] if b == 'body' else [4, 12, 4], uv, skin, M, T)
            node = model_json['bones'][b]
            def walk(nd, M, T):
                out = []
                for c in nd['cubes']:
                    out += box_quads(c['from'], c['size'], c['uv'], detail, M, T)
                for ch in nd.get('children', []):
                    M2 = M @ Rzyx(ch.get('rot', [0, 0, 0])); T2 = M @ np.array(ch['pivot'], float) + T
                    out += walk(ch, M2, T2)
                return out
            quads += walk(node, M, T)
            if b == 'head' and face is not None:
                quads += [(q[0] - 0.05, q[1], q[2]) for q in box_quads([-4, -8, -4.08], [8, 8, 0], (0, 0), face, M, T)
                          if True]
        quads.sort(key=lambda q: -q[0])
        W, H = 30 * scale, 36 * scale
        img = Image.new('RGB', (W, H), (40, 44, 52))
        dr = ImageDraw.Draw(img)
        for _, pts, col in quads:
            dr.polygon([(W / 2 + x * scale, 3 * scale + (y + 8) * scale) for x, y in pts], fill=col)
        ims.append(img)
    sheet = Image.new('RGB', (sum(i.width for i in ims), ims[0].height))
    x = 0
    for i in ims: sheet.paste(i, (x, 0)); x += i.width
    return sheet

def recolor_rect(img, x, y, w, h, target, keep_lum=True):
    px = img.load()
    cells = [(xx, yy) for yy in range(y, y + h) for xx in range(x, x + w) if px[xx, yy][3] > 0]
    if not cells: return
    lum = [sum(px[c][:3]) / 3 for c in cells]; avg = sum(lum) / len(lum) or 1
    for c, l in zip(cells, lum):
        f = l / avg if keep_lum else 1
        px[c] = mul(C(target), f) + (255,)

GOGGLE = {'frame': [(203, 36), (227, 36)], 'lens': [(216, 36), (240, 36)], 'glint': [(24, 70), (31, 70), (36, 70), (43, 70)],
          'bridge': [(48, 70)]}
