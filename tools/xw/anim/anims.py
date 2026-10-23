"""Writes Mikiko's GeckoLib animation file. Poses are written in the game's model angles (degrees):
arm x<0 = raised forward, right arm z>0 / left arm z<0 = out to the side, leg x<0 = forward, head x<0 = look up.
They're converted to Blockbench/GeckoLib's convention (x and y flipped) on export."""
import json

OUT = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/animations/mikiko.animation.json'
BONES = ['head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg']


def anim(length, keys, loop=False, ease='easeInOutSine'):
    """keys: list of (time_s, {bone: (x, y, z)}). Bones not given in a key hold their previous pose."""
    tracks = {}
    cur = {b: (0, 0, 0) for b in BONES}
    used = set()
    for t, pose in keys:
        used |= set(pose)
    for t, pose in keys:
        for b in used:
            if b in pose:
                cur[b] = pose[b]
            x, y, z = cur[b]
            tracks.setdefault(b, {})[f'{t:.4f}'.rstrip('0').rstrip('.') or '0'] = {
                'vector': [round(-x, 3), round(-y, 3), round(z, 3)], 'easing': ease}
    a = {'animation_length': length, 'bones': {b: {'rotation': tracks[b]} for b in tracks}}
    if loop:
        a['loop'] = True
    return a


def snaps(poses, hold, snap, t0=0.0):
    """Robot-style: each pose snaps in over `snap` seconds and holds for the rest of `hold`."""
    keys, t = [], t0
    prev = poses[-1]
    for p in poses:
        keys.append((t, prev))
        keys.append((t + snap, p))
        prev = p
        t += hold
    keys.append((t, prev))
    return keys


R, L = 'right_arm', 'left_arm'
# ---------------- robot dance ("cat" on a jukebox) - 8 stiff poses, half a second each
rest_legs = {'right_leg': (0, 0, 0), 'left_leg': (0, 0, 0)}
robot_poses = [
    {R: (-90, 0, 0), L: (0, 0, -4), 'head': (0, 0, 0), 'right_leg': (-8, 0, 0), 'left_leg': (6, 0, 0)},
    {R: (-90, 0, 0), L: (-90, 0, 0), 'head': (0, 35, 0), 'right_leg': (6, 0, 0), 'left_leg': (-8, 0, 0)},
    {R: (0, 0, 90), L: (0, 0, -90), 'head': (0, -35, 0), 'right_leg': (-8, 0, 0), 'left_leg': (6, 0, 0)},
    {R: (-150, 0, 0), L: (0, 0, -90), 'head': (-15, 0, 0), 'right_leg': (6, 0, 0), 'left_leg': (-8, 0, 0)},
    {R: (-90, 0, 20), L: (-90, 0, -20), 'head': (0, 0, 20), 'right_leg': (-8, 0, 0), 'left_leg': (6, 0, 0)},
    {R: (-90, 0, -20), L: (-90, 0, 20), 'head': (0, 0, -20), 'right_leg': (6, 0, 0), 'left_leg': (-8, 0, 0)},
    {R: (-45, 0, 120), L: (-45, 0, -120), 'head': (10, 0, 0), 'right_leg': (-8, 0, 0), 'left_leg': (6, 0, 0)},
    {R: (-120, 0, 0), L: (30, 0, -10), 'head': (0, 0, 0), 'right_leg': (6, 0, 0), 'left_leg': (-8, 0, 0)},
]
robot = anim(4.0, snaps(robot_poses, 0.5, 0.07), loop=True, ease='linear')

# ---------------- idle: a big stretch, arms up over his head, looking up, then a shake-out
stretch = anim(3.0, [
    (0.0, {R: (0, 0, 0), L: (0, 0, 0), 'head': (0, 0, 0)}),
    (0.8, {R: (-168, 0, -12), L: (-168, 0, 12), 'head': (-28, 0, 0)}),
    (1.2, {R: (-176, 0, -4), L: (-176, 0, 4), 'head': (-34, 0, 6)}),
    (1.6, {R: (-168, 0, -14), L: (-168, 0, 14), 'head': (-30, 0, -6)}),
    (2.1, {R: (-20, 0, 25), L: (-20, 0, -25), 'head': (5, 0, 0)}),
    (2.4, {R: (0, 0, 8), L: (0, 0, -8), 'head': (0, 0, 0)}),
    (3.0, {R: (0, 0, 0), L: (0, 0, 0), 'head': (0, 0, 0)}),
])

# ---------------- idle: scratching the back of his head, head tilted
scratch_keys = [(0.0, {R: (0, 0, 0), 'head': (0, 0, 0)}), (0.6, {R: (-150, -30, -25), 'head': (12, 0, 14)})]
t = 0.6
for i in range(10):
    t += 0.15
    scratch_keys.append((t, {R: (-150 + (6 if i % 2 else -6), -30, -25 + (8 if i % 2 else -8)), 'head': (12, 0, 14)}))
scratch_keys += [(t + 0.5, {R: (0, 0, 0), 'head': (0, 0, 0)}), (3.0, {R: (0, 0, 0), 'head': (0, 0, 0)})]
scratch = anim(3.0, scratch_keys)

# ---------------- idle: kicking at the dirt, looking down, hands loose
kick = anim(3.0, [
    (0.0, {'right_leg': (0, 0, 0), 'head': (0, 0, 0), R: (0, 0, 0), L: (0, 0, 0)}),
    (0.5, {'right_leg': (10, 0, 0), 'head': (30, 0, 0), R: (5, 0, 6), L: (5, 0, -6)}),
    (0.75, {'right_leg': (-28, 0, 4), 'head': (32, 0, 0)}),
    (1.0, {'right_leg': (12, 0, 0)}),
    (1.3, {'right_leg': (-30, 0, 6)}),
    (1.55, {'right_leg': (8, 0, 0)}),
    (2.0, {'right_leg': (0, 0, 0), 'head': (24, 0, 0)}),
    (2.5, {'right_leg': (0, 0, 0), 'head': (0, 0, 0), R: (0, 0, 0), L: (0, 0, 0)}),
    (3.0, {'right_leg': (0, 0, 0), 'head': (0, 0, 0), R: (0, 0, 0), L: (0, 0, 0)}),
])

# ---------------- sealing a defeated mob in the Hekublade: blade raised high in front of him
absorb = anim(1.1, [
    (0.0, {R: (0, 0, 0), L: (0, 0, 0), 'head': (0, 0, 0)}),
    (0.12, {R: (-125, 0, 8), L: (-20, 0, -15), 'head': (-12, 0, 0)}),
    (0.75, {R: (-132, 0, 4), L: (-20, 0, -15), 'head': (-14, 0, 0)}),
    (1.1, {R: (0, 0, 0), L: (0, 0, 0), 'head': (0, 0, 0)}),
])

# ---------------- breakdance ("It's Time to Get Groovy") - 16 s loop, synced with the body moves in code:
# toprock 0-4 s, drop 4-5, footwork 5-9, windmills 9-12, headspin 12-14.5, freeze 14.5-15.3, pop up 15.3-16
bd = []
for i in range(8):                                     # toprock: stepping out, arms swinging across
    t = i * 0.5; s_ = 1 if i % 2 == 0 else -1
    bd.append((t, {R: (-55, 0, -35) if s_ > 0 else (-20, 0, 15), L: (-20, 0, -15) if s_ > 0 else (-55, 0, 35),
                   'right_leg': (-35 if s_ > 0 else 5, 0, 4), 'left_leg': (5 if s_ > 0 else -35, 0, -4),
                   'head': (5, 0, 8 * s_), 'body': (0, 0, 0)}))
bd.append((4.0, {R: (-20, 0, 15), L: (-55, 0, 35), 'right_leg': (5, 0, 4), 'left_leg': (-35, 0, -4), 'head': (0, 0, 0)}))
bd.append((4.6, {R: (-75, 0, 10), L: (-75, 0, -10), 'right_leg': (-70, 0, 6), 'left_leg': (-70, 0, -6), 'head': (10, 0, 0)}))
for i in range(8):                                     # footwork: one hand planted, legs kicking out in turn
    t = 5.0 + i * 0.5; s_ = 1 if i % 2 == 0 else -1
    bd.append((t, {R: (-10, 0, 35), L: (-80, 0, -50), 'right_leg': (-95 if s_ > 0 else -20, 0, 10),
                   'left_leg': (-20 if s_ > 0 else -95, 0, -10), 'head': (-10, 0, 0)}))
bd.append((9.0, {R: (-110, 0, -40), L: (-110, 0, 40), 'right_leg': (-35, 0, 70), 'left_leg': (-35, 0, -70), 'head': (-20, 0, 0)}))
for i in range(6):                                     # windmills: legs in a wide V, arms tucked
    t = 9.0 + (i + 1) * 0.5; s_ = 1 if i % 2 == 0 else -1
    bd.append((t, {R: (-110, 0, -40), L: (-110, 0, 40), 'right_leg': (-35 + 10 * s_, 0, 75), 'left_leg': (-35 - 10 * s_, 0, -75),
                   'head': (-20, 0, 0)}))
bd.append((12.3, {R: (-170, 0, -20), L: (-170, 0, 20), 'right_leg': (0, 0, 40), 'left_leg': (0, 0, -40), 'head': (0, 0, 0)}))
bd.append((14.5, {R: (-170, 0, -25), L: (-170, 0, 25), 'right_leg': (0, 0, 45), 'left_leg': (0, 0, -45), 'head': (0, 0, 0)}))
bd.append((14.8, {R: (-175, 0, 0), L: (-40, 0, -60), 'right_leg': (-60, 0, 30), 'left_leg': (20, 0, -20), 'head': (0, 0, -15)}))   # freeze
bd.append((15.3, {R: (-175, 0, 0), L: (-40, 0, -60), 'right_leg': (-60, 0, 30), 'left_leg': (20, 0, -20), 'head': (0, 0, -15)}))
bd.append((15.7, {R: (-150, 0, -30), L: (-150, 0, 30), 'right_leg': (-20, 0, 5), 'left_leg': (-20, 0, -5), 'head': (-20, 0, 0)}))
bd.append((16.0, {R: (-55, 0, -35), L: (-20, 0, -15), 'right_leg': (-35, 0, 4), 'left_leg': (5, 0, -4), 'head': (5, 0, 8), 'body': (0, 0, 0)}))
breakdance = anim(16.0, bd, loop=True)
# on alert ("Mimicry Eye Catcher"): guard up, low stance, snapping his head around to look (6 s loop)
guard = {R: (-75, 0, -20), L: (-60, 0, 25), 'right_leg': (-8, 0, 12), 'left_leg': (8, 0, -12)}
alert_keys = []
looks = [(0.0, (0, 0, 0)), (0.6, (0, 0, 0)), (0.75, (0, 60, 0)), (1.6, (0, 60, 0)), (1.75, (-10, -55, 0)), (2.6, (-10, -55, 0)),
         (2.75, (-25, 0, 0)), (3.4, (-25, 0, 0)), (3.55, (5, 75, 0)), (4.2, (5, 75, 0)), (4.3, (0, 20, 0)), (4.5, (0, -20, 0)),
         (4.7, (0, 20, 0)), (5.0, (0, -70, 0)), (5.7, (0, -70, 0)), (6.0, (0, 0, 0))]
for t, head in looks:
    k = dict(guard); k['head'] = head
    # guard follows the way he looks a little
    k[R] = (-75, head[1] * 0.4, -20); k[L] = (-60, head[1] * 0.4, 25)
    alert_keys.append((t, k))
alert = anim(6.0, alert_keys, loop=True, ease='easeOutQuad')
# hyped ("SkyBound Pursuit"): jumping on every beat (0.5 s), fists pumping in the air, knees tucking up (4 s loop,
# the jump height itself is done in code)
hype_keys = []
for i in range(9):
    t = i * 0.5
    up = i % 2 == 0
    k = {'head': (-15 if up else 10, 0, 0),
         R: (-170, 0, -10) if (i // 2) % 2 == 0 else (-60, 0, 30),
         L: (-60, 0, -30) if (i // 2) % 2 == 0 else (-170, 0, 10),
         'right_leg': (0, 0, 4), 'left_leg': (0, 0, -4)}
    hype_keys.append((t, k))
    if i < 8:   # knees up at the top of each jump
        k2 = dict(k); k2['right_leg'] = (-55, 0, 6); k2['left_leg'] = (-55, 0, -6)
        k2[R] = (k[R][0] - 10, 0, k[R][2]); k2[L] = (k[L][0] - 10, 0, k[L][2])
        hype_keys.append((t + 0.25, k2))
hype = anim(4.0, hype_keys, loop=True, ease='easeInOutQuad')

# shadow boxing ("Virtual Grind"), 10 s loop: 7 s of combos, then a rage yell (7.4-9 s), then back to it
guardR, guardL = (-75, 0, -20), (-80, 0, 25)
stance = {'right_leg': (-10, 0, 6), 'left_leg': (12, 0, -6)}
sb = [(0.0, dict(stance, **{R: guardR, L: guardL, 'head': (8, 0, 0)}))]
punches = [(L, (-92, -8, 0)), (R, (-95, 10, 0)), (L, (-92, -8, 0)), (R, (-60, -55, -10)), (L, (-150, 20, 0)),
           (R, (-95, 10, 0)), (R, (-95, 10, 0)), (L, (-60, 55, 10)), (R, (-150, -20, 0)), (L, (-92, -8, 0))]
t = 0.3
for k, (arm, pose) in enumerate(punches):
    bob = (8 if k % 3 else 18, 0, 6 if k % 2 else -6)
    hit = dict(stance, **{R: guardR, L: guardL, 'head': bob}); hit[arm] = pose
    sb.append((t, hit))
    sb.append((t + 0.22, dict(stance, **{R: guardR, L: guardL, 'head': (8, 0, 0)})))
    t += 0.62
sb.append((7.0, dict(stance, **{R: guardR, L: guardL, 'head': (8, 0, 0)})))
sb.append((7.4, {R: (22, 0, 35), L: (22, 0, -35), 'head': (-30, 0, 0), 'right_leg': (0, 0, 10), 'left_leg': (0, 0, -10)}))
for i in range(8):                                    # shaking with rage, yelling
    sb.append((7.5 + i * 0.18, {R: (18 + (4 if i % 2 else -4), 0, 40), L: (18 - (4 if i % 2 else -4), 0, -40),
                                'head': (-36 + (3 if i % 2 else 0), 0, 0), 'right_leg': (0, 0, 10), 'left_leg': (0, 0, -10)}))
sb.append((9.4, dict(stance, **{R: guardR, L: guardL, 'head': (8, 0, 0)})))
sb.append((10.0, dict(stance, **{R: guardR, L: guardL, 'head': (8, 0, 0)})))
shadow_box = anim(10.0, sb, loop=True, ease='easeOutQuad')

# sad, slow dance ("Corrupted Pulse"), 8 s loop: swaying, head down, a hand pressed to his heart now and then
sd = []
for i, t in enumerate([0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0]):
    sw = 1 if i % 2 == 0 else -1
    heart = 2.5 <= t <= 5.0
    sd.append((t, {'head': (25, 0, 8 * sw), R: (5, 0, 6 + 4 * sw),
                   L: (-45, 50, -10) if heart else (5, 0, -6 + 4 * sw),
                   'right_leg': (0, 0, 3 + 3 * sw), 'left_leg': (0, 0, -3 + 3 * sw), 'body': (6, 0, 0)}))
sad_dance = anim(8.0, sd, loop=True)

# chill vibing ("Max's Chill Day"), 4 s loop: head bobbing on the beat, tapping a foot, loose arms
cv = []
for i in range(9):
    t = i * 0.5
    cv.append((t, {'head': (2, 0, 3 if (i // 2) % 2 else -3), 'right_leg': (0, 0, 3), 'left_leg': (0, 0, -3),
                   R: (-8, 0, 8), L: (-8, 0, -8)}))
    if i < 8:
        cv.append((t + 0.25, {'head': (14, 0, 3 if (i // 2) % 2 else -3), 'right_leg': (-14, 0, 3), 'left_leg': (0, 0, -3),
                              R: (-12, 0, 10), L: (-12, 0, -10)}))
chill = anim(4.0, cv, loop=True)

data = {'format_version': '1.8.0', 'animations': {
    'robot_dance': robot, 'idle_stretch': stretch, 'idle_scratch': scratch, 'idle_kick_dirt': kick, 'absorb': absorb, 'breakdance': breakdance, 'alert': alert, 'hype': hype,
    'shadow_box': shadow_box, 'sad_dance': sad_dance, 'chill_vibe': chill},
    'geckolib_format_version': 2}

# ---------------- Mikiko fighting while surfing on a Hekublade: feet planted on the board, all upper body
SURF = {R: (-14, 0, 66), L: (11, 0, -57), 'body': (11, 0, 0), 'head': (0, 0, 0),
        'right_leg': (-9, 14, 13), 'left_leg': (9, -14, -13)}


def surf_strike(length, windup, hit, wt=0.3, ht=0.46, hold=0.62):
    g = dict(SURF)
    w = dict(g); w.update(windup)
    h = dict(w); h.update(hit)
    return anim(length, [(0.0, g), (length * wt, w), (length * ht, h), (length * hold, h), (length, g)], ease='easeOutQuad')


surf_moves = {
    # carving slash: crouched into the turn, a wide cut across
    'surf_carve_slash': surf_strike(0.5, {R: (-88, 50, 20), L: (-20, 0, -70), 'body': (22, 35, 0), 'head': (0, -15, 0)},
                                    {R: (-84, -76, 0), L: (10, 0, -62), 'body': (22, -40, 0), 'head': (0, 22, 0)}),
    # rising backhand off the low side of the board
    'surf_rising_slash': surf_strike(0.5, {R: (-25, -60, -10), L: (0, 0, -75), 'body': (26, -30, 0), 'head': (8, -10, 0)},
                                     {R: (-160, 25, 35), L: (15, 0, -50), 'body': (2, 25, 0), 'head': (-12, 10, 0)}),
    # a snap kick off the front of the board, arms flung wide for balance
    'surf_board_kick': surf_strike(0.46, {'right_leg': (-40, 14, 13), 'body': (2, 0, 0), R: (-20, 0, 85), L: (0, 0, -85)},
                                   {'right_leg': (-98, 0, 6), 'body': (-12, 0, 0), R: (-25, 0, 95), L: (5, 0, -90)}),
    # leaping up off the board into an overhead chop
    'surf_overhead_chop': surf_strike(0.55, {R: (-172, 0, 5), L: (-150, 0, -20), 'body': (-8, 0, 0), 'head': (-12, 0, 0)},
                                      {R: (-40, -10, 0), L: (10, 0, -55), 'body': (32, 0, 0), 'head': (12, 0, 0)}, wt=0.36, ht=0.5),
    # whipping round with the arm out flat
    'surf_spin_slash': anim(0.56, [(0.0, SURF),
                                   (0.18, dict(SURF, **{R: (-84, 70, 12), L: (-30, 0, -60), 'body': (18, 65, 0), 'head': (0, -28, 0)})),
                                   (0.32, dict(SURF, **{R: (-86, -88, -4), L: (20, 0, -75), 'body': (18, -70, 0), 'head': (0, 32, 0)})),
                                   (0.42, dict(SURF, **{R: (-86, -88, -4), L: (20, 0, -75), 'body': (18, -70, 0), 'head': (0, 32, 0)})),
                                   (0.56, SURF)], ease='easeOutQuad'),
    # both palms driving forward, a gust of wind
    'surf_wind_palm': surf_strike(0.5, {R: (-60, 20, 30), L: (-60, -20, -30), 'body': (4, 0, 0)},
                                  {R: (-92, -10, 0), L: (-92, 10, 0), 'body': (22, 0, 0), 'head': (-8, 0, 0)}),
}
data['animations'].update(surf_moves)

# ---------------- after a long, quiet stretch without a fight: Mikiko stretches his legs out
_r0 = {R: (0, 0, 6), L: (0, 0, -6), 'body': (0, 0, 0), 'head': (0, 0, 0), 'right_leg': (0, 0, 0), 'left_leg': (0, 0, 0)}
_lr = {R: (-32, 0, 10), L: (-32, 0, -10), 'body': (16, 0, -8), 'head': (-6, 0, 6), 'right_leg': (-6, 0, 32), 'left_leg': (4, 0, -34)}   # side lunge to the right
_ll = {R: (-32, 0, 10), L: (-32, 0, -10), 'body': (16, 0, 8), 'head': (-6, 0, -6), 'right_leg': (4, 0, 34), 'left_leg': (-6, 0, -32)}   # and to the left
_qd = {R: (-10, 0, 28), L: (38, 0, -12), 'body': (-3, 0, 0), 'head': (4, 0, 0), 'right_leg': (0, 0, 2), 'left_leg': (24, 0, -3)}       # pulling his left heel up behind
data['animations']['idle_leg_stretch'] = anim(5.0, [(0.0, _r0), (0.6, _lr), (1.8, _lr), (2.2, _ll), (3.0, _ll), (3.4, _r0), (3.6, _qd), (4.6, _qd), (5.0, _r0)])
import os
os.makedirs(os.path.dirname(OUT), exist_ok=True)
json.dump(data, open(OUT, 'w'), indent=1)
print('animations written', [k for k in data['animations']])

# ============================================================ Rhalix
RH_OUT = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/animations/rhalix.animation.json'
wings_out = anim(2.5, [
    (0.0, {R: (0, 0, 0), L: (0, 0, 0), 'head': (0, 0, 0), 'body': (0, 0, 0)}),
    (0.55, {R: (-165, 0, -14), L: (-165, 0, 14), 'head': (-25, 0, 0), 'body': (-6, 0, 0)}),     # big stretch
    (0.8, {R: (-172, 0, -6), L: (-172, 0, 6), 'head': (-30, 0, 0), 'body': (-8, 0, 0)}),
    (1.15, {R: (-25, 0, 75), L: (-25, 0, -75), 'head': (-12, 0, 0), 'body': (-12, 0, 0)}),     # arms flung wide as the wings burst out
    (1.6, {R: (-20, 0, 70), L: (-20, 0, -70), 'head': (-10, 0, 0), 'body': (-10, 0, 0)}),
    (2.1, {R: (0, 0, 8), L: (0, 0, -8), 'head': (0, 0, 0), 'body': (0, 0, 0)}),
    (2.5, {R: (0, 0, 0), L: (0, 0, 0), 'head': (0, 0, 0), 'body': (0, 0, 0)}),
])
wings_in = anim(1.2, [
    (0.0, {R: (0, 0, 0), L: (0, 0, 0), 'head': (0, 0, 0)}),
    (0.3, {R: (-22, 0, -12), L: (-22, 0, 12), 'head': (14, 0, 0)}),      # shoulders in, a little shrug
    (0.8, {R: (-18, 0, -10), L: (-18, 0, 10), 'head': (10, 0, 0)}),
    (1.2, {R: (0, 0, 0), L: (0, 0, 0), 'head': (0, 0, 0)}),
])
hover_keys = []
for i, t in enumerate([0.0, 0.75, 1.5, 2.25, 3.0]):
    s = 1 if i % 2 == 0 else -1
    hover_keys.append((t, {R: (12 + 4 * s, 0, 18 + 3 * s), L: (12 - 4 * s, 0, -18 - 3 * s),
                           'right_leg': (14 + 6 * s, 0, 4), 'left_leg': (6 - 6 * s, 0, -4),
                           'head': (-4 + 2 * s, 0, 0)}))
hover = anim(3.0, hover_keys, loop=True)
# "Gravity Glitch Duel": floating, waving his arms through the air, glitchy stutter-snaps, gravity pushes - 8 s loop
gd_poses = [
    (0.0, {R: (0, 0, 90), L: (0, 0, -90), 'right_leg': (10, 0, 5), 'left_leg': (10, 0, -5), 'head': (0, 0, 0)}),
    (0.5, {R: (0, 0, 115), L: (0, 0, -65), 'head': (0, 0, 8)}),
    (1.0, {R: (0, 0, 65), L: (0, 0, -115), 'head': (0, 0, -8)}),
    (1.5, {R: (0, 0, 110), L: (0, 0, -75), 'head': (0, 0, 6)}),
    (2.0, {R: (-170, 0, -10), L: (-170, 0, 10), 'head': (-30, 0, 0), 'right_leg': (15, 0, 5), 'left_leg': (-10, 0, -5)}),
    (2.45, {R: (-170, 0, -10), L: (-170, 0, 10)}),
    (2.5, {R: (-90, 40, 0), L: (0, 0, -20), 'head': (0, 0, 20)}),
    (2.7, {R: (-90, 40, 0), L: (0, 0, -20), 'head': (0, 0, 20)}),
    (2.75, {R: (-20, 0, 10), L: (-90, -40, 0), 'head': (0, 0, -20)}),
    (2.95, {R: (-20, 0, 10), L: (-90, -40, 0), 'head': (0, 0, -20)}),
    (3.0, {R: (-90, 40, 0), L: (0, 0, -20), 'head': (0, 0, 20)}),
    (3.25, {R: (0, 0, 60), L: (0, 0, -60), 'head': (0, 0, 0)}),
    (4.0, {R: (-90, 0, 10), L: (-90, 0, -10), 'right_leg': (20, 0, 5), 'left_leg': (20, 0, -5), 'head': (-5, 0, 0)}),
    (4.5, {R: (-60, 0, -30), L: (-60, 0, 30), 'head': (5, 0, 0)}),
    (5.0, {R: (-95, 0, 10), L: (-95, 0, -10), 'head': (-5, 0, 0)}),
    (5.5, {R: (0, 0, 80), L: (0, 0, -80), 'right_leg': (5, 0, 2), 'left_leg': (5, 0, -2), 'head': (0, 0, 0)}),
    (6.0, {R: (-160, 0, 0), L: (10, 0, -10), 'right_leg': (15, 0, 4), 'left_leg': (0, 0, -4)}),
    (6.5, {R: (10, 0, 10), L: (-160, 0, 0), 'right_leg': (0, 0, 4), 'left_leg': (15, 0, -4)}),
    (7.0, {R: (-160, 0, 0), L: (10, 0, -10), 'right_leg': (15, 0, 4), 'left_leg': (0, 0, -4)}),
    (7.5, {R: (0, 0, 85), L: (0, 0, -85), 'right_leg': (10, 0, 5), 'left_leg': (10, 0, -5)}),
    (8.0, {R: (0, 0, 90), L: (0, 0, -90), 'right_leg': (10, 0, 5), 'left_leg': (10, 0, -5), 'head': (0, 0, 0)}),
]
gravity_dance = anim(8.0, gd_poses, loop=True)
# "Lunar Ascendence": vibing sadly - head down, slow heavy nods, shoulders slumped, a slow sway (4 s loop)
sad_keys = []
for i, t in enumerate([0.0, 1.0, 2.0, 3.0, 4.0]):
    s_ = 1 if i % 2 == 0 else -1
    sad_keys.append((t, {'head': (24 + (6 if i % 2 else 0), 0, 6 * s_), R: (6, 0, 4 + 2 * s_), L: (6, 0, -4 + 2 * s_),
                         'right_leg': (0, 0, 2), 'left_leg': (0, 0, -2), 'body': (6, 0, 0)}))
sad_vibe = anim(4.0, sad_keys, loop=True)
# "Smoke Break": leaning back on one leg, other hand in his pocket, bringing the cigarette up for a drag and
# letting it hang at his side while he breathes out (6 s loop)
rest = {R: (-25, 0, 8), L: (5, 0, -10), 'right_leg': (0, 0, 4), 'left_leg': (-12, 0, -8), 'head': (5, 0, 4), 'body': (0, 0, 0)}
drag = dict(rest); drag[R] = (-143, 0, 41); drag['head'] = (-6, 0, 0)     # hand up at his mouth
smoke = anim(6.0, [(0.0, rest), (1.0, rest), (1.6, drag), (2.6, drag), (3.2, rest), (6.0, rest)], loop=True)
# ---------------- Rhalix's martial arts: quick boxing-style punches (like DragonMineZ's melee) and lots of kicks.
# Every strike: wind-up -> snap out -> hold a beat -> recover to a fighting guard.
GUARD = {R: (-12, 0, 6), L: (-12, 0, -6),   # relaxed ready stance: arms loose at his sides
         'head': (8, 0, 0), 'body': (4, 0, 0), 'right_leg': (6, 0, 4), 'left_leg': (-8, 0, -4)}


def strike(length, windup, hit, wt=0.3, ht=0.45, hold=0.65):
    """A strike as fractions of its length: guard, wind-up, the hit (held), back to guard."""
    g = dict(GUARD)
    w = dict(g); w.update(windup)
    h = dict(w); h.update(hit)
    return anim(length, [(0.0, g), (length * wt, w), (length * ht, h), (length * hold, h), (length, g)], ease='easeOutQuad')


rh_combat = {
    # punches - a jab, a cross and an uppercut, each turning the body into it
    'rh_jab_right': strike(0.42, {R: (-40, 0, -5), 'body': (4, 15, 0)}, {R: (-92, 8, 0), L: (15, 0, -8), 'body': (8, -22, 0)}),
    'rh_jab_left': strike(0.42, {L: (-40, 0, 5), 'body': (4, -15, 0)}, {L: (-92, -8, 0), R: (15, 0, 8), 'body': (8, 22, 0)}),
    'rh_uppercut_right': strike(0.5, {R: (-10, 0, 10), 'body': (12, 10, 0), 'head': (12, 0, 0)},
                                {R: (-165, -10, -5), L: (12, 0, -8), 'body': (-10, -15, 0), 'head': (-15, 0, 0)}),
    # kicks - he loves using his legs
    'rh_front_kick_right': strike(0.48, {'right_leg': (-55, 0, 0), 'body': (-4, 0, 0)},
                                  {'right_leg': (-100, 0, 3), 'left_leg': (4, 0, -3), 'body': (-12, 0, 0), R: (12, 0, 15), L: (-25, 0, -10)}),
    'rh_gut_kick_left': strike(0.48, {'left_leg': (-50, 0, 0), 'body': (-4, 0, 0)},
                               {'left_leg': (-88, 0, -3), 'right_leg': (4, 0, 3), 'body': (-10, 0, 0), L: (12, 0, -15), R: (-25, 0, 10)}),
    'rh_roundhouse_left': strike(0.55, {'left_leg': (-30, 0, -40), 'body': (0, 25, 0), 'head': (0, -10, 0)},
                                 {'left_leg': (-75, -20, -80), 'right_leg': (0, 0, 8), 'body': (-5, -35, -8), R: (-10, 0, 35), L: (10, 0, -15)},
                                 wt=0.32, ht=0.5),
    'rh_low_kick_right': strike(0.42, {'right_leg': (-15, 0, 25), 'body': (6, 10, 0)},
                                {'right_leg': (-45, 15, 40), 'body': (10, -20, 0), R: (15, 0, 10), L: (-20, 0, -8)}),
    'rh_axe_kick_right': anim(0.65, [(0.0, GUARD),
                                     (0.25, dict(GUARD, right_leg=(-150, 0, 5), body=(-15, 0, 0), head=(-5, 0, 0))),
                                     (0.36, dict(GUARD, right_leg=(-150, 0, 5), body=(-15, 0, 0), head=(-5, 0, 0))),
                                     (0.44, dict(GUARD, right_leg=(-20, 0, 3), body=(18, 0, 0), head=(15, 0, 0))),   # down it comes
                                     (0.52, dict(GUARD, right_leg=(-20, 0, 3), body=(18, 0, 0), head=(15, 0, 0))),
                                     (0.65, GUARD)], ease='easeInOutQuad'),
    'rh_spin_back_kick': anim(0.6, [(0.0, GUARD),
                                    (0.22, dict(GUARD, body=(0, 70, 0), head=(0, -40, 0), left_leg=(-20, 0, -10))),
                                    (0.34, dict(GUARD, body=(10, 110, 0), head=(-5, -60, 0), left_leg=(55, 0, -25), right_leg=(0, 0, 6),
                                                right_arm=(-10, 0, 40), left_arm=(5, 0, -22))),
                                    (0.44, dict(GUARD, body=(10, 110, 0), head=(-5, -60, 0), left_leg=(55, 0, -25), right_leg=(0, 0, 6),
                                                right_arm=(-10, 0, 40), left_arm=(5, 0, -22))),
                                    (0.6, GUARD)], ease='easeOutQuad'),
}

# ---------------- Rhalix with a sword: a trained swordsman. Fencing stance, sword held ready in front,
# clean cuts that turn the hips into them, a lunge, a two-handed cleave, a whirling finisher.
SW_GUARD = {R: (-38, -8, 6), L: (8, 0, -10), 'head': (4, 0, 0), 'body': (3, -12, 0),
            'right_leg': (-14, 0, 3), 'left_leg': (12, 0, -5)}


def sw(length, windup, hit, wt=0.32, ht=0.48, hold=0.62, ease='easeOutQuad'):
    g = dict(SW_GUARD)
    w = dict(g); w.update(windup)
    h = dict(w); h.update(hit)
    return anim(length, [(0.0, g), (length * wt, w), (length * ht, h), (length * hold, h), (length, g)], ease=ease)


rh_sword = {
    # high right to low left
    'rh_sw_down_slash': sw(0.5, {R: (-168, 22, 32), 'body': (-4, 22, 0), 'head': (-4, 8, 0)},
                           {R: (-48, -48, -12), 'body': (10, -26, 0), 'head': (8, -6, 0), 'right_leg': (-28, 0, 3), 'left_leg': (18, 0, -5)}),
    # low left rising to high right (backhand)
    'rh_sw_rising_slash': sw(0.5, {R: (-28, -62, -14), 'body': (8, -30, 0), 'head': (6, -10, 0)},
                             {R: (-152, 26, 34), 'body': (-6, 26, 0), 'head': (-10, 10, 0), L: (20, 0, -25)}),
    # wide horizontal cut, hips coiled then unwinding
    'rh_sw_horizontal': sw(0.52, {R: (-82, 42, 18), 'body': (2, 40, 0), 'head': (0, -18, 0), L: (-30, 0, -20)},
                           {R: (-86, -72, 0), 'body': (4, -46, 0), 'head': (0, 24, 0), L: (25, 0, -35), 'right_leg': (-22, 0, 6)}),
    # fencer's lunge: pull back, then drive the point straight through
    'rh_sw_thrust': sw(0.48, {R: (-58, 14, 12), 'body': (0, 18, 0), 'right_leg': (-4, 0, 3), 'left_leg': (10, 0, -4)},
                       {R: (-90, 0, 0), 'body': (14, -6, 0), 'head': (-10, 0, 0), L: (35, 0, -28),
                        'right_leg': (-48, 0, 2), 'left_leg': (34, 0, -6)}, wt=0.36, ht=0.5),
    # two-handed overhead cleave
    'rh_sw_overhead': sw(0.6, {R: (-172, 0, 4), L: (-168, 8, -6), 'body': (-10, 0, 0), 'head': (-12, 0, 0)},
                         {R: (-46, -14, 0), L: (-50, 32, 0), 'body': (16, 0, 0), 'head': (10, 0, 0),
                          'right_leg': (-30, 0, 3), 'left_leg': (20, 0, -4)}, wt=0.38, ht=0.52),
    # whirling finisher: winds right round, sweeps all the way across with the arm out flat
    'rh_sw_whirl': anim(0.62, [(0.0, SW_GUARD),
                               (0.2, dict(SW_GUARD, **{R: (-84, 70, 10), L: (-40, 0, -50), 'body': (0, 62, 0), 'head': (0, -30, 0)})),
                               (0.34, dict(SW_GUARD, **{R: (-88, -20, 0), L: (10, 0, -70), 'body': (6, -20, 0), 'head': (0, 0, 0)})),
                               (0.42, dict(SW_GUARD, **{R: (-86, -88, -6), L: (25, 0, -60), 'body': (8, -60, 0), 'head': (0, 30, 0),
                                                         'right_leg': (-26, 0, 8), 'left_leg': (20, 0, -8)})),
                               (0.5, dict(SW_GUARD, **{R: (-86, -88, -6), L: (25, 0, -60), 'body': (8, -60, 0), 'head': (0, 30, 0),
                                                        'right_leg': (-26, 0, 8), 'left_leg': (20, 0, -8)})),
                               (0.62, SW_GUARD)], ease='easeOutQuad'),
}

# idle with a sword: all three start and end on the same pose so they chain without a hitch
INSPECT = {R: (-82, -26, 0), L: (-62, 32, 0), 'head': (6, -12, 0), 'body': (3, 0, 0), 'right_leg': (0, 0, 2), 'left_leg': (0, 0, -2)}


def ins(**kw):
    d = dict(INSPECT)
    d.update({R if k == 'R' else L if k == 'L' else k: v for k, v in kw.items()})
    return d


rh_sword['rh_sw_inspect'] = anim(8.0, [
    (0.0, INSPECT),
    (1.2, ins(R=(-76, -38, -22), L=(-58, 36, 4), head=(10, -18, 6))),          # turning the blade to the light
    (2.4, ins(R=(-76, -38, -22), L=(-70, 30, 0), head=(10, -18, 6))),          # fingers along the flat
    (3.6, ins(R=(-98, -18, 14), L=(-70, 22, 0), head=(-6, -8, -4))),           # held up, the other side
    (4.6, ins(R=(-98, -18, 14), L=(-40, 18, 0), head=(-6, -8, -4))),
    (5.6, ins(R=(-104, -6, 0), L=(-20, 10, -6), head=(-12, -2, 0))),           # sighting down the edge
    (6.8, ins(R=(-104, -6, 0), L=(-20, 10, -6), head=(-12, -2, 0))),
    (8.0, INSPECT)])

strokes = [(0.0, INSPECT), (0.7, ins(R=(-30, -72, 0), L=(-44, 8, 0), head=(26, -16, 0), body=(10, 0, 0)))]
t = 0.7
for i in range(10):                       # long strokes of the whetstone along the edge
    t += 0.28; strokes.append((t, ins(R=(-30, -72, 0), L=(-44, 42, 0), head=(26, -18, 0), body=(10, 0, 0))))
    t += 0.28; strokes.append((t, ins(R=(-30, -72, 0), L=(-44, 8, 0), head=(26, -16, 0), body=(10, 0, 0))))
t += 0.5; strokes.append((t, ins(R=(-30, -72, 0), L=(-30, 10, -10), head=(22, -16, 0), body=(8, 0, 0))))   # checks the edge
t += 0.8; strokes.append((t, INSPECT))
SHARPEN_LEN = round(t, 2)
rh_sword['rh_sw_sharpen'] = anim(SHARPEN_LEN, strokes)

kata = [(0.0, INSPECT), (0.5, SW_GUARD), (0.9, SW_GUARD)]
seq = ['rh_sw_down_slash', 'rh_sw_rising_slash', 'rh_sw_thrust', 'rh_sw_horizontal', 'rh_sw_overhead']
poses = {'rh_sw_down_slash': ({R: (-168, 22, 32), 'body': (-4, 22, 0)}, {R: (-48, -48, -12), 'body': (10, -26, 0), 'right_leg': (-28, 0, 3)}),
         'rh_sw_rising_slash': ({R: (-28, -62, -14), 'body': (8, -30, 0)}, {R: (-152, 26, 34), 'body': (-6, 26, 0)}),
         'rh_sw_thrust': ({R: (-58, 14, 12), 'body': (0, 18, 0)}, {R: (-90, 0, 0), 'body': (14, -6, 0), L: (35, 0, -28), 'right_leg': (-48, 0, 2), 'left_leg': (34, 0, -6)}),
         'rh_sw_horizontal': ({R: (-82, 42, 18), 'body': (2, 40, 0)}, {R: (-86, -72, 0), 'body': (4, -46, 0)}),
         'rh_sw_overhead': ({R: (-172, 0, 4), L: (-168, 8, -6), 'body': (-10, 0, 0)}, {R: (-46, -14, 0), L: (-50, 32, 0), 'body': (16, 0, 0)})}
t = 0.9
for name in seq:                          # slow, controlled form, holding each cut
    wu, hit = poses[name]
    t += 0.45; kata.append((t, dict(SW_GUARD, **wu)))
    t += 0.25; kata.append((t, dict(SW_GUARD, **dict(wu, **hit))))
    t += 0.5; kata.append((t, dict(SW_GUARD, **dict(wu, **hit))))
    t += 0.35; kata.append((t, SW_GUARD))
for i in range(4):                        # a flourish: twirling the sword at his side
    t += 0.18; kata.append((t, dict(SW_GUARD, **{R: (-20, 0, 40 if i % 2 == 0 else -10)})))
t += 0.4; kata.append((t, SW_GUARD))
t += 0.6; kata.append((t, INSPECT))
rh_sword['rh_sw_practice'] = anim(round(t, 2), kata)
rh_combat.update(rh_sword)

# ---------------- Rhalix starving: the stomach growls, he doubles over clutching it
HUNCH = {R: (-38, 38, 6), L: (-38, -38, -6), 'head': (22, 0, 0), 'body': (14, 0, 0), 'right_leg': (-6, 0, 3), 'left_leg': (4, 0, -3)}
rh_combat['rh_hungry_growl'] = anim(2.4, [
    (0.0, {R: (0, 0, 6), L: (0, 0, -6), 'head': (0, 0, 0), 'body': (0, 0, 0), 'right_leg': (0, 0, 0), 'left_leg': (0, 0, 0)}),
    (0.25, dict(HUNCH, body=(22, 0, 0), head=(30, 0, 0))),                       # the growl hits: doubles over
    (0.55, dict(HUNCH, body=(18, 0, -4), head=(26, 8, -6))),
    (0.85, dict(HUNCH, body=(20, 0, 4), head=(28, -8, 6))),                       # rocking, rubbing it
    (1.15, dict(HUNCH, body=(18, 0, -3), head=(24, 6, -4))),
    (1.6, dict(HUNCH, body=(10, 0, 0), head=(-4, 0, 0), **{L: (-10, 0, -8)})),        # looks up, sighs
    (2.4, {R: (0, 0, 6), L: (0, 0, -6), 'head': (0, 0, 0), 'body': (0, 0, 0), 'right_leg': (0, 0, 0), 'left_leg': (0, 0, 0)})],
    ease='easeInOutQuad')
rh_combat['rh_hungry_idle'] = anim(4.0, [
    (0.0, {R: (-30, 34, 4), L: (4, 0, -6), 'head': (14, 0, 0), 'body': (8, 0, 0)}),
    (1.0, {R: (-34, 42, 4), L: (4, 0, -6), 'head': (16, 0, 3), 'body': (9, 0, 0)}),   # rubbing his stomach
    (2.0, {R: (-30, 30, 4), L: (4, 0, -6), 'head': (14, 0, 0), 'body': (8, 0, 0)}),
    (3.0, {R: (-34, 42, 4), L: (4, 0, -6), 'head': (16, 0, -3), 'body': (9, 0, 0)}),
    (4.0, {R: (-30, 34, 4), L: (4, 0, -6), 'head': (14, 0, 0), 'body': (8, 0, 0)})], loop=True)

# ---------------- Rhalix fidgeting with the stick in his mouth: takes it out, pockets it, takes it out again,
# looks it over, back in his mouth. (The stick itself is moved mouth -> hand -> pocket -> hand -> mouth in code,
# at 0.8 s, 1.5 s, 2.6 s and 4.4 s.)
STAND = {R: (0, 0, 6), L: (0, 0, -6), 'head': (0, 0, 0)}
MOUTH = {R: (-143, 0, 41), L: (0, 0, -6), 'head': (-6, 0, 0)}
rh_combat['rh_stick_idle'] = anim(5.4, [
    (0.0, STAND),
    (0.6, MOUTH), (0.85, dict(MOUTH, **{R: (-138, 0, 38)})),                     # pinches it out of his mouth
    (1.3, {R: (6, 0, 12), L: (0, 0, -6), 'head': (6, 0, 0)}),                    # into his pocket
    (1.6, {R: (10, 0, 10), L: (0, 0, -6), 'head': (4, 0, 0)}),
    (2.1, {R: (8, 0, 9), L: (0, 0, -6), 'head': (0, -14, 0)}),                   # hand in pocket, glances around
    (2.5, {R: (8, 0, 9), L: (0, 0, -6), 'head': (0, 10, 0)}),
    (3.0, {R: (-55, 25, -5), L: (0, 0, -6), 'head': (18, -12, 0)}),              # takes it back out, looks at it
    (3.4, {R: (-60, 30, -15), L: (0, 0, -6), 'head': (20, -14, 4)}),             # rolling it in his fingers
    (3.8, {R: (-55, 22, 8), L: (0, 0, -6), 'head': (18, -10, -3)}),
    (4.3, MOUTH), (4.5, dict(MOUTH, **{R: (-140, 0, 40)})),                      # back in his mouth
    (5.4, STAND)])

# ---------------- Rhalix after a long, quiet stretch without a fight: hamstrings, then a knee hug
_z = {R: (0, 0, 6), L: (0, 0, -6), 'body': (0, 0, 0), 'head': (0, 0, 0), 'right_leg': (0, 0, 0), 'left_leg': (0, 0, 0)}
_hr = {R: (-55, 0, 5), L: (-55, 0, -5), 'body': (40, 0, 0), 'head': (10, 0, 0), 'right_leg': (-30, 0, 2), 'left_leg': (6, 0, -2)}
_hl = {R: (-55, 0, 5), L: (-55, 0, -5), 'body': (40, 0, 0), 'head': (10, 0, 0), 'right_leg': (6, 0, 2), 'left_leg': (-30, 0, -2)}
_kh = {R: (-75, -30, 0), L: (-75, 30, 0), 'body': (5, 0, 0), 'head': (6, 0, 0), 'right_leg': (-95, 0, 2), 'left_leg': (0, 0, -2)}
rh_combat['rh_leg_stretch'] = anim(6.0, [(0.0, _z), (0.7, _hr), (1.3, dict(_hr, body=(46, 0, 0))), (1.9, _hr), (2.5, _hl),
                                         (3.1, dict(_hl, body=(46, 0, 0))), (3.7, _hl), (4.2, _kh), (4.9, _kh), (5.25, _z),
                                         (5.5, dict(_z, right_leg=(-10, 0, 0), left_leg=(8, 0, 0))), (5.75, dict(_z, right_leg=(8, 0, 0), left_leg=(-10, 0, 0))), (6.0, _z)])
json.dump({'format_version': '1.8.0', 'animations': dict({'wings_out_stretch': wings_out, 'wings_in': wings_in, 'hover_idle': hover,
                                                    'gravity_dance': gravity_dance, 'sad_vibe': sad_vibe, 'smoke_break': smoke, 'hype': hype, 'chill_vibe': chill},
                                                   **rh_combat),
           'geckolib_format_version': 2}, open(RH_OUT, 'w'), indent=1)
print('rhalix animations written')
