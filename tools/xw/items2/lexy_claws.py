"""Lexy's claws (an original design until the user's reference arrives): a punch-grip held across the fist, an indigo
knuckle plate with a glowing blue seam, and three hooked steel blades sweeping out over the knuckles. She summons a pair."""
exec(open('/home/claude/xeno-warriors-mod/tools/xw/items2/make.py').read().split('r = random.Random(4)')[0])
STEEL = lambda x, y: (200 + 4 * (x % 2), 208 + 4 * (x % 2), 228, 255) if y % 5 else (168, 176, 204, 255)
EDGE = lambda x, y: (240, 244, 255, 255)
INDIGO = lambda x, y: (44, 54, 150, 255) if (x + y) % 3 else (62, 74, 190, 255)
GLOW = lambda x, y: (120, 170, 255, 255) if (x + y) % 2 else (170, 210, 255, 255)
GRIP = lambda x, y: (28, 26, 44, 255) if (x + y) % 2 else (42, 38, 62, 255)
tex('lexy_claws', [(0, 0, 4, 16, STEEL), (4, 0, 6, 16, EDGE), (6, 0, 10, 8, INDIGO), (6, 8, 10, 16, GLOW), (10, 0, 16, 16, GRIP)])
ST, ED, IN, GL, GR = [0, 0, 4, 16], [4, 0, 6, 16], [6, 0, 10, 8], [6, 8, 10, 16], [10, 0, 16, 16]
els = [
    {"from": [7.3, -2.0, 7.3], "to": [8.7, 5.0, 8.7], "faces": F(GR)},                          # the grip in the fist
    {"from": [9.6, -2.0, 7.55], "to": [10.5, 6.0, 8.45], "faces": F(IN)},                        # side bar round the fist
    {"from": [7.3, -2.8, 7.55], "to": [10.5, -2.0, 8.45], "faces": F(IN)},
    {"from": [5.4, 5.0, 7.0], "to": [10.6, 7.0, 9.0], "faces": F(IN)},                           # knuckle plate
    {"from": [5.6, 5.6, 6.9], "to": [10.4, 6.4, 9.1], "faces": F(GL)}]                           # its glowing seam
for x0, top in ((5.7, 18.5), (7.6, 21.5), (9.5, 18.5)):                                        # three hooked blades
    segs = [(7.0, 12.0, 0.0), (12.0, 15.5, 0.5), (15.5, top - 1.5, 1.1), (top - 1.5, top, 1.8)]
    for i, (y0, y1, dx) in enumerate(segs):
        w = 1.0 if i < 3 else 0.6
        els.append({"from": [x0 + dx, y0, 7.8], "to": [x0 + dx + w, y1, 8.2], "faces": F(ST)})
        if i < 3: els.append({"from": [x0 + dx + w, y0, 7.9], "to": [x0 + dx + w + 0.25, y1, 8.1], "faces": F(ED)})
disp = {"gui": {"rotation": [0, 0, -45], "scale": [0.62, 0.62, 0.62]},
        "ground": {"translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
        "fixed": {"rotation": [0, 0, -45], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 2.0, 0.5], "scale": [0.75, 0.75, 0.75]},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 2.0, 0.5], "scale": [0.75, 0.75, 0.75]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 2.0, 1.13], "scale": [0.5, 0.5, 0.5]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 2.0, 1.13], "scale": [0.5, 0.5, 0.5]}}
model('lexy_claws', els, disp)
print('ok', len(els))
