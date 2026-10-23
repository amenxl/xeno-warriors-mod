"""Corrupted Armor: a jagged black-and-purple 3D set for Mikiko and Rhalix (shared atlas)."""
import sys
sys.path.insert(0, '/home/claude/xw/sky')
exec(open('/home/claude/xw/sky/sky_armor.py').read().split("atlas = Image.new")[0])   # reuse kit + export_armor

BK, BKD, PU, PUD, PUL, GL = '#1d1624', '#120d18', '#5a1a8a', '#3c1060', '#8a3cc8', '#d690ff'
veins = lambda base: (lambda x, y: PU if (abs(x - .5 - 0.3 * __import__('math').sin(y * 9)) < .07) else base)

def c_helmet():
    f = Fit()
    f.shell('head', [-4, 27.4, -4], [4, 32, 4], 0.45, BK, {'bottom': None, 'front': None})       # open face
    f.add('head', [-4.45, 30.4, -4.6], [4.45, 32.45, -4.4], BK, {'front': veins(BK)})          # brow plate
    f.add('head', [-4.45, 27.4, -4.6], [-2.9, 30.4, -4.4], BK)                                  # cheek guards
    f.add('head', [2.9, 27.4, -4.6], [4.45, 30.4, -4.4], BK)
    f.add('head', [-4.5, 30.2, -4.7], [4.5, 30.6, -4.5], PUL)                         # brow ridge
    f.add('head', [-0.5, 31.2, -4.75], [0.5, 32.1, -4.55], GL, shade=False)           # eye of the soul
    for side in (1, -1):                                                              # horns
        x = 3.2 if side > 0 else -3.2
        f.add('head', [x - 0.6, 32.2, -1.2], [x + 0.6, 33.6, 0.0], BK, rot=[0, 0, side * -18], origin=[x, 32.2, -0.6])
        f.add('head', [x + side * 0.2 - 0.4, 33.4, -1.0], [x + side * 0.2 + 0.4, 35.0, -0.2], PUD, rot=[15, 0, side * -32], origin=[x, 33.4, -0.6])
    return f

def c_chest():
    f = Fit()
    f.add('body', [-4.25, 12.4, -2.65], [4.25, 24.25, -2.1], BK, {'front': veins(BK)})
    f.add('body', [-4.25, 12.4, 2.1], [4.25, 24.25, 2.65], BK, {'back': veins(BK)})
    f.add('body', [-4.4, 12.4, -2.1], [-4.0, 24.25, 2.1], BKD)
    f.add('body', [4.0, 12.4, -2.1], [4.4, 24.25, 2.1], BKD)
    f.add('body', [-1.1, 18.6, -2.95], [1.1, 20.8, -2.6], GL, shade=False)           # corrupted core
    f.add('body', [-1.6, 18.1, -2.8], [1.6, 21.3, -2.6], PU)
    for i, y in enumerate((14.0, 15.6)):                                             # ribs
        f.add('body', [-3.6, y, -2.85], [-1.8, y + 0.6, -2.6], PUD)
        f.add('body', [1.8, y, -2.85], [3.6, y + 0.6, -2.6], PUD)
    for x in (-2.6, 0.0, 2.6):                                                       # spine spikes
        f.add('body', [x - 0.35, 19.0, 2.65], [x + 0.35, 22.4, 3.8], BK, rot=[-30, 0, 0], origin=[x, 19.0, 2.65])
    f.both('right_arm', [3.3, 20.6, -2.7], [8.7, 25.0, 2.7], BK, {'top': veins(BK), 'right': veins(BK)})   # pauldrons
    f.both('right_arm', [3.2, 20.2, -2.8], [8.8, 20.6, 2.8], PU)
    f.both('right_arm', [7.4, 24.6, -0.6], [8.4, 26.6, 0.6], PUD, rot=[0, 0, -20], origin=[7.9, 24.6, 0])
    f.both('right_arm', [3.65, 12.6, -2.35], [8.35, 17.0, 2.35], BK, {'top': None, 'bottom': None})       # gauntlets
    f.both('right_arm', [8.3, 13.5, -0.8], [9.1, 16.2, 0.8], PU)
    return f

def c_legs():
    f = Fit()
    f.shell('body', [-4, 11.6, -2], [4, 13.2, 2], 0.45, PUD)
    f.add('body', [-1.0, 11.6, -2.75], [1.0, 13.2, -2.45], BK)
    f.add('body', [-0.45, 12.05, -2.9], [0.45, 12.75, -2.7], GL, shade=False)
    f.both('right_leg', [-0.4, 5.0, -2.4], [4.4, 11.8, 2.4], BK, {'top': None, 'bottom': None, 'front': veins(BK)})
    f.both('right_leg', [0.3, 4.4, -2.85], [3.7, 6.8, -2.35], PUD)                 # spiked knees
    f.both('right_leg', [1.4, 5.0, -3.6], [2.6, 6.2, -2.8], BK, rot=[25, 0, 0], origin=[2.0, 5.6, -2.85])
    return f

def c_boots():
    f = Fit()
    f.both('right_leg', [-0.4, 0.0, -2.4], [4.4, 4.8, 2.4], BK, {'top': None, 'front': veins(BK)})
    f.both('right_leg', [-0.45, -0.32, -3.6], [4.45, 0.45, 2.5], BKD)
    f.both('right_leg', [-0.4, 0.0, -3.6], [4.4, 1.7, -2.4], PUD)
    f.both('right_leg', [-0.45, 4.3, -2.45], [4.45, 4.9, 2.45], PU, {'top': None, 'bottom': None})
    f.both('right_leg', [1.6, 0.4, 2.4], [2.4, 2.6, 3.6], BK, rot=[-35, 0, 0], origin=[2.0, 1.0, 2.4])   # heel spur
    return f

atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
parts = [('helmet', c_helmet(), (0, 0, 256, 50)), ('chestplate', c_chest(), (0, 50, 256, 160)),
         ('leggings', c_legs(), (0, 160, 256, 210)), ('boots', c_boots(), (0, 210, 256, 256))]
for name, f, region in parts:
    pack(f, atlas, region, seed=len(name) + 7)
    for ch in ('mikiko', 'rhalix'):
        export_armor(f, f'{ch}_{name}.json', ARMOR + f'{ch}_corrupted_{name}.json')
atlas.save(CHAR + 'corrupted_armor.png')
print('ok', [len(f.cubes) for _, f, _ in parts])
