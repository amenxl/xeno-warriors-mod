"""Garry's microphone (a black handle with a silver grille ball) - held for his beatbox idle."""
import sys
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/items2')
exec(open('/home/claude/xeno-warriors-mod/tools/xw/items2/make.py').read().split('r = random.Random(4)')[0])
tex('microphone', [
    (0, 0, 8, 8, lambda x, y: (190, 194, 204, 255) if (x + y) % 2 else (120, 124, 134, 255)),       # grille mesh
    (8, 0, 16, 8, lambda x, y: (26, 26, 30, 255) if y % 4 else (50, 50, 56, 255)),                  # handle
    (0, 8, 8, 16, lambda x, y: (60, 200, 70, 255)),                                                # green band (Garry)
    (8, 8, 16, 16, lambda x, y: (170, 174, 184, 255))])
G, H, B, S = [0, 0, 8, 8], [8, 0, 16, 8], [0, 8, 8, 16], [8, 8, 16, 16]
els = [{"from": [7.2, 0.0, 7.2], "to": [8.8, 7.0, 8.8], "faces": F(H)},
       {"from": [7.0, 6.6, 7.0], "to": [9.0, 7.6, 9.0], "faces": F(B)},
       {"from": [6.9, 7.6, 6.9], "to": [9.1, 8.2, 9.1], "faces": F(S)},
       {"from": [6.6, 8.2, 6.6], "to": [9.4, 10.4, 9.4], "faces": F(G)},
       {"from": [6.9, 10.4, 6.9], "to": [9.1, 11.0, 9.1], "faces": F(G)},
       {"from": [7.4, -0.6, 7.4], "to": [8.6, 0.0, 8.6], "faces": F(S)}]
disp = {"gui": {"rotation": [0, 0, -30], "scale": [1.0, 1.0, 1.0]},
        "ground": {"translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        "fixed": {"rotation": [0, 0, -30], "scale": [1.0, 1.0, 1.0]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 1.5, 0.5], "scale": [0.7, 0.7, 0.7]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 1.5, 0.5], "scale": [0.7, 0.7, 0.7]},
        "firstperson_righthand": {"rotation": [0, -90, 20], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
        "firstperson_lefthand": {"rotation": [0, 90, -20], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]}}
model('microphone', els, disp)
