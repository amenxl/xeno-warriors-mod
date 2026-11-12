"""Elementalists as wizards (original designs): long robes, wide sleeves, a tall pointed hat, a staff with an orb."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
import npcs
from npcs import clothes, shell, Fit, pack, paint_skin, face_img, darken, export, preview, TEX, MOD, CH
from PIL import Image

def wizard(f, robe, trim, hat, hat_band, orb, wood='#5a3a22', beard=None, hair_col=None, star=None):
    pat = (lambda x, y: star if star and (int(x * 8) * 7 + int(y * 12) * 5) % 11 == 0 else robe) if star else robe
    shell(f, 'body', [-4, 12, -2], [4, 24, 2], 0.34, robe, {k: pat for k in ('front', 'back', 'right', 'left')})
    f.add('body', [-4.5, 15.0, -2.5], [4.5, 16.2, 2.5], trim)                                         # sash
    f.add('body', [-1.2, 21.5, -2.55], [1.2, 24.3, -2.3], trim)                                       # collar clasp
    for s in ('right_leg',):                                                                         # the robe's skirt (moves with the legs)
        f.both(s, [-0.45, 0.4, -2.55], [4.45, 12.3, 2.55], robe, {k: pat for k in ('front', 'back', 'right', 'left')})
        f.both(s, [-0.6, 0.0, -2.7], [4.6, 1.2, 2.7], trim)                                          # hem
    f.both('right_arm', [3.45, 12.6, -2.55], [8.55, 24.45, 2.55], robe)                             # wide sleeves
    f.both('right_arm', [3.1, 12.0, -2.9], [8.9, 13.6, 2.9], trim)                                  # bell cuffs
    # the hat: a wide brim and a tall, bent cone
    f.add('head', [-6.6, 31.4, -6.6], [6.6, 32.3, 6.6], hat)
    f.add('head', [-4.7, 32.3, -4.7], [4.7, 33.5, 4.7], hat_band)
    tiers = [(4.3, 33.5, 35.6), (3.4, 35.6, 37.6), (2.5, 37.6, 39.4), (1.7, 39.4, 41.0)]
    for r, y0, y1 in tiers: f.add('head', [-r, y0, -r + 0.3], [r, y1, r + 0.3], hat)
    f.add('head', [-1.0, 40.6, -0.4], [1.0, 43.6, 1.6], hat, rot=[-35, 0, 0], origin=[0, 40.6, 0.6])  # the tip, bent back
    if hair_col:                                                                                    # hair under the hat
        f.add('head', [-4.55, 26.5, -1.0], [-4.05, 31.5, 4.55], hair_col); f.add('head', [4.05, 26.5, -1.0], [4.55, 31.5, 4.55], hair_col)
        f.add('head', [-4.55, 25.0, 4.05], [4.55, 31.5, 4.6], hair_col)
    if beard:
        f.add('head', [-3.4, 24.2, -4.75], [3.4, 26.6, -4.15], beard)
        f.add('head', [-2.6, 21.0, -4.95], [2.6, 24.4, -4.25], beard)
        f.add('head', [-1.4, 19.2, -4.85], [1.4, 21.2, -4.35], beard)
    # the staff, in the right hand, and its orb
    f.add('right_arm', [5.55, 1.0, -3.4], [6.65, 29.0, -2.3], wood)
    f.add('right_arm', [5.1, 26.0, -3.85], [7.1, 27.0, -1.85], '#c8a040')
    f.add('right_arm', [4.9, 27.0, -4.05], [7.3, 29.4, -1.65], orb, {'front': lambda x, y: '#ffffff' if x < .35 and y < .35 else orb})

def robe_paint(robe, trim, skin):
    return clothes(robe, 12, robe, 12, '#2a2024', 1, extra=lambda part, face, x, y, W, H, o:
                   (trim if part == 'body' and y in (3, 4) else None) if part == 'body' else (trim if part in ('rarm', 'larm') and y >= 10 else None))

SPECS = {
    'wiz_storm': dict(skin='#d6a07a', eyes='#3aa8ff', hair=['#cfd3dc', '#b8bcc8'], robe='#22306e', trim='#d8c060', hat='#1c285c', band='#d8c060', orb='#7ad0ff', star='#e8e070', hair_col='#cfd3dc',
                      mouth=['.kkkk.'], mouth_x=13, mouth_y=24),
    'wiz_ember': dict(skin='#c98a62', eyes='#ff7a28', hair=['#3a1a10', '#2a120a'], robe='#7a1616', trim='#f0a030', hat='#5e1010', band='#1a1a1a', orb='#ff8a2a', hair_col='#2a120a',
                      brow=['kkkkk....', '...kkk...'], mouth=['kkkkk'], mouth_x=13, mouth_y=24),
    'wiz_sage': dict(skin='#e2b894', eyes='#4a8a3a', hair=['#ececec', '#d8d8d8'], robe='#2e5a2a', trim='#c8b878', hat='#24481f', band='#7a5a2a', orb='#a8ff7a', beard='#ececec', hair_col='#ececec',
                      brow=['.kkkkk...'], mouth=[''], mouth_x=13, mouth_y=24),
    'wiz_frost': dict(skin='#f0d6c8', eyes='#8ad8ff', hair=['#e8f4ff', '#c8e0f0'], robe='#9ac8e0', trim='#f4fbff', hat='#7ab0cc', band='#f4fbff', orb='#e8f8ff', hair_col='#e8f4ff', star='#ffffff',
                      mouth=['.kk.'], mouth_x=14, mouth_y=24),
    'wiz_shadow': dict(skin='#a87050', eyes='#c86aff', hair=['#1a1020', '#120a18'], robe='#3a1a5a', trim='#a070e0', hat='#2a1244', band='#a070e0', orb='#c06aff', hair_col='#120a18', beard='#1a1020',
                      brow=['kkkkkk...', '.....kk..'], mouth=['.kkk.'], mouth_x=13, mouth_y=24),
}

if __name__ == '__main__':
    sheets = []
    for key, s in SPECS.items():
        spec = dict(skin=s['skin'], eyes=s['eyes'], hair=s['hair'], paint=robe_paint(s['robe'], s['trim'], s['skin']))
        for k in ('mouth', 'mouth_x', 'mouth_y', 'brow'):
            if k in s: spec[k] = s[k]
        spec['fit'] = lambda f, s=s: wizard(f, s['robe'], s['trim'], s['hat'], s['band'], s['orb'], beard=s.get('beard'), hair_col=s.get('hair_col'), star=s.get('star'))
        CH[key] = spec
        fit = Fit(); spec['fit'](fit)
        atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); pack(fit, atlas, region=(0, 0, 256, 256))
        skin = paint_skin(spec); face = face_img(spec)
        skin.save(TEX + key + '.png'); atlas.save(TEX + key + '_details.png'); face.save(TEX + key + '_face.png')
        darken(skin).save(TEX + key + '_dark.png'); darken(atlas).save(TEX + key + '_dark_details.png'); face_img(spec, dark=True).save(TEX + key + '_face_dark.png')
        mj = export(fit, MOD + key + '.json')
        sheets.append(preview(mj, skin, atlas, face, yaws=(20, 200), scale=5)); print(key, len(fit.cubes))
    tw = max(s.width for s in sheets); th = max(s.height for s in sheets)
    big = Image.new('RGB', (len(sheets) * tw, th), (30, 30, 36))
    for i, sh in enumerate(sheets): big.paste(sh, (i * tw, 0))
    big.save('/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/outfit/wizards.png')
