"""Custom armor for each character, generated so every plate sits OUTSIDE their clothes and hair.

For each plate we measure how far that character's clothes/hair/skin reach in that region and put
the plate just beyond it. Plates are grey; the mod tints them by armor material (leather dye, iron,
gold, diamond, netherite...). One JSON per armor slot, one shared texture per character.
"""
import json, math, sys
sys.path.insert(0, '/home/claude/xw/detail')
from core import Model, pack_and_paint
from export_mod import box, rel, rot, BONES

FORGE = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
GAP, T = 0.14, 0.5                     # air gap off the clothes, plate thickness
BASE, LIGHT, DARK, TRIM, GEM = '#cfcfcf', '#f4f4f4', '#8c8c8c', '#adadad', '#ffffff'

# skin + overlay of the vanilla player model, in Blockbench space
SKIN = [('Head', [-4.5, 23.5, -4.5], [4.5, 32.5, 4.5]), ('Body', [-4.25, 11.75, -2.25], [4.25, 24.25, 2.25]),
        ('RightArm', [3.75, 11.75, -2.25], [8.25, 24.25, 2.25]), ('LeftArm', [-8.25, 11.75, -2.25], [-3.75, 24.25, 2.25]),
        ('RightLeg', [-0.35, -0.25, -2.25], [4.15, 12.25, 2.25]), ('LeftLeg', [-4.15, -0.25, -2.25], [0.35, 12.25, 2.25])]

def rotm(r):
    x, y, z = [math.radians(a) for a in r]
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]; Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]; Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    mul = lambda A, B: [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mul(Rz, mul(Ry, Rx))

def aabb(c):
    lo, hi, o = c['frm'], c['to'], c['origin']
    if not any(abs(a) > 1e-6 for a in c['rot']):
        return lo, hi
    M = rotm(c['rot']); pts = []
    for x in (lo[0], hi[0]):
        for y in (lo[1], hi[1]):
            for z in (lo[2], hi[2]):
                v = [x - o[0], y - o[1], z - o[2]]
                pts.append([sum(M[i][k] * v[k] for k in range(3)) + o[i] for i in range(3)])
    return [min(p[i] for p in pts) for i in range(3)], [max(p[i] for p in pts) for i in range(3)]

class Fit:
    def __init__(self, name):
        spec = json.load(open(f'/home/claude/xw/detail/{name}_spec.json'))
        self.boxes = [(c['bone'],) + tuple(aabb(c)) for c in spec['cubes'] if c['bone'] in Model.BONES]
        self.boxes += [(b, lo, hi) for b, lo, hi in SKIN]

    def reach(self, axis, sign, region, bones=None):
        """Furthest point along axis*sign of anything overlapping region {axis_index: (lo, hi)}."""
        best = -99
        for bone, lo, hi in self.boxes:
            if bones and bone not in bones:
                continue
            if all(hi[a] > r[0] and lo[a] < r[1] for a, r in region.items()):
                best = max(best, hi[axis] if sign > 0 else -lo[axis])
        return best

def build(name):
    f = Fit(name)
    m = Model(name + '_armor')
    X, Y, Z = 0, 1, 2

    # ================= HELMET: a crown helm riding on top of the hair
    top = f.reach(Y, 1, {X: (-3, 3), Z: (-3, 3)}, ['Head'])
    band = (top - 2.1, top - 0.5)
    fr = f.reach(Z, -1, {X: (-3, 3), Y: band}, ['Head']) + GAP
    bk = f.reach(Z, 1, {X: (-3, 3), Y: band}, ['Head']) + GAP
    sd = max(f.reach(X, 1, {Z: (-3, 3), Y: band}, ['Head']), f.reach(X, -1, {Z: (-3, 3), Y: band}, ['Head'])) + GAP
    y0, y1 = band
    H = lambda n, a, b, c, sh='edge', r=None, o=None: m.add('Head', 'H:' + n, a, b, c, r, o, sh)
    H('Band Front', [-sd - T, y0, -fr - T], [sd + T, y1, -fr], BASE)
    H('Band Back', [-sd - T, y0, bk], [sd + T, y1, bk + T], BASE)
    # side guards hanging down over the hair
    gy0 = y0 - 2.6
    sg = max(f.reach(X, 1, {Z: (-2.5, 1.5), Y: (gy0, y1)}, ['Head']), f.reach(X, -1, {Z: (-2.5, 1.5), Y: (gy0, y1)}, ['Head'])) + GAP
    for s in (1, -1):
        H('Band Side', [min(s * sd, s * (sd + T)), y0, -fr], [max(s * sd, s * (sd + T)), y1, bk], BASE)
        g0, g1 = sorted([s * max(sd, sg), s * (max(sd, sg) + T)])
        H('Side Guard', [g0, gy0, -2.4], [g1, y0, 1.4], BASE)
        H('Side Guard Rim', [min(g0, g1) - 0.02, gy0, -2.6], [max(g0, g1) + 0.02, gy0 + 0.4, 1.6], DARK)
        r0, r1 = sorted([s * (max(sd, sg) + T), s * (max(sd, sg) + T + 0.3)])
        H('Side Rivet', [r0, y0 - 1.4, -0.9], [r1, y0 - 0.6, -0.1], LIGHT, None)
    H('Band Trim', [-sd - T - 0.05, y0 - 0.25, -fr - T - 0.08], [sd + T + 0.05, y0, -fr - T + 0.3], DARK)
    H('Cap', [-sd + 0.4, top + GAP, -fr + 0.5], [sd - 0.4, top + GAP + 0.45, bk - 0.5], BASE)
    H('Cap Ridge', [-0.4, top + GAP + 0.45, -fr + 0.3], [0.4, top + GAP + 0.85, bk - 0.3], LIGHT)
    H('Crest', [-0.35, y0, -fr - T - 0.25], [0.35, top + 1.5, -fr - T + 0.15], LIGHT)
    H('Gem', [-0.55, y0 + 0.15, -fr - T - 0.4], [0.55, y1 - 0.1, -fr - T - 0.2], GEM, None)

    # ================= CHESTPLATE: breastplate, back plate, pauldrons, bracers
    yb0, yb1 = 17.4, 23.3
    cf = f.reach(Z, -1, {X: (-3.4, 3.4), Y: (yb0, yb1)}, ['Body']) + GAP
    cb = f.reach(Z, 1, {X: (-3.4, 3.4), Y: (16.2, yb1)}, ['Body']) + GAP
    C = lambda bone, n, a, b, c, sh='edge', r=None, o=None: m.add(bone, 'C:' + n, a, b, c, r, o, sh)
    C('Body', 'Breastplate', [-3.4, yb0, -cf - T], [3.4, yb1, -cf], BASE)
    C('Body', 'Breast Ridge', [-0.35, yb0 + 0.3, -cf - T - 0.22], [0.35, yb1 - 0.3, -cf - T], LIGHT)
    C('Body', 'Breast Trim', [-3.45, yb0 - 0.35, -cf - T - 0.1], [3.45, yb0, -cf + 0.1], DARK)
    for s in (1, -1):
        x0, x1 = sorted([s * 0.7, s * 3.0])
        C('Body', 'Pec Plate', [x0, 20.4, -cf - T - 0.2], [x1, 22.6, -cf - T], TRIM)
    C('Body', 'Back Plate', [-3.4, 16.2, cb], [3.4, yb1, cb + T], BASE)
    C('Body', 'Back Ridge', [-0.35, 16.5, cb + T], [0.35, yb1 - 0.3, cb + T + 0.2], LIGHT)
    for bone, s in (('RightArm', 1), ('LeftArm', -1)):
        # one outline per arm section (furthest the sleeve/glove/pad reaches on every side), so the plates sit
        # evenly around the arm instead of each being placed on its own
        def box(y0, y1):
            reg = {Y: (y0, y1), Z: (-2.4, 2.4)}
            out = f.reach(X, s, reg, [bone]) + GAP
            inn = f.reach(X, -s, reg, [bone]) + GAP
            fz = f.reach(Z, -1, {Y: (y0, y1), X: sorted([s * 3.6, s * 8.6])}, [bone]) + GAP
            bz = f.reach(Z, 1, {Y: (y0, y1), X: sorted([s * 3.6, s * 8.6])}, [bone]) + GAP
            return out, inn, fz, bz
        A = lambda n, x0, x1, y0, y1, z0, z1, c, sh='edge': m.add(bone, 'C:' + n, [min(x0, x1), y0, z0], [max(x0, x1), y1, z1], c, None, None, sh)
        TA = 0.4    # arm plate thickness

        # ---- shoulder guard: top plate, a cap that wraps front/back/outside, and two lames below it
        out, inn, fz, bz = box(19.5, 24.6)
        top = f.reach(Y, 1, {X: sorted([s * 3.6, s * 8.6]), Z: (-2.4, 2.4)}, [bone]) + GAP
        xin = -s * (inn - 0.9)                       # stops short of the body side
        A('Pauldron Top', xin, s * (out + TA), top, top + TA, -fz - TA, bz + TA, BASE)
        A('Pauldron Cap Out', s * out, s * (out + TA), top - 1.3, top + TA, -fz - TA, bz + TA, BASE)
        A('Pauldron Cap Front', xin, s * (out + TA), top - 1.3, top, -fz - TA, -fz, BASE)
        A('Pauldron Cap Back', xin, s * (out + TA), top - 1.3, top, bz, bz + TA, BASE)
        A('Pauldron Rim', s * (out - 0.2), s * (out + TA + 0.08), top + TA - 0.15, top + TA + 0.15, -fz - TA - 0.06, bz + TA + 0.06, LIGHT, None)
        for i, (ya, yb) in enumerate(((top - 2.4, top - 1.2), (top - 3.4, top - 2.3))):
            g = TA + 0.18 * (i + 1)                  # each lame steps out a little further
            A('Pauldron Lame Out', s * (out + g - TA), s * (out + g), ya, yb, -fz - g + 0.2, bz + g - 0.2, TRIM)
            A('Pauldron Lame Front', -s * (inn - 1.4), s * (out + g), ya, yb, -fz - g + 0.2, -fz - g + 0.2 + TA, TRIM)
            A('Pauldron Lame Back', -s * (inn - 1.4), s * (out + g), ya, yb, bz + g - 0.2 - TA, bz + g - 0.2, TRIM)
        A('Pauldron Rivet', s * (out + TA), s * (out + TA + 0.15), top - 0.9, top - 0.4, -0.3, 0.3, LIGHT, None)

        # ---- vambrace: a full sleeve around the forearm with raised rims at both ends
        out, inn, fz, bz = box(12.9, 17.3)
        y0, y1 = 13.1, 17.1
        A('Vambrace Front', -s * inn, s * out, y0, y1, -fz - TA, -fz, BASE)
        A('Vambrace Back', -s * inn, s * out, y0, y1, bz, bz + TA, BASE)
        A('Vambrace Out', s * out, s * (out + TA), y0, y1, -fz - TA, bz + TA, BASE)
        A('Vambrace In', -s * inn, -s * (inn + TA), y0, y1, -fz - TA, bz + TA, BASE)
        for ya, yb, col in ((y0, y0 + 0.45, DARK), (y1 - 0.45, y1, LIGHT)):
            e = 0.12
            A('Vambrace Rim Front', -s * (inn + e), s * (out + e), ya, yb, -fz - TA - e, -fz - TA + 0.1, col, None)
            A('Vambrace Rim Back', -s * (inn + e), s * (out + e), ya, yb, bz + TA - 0.1, bz + TA + e, col, None)
            A('Vambrace Rim Out', s * (out + TA - 0.1), s * (out + TA + e), ya, yb, -fz - TA - e, bz + TA + e, col, None)
            A('Vambrace Rim In', -s * (inn + TA - 0.1), -s * (inn + TA + e), ya, yb, -fz - TA - e, bz + TA + e, col, None)
        A('Vambrace Ridge', s * (out + TA), s * (out + TA + 0.15), y0 + 0.6, y1 - 0.6, -0.25, 0.25, LIGHT, None)

    # ================= LEGGINGS: thigh plates and knee cops
    # ================= BOOTS: greaves and toe caps
    for bone, s in (('RightLeg', 1), ('LeftLeg', -1)):
        lx = sorted([s * 0.15, s * 3.7])
        for slot, (ya, yb), names in (('L', (7.4, 11.7), 'Thigh'), ('B', (1.2, 5.2), 'Greave')):
            P = lambda n, a, b, c, sh='edge': m.add(bone, slot + ':' + n, a, b, c, None, None, sh)
            front = f.reach(Z, -1, {X: lx, Y: (ya, yb)}) + GAP
            out = f.reach(X, s, {Y: (ya, yb), Z: (-2.3, 1.0)}, [bone]) + GAP
            P(names + ' Front', [lx[0], ya, -front - T], [lx[1], yb, -front], BASE)
            P(names + ' Front Ridge', [s * 1.7 - 0.3 if s > 0 else s * 1.7 - 0.3, ya + 0.3, -front - T - 0.2],
              [s * 1.7 + 0.3, yb - 0.3, -front - T], LIGHT)
            o0, o1 = sorted([s * out, s * (out + T)])
            P(names + ' Side', [o0, ya, -front - T + 0.1], [o1, yb, 1.4], BASE)
            P(names + ' Trim', [lx[0] - 0.05, yb - 0.35, -front - T - 0.1], [lx[1] + 0.05, yb, -front + 0.1], DARK)
        # knee cop
        kf = f.reach(Z, -1, {X: lx, Y: (5.3, 7.4)}) + GAP
        m.add(bone, 'L:Knee', [s * 1.7 - 1.3, 5.3, -kf - T - 0.1], [s * 1.7 + 1.3, 7.4, -kf], LIGHT, None, None, 'edge')
        m.add(bone, 'L:Knee Stud', [s * 1.7 - 0.4, 5.9, -kf - T - 0.35], [s * 1.7 + 0.4, 6.8, -kf - T - 0.1], GEM, None, None, None)
        # toe cap
        tf = f.reach(Z, -1, {X: lx, Y: (0, 1.3)}) + GAP
        m.add(bone, 'B:Toe Cap', [lx[0], 0.0, -tf - T], [lx[1], 1.3, -tf], DARK, None, None, 'edge')
        m.add(bone, 'B:Cuff', [lx[0], 5.0, -front - T - 0.15], [lx[1], 5.45, -front + 0.05], LIGHT, None, None, None)
    build_hoodie(f, m)
    build_sr_hoodie(f, m)
    return m

def build_hoodie(f, m):
    """The demon Karma-reducing hoodie: a dark hoodie fitted band by band over the outfit, hood down."""
    X, Y, Z = 0, 1, 2
    CLOTH, CLOTH_D, TRIMC, RED, STRING = '#ececec', '#c2c2c2', '#6a2fa0', '#c4202a', '#d8d0e0'
    def D(bone, n, a, b, c, sh='edge'):
        key = 'D:' if c in (CLOTH, CLOTH_D) else 'E:'      # D = dyed cloth, E = trim/strings/emblem
        m.add(bone, key + n, a, b, c, None, None, sh)
    bands = [(12.0, 13.6), (13.6, 15.2), (15.2, 16.8), (16.8, 18.4), (18.4, 20.0), (20.0, 21.6), (21.6, 23.2), (23.2, 24.3)]
    for i, (y0, y1) in enumerate(bands):
        fz = f.reach(Z, -1, {X: (-4.2, 4.2), Y: (y0, y1)}, ['Body']) + 0.08
        bz = f.reach(Z, 1, {X: (-4.2, 4.2), Y: (y0, y1)}, ['Body']) + 0.08
        col = TRIMC if i == 0 else CLOTH
        sx = max(4.02, f.reach(X, 1, {Y: (y0, y1), Z: (-fz, bz)}, ['Body']) + 0.08,
                 f.reach(X, -1, {Y: (y0, y1), Z: (-fz, bz)}, ['Body']) + 0.08)
        D('Body', 'Front', [-sx - 0.35, y0, -fz - 0.4], [sx + 0.35, y1, -fz], col)
        D('Body', 'Back', [-sx - 0.35, y0, bz], [sx + 0.35, y1, bz + 0.4], col)
        for s in (1, -1):
            x0, x1 = sorted([s * sx, s * (sx + 0.35)])
            D('Body', 'Side', [x0, y0, -fz], [x1, y1, bz], col if i == 0 else CLOTH_D)
        if i == 1:
            D('Body', 'Pocket', [-2.7, y0 + 0.1, -fz - 0.62], [2.7, y1 + 1.4, -fz - 0.4], CLOTH_D)
            D('Body', 'Pocket Trim', [-2.7, y1 + 1.2, -fz - 0.66], [2.7, y1 + 1.4, -fz - 0.4], TRIMC, None)
        if i == 6:
            for s in (1, -1):
                D('Body', 'Drawstring', [s * 0.9 - 0.12, 19.6, -fz - 0.52], [s * 0.9 + 0.12, y1 + 0.6, -fz - 0.4], STRING, None)
                D('Body', 'Drawstring Tip', [s * 0.9 - 0.2, 19.2, -fz - 0.56], [s * 0.9 + 0.2, 19.7, -fz - 0.4], TRIMC, None)
        if i == 4:
            # demon horns and eyes on the back
            for s in (1, -1):
                D('Body', 'Emblem Horn', [s * 1.6 - 0.25, 19.0, bz + 0.4], [s * 1.6 + 0.25, 21.2, bz + 0.5], RED, None)
                D('Body', 'Emblem Horn Tip', [s * 1.85 - 0.2, 21.0, bz + 0.4], [s * 1.85 + 0.2, 21.8, bz + 0.5], RED, None)
                D('Body', 'Emblem Eye', [s * 0.7 - 0.35, 18.0, bz + 0.4], [s * 0.7 + 0.35, 18.5, bz + 0.5], RED, None)
    # hood pulled down, bunched behind the neck, and a collar
    bk = f.reach(Z, 1, {X: (-3.5, 3.5), Y: (21.0, 24.3)}, ['Body']) + 0.08
    D('Body', 'Hood', [-3.4, 21.6, bk + 0.4], [3.4, 25.2, bk + 1.7], CLOTH)
    D('Body', 'Hood Top', [-2.8, 25.2, bk + 0.5], [2.8, 25.8, bk + 1.5], CLOTH_D)
    D('Body', 'Hood Rim', [-3.5, 24.6, bk + 1.6], [3.5, 25.3, bk + 1.85], TRIMC, None)
    fz = f.reach(Z, -1, {X: (-3, 3), Y: (23.0, 24.3)}, ['Body']) + 0.08
    D('Body', 'Collar', [-3.2, 24.0, -fz - 0.5], [3.2, 24.6, -fz], TRIMC)
    # sleeves
    for bone, s in (('RightArm', 1), ('LeftArm', -1)):
        ax = sorted([s * 3.95, s * 8.5])
        for i, (y0, y1) in enumerate([(13.4, 14.4), (14.4, 17.0), (17.0, 19.6), (19.6, 22.2), (22.2, 24.3)]):
            fz = f.reach(Z, -1, {X: ax, Y: (y0, y1)}, [bone]) + 0.08
            bz = f.reach(Z, 1, {X: ax, Y: (y0, y1)}, [bone]) + 0.08
            out = f.reach(X, s, {Z: (-2.3, 2.3), Y: (y0, y1)}, [bone]) + 0.08
            col = TRIMC if i == 0 else CLOTH
            x0, x1 = sorted([s * 4.05, s * out])
            D(bone, 'Sleeve Front', [x0, y0, -fz - 0.35], [x1, y1, -fz], col)
            D(bone, 'Sleeve Back', [x0, y0, bz], [x1, y1, bz + 0.35], col)
            o0, o1 = sorted([s * out, s * (out + 0.35)])
            D(bone, 'Sleeve Side', [o0, y0, -fz - 0.35], [o1, y1, bz + 0.35], col if i == 0 else CLOTH_D)
            inn = f.reach(X, -s, {Z: (-2.3, 2.3), Y: (y0, y1)}, [bone]) + 0.08
            n0, n1 = sorted([-s * inn, -s * (inn + 0.35)])
            D(bone, 'Sleeve Inner', [n0, y0, -fz - 0.35], [n1, y1, bz + 0.35], col if i == 0 else CLOTH_D)
        top = f.reach(Y, 1, {X: ax, Z: (-2.3, 2.3)}, [bone]) + 0.08
        x0, x1 = sorted([s * 4.05, s * (out + 0.35)])
        D(bone, 'Sleeve Top', [x0, top, -fz - 0.35], [x1, top + 0.35, bz + 0.35], CLOTH)

def build_sr_hoodie(f, m):
    """SR's hoodie: pitch black, hood up over the hair, short sleeves with white cuffs, a white eye on the chest."""
    X, Y, Z = 0, 1, 2
    BLK, BLK_D, WHT = '#17151b', '#0e0d11', '#ececec'
    S = lambda bone, n, a, b, c, sh='edge': m.add(bone, 'S:' + n, a, b, c, None, None, sh)
    bands = [(12.0, 13.6), (13.6, 15.2), (15.2, 16.8), (16.8, 18.4), (18.4, 20.0), (20.0, 21.6), (21.6, 23.2), (23.2, 24.3)]
    chest_z = None
    for i, (y0, y1) in enumerate(bands):
        fz = f.reach(Z, -1, {X: (-4.2, 4.2), Y: (y0, y1)}, ['Body']) + 0.08
        bz = f.reach(Z, 1, {X: (-4.2, 4.2), Y: (y0, y1)}, ['Body']) + 0.08
        sx = max(4.02, f.reach(X, 1, {Y: (y0, y1), Z: (-fz, bz)}, ['Body']) + 0.08,
                 f.reach(X, -1, {Y: (y0, y1), Z: (-fz, bz)}, ['Body']) + 0.08)
        S('Body', 'Front', [-sx - 0.35, y0, -fz - 0.4], [sx + 0.35, y1, -fz], BLK)
        S('Body', 'Back', [-sx - 0.35, y0, bz], [sx + 0.35, y1, bz + 0.4], BLK)
        for s in (1, -1):
            x0, x1 = sorted([s * sx, s * (sx + 0.35)])
            S('Body', 'Side', [x0, y0, -fz], [x1, y1, bz], BLK_D)
        if i == 0:   # white hem line on the outside surface only
            S('Body', 'Hem Front', [-sx - 0.38, 12.0, -fz - 0.45], [sx + 0.38, 12.3, -fz - 0.38], WHT, None)
            S('Body', 'Hem Back', [-sx - 0.38, 12.0, bz + 0.38], [sx + 0.38, 12.3, bz + 0.45], WHT, None)
            for s in (1, -1):
                h0, h1 = sorted([s * (sx + 0.33), s * (sx + 0.4)])
                S('Body', 'Hem Side', [h0, 12.0, -fz - 0.38], [h1, 12.3, bz + 0.38], WHT, None)
        if y0 <= 20.4 < y1 or y0 <= 19.8 < y1:
            chest_z = fz if chest_z is None else max(chest_z, fz)
    # the eye on the chest (almond outline + round pupil), drawn in white
    z0, z1 = -chest_z - 0.47, -chest_z - 0.4
    eye = [(-0.9, 0.9, 21.15, 21.4), (-1.55, -0.9, 20.85, 21.1), (0.9, 1.55, 20.85, 21.1), (-2.0, -1.55, 20.35, 20.85),
           (1.55, 2.0, 20.35, 20.85), (-1.55, -0.9, 20.1, 20.35), (0.9, 1.55, 20.1, 20.35), (-0.9, 0.9, 19.8, 20.05),
           (-0.4, 0.4, 20.2, 21.0), (-0.55, 0.55, 20.35, 20.85)]
    for xa, xb, ya, yb in eye:
        S('Body', 'Eye', [xa, ya, z0], [xb, yb, z1], WHT, None)
    # short sleeves with a white cuff
    for bone, s in (('RightArm', 1), ('LeftArm', -1)):
        ax = sorted([s * 3.95, s * 8.5])
        for i, (y0, y1) in enumerate([(19.4, 20.0), (20.0, 22.2), (22.2, 24.3)]):
            fz = f.reach(Z, -1, {X: ax, Y: (y0, y1)}, [bone]) + 0.08
            bz = f.reach(Z, 1, {X: ax, Y: (y0, y1)}, [bone]) + 0.08
            out = f.reach(X, s, {Z: (-2.3, 2.3), Y: (y0, y1)}, [bone]) + 0.08
            col = WHT if i == 0 else BLK
            x0, x1 = sorted([s * 4.05, s * out])
            S(bone, 'Sleeve Front', [x0, y0, -fz - 0.35], [x1, y1, -fz], col)
            S(bone, 'Sleeve Back', [x0, y0, bz], [x1, y1, bz + 0.35], col)
            o0, o1 = sorted([s * out, s * (out + 0.35)])
            S(bone, 'Sleeve Side', [o0, y0, -fz - 0.35], [o1, y1, bz + 0.35], col if i == 0 else BLK_D)
        top = f.reach(Y, 1, {X: ax, Z: (-2.3, 2.3)}, [bone]) + 0.08
        x0, x1 = sorted([s * 4.05, s * (out + 0.35)])
        S(bone, 'Sleeve Top', [x0, top, -fz - 0.35], [x1, top + 0.35, bz + 0.35], BLK)
    # hood up. The hair and goggles tuck under it (the mod hides them while this hoodie is worn,
    # leaving only a few front bangs), so the hood can hug the head like a real hood.
    HK, HR, HS = '#17151b', '#221f28', '#060507'      # cloth, rim, shadow inside the opening
    H = lambda n, a, b, c, sh='edge': m.add('Head', 'S:' + n, a, b, c, None, None, sh)
    for sgn in (1, -1):
        o = lambda a, b: sorted([sgn * a, sgn * b])
        x = o(4.75, 5.3);   H('Hood Side', [x[0], 24.0, -4.4], [x[1], 31.8, 4.75], HK)
        x = o(4.45, 5.0);   H('Hood Side Round', [x[0], 31.8, -4.2], [x[1], 32.6, 4.6], HK)
        x = o(3.4, 5.3);    H('Hood Cheek Rim', [x[0], 24.0, -5.0], [x[1], 30.9, -4.4], HR)
        x = o(3.15, 3.4);   H('Hood Opening Shadow', [x[0], 24.3, -4.46], [x[1], 30.9, -4.4], HS, None)
        x = o(4.75, 5.45);  H('Hood Hem Side', [x[0], 23.6, -4.5], [x[1], 24.2, 5.4], HR)
    H('Hood Back', [-4.75, 23.8, 4.75], [4.75, 31.8, 5.3], HK)
    H('Hood Back Round', [-4.45, 31.8, 4.45], [4.45, 32.6, 5.0], HK)
    H('Hood Hem Back', [-5.45, 23.4, 5.3], [5.45, 24.0, 5.45], HR)
    H('Hood Top', [-4.3, 32.6, -4.3], [4.3, 33.15, 4.5], HK)
    H('Hood Peak', [-1.2, 32.9, 3.6], [1.2, 33.7, 5.2], HK)
    H('Hood Peak Tip', [-0.75, 33.5, 4.4], [0.75, 34.0, 5.4], HR)
    H('Hood Brim', [-5.3, 30.9, -5.0], [5.3, 32.3, -4.4], HR)
    H('Hood Brim Roll', [-4.3, 32.0, -4.75], [4.3, 32.75, -4.2], HK)
    H('Hood Opening Shadow Top', [-3.4, 30.65, -4.46], [3.4, 30.9, -4.4], HS, None)
    if m.name.startswith('mikiko'):
        # his hair stops at the hood: just a short fringe of red dread tips peeking out under the brim
        for x, ln, c in ((-2.3, 0.55, '#b3171d'), (-1.2, 0.85, '#d42026'), (0.0, 0.7, '#b3171d'), (1.15, 0.9, '#d42026'), (2.3, 0.5, '#b3171d')):
            H('Hood Fringe', [x - 0.38, 30.9 - ln, -4.95], [x + 0.38, 30.9, -4.47], c, None)
    # fabric bunched at the back of the neck where the hood meets the hoodie
    nb = f.reach(Z, 1, {X: (-3.6, 3.6), Y: (22.4, 24.4)}, ['Body']) + 0.1
    S('Body', 'Hood Drape', [-3.6, 22.4, nb], [3.6, 24.4, nb + 0.7], BLK)

def export(m, char):
    img, size = pack_and_paint(m.cubes, 128)
    img.save(FORGE + f'textures/entity/character/{char}_armor.png')
    for slot, key in (('helmet', 'H'), ('chestplate', 'C'), ('leggings', 'L'), ('boots', 'B'), ('hoodie', 'D'), ('hoodie_trim', 'E'), ('sr_hoodie', 'S')):
        out = {'texture_size': size, 'bones': {}}
        nodes = {}
        for bb, (mod, bbp, jp) in BONES.items():
            nodes[bb] = {'pivot': jp, 'cubes': [], 'children': []}
            out['bones'][mod] = nodes[bb]
        for c in m.cubes:
            if not c['name'].startswith(key + ':'):
                continue
            parent = nodes[c['bone']]
            bbp = BONES[c['bone']][1]
            if any(abs(a) > 1e-6 for a in c['rot']):
                frm, sz = box(c['origin'], c['frm'], c['to'])
                parent['children'].append({'name': 'c%d' % len(parent['children']), 'pivot': rel(bbp, c['origin']),
                                           'rot': rot(c['rot']), 'cubes': [{'uv': c['uv'], 'from': frm, 'size': sz}], 'children': []})
            else:
                frm, sz = box(bbp, c['frm'], c['to'])
                parent['cubes'].append({'uv': c['uv'], 'from': frm, 'size': sz})
        json.dump(out, open(FORGE + f'models/armor/{char}_{slot}.json', 'w'), separators=(',', ':'))
    print(char, 'armor cubes', len(m.cubes), 'atlas', size)

if __name__ == '__main__':
    import os
    os.makedirs(FORGE + 'models/armor', exist_ok=True)
    for ch in ('mikiko', 'rhalix'):
        mm = build(ch)
        export(mm, ch)
        json.dump({'size': 0, 'cubes': mm.cubes}, open(f'/home/claude/xw/detail/{ch}_armor_spec.json', 'w'))
