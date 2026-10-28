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
