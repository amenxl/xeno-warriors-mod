from core import *

def build():
    m = Model('rhalix')
    A = m.add
    H_LT, H_ML, H_M, H_D = '#cf6af5', '#b44ae2', '#9a30c8', '#7a22a6'
    UNDER, UNDER2 = '#1f2b22', '#2c3a2c'            # dark green-black under-layer of his hair
    COAT, COAT_L, COAT_D = '#9b2bc0', '#b844d8', '#7a1e98'
    LINING, LINING_D = '#6a1f3a', '#4e1529'
    GOLD, GOLD_D, RIVET = '#e6c84a', '#b39328', '#8a7020'
    TANK, CUFF, BELT = '#e8e6ea', '#7b1f2e', '#6b2030'
    SILVER, SILVER_D = '#c9ccd2', '#7b7f87'
    BOOT, BOOT_D, TOE, TOE_D, SOLE, LACE = '#8a2440', '#6c1a32', '#2f45a8', '#22337e', '#5a1428', '#cfcfd6'
    F1, F2, F3, F4 = '#121016', '#1a161f', '#241f2b', '#332b3c'
    rnd = random.Random(7)

    # ---------------- hair: long straight purple locks, pink highlights on top, a few black strands
    PH, P1, P2, P3, PK = '#d06ae8', '#a63fdc', '#8a2cc4', '#6a1f9e', '#1c1622'
    import os
    STYLE = os.environ.get('HAIR_STYLE', 'A')     # A: black tips; B: black tips + black under-layer; C: purple fading to black
    BK = '#18141c'
    def tip(c, under=False):
        # his hair is purple, only the bottom part black
        if STYLE == 'C': return (c[0], P3, BK)
        if STYLE == 'B' and under: return (c[0], BK, BK)
        return (c[0], c[1], BK)
    A('Head', 'Hair Cap Top', [-4.6, 32.0, -4.6], [4.6, 33.2, 4.6], P2)
    A('Head', 'Hair Cap Front', [-4.6, 30.8, -4.75], [4.6, 32.0, -4.0], P3)
    A('Head', 'Hair Cap Back', [-4.6, 26.2, 4.0], [4.6, 32.0, 4.75], P3)
    for s in (1, -1):
        x0, x1 = sorted([s * 4.0, s * 4.75])
        A('Head', 'Hair Cap Side', [x0, 27.0, -4.6], [x1, 32.0, 4.75], P3)

    SHORTER = 0.75                                           # hanging hair trimmed to 3/4 length
    def hang(root, d, length, width, cols, wide='x', thick=0.95, trim=True):
        """A lock: a light top segment, a main segment, and a slightly narrower tip."""
        if trim:
            length *= SHORTER
        segs = [length * 0.22, length * 0.56, length * 0.22]
        widths = [width, width, width * 0.72]
        strand(m, 'Head', root, d, segs, widths, thick, cols, wide, name='Lock')

    # top: layered locks lying over the crown, glowing pink at the top
    for k in range(14):
        az = math.radians(k * (360 / 14) + 8)
        dx, dz = math.sin(az), -math.cos(az)
        wide = 'z' if abs(dx) > abs(dz) else 'x'
        hang((dx * 0.8, 34.3, 0.3 + dz * 0.8), (dx, -0.48, dz), 4.8, 2.3, (PH, P1, P2), wide, 1.1, trim=False)
    # bangs: purple locks over the forehead plus two black strands in front of the face
    for x, ln, w, col in ((3.45, 4.8, 1.4, (PH, P1, P2)), (1.95, 3.4, 1.7, (PH, P1, P1)),
                          (-0.55, 4.4, 1.3, (P1, P2, P2)), (-1.85, 3.2, 1.7, (PH, P1, P1)),
                          (-3.3, 4.4, 1.6, (P1, P2, P3))):
        hang((x, 32.8, -4.95), (x * 0.02, -1, -0.12), ln, w, col)
    hang((2.95, 32.4, -5.05), (0.02, -1, -0.1), 6.6, 0.95, tip((P1, P2, P3)))      # black strand by his eye
    hang((0.35, 32.4, -5.05), (0.0, -1, -0.1), 6.0, 0.95, tip((P1, P2, P3)))       # black strand between his eyes
    # sides: two layers down past the jaw, flaring out a little
    for s in (1, -1):
        for z, ln in ((-3.3, 7.6), (-1.9, 8.2), (-0.5, 8.6), (0.9, 8.6), (2.3, 8.2), (3.6, 7.8)):
            hang((s * 5.0, 32.4, z), (s * 0.12, -1, 0.04), ln + rnd.uniform(-.3, .3), 1.5,
                 tip((PH, P1, P2) if int((z + 4) * 10) % 2 else (P1, P2, P3)), 'z')
        for z, ln in ((-2.6, 7.0), (0.2, 7.4), (2.9, 7.0)):
            hang((s * 5.7, 31.6, z), (s * 0.2, -1, 0.05), ln, 1.6, tip((P1, P2, P3), True), 'z')
        hang((s * 4.85, 31.0, -4.0), (s * 0.1, -1, -0.02), 6.2, 1.0, tip((P2, P3, P3), True), 'z')
    # back
    for x in [-3.7, -2.25, -0.75, 0.75, 2.25, 3.7]:
        hang((x, 32.4, 5.0), (x * 0.03, -1, 0.12), 9.0 + rnd.uniform(-.4, .4), 1.5,
             tip((PH, P1, P2) if int((x + 4) * 10) % 2 else (P1, P2, P3)))
    for x in [-3.0, -1.0, 1.0, 3.0]:
        hang((x, 31.6, 5.7), (x * 0.04, -1, 0.18), 8.0, 1.6, tip((P1, P2, P3), True))
    # twig in his mouth
    A('Head', 'Twig', [-4.6, 25.95, -4.55], [-1.5, 26.25, -4.25], '#3d5a24', [0, 18, -10], [-1.6, 26.1, -4.4])
    A('Head', 'Twig Leaf', [-4.8, 26.2, -4.9], [-3.8, 26.45, -3.9], '#4f7a2a', [0, 30, -8], [-1.6, 26.1, -4.4])
    A('Head', 'Twig Leaf', [-3.8, 25.7, -5.0], [-3.1, 25.95, -4.3], '#4f7a2a', [0, 10, -22], [-1.6, 26.1, -4.4])

    # ---------------- long coat with maroon lining
    A('Body', 'Coat Back', [-4.55, 4.6, 2.0], [4.55, 24.3, 2.9], COAT, shade='grad')
    A('Body', 'Hood', [-3.8, 20.4, 2.9], [3.8, 25.3, 4.7], COAT_L)
    A('Body', 'Hood Lining', [-3.1, 22.6, 2.2], [3.1, 25.5, 3.7], LINING)
    A('Body', 'Hood Fold', [-3.4, 19.4, 3.0], [3.4, 20.5, 4.3], COAT)
    for s in (1, -1):
        x0, x1 = sorted([s * 2.35, s * 4.55])
        A('Body', 'Lapel', [x0, 12.6, -2.8], [x1, 24.3, -2.0], COAT)
        t0, t1 = sorted([s * 2.05, s * 2.5])
        A('Body', 'Lapel Lining', [t0, 12.6, -2.95], [t1, 24.0, -2.5], LINING)
        c0, c1 = sorted([s * 3.0, s * 4.5])
        A('Body', 'Collar', [c0, 22.9, -2.4], [c1, 25.8, 2.9], COAT, [0, 0, s * -7], [s * 3.6, 23, 0])
        ci0, ci1 = sorted([s * 2.8, s * 3.05])
        A('Body', 'Collar Lining', [ci0, 23.1, -2.2], [ci1, 25.6, 2.7], LINING, [0, 0, s * -7], [s * 3.6, 23, 0])
        # skirt of the coat, open at the front, maroon inside
        f0, f1 = sorted([s * 1.7, s * 4.65])
        A('Body', 'Coat Skirt Front', [f0, 4.6, -2.95], [f1, 12.6, -2.35], COAT, shade='grad')
        k0, k1 = sorted([s * 1.5, s * 1.95])
        A('Body', 'Skirt Lining', [k0, 4.6, -3.05], [k1, 12.6, -2.6], LINING)
        g0, g1 = sorted([s * 4.05, s * 4.65])
        A('Body', 'Coat Skirt Side', [g0, 4.6, -2.95], [g1, 12.6, 2.9], COAT_D, shade='grad')
        # gold strap stubs on the lapels and the chest straps crossing over the tank top
        A('Body', 'Lapel Strap', [min(s * 2.4, s * 4.5), 21.2, -3.0], [max(s * 2.4, s * 4.5), 21.9, -2.85], GOLD)
        A('Body', 'Strap Rivet', [s * 4.0 - .25, 21.3, -3.1], [s * 4.0 + .25, 21.8, -2.95], RIVET)
        A('Body', 'Chest Strap', [-4.0, 17.9, -2.62], [4.0, 18.65, -2.42], GOLD, [0, 0, s * 38], [0, 18.3, -2.5])
        A('Body', 'Chest Rivet', [s * 2.9 - .25, 20.6, -2.75], [s * 2.9 + .25, 21.1, -2.6], RIVET)
        # vertical gold strap hanging on the coat tail
        A('Body', 'Tail Strap', [s * 2.6 - .35, 6.5, 2.9], [s * 2.6 + .35, 12.6, 3.05], GOLD)
        A('Body', 'Tail Strap Rivet', [s * 2.6 - .3, 6.6, 3.05], [s * 2.6 + .3, 7.2, 3.15], RIVET)
    # gold band around the coat at the waist (back + sides)
    A('Body', 'Waist Band', [-4.6, 12.6, 2.9], [4.6, 13.4, 3.05], GOLD)
    A('Body', 'Waist Band R', [4.65, 12.6, -2.95], [4.8, 13.4, 2.95], GOLD)
    A('Body', 'Waist Band L', [-4.8, 12.6, -2.95], [-4.65, 13.4, 2.95], GOLD)
    # tattered hem
    def tatters(xs, z0, z1, side=False):
        for x in xs:
            ln = rnd.choice([0.8, 1.4, 2.0, 2.6, 1.1, 1.8, 3.0])
            col = rnd.choice([COAT, COAT_D, LINING])
            if side:
                A('Body', 'Tatter', [z0, 4.6 - ln, x], [z1, 4.7, x + .9], col)
            else:
                A('Body', 'Tatter', [x, 4.6 - ln, z0], [x + .9, 4.7, z1], col)
    tatters([-4.5 + i for i in range(9)], 2.0, 2.9)
    tatters([1.75 + i for i in range(3)], -2.95, -2.35)
    tatters([-4.65 + i for i in range(3)], -2.95, -2.35)
    tatters([-2.9 + i for i in range(6)], 4.05, 4.65, side=True)
    tatters([-2.9 + i for i in range(6)], -4.65, -4.05, side=True)
    # maroon belt with a silver ring buckle
    A('Body', 'Belt', [-2.35, 11.7, -2.45], [2.35, 12.8, -2.1], BELT)
    A('Body', 'Buckle Top', [-1.0, 12.55, -2.65], [1.0, 12.9, -2.4], SILVER)
    A('Body', 'Buckle Bottom', [-1.0, 11.6, -2.65], [1.0, 11.95, -2.4], SILVER)
    A('Body', 'Buckle Side', [0.65, 11.6, -2.65], [1.0, 12.9, -2.4], SILVER)
    A('Body', 'Buckle Side', [-1.0, 11.6, -2.65], [-0.65, 12.9, -2.4], SILVER)
    A('Body', 'Buckle Pin', [-0.1, 11.9, -2.7], [0.1, 12.6, -2.55], SILVER_D)

    # ---------------- sleeves
    A('RightArm', 'Coat Sleeve', [3.6, 13.4, -2.4], [8.4, 24.4, 2.4], COAT)
    A('RightArm', 'Shoulder', [3.5, 22.6, -2.55], [8.5, 24.6, 2.55], COAT_L)
    A('RightArm', 'Sleeve Cuff Fold', [3.55, 13.4, -2.5], [8.45, 14.4, 2.5], COAT_D)
    A('RightArm', 'Inner Cuff', [3.75, 12.1, -2.3], [8.25, 13.5, 2.3], CUFF)
    A('RightArm', 'Arm Strap', [3.52, 20.0, -2.48], [8.48, 20.7, 2.48], GOLD)
    A('RightArm', 'Arm Strap Rivet', [5.7, 20.05, -2.6], [6.3, 20.65, -2.45], RIVET)
    m.mirror_x('RightArm', 'LeftArm')

    # ---------------- tall maroon boots, blue toes, gold X straps
    A('RightLeg', 'Boot Shaft', [-0.4, 1.6, -2.45], [4.4, 8.0, 2.45], BOOT, shade='grad')
    A('RightLeg', 'Boot Top', [-0.6, 7.6, -2.7], [4.6, 8.8, 2.7], BOOT_D, shade='edge')
    A('RightLeg', 'Boot Lace Strip', [1.3, 1.8, -2.55], [2.7, 7.6, -2.42], LACE)
    A('RightLeg', 'Toe Cap', [-0.5, 0.6, -3.5], [4.5, 2.7, -0.6], TOE, shade='edge')
    A('RightLeg', 'Heel Cap', [-0.5, 0.6, 0.9], [4.5, 2.2, 2.6], TOE_D)
    A('RightLeg', 'Sole', [-0.6, 0.0, -3.65], [4.6, 0.7, 2.7], SOLE)
    for z in (-3.1, -1.7, -0.3, 1.1):
        A('RightLeg', 'Sole Lug', [-0.65, 0.0, z], [4.65, 0.3, z + .6], '#3c0c1a')
    for yc in (6.2, 3.8):
        for s in (1, -1):
            A('RightLeg', 'Boot Strap', [0.0, yc - .25, -2.68], [4.0, yc + .25, -2.5], GOLD, [0, 0, s * 36], [2, yc, -2.6])
        for xr in (0.3, 3.7):
            A('RightLeg', 'Strap Rivet', [xr - .25, yc + 1.0, -2.75], [xr + .25, yc + 1.5, -2.6], RIVET)
            A('RightLeg', 'Strap Rivet', [xr - .25, yc - 1.5, -2.75], [xr + .25, yc - 1.0, -2.6], RIVET)
    A('RightLeg', 'Pull Tab', [1.4, 8.6, 2.3], [2.6, 9.6, 2.7], BOOT_D)
    m.mirror_x('RightLeg', 'LeftLeg')

    # ---------------- wings (folded away in-game unless flying)
    edge = [(3.4, 21.8), (6.6, 30.0), (10.4, 35.8), (14.6, 38.8), (19.2, 38.0), (22.8, 34.0), (25.0, 28.6)]
    seglen = [math.dist(edge[i], edge[i + 1]) for i in range(len(edge) - 1)]
    total = sum(seglen)
    def along(t):
        d = t * total
        for i, L in enumerate(seglen):
            if d <= L or i == len(seglen) - 1:
                f = min(1, d / L)
                return (edge[i][0] + (edge[i + 1][0] - edge[i][0]) * f, edge[i][1] + (edge[i + 1][1] - edge[i][1]) * f)
            d -= L

    for s, side in ((1, 'RightWing'), (-1, 'LeftWing')):
        m.group(side, [s * 3.4, 21.8, 3.4], [0, -s * 20, 0], 'Body')
        W = lambda name, frm, to, col, rz, o, sh=None: A(side, name,
                [s * frm[0], frm[1], frm[2]], [s * to[0], to[1], to[2]], col, [0, 0, s * rz], [s * o[0], o[1], o[2]], sh)
        for i in range(len(edge) - 1):
            (x0, y0), (x1, y1) = edge[i], edge[i + 1]
            ang = math.degrees(math.atan2(y1 - y0, x1 - x0))
            L = seglen[i] + 0.6
            W('Wing Arm', [x0 - .3, y0 - .9, 3.4], [x0 - .3 + L, y0 + .9, 5.0], F2, ang, [x0, y0, 4.2])
        n = 12
        for i in range(n):
            t = 0.10 + 0.90 * i / (n - 1)
            x, y = along(t)
            L = min(10 + 28 * t ** 0.8, y - 0.6)
            rz = -6 + 26 * t
            z0 = 4.4
            col = F1 if i % 2 else F2
            W('Primary', [x - 1.25, y - (L - 3.2), z0], [x + 1.25, y, z0 + .8], col, rz, [x, y, z0])
            W('Primary Tip', [x - .75, y - (L - 1.2), z0 + .05], [x + .75, y - (L - 3.2), z0 + .75], col, rz, [x, y, z0])
            W('Primary Point', [x - .3, y - L, z0 + .1], [x + .3, y - (L - 1.2), z0 + .7], col, rz, [x, y, z0])
            W('Primary Shine', [x - .15, y - (L - 3.4), z0 - .05], [x + .15, y - .6, z0 + .02], F4, rz, [x, y, z0])
        for i in range(n - 1):
            t = 0.12 + 0.88 * (i + .5) / (n - 1)
            x, y = along(t)
            L = min(7 + 15 * t, y - 4)
            rz = -3 + 22 * t
            z0 = 3.65
            W('Secondary', [x - 1.2, y - (L - 2), z0], [x + 1.2, y, z0 + .75], F2, rz, [x, y, z0])
            W('Secondary Tip', [x - .55, y - L, z0 + .05], [x + .55, y - (L - 2), z0 + .7], F2, rz, [x, y, z0])
        for i in range(16):
            t = 0.06 + 0.92 * i / 15
            x, y = along(t)
            L = 3.6 + 2.2 * math.sin(t * math.pi)
            rz = -10 + 25 * t
            z0 = 2.95
            col = F3 if i % 2 else F2
            W('Covert', [x - 1.0, y - (L - 1), z0], [x + 1.0, y + .4, z0 + .7], col, rz, [x, y, z0])
            W('Covert Tip', [x - .45, y - L, z0 + .05], [x + .45, y - (L - 1), z0 + .65], col, rz, [x, y, z0])
    return m
