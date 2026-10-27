"""Senju's Blade: the SR leader's long straight sword (manga ref) - dark steel, pale edge, square black guard, wrapped grip."""
import sys
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/items2')
exec(open('/home/claude/xeno-warriors-mod/tools/xw/items2/make.py').read().split('r = random.Random(4)')[0])
STEEL = lambda x, y: (58 + 4 * (x % 2), 58 + 4 * (x % 2), 68 + 4 * (x % 2), 255) if y % 7 else (80, 80, 92, 255)
tex('senju_blade', [
    (0, 0, 4, 16, STEEL),                                                         # blade flat
    (4, 0, 6, 16, lambda x, y: (196, 200, 212, 255) if x == 0 else (150, 154, 166, 255)),   # the edge
    (6, 0, 10, 4, lambda x, y: (22, 20, 24, 255) if (x + y) % 3 else (48, 44, 50, 255)),   # guard
    (6, 4, 10, 8, lambda x, y: (150, 150, 160, 255) if (x + y) % 2 else (110, 110, 120, 255)),  # pommel / collar
    (10, 0, 14, 16, lambda x, y: (120, 30, 34, 255) if (x + y) % 4 == 0 or (x - y) % 4 == 0 else (24, 20, 24, 255)),  # wrapped grip, dark red diamonds
    (14, 0, 16, 16, lambda x, y: (36, 34, 42, 255))])                             # spine
BL, ED, GU, PO, GR, SP = [0, 0, 4, 16], [4, 0, 6, 16], [6, 0, 10, 4], [6, 4, 10, 8], [10, 0, 14, 16], [14, 0, 16, 16]
def blade(y0, y1, x0, x1, xe):
    return [{"from": [x0, y0, 7.6], "to": [x1, y1, 8.4], "faces": {**F(BL, ("north", "south")), **F(SP, ("west", "up", "down"))}},
            {"from": [x1, y0, 7.75], "to": [xe, y1, 8.25], "faces": F(ED)}]
els = (blade(7.4, 29.0, 7.0, 9.0, 9.6) + blade(29.0, 30.5, 7.3, 9.0, 9.5) + blade(30.5, 31.6, 7.8, 8.9, 9.3)
       + [{"from": [8.6, 31.6, 7.8], "to": [9.2, 32.0, 8.2], "faces": F(ED)}]
       + [{"from": [5.4, 6.0, 6.4], "to": [10.6, 7.4, 9.6], "faces": F(GU)},          # square guard
          {"from": [6.6, 7.4, 7.3], "to": [9.4, 8.2, 8.7], "faces": F(PO)},           # collar
          {"from": [7.2, -2.6, 7.2], "to": [8.8, 6.0, 8.8], "faces": F(GR)},          # grip
          {"from": [6.8, -3.6, 6.8], "to": [9.2, -2.6, 9.2], "faces": F(PO)}])        # pommel
disp = {"gui": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.48, 0.48, 0.48]},
        "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        "fixed": {"rotation": [0, 0, -45], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [0, -90, 30], "translation": [0, 1.5, 0.5], "scale": [0.8, 0.8, 0.8]},
        "thirdperson_lefthand": {"rotation": [0, 90, -30], "translation": [0, 1.5, 0.5], "scale": [0.8, 0.8, 0.8]},
        "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 1.0, 1.13], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 1.0, 1.13], "scale": [0.4, 0.4, 0.4]}}
model('senju_blade', els, disp)
