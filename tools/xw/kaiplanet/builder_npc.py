"""Brik, the builder of King Kai's planet (an original character): yellow hard hat, blue work overalls over a white tee,
a tool belt with a hammer, work gloves and boots - and an Otherworld resident's halo."""
import sys
sys.path.insert(0, '/home/claude/xw/npcs'); sys.path.insert(0, '/home/claude/xw/outfits'); sys.path.insert(0, '/home/claude/xw/detail'); sys.path.insert(0, '/home/claude/xw/ultima')
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/npcs')
import npcs
from npcs import Fit, pack, shell, pants, shoes, clothes, paint_skin, face_img, export, TEX, MOD
from PIL import Image, ImageDraw
import ultima as U

HAT, HAT2, OVR, OVR2, TEE, BELT, GLOVE, BOOT, GOLD = '#f2c230', '#d29a18', '#2f5fa8', '#244a86', '#f2f0ea', '#6a4426', '#c89a5a', '#5a3a22', '#ffe27a'
KEY = 'kai_builder'
SPEC = dict(skin='#e2b48a', eyes='#5a3a22', hair=['#6a3a1e', '#4e2a14'],
            paint=clothes(TEE, 4, OVR, 12, BOOT, 3, extra=lambda part, face, x, y, W, H, outer:
                          (OVR if part == 'body' and y >= 4 and face in ('front', 'back') else
                           OVR if part == 'body' and face in ('front', 'back') and (x in (1, 2) or x in (W - 3, W - 2)) else None)),
            brow=['..kkkkk..', 'k.......k'], mouth=['k.....k', '.kkkkk.'], mouth_x=12, mouth_y=23,
            face_extra=[lambda px, cm, dark: [px.__setitem__((x, y), cm['h'] + (255,)) for x in range(9, 23) for y in (27, 28) if (x + y) % 3]])   # (stubble)

def fit_builder(f):
    # hard hat: dome, brim all round, a ridge down the middle
    f.add('head', [-4.8, 31.2, -4.8], [4.8, 34.0, 4.8], HAT, {'top': lambda x, y: HAT2 if abs(x - .5) < .07 else HAT})
    f.add('head', [-5.6, 30.6, -6.2], [5.6, 31.3, 5.6], HAT)
    f.add('head', [-0.6, 31.4, -5.0], [0.6, 34.4, 5.0], HAT2)
    f.add('head', [-4.7, 28.6, 4.0], [4.7, 31.0, 4.7], '#4e2a14')                       # hair at the back under the hat
    for s in (1, -1): f.add('head', [s * 4.0 if s > 0 else -4.7, 28.4, -1.0], [4.7 if s > 0 else -4.0, 31.0, 4.7], '#4e2a14')
    # the halo, floating over the hat
    r, y = 3.4, 36.2
    f.add('head', [-r, y, -r - 0.5], [r, y + 0.6, -r + 0.3], GOLD)
    f.add('head', [-r, y, r - 0.3], [r, y + 0.6, r + 0.5], GOLD)
    f.add('head', [-r - 0.5, y, -r], [-r + 0.3, y + 0.6, r], GOLD)
    f.add('head', [r - 0.3, y, -r], [r + 0.5, y + 0.6, r], GOLD)
    # overalls: bib with a pocket, straps over the tee
    shell(f, 'body', [-4, 12.2, -2], [4, 19.5, 2], 0.32, OVR)
    f.add('body', [-3.0, 19.2, -2.5], [3.0, 23.0, -2.15], OVR, {'front': lambda x, y: OVR2 if .3 < x < .7 and .25 < y < .7 else OVR})
    for s in (1, -1): f.add('body', [s * 2.0 if s > 0 else -3.2, 19.0, -2.45], [3.2 if s > 0 else -2.0, 24.4, 2.45], OVR)
    for s in (1, -1): f.add('body', [s * 2.2 if s > 0 else -3.0, 22.0, -2.6], [3.0 if s > 0 else -2.2, 22.8, -2.45], '#d8d8d8')   # buttons
    # tool belt with a hammer and a pouch
    shell(f, 'body', [-4, 12.0, -2], [4, 13.4, 2], 0.55, BELT)
    f.add('body', [-1.0, 12.1, -2.75], [1.0, 13.3, -2.55], '#c8c8c8')                    # buckle
    f.add('body', [2.6, 9.6, -2.9], [4.6, 13.0, -1.4], BELT)                             # pouch
    f.add('body', [-4.9, 7.0, -0.6], [-4.3, 12.6, 0.6], '#8a5a30')                       # hammer handle
    f.add('body', [-5.4, 11.2, -1.8], [-3.8, 12.8, 1.8], '#7a7c84')                      # hammer head
    # tee sleeves, gloves
    f.both('right_arm', [3.62, 20.5, -2.38], [8.38, 24.38, 2.38], TEE)
    f.both('right_arm', [3.5, 12.0, -2.5], [8.5, 15.6, 2.5], GLOVE, {'top': lambda x, y: '#a87a42'})
    pants(f, OVR, 2.4, cuff=OVR2)
    shoes(f, BOOT, '#2a1a10', 2.6, lace='#c8a060')

if __name__ == '__main__':
    fit = Fit(); fit_builder(fit)
    atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); pack(fit, atlas, region=(0, 0, 256, 256))
    skin = paint_skin(SPEC); face = face_img(SPEC)
    skin.save(TEX + KEY + '.png'); atlas.save(TEX + KEY + '_details.png'); face.save(TEX + KEY + '_face.png')
    mj = export(fit, MOD + KEY + '.json')
    im = U.preview(mj, skin, atlas, face, yaws=(20, 160), scale=6)
    im.save(sys.argv[1] if len(sys.argv) > 1 else '/tmp/kai_builder_preview.png')
    print(KEY, len(fit.cubes))
