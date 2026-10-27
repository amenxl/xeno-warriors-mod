"""Kaeru (Hekublade guardian, Orenji clan): brown bob fading to orange, freckles, amber eyes; black cropped hoodie with
an orange hood (down) and rolled sleeves, black fingerless gloves, orange shorts with the black Orenji spiral, black
thigh-high socks, black shoes with orange stripes, and two fox tails (orange and black)."""
import sys
sys.path.insert(0, '/home/claude/xw/ultima'); sys.path.insert(0, '/home/claude/xw/npcs'); sys.path.insert(0, '/home/claude/xw/outfits'); sys.path.insert(0, '/home/claude/xw/detail')
import ultima as U
import npcs, core
from npcs import hair, shell, Fit, pack, paint_skin, face_img, fringe_locks, Adapter
from PIL import Image, ImageDraw

SKIN, EYES = '#a86a4a', '#e8901c'
BLK, BLK2, OR, OR2, OR3 = '#222024', '#2e2b30', '#d4661e', '#a84a14', '#f08a3a'
HAIR_T, HAIR_M, HAIR_E = '#3c2414', '#6a3418', '#a2541e'

def spiral(x, y):
    """The black Orenji spiral (on the shorts)."""
    import math
    dx, dy = x - .5, y - .5
    r = math.hypot(dx, dy); a = math.atan2(dy, dx)
    if r > .46: return None
    k = (r * 11 - a / math.pi) % 2
    return BLK if k < 0.75 or r < .07 else None

def paint(part, face, x, y, W, H, outer):
    if part == 'body':
        if y >= 9: return OR                                            # shorts (high waist)
        return BLK
    if part in ('rarm', 'larm'):
        if y < 5: return BLK                                            # rolled sleeve
        if y >= 10: return BLK2                                         # fingerless gloves
        return None
    if part in ('rleg', 'lleg'):
        if face == 'top': return OR
        if y < 5: return OR
        if y == 5: return BLK
        if y == 6: return None                                          # bare knee
        if y >= 10: return BLK if not (face == 'front' and y == 10) else OR   # shoes
        return BLK                                                      # thigh-high socks
    return None

def build(f):
    # brown bob, darker on top, orange-brown at the ends, messy fringe and side locks
    hair(f, HAIR_T, HAIR_M, back_len=22, side_len=23,
         locks=fringe_locks(6, 31, 7.8, 4.4, 1.7, 0.35)
         + [([-1.6, 31.8, -4.7], [0.35, -1, -0.15], 3.4, 2.0)]
         + [([s * 4.6, 30, z], [s * 0.35, -1, 0.15], 5.6, 1.8) for s in (1, -1) for z in (-2.6, 0.2, 3.0)]
         + [([x, 30, 4.7], [0, -1, 0.25], 6.6, 2.3) for x in (-2.8, 0, 2.8)])
    for s in (1, -1):                                                   # orange-brown ends
        for z in (-2.6, 0.2, 3.0):
            f.add('head', [s * 4.9 - 0.8, 23.0, z - 0.8], [s * 4.9 + 0.8, 24.6, z + 0.8], HAIR_E)
    f.add('head', [-2.4, 32.6, -1.2], [2.4, 33.1, 0.2], '#8a6a5a')                 # shine
    # cropped black hoodie, orange hood down behind the neck, orange drawstring edge at the collar
    shell(f, 'body', [-4, 14.6, -2], [4, 24, 2], 0.36, BLK, {'front': lambda x, y: BLK2 if y > .82 else BLK})
    f.add('body', [-4.4, 14.4, -2.4], [4.4, 15.2, 2.4], BLK2)                     # ribbed hem
    f.add('body', [-3.9, 20.2, 2.2], [3.9, 24.7, 4.3], OR, {'back': lambda x, y: OR2 if y > .7 else OR, 'top': OR2})   # hood (down)
    f.add('body', [-3.0, 23.6, -2.55], [3.0, 24.6, 2.4], OR2)                     # hood lining at the collar
    # rolled short sleeves and black fingerless gloves
    f.both('right_arm', [3.5, 19.4, -2.5], [8.5, 24.4, 2.5], BLK)
    f.both('right_arm', [3.4, 18.6, -2.6], [8.6, 19.8, 2.6], BLK2)                 # the roll
    f.both('right_arm', [3.5, 12.0, -2.5], [8.5, 14.8, 2.5], BLK2, {'bottom': '#a86a4a'})
    f.both('right_arm', [3.4, 14.4, -2.6], [8.6, 15.2, 2.6], BLK)                  # glove cuff
    # orange shorts: high waist, baggy, black cuffs, the spiral on her left leg
    shell(f, 'body', [-4, 9.6, -2], [4, 14.8, 2], 0.42, OR, {'front': lambda x, y: OR2 if abs(x - .5) < .03 else OR})
    f.both('right_leg', [-0.55, 6.0, -2.55], [4.55, 12.3, 2.55], OR)
    # the Orenji spiral on her left leg (squared off, inked in black bars)
    X0, X1, Y0, Y1, Z = -4.2, -0.8, 7.4, 10.8, -2.62
    for lo, hi in (([X0, Y1 - 0.45], [X1, Y1]), ([X1 - 0.45, Y0], [X1, Y1]), ([X0 + 0.9, Y0], [X1, Y0 + 0.45]), ([X0 + 0.9, Y0], [X0 + 1.35, Y1 - 1.3]),
                   ([X0 + 0.9, Y1 - 1.75], [X1 - 1.3, Y1 - 1.3]), ([X1 - 1.75, Y0 + 1.3], [X1 - 1.3, Y1 - 1.3]), ([X0 + 2.0, Y0 + 1.3], [X1 - 1.3, Y0 + 1.75])):
        f.add('left_leg', [lo[0], lo[1], Z], [hi[0], hi[1], Z + 0.06], BLK)
    f.both('right_leg', [-0.65, 5.6, -2.65], [4.65, 6.6, 2.65], BLK)               # cuffs
    # thigh-high socks and shoes with an orange stripe
    f.both('right_leg', [-0.3, 1.8, -2.3], [4.3, 5.0, 2.3], BLK)
    f.both('right_leg', [-0.36, 0, -2.6], [4.36, 2.0, 2.36], '#1a181c', {'front': lambda x, y: OR if .35 < y < .6 else '#1a181c',
                                                                          'right': lambda x, y: OR if .3 < y < .55 and x < .7 else '#1a181c'})
    f.both('right_leg', [0.1, 0, -3.15], [3.9, 1.2, -2.6], '#1a181c')
    f.both('right_leg', [-0.44, 0, -3.3], [4.44, 0.35, 2.44], '#101012')
    # two fox tails from the lower back: orange (big, fluffy) and black
    m = Adapter(f)
    core.lock(m, 'Body', [1.0, 11.2, 2.4], [0.4, -1.0, 0.75], 11.0, 4.6, 3.8, (OR, OR3, OR2), wide='x', taper=(0.85, 0.5), tip_len=(0.35, 0.2))
    core.lock(m, 'Body', [-1.2, 11.4, 2.4], [-0.5, -0.9, 0.8], 9.5, 3.6, 3.0, (BLK, BLK2, '#3a3640'), wide='x', taper=(0.8, 0.45), tip_len=(0.35, 0.2))

SPEC = dict(skin=SKIN, eyes=EYES, hair=[HAIR_T, HAIR_M], paint=paint,
            brow=['.........', '.kkkkkk..', 'k........'],
            eye=['..kkkkkk.', 'kkkkkkkkk', '.wOOooOw.', '.wWOpoOw.', '.wOoopOw.', '..wOOOw..', '..kkkkk..'],
            mouth=['k......k', '.kkkkkk.', '...rr...'], mouth_x=12, mouth_y=24)

def freckles(px, cm, dark):
    for x, y in [(4, 22), (6, 23), (5, 24), (7, 22), (24, 22), (26, 23), (25, 24), (27, 22)]: px[x, y] = (110, 62, 40, 255)
SPEC['face_extra'] = [freckles]

def make():
    fit = Fit(); build(fit)
    atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); pack(fit, atlas, region=(0, 0, 256, 256))
    skin = paint_skin(SPEC)
    return fit, atlas, skin

if __name__ == '__main__':
    fit, atlas, skin = make()
    import json
    mj = npcs.export(fit, '/tmp/kaeru_model.json')
    # the 'r' tongue colour isn't in face_img's map: patch it in after
    face = face_img(dict(SPEC, mouth=['k......k', '.kkkkkk.']))
    px = face.load()
    for x in (15, 16): px[x, 26] = (220, 96, 104, 255)
    tiles = [U.preview(mj, skin, atlas, face, yaws=(25, 160), scale=8)]
    tiles.append(U.preview(mj, skin, atlas, face, yaws=(0,), scale=16).crop((0, 0, 600, 300)))
    w = max(t.width for t in tiles); h = sum(t.height for t in tiles)
    S = Image.new('RGB', (w, h), (30, 32, 40)); y = 0
    for t in tiles: S.paste(t, (0, y)); y += t.height
    S.save('/tmp/kaeru_preview.png'); skin.save('/tmp/kaeru_skin.png'); atlas.save('/tmp/kaeru_details.png'); face.save('/tmp/kaeru_face.png')
    print(S.size)
