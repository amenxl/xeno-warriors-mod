"""Sky Rebellion members: black SR hoodie (hood up) with the white eye on the chest, red demon-vessel eyes."""
import sys
sys.path.insert(0, '/home/claude/xw/npcs'); sys.path.insert(0, '/home/claude/xw/outfits'); sys.path.insert(0, '/home/claude/xw/detail'); sys.path.insert(0, '/home/claude/xw/ultima')
import npcs
from npcs import Fit, pack, shell, pants, shoes, clothes, paint_skin, face_img, export, TEX, MOD
from PIL import Image, ImageDraw
import ultima as U
BLK, BLK2, WHT = '#17151b', '#221f27', '#ececec'
def eye(x, y):
    # the SR eye: white almond with a dark pupil, centered on the chest
    dx, dy = (x - .5) * 2.2, (y - .55) * 3.2
    if dx * dx + dy * dy < 0.28: return '#141218' if dx * dx + dy * dy < 0.05 else WHT
    return BLK2
def base(f, trim):
    f.add('head', [-4.7, 27.0, -3.2], [4.7, 33.2, 4.8], BLK, {'top': lambda x, y: BLK2 if abs(x - .5) < .1 else BLK})       # hood
    f.add('head', [-4.7, 31.6, -5.0], [4.7, 33.2, -4.4], BLK2)
    for s in (1, -1): f.add('head', [s * 4.0 if s > 0 else -4.75, 25.6, -5.0], [4.75 if s > 0 else -4.0, 31.6, -4.3], BLK)
    shell(f, 'body', [-4, 11.5, -2], [4, 24, 2], 0.45, BLK2, {'front': eye})
    f.add('body', [-4.5, 11.5, -2.6], [4.5, 12.6, 2.6], trim)                # hoodie hem
    f.add('body', [-2.2, 16.8, -2.75], [2.2, 19.6, -2.5], WHT)            # the SR eye
    f.add('body', [-3.0, 17.6, -2.7], [3.0, 18.8, -2.5], WHT)
    f.add('body', [-0.8, 17.4, -2.85], [0.8, 19.0, -2.7], '#141218')
    f.both('right_arm', [3.5, 13.0, -2.45], [8.5, 24.4, 2.45], BLK2)
    f.both('right_arm', [3.45, 12.0, -2.55], [8.55, 13.4, 2.55], trim)       # cuffs
    pants(f, '#2a2830', 1.6); shoes(f, '#141216', '#0a090c', 2.4)
SPECS = {
    'sr_1': dict(skin='#e2b48e', eyes='#c01818', hair=['#d8d8e0', '#a8a8b8'], paint=clothes(BLK2, 12, '#2a2830', 12, '#141216', 3), mouth=['.kkkk.'], mouth_x=13, mouth_y=25),
    'sr_2': dict(skin='#8a5a3a', eyes='#c01818', hair=['#24202a', '#141018'], paint=clothes(BLK2, 12, '#2a2830', 12, '#141216', 3), mouth=['.kkkk.'], mouth_x=13, mouth_y=25),
}
SPECS['sr_1']['fit'] = lambda f: base(f, '#3a3640')
SPECS['sr_2']['fit'] = lambda f: base(f, '#5a1218')
if __name__ == '__main__':
    tiles = []
    for key, spec in SPECS.items():
        fit = Fit(); spec['fit'](fit)
        atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); pack(fit, atlas, region=(0, 0, 256, 256))
        skin = paint_skin(spec); face = face_img(spec)
        skin.save(TEX + key + '.png'); atlas.save(TEX + key + '_details.png'); face.save(TEX + key + '_face.png')
        mj = export(fit, MOD + key + '.json')
        im = U.preview(mj, skin, atlas, face, yaws=(20, 200), scale=6)
        ImageDraw.Draw(im).text((6, 6), 'SR member ' + key[-1], fill=(255, 255, 255)); tiles.append(im)
    tw, th = tiles[0].size
    sheet = Image.new('RGB', (tw * 2, th), (30, 30, 36))
    for i, t in enumerate(tiles): sheet.paste(t, (i * tw, 0))
    sheet.save('/tmp/sr_members.png'); print(sheet.size, TEX, MOD)
