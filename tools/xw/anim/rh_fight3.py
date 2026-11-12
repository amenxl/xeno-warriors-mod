"""Rhalix fight animation pass 3 (user notes from the Blockbench check): kicks turned the hips too much, and he turned
his head away too much. Hip twist in the kicks is now at most 12 degrees, head yaw in every fight move at most 12,
then the shoulder positions are re-baked for the new twist (see rh_fight2.py)."""
import json, math
P = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/animations/rhalix.animation.json'
data = json.load(open(P)); A = data['animations']
FIGHT = [n for n in A if n.startswith('rh_') and not any(k in n for k in ('hungry', 'stick', 'leg_stretch', 'inspect', 'sharpen'))]
KICKS = [n for n in FIGHT if 'kick' in n or 'roundhouse' in n]
for n in FIGHT:
    bones = A[n]['bones']
    cap_body = 12 if n in KICKS else 45
    for t, v in bones.get('body', {}).get('rotation', {}).items():
        v['vector'][1] = max(-cap_body, min(cap_body, v['vector'][1]))
    for t, v in bones.get('head', {}).get('rotation', {}).items():
        v['vector'][1] = max(-12, min(12, v['vector'][1]))
for n, a in A.items():
    b = a['bones'].get('body')
    for arm, s in (('right_arm', 1), ('left_arm', -1)):
        if b and arm in a['bones'] and 'position' in a['bones'][arm]:
            a['bones'][arm]['position'] = {t: {'vector': [round(s * (5 * math.cos(math.radians(-v['vector'][1])) - 5), 3), 0,
                                                          round(s * 5 * math.sin(math.radians(-v['vector'][1])), 3)],
                                               'easing': v.get('easing', 'easeOutQuad')} for t, v in b['rotation'].items()}
json.dump(data, open(P, 'w'), indent=1)
print('kicks:', KICKS)
