import json, math, copy, sys
sys.path.insert(0, '/home/claude/xw/anim')
import clip
RES = clip.RES
OUTFITS = [json.load(open(RES + f'models/detail/{o}.json')) for o in ['rhalix', 'rhalix_origins1', 'rhalix_origins2', 'rhalix_s3']]
data = json.load(open(RES + 'animations/rhalix.animation.json'))
anims = data['animations']

def pen(arm, pose):
    worst = 0
    for m in OUTFITS:
        pts = clip.world_points(m, arm, pose)
        bx = clip.boxes_world(m, 'body', pose) + clip.boxes_world(m, 'head', pose)
        worst = max(worst, clip.inside(pts, bx).mean())
    return worst

changes = []
for name, a in anims.items():
    clip.ANIM = anims
    for arm, sign in (('right_arm', 1), ('left_arm', -1)):
        tr = a.get('bones', {}).get(arm, {}).get('rotation')
        if not isinstance(tr, dict): continue
        for k in sorted(tr, key=float):
            t = float(k)
            before = pen(arm, clip.pose_of(name, t))
            if before <= 0.03: continue
            v = tr[k]['vector'] if isinstance(tr[k], dict) else tr[k]
            orig = list(v)
            best = (before * 100, 0, 0, before)
            for dx in (0, 10, 20, 30):
                for dz in range(0, 45, 5):
                    v[0] = orig[0] + dx; v[2] = orig[2] + sign * dz
                    p = pen(arm, clip.pose_of(name, t))
                    score = p * 100 + 0.04 * (dx + dz)
                    if score < best[0]: best = (score, dx, dz, p)
            ok = best[3] <= 0.03 or best[3] <= before * 0.4
            v[0] = orig[0] + (best[1] if ok else 0); v[2] = orig[2] + sign * (best[2] if ok else 0)
            if ok and (best[1] or best[2]): changes.append((name, arm, k, round(before, 2), round(best[3], 2), best[1], best[2]))
for c in changes: print(c)
json.dump(data, open(RES + 'animations/rhalix.animation.json', 'w'), indent=1)
