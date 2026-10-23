import json, sys
sys.path.insert(0, '/home/claude/xw/anim')
import clip
RES = clip.RES
OUTFITS = [json.load(open(RES + f'models/detail/{o}.json')) for o in ['rhalix', 'rhalix_origins1', 'rhalix_origins2', 'rhalix_s3']]
data = json.load(open(RES + 'animations/rhalix.animation.json')); anims = data['animations']; clip.ANIM = anims
def pen(arm, pose):
    w = 0
    for m in OUTFITS:
        pts = clip.world_points(m, arm, pose); bx = clip.boxes_world(m, 'body', pose) + clip.boxes_world(m, 'head', pose)
        w = max(w, clip.inside(pts, bx).mean())
    return w
for name, a in anims.items():
    for arm, sign in (('right_arm', 1), ('left_arm', -1)):
        tr = a.get('bones', {}).get(arm, {}).get('rotation')
        if not isinstance(tr, dict): continue
        for k in sorted(tr, key=float):
            before = pen(arm, clip.pose_of(name, float(k)))
            if before <= 0.05: continue
            v = tr[k]['vector']; orig = list(v); best = (1e9,)
            for dx in (-20, -10, 0, 10, 20):
                for dy in (-30, -15, 0, 15, 30):
                    for dz in (-40, -30, -20, -10, 0, 10, 20, 30, 40):
                        v[0], v[1], v[2] = orig[0] + dx, orig[1] + sign * dy, orig[2] + sign * dz
                        p = pen(arm, clip.pose_of(name, float(k)))
                        sc = p * 100 + 0.03 * (abs(dx) + abs(dy) + abs(dz))
                        if sc < best[0]: best = (sc, p, list(v))
            if best[1] <= 0.035:
                v[:] = best[2]; print(name, arm, k, round(before, 2), '->', round(best[1], 2), orig, '->', v)
            else:
                v[:] = orig; print('  left', name, arm, k, round(before, 2), 'best', round(best[1], 2))
json.dump(data, open(RES + 'animations/rhalix.animation.json', 'w'), indent=1)
