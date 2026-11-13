"""Mikiko for Yes Steve Model in the anime style of YSM's own models (v2): a bigger head with big anime eyes, a
slimmer torso, slim arms and legs - the same hair, clothes and colours as the mod's Mikiko.

python3 -I tools/xw/ysm/ysm_anime.py <out_dir> [char]

Every part is the mod's model (Java ModelPart space) put through a per-region scale about an anchor, then written as
Bedrock geometry with per-face UVs from the original sizes (so the texture stays put however a cube is resized).
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ysm_export as X
import anime_face

HEAD_K = 1.22
BODY_K = (0.86, 0.92, 0.86)
ARM_K = (0.75, 0.92, 0.8)
# Java space: y down, head pivot y 0 (neck), body 0..12, legs 12..24.  The torso shrinks to 0..11.04; the legs move up.
BODY_H = 12 * BODY_K[1]
LEG_Y = BODY_H                       # new hip height (Java y)
LEG_K = (0.82, (24 - LEG_Y) / 12, 0.82)   # (a little longer: the feet stay on the ground)
REG = {
    # region: (anchor in old space, anchor in new space, scale)
    'head': ((0, 0, 0), (0, 0, 0), (HEAD_K,) * 3),
    'body': ((0, 0, 0), (0, 0, 0), BODY_K),
    'right_arm': ((-5, 2, 0), (-4.4, 1.6, 0), ARM_K),
    'left_arm': ((5, 2, 0), (4.4, 1.6, 0), ARM_K),
    'right_leg': ((-1.9, 12, 0), (-1.65, LEG_Y, 0), LEG_K),
    'left_leg': ((1.9, 12, 0), (1.65, LEG_Y, 0), LEG_K),
}


def tp(reg, p):
    a, b, k = REG[reg]
    return tuple(b[i] + (p[i] - a[i]) * k[i] for i in range(3))


def ts(reg, s):
    k = REG[reg][2]
    return tuple(s[i] * k[i] for i in range(3))


def cube(reg, jfrom, size, uv, v0=None, v1=None, inflate=0.0, top=True, bottom=True):
    """A cube from the old model, resized: per-face UVs laid out from the old size."""
    f2, s2 = tp(reg, jfrom), ts(reg, size)
    w, h, d = size
    u, v = uv
    if v0 is None: v0, v1 = 0, h
    sv, sh = v + d + v0, v1 - v0
    faces = {"east": {"uv": [u, sv], "uv_size": [d, sh]}, "north": {"uv": [u + d, sv], "uv_size": [w, sh]},
             "west": {"uv": [u + d + w, sv], "uv_size": [d, sh]}, "south": {"uv": [u + d + w + d, sv], "uv_size": [w, sh]},
             "up": {"uv": [u + d, v], "uv_size": [w, d]}, "down": {"uv": [u + d + w, v + d], "uv_size": [w, -d]}}
    if not top: faces.pop("up")
    if not bottom: faces.pop("down")
    c = {"origin": [round(f2[0], 4), round(24 - (f2[1] + s2[1]), 4), round(f2[2], 4)],
         "size": [round(s2[0], 4), round(s2[1], 4), round(s2[2], 4)], "uv": faces}
    if inflate: c["inflate"] = inflate
    return c


def build(char='mikiko'):
    det = json.load(open(os.path.join(X.ASSETS, f'models/detail/{char}.json')))
    bones = {}

    def add(name, reg, jpivot, parent=None, rot=(0, 0, 0)):
        p = tp(reg, jpivot) if reg else jpivot
        b = X.Bone(name, (p[0], 24 - p[1], p[2]), parent, rot)
        bones[name] = b
        return b

    add('AllBody', None, (0, 24, 0))
    add('UpBody', None, (0, LEG_Y, 0), 'AllBody')
    add('Head', 'head', (0, 0, 0), 'UpBody')
    for side, sx in (('Right', -1), ('Left', 1)):
        reg = side.lower() + '_arm'
        add(f'{side}Arm', reg, (5 * sx, 2, 0), 'UpBody')
        add(f'{side}ForeArm', reg, (5 * sx, 6, 0), f'{side}Arm')
        add(f'{side}Hand', reg, (6 * sx, 12, 0), f'{side}ForeArm')
        add(f'{side}HandLocator', reg, (6 * sx, 12, 0), f'{side}Hand')
        lreg = side.lower() + '_leg'
        add(f'{side}Leg', lreg, (1.9 * sx, 12, 0), 'AllBody')
        add(f'{side}LowerLeg', lreg, (1.9 * sx, 18, 0), f'{side}Leg')

    sk = lambda u, v: (u, v + X.SKIN_V)
    bones['Head'].cubes += [cube('head', (-4, -8, -4), (8, 8, 8), sk(0, 0)), cube('head', (-4, -8, -4), (8, 8, 8), sk(32, 0), inflate=0.5)]
    bones['UpBody'].cubes += [cube('body', (-4, 0, -2), (8, 12, 4), sk(16, 16)), cube('body', (-4, 0, -2), (8, 12, 4), sk(16, 32), inflate=0.25)]

    def limb(reg, up, low, px, py, uvs, split):
        for uv, inf in zip(uvs, (0.0, 0.25)):
            bones[up].cubes.append(cube(reg, (px - 2, py, -2), (4, split, 4), sk(*uv), 0, split, inf, bottom=False))
            bones[low].cubes.append(cube(reg, (px - 2, py + split, -2), (4, 12 - split, 4), sk(*uv), split, 12, inf, top=False))

    # (the arm's old cube: x -3..1 about the pivot, i.e. centred 1 px in - centre it on the pivot's line)
    limb('right_arm', 'RightArm', 'RightForeArm', -6, 2, ((40, 16), (40, 32)), 4)
    limb('left_arm', 'LeftArm', 'LeftForeArm', 6, 2, ((32, 48), (48, 48)), 4)
    limb('right_leg', 'RightLeg', 'RightLowerLeg', -1.9, 12, ((0, 16), (0, 32)), 6)
    limb('left_leg', 'LeftLeg', 'LeftLowerLeg', 1.9, 12, ((16, 48), (0, 48)), 6)

    fc = det['face']
    fr, fs = fc['from'], fc['size']
    f2, s2 = tp('head', fr), ts('head', fs)
    bones['Head'].cubes.append({"origin": [round(f2[0], 4), round(24 - (f2[1] + s2[1]), 4), round(f2[2] - 0.02, 4)],
                                "size": [round(s2[0], 4), round(s2[1], 4), 0],
                                "uv": {"north": {"uv": [X.FACE_U, X.FACE_V], "uv_size": [8, 8]}}})

    owner = {'head': ('Head', None, 0), 'body': ('UpBody', None, 0), 'right_arm': ('RightArm', 'RightForeArm', 4),
             'left_arm': ('LeftArm', 'LeftForeArm', 4), 'right_leg': ('RightLeg', 'RightLowerLeg', 6),
             'left_leg': ('LeftLeg', 'LeftLowerLeg', 6)}
    jpiv = {'head': (0, 0, 0), 'body': (0, 0, 0), 'right_arm': (-5, 2, 0), 'left_arm': (5, 2, 0),
            'right_leg': (-1.9, 12, 0), 'left_leg': (1.9, 12, 0)}
    n = [0]

    def walk(reg, node, abs_old, parent, split, low, root_bone):
        # abs_old: this node's pivot in the OLD absolute Java space; cubes are placed in old space, then mapped
        for c in node.get('cubes', []):
            f, s = c['from'], c['size']
            jf = (abs_old[0] + f[0], abs_old[1] + f[1], abs_old[2] + f[2])
            tgt = low if (split and parent == root_bone and f[1] + s[1] / 2 >= split) else parent
            bones[tgt].cubes.append(cube(reg, jf, s, c['uv']))
            n[0] += 1
        for ch in node.get('children', []):
            p = ch['pivot']
            ap = (abs_old[0] + p[0], abs_old[1] + p[1], abs_old[2] + p[2])
            par = low if (split and parent == root_bone and p[1] >= split) else parent
            name = f"{par}_{ch['name']}"
            i = 1
            while name in bones: name = f"{par}_{ch['name']}_{i}"; i += 1
            add(name, reg, ap, par, ch.get('rot', (0, 0, 0)))
            walk(reg, ch, ap, name, 0, None, None)

    for key, node in det['bones'].items():
        if key not in owner: continue
        up, low, split = owner[key]
        walk(key, node, jpiv[key], up, split, low, up)

    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{char}", "texture_width": X.ATLAS_W, "texture_height": X.ATLAS_H,
                        "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": [b.json() for b in bones.values()]}]}
    return geo, bones, n[0]


if __name__ == '__main__':
    out = sys.argv[1]
    char = sys.argv[2] if len(sys.argv) > 2 else 'mikiko'
    geo, bones, n = build(char)
    d = os.path.join(out, char)
    for sub in ('models', 'textures'): os.makedirs(os.path.join(d, sub), exist_ok=True)
    json.dump(geo, open(os.path.join(d, 'models/main.json'), 'w'), indent=1)
    json.dump(X.arm_geo(geo), open(os.path.join(d, 'models/arm.json'), 'w'), indent=1)
    X.atlas(char, anime=True).save(os.path.join(d, f'textures/{char}.png'))
    ysm = {"spec": 2,
           "metadata": {"name": "Mikiko (Xeno Warriors Recarnet)",
                        "tips": "Mikiko from the Xeno Warriors Recarnet mod. Characters by Recarnetwork Gacha.",
                        "license": {"type": "All Rights Reserved", "desc": "Made for the Xeno Warriors Recarnet mod; not for redistribution."},
                        "authors": [{"name": "Xeno Warriors Recarnet", "role": "Model"}]},
           "files": {"player": {"model": {"main": "models/main.json", "arm": "models/arm.json"},
                                "texture": [f"textures/{char}.png"]}}}
    json.dump(ysm, open(os.path.join(d, 'ysm.json'), 'w'), indent=2, ensure_ascii=False)
    print(f"{char}: {len(bones)} bones, {n} detail cubes -> {d}")
