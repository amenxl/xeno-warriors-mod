"""Zane's broom staff (crooked wooden handle, brown bristles tied with black bands and black stars), Max's dark serrated
knife and Sam's baseball bat (tan wood, a dark band, a dark taped grip)."""
import sys
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/items2')
exec(open('/home/claude/xeno-warriors-mod/tools/xw/items2/make.py').read().split('r = random.Random(4)')[0])
STAFF_DISP = {"gui": {"rotation": [0, 0, -45], "scale": [0.42, 0.42, 0.42]},
        "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        "fixed": {"rotation": [0, 0, -45], "scale": [0.45, 0.45, 0.45]},
        "thirdperson_righthand": {"rotation": [0, -90, 20], "translation": [0, 2.5, 0.5], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [0, 90, -20], "translation": [0, 2.5, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, -15], "translation": [1.13, 1.5, 1.13], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 90, 15], "translation": [1.13, 1.5, 1.13], "scale": [0.4, 0.4, 0.4]}}
WOOD = lambda x, y: (150, 96, 60, 255) if (x + y // 3) % 3 else (120, 74, 44, 255)
tex('zane_broom', [
    (0, 0, 4, 16, WOOD),
    (4, 0, 8, 16, lambda x, y: (184, 116, 74, 255) if (x * 3 + y) % 4 else (150, 90, 56, 255)),         # bristles
    (8, 0, 10, 16, lambda x, y: (22, 20, 24, 255)),                                                      # black band / stars
    (10, 0, 14, 4, lambda x, y: (176, 120, 80, 255)),                                                    # cut end
    (14, 0, 16, 16, lambda x, y: (100, 60, 36, 255))])
W, BR, BK, END, DK = [0, 0, 4, 16], [4, 0, 8, 16], [8, 0, 10, 16], [10, 0, 14, 4], [14, 0, 16, 16]
b = []
for i, (x, y0, y1) in enumerate(((7.3, 4.0, 10.0), (7.0, 9.6, 16.0), (7.5, 15.6, 22.0), (7.1, 21.6, 27.4))):   # crooked handle
    b.append({"from": [x, y0, 7.3], "to": [x + 1.5, y1, 8.7], "faces": F(W)})
b += [{"from": [6.6, 27.0, 7.1], "to": [8.9, 29.4, 8.9], "faces": {**F(W), "up": {"uv": END, "texture": "#t"}}},          # knobbly top
      {"from": [8.4, 28.2, 7.4], "to": [9.6, 31.4, 8.6], "faces": {**F(W), "up": {"uv": END, "texture": "#t"}}},          # little branch
      {"from": [6.8, 2.6, 7.0], "to": [9.0, 4.4, 9.0], "faces": F(DK)},                                                  # the tie at the neck
      {"from": [5.8, -6.0, 6.0], "to": [10.0, 2.8, 10.0], "faces": F(BR)},                                               # bristles, flaring out
      {"from": [5.0, -12.6, 5.2], "to": [10.8, -5.8, 10.8], "faces": F(BR)},
      {"from": [5.4, -14.6, 5.6], "to": [10.4, -12.4, 10.4], "faces": F(BR)},
      {"from": [5.7, -1.4, 5.9], "to": [10.1, -0.6, 10.1], "faces": F(BK)},                                              # black bands
      {"from": [4.9, -9.4, 5.1], "to": [10.9, -8.6, 10.9], "faces": F(BK)}]
for (cx, cy, z) in ((7.9, -4.0, 5.0), (8.0, -11.0, 4.3), (7.9, -4.0, 11.0), (8.0, -11.0, 11.7)):                     # black stars on the bristles
    zs = (z - 0.25, z + 0.25) if z < 8 else (z - 0.25, z + 0.25)
    b += [{"from": [cx - 1.2, cy - 0.3, zs[0]], "to": [cx + 1.2, cy + 0.3, zs[1]], "faces": F(BK)},
          {"from": [cx - 0.3, cy - 1.2, zs[0]], "to": [cx + 0.3, cy + 1.2, zs[1]], "faces": F(BK)},
          {"from": [cx - 0.7, cy - 0.7, zs[0]], "to": [cx + 0.7, cy + 0.7, zs[1]], "faces": F(BK)}]
model('zane_broom', b, STAFF_DISP)

KNIFE_DISP = {"gui": {"rotation": [0, 0, -45], "scale": [0.8, 0.8, 0.8]},
        "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 0, -45], "scale": [0.8, 0.8, 0.8]},
        "thirdperson_righthand": {"rotation": [0, -90, 40], "translation": [0, 3, 0.5], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [0, 90, -40], "translation": [0, 3, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]}}
tex('max_knife', [
    (0, 0, 4, 16, lambda x, y: (84 + 6 * (x % 2), 86 + 6 * (x % 2), 92 + 6 * (x % 2), 255)),             # dark steel
    (4, 0, 6, 16, lambda x, y: (176, 180, 190, 255)),                                                    # edge
    (6, 0, 10, 16, lambda x, y: (26, 24, 28, 255) if (x + y) % 3 else (44, 42, 48, 255)),                 # black grip
    (10, 0, 14, 16, lambda x, y: (60, 62, 68, 255)),
    (14, 0, 16, 16, lambda x, y: (40, 40, 46, 255))])
k = [{"from": [7.0, 6.0, 7.65], "to": [9.2, 19.0, 8.35], "faces": F([0, 0, 4, 16])},                                     # broad blade
     {"from": [9.2, 6.0, 7.75], "to": [9.6, 18.2, 8.25], "faces": F([4, 0, 6, 16])},                                   # edge
     {"from": [7.0, 19.0, 7.65], "to": [8.6, 21.4, 8.35], "faces": F([0, 0, 4, 16])},                                  # clipped point
     {"from": [8.6, 19.0, 7.75], "to": [9.3, 20.2, 8.25], "faces": F([4, 0, 6, 16])}]
for i in range(6):                                                                                                    # the serrated spine
    y = 8.0 + i * 1.8
    k.append({"from": [6.4, y, 7.7], "to": [7.0, y + 0.9, 8.3], "faces": F([10, 0, 14, 16])})
k += [{"from": [6.4, 5.2, 7.2], "to": [9.8, 6.0, 8.8], "faces": F([14, 0, 16, 16])},                                     # guard
      {"from": [7.3, -1.2, 7.3], "to": [8.9, 5.2, 8.7], "faces": F([6, 0, 10, 16])},                                    # grip
      {"from": [7.1, -1.8, 7.1], "to": [9.1, -1.2, 8.9], "faces": F([14, 0, 16, 16])}]
model('max_knife', k, KNIFE_DISP)

tex('sam_bat', [
    (0, 0, 4, 16, lambda x, y: (196, 160, 82, 255) if (x + y // 4) % 3 else (176, 140, 66, 255)),         # tan wood
    (4, 0, 8, 16, lambda x, y: (92, 62, 30, 255)),                                                       # dark band
    (8, 0, 12, 16, lambda x, y: (60, 40, 22, 255) if y % 3 else (40, 26, 14, 255)),                       # taped grip
    (12, 0, 16, 4, lambda x, y: (214, 180, 104, 255))])
T, BD, GP, CAP = [0, 0, 4, 16], [4, 0, 8, 16], [8, 0, 12, 16], [12, 0, 16, 4]
bt = [{"from": [7.25, 3.0, 7.25], "to": [8.75, 10.0, 8.75], "faces": F(T)},
      {"from": [7.0, 10.0, 7.0], "to": [9.0, 16.0, 9.0], "faces": F(T)},
      {"from": [6.8, 16.0, 6.8], "to": [9.2, 18.6, 9.2], "faces": F(BD)},                                                # the dark band
      {"from": [6.6, 18.6, 6.6], "to": [9.4, 28.0, 9.4], "faces": F(T)},
      {"from": [6.8, 28.0, 6.8], "to": [9.2, 29.4, 9.2], "faces": {**F(T), "up": {"uv": CAP, "texture": "#t"}}},
      {"from": [7.3, -3.4, 7.3], "to": [8.7, 3.0, 8.7], "faces": F(GP)},                                                # taped grip
      {"from": [6.9, -4.4, 6.9], "to": [9.1, -3.4, 9.1], "faces": F(BD)}]                                               # knob
model('sam_bat', bt, STAFF_DISP)
