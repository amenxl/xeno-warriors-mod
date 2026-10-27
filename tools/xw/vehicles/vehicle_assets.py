"""Vehicle item icons, the dealer kiosk block (model, texture, blockstate), lang entries."""
import json
from PIL import Image, ImageDraw
A = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
def icon(name, draw):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im); draw(d); im.save(A + f'textures/item/{name}.png')
    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'xenowarriors:item/{name}'}}, open(A + f'models/item/{name}.json', 'w'))
K, T, G, W = (24, 24, 28, 255), (40, 40, 44, 255), (150, 205, 235, 255), (240, 240, 230, 255)
def car(d):
    d.rectangle((1, 7, 14, 11), fill=(190, 24, 30, 255)); d.rectangle((4, 4, 11, 7), fill=(190, 24, 30, 255)); d.rectangle((5, 5, 7, 6), fill=G); d.rectangle((8, 5, 10, 6), fill=G)
    d.rectangle((14, 8, 14, 9), fill=W); d.ellipse((2, 10, 5, 13), fill=K); d.ellipse((10, 10, 13, 13), fill=K); d.point((3, 11), fill=(170, 170, 180, 255)); d.point((11, 11), fill=(170, 170, 180, 255))
def bike(d):
    d.ellipse((0, 9, 5, 14), outline=K); d.ellipse((10, 9, 15, 14), outline=K); d.line((3, 11, 7, 7, 12, 11), fill=(190, 24, 30, 255), width=2)
    d.rectangle((5, 6, 9, 7), fill=(190, 24, 30, 255)); d.line((11, 11, 11, 5), fill=(170, 170, 180, 255)); d.line((10, 5, 13, 5), fill=T); d.point((13, 7), fill=W)
def heli(d):
    d.line((1, 3, 15, 3), fill=T); d.line((8, 3, 8, 5), fill=T); d.ellipse((3, 5, 11, 11), fill=(70, 140, 210, 255)); d.rectangle((9, 7, 15, 8), fill=(70, 140, 210, 255))
    d.ellipse((4, 6, 7, 9), fill=G); d.line((3, 13, 12, 13), fill=T); d.line((5, 11, 5, 13), fill=T); d.line((10, 11, 10, 13), fill=T); d.line((15, 5, 15, 9), fill=T)
def truck(d):
    d.rectangle((1, 4, 10, 11), fill=(70, 74, 82, 255)); d.rectangle((10, 6, 15, 11), fill=(70, 74, 82, 255)); d.rectangle((11, 7, 13, 8), fill=G)
    d.line((1, 8, 15, 8), fill=(180, 20, 24, 255)); d.ellipse((2, 10, 5, 13), fill=K); d.ellipse((11, 10, 14, 13), fill=K); d.point((15, 10), fill=W)
for n, f in (('car', car), ('motorbike', bike), ('helicopter', heli), ('gov_truck', truck)): icon(n, f)
# dealer kiosk texture 16x16: cyan/white panel with a car sign
tx = Image.new('RGBA', (16, 16), (0, 0, 0, 255)); d = ImageDraw.Draw(tx)
d.rectangle((0, 0, 15, 15), fill=(40, 46, 56, 255)); d.rectangle((0, 0, 7, 7), fill=(30, 170, 200, 255)); d.rectangle((1, 3, 6, 5), fill=(240, 240, 240, 255)); d.rectangle((2, 2, 5, 3), fill=(240, 240, 240, 255))
d.point((2, 6), fill=(20, 20, 20, 255)); d.point((5, 6), fill=(20, 20, 20, 255))
d.rectangle((8, 0, 15, 7), fill=(200, 205, 214, 255)); d.rectangle((0, 8, 7, 15), fill=(60, 66, 78, 255)); d.rectangle((8, 8, 15, 15), fill=(250, 220, 60, 255))
tx.save(A + 'textures/block/vehicle_dealer.png')
def face(uv): return {f: {'uv': uv, 'texture': '#t'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}
model = {'textures': {'t': 'xenowarriors:block/vehicle_dealer', 'particle': 'xenowarriors:block/vehicle_dealer'}, 'elements': [
    {'from': [3, 0, 3], 'to': [13, 2, 13], 'faces': face([0, 8, 8, 16])},
    {'from': [6, 2, 6], 'to': [10, 10, 10], 'faces': face([8, 0, 16, 8])},
    {'from': [2, 10, 6.5], 'to': [14, 16, 9.5], 'faces': {**face([8, 0, 16, 8]), 'north': {'uv': [0, 0, 8, 8], 'texture': '#t'}, 'south': {'uv': [0, 0, 8, 8], 'texture': '#t'}}},
    {'from': [2, 15.5, 6], 'to': [14, 16, 10], 'faces': face([8, 8, 16, 16])}]}
json.dump(model, open(A + 'models/block/vehicle_dealer.json', 'w'))
json.dump({'parent': 'xenowarriors:block/vehicle_dealer'}, open(A + 'models/item/vehicle_dealer.json', 'w'))
json.dump({'variants': {f'facing={f}': {'model': 'xenowarriors:block/vehicle_dealer', 'y': y} for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}},
          open(A + 'blockstates/vehicle_dealer.json', 'w'), indent=1)
lang = json.load(open(A + 'lang/en_us.json'))
lang.update({'item.xenowarriors.car': 'Car', 'item.xenowarriors.motorbike': 'Motorbike', 'item.xenowarriors.helicopter': 'Helicopter',
             'item.xenowarriors.gov_truck': 'Armored Truck', 'block.xenowarriors.vehicle_dealer': 'Vehicle Dealer',
             'entity.xenowarriors.car': 'Car', 'entity.xenowarriors.motorbike': 'Motorbike', 'entity.xenowarriors.helicopter': 'Helicopter',
             'entity.xenowarriors.gov_truck': 'Government Armored Truck'})
json.dump(lang, open(A + 'lang/en_us.json', 'w'), indent=2, ensure_ascii=False)
print('ok')
