"""Rhalix (Season 1 / "Recarnet 1" outfit) model cleanup: stops faces z-fighting and parts riding on the wrong limb.

Works on models/detail/rhalix.json and rhalix_lunar.json (same coat, hair and boots; Lunar only has other wings) in
Minecraft model space. Run it after tools/xw/rhalix_plus/plus.py (that one re-adds its own cubes to the bones).
No texture changes: every cube keeps its UV, sizes only shrink by a few hundredths of a pixel.

  python3 tools/xw/rhalix_cleanup/cleanup.py            (the files carry a marker, so a second run does nothing)

What it fixes:
 1. belt + buckle were split between the two front coat flaps -> the buckle tore apart when he walked: back on the body
 2. pocket flaps + their buttons sat on the body, not on the flaps -> they floated off the coat when a flap swung
 3. the front-corner hem tatters hung from the side panels, the last side tatters from the back tail -> moved to
    the panel they hang from
 4. coat corners: front flap / side panel / back tail faces in the same planes (flicker) -> side panels tucked
    0.05 behind the front flaps and the back tail, front flaps 0.05 inside the side panels
 5. hem tatters flush with the panel they hang from -> inset 0.03; the maroon edges of the flaps and lapels flush
    with their top/bottom; the old purple collar left inside the black stand-up collar removed
 6. strap rivets flush with the chest X straps, the two X straps (and the old straps under them) flush where they
    cross, the boots' X straps likewise, the boot rivets doubled up, the boot pull tab flush with the boot top
 7. sleeve flush with its cuff fold underneath, outer sleeve seam flush with the cuff seam
 8. the two boots overlap at the inseam with their faces in the same planes -> the left boot sits 0.02 back/down
 9. hair: neighbouring locks of a row overlap side by side in the same plane (thin flickering lines), the hair cap's
    sides flush with its back -> locks layered 0.03 apart; wing feathers the same
"""
import json, math, os, sys
import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
DETAIL = os.path.join(ROOT, 'src/main/resources/assets/xenowarriors/models/detail/')
MARK = 'xw_cleanup'
EPS = 0.015          # faces closer than this (pixels) in the same plane flicker
STEP = 0.03          # how far apart layered parts are moved


def r4(v): return [round(x, 4) for x in v]


def child(node, name):
    for c in node['children']:
        if c['name'] == name: return c
    raise KeyError(name)


def find_cube(cubes, frm, size=None):
    for c in cubes:
        if all(abs(c['from'][i] - frm[i]) < 1e-3 for i in range(3)) and (size is None or all(abs(c['size'][i] - size[i]) < 1e-3 for i in range(3))):
            return c
    raise KeyError(f'no cube at {frm}')


def move(cube, src, dst_list, offset):
    """Takes the cube out of src's list, re-bases it by offset (old pivot - new pivot) and adds it to dst_list."""
    src.remove(cube)
    cube['from'] = r4([cube['from'][i] + offset[i] for i in range(3)])
    dst_list.append(cube)
    return cube


def trim(cube, axis, lo=0.0, hi=0.0):
    """Moves the cube's low face in by lo and its high face in by hi along axis (0 x, 1 y, 2 z)."""
    cube['from'][axis] = round(cube['from'][axis] + lo, 4)
    cube['size'][axis] = round(cube['size'][axis] - lo - hi, 4)


def coat_fixes(d):
    body = d['bones']['body']
    fr, fl = child(body, 'coat_front_r'), child(body, 'coat_front_l')
    sr, sl, bk = child(body, 'coat_side_r'), child(body, 'coat_side_l'), child(body, 'coat_back')
    P = {n['name']: n['pivot'] for n in (fr, fl, sr, sl, bk)}
    off = lambda a, b: [P[a][i] - (P[b][i] if b else 0) for i in range(3)]

    # 1. belt and buckle onto the body (they were cut between the flaps by x)
    for frm in ([-4.75, -0.2, 0.5], [-3.4, -0.3, 0.3], [-3.4, 0.65, 0.3], [-1.75, -0.3, 0.3], [-2.5, 0.0, 0.25]):
        move(find_cube(fl['cubes'], frm), fl['cubes'], body['cubes'], off('coat_front_l', None))
    move(find_cube(fr['cubes'], [1.4, -0.3, 0.3]), fr['cubes'], body['cubes'], off('coat_front_r', None))
    # the buckle's sides ran over its top and bottom bars (same front plane): just the part between them
    for x in (0.65, -1.0):
        trim(find_cube(body['cubes'], [x, 11.1, -2.65], [0.35, 1.3, 0.25]), 1, 0.35, 0.35)

    # 2. pocket flaps and their buttons onto the flaps they're sewn on
    for frm in ([-4.25, 13.65, -3.08], [-3.45, 14.15, -3.16]):
        move(find_cube(body['cubes'], frm), body['cubes'], fr['cubes'], [-P['coat_front_r'][i] for i in range(3)])
    for frm in ([2.2, 13.65, -3.08], [3.15, 14.15, -3.16]):
        move(find_cube(body['cubes'], frm), body['cubes'], fl['cubes'], [-P['coat_front_l'][i] for i in range(3)])

    # 3. tatters onto the panel they hang from
    t = move(find_cube(sr['cubes'], [0.0, 7.9, -2.95], [0.9, 1.9, 0.6]), sr['cubes'], fr['cubes'], off('coat_side_r', 'coat_front_r'))
    trim(t, 0, 0.05, 0.0)                       # (the flap's outer edge comes in 0.05, below)
    t = move(find_cube(sl['cubes'], [-0.9, 7.9, -2.95], [0.9, 2.1, 0.6]), sl['cubes'], fl['cubes'], off('coat_side_l', 'coat_front_l'))
    trim(t, 0, 0.0, 0.05)
    t = move(find_cube(bk['cubes'], [-4.65, 7.6, 0.1]), bk['cubes'], sr['cubes'], off('coat_back', 'coat_side_r'))
    trim(t, 2, 0.0, 0.18)                       # ends inside the side panel's back edge like the others
    t = move(find_cube(bk['cubes'], [4.05, 7.6, 0.1]), bk['cubes'], sl['cubes'], off('coat_back', 'coat_side_l'))
    trim(t, 2, 0.0, 0.18)

    # 4. the corners: front flaps over the side panels at the front, the back tail over them at the back
    trim(find_cube(fr['cubes'], [-2.25, 0.0, 0.0], [2.95, 8.0, 0.6]), 0, 0.05, 0.0)
    trim(find_cube(fl['cubes'], [-0.7, 0.0, 0.0], [2.95, 8.0, 0.6]), 0, 0.0, 0.05)
    for s in (sr, sl):
        trim(s['cubes'][0], 2, 0.05, 0.05)

    # 5. hem tatters: a hair inside the panel they hang from on all four sides (their top overlaps its hem)
    for n in (fr, fl, bk, sr, sl):
        for c in n['cubes']:
            if c['from'][1] > 7.5 and c['size'][1] < 3.5:
                trim(c, 0, STEP, STEP); trim(c, 2, STEP, STEP)
    # the flaps' maroon inner edge: its top and bottom just inside the flap's
    for n, x in ((fr, 0.45), (fl, -0.9)):
        trim(find_cube(n['cubes'], [x, 0.0, -0.1], [0.45, 8.0, 0.45]), 1, STEP, STEP)
    for x in (-2.5, 2.05):                       # the lapels' maroon edge likewise at the bottom
        trim(find_cube(body['cubes'], [x, 0.0, -2.95], [0.45, 11.4, 0.45]), 1, 0.0, STEP)
    # the old purple stand-up collar sits entirely inside the black one that replaced it: gone
    body['children'] = [c for c in body['children'] if not (c['name'] in ('c156', 'c159')
                        and c['cubes'] and abs(c['cubes'][0]['size'][2] - 5.3) < 1e-3)]


def strap_fixes(d):
    body = d['bones']['body']
    # 6. strap rivets out in front of the X straps; the crossing straps a step apart
    for frm in ([-1.57, 6.36, -2.72], [-1.57, 4.16, -2.72], [1.13, 6.36, -2.72], [1.13, 4.16, -2.72]):
        find_cube(body['cubes'], frm)['from'][2] = -2.8
    for frm in ([-3.15, 2.9, -2.75], [2.65, 2.9, -2.75]):           # the rivets at the straps' top ends
        find_cube(body['cubes'], frm)['from'][2] = -2.82
    for name in ('xwplus14', 'c161'):
        c = child(body, name)['cubes'][0]
        c['from'][2] = round(c['from'][2] - 0.02, 4)
    # boots: one X strap of each pair a step forward, the doubled rivets once
    for leg, names in (('right_leg', ('c163', 'c165')), ('left_leg', ('c167', 'c169'))):
        bone = d['bones'][leg]
        for n in names:
            child(bone, n)['pivot'][2] = round(child(bone, n)['pivot'][2] - 0.02, 4)
        for x in (1.35, -2.05):
            try:
                c = find_cube(bone['cubes'], [x if leg == 'right_leg' else -x - 0.5, 6.7, -2.75])
                bone['cubes'].remove(c)
            except KeyError:
                pass
        # the pull tab at the back of the boot top: out past the boot top's back face
        tab = next(c for c in bone['cubes'] if abs(c['size'][0] - 1.2) < 1e-3 and abs(c['size'][2] - 0.4) < 1e-3)
        trim(tab, 2, 0.0, -0.05)


def limb_fixes(d):
    for arm in ('right_arm', 'left_arm'):
        cubes = d['bones'][arm]['cubes']
        sleeve = next(c for c in cubes if abs(c['size'][1] - 11.0) < 1e-3)
        trim(sleeve, 1, 0.0, 0.05)              # 7. ends just inside the cuff fold
        seam = next(c for c in cubes if abs(c['size'][0] - 0.09) < 1e-3)
        trim(seam, 1, 0.0, 0.25)                # stops where the cuff seam starts
    # 8. the left boot a step back and down (the boots overlap 0.8 at the inseam)
    leg = d['bones']['left_leg']
    for c in leg['cubes']:
        c['from'][1] = round(c['from'][1] + 0.02, 4); c['from'][2] = round(c['from'][2] - 0.02, 4)
    for c in leg['children']:
        c['pivot'][1] = round(c['pivot'][1] + 0.02, 4); c['pivot'][2] = round(c['pivot'][2] - 0.02, 4)
    # 9a. hair cap: the sides end just inside the back piece
    hair = child(d['bones']['head'], 'hair')
    for c in hair['cubes']:
        if abs(c['size'][2] - 9.35) < 1e-3: trim(c, 2, 0.0, 0.05)


# ---------------------------------------------------------------- layering strands (hair locks, feathers)

def rot_zyx(r):
    x, y, z = r
    cz, sz, cy, sy, cx, sx = math.cos(z), math.sin(z), math.cos(y), math.sin(y), math.cos(x), math.sin(x)
    return (np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
            @ np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]))


def faces(M, c):
    lo = np.array(c['from'], float); hi = lo + np.array(c['size'], float)
    out = []
    for axis in range(3):
        for side, v in ((-1, lo[axis]), (1, hi[axis])):
            a, b = [k for k in range(3) if k != axis]
            q = []
            for ua, ub in ((lo[a], lo[b]), (hi[a], lo[b]), (hi[a], hi[b]), (lo[a], hi[b])):
                p = np.zeros(3); p[axis] = v; p[a] = ua; p[b] = ub
                q.append(M[:3, :3] @ p + M[:3, 3])
            n = M[:3, :3][:, axis] * side
            out.append((axis, n, float(n @ q[0]), np.array(q)))
    return out


def overlap(P, Q, n):
    a = P[1] - P[0]; a = a / np.linalg.norm(a); b = np.cross(n, a)
    p2 = np.array([[x @ a, x @ b] for x in P]); q2 = np.array([[x @ a, x @ b] for x in Q])
    lo1, hi1, lo2, hi2 = p2.min(0), p2.max(0), q2.min(0), q2.max(0)       # (faces here are axis-aligned in-plane)
    w = min(hi1[0], hi2[0]) - max(lo1[0], lo2[0]); h = min(hi1[1], hi2[1]) - max(lo1[1], lo2[1])
    return w * h if w > 0.01 and h > 0.01 else 0.0


def strand_units(node, M, prefix):
    """(key, node, M) for each child node under node (all of a strand's pieces share pivot and angle)."""
    units = {}
    for ch in node['children']:
        Mc = M @ mat(ch['pivot'], ch.get('rot'))
        key = (prefix,) + tuple(np.round(ch['pivot'], 4)) + tuple(np.round(ch.get('rot') or [0, 0, 0], 4))
        units.setdefault(key, []).append((ch, Mc))
    return units


def mat(p, r):
    M = np.eye(4); M[:3, 3] = p
    if r is not None and any(abs(x) > 1e-9 for x in r): M[:3, :3] = rot_zyx(r)
    return M


def layer_strands(node, M, prefix):
    """Moves strands (along the axis of the clashing faces) until no two strands share a face plane."""
    units = strand_units(node, M, prefix)
    keys = list(units)

    def unit_faces(k):
        return [f for ch, Mc in units[k] for c in ch['cubes'] for f in faces(Mc, c)]

    def box(fs):
        P = np.vstack([f[3] for f in fs]); return P.min(0) - 0.2, P.max(0) + 0.2

    fl = {k: unit_faces(k) for k in keys}
    bb = {k: box(fl[k]) for k in keys}
    near = {k: [o for o in keys if o != k and np.all(bb[k][0] <= bb[o][1]) and np.all(bb[o][0] <= bb[k][1])] for k in keys}

    def clashes(k):
        """[(other, local axis of k's face)] for faces of k in the same plane as another strand's, overlapping."""
        out = []
        for o in near[k]:
            for ax, n, dd, P in fl[k]:
                for ax2, n2, d2, Q in fl[o]:
                    if n @ n2 > 0.9999 and abs(dd - d2) < EPS and overlap(P, Q, n) > 0.02:
                        out.append((o, ax))
        return out

    def shift(k, axis, by):
        for ch, Mc in units[k]:
            for c in ch['cubes']: c['from'][axis] = round(c['from'][axis] + by, 4)
        fl[k] = unit_faces(k)

    moved = set()
    for _ in range(6):                               # a few passes: a move can make a new (rarer) clash
        bad = False
        for k in keys:                               # in model order, so neighbours in a row alternate
            cl = clashes(k)
            if not cl: continue
            bad = True
            axis = cl[0][1]
            for cand in (STEP, -STEP, 2 * STEP, -2 * STEP, 3 * STEP):
                shift(k, axis, cand)
                if not clashes(k): break
                shift(k, axis, -cand)
            else:
                shift(k, axis, STEP)                 # (best effort; the next pass tries again)
            moved.add(k)
        if not bad: break
    return len(moved)


def layering(d, wings):
    head = d['bones']['head']
    n = 0
    # hair and bangs are separate groups but their locks overlap each other: treat them as one set
    hair, bangs = child(head, 'hair'), child(head, 'bangs')
    merged = {'children': hair['children'] + bangs['children']}
    n += layer_strands(merged, np.eye(4), 'hair')
    if wings:
        body = d['bones']['body']
        for w in ('right_wing', 'left_wing'):
            wn = child(body, w)
            n += layer_strands(wn, mat(wn['pivot'], wn.get('rot')), w)
    return n


def run(name, wings):
    path = DETAIL + name + '.json'
    d = json.load(open(path))
    if d.get(MARK):
        print(name, 'already cleaned up'); return
    coat_fixes(d); strap_fixes(d); limb_fixes(d)
    n = layering(d, wings)
    d[MARK] = 1
    json.dump(d, open(path, 'w'))
    print(name, 'cleaned up;', n, 'strand layers moved')


if __name__ == '__main__':
    run('rhalix', True)
    run('rhalix_lunar', False)          # (its crescent wings are its own model)
