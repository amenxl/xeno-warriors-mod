"""Convert the Blockbench detail specs into the JSON the Fabric mod loads (Minecraft model space)."""
import json, math, shutil, sys

FAB = '/home/claude/xw/XenoWarriorsFabric/src/main/resources/assets/xenowarriors/'
BONES = {  # blockbench name -> (mod name, blockbench pivot, vanilla java pivot)
    'Head': ('head', [0, 24, 0], [0, 0, 0]),
    'Body': ('body', [0, 24, 0], [0, 0, 0]),
    'RightArm': ('right_arm', [5, 22, 0], [-5, 2, 0]),
    'LeftArm': ('left_arm', [-5, 22, 0], [5, 2, 0]),
    'RightLeg': ('right_leg', [1.9, 12, 0], [-1.9, 12, 0]),
    'LeftLeg': ('left_leg', [-1.9, 12, 0], [1.9, 12, 0]),
}
WINGS = {'RightWing': 'right_wing', 'LeftWing': 'left_wing'}

def r3(v):
    return [round(x, 4) for x in v]

def rel(parent, p):
    return r3([parent[0] - p[0], parent[1] - p[1], p[2] - parent[2]])

def box(parent, lo, hi):
    return r3([parent[0] - hi[0], parent[1] - hi[1], lo[2] - parent[2]]), r3([hi[i] - lo[i] for i in range(3)])

def rot(r):
    return r3([-math.radians(r[0]), -math.radians(r[1]), math.radians(r[2])])

BANGS_UNDER_HOOD = {'mikiko': False, 'rhalix': True}   # Mikiko gets a short fringe from the hood model instead
CURRENT = {'name': None}

def is_bang(c):
    """Front locks hanging over the forehead, near the middle."""
    if not BANGS_UNDER_HOOD.get(CURRENT['name'], True):
        return False
    if c['name'] not in ('Dread', 'Lock', 'Strand'):
        return False
    from armor import aabb            # where the cube really ends up after its rotation
    lo, hi = aabb(c)
    cx, cy, cz = [(lo[i] + hi[i]) / 2 for i in range(3)]
    return cz < -4.3 and abs(cx) < 2.3 and cy < 31.6

def export(name):
    CURRENT['name'] = name
    spec = json.load(open(f'/home/claude/xw/detail/{name}_spec.json'))
    groups = {g['name']: g for g in spec['groups']}
    out = {'texture_size': spec['size'], 'bones': {}}
    nodes = {}
    for bb, (mod, bbp, jp) in BONES.items():
        nodes[bb] = {'pivot': jp, 'cubes': [], 'children': [], '_origin': bbp}
        out['bones'][mod] = nodes[bb]
    for gname, g in groups.items():
        parent = nodes[g['parent']]
        node = {'name': WINGS.get(gname, gname.lower()), 'pivot': rel(parent['_origin'], g['origin']),
                'rot': rot(g['rot']), 'cubes': [], 'children': [], '_origin': g['origin']}
        parent['children'].append(node)
        nodes[gname] = node
    # hair/goggles go in a "hair" group under the head (hidden under the SR hood), except a few front
    # bangs that stay visible in the face opening
    head = nodes['Head']
    for grp in ('hair', 'bangs'):
        node = {'name': grp, 'pivot': [0, 0, 0], 'rot': [0, 0, 0], 'cubes': [], 'children': [], '_origin': head['_origin']}
        head['children'].append(node)
        nodes['Head:' + grp] = node
    counter = 0
    for c in spec['cubes']:
        parent = nodes[c['bone']]
        if c['bone'] == 'Head':
            parent = nodes['Head:bangs' if is_bang(c) else 'Head:hair']
        if any(abs(a) > 1e-6 for a in c['rot']):
            frm, size = box(c['origin'], c['frm'], c['to'])
            parent['children'].append({'name': f'c{counter}', 'pivot': rel(parent['_origin'], c['origin']),
                                       'rot': rot(c['rot']), 'cubes': [{'uv': c['uv'], 'from': frm, 'size': size}],
                                       'children': []})
            counter += 1
        else:
            frm, size = box(parent['_origin'], c['frm'], c['to'])
            parent['cubes'].append({'uv': c['uv'], 'from': frm, 'size': size})

    def strip(n):
        n.pop('_origin', None)
        for ch in n['children']:
            strip(ch)
    for n in out['bones'].values():
        strip(n)
    # HD face: zero-depth plane on the front of the head, own 64x64 texture used as 16x16 UV space
    frm, size = box([0, 24, 0], [-4, 24, -4.08], [4, 32, -4.08])
    out['face'] = {'bone': 'head', 'texture_size': 16, 'uv': [0, 0], 'from': frm, 'size': size}
    json.dump(out, open(FAB + f'models/detail/{name}.json', 'w'), separators=(',', ':'))
    for f in (f'{name}.png', f'{name}_details.png', f'{name}_face.png'):
        shutil.copy(f'/home/claude/xw/tex/{f}', FAB + 'textures/entity/character/' + f)
    print(name, 'exported', len(spec['cubes']), 'cubes')

if __name__ == '__main__':
    for n in sys.argv[1:] or ['mikiko', 'rhalix']:
        export(n)
