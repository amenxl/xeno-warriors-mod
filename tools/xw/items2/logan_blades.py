"""Logan's sword (long silver blade, a dark fuller with white notches, angled tip, small guard, cream-wrapped grip) and
Keita's sword (a slim, slightly curved white blade with a dark guard and navy grip)."""
import sys
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/items2')
exec(open('/home/claude/xeno-warriors-mod/tools/xw/items2/make.py').read().split('r = random.Random(4)')[0])
SILVER = lambda x, y: (200 + 6 * (x % 2), 204 + 6 * (x % 2), 214 + 6 * (x % 2), 255) if y % 9 else (176, 180, 190, 255)
FULLER = lambda x, y: (238, 236, 228, 255) if y % 4 == 1 and x in (1, 2) else (52, 46, 40, 255)
tex('logan_blade', [
    (0, 0, 4, 16, SILVER), (4, 0, 6, 16, lambda x, y: (236, 240, 248, 255)),
    (6, 0, 10, 4, lambda x, y: (60, 56, 62, 255) if (x + y) % 3 else (90, 86, 94, 255)),
    (6, 4, 10, 8, lambda x, y: (200, 200, 208, 255)),
    (10, 0, 14, 16, lambda x, y: (214, 196, 156, 255) if (x + y) % 4 else (170, 150, 110, 255)),
    (14, 0, 16, 16, FULLER)])
BL, ED, GU, PO, GR, FU = [0, 0, 4, 16], [4, 0, 6, 16], [6, 0, 10, 4], [6, 4, 10, 8], [10, 0, 14, 16], [14, 0, 16, 16]
els = [{"from": [6.8, 7.0, 7.6], "to": [9.4, 30.0, 8.4], "faces": {**F(BL, ("north", "south", "east", "west", "down"))}},       # wide blade
       {"from": [7.4, 8.0, 7.5], "to": [8.4, 28.5, 8.5], "faces": F(FU, ("north", "south"))},                                   # dark fuller with notches
       {"from": [9.4, 7.0, 7.75], "to": [9.9, 30.0, 8.25], "faces": F(ED)},                                                     # edge
       {"from": [6.8, 30.0, 7.6], "to": [8.6, 32.2, 8.4], "faces": F(BL)},                                                       # the angled tip
       {"from": [8.6, 30.0, 7.7], "to": [9.9, 31.2, 8.3], "faces": F(ED)},
       {"from": [6.0, 6.0, 7.0], "to": [10.2, 7.0, 9.0], "faces": F(GU)},                                                        # small guard
       {"from": [7.2, -3.0, 7.2], "to": [8.8, 6.0, 8.8], "faces": F(GR)},                                                        # cream-wrapped grip
       {"from": [6.9, -3.8, 6.9], "to": [9.1, -3.0, 9.1], "faces": F(PO)}]
disp = {"gui": {"rotation": [0, 0, -45], "scale": [0.46, 0.46, 0.46]},
        "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        "fixed": {"rotation": [0, 0, -45], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [0, -90, 30], "translation": [0, 1.5, 0.5], "scale": [0.8, 0.8, 0.8]},
        "thirdperson_lefthand": {"rotation": [0, 90, -30], "translation": [0, 1.5, 0.5], "scale": [0.8, 0.8, 0.8]},
        "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 1.0, 1.13], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 1.0, 1.13], "scale": [0.4, 0.4, 0.4]}}
model('logan_blade', els, disp)

WHITE = lambda x, y: (232 + 4 * (x % 2), 234 + 4 * (x % 2), 240, 255)
tex('keita_blade', [
    (0, 0, 4, 16, WHITE), (4, 0, 6, 16, lambda x, y: (250, 250, 255, 255)),
    (6, 0, 10, 4, lambda x, y: (30, 34, 70, 255) if (x + y) % 3 else (60, 66, 120, 255)),
    (6, 4, 10, 8, lambda x, y: (150, 156, 176, 255)),
    (10, 0, 14, 16, lambda x, y: (30, 40, 110, 255) if (x + y) % 4 else (14, 18, 50, 255)),
    (14, 0, 16, 16, lambda x, y: (180, 184, 196, 255))])
k = []
for i in range(6):                                                    # a slim blade, curving back a little toward the tip
    y0 = 7.0 + i * 3.6; off = 0.12 * i * i / 2
    k += [{"from": [7.2 - off, y0, 7.7], "to": [8.6 - off, y0 + 3.7, 8.3], "faces": F([0, 0, 4, 16], ("north", "south", "west", "up", "down"))},
          {"from": [8.6 - off, y0, 7.8], "to": [9.0 - off, y0 + 3.7, 8.2], "faces": F([4, 0, 6, 16])}]
k += [{"from": [7.0 - 1.9, 28.6, 7.75], "to": [8.2 - 1.9, 30.2, 8.25], "faces": F([4, 0, 6, 16])},
      {"from": [6.2, 6.0, 6.6], "to": [9.8, 7.0, 9.4], "faces": F([6, 0, 10, 4])},                                              # round-ish guard
      {"from": [7.25, -2.6, 7.25], "to": [8.75, 6.0, 8.75], "faces": F([10, 0, 14, 16])},
      {"from": [7.0, -3.2, 7.0], "to": [9.0, -2.6, 9.0], "faces": F([6, 4, 10, 8])}]
model('keita_blade', k, disp)
