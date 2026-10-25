"""Sky Armor: a 3D set for Mikiko and Rhalix (same body size), one shared atlas."""
import sys, json, copy
sys.path.insert(0, '/home/claude/xw/outfits')
from kit import *
from PIL import Image

SKY, SKYD, WH, WHD, GOLD, GOLDD, GLOW = '#8fd3ff', '#4f9fdc', '#f1f7ff', '#cfdcee', '#e8c25a', '#b8902e', '#c8f6ff'
ARMOR = RES + 'models/armor/'

def wing_fin(f, bone, x0, y0, z0, length, up, side, col=GOLD):
    """A little feathered wing: three stepped feathers angled back and up."""
    for i in range(3):
        f.add(bone, [x0, y0 + i * 0.55, z0 + i * 0.35], [x0 + side * 0.35, y0 + i * 0.55 + 0.5, z0 + i * 0.35 + length - i * 0.6],
              col if i < 2 else WH, rot=[up, 0, 0], origin=[x0, y0, z0])

def helmet():
    f = Fit()
    # circlet around the head with a sky band and gold rims
    f.shell('head', [-4, 30.2, -4], [4, 31.4, 4], 0.55, SKY, {'top': None, 'bottom': None})
    f.shell('head', [-4, 31.4, -4], [4, 31.8, 4], 0.6, GOLD, {'top': None, 'bottom': None})
    f.shell('head', [-4, 29.8, -4], [4, 30.2, 4], 0.6, GOLD, {'top': None, 'bottom': None})
    # crest on the brow with a glowing gem
    f.add('head', [-1.2, 30.6, -4.9], [1.2, 33.0, -4.5], WH)
    f.add('head', [-0.7, 33.0, -4.9], [0.7, 34.0, -4.55], WH)
    f.add('head', [-0.45, 31.2, -5.1], [0.45, 32.1, -4.85], GLOW, shade=False)
    # back guard under the circlet, behind the hair
    f.add('head', [-3.6, 28.6, 4.5], [3.6, 30.2, 4.95], WH, {'back': lambda x, y: GOLD if y > .7 else WH})
    # wings at the temples sweeping back
    for side in (1, -1):
        x = 4.55 if side > 0 else -4.55
        f.add('head', [x, 30.4, -2.5], [x + side * 0.5, 32.2, 0.5], WH)
        f.add('head', [x + side * 0.1, 31.2, 0.2], [x + side * 0.45, 33.4, 2.4], WH, rot=[-25, 0, 0], origin=[x, 31.2, 0.2])
        f.add('head', [x + side * 0.1, 30.6, 0.6], [x + side * 0.45, 32.2, 3.2], SKY, rot=[-12, 0, 0], origin=[x, 30.6, 0.6])
    return f

def chest():
    f = Fit()
    # breastplate and back plate
    f.add('body', [-3.7, 15.2, -2.65], [3.7, 23.6, -2.1], WH, {'front': lambda x, y: GOLD if y < .07 or x < .05 or x > .95 else WH})
    f.add('body', [-3.9, 14.6, -2.75], [3.9, 15.2, -2.05], SKYD)
    f.add('body', [-3.7, 14.6, 2.1], [3.7, 23.6, 2.6], SKY)
    f.add('body', [-4.25, 14.6, -2.1], [-3.9, 23.6, 2.1], SKYD)
    f.add('body', [3.9, 14.6, -2.1], [4.25, 23.6, 2.1], SKYD)
    f.add('body', [-3.9, 23.6, -2.5], [3.9, 24.25, 2.5], GOLD)
    # sky gem in the centre, ribbed sky panels
    f.add('body', [-0.9, 19.0, -2.95], [0.9, 20.8, -2.6], GLOW, shade=False)
    f.add('body', [-0.6, 18.5, -2.85], [0.6, 19.0, -2.6], GOLD)
    for x in (-3.0, 1.6):
        f.add('body', [x, 16.0, -2.8], [x + 1.4, 18.6, -2.6], SKY)
    # little wings on the back
    for side in (1, -1):
        f.add('body', [side * 0.6, 19.5, 2.6], [side * 3.4, 22.6, 3.0], WH, rot=[0, 0, side * -18], origin=[side * 0.6, 21.0, 2.8])
        f.add('body', [side * 0.8, 18.0, 2.65], [side * 3.0, 20.0, 3.05], SKY, rot=[0, 0, side * -32], origin=[side * 0.8, 19.0, 2.85])
    # pauldrons
    f.both('right_arm', [3.4, 21.0, -2.6], [8.6, 24.7, 2.6], SKY, {'top': SKY, 'right': lambda x, y: GOLD if y < .15 else SKY})
    f.both('right_arm', [3.3, 20.5, -2.7], [8.7, 21.0, 2.7], GOLD)
    f.both('right_arm', [8.6, 21.6, -1.8], [9.0, 23.6, 1.8], WH)
    # vambraces
    f.both('right_arm', [3.65, 13.0, -2.35], [8.35, 16.5, 2.35], WH, {'top': None, 'bottom': None})
    f.both('right_arm', [3.6, 16.5, -2.4], [8.4, 17.0, 2.4], GOLD, {'top': None, 'bottom': None})
    # upper-arm sleeves, elbow guards
    f.both('right_arm', [3.55, 17.0, -2.3], [8.45, 20.6, 2.3], SKYD, {'top': None, 'bottom': None, 'front': lambda x, y: WH if .35 < x < .65 else SKYD})
    f.both('right_arm', [4.2, 16.4, -2.75], [7.8, 17.8, -2.3], WH)
    # faulds: white plates with a sky stripe and gold edge, and a back tabard
    for i, y in enumerate((12.2, 11.0)):
        f.add('body', [-4.3 + i * .25, y, -2.8], [4.3 - i * .25, y + 1.3, 2.7], WH if i == 0 else SKY,
              {'top': None, 'bottom': None, 'front': lambda x, yy: GOLD if yy > .8 else (SKY if abs(x - .5) < .1 else WH)})
    f.add('body', [-2.6, 8.4, 2.6], [2.6, 14.4, 2.95], SKY, {'back': lambda x, y: GOLD if x < .1 or x > .9 or y > .9 else (GLOW if abs(x - .5) < .08 and .3 < y < .6 else SKY)})
    return f

def legs():
    f = Fit()
    f.shell('body', [-4, 11.8, -2], [4, 13.4, 2], 0.45, SKYD)                 # belt
    f.add('body', [-1.0, 11.8, -2.75], [1.0, 13.4, -2.45], GOLD)             # buckle
    f.add('body', [-0.45, 12.25, -2.9], [0.45, 12.95, -2.7], GLOW, shade=False)
    f.both('right_leg', [-0.4, 5.5, -2.4], [4.4, 11.9, 2.4], WH, {'top': None, 'bottom': None,
                                                                   'front': lambda x, y: SKY if .3 < x < .7 else WH})
    f.both('right_leg', [-0.45, 11.3, -2.45], [4.45, 11.8, 2.45], GOLD, {'top': None, 'bottom': None})
    f.both('right_leg', [0.4, 4.6, -2.75], [3.6, 6.6, -2.35], SKY)                # knee guard
    f.both('right_leg', [3.9, 7.0, -1.6], [4.6, 10.6, 1.6], SKYD)               # hip plate
    f.both('right_leg', [1.5, 5.2, -3.0], [2.5, 6.0, -2.75], GLOW, shade=False)   # knee gem
    f.both('right_leg', [-0.4, 8.0, 2.4], [4.4, 11.0, 2.7], SKY)                  # back thigh plate
    return f

def boots():
    f = Fit()
    f.both('right_leg', [-0.4, 0.0, -2.4], [4.4, 4.6, 2.4], WH, {'top': None})
    f.both('right_leg', [-0.45, 4.1, -2.45], [4.45, 4.7, 2.45], GOLD, {'top': None, 'bottom': None})
    f.both('right_leg', [-0.45, -0.32, -3.4], [4.45, 0.45, 2.5], SKYD)                # sole (just below the foot)
    f.both('right_leg', [-0.4, 0.0, -3.4], [4.4, 1.9, -2.4], SKY)                 # toe cap
    # ankle wings, out to the side
    f.add('right_leg', [4.4, 2.6, -0.6], [4.8, 4.2, 2.4], GOLD, rot=[-20, 0, 0], origin=[4.6, 2.6, -0.6])
    f.add('right_leg', [4.4, 3.2, 0.4], [4.75, 4.6, 3.4], WH, rot=[-32, 0, 0], origin=[4.6, 3.2, 0.4])
    f.add('left_leg', [-4.8, 2.6, -0.6], [-4.4, 4.2, 2.4], GOLD, rot=[-20, 0, 0], origin=[-4.6, 2.6, -0.6])
    f.add('left_leg', [-4.75, 3.2, 0.4], [-4.4, 4.6, 3.4], WH, rot=[-32, 0, 0], origin=[-4.6, 3.2, 0.4])
    return f

def export_armor(fit, template, path):
    src = json.load(open(ARMOR + template))
    out = {'texture_size': 256, 'bones': {}}
    for b, node in src['bones'].items():
        out['bones'][b] = {'pivot': node['pivot'], 'cubes': [], 'children': []}
    n = 0
    for c in fit.cubes:
        P = BB_PIV[c['bone']]; node = out['bones'][c['bone']]
        lo, hi = c['lo'], c['hi']
        size3 = r4([hi[k] - lo[k] for k in range(3)])
        if c['rot']:
            o = c['origin']
            frm = r4([o[0] - hi[0], o[1] - hi[1], lo[2] - o[2]])
            r = c['rot']
            node['children'].append({'name': f'o{n}', 'pivot': r4([P[0] - o[0], P[1] - o[1], o[2] - P[2]]),
                                     'rot': r4([-math.radians(r[0]), -math.radians(r[1]), math.radians(r[2])]),
                                     'cubes': [{'uv': c['uv'], 'from': frm, 'size': size3}], 'children': []})
            n += 1
        else:
            node['cubes'].append({'uv': c['uv'], 'from': r4([P[0] - hi[0], P[1] - hi[1], lo[2] - P[2]]), 'size': size3})
    json.dump(out, open(path, 'w'), separators=(',', ':'))

atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
parts = [('helmet', helmet(), (0, 0, 256, 40)), ('chestplate', chest(), (0, 40, 256, 150)),
         ('leggings', legs(), (0, 150, 256, 205)), ('boots', boots(), (0, 205, 256, 256))]
for name, f, region in parts:
    pack(f, atlas, region, seed=len(name))
    for ch in ('mikiko', 'rhalix'):
        export_armor(f, f'{ch}_{name}.json', ARMOR + f'{ch}_sky_{name}.json')
atlas.save(CHAR + 'sky_armor.png')
print('ok', [len(f.cubes) for _, f, _ in parts])
