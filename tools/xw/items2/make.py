"""3D (Blockbench-style) item models for the Recarnet items."""
import json, random
from PIL import Image
A = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
def F(u, faces=("north", "south", "east", "west", "up", "down")): return {f: {"uv": u, "texture": "#t"} for f in faces}
DISP = {"gui": {"rotation": [30, 225, 0], "scale": [0.9, 0.9, 0.9]}, "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"scale": [0.9, 0.9, 0.9]}, "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.55, 0.55, 0.55]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]}}
def tex(name, regions):
    """regions: list of (x0,y0,x1,y1, fn(x,y)->rgba)"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); px = im.load()
    for x0, y0, x1, y1, fn in regions:
        for y in range(y0, y1):
            for x in range(x0, x1): px[x, y] = fn(x - x0, y - y0)
    im.save(A + f'textures/item/{name}.png')
def model(name, els, disp=None):
    json.dump({"credit": "Xeno Warriors", "textures": {"t": f"xenowarriors:item/{name}", "particle": f"xenowarriors:item/{name}"}, "elements": els,
               "display": disp or DISP}, open(A + f'models/item/{name}.json', 'w'), indent=1)
r = random.Random(4)
# --- Zane's Magical Orb: purple glass sphere with a glowing core on a gold stand
tex('magical_orb', [(0, 0, 8, 8, lambda x, y: (120 + 8 * y, 60 + 4 * y, 200 + 4 * y, 255) if (x + y) % 5 else (210, 170, 255, 255)),
                    (8, 0, 16, 8, lambda x, y: (255, 230, 255, 255) if abs(x - 3.5) + abs(y - 3.5) < 2 else (230, 170, 255, 255)),
                    (0, 8, 8, 16, lambda x, y: (230, 180, 60, 255) if y % 3 else (180, 130, 30, 255)),
                    (8, 8, 16, 16, lambda x, y: (90, 40, 150, 255))])
orb, core, gold, dark = [0, 0, 8, 8], [8, 0, 16, 8], [0, 8, 8, 16], [8, 8, 16, 16]
model('magical_orb', [
    {"from": [5, 0, 5], "to": [11, 1, 11], "faces": F(gold)}, {"from": [6, 1, 6], "to": [10, 3, 10], "faces": F(gold)},
    {"from": [4.5, 3, 4.5], "to": [11.5, 4, 11.5], "faces": F(gold)},
    {"from": [4, 4, 5], "to": [12, 11, 11], "faces": F(orb)}, {"from": [5, 4, 4], "to": [11, 11, 12], "faces": F(orb)},
    {"from": [5, 3.5, 5], "to": [11, 11.5, 11], "faces": F(orb)},
    {"from": [6.5, 6, 6.5], "to": [9.5, 9, 9.5], "faces": F(core)},
    {"from": [7, 11.5, 7], "to": [9, 12, 9], "faces": F(dark)}])
# --- Data Capsule: a glowing green pill with silver end caps
tex('data_capsule', [(0, 0, 8, 8, lambda x, y: (40, 230, 140, 255) if 2 <= y <= 5 else (20, 120, 80, 255)),
                     (8, 0, 16, 8, lambda x, y: (200, 206, 214, 255) if (x + y) % 4 else (150, 156, 166, 255)),
                     (0, 8, 8, 16, lambda x, y: (190, 255, 220, 255)), (8, 8, 16, 16, lambda x, y: (40, 44, 52, 255))])
model('data_capsule', [
    {"from": [6, 5, 3.5], "to": [10, 9, 12.5], "faces": F([0, 0, 8, 8])},
    {"from": [6.3, 5.3, 2], "to": [9.7, 8.7, 4], "faces": F([8, 0, 16, 8])}, {"from": [6.3, 5.3, 12], "to": [9.7, 8.7, 14], "faces": F([8, 0, 16, 8])},
    {"from": [6.8, 5.8, 1.4], "to": [9.2, 8.2, 2], "faces": F([8, 8, 16, 16])},
    {"from": [5.8, 6.5, 6.5], "to": [10.2, 7.5, 9.5], "faces": F([0, 8, 8, 16])}])
# --- F1 Tracker Pendant: a dark steel crest (diamond) with a red light, on a chain loop
tex('tracker_pendant', [(0, 0, 8, 8, lambda x, y: (70, 74, 84, 255) if (x + y) % 3 else (96, 100, 110, 255)),
                        (8, 0, 16, 8, lambda x, y: (230, 40, 40, 255) if abs(x - 3.5) + abs(y - 3.5) < 2.5 else (120, 20, 20, 255)),
                        (0, 8, 8, 16, lambda x, y: (170, 174, 182, 255) if (x + y) % 2 else (120, 124, 132, 255))])
model('tracker_pendant', [
    {"from": [5, 3, 7.5], "to": [11, 9, 8.5], "rotation": {"angle": 45, "axis": "z", "origin": [8, 6, 8]}, "faces": F([0, 0, 8, 8])},
    {"from": [6.8, 4.8, 7.2], "to": [9.2, 7.2, 8.8], "rotation": {"angle": 45, "axis": "z", "origin": [8, 6, 8]}, "faces": F([8, 0, 16, 8])},
    {"from": [7.5, 10, 7.6], "to": [8.5, 14, 8.4], "faces": F([0, 8, 8, 16])}, {"from": [6, 13, 7.6], "to": [10, 14, 8.4], "faces": F([0, 8, 8, 16])},
    {"from": [5.5, 10.5, 7.6], "to": [6.5, 13, 8.4], "faces": F([0, 8, 8, 16])}, {"from": [9.5, 10.5, 7.6], "to": [10.5, 13, 8.4], "faces": F([0, 8, 8, 16])}])
# --- Fox King Crown: gold band with five fox-ear spikes and orange gems (shows on your head)
tex('fox_king_crown', [(0, 0, 8, 8, lambda x, y: (250, 200, 60, 255) if y % 4 else (200, 140, 30, 255)),
                       (8, 0, 16, 8, lambda x, y: (255, 140, 30, 255) if (x + y) % 3 else (255, 210, 120, 255)),
                       (0, 8, 8, 16, lambda x, y: (255, 230, 140, 255))])
G, GEM, HI = [0, 0, 8, 8], [8, 0, 16, 8], [0, 8, 8, 16]
crown = [{"from": [3, 0, 3], "to": [13, 3, 4], "faces": F(G)}, {"from": [3, 0, 12], "to": [13, 3, 13], "faces": F(G)},
         {"from": [3, 0, 4], "to": [4, 3, 12], "faces": F(G)}, {"from": [12, 0, 4], "to": [13, 3, 12], "faces": F(G)}]
for x in (3.2, 7.2, 11.2):
    crown.append({"from": [x, 3, 3], "to": [x + 1.6, 6.5 if x == 7.2 else 5.5, 4], "faces": F(G)})
    crown.append({"from": [x, 3, 12], "to": [x + 1.6, 5.5, 13], "faces": F(G)})
crown += [{"from": [7.3, 1, 2.7], "to": [8.7, 2.4, 3.1], "faces": F(GEM)}, {"from": [4.3, 1, 2.7], "to": [5.3, 2, 3.1], "faces": F(GEM)},
          {"from": [10.7, 1, 2.7], "to": [11.7, 2, 3.1], "faces": F(GEM)}, {"from": [7.5, 6.5, 3.1], "to": [8.5, 7.3, 3.9], "faces": F(HI)}]
d = dict(DISP); d["head"] = {"translation": [0, 14.5, 0], "scale": [1.05, 1.05, 1.05]}
model('fox_king_crown', crown, d)
# --- Facility map: a folded blueprint sheet
tex('facility_map', [(0, 0, 16, 16, lambda x, y: (240, 240, 250, 255) if (x % 4 == 0 or y % 4 == 0) else ((230, 60, 60, 255) if (x, y) in ((10, 6), (11, 6), (10, 7), (11, 7)) else (40, 90, 170, 255)))])
model('facility_map', [
    {"from": [1, 0, 2], "to": [8, 0.3, 14], "rotation": {"angle": -22.5, "axis": "z", "origin": [8, 0, 8]}, "faces": F([0, 0, 8, 16])},
    {"from": [8, 0, 2], "to": [15, 0.3, 14], "rotation": {"angle": 22.5, "axis": "z", "origin": [8, 0, 8]}, "faces": F([8, 0, 16, 16])}],
    {**DISP, "gui": {"rotation": [60, 0, 0], "scale": [1, 1, 1]}})
p = A + 'lang/en_us.json'; s = open(p).read()
s = s.replace('  "block.xenowarriors.vr_console": "VR Console",', '''  "block.xenowarriors.vr_console": "VR Console",
  "item.xenowarriors.magical_orb": "Zane's Magical Orb",
  "item.xenowarriors.data_capsule": "Data Capsule",
  "item.xenowarriors.tracker_pendant": "F1 Tracker Pendant",
  "item.xenowarriors.fox_king_crown": "Fox King Crown",
  "item.xenowarriors.facility_map": "Facility Map",''')
open(p, 'w').write(s)
print('ok')
