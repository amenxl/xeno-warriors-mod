"""Kyrie's rapier (the user's picture): a long, slender white blade, a purple ribbon wound round the base of the blade,
and a purple guard that curls up into a hook on one side and sweeps down round the grip into a knuckle bow."""
import sys
exec(open('/home/claude/xeno-warriors-mod/tools/xw/items2/make.py').read().split('r = random.Random(4)')[0])
WHITE = lambda x, y: (236 + 3 * (x % 2), 232 + 3 * (x % 2), 248, 255) if y % 7 else (214, 208, 232, 255)
EDGE = lambda x, y: (250, 248, 255, 255)
PURP = lambda x, y: (120, 40, 150, 255) if (x + y) % 3 else (150, 66, 182, 255)
RIBB = lambda x, y: (196, 110, 220, 255) if (x + 2 * y) % 4 else (226, 150, 240, 255)
GRIP = lambda x, y: (74, 26, 96, 255) if (x + y) % 2 else (96, 36, 120, 255)
DARK = lambda x, y: (60, 20, 80, 255)
tex('kyrie_rapier', [(0, 0, 4, 16, WHITE), (4, 0, 6, 16, EDGE), (6, 0, 10, 4, PURP), (6, 4, 10, 8, RIBB),
                     (10, 0, 14, 16, GRIP), (14, 0, 16, 16, DARK)])
BL, ED, GU, RI, GR, DK = [0, 0, 4, 16], [4, 0, 6, 16], [6, 0, 10, 4], [6, 4, 10, 8], [10, 0, 14, 16], [14, 0, 16, 16]
els = [
    {"from": [7.45, 8.4, 7.75], "to": [8.55, 31.0, 8.25], "faces": F(BL)},                     # slender blade
    {"from": [8.55, 8.4, 7.85], "to": [8.8, 31.0, 8.15], "faces": F(ED)},                      # edge
    {"from": [7.7, 31.0, 7.8], "to": [8.4, 32.0, 8.2], "faces": F(BL)},                        # tip
    {"from": [7.25, 6.6, 7.6], "to": [8.75, 8.6, 8.4], "faces": F(BL)},                        # ricasso
    {"from": [7.05, 7.3, 7.45], "to": [8.95, 8.1, 8.55], "faces": F(RI)},                      # ribbon wound round it
    {"from": [8.95, 7.7, 7.85], "to": [10.6, 8.3, 8.15], "faces": F(RI)},                      # its ends, fluttering
    {"from": [10.4, 8.3, 7.85], "to": [11.2, 9.5, 8.15], "faces": F(RI)},
    {"from": [5.5, 6.6, 7.85], "to": [7.05, 7.2, 8.15], "faces": F(RI)},
    {"from": [5.6, 5.9, 7.5], "to": [10.1, 6.6, 8.5], "faces": F(GU)},                         # crossguard
    {"from": [9.5, 6.6, 7.55], "to": [10.3, 8.6, 8.45], "faces": F(GU)},                       # the hook curling up
    {"from": [8.9, 8.6, 7.6], "to": [10.0, 9.4, 8.4], "faces": F(GU)},
    {"from": [5.2, -1.6, 7.55], "to": [6.0, 5.9, 8.45], "faces": F(GU)},                       # knuckle bow down the grip
    {"from": [5.2, -2.6, 7.55], "to": [8.3, -1.6, 8.45], "faces": F(GU)},
    {"from": [7.3, -2.3, 7.3], "to": [8.7, 5.9, 8.7], "faces": F(GR)},                         # grip
    {"from": [7.1, -3.0, 7.1], "to": [8.9, -2.3, 8.9], "faces": F(DK)}]                        # pommel
disp = {"gui": {"rotation": [0, 0, -45], "scale": [0.46, 0.46, 0.46]},
        "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        "fixed": {"rotation": [0, 0, -45], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [0, -90, 30], "translation": [0, 2.4, -3.0], "scale": [0.8, 0.8, 0.8]},
        "thirdperson_lefthand": {"rotation": [0, 90, -30], "translation": [0, 2.4, -3.0], "scale": [0.8, 0.8, 0.8]},
        "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 1.0, 1.13], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 1.0, 1.13], "scale": [0.4, 0.4, 0.4]}}
model('kyrie_rapier', els, disp)
print('ok')
