"""Government shop/loot assets: supply terminal + vault block textures/models, keycard + human-mod injector items."""
import json, os, sys
from PIL import Image, ImageDraw

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/xenowarriors"
T = os.path.join(ROOT, "textures"); M = os.path.join(ROOT, "models"); BS = os.path.join(ROOT, "blockstates")
for d in ("block", "item"):
    os.makedirs(os.path.join(T, d), exist_ok=True); os.makedirs(os.path.join(M, d), exist_ok=True)
os.makedirs(BS, exist_ok=True)

def px(img, pts, c):
    d = ImageDraw.Draw(img)
    for p in pts: d.point(p, fill=c)

# --- terminal texture (16x16 split in quadrants: screen 0,0-8,8 ; body 8,0-16,8 ; base 0,8-8,16 ; trim 8,8-16,16)
t = Image.new("RGBA", (16, 16))
d = ImageDraw.Draw(t)
d.rectangle([0, 0, 7, 7], fill=(10, 24, 40, 255))                  # screen
d.rectangle([1, 1, 6, 6], fill=(20, 90, 140, 255))
for y in (2, 4): d.line([2, y, 5, y], fill=(140, 230, 255, 255))
d.point((2, 6 - 0), fill=(120, 255, 170, 255))
d.rectangle([8, 0, 15, 7], fill=(205, 212, 220, 255))               # white gov body
d.line([8, 3, 15, 3], fill=(64, 200, 255, 255))
d.rectangle([0, 8, 7, 15], fill=(60, 66, 76, 255))                  # dark base
d.line([0, 8, 7, 8], fill=(90, 98, 110, 255))
d.rectangle([8, 8, 15, 15], fill=(64, 200, 255, 255))               # cyan trim
d.rectangle([9, 9, 14, 14], fill=(40, 150, 210, 255))
t.save(os.path.join(T, "block", "gov_terminal.png"))
F = lambda uv: {f: {"uv": uv, "texture": "#t"} for f in ("north", "south", "east", "west", "up", "down")}
def el(a, b, faces): return {"from": a, "to": b, "faces": faces}
screen_faces = F([8, 0, 16, 8]); screen_faces["north"] = {"uv": [0, 0, 8, 8], "texture": "#t"}
model = {"parent": "block/block", "textures": {"t": "xenowarriors:block/gov_terminal", "particle": "xenowarriors:block/gov_terminal"},
         "elements": [el([3, 0, 4], [13, 2, 12], F([0, 8, 8, 16])),
                      el([5, 2, 6], [11, 9, 10], F([8, 0, 16, 8])),
                      el([2, 9, 5], [14, 16, 9], screen_faces),
                      el([2, 15.5, 4.5], [14, 16, 9.5], F([8, 8, 16, 16])),
                      el([4, 6, 3.5], [12, 7, 6], F([8, 8, 16, 16]))]}
json.dump(model, open(os.path.join(M, "block", "gov_terminal.json"), "w"))
json.dump({"variants": {f"facing={f}": {"model": "xenowarriors:block/gov_terminal", "y": y} for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}},
          open(os.path.join(BS, "gov_terminal.json"), "w"), indent=1)
json.dump({"parent": "xenowarriors:block/gov_terminal"}, open(os.path.join(M, "item", "gov_terminal.json"), "w"))

# --- vault: side, front (closed / open)
def steel():
    im = Image.new("RGBA", (16, 16), (96, 102, 112, 255)); dd = ImageDraw.Draw(im)
    dd.rectangle([0, 0, 15, 15], outline=(58, 62, 70, 255))
    for p in [(2, 2), (13, 2), (2, 13), (13, 13)]: dd.point(p, fill=(150, 156, 166, 255))
    return im, dd
s, sd = steel(); sd.line([0, 7, 15, 7], fill=(80, 86, 96, 255)); s.save(os.path.join(T, "block", "gov_vault_side.png"))
f, fd = steel()
fd.ellipse([3, 3, 12, 12], outline=(40, 44, 52, 255), fill=(120, 126, 136, 255))
fd.line([7, 4, 8, 11], fill=(40, 44, 52, 255)); fd.line([4, 7, 11, 8], fill=(40, 44, 52, 255))
fd.rectangle([13, 6, 14, 9], fill=(64, 200, 255, 255)); fd.point((13, 7), fill=(255, 70, 70, 255))
f.save(os.path.join(T, "block", "gov_vault_front.png"))
o = Image.new("RGBA", (16, 16), (20, 20, 24, 255)); od = ImageDraw.Draw(o)
od.rectangle([0, 0, 15, 15], outline=(96, 102, 112, 255)); od.rectangle([1, 1, 14, 14], outline=(70, 74, 82, 255))
od.rectangle([3, 10, 12, 13], fill=(200, 170, 60, 255)); od.point((5, 9), fill=(240, 220, 120, 255)); od.point((10, 9), fill=(64, 200, 255, 255))
o.save(os.path.join(T, "block", "gov_vault_open.png"))
for name, front in (("gov_vault", "gov_vault_front"), ("gov_vault_open", "gov_vault_open")):
    json.dump({"parent": "block/orientable", "textures": {"top": "xenowarriors:block/gov_vault_side", "side": "xenowarriors:block/gov_vault_side", "front": "xenowarriors:block/" + front}},
              open(os.path.join(M, "block", name + ".json"), "w"))
json.dump({"variants": {f"facing={f},open={op}": {"model": "xenowarriors:block/gov_vault" + ("_open" if op == "true" else ""), "y": y}
                        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)) for op in ("false", "true")}},
          open(os.path.join(BS, "gov_vault.json"), "w"), indent=1)
json.dump({"parent": "xenowarriors:block/gov_vault"}, open(os.path.join(M, "item", "gov_vault.json"), "w"))

# --- keycard
k = Image.new("RGBA", (16, 16)); kd = ImageDraw.Draw(k)
kd.rectangle([2, 4, 13, 11], fill=(225, 232, 240, 255), outline=(60, 70, 84, 255))
kd.rectangle([3, 5, 12, 6], fill=(64, 200, 255, 255))
kd.rectangle([4, 8, 6, 10], fill=(220, 180, 60, 255))
kd.line([8, 9, 12, 9], fill=(120, 130, 144, 255))
k.save(os.path.join(T, "item", "gov_keycard.png"))

# --- injectors: a syringe with coloured fluid
def injector(name, fluid):
    im = Image.new("RGBA", (16, 16)); dd = ImageDraw.Draw(im)
    body = [(4 + i, 11 - i) for i in range(7)]
    for (x, y) in body:
        dd.rectangle([x - 1, y - 1, x + 1, y + 1], fill=(200, 210, 222, 255))
    for (x, y) in body[1:6]: dd.point((x, y), fill=fluid)
    for (x, y) in body[2:5]: dd.point((x - 1, y), fill=fluid)
    dd.line([2, 13, 3, 12], fill=(160, 166, 176, 255))                   # needle
    dd.line([11, 3, 13, 1], fill=(90, 96, 108, 255)); dd.line([12, 0, 14, 2], fill=(90, 96, 108, 255))   # plunger
    dd.point((9, 8), fill=(255, 255, 255, 255))
    im.save(os.path.join(T, "item", name + ".png"))
for name, c in (("speed_mod", (80, 220, 255, 255)), ("jump_mod", (120, 255, 120, 255)), ("scanner_mod", (255, 70, 70, 255)), ("plating_mod", (255, 200, 60, 255))):
    injector(name, c)
for n in ("gov_keycard", "speed_mod", "jump_mod", "scanner_mod", "plating_mod"):
    json.dump({"parent": "item/generated", "textures": {"layer0": "xenowarriors:item/" + n}}, open(os.path.join(M, "item", n + ".json"), "w"))
print("ok")
