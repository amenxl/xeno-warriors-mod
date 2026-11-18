"""The new characters' extra outfits (Nov 2026): Chase's navy cap look, Ava's and Persephone's casual looks. Skin, details,
model, then the girls' slim skin, the hair mask, the shirt-off form bodies and the cel versions - like yuri_c1pjs.
Run: python3 tools/xw/sp/outfits_nov.py [--preview out.png]"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
import sp_chars as S
RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
CH = RES + 'textures/entity/character/'
KEYS = ['chase_cap', 'ava_casual', 'persephone_casual']
for key in KEYS:
    mj, fit, atlas, skin = S.export(key, RES + f'models/detail/{key}.json')
    skin.save(CH + f'{key}.png'); atlas.save(CH + f'{key}_details.png')
    if key != 'chase_cap':
        sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/female'); import slim
        slim.convert(CH + f'{key}.png')
    print('built', key)
if '--preview' in sys.argv:
    sys.path.insert(0, '/home/claude/xw/ultima'); import ultima as U
    from PIL import Image, ImageDraw
    import json
    tiles = []
    for key in ['chase', 'chase_cap', 'ava', 'ava_casual', 'persephone', 'persephone_casual']:
        mj = json.load(open(RES + f'models/detail/{key}.json'))
        base = key.split('_')[0]
        im = U.preview(mj, Image.open(CH + key + '.png').convert('RGBA'), Image.open(CH + key + '_details.png').convert('RGBA'),
                       Image.open(CH + base + '_face.png').convert('RGBA'), yaws=(20, 200), scale=8)
        ImageDraw.Draw(im).text((6, 6), key, fill=(255, 255, 255)); tiles.append(im)
    tw, th = tiles[0].size
    sheet = Image.new('RGB', (tw * 2, th * 3), (30, 30, 36))
    for i, t in enumerate(tiles): sheet.paste(t, ((i % 2) * tw, (i // 2) * th))
    sheet.save(sys.argv[sys.argv.index('--preview') + 1])
