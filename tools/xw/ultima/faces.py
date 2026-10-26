"""Ultima face options: evil smiles and bored looks."""
import sys
sys.path.insert(0, '/home/claude/xw/ultima')
import ultima as U
from PIL import Image, ImageDraw, ImageFont
EYE_OPEN = ['..kkkkkk.', 'kkkkkkkkk', '.wOOooOw.', '.wOOpoOw.', '.wOoopOw.', '..wOOOw..', '..kkkkk..']
EYE_NARROW = ['.........', 'kkk......', '.kkkkkkkk', '.wOOpoOwk', '..wOOOw..', '..kkkkk..', '.........']     # lids pressed down, sly
EYE_HALF = ['.........', '.........', 'kkkkkkkkk', 'kkkkkkkkk', '.wOOpoOw.', '..kkkkk..', '.........']       # heavy lids: bored
EYE_HALF_SIDE = ['.........', '.........', 'kkkkkkkkk', 'kkkkkkkkk', '.wwwOpOw.', '..kkkkk..', '.........']  # bored, eyes to the side
EYE_CLOSED_U = ['.........', '.........', '.........', '..kkkkk..', '.k.....k.', 'k.......k', '.........']  # ^ ^ closed grin
EYE_WIDE = ['..kkkkkk.', 'kkkkkkkkk', '.wwOOOww.', '.wwOpOww.', '.wwOOOww.', '..wwwww..', '..kkkkk..']       # crazed, small pupils
BROW_EVIL = ['......kkk', '...kkk...', 'kkk......']
BROW_FLAT = ['.........', 'kkkkkkk..', '.........']
BROW_UP = ['..kkkkk..', 'kk.....k.', '.........']
OPTS = [
    ('Evil grin (current)', BROW_EVIL, EYE_OPEN, ['kwwwwwwwwk', '.kwkwkwkk.', '..kkkkkk..'], 11),
    ('Sly evil smile', BROW_EVIL, EYE_NARROW, ['.........k', 'k.......k.', '.kkkkkkk..'], 11),
    ('Smirk (one side)', BROW_EVIL, EYE_NARROW, ['.......k', '......k.', 'kkkkkk..'], 12),
    ('Fanged grin', BROW_EVIL, EYE_OPEN, ['kkkkkkkkkk', 'kw.wwww.wk', 'k.wwwwww.k', '.kkkkkkkk.'], 11),
    ('Crazed smile', BROW_UP, EYE_WIDE, ['k........k', 'kwwwwwwwwk', '.kwwwwwwk.', '..kkkkkk..'], 11),
    ('Closed-eye grin', BROW_EVIL, EYE_CLOSED_U, ['kwwwwwwwwk', '.kkkkkkkk.'], 11),
    ('Bored', BROW_FLAT, EYE_HALF, ['.kkkkkk.'], 12),
    ('Bored, looking away', BROW_FLAT, EYE_HALF_SIDE, ['..kkkkk.'], 12),
    ('Unimpressed frown', BROW_FLAT, EYE_HALF, ['..kkkk..', '.k....k.'], 12),
    ('Bored, mouth open (sigh)', BROW_FLAT, EYE_HALF, ['.kkkk.', 'k....k', '.kkkk.'], 13),
]
title, build, gloves, sleek = U.OPTIONS['A']
fit = U.Fit(); build(fit)
atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); U.pack(fit, atlas, region=(0, 0, 256, 256))
mj = U.npcs.export(fit, '/tmp/ult_tmp.json')
F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 15)
tiles = []
for i, (name, brow, eye, mouth, mx) in enumerate(OPTS):
    spec = dict(U.SPEC); spec['paint'] = U.body_paint(gloves, sleek)
    spec.update(brow=brow, eye=eye, mouth=mouth, mouth_x=mx, mouth_y=23)
    skin = U.paint_skin(spec); face = U.face_img(spec)
    face.save(f'/home/claude/xw/ultima/out/face_opt_{i + 1}.png')
    im = U.preview(mj, skin, atlas, face, yaws=(0,), scale=14)
    W = im.width; im = im.crop((int(W * .2), 0, int(W * .8), int(im.height * .36)))
    d = ImageDraw.Draw(im); d.rectangle((0, 0, im.width, 22), fill=(20, 20, 28)); d.text((8, 3), f'{i + 1}. {name}', font=F, fill=(255, 255, 255))
    tiles.append(im)
tw, th = tiles[0].size
sheet = Image.new('RGB', (tw * 5, th * 2), (30, 30, 36))
for i, t in enumerate(tiles): sheet.paste(t, ((i % 5) * tw, (i // 5) * th))
sheet.save('/mnt/user-data/outputs/ultima_face_options.png'); print(sheet.size)
