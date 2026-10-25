"""Vanilla armor, each material its own 3D cut for Mikiko and Rhalix (shared body size).
Writes models/armor/<char>_<mat>_<slot>.json and textures/entity/character/vanilla_<mat>.png.
Face stays open (no visor over the eyes)."""
import sys, json, math
sys.path.insert(0, '/home/claude/xw/outfits'); sys.path.insert(0, '/home/claude/xw/sky')
from kit import *
from PIL import Image
ARMOR = RES + 'models/armor/'
NO_TB = {'top': None, 'bottom': None}

def stripes(a, b, n=6, vertical=False):
    return lambda x, y: a if int((x if vertical else y) * n) % 2 == 0 else b
def mail(a, b):          # chainmail rings
    return lambda x, y: b if (int(x * 14) + int(y * 14)) % 2 == 0 or int(y * 14) % 3 == 0 else a
def trim(main, edge, t=.12):
    return lambda x, y: edge if y < t or y > 1 - t or x < t * .6 or x > 1 - t * .6 else main
def rim_bottom(main, edge, t=.2):
    return lambda x, y: edge if y > 1 - t else main

# ------------------------------------------------------------------ leather (painted light, the dye tints it)
def leather():
    L, LD, ST, BR = '#f2e6d4', '#cdb89c', '#a8916f', '#8a7356'
    h = Fit()
    h.shell('head', [-4, 28.6, -4], [4, 32, 4], 0.45, L, {'bottom': None, 'front': rim_bottom(L, ST, .25)})
    for s in (1, -1):                                   # ear flaps
        h.add('head', [s * 4.3, 25.4, -1.6], [s * 4.75, 28.8, 1.8], LD)
        h.add('head', [s * 4.35, 25.0, -1.0], [s * 4.7, 25.4, 1.2], ST)
    h.add('head', [-4.4, 28.4, 3.9], [4.4, 30.2, 4.6], LD)                 # back strap
    c = Fit()
    c.add('body', [-4.2, 12.6, -2.6], [4.2, 24.2, 2.6], L, {'top': None, 'bottom': None,
          'front': lambda x, y: ST if abs(x - .5) < .04 and int(y * 10) % 2 == 0 else (LD if y > .82 else L)})
    for z in (-2.85, 2.6):                                                 # crossing straps
        c.add('body', [-3.2, 17.0, z], [3.2, 17.9, z + .25], BR, rot=[0, 0, 35], origin=[0, 17.5, z])
    c.shell('body', [-4, 12.6, -2], [4, 13.8, 2], 0.7, BR)                 # belt
    c.add('body', [-0.8, 12.4, -2.95], [0.8, 14.0, -2.7], ST)
    c.both('right_arm', [3.4, 20.6, -2.6], [8.6, 24.5, 2.6], LD, {'bottom': None, 'right': rim_bottom(LD, ST)})   # soft pad
    c.both('right_arm', [3.6, 12.5, -2.35], [8.4, 15.5, 2.35], L, {**NO_TB, 'front': stripes(L, ST, 5)})          # wrapped bracer
    c.both('right_arm', [3.55, 16.3, -2.3], [8.45, 21.0, 2.3], L, NO_TB)   # upper arm
    l = Fit()
    l.both('right_leg', [-0.35, 5.0, -2.35], [4.35, 12.2, 2.35], L, {**NO_TB, 'front': lambda x, y: LD if .3 < y < .55 and .2 < x < .8 else L})
    l.both('right_leg', [-0.4, 9.2, -2.45], [4.4, 9.8, 2.45], BR, NO_TB)                                          # garter strap
    b = Fit()
    b.both('right_leg', [-0.4, 0, -2.4], [4.4, 5.2, 2.4], L, {'top': None, 'front': stripes(L, LD, 6)})
    b.both('right_leg', [-0.45, 4.9, -2.6], [4.45, 5.6, 2.6], LD, NO_TB)                                          # folded cuff
    b.both('right_leg', [-0.45, -0.3, -3.1], [4.45, 0.4, 2.5], BR)
    return h, c, l, b

# ------------------------------------------------------------------ chainmail
def chain():
    M, MD, LE, LED = '#b8bcc6', '#7e838e', '#7a5a3c', '#5a4028'
    m = mail(M, MD)
    h = Fit()                                                               # coif: top, sides and back, face open
    h.shell('head', [-4, 29.6, -4], [4, 32, 4], 0.5, M, {'bottom': None, 'front': m, 'back': m, 'left': m, 'right': m, 'top': m})
    for s in (1, -1): h.add('head', [s * 4.0, 24.0, -2.0], [s * 4.55, 29.6, 4.5], M, {'right': m, 'left': m, 'front': m, 'back': m})
    h.add('head', [-4.55, 24.0, 4.0], [4.55, 29.6, 4.55], M, {'back': m, 'front': m})
    h.shell('body', [-4, 23.2, -2], [4, 24.4, 2], 0.75, M, {'front': m, 'back': m})   # mail collar on the shoulders
    c = Fit()
    c.add('body', [-4.25, 10.6, -2.6], [4.25, 24.1, 2.6], M, {'top': None, 'bottom': None, 'front': m, 'back': m, 'left': m, 'right': m})  # hauberk, to the thigh
    c.shell('body', [-4, 13.0, -2], [4, 13.9, 2], 0.72, LE)
    c.add('body', [-0.6, 12.8, -2.95], [0.6, 14.1, -2.7], '#c9cdd6')
    c.both('right_arm', [3.6, 13.0, -2.35], [8.4, 24.3, 2.35], M, {'bottom': None, 'front': m, 'back': m, 'right': m, 'left': m, 'top': m})
    c.both('right_arm', [3.55, 12.4, -2.45], [8.45, 13.4, 2.45], LED, NO_TB)
    c.both('right_arm', [3.55, 16.3, -2.3], [8.45, 21.0, 2.3], M, {**NO_TB, 'front': m, 'back': m, 'right': m, 'left': m})   # upper arm
    l = Fit()
    l.both('right_leg', [-0.35, 4.5, -2.35], [4.35, 11.6, 2.35], M, {**NO_TB, 'front': m, 'back': m, 'right': m, 'left': m})
    l.both('right_leg', [0.4, 4.4, -2.7], [3.6, 6.4, -2.35], '#c9cdd6')                                           # knee cop
    b = Fit()
    b.both('right_leg', [-0.4, 0, -2.4], [4.4, 4.8, 2.4], M, {'top': None, 'front': m, 'back': m, 'right': m, 'left': m})
    b.both('right_leg', [-0.45, -0.3, -3.2], [4.45, 0.5, 2.5], LED)
    b.both('right_leg', [-0.4, 0, -3.2], [4.4, 1.6, -2.4], LE)
    return h, c, l, b

# ------------------------------------------------------------------ iron (knight plate, open nasal helm)
def iron():
    I, ID, IL, LE = '#dfe2e8', '#9da3ad', '#f6f7fa', '#6a4e34'
    h = Fit()
    h.shell('head', [-4, 29.2, -4], [4, 32, 4], 0.55, I, {'bottom': None})
    h.add('head', [-0.5, 33.0, -4.0], [0.5, 33.7, 4.0], IL)                 # ridge
    h.add('head', [-0.45, 26.3, -4.95], [0.45, 29.4, -4.5], I)              # nasal guard (between the eyes)
    for s in (1, -1):                                                       # cheek plates beside the face
        h.add('head', [s * 3.0, 24.6, -4.7], [s * 4.6, 29.2, -1.0], ID, {'front': trim(ID, I, .1)})
        h.add('head', [s * 4.0, 24.6, -1.0], [s * 4.6, 29.2, 4.6], I)
    h.add('head', [-4.6, 24.6, 4.0], [4.6, 29.2, 4.6], I)                   # neck guard
    c = Fit()
    c.add('body', [-4.2, 13.4, -2.75], [4.2, 24.2, 2.6], I, {'top': None, 'bottom': None,
          'front': lambda x, y: IL if abs(x - .5) < .05 else (ID if y > .85 or y < .06 else I)})
    c.add('body', [-0.4, 15.0, -3.05], [0.4, 23.0, -2.7], IL)               # centre ridge
    for i, y in enumerate((12.2, 11.0)):                                    # faulds
        c.add('body', [-4.4 + i * .2, y, -2.9], [4.4 - i * .2, y + 1.3, 2.75], ID if i else I, {'top': None, 'bottom': None})
    c.shell('body', [-4, 13.4, -2], [4, 14.2, 2], 0.85, LE)
    for k, (y0, y1, out) in enumerate(((21.6, 25.0, 0), (20.1, 22.0, .25), (18.7, 20.5, .5))):   # layered pauldrons
        c.both('right_arm', [3.3 - out * .1, y0, -2.65 - out], [8.7 + out, y1, 2.65 + out], I if k % 2 == 0 else ID,
               {'bottom': None, 'right': rim_bottom(I if k % 2 == 0 else ID, IL, .2)})
    c.both('right_arm', [3.6, 12.6, -2.4], [8.4, 16.6, 2.4], I, NO_TB)      # vambrace
    c.both('right_arm', [3.5, 12.2, -2.55], [8.5, 13.1, 2.55], ID, NO_TB)
    c.both('right_arm', [3.9, 16.9, -2.9], [8.1, 18.4, -2.4], ID)           # couter
    c.both('right_arm', [3.55, 16.3, -2.3], [8.45, 21.0, 2.3], ID, NO_TB)   # upper arm
    l = Fit()
    l.both('right_leg', [-0.4, 5.5, -2.45], [4.4, 11.6, 2.45], I, NO_TB)    # cuisses
    l.both('right_leg', [0.0, 8.5, -2.8], [4.0, 12.0, -2.45], ID)           # tasset
    l.both('right_leg', [0.3, 4.3, -2.95], [3.7, 6.6, -2.45], IL)           # poleyn
    l.both('right_leg', [3.9, 4.6, -1.0], [4.65, 6.3, 1.0], ID)
    b = Fit()
    b.both('right_leg', [-0.45, 0, -2.45], [4.45, 4.6, 2.45], I, {'top': None})
    for k in range(3):                                                      # sabaton lames
        b.both('right_leg', [-0.45, 0, -3.5 + k * .35], [4.45, 0.8 + k * .55, -2.45], ID if k % 2 else IL)
    b.both('right_leg', [-0.5, -0.32, -3.5], [4.5, 0.25, 2.6], '#55585f')
    return h, c, l, b

# ------------------------------------------------------------------ gold (ornate, crowned)
def gold():
    G, GD, GL, RED, GEM = '#ffd75e', '#c79a26', '#fff2b0', '#b0262e', '#4fe0ff'
    h = Fit()
    h.shell('head', [-4, 29.6, -4], [4, 31.4, 4], 0.55, G, {'top': None, 'bottom': None, 'front': trim(G, GD, .2)})
    for i in range(8):                                                      # crown points
        a = i * math.pi / 4; x, z = math.sin(a) * 4.3, -math.cos(a) * 4.3
        h.add('head', [x - .45, 31.9, z - .45], [x + .45, 33.6 if i % 2 == 0 else 32.8, z + .45], G if i % 2 == 0 else GL)
    h.add('head', [-0.55, 30.0, -4.95], [0.55, 31.1, -4.6], RED, shade=False)
    for s in (1, -1): h.add('head', [s * 4.5, 27.0, -1.0], [s * 4.75, 30.2, 1.5], GD)     # temple pieces
    c = Fit()
    sun = lambda x, y: GL if (x - .5) ** 2 + (y - .45) ** 2 * 1.5 < .025 else (GD if abs(x - .5) + abs(y - .45) * .8 < .32 and int((math.atan2(y - .45, x - .5) * 6 / math.pi) + 12) % 2 == 0 else G)
    c.add('body', [-4.2, 13.6, -2.7], [4.2, 24.2, 2.6], G, {'top': None, 'bottom': None, 'front': sun, 'back': trim(G, GD)})
    c.add('body', [-0.6, 18.8, -3.0], [0.6, 20.0, -2.7], RED, shade=False)
    c.shell('body', [-4, 12.6, -2], [4, 13.8, 2], 0.8, GD)
    c.add('body', [-1.6, 9.6, -2.9], [1.6, 12.8, -2.7], RED, {'front': lambda x, y: G if y > .82 or x < .1 or x > .9 else RED})   # tabard flap
    c.both('right_arm', [3.2, 20.4, -2.9], [9.0, 24.8, 2.9], G, {'bottom': None, 'right': rim_bottom(G, GL, .22), 'top': trim(G, GD, .15)})
    c.both('right_arm', [8.9, 21.6, -2.0], [9.4, 24.0, 2.0], GD)
    c.both('right_arm', [3.6, 12.6, -2.4], [8.4, 16.4, 2.4], G, {**NO_TB, 'front': stripes(G, GD, 4)})
    c.add('body', [-4.6, 22.0, 2.4], [4.6, 23.6, 2.8], RED)                 # cape clasp bar
    c.both('right_arm', [3.55, 16.3, -2.3], [8.45, 21.0, 2.3], GD, NO_TB)   # upper arm
    l = Fit()
    l.both('right_leg', [-0.4, 5.4, -2.45], [4.4, 12.0, 2.45], G, {**NO_TB, 'front': lambda x, y: GD if abs(x - .5) < .08 else G})
    l.both('right_leg', [0.3, 4.4, -2.95], [3.7, 6.7, -2.45], GL)
    l.both('right_leg', [1.4, 5.0, -3.15], [2.6, 6.1, -2.9], GEM, shade=False)
    b = Fit()
    b.both('right_leg', [-0.45, 0, -2.45], [4.45, 4.8, 2.45], G, {'top': None, 'front': trim(G, GD, .15)})
    b.both('right_leg', [-0.5, -0.3, -3.4], [4.5, 0.4, 2.6], GD)
    b.both('right_leg', [-0.45, 0, -3.4], [4.45, 1.5, -2.45], GL)
    for s, bone in ((1, 'right_leg'), (-1, 'left_leg')):                    # ankle wings
        x = 4.45 if s > 0 else -4.45
        b.add(bone, [x, 2.6, -0.5], [x + s * .45, 4.4, 2.6], GL, rot=[-25, 0, 0], origin=[x, 2.6, -0.5])
    return h, c, l, b

# ------------------------------------------------------------------ diamond (faceted crystal)
def diamond():
    D, DD, DL, DG = '#6ff0f2', '#2aaab8', '#d8ffff', '#1d6f80'
    facet = lambda x, y: DL if (x + y) % .5 < .08 else (DD if (x - y) % .5 < .1 else D)
    h = Fit()
    h.shell('head', [-4, 29.4, -4], [4, 32, 4], 0.55, D, {'bottom': None, 'front': facet, 'top': facet, 'left': facet, 'right': facet, 'back': facet})
    h.add('head', [-1.0, 30.0, -5.0], [1.0, 33.4, -4.5], DL, rot=[0, 0, 45], origin=[0, 31.4, -4.7])   # crystal on the brow
    for s in (1, -1):                                                       # crystal horns sweeping back
        x = 3.0 * s
        h.add('head', [x - .6, 32.2, -1.0], [x + .6, 35.6, 0.2], D, rot=[-35, 0, s * -15], origin=[x, 32.2, -0.4])
        h.add('head', [x - .35, 34.8, 1.0], [x + .35, 36.6, 1.8], DL, rot=[-55, 0, s * -15], origin=[x, 34.8, 1.4])
        h.add('head', [s * 4.0, 25.0, -2.0], [s * 4.6, 29.4, 4.6], DD)
    c = Fit()
    c.add('body', [-4.2, 13.4, -2.7], [4.2, 24.2, 2.6], D, {'top': None, 'bottom': None, 'front': facet, 'back': facet})
    for s in (1, -1):                                                       # angled chest facets
        c.add('body', [s * 0.3, 17.5, -3.1], [s * 3.6, 22.8, -2.7], DL if s > 0 else D, rot=[0, s * 12, 0], origin=[s * 2, 20, -2.9])
    c.add('body', [-0.8, 15.0, -3.2], [0.8, 17.0, -2.7], DG, rot=[0, 0, 45], origin=[0, 16, -2.9])
    c.shell('body', [-4, 12.8, -2], [4, 13.8, 2], 0.8, DG)
    c.both('right_arm', [3.3, 20.6, -2.8], [8.8, 24.8, 2.8], D, {'bottom': None, 'top': facet, 'right': facet})
    for k, z in enumerate((-1.8, 0.0, 1.8)):                                # shoulder shards
        c.both('right_arm', [6.4, 24.6, z - .45], [7.3, 26.6 - abs(z) * .4, z + .45], DL if k == 1 else D, rot=[0, 0, -20], origin=[6.8, 24.6, z])
    c.both('right_arm', [3.6, 12.6, -2.4], [8.4, 16.8, 2.4], D, {**NO_TB, 'front': facet, 'right': facet})
    c.both('right_arm', [8.3, 13.6, -0.5], [9.2, 16.2, 0.5], DL)            # forearm blade fin
    c.both('right_arm', [3.55, 16.3, -2.3], [8.45, 21.0, 2.3], DD, NO_TB)   # upper arm
    l = Fit()
    l.both('right_leg', [-0.4, 5.4, -2.45], [4.4, 12.0, 2.45], D, {**NO_TB, 'front': facet, 'right': facet, 'left': facet})
    l.both('right_leg', [0.4, 4.2, -3.2], [3.6, 7.0, -2.45], DL, rot=[0, 0, 0])
    b = Fit()
    b.both('right_leg', [-0.45, 0, -2.45], [4.45, 4.8, 2.45], D, {'top': None, 'front': facet})
    b.both('right_leg', [-0.5, -0.3, -3.6], [4.5, 0.4, 2.6], DG)
    b.both('right_leg', [0.2, 0, -3.6], [3.8, 1.8, -2.45], DL)
    return h, c, l, b

# ------------------------------------------------------------------ netherite (heavy, horned, spiked)
def netherite():
    N, ND, NL, PU, EM = '#4a4448', '#2a2629', '#6e6670', '#5b3f62', '#ff7a2a'
    grain = lambda x, y: NL if int(x * 8 + y * 3) % 5 == 0 else (ND if int(y * 9) % 4 == 0 else N)
    h = Fit()
    h.shell('head', [-4, 29.0, -4], [4, 32, 4], 0.65, N, {'bottom': None, 'top': grain, 'front': trim(N, PU, .12), 'left': grain, 'right': grain, 'back': grain})
    h.add('head', [-0.55, 25.8, -5.2], [0.55, 29.2, -4.6], ND)              # nasal
    for s in (1, -1):
        h.add('head', [s * 3.2, 24.4, -4.8], [s * 4.7, 29.0, 4.7], N, {'front': grain, 'left': grain, 'right': grain})
        h.add('head', [s * 4.5, 30.0, -1.4], [s * 6.6, 31.4, 0.0], ND)                                             # horn base
        h.add('head', [s * 6.0, 30.4, -1.2], [s * 6.9, 34.2, -0.2], NL, rot=[0, 0, s * -18], origin=[s * 6.4, 30.4, -0.7])
    h.add('head', [-4.7, 24.4, 4.1], [4.7, 29.0, 4.7], N)
    c = Fit()
    c.add('body', [-4.3, 12.8, -2.85], [4.3, 24.2, 2.7], N, {'top': None, 'bottom': None, 'front': grain, 'back': grain})
    for y in (16.0, 19.0):                                                  # plate bands
        c.add('body', [-4.5, y, -3.05], [4.5, y + 0.9, 2.9], ND, NO_TB)
    c.add('body', [-0.9, 20.6, -3.15], [0.9, 22.4, -2.85], EM, shade=False) # ember core
    c.shell('body', [-4, 12.4, -2], [4, 13.6, 2], 0.95, ND)
    for k, (y0, y1, out) in enumerate(((21.2, 25.4, 0), (19.4, 21.6, .4))):
        c.both('right_arm', [3.1, y0, -3.0 - out], [9.2 + out, y1, 3.0 + out], N if k == 0 else ND, {'bottom': None, 'top': grain, 'right': grain})
    for z in (-1.6, 0.6):                                                   # pauldron spikes
        c.both('right_arm', [6.2, 25.2, z], [7.2, 27.8, z + 1.0], NL, rot=[0, 0, -22], origin=[6.7, 25.2, z + .5])
    c.both('right_arm', [3.5, 12.4, -2.55], [8.5, 17.0, 2.55], N, {**NO_TB, 'front': grain})
    c.both('right_arm', [8.4, 13.5, -0.4], [9.4, 15.0, 0.4], NL)
    c.both('right_arm', [3.55, 16.3, -2.3], [8.45, 21.0, 2.3], ND, NO_TB)   # upper arm
    l = Fit()
    l.both('right_leg', [-0.45, 5.2, -2.55], [4.45, 12.2, 2.55], N, {**NO_TB, 'front': grain, 'right': grain})
    l.both('right_leg', [0.2, 4.0, -3.15], [3.8, 7.0, -2.55], ND)
    l.both('right_leg', [1.5, 5.0, -3.6], [2.5, 6.0, -3.1], NL)             # knee spike
    b = Fit()
    b.both('right_leg', [-0.5, 0, -2.55], [4.5, 5.0, 2.55], N, {'top': None, 'front': grain})
    b.both('right_leg', [-0.55, -0.35, -3.8], [4.55, 0.45, 2.7], ND)
    b.both('right_leg', [-0.5, 0, -3.8], [4.5, 2.0, -2.55], N)
    return h, c, l, b

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
            o = c['origin']; r = c['rot']
            node['children'].append({'name': f'o{n}', 'pivot': r4([P[0] - o[0], P[1] - o[1], o[2] - P[2]]),
                                     'rot': r4([-math.radians(r[0]), -math.radians(r[1]), math.radians(r[2])]),
                                     'cubes': [{'uv': c['uv'], 'from': r4([o[0] - hi[0], o[1] - hi[1], lo[2] - o[2]]), 'size': size3}], 'children': []})
            n += 1
        else:
            node['cubes'].append({'uv': c['uv'], 'from': r4([P[0] - hi[0], P[1] - hi[1], lo[2] - P[2]]), 'size': size3})
    json.dump(out, open(path, 'w'), separators=(',', ':'))

SETS = {'leather': leather, 'chainmail': chain, 'iron': iron, 'gold': gold, 'diamond': diamond, 'netherite': netherite}
if __name__ == '__main__':
    only = sys.argv[1:] or list(SETS)
    for mat in only:
        h, c, l, b = SETS[mat]()
        atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
        for name, f, region in (('helmet', h, (0, 0, 256, 56)), ('chestplate', c, (0, 56, 256, 160)),
                                ('leggings', l, (0, 160, 256, 208)), ('boots', b, (0, 208, 256, 256))):
            pack(f, atlas, region, seed=len(name) + len(mat))
            for ch in ('mikiko', 'rhalix'):
                export_armor(f, f'{ch}_{name}.json', ARMOR + f'{ch}_{mat}_{name}.json')
        atlas.save(CHAR + f'vanilla_{mat}.png')
        print(mat, [len(f.cubes) for f in (h, c, l, b)])
