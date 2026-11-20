"""Chase's two swords, as on his back: black blades, red-wrapped grips, dark square guards. Ash to Ash (his right hand)
has a glowing ember-red edge; Dust to Dust (his left) a pale ash-grey one."""
import sys
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/items2')
exec(open('/home/claude/xeno-warriors-mod/tools/xw/items2/make.py').read().split('r = random.Random(4)')[0])
BLACK = lambda x, y: (20 + 3 * (x % 2), 18 + 3 * (x % 2), 24 + 3 * (x % 2), 255) if y % 6 else (34, 32, 40, 255)
EDGES = {'ash_to_ash': lambda x, y: (255, 120, 40, 255) if x == 0 else (200, 40, 30, 255),        # embers
         'dust_to_dust': lambda x, y: (214, 210, 204, 255) if x == 0 else (150, 146, 142, 255)}   # ash
for name, edge in EDGES.items():
    tex(name, [
        (0, 0, 4, 16, BLACK),                                                     # blade flat
        (4, 0, 6, 16, edge),                                                      # the edge
        (6, 0, 10, 4, lambda x, y: (42, 40, 48, 255) if (x + y) % 3 else (60, 56, 66, 255)),   # guard
        (6, 4, 10, 8, lambda x, y: (52, 50, 58, 255) if (x + y) % 2 else (36, 34, 40, 255)),   # collar / pommel
        (10, 0, 14, 16, lambda x, y: (196, 30, 36, 255) if (x + y) % 4 else (120, 16, 22, 255)),   # red-wrapped grip
        (14, 0, 16, 16, lambda x, y: (14, 12, 16, 255))])                        # spine
    BL, ED, GU, PO, GR, SP = [0, 0, 4, 16], [4, 0, 6, 16], [6, 0, 10, 4], [6, 4, 10, 8], [10, 0, 14, 16], [14, 0, 16, 16]
    def blade(y0, y1, x0, x1, xe):
        return [{"from": [x0, y0, 7.65], "to": [x1, y1, 8.35], "faces": {**F(BL, ("north", "south")), **F(SP, ("west", "up", "down"))}},
                {"from": [x1, y0, 7.78], "to": [xe, y1, 8.22], "faces": F(ED)}]
    els = (blade(7.4, 27.0, 7.1, 8.9, 9.5) + blade(27.0, 28.6, 7.4, 8.9, 9.4) + blade(28.6, 29.8, 7.9, 8.8, 9.2)
           + [{"from": [8.5, 29.8, 7.85], "to": [9.1, 30.3, 8.15], "faces": F(ED)}]
           + [{"from": [5.6, 6.2, 6.6], "to": [10.4, 7.4, 9.4], "faces": F(GU)},      # square guard
              {"from": [6.7, 7.4, 7.35], "to": [9.3, 8.0, 8.65], "faces": F(PO)},     # collar
              {"from": [7.25, -1.6, 7.25], "to": [8.75, 6.2, 8.75], "faces": F(GR)},  # red grip
              {"from": [6.9, -2.5, 6.9], "to": [9.1, -1.6, 9.1], "faces": F(PO)}])    # pommel
    disp = {"gui": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 0, -45], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [0, -90, 30], "translation": [0, 1.5, 0.5], "scale": [0.8, 0.8, 0.8]},
            "thirdperson_lefthand": {"rotation": [0, 90, -30], "translation": [0, 1.5, 0.5], "scale": [0.8, 0.8, 0.8]},
            "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 1.0, 1.13], "scale": [0.4, 0.4, 0.4]},
            "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 1.0, 1.13], "scale": [0.4, 0.4, 0.4]}}
    model(name, els, disp)
