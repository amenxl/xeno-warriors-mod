"""Rhalix fight animation pass 2 (checked in Blockbench). Run once on rhalix.animation.json.
- Shoulders follow the torso: when the body twists, the arms' pivots move round with it (position keys), so the
  arms stay on the shoulders instead of floating where the shoulders were.
- Torso twist capped at 45 degrees in the fight moves (more than that tore the coat away from the legs).
- The spinning back kick and the sword whirl spin his whole body (a 'root' track, played by RhalixCombatAnim)
  instead of twisting the torso round 110 degrees.
Vectors here are in the file's (Blockbench) convention: model yaw = -vector y."""
import json, math
P = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/animations/rhalix.animation.json'
data = json.load(open(P)); A = data['animations']
FIGHT = [n for n in A if n.startswith('rh_') and not any(k in n for k in ('hungry', 'stick', 'leg_stretch', 'inspect', 'sharpen'))]
E = 'easeOutQuad'

def k(v, e=E): return {'vector': v, 'easing': e}

# -- whole-body spins
sbk = A['rh_spin_back_kick']['bones']
sbk['body']['rotation'] = {'0': k([-4, 0, 0]), '0.22': k([0, -30, 0]), '0.34': k([-10, -20, 0]), '0.44': k([-10, -20, 0]), '0.6': k([-4, 0, 0])}
sbk['head']['rotation'] = {'0': k([-8, 0, 0]), '0.22': k([0, 25, 0]), '0.34': k([5, 40, 0]), '0.44': k([5, 40, 0]), '0.6': k([-8, 0, 0])}
sbk['root'] = {'rotation': {'0': k([0, 0, 0], 'linear'), '0.22': k([0, -90, 0]), '0.34': k([0, -180, 0]), '0.44': k([0, -180, 0], 'linear'),
                            '0.6': k([0, -360, 0])}}
wh = A['rh_sw_whirl']['bones']
wh['body']['rotation'] = {'0': k([-3, 12, 0]), '0.2': k([0, -40, 0]), '0.34': k([-6, 0, 0]), '0.42': k([-8, 30, 0]), '0.5': k([-8, 30, 0]),
                          '0.62': k([-3, 12, 0])}
wh['root'] = {'rotation': {'0': k([0, 0, 0], 'linear'), '0.2': k([0, 0, 0], 'linear'), '0.34': k([0, 150, 0], 'linear'),
                           '0.42': k([0, 300, 0]), '0.5': k([0, 340, 0]), '0.62': k([0, 360, 0])}}

# -- twist cap
for n in FIGHT:
    b = A[n]['bones'].get('body')
    if not b: continue
    for t, v in b['rotation'].items():
        v['vector'][1] = max(-45, min(45, v['vector'][1]))

# -- shoulders follow the twist (every animation with a body track)
for n, a in A.items():
    b = a['bones'].get('body')
    if not b or not any(abs(v['vector'][1]) > 0.01 for v in b['rotation'].values()): continue
    for arm, s in (('right_arm', 1), ('left_arm', -1)):
        bone = a['bones'].setdefault(arm, {})
        assert 'position' not in bone, (n, arm)
        pos = {}
        for t, v in b['rotation'].items():
            th = math.radians(-v['vector'][1])
            pos[t] = {'vector': [round(s * (5 * math.cos(th) - 5), 3), 0, round(s * 5 * math.sin(th), 3)], 'easing': v.get('easing', E)}
        bone['position'] = pos
json.dump(data, open(P, 'w'), indent=1)
print('ok', len(FIGHT), 'fight moves')
