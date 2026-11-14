"""The Ancient Swordstress's greatsword (it was part of her arm's model - now she holds it, and it can drop): a long
crystal-green blade with a pale core line, a crystal-knot guard, a dark green grip and an arrow-tipped shaft behind.
As big as the Aatrox greatsword (the user's pick): the display scales it up, the grip kept in the hand."""
exec(open('/home/claude/xeno-warriors-mod/tools/xw/items2/make.py').read().split('r = random.Random(4)')[0])
GB = lambda x, y: (126, 224, 106, 255) if (x + y) % 4 else (104, 196, 88, 255)
GL = lambda x, y: (184, 255, 154, 255)
GRIP = lambda x, y: (42, 74, 30, 255) if (x + 2 * y) % 3 else (30, 56, 22, 255)
DARK = lambda x, y: (20, 38, 14, 255)
tex('swordstress_greatsword', [(0, 0, 6, 16, GB), (6, 0, 8, 16, GL), (8, 0, 12, 16, GRIP), (12, 0, 16, 16, DARK)])
B, L, G, D = [0, 0, 6, 16], [6, 0, 8, 16], [8, 0, 12, 16], [12, 0, 16, 16]
els = [
    {"from": [5.4, 5.6, 7.3], "to": [10.6, 29.0, 8.7], "faces": F(B)},                         # the broad blade
    {"from": [7.6, 5.6, 7.2], "to": [8.4, 29.0, 8.8], "faces": F(L)},                          # its pale core
    {"from": [6.2, 29.0, 7.4], "to": [9.8, 31.0, 8.6], "faces": F(B)},                         # the point
    {"from": [7.2, 31.0, 7.5], "to": [8.8, 32.0, 8.5], "faces": F(L)},
    {"from": [3.0, 4.2, 6.8], "to": [13.0, 5.8, 9.2], "faces": F(B)},                          # guard: a crystal knot
    {"from": [2.2, 4.6, 7.2], "to": [3.4, 7.4, 8.8], "faces": F(L), "rotation": {"angle": 22.5, "axis": "z", "origin": [3, 5, 8]}},
    {"from": [12.6, 4.6, 7.2], "to": [13.8, 7.4, 8.8], "faces": F(L), "rotation": {"angle": -22.5, "axis": "z", "origin": [13, 5, 8]}},
    {"from": [7.1, -4.0, 7.1], "to": [8.9, 4.2, 8.9], "faces": F(G)},                          # grip
    {"from": [7.5, -9.0, 7.5], "to": [8.5, -4.0, 8.5], "faces": F(G)},                         # the shaft behind
    {"from": [6.6, -10.4, 7.2], "to": [9.4, -9.0, 8.8], "faces": F(B)},                        # its arrow tip
    {"from": [7.3, -11.8, 7.5], "to": [8.7, -10.4, 8.5], "faces": F(L)},
    {"from": [6.8, -4.6, 6.8], "to": [9.2, -4.0, 9.2], "faces": F(D)}]
disp = {"gui": {"rotation": [0, 0, -45], "scale": [0.42, 0.42, 0.42]},
        "ground": {"translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
        "fixed": {"rotation": [0, 0, -45], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 8.0, -5.2], "scale": [1.4, 1.4, 1.4]},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 8.0, -5.2], "scale": [1.4, 1.4, 1.4]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 4.0, 1.13], "scale": [0.7, 0.7, 0.7]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 4.0, 1.13], "scale": [0.7, 0.7, 0.7]}}
model('swordstress_greatsword', els, disp)
print('ok')
