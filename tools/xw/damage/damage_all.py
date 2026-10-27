"""Battle damage for the newer playables (the same look Mikiko and Rhalix have): scratches, grime and little tears as
they get hurt (damage1/2 overlays on the skin, the 3D clothes and the face), and torn clothes after explosions (torn1-3:
skin showing through holes; the 3D clothes get real holes punched through). Run after the character's assets."""
import sys, math, random, os
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/sp'); sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/kaeru')
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/female')
from PIL import Image
import slim

CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
def hexc(h): h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
def near(c, ref, tol=34): return sum(abs(a - b) for a, b in zip(c[:3], ref)) < tol * 3

def spec(key):
    if key == 'ultima':                                   # (no fit to hand: his skin tone from his face, no 3D holes)
        im = Image.open(CH + 'ultima.png').convert('RGBA'); from collections import Counter
        c = Counter(im.getpixel((x, y))[:3] for x in range(9, 15) for y in range(12, 16)).most_common(1)[0][0]
        return '#%02x%02x%02x' % c, None
    if key == 'kaeru':
        import kaeru as K
        return K.SPEC['skin'], K.make()[0]
    import sp_chars as S
    sp = S.CHARS[key]
    return sp['skin'], S.make(key)[0]

def cloth_mask(fit, size):
    ok = set()
    for c in fit.cubes:
        if c['bone'] == 'head' or 'uv' not in c: continue
        w, h, d = (math.ceil(c['hi'][i] - c['lo'][i] - 1e-6) or 1 for i in range(3))
        u, v = c['uv']
        for y in range(v, v + d + h + 1):
            for x in range(u, u + 2 * (d + w) + 1):
                if 0 <= x < size[0] and 0 <= y < size[1]: ok.add((x, y))
    return ok

def overlay(img, skin, level, seed, density):
    w, h = img.size; px = img.load()
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0)); op = out.load()
    rnd = random.Random(seed)
    opaque = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] > 0]
    if not opaque: return out
    def put(x, y, c):
        if 0 <= x < w and 0 <= y < h and px[x, y][3] > 0 and op[x, y][3] == 0: op[x, y] = c
    for _ in range(int(len(opaque) * density * (1 if level == 1 else 2.2))):
        x, y = rnd.choice(opaque)
        if near(px[x, y], skin):
            d = rnd.choice([(1, 1), (1, -1)])
            for i in range(rnd.randint(2, 3)): put(x + d[0] * i, y + d[1] * i, (122, 30, 26, 210))
            put(x + 1, y, (190, 110, 90, 120))
        else:
            r = rnd.random()
            if r < 0.45:
                c = (60, 46, 34, 110) if rnd.random() < 0.6 else (24, 20, 20, 120)
                for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0)):
                    if rnd.random() < 0.75: put(x + dx, y + dy, c)
            elif r < 0.8:
                put(x, y, (18, 14, 14, 230)); put(x + 1, y, (40, 30, 28, 160))
                if level == 2: put(x, y + 1, (18, 14, 14, 200))
            else: put(x, y, (220, 210, 200, 70))
    return out

def holes(img, skin, seed, count, rmax, mask):
    w, h = img.size; px = img.load(); rnd = random.Random(seed)
    cloth = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] > 0 and not near(px[x, y], skin) and (mask is None or (x, y) in mask)]
    stages, cur = [], set()
    if not cloth: return [set(), set(), set()]
    for stage in range(3):
        for _ in range(count * (stage + 1)):
            cx, cy = rnd.choice(cloth); r = rnd.uniform(0.8, rmax)
            for y in range(int(cy - r - 1), int(cy + r + 2)):
                for x in range(int(cx - r - 1), int(cx + r + 2)):
                    if 0 <= x < w and 0 <= y < h and (x - cx) ** 2 + ((y - cy) * 1.3) ** 2 <= r * r + rnd.uniform(-0.6, 0.6):
                        if px[x, y][3] > 0 and not near(px[x, y], skin) and (mask is None or (x, y) in mask): cur.add((x, y))
        stages.append(set(cur))
    return stages

def edges(hole, w, h):
    out = set()
    for x, y in hole:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in hole and 0 <= q[0] < w and 0 <= q[1] < h: out.add(q)
    return out

def face_damage(key, mouth_x=12, mouth_y=24):
    for level in (1, 2):
        f = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); p = f.load()
        for x, y in [(24, 19), (25, 20), (26, 21)]: p[x, y] = (130, 30, 26, 220)            # cut on the cheek
        for x, y in [(6, 24), (7, 24), (7, 25), (26, 26), (27, 26)]: p[x, y] = (60, 46, 34, 120)   # grime
        if level == 2:
            for x in range(4, 10):
                for y in (20, 21): p[x, y] = (236, 214, 180, 255) if x not in (4, 9) else (200, 176, 140, 255)   # a plaster
            p[6, 20] = (210, 186, 150, 255); p[7, 21] = (210, 186, 150, 255)
            for x, y in [(20, 20), (21, 20), (22, 21), (20, 21)]: p[x, y] = (90, 50, 90, 110)       # bruise
            p[min(31, mouth_x + 4), min(31, mouth_y + 2)] = (130, 30, 26, 200)                     # split lip
        f.save(CH + f'{key}_face_damage{level}.png')

def build(key):
    skin_hex, fit = spec(key)
    skin = hexc(skin_hex)
    base = Image.open(CH + f'{key}.png').convert('RGBA')
    det = Image.open(CH + f'{key}_details.png').convert('RGBA')
    girl = key in slim.GIRLS
    for level in (1, 2):
        o = overlay(base, skin, level, 11 * level + len(key), 0.035); o.save(CH + f'{key}_damage{level}.png')
        if girl: slim.mark(CH + f'{key}_damage{level}.png')
        overlay(det, skin, level, 17 * level + len(key), 0.02).save(CH + f'{key}_details_damage{level}.png')
    import sp_chars as S
    sp = S.CHARS.get(key, {})
    face_damage(key, sp.get('mouth_x', 12), sp.get('mouth_y', 24))
    # torn: skin overlay holes (not the head), and holes punched through the 3D clothes
    sp_ = base.load(); dark = tuple(int(c * 0.85) for c in skin)
    body = {(x, y) for y in range(16, 64) for x in range(64)}
    for i, hole in enumerate(holes(base, skin, 7 + len(key), 7, 2.2, body), 1):
        o = Image.new('RGBA', base.size, (0, 0, 0, 0)); op = o.load(); rnd = random.Random(i)
        for x, y in hole: op[x, y] = (skin if rnd.random() < 0.8 else dark) + (255,)
        for x, y in edges(hole, *base.size):
            if sp_[x, y][3] > 0 and not near(sp_[x, y], skin):
                r, g, b, a = sp_[x, y]; op[x, y] = (int(r * 0.55), int(g * 0.55), int(b * 0.55), 255)
        o.save(CH + f'{key}_torn{i}.png')
        if girl: slim.mark(CH + f'{key}_torn{i}.png')
    if fit is None: return
    for i, hole in enumerate(holes(det, skin, 31 + len(key), 10, 3.0, cloth_mask(fit, det.size)), 1):
        t = det.copy(); tp = t.load()
        for x, y in hole: tp[x, y] = (0, 0, 0, 0)
        for x, y in edges(hole, *det.size):
            r, g, b, a = tp[x, y]
            if a: tp[x, y] = (int(r * 0.5), int(g * 0.5), int(b * 0.5), a)
        t.save(CH + f'{key}_details_torn{i}.png')

KEYS = ['ultima', 'kaeru', 'kyrie', 'lexy', 'max', 'sam', 'technica', 'aioni', 'stevie', 'garry', 'yuri', 'zane']
if __name__ == '__main__':
    for k in (sys.argv[1:] or KEYS):
        build(k); print('damage:', k)
