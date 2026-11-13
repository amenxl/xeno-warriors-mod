"""Lexy's claws (the user's notes): gloves she wears, with three blue claw blades over the knuckles.

The item is drawn where a held item goes, so the model is built round the fist: in the item frame (vanilla's
ItemInHandLayer: arm transform, rotX -90, rotY 180, translate(+-1/16, 0.125, -0.625)) the fist's centre sits at
(0, -0.125, 0) blocks; model x = across the hand (arm -x), model y = the arm's front-back (arm -z), model z = up the
arm towards the shoulder (arm -y). Fist: the bottom 4 px of a 3-px slim arm. The display transforms leave the model
where it is (no rotation, scale 1), only shifting it half a pixel for the slim arm's centre (mirrored for the left hand)."""
exec(open('/home/claude/xeno-warriors-mod/tools/xw/items2/make.py').read().split('r = random.Random(4)')[0])
BLUE = lambda x, y: (40 + 6 * (y % 3), 110 + 8 * (x % 2), 235, 255) if y % 6 else (20, 70, 200, 255)
EDGE = lambda x, y: (160, 215, 255, 255)
GLOVE = lambda x, y: (30, 28, 48, 255) if (x + y) % 3 else (44, 40, 70, 255)
CUFF = lambda x, y: (58, 74, 208, 255) if (x + y) % 2 else (40, 52, 170, 255)
PLATE = lambda x, y: (70, 76, 110, 255) if (x + 2 * y) % 4 else (96, 104, 140, 255)
tex('lexy_claws', [(0, 0, 4, 16, BLUE), (4, 0, 6, 16, EDGE), (6, 0, 10, 8, GLOVE), (6, 8, 10, 16, CUFF), (10, 0, 16, 16, PLATE)])
BL, ED, GL, CU, PL = [0, 0, 4, 16], [4, 0, 6, 16], [6, 0, 10, 8], [6, 8, 10, 16], [10, 0, 16, 16]
CY = 6.0                                   # the fist's centre in model y (item frame y -0.125 blocks = -2 px)
els = [
    {"from": [6.1, CY - 2.35, 5.6], "to": [9.9, CY + 2.35, 10.4], "faces": F(GL)},       # the glove round the fist (3 x 4 px hand)
    {"from": [5.9, CY - 2.55, 9.4], "to": [10.1, CY + 2.55, 11.4], "faces": F(CU)},      # its blue cuff at the wrist
    {"from": [9.9, CY - 2.0, 6.0], "to": [10.4, CY + 2.0, 9.4], "faces": F(PL)},         # metal plate over the back of the hand
    {"from": [6.4, CY - 2.0, 5.2], "to": [9.6, CY + 2.0, 5.6], "faces": F(PL)}]          # knuckle guard the claws come out of
# three claws out of the knuckles, along the arm past the fingertips, spaced along the knuckle line (model y);
# each curves a little outward (model +x, away from the palm) towards its point
for i, dy in enumerate((-1.35, 0.0, 1.35)):
    y0, y1 = CY + dy - 0.2, CY + dy + 0.2
    L = 7.0 if i == 1 else 6.2
    segs = [(5.2, 5.2 - L * 0.5, 0.0, 1.3), (5.2 - L * 0.5, 5.2 - L * 0.85, 0.35, 1.1), (5.2 - L * 0.85, 5.2 - L, 0.8, 0.6)]
    for z1, z0, dx, w in segs:
        els.append({"from": [8.6 + dx, y0, z0], "to": [8.6 + dx + w, y1, z1], "faces": F(BL)})
    els.append({"from": [8.6 + 1.3, y0 + 0.05, 5.2 - L * 0.5], "to": [8.6 + 1.6, y1 - 0.05, 5.2], "faces": F(ED)})   # bright edge
disp = {"gui": {"rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [0.95, 0.95, 0.95]},
        "ground": {"translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        "fixed": {"rotation": [0, 90, 0], "scale": [0.9, 0.9, 0.9]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [-0.5, 0, 0], "scale": [1, 1, 1]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [-0.5, 0, 0], "scale": [1, 1, 1]},
        "firstperson_righthand": {"rotation": [-30, 0, 0], "translation": [1.5, 1.5, -1], "scale": [0.8, 0.8, 0.8]},
        "firstperson_lefthand": {"rotation": [-30, 0, 0], "translation": [1.5, 1.5, -1], "scale": [0.8, 0.8, 0.8]}}
model('lexy_claws', els, disp)
print('ok', len(els))
