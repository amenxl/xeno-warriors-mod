"""Turns recorded animations (the "packbake" test's XW-BAKE log lines) into Epic Fight animations.

python3 -I tools/xw/ef/bake.py <client.log> [out_dir] [--only name,name]

A recording is our own animation as it really plays: every frame, the vanilla model's part rotations (head, body,
arms, legs), the elbow / knee bends and the whole-body motion (lift, forward, spin, lean, roll, barrel roll).
Each frame is rebuilt on Epic Fight's biped skeleton:
  - vanilla model space (x left-of-screen = the body's left... y down, -z forward) -> Epic Fight armature space
    (x the body's right, y forward, z up):  E = C V,  C = [[-1,0,0],[0,0,-1],[0,-1,0]];  a rotation R -> C R C^T
  - each part's rotation is absolute in the vanilla model (all parts are siblings), so a joint's posed world
    orientation is  root * part * rest,  and its local matrix is  inverse(parent world) * world  (rest translation)
  - forearm = arm * Rx(elbow bend), shin = leg * Rx(knee bend), torso = body
  - whole-body motion turns the root about a point 0.9 blocks up (as our renderer does)
Written to <out_dir>/biped/xw/<name>.json (default: the mod's animmodels/animations).
"""
import json, math, os, re, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', '..', 'src/main/resources/assets/xenowarriors/animmodels/animations')

src = open(os.path.join(HERE, 'ef_anims.py')).read()
REST = {}
exec(src[src.index('REST = {'):src.index('def mat(')], {}, g := {})
REST = {k: np.array(v, float).reshape(4, 4) for k, v in g['REST'].items()}
PARENT = {"Root": None, "Thigh_R": "Root", "Leg_R": "Thigh_R", "Knee_R": "Leg_R", "Thigh_L": "Root", "Leg_L": "Thigh_L",
          "Knee_L": "Leg_L", "Torso": "Root", "Chest": "Torso", "Head": "Chest", "Shoulder_R": "Chest", "Arm_R": "Shoulder_R",
          "Hand_R": "Arm_R", "Tool_R": "Hand_R", "Elbow_R": "Arm_R", "Shoulder_L": "Chest", "Arm_L": "Shoulder_L",
          "Hand_L": "Arm_L", "Tool_L": "Hand_L", "Elbow_L": "Arm_L"}
ORDER = list(REST.keys())

C = np.array([[-1, 0, 0], [0, 0, -1], [0, -1, 0]], float)      # vanilla model space -> armature space
D = np.array([[-1, 0, 0], [0, 0, 1], [0, 1, 0]], float)        # entity-facing world (y up, +z forward) -> armature


def Rx(a): c, s = math.cos(a), math.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
def Ry(a): c, s = math.cos(a), math.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
def Rz(a): c, s = math.cos(a), math.sin(a); return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
def zyx(x, y, z): return Rz(z) @ Ry(y) @ Rx(x)          # ModelPart.translateAndRotate: rotationZYX(z, y, x)


REST_W = {}
def rest_world(j):
    if j in REST_W: return REST_W[j]
    p = PARENT[j]
    REST_W[j] = REST[j] if p is None else rest_world(p) @ REST[j]
    return REST_W[j]
for j in ORDER: rest_world(j)


def frame_locals(f):
    """One recorded frame -> {joint: 4x4 local matrix}."""
    t = f[0]
    head, body, ra, la, rl, ll = (f[1 + 3 * i: 4 + 3 * i] for i in range(6))
    er, el, kr, kl = f[19:23]
    motion = f[24:30] if len(f) >= 30 else [0] * 6
    lift, fwd, spin, lean, roll, barrel = motion
    E = lambda R: C @ R @ C.T
    delta = None
    if len(f) >= 46:                                       # the render pose change, recorded as is (4x4 column-major)
        delta = np.array(f[30:46], float).reshape(4, 4).T
        if np.allclose(delta, np.eye(4), atol=1e-5): delta = None
    if delta is not None:
        Rroot = D @ delta[:3, :3] @ D.T
    else:
        # whole-body motion about (0, 0.9, 0) in the entity-facing world
        Rw = Ry(math.radians(spin)) @ Rx(math.radians(lean)) @ Ry(math.radians(barrel)) @ Rz(math.radians(roll))
        Rroot = D @ Rw @ D.T
    part = {
        'Torso': zyx(*body), 'Head': zyx(*head),
        'Arm_R': zyx(*ra), 'Hand_R': zyx(*ra) @ Rx(er),
        'Arm_L': zyx(*la), 'Hand_L': zyx(*la) @ Rx(el),
        'Thigh_R': zyx(*rl), 'Leg_R': zyx(*rl) @ Rx(kr),
        'Thigh_L': zyx(*ll), 'Leg_L': zyx(*ll) @ Rx(kl),
    }
    W = {}
    out = {}
    for j in ORDER:                                         # (parents come before children in ORDER)
        p = PARENT[j]
        rest_rot = rest_world(j)[:3, :3]
        if j == 'Root':
            pr = rest_world('Root')[:3, 3]
            if delta is not None:
                pos = D @ (delta[:3, :3] @ (D.T @ pr) + delta[:3, 3])
            else:
                c = np.array([0, 0, 0.9])
                pos = c + Rroot @ (pr - c) + np.array([0, fwd, lift])
            M = np.eye(4); M[:3, :3] = Rroot @ rest_rot; M[:3, 3] = pos
            W[j] = M
            out[j] = M
            continue
        if j in part:
            rot_w = Rroot @ E(part[j]) @ rest_rot
        else:
            rot_w = W[p][:3, :3] @ REST[j][:3, :3]              # follows its parent as at rest
        local = np.eye(4)
        local[:3, :3] = W[p][:3, :3].T @ rot_w
        local[:3, 3] = REST[j][:3, 3]
        W[j] = W[p] @ local
        out[j] = local
    return t, out


def to_ef(frames, fps_ticks=20.0):
    keys = [frame_locals(f) for f in frames]
    t0 = keys[0][0]
    anim = []
    for j in ORDER:
        anim.append({"name": j, "time": [round((t - t0) / fps_ticks, 4) for t, _ in keys],
                     "transform": [[round(float(v), 5) for v in m[j].reshape(-1)] for _, m in keys]})
    return {"animation": anim}


def parse(log):
    out = {}
    for line in open(log, errors='replace'):
        m = re.search(r'XW-BAKE (\S+) (\d+) (.*)$', line)
        if not m: continue
        frames = [list(map(float, x.split(','))) for x in re.findall(r'\[([^\]]*)\]', m.group(3))]
        if frames: out[m.group(1)] = frames
    return out


if __name__ == '__main__':
    log = sys.argv[1]
    out_dir = sys.argv[2] if len(sys.argv) > 2 and not sys.argv[2].startswith('--') else OUT
    only = None
    if '--only' in sys.argv: only = set(sys.argv[sys.argv.index('--only') + 1].split(','))
    recs = parse(log)
    for name, frames in recs.items():
        if only and name not in only: continue
        path = os.path.join(out_dir, 'biped/xw', name + '.json')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        json.dump(to_ef(frames), open(path, 'w'), separators=(',', ':'))
        print(f'{name}: {len(frames)} frames -> {path}')
