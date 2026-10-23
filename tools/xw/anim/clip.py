"""Rough clip checker for Rhalix's animations: samples poses, finds arms/legs sinking into the torso, head or each other."""
import json, math, sys
import numpy as np
RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
ANIM = json.load(open(RES + 'animations/rhalix.animation.json'))['animations']
PIV = {'head': (0, 0, 0), 'body': (0, 0, 0), 'right_arm': (-5, 2, 0), 'left_arm': (5, 2, 0), 'right_leg': (-1.9, 12, 0), 'left_leg': (1.9, 12, 0)}
SKIN = {'head': [(-4, -8, -4, 8, 8, 8)], 'body': [(-4, 0, -2, 8, 12, 4)], 'right_arm': [(-3, -2, -2, 4, 12, 4)],
        'left_arm': [(-1, -2, -2, 4, 12, 4)], 'right_leg': [(-2, 0, -2, 4, 12, 4)], 'left_leg': [(-2, 0, -2, 4, 12, 4)]}

def R(x, y, z):
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]); Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx

def sample(track, t):
    keys = sorted((float(k), v['vector'] if isinstance(v, dict) else v) for k, v in track.items()) if isinstance(track, dict) else [(0, track)]
    if t <= keys[0][0]: return keys[0][1]
    for (t0, a), (t1, b) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return [a[i] + (b[i] - a[i]) * f for i in range(3)]
    return keys[-1][1]

PARENTED = False

def pose_of(name, t):
    a = ANIM[name]; out = {}
    for b, tr in a.get('bones', {}).items():
        rot = tr.get('rotation'); pos = tr.get('position')
        r = sample(rot, t) if rot is not None else [0, 0, 0]
        p = sample(pos, t) if pos is not None else [0, 0, 0]
        out[b] = ([-math.radians(r[0]), -math.radians(r[1]), math.radians(r[2])], [-p[0], -p[1], p[2]])
    if PARENTED and 'body' in out:
        # the arms ride on the body: shoulders swing round with its twist and lean, and the arms turn with it
        br = out['body'][0]
        Rb = R(*br)
        for arm, sx in (('right_arm', -5), ('left_arm', 5)):
            rot, pos = out.get(arm, ([0, 0, 0], [0, 0, 0]))
            sh = Rb @ np.array([sx, 2, 0]) - np.array([sx, 2, 0])
            out[arm] = ([rot[0], rot[1], rot[2]], [pos[0] + sh[0], pos[1] + sh[1], pos[2] + sh[2]])
    return out

def collect(model, bone):
    """(box, local transform chain) for skin + detail cubes of a bone, skipping hair/wings/face."""
    boxes = [(np.eye(3), np.zeros(3), b) for b in SKIN[bone]]
    node = model['bones'][bone]
    def walk(nd, M, T):
        for c in nd.get('cubes', []):
            f, s = c['from'], c['size']; boxes.append((M, T, (f[0], f[1], f[2], s[0], s[1], s[2])))
        for ch in nd.get('children', []):
            if ch.get('name') in ('hair', 'bangs', 'right_wing', 'left_wing', 'hood_up', 'hood_down') or ch.get('name', '').startswith('coat'): continue
            M2 = M @ R(*ch.get('rot', [0, 0, 0])); T2 = M @ np.array(ch['pivot'], float) + T
            walk(ch, M2, T2)
    walk(node, np.eye(3), np.zeros(3))
    return boxes

def world_points(model, bone, pose, step=0.75):
    rot, pos = pose.get(bone, ([0, 0, 0], [0, 0, 0]))
    Rb = R(*rot); Tb = np.array(PIV[bone], float) + np.array(pos, float)
    pts = []
    for M, T, (x, y, z, w, h, d) in collect(model, bone):
        if w * h * d < 0.05: continue
        xs = np.arange(x + step / 2, x + w, step) if w > step else [x + w / 2]
        ys = np.arange(y + step / 2, y + h, step) if h > step else [y + h / 2]
        zs = np.arange(z + step / 2, z + d, step) if d > step else [z + d / 2]
        g = np.array(np.meshgrid(xs, ys, zs, indexing='ij')).reshape(3, -1).T
        pts.append((g @ M.T + T) @ Rb.T + Tb)
    return np.vstack(pts) if pts else np.zeros((0, 3))

def boxes_world(model, bone, pose, shrink=0.6):
    """Oriented boxes (center, axes, half sizes) shrunk a little - only deep penetration counts."""
    rot, pos = pose.get(bone, ([0, 0, 0], [0, 0, 0]))
    Rb = R(*rot); Tb = np.array(PIV[bone], float) + np.array(pos, float)
    out = []
    for M, T, (x, y, z, w, h, d) in collect(model, bone):
        if min(w, h, d) < 0.3: continue
        c = np.array([x + w / 2, y + h / 2, z + d / 2])
        out.append(((Rb @ (M @ c + T)) + Tb, Rb @ M, np.maximum(np.array([w, h, d]) / 2 - shrink, 0.05)))
    return out

def inside(pts, boxes):
    hit = np.zeros(len(pts), bool)
    for c, A, hs in boxes:
        loc = (pts - c) @ A
        hit |= np.all(np.abs(loc) < hs, axis=1)
    return hit

PAIRS = [('right_arm', ['body', 'head']), ('left_arm', ['body', 'head']), ('right_leg', ['left_leg']), ('right_arm', ['left_arm'])]

def check(model, pose):
    res = {}
    for a, targets in PAIRS:
        pts = world_points(model, a, pose)
        bx = sum([boxes_world(model, t, pose) for t in targets], [])
        frac = inside(pts, bx).mean() if len(pts) else 0
        if frac > 0.04: res[a + '>' + '+'.join(targets)] = round(float(frac), 2)
    return res

if __name__ == '__main__':
    outfits = sys.argv[1:] or ['rhalix', 'rhalix_origins1', 'rhalix_origins2', 'rhalix_s3']
    for o in outfits:
        model = json.load(open(RES + f'models/detail/{o}.json'))
        print('==', o)
        for name, a in ANIM.items():
            L = float(a.get('animation_length', 1))
            bad = []
            for i in range(25):
                t = L * i / 24
                r = check(model, pose_of(name, t))
                if r: bad.append((round(t, 2), r))
            if bad:
                worst = max(bad, key=lambda b: max(b[1].values()))
                print(f'  {name}: {len(bad)}/25 frames, worst t={worst[0]} {worst[1]}')
