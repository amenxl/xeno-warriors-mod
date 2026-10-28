"""
Epic Fight animations for Xeno Warriors characters, written from scratch: each animation is a few key poses
(per-joint rotations in the joint's own axes, plus a root offset) turned into Epic Fight's joint-matrix JSON.

  python3 tools/xw/ef/ef_anims.py            -> src/main/resources/assets/xenowarriors/animmodels/animations/biped/...

Axis conventions (from the calibration run, focus 'efcal'): see CONVENTIONS below.
"""
import json, math, os, sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/xenowarriors/animmodels/animations"

# Epic Fight biped rest pose: joint -> local 4x4 (row-major, translation in the last column)
REST = {
 "Root":      [1,0,0,0, 0,0,-1,0.001, 0,1,0,0.764, 0,0,0,1],
 "Thigh_R":   [1,0,0,0.125, 0,-1,0,-0.003, 0,0,-1,0, 0,0,0,1],
 "Leg_R":     [1,0,0,0, 0,1,0,0.375, 0,0,1,0, 0,0,0,1],
 "Knee_R":    [1,0,0,0, 0,0,-1,0.375, 0,1,0,0, 0,0,0,1],
 "Thigh_L":   [1,0,0,-0.125, 0,-1,0,-0.003, 0,0,-1,0, 0,0,0,1],
 "Leg_L":     [1,0,0,0, 0,1,0,0.375, 0,0,1,0, 0,0,0,1],
 "Knee_L":    [1,0,0,0, 0,0,-1,0.375, 0,1,0,0, 0,0,0,1],
 "Torso":     [1,0,0,0, 0,1,0,0.05, 0,0,1,0, 0,0,0,1],
 "Chest":     [1,0,0,0, 0,1,0,0.3, 0,0,1,0, 0,0,0,1],
 "Head":      [1,0,0,0, 0,1,0,0.4, 0,0,1,0, 0,0,0,1],
 "Shoulder_R":[0,0.952,0.306,0, 0,-0.306,0.952,0.4, 1,0,0,0, 0,0,0,1],
 "Arm_R":     [0,0,-1,0, 0.952,0.306,0,0.394, 0.306,-0.952,0,0, 0,0,0,1],
 "Hand_R":    [1,0,0,0, 0,0.994,0.11,0.3, 0,-0.11,0.994,0, 0,0,0,1],
 "Tool_R":    [1,0,0,0, 0,-1,0.018,0.273, 0,-0.018,-0.999,0, 0,0,0,1],
 "Elbow_R":   [-1,0,0,0, 0,0,-1,0.3, 0,-1,0,0, 0,0,0,1],
 "Shoulder_L":[0,-0.952,-0.306,0, 0,-0.306,0.952,0.4, -1,0,0,0, 0,0,0,1],
 "Arm_L":     [0,0,1,0, -0.952,0.306,0,0.394, -0.306,-0.952,0,0, 0,0,0,1],
 "Hand_L":    [1,0,0,0, 0,0.994,0.11,0.3, 0,-0.11,0.994,0, 0,0,0,1],
 "Tool_L":    [1,0,0,0, 0,-1,0.018,0.273, 0,-0.018,-0.999,0, 0,0,0,1],
 "Elbow_L":   [-1,0,0,0, 0,0,-1,0.3, 0,-1,0,0, 0,0,0,1],
}

def mat(a): return [a[0:4], a[4:8], a[8:12], a[12:16]]
def flat(m): return [round(v, 6) for r in m for v in r]
def mul(a, b): return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]
def rot(x, y, z):
    x, y, z = map(math.radians, (x, y, z))
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    Rx = [[1,0,0,0],[0,cx,-sx,0],[0,sx,cx,0],[0,0,0,1]]
    Ry = [[cy,0,sy,0],[0,1,0,0],[-sy,0,cy,0],[0,0,0,1]]
    Rz = [[cz,-sz,0,0],[sz,cz,0,0],[0,0,1,0],[0,0,0,1]]
    return mul(mul(Rz, Ry), Rx)

def joint_matrix(name, r=(0, 0, 0), t=(0, 0, 0)):
    m = mul(mat(REST[name]), rot(*r))
    m[0][3] += t[0]; m[1][3] += t[1]; m[2][3] += t[2]
    return flat(m)

def anim(keys):
    """keys: list of (time, {joint: (rx, ry, rz)} , {"Root": (tx,ty,tz)}) -> EF json"""
    out = []
    for j in REST:
        times, mats = [], []
        for (t, rots, trans) in keys:
            times.append(round(t, 4))
            mats.append(joint_matrix(j, rots.get(j, (0, 0, 0)), trans.get(j, (0, 0, 0))))
        out.append({"name": j, "time": times, "transform": mats})
    return {"animation": out}

def write(path, data):
    f = os.path.join(OUT, path + ".json")
    os.makedirs(os.path.dirname(f), exist_ok=True)
    json.dump(data, open(f, "w"))
    print("wrote", f)

# ---- calibration: one joint/axis at a time, +60 (or +30) at the half-second marks
CAL = [("Arm_R", (60, 0, 0)), ("Arm_R", (0, 0, 60)), ("Arm_R", (0, 60, 0)), ("Chest", (30, 0, 0)), ("Chest", (0, 45, 0)),
       ("Root", (0, 90, 0)), ("Thigh_R", (60, 0, 0)), ("Leg_R", (60, 0, 0)), ("Elbow_R", (60, 0, 0)), ("Hand_R", (0, 0, 60))]
keys = []
for i, (j, r) in enumerate(CAL):
    keys += [(i * 1.0, {}, {}), (i * 1.0 + 0.3, {j: r}, {}), (i * 1.0 + 0.7, {j: r}, {})]
keys.append((len(CAL) * 1.0, {}, {}))
write("biped/xw/xw_cal", anim(keys))

# ---------------------------------------------------------------- semantic poses -> joint rotations
# CONVENTIONS: (axis index, sign) per motion, checked against the calibration run.
CONV = {
    # worked out from Epic Fight's own animations (kneel, sneak, float, climb, dig, crossbow, dash):
    "arm_fwd": (0, +1),      # raise an arm forward (climb/dig: Arm x +85..125)
    "arm_out_R": (2, -1),    # right arm out to the side (float: Arm_R z -60)
    "arm_out_L": (2, +1),    #                           (float: Arm_L z +60)
    "elbow": (0, +1),        # bend the elbow - on the Hand joint (crossbow/fist: Hand x +60..110)
    "chest_bend": (0, -1),   # lean forward (sneak: Chest x -23)
    "chest_twist": (1, +1),  # turn the shoulders
    "root_turn": (1, +1),    # whole body turns
    "thigh_fwd": (0, +1),    # leg forward (kneel: front Thigh x +78)
    "knee": (0, -1),         # bend the knee (kneel: Leg x -80..-96)
}
# root offset: parent-space y is forward (dash moves +y), z is up

def P(arm_r=0, arm_l=0, out_r=0, out_l=0, el_r=0, el_l=0, bend=0, twist=0, turn=0, th_r=0, th_l=0, kn_r=0, kn_l=0, drop=0.0, fwd=0.0):
    rots = {}
    def add(j, motion, deg):
        if not deg: return
        ax, sg = CONV[motion]
        r = list(rots.get(j, (0, 0, 0))); r[ax] += sg * deg; rots[j] = tuple(r)
    add("Arm_R", "arm_fwd", arm_r); add("Arm_L", "arm_fwd", arm_l)
    add("Arm_R", "arm_out_R", out_r); add("Arm_L", "arm_out_L", out_l)
    add("Hand_R", "elbow", el_r); add("Hand_L", "elbow", el_l)
    add("Chest", "chest_bend", bend); add("Chest", "chest_twist", twist)
    add("Root", "root_turn", turn)
    add("Thigh_R", "thigh_fwd", th_r); add("Thigh_L", "thigh_fwd", th_l)
    add("Leg_R", "knee", kn_r); add("Leg_L", "knee", kn_l)
    trans = {"Root": (0, fwd, -drop)} if (drop or fwd) else {}
    return rots, trans

def seq(*frames):
    return anim([(t, *P(**kw)) for t, kw in frames])

GUARD = dict(arm_r=40, arm_l=40, el_r=40, el_l=40, out_r=10, out_l=10)
# Mikiko - Power Pole 1: wind up to the right, a full spin with the pole held out flat, back on guard
write("biped/combat/xw_pole1", seq(
    (0.00, GUARD),
    (0.15, dict(arm_r=70, arm_l=70, out_r=30, out_l=-10, twist=-60, turn=-20, th_r=10, kn_r=20, kn_l=20, drop=0.05)),
    (0.30, dict(arm_r=90, arm_l=90, out_r=45, out_l=-20, twist=10, turn=90, kn_r=15, kn_l=15, drop=0.04)),
    (0.42, dict(arm_r=90, arm_l=90, out_r=45, out_l=-20, twist=40, turn=200, kn_r=15, kn_l=15, drop=0.04)),
    (0.55, dict(arm_r=85, arm_l=85, out_r=40, out_l=-15, twist=30, turn=330, kn_r=15, kn_l=15, drop=0.04)),
    (0.75, dict(GUARD, turn=360)),
    (0.90, dict(GUARD, turn=360))))
# Power Pole 2: twirl it overhead, leaning back, then slam it down with a deep bend
write("biped/combat/xw_pole2", seq(
    (0.00, GUARD),
    (0.20, dict(arm_r=170, arm_l=170, el_r=10, el_l=10, bend=-15, th_l=15, kn_l=10)),
    (0.38, dict(arm_r=175, arm_l=175, el_r=5, el_l=5, bend=-20, twist=25, th_l=20, kn_l=10)),
    (0.52, dict(arm_r=60, arm_l=60, el_r=5, el_l=5, bend=35, th_l=40, kn_l=50, kn_r=30, drop=0.18, fwd=0.15)),
    (0.70, dict(arm_r=55, arm_l=55, bend=30, th_l=40, kn_l=50, kn_r=30, drop=0.18, fwd=0.15)),
    (0.95, GUARD)))
# Power Pole 3: coil back, then a spinning lunge thrust
write("biped/combat/xw_pole3", seq(
    (0.00, GUARD),
    (0.18, dict(arm_r=50, arm_l=30, el_r=90, el_l=60, twist=-45, th_r=-20, kn_l=25, drop=0.06)),
    (0.30, dict(arm_r=90, arm_l=85, el_r=0, el_l=10, twist=20, turn=45, th_l=45, th_r=-25, kn_l=40, kn_r=10, drop=0.15, fwd=0.35)),
    (0.45, dict(arm_r=92, arm_l=88, twist=25, turn=90, th_l=45, th_r=-25, kn_l=40, kn_r=10, drop=0.15, fwd=0.4)),
    (0.70, dict(GUARD, turn=90)),
    (0.85, dict(GUARD, turn=90))))
HEAVY = dict(arm_r=35, arm_l=35, el_r=50, el_l=50, out_r=5, out_l=15, twist=-20)
# Rhalix - Z Sword 1: low on the left, a rising cleave up to the right
write("biped/combat/xw_cleave1", seq(
    (0.00, HEAVY),
    (0.25, dict(arm_r=10, arm_l=20, el_r=20, el_l=20, out_l=40, twist=-60, bend=20, kn_r=30, kn_l=30, drop=0.12)),
    (0.45, dict(arm_r=150, arm_l=140, el_r=10, el_l=10, out_r=40, twist=40, bend=-15, th_r=20, kn_l=10)),
    (0.60, dict(arm_r=160, arm_l=150, out_r=50, twist=50, bend=-20, th_r=20)),
    (0.95, HEAVY)))
# Z Sword 2: heave it up, crouch, and bring it down like a falling star
write("biped/combat/xw_cleave2", seq(
    (0.00, HEAVY),
    (0.30, dict(arm_r=175, arm_l=175, el_r=30, el_l=30, bend=-20, kn_r=25, kn_l=25, drop=0.1)),
    (0.50, dict(arm_r=180, arm_l=180, el_r=20, el_l=20, bend=-25, kn_r=35, kn_l=35, drop=0.15)),
    (0.62, dict(arm_r=50, arm_l=50, bend=45, th_l=45, kn_l=70, kn_r=60, drop=0.35, fwd=0.2)),
    (0.85, dict(arm_r=45, arm_l=45, bend=40, th_l=45, kn_l=70, kn_r=60, drop=0.35, fwd=0.2)),
    (1.15, HEAVY)))
