"""Order of Assassins members: dark hooded cloaks, a mask over the mouth, leather straps and a dagger sheath."""
import sys
sys.path.insert(0, '/home/claude/xw/npcs'); sys.path.insert(0, '/home/claude/xw/outfits'); sys.path.insert(0, '/home/claude/xw/detail'); sys.path.insert(0, '/home/claude/xw/ultima')
import npcs
from npcs import Fit, pack, hair, shell, pants, shoes, clothes, paint_skin, face_img, fringe_locks, export, TEX, MOD
from PIL import Image, ImageDraw
import ultima as U
BLK, BLK2, GRY, LEATH, STEEL = '#16141a', '#24202a', '#3a3640', '#4a3222', '#9aa0aa'
SPECS = {}
def base(f, accent):
    # hood: shell around the head, open face, a peak at the front, hanging down the back
    f.add('head', [-4.7, 27.0, -3.2], [4.7, 33.2, 4.8], BLK, {'top': lambda x, y: BLK2 if abs(x - .5) < .1 else BLK})
    f.add('head', [-4.7, 31.6, -5.0], [4.7, 33.2, -4.4], BLK2)
    for s in (1, -1): f.add('head', [s * 4.0 if s > 0 else -4.75, 24.6, -5.0], [4.75 if s > 0 else -4.0, 31.6, -4.3], BLK)
    f.add('head', [-4.0, 24.0, -4.95], [4.0, 27.0, -4.5], accent)          # mask over the mouth and nose
    f.add('body', [-4.6, 13.0, 2.0], [4.6, 24.2, 3.1], BLK, {'back': lambda x, y: BLK2 if abs(x - .5) < .05 else BLK})   # cloak back
    shell(f, 'body', [-4, 11.5, -2], [4, 24, 2], 0.45, BLK2, {'front': lambda x, y: BLK if abs(x - .5) < .06 else BLK2})
    f.add('body', [-4.0, 18.0, -2.7], [4.0, 18.8, -2.4], LEATH)             # chest strap
    f.add('body', [-4.5, 11.7, -2.7], [4.5, 12.7, 2.7], LEATH)              # belt
    f.add('body', [2.4, 8.0, -2.9], [3.4, 12.4, -2.5], LEATH); f.add('body', [2.6, 12.4, -3.0], [3.2, 13.6, -2.6], STEEL)   # dagger sheath + hilt
    f.both('right_arm', [3.5, 13.0, -2.45], [8.5, 24.4, 2.45], BLK2)
    f.both('right_arm', [3.45, 12.0, -2.55], [8.55, 15.2, 2.55], LEATH)     # bracers
    f.both('right_arm', [3.5, 22.2, -2.6], [8.5, 24.6, 2.6], GRY)           # shoulder wrap
    pants(f, '#1e1c22', 1.6); shoes(f, '#120f14', '#08070a', 2.4)
SPECS['order_1'] = dict(skin='#c99a76', eyes='#6a2a2a', hair=['#141216', '#0c0b0e'], paint=clothes(BLK2, 12, '#1e1c22', 12, '#120f14', 3), mouth=['......'], mouth_x=12, mouth_y=25)
SPECS['order_1']['fit'] = lambda f: base(f, '#26222c')
SPECS['order_2'] = dict(skin='#7a4e34', eyes='#3a3a3a', hair=['#141216', '#0c0b0e'], paint=clothes(BLK2, 12, '#1e1c22', 12, '#120f14', 3), mouth=['......'], mouth_x=12, mouth_y=25)
SPECS['order_2']['fit'] = lambda f: (base(f, '#7a1420'), f.add('body', [-4.8, 22.2, -2.9], [4.8, 24.3, 2.9], '#7a1420'))   # red scarf
if __name__ == '__main__':
    tiles = []
    for key, spec in SPECS.items():
        fit = Fit(); spec['fit'](fit)
        atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); pack(fit, atlas, region=(0, 0, 256, 256))
        skin = paint_skin(spec); face = face_img(spec)
        skin.save(TEX + key + '.png'); atlas.save(TEX + key + '_details.png'); face.save(TEX + key + '_face.png')
        mj = export(fit, MOD + key + '.json')
        im = U.preview(mj, skin, atlas, face, yaws=(20, 200), scale=6)
        ImageDraw.Draw(im).text((6, 6), key.replace('_', ' ').title(), fill=(255, 255, 255)); tiles.append(im)
    tw, th = tiles[0].size
    sheet = Image.new('RGB', (tw * 2, th), (30, 30, 36))
    for i, t in enumerate(tiles): sheet.paste(t, (i * tw, 0))
    sheet.save('/mnt/user-data/outputs/order_assassins.png'); print(sheet.size)
