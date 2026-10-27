"""Fox King mask (the Fox King Crown) design options for Mikiko. Each option is modelled in the mask's own frame
(facing -z, centred on the origin) and placed on his head; previews swap it in for the current small side mask."""
import sys, json, copy, math
sys.path.insert(0, '/home/claude/xw/ultima'); sys.path.insert(0, '/home/claude/xw/npcs'); sys.path.insert(0, '/home/claude/xw/outfits'); sys.path.insert(0, '/home/claude/xw/detail')
import ultima as U
import npcs
from npcs import Fit, pack
from PIL import Image

RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
MW, MW2, MR, MR2, MK, GOLD, GOLD2 = '#f6f6f6', '#dcdce4', '#c8141c', '#e8333a', '#141114', '#f0c040', '#c89420'

class Place:
    """Adds cubes given in mask space: scaled by s, rotated by rot (degrees) about the mask centre, moved to t."""
    def __init__(self, f, t, rot=None, s=1.0, bone='head'):
        self.f, self.t, self.rot, self.s, self.bone = f, t, rot, s, bone
    def add(self, lo, hi, col, shade=True):
        s, t = self.s, self.t
        a = [t[i] + lo[i] * s for i in range(3)]; b = [t[i] + hi[i] * s for i in range(3)]
        self.f.add(self.bone, a, b, col, rot=self.rot, origin=list(t) if self.rot else None, shade=shade)
    def flat(self, x0, y0, x1, y1, col, z=-0.06, d=0.05):
        self.add([x0, y0, z], [x1, y1, z + d], col, shade=False)

def mask(P, ears=True, snout=True, markings=True, crown=False, lower=True, eyeholes=False):
    """The kitsune face: a rounded white plate, ears with red insides, a snout with a black nose, red markings."""
    A = P.add
    A([-2.2, -1.6, 0], [2.2, 1.9, 0.55], MW)                                 # the face plate, rounded off in steps
    A([-1.8, 1.9, 0.05], [1.8, 2.5, 0.55], MW)
    A([-2.6, -1.2, 0.1], [-2.2, 1.2, 0.55], MW2); A([2.2, -1.2, 0.1], [2.6, 1.2, 0.55], MW2)   # cheek fluff
    if lower:
        A([-1.6, -2.4, 0.08], [1.6, -1.6, 0.55], MW)
        A([-1.0, -2.9, 0.12], [1.0, -2.4, 0.55], MW2)                       # pointed chin
    if snout:
        A([-0.95, -2.0, -0.8], [0.95, -0.3, 0.0], MW)
        A([-0.65, -1.8, -1.4], [0.65, -0.6, -0.8], MW)
        A([-0.4, -1.15, -1.55], [0.4, -0.7, -1.38], MK, shade=False)          # nose
        A([-0.08, -1.8, -1.42], [0.08, -1.15, -1.38], MK, shade=False)        # philtrum line
    if ears:
        for sx in (1, -1):
            for lo, hi in (((1.0, 2.2), (2.5, 3.3)), ((1.25, 3.3), (2.3, 4.1)), ((1.5, 4.1), (2.05, 4.8)), ((1.65, 4.8), (1.9, 5.2))):
                x0, x1 = sorted([sx * lo[0], sx * hi[0]])
                A([x0, lo[1], 0.1], [x1, hi[1], 0.5], MW)
            for lo, hi in (((1.3, 2.5), (2.2, 3.4)), ((1.5, 3.4), (2.05, 4.2)), ((1.62, 4.2), (1.92, 4.7))):
                x0, x1 = sorted([sx * lo[0], sx * hi[0]])
                A([x0, lo[1], 0.04], [x1, hi[1], 0.1], MR, shade=False)      # red insides
    for sx in (1, -1):                                                       # eyes: black slits angled up at the outer corner
        x0, x1 = sorted([sx * 0.45, sx * 1.75])
        if eyeholes:
            A([x0, 0.35, -0.08], [x1, 0.7, -0.02], MK, shade=False)
            A([x0 + (0.0 if sx > 0 else 0.0), 0.7, -0.08], [x1, 0.85, -0.02], MR, shade=False)
        else:
            P.flat(x0, 0.3, x1, 0.62, MK)
            ox0, ox1 = sorted([sx * 1.45, sx * 1.95]); P.flat(ox0, 0.55, ox1, 0.85, MK)    # the flick up
    if markings:
        for sx in (1, -1):
            x0, x1 = sorted([sx * 0.35, sx * 1.6]); P.flat(x0, 1.05, x1, 1.3, MR)          # brows
            ox0, ox1 = sorted([sx * 1.2, sx * 1.85]); P.flat(ox0, 1.3, ox1, 1.55, MR)
            for k, y in enumerate((-0.55, -0.95)):                                       # whisker stripes
                c0, c1 = sorted([sx * (1.15 + 0.1 * k), sx * 2.15]); P.flat(c0, y, c1, y + 0.22, MR)
        P.flat(-0.28, 1.45, 0.28, 2.25, MR); P.flat(-0.12, 1.65, 0.12, 2.05, GOLD)        # forehead flame, gold heart
        P.flat(-0.5, 1.6, -0.28, 1.95, MR); P.flat(0.28, 1.6, 0.5, 1.95, MR)
    if crown:
        A([-2.0, 2.45, -0.1], [2.0, 2.75, 0.6], GOLD)                            # gold band along the top
        for x, h in ((-1.3, 0.7), (0.0, 1.1), (1.3, 0.7)):
            A([x - 0.3, 2.75, 0.0], [x + 0.3, 2.75 + h, 0.5], GOLD)
            A([x - 0.15, 2.75 + h, 0.05], [x + 0.15, 2.95 + h, 0.45], GOLD2)
        A([-0.22, 2.9, -0.18], [0.22, 3.3, -0.08], MR2, shade=False)            # a red gem

def cord(f, y=31.2, col=MR):
    """The red cord tied round his head (under the hair), knotted at the back."""
    f.add('head', [-4.75, y, -4.75], [4.75, y + 0.35, -4.6], col)
    f.add('head', [-4.75, y, 4.6], [4.75, y + 0.35, 4.75], col)
    for sx in (1, -1):
        x0, x1 = sorted([sx * 4.6, sx * 4.9]); f.add('head', [x0, y, -4.75], [x1, y + 0.35, 4.75], col)
    f.add('head', [-0.5, y - 0.4, 4.75], [0.5, y + 0.7, 5.2], col)               # knot
    for sx in (1, -1): f.add('head', [sx * 0.3 - 0.15, y - 2.4, 4.8], [sx * 0.3 + 0.15, y - 0.3, 5.05], col)   # tails

OPTIONS = {
    # A: the side mask, but a proper 3D kitsune mask, angled out and forward, tied on with a red cord
    'side': lambda f: (mask(Place(f, [7.6, 29.0, -1.2], rot=[0, -62, 6], s=1.1)), cord(f)),
    # B: pushed up onto the top of his head like a crown: ears up, gold band and points
    'crown': lambda f: (mask(Place(f, [0.0, 35.4, -2.4], rot=[20, 0, 0], s=1.15), crown=True, lower=False), cord(f, y=31.6, col=GOLD2)),
    # C: worn over his face in Fox King mode: big, eye slits, snout out front
    'face': lambda f: mask(Place(f, [0.0, 28.0, -5.6], s=1.6), eyeholes=True),
    # D: a half mask over the eyes and nose, with the gold crown points on top
    'half': lambda f: mask(Place(f, [0.0, 29.2, -5.6], s=1.6), lower=False, snout=False, crown=True),
}

def build(option):
    src = json.load(open(RES + 'models/detail/mikiko_foxking.json'))
    old = Image.open(RES + 'textures/entity/character/mikiko_foxking_details.png').convert('RGBA')
    mj = copy.deepcopy(src)
    hairg = mj['bones']['head']['children'][0]
    hairg['cubes'] = [c for c in hairg['cubes'] if c['from'][0] >= -4.95]      # the old side mask out
    fit = Fit(); OPTIONS[option](fit)
    atlas = Image.new('RGBA', (512, 512), (0, 0, 0, 0)); atlas.paste(old, (0, 0))
    pack(fit, atlas, region=(0, 256, 512, 512))
    part = npcs.export(fit, '/tmp/foxmask_part.json')
    hb = part['bones']['head']
    hairg['cubes'] += hb['cubes']
    hairg['children'] = hairg.get('children', []) + hb['children']
    mj['texture_size'] = 512
    return mj, atlas

if __name__ == '__main__':
    skin = Image.open(RES + 'textures/entity/character/mikiko.png').convert('RGBA')
    face = Image.open(RES + 'textures/entity/character/mikiko_face.png').convert('RGBA')
    rows = []
    for k in (sys.argv[1:] or OPTIONS):
        mj, atlas = build(k)
        json.dump(mj, open(f'/tmp/foxmask_{k}.json', 'w'))
        atlas.save(f'/tmp/foxmask_{k}.png')
        rows.append(U.preview(mj, skin, atlas, face, yaws=(0, 35, -90, -35), scale=12, wide=26, tall=17, top=7))
    w = max(r.width for r in rows); h = sum(r.height for r in rows)
    S = Image.new('RGB', (w, h), (30, 32, 40)); y = 0
    for r in rows: S.paste(r, (0, y)); y += r.height
    S.save('/tmp/foxmask.png'); print(S.size)
