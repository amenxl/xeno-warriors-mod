"""Ultima's in-game faces in the detailed anime style. Default (user's pick, 2026-10-26): option 8 'Narrow, distant'."""
import sys, os, glob
sys.path.insert(0, '/home/claude/xw/ultima')
import ultima as U
import anime_faces as A
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
A.EYES.update({
 'shut': ['...........', '...........', '...........', '...........', 'l.........k', '.kkkkkkkkk.', '..LLLLLLL..', '...........', '...........'],
 'happy': ['...........', '...........', '...kkkkk...', '..k.....k..', '.k.......k.', '...........', '...........', '...........', '...........'],
 'pain': ['...........', 'kk.........', '..kkk......', '.....kkkk..', '..kkk......', 'kk.........', '...........', '...........', '...........'],
 'wide': ['...kkkkkkk.', 'lkkkkkkkkkk', 'k.wwwwwwwk.', '..wwsUsww..', '..wsupWsw..', '..wsOOOsw..', '..wwsssww..', '...wwwww...', '....LLL....'],
})
A.MOUTHS.update({
 'grin': (['kWWWWWWWWk', '.kWkWkWkk.', '..kkkkkk..'], 11),
 'fang': (['kkkkkkkkkk', 'kW.WWWW.Wk', 'k.dddddd.k', '.kkkkkkkk.'], 11),
 'crazed': (['k........k', 'kWWWWWWWWk', '.kddddddk.', '..kkkkkk..'], 11),
 'shout': (['.kkkk.', 'kddddk', 'kddddk', '.kkkk.'], 13),
 'o': (['.kk.', 'kddk', '.kk.'], 14),
 'hurt_open': (['kkkkkk', 'kWWWWk', 'kkkkkk'], 13),
})
spec = dict(U.SPEC)
F = lambda b, e, m: A.face(spec, b, e, m)
V = {
    'base': F('flat', 'narrow', 'flat_off'),          # option 8: narrow, distant
    'blink': F('flat', 'shut', 'flat_off'),
    'grin': F('angry', 'open', 'grin'),
    'sly': F('angry', 'narrow', 'smirk'),
    'smirk': F('one_up', 'smug', 'smirk'),
    'fang': F('angry', 'sharp', 'fang'),
    'crazed': F('raised', 'wide', 'crazed'),
    'hurt': F('raised', 'pain', 'hurt_open'),
    'shout': F('angry', 'sharp', 'shout'),
    'tired': F('raised', 'half', 'open_sigh'),
    'frown': F('low', 'half', 'small_frown'),
    'surprised': F('raised', 'wide', 'o'),
    'happy': F('raised', 'happy', 'grin'),
    'annoyed': F('angry', 'sharp', 'tch'),
    'cold': F('low', 'cold', 'line'),
    'away': F('flat', 'half_side', 'flat_off'),
}
V['base'].save(CH + 'ultima_face.png')
V['crazed'].save(CH + 'ultima_face_overload_eyes.png')
U.overload_face(V['crazed']).save(CH + 'ultima_face_overload.png')
MAP = {'blink': 'blink', 'sleep': 'blink', 'sleepy': 'blink', 'snore': 'blink', 'yawn': 'tired', 'grin': 'grin', 'happy': 'happy', 'excited': 'crazed',
       'smug': 'smirk', 'cool': 'sly', 'wink': 'smirk', 'fierce': 'fang', 'rage': 'fang', 'focused': 'cold', 'concentrate': 'cold', 'sense': 'cold',
       'hurt': 'hurt', 'damage1': 'hurt', 'damage2': 'hurt', 'dizzy': 'tired', 'exhausted': 'tired', 'exhausted2': 'tired', 'strained': 'hurt',
       'panic': 'surprised', 'scared': 'surprised', 'surprised': 'surprised', 'shout': 'shout', 'sing': 'shout', 'headbang': 'shout', 'tongue': 'grin',
       'annoyed': 'annoyed', 'sad': 'frown', 'crying': 'frown', 'melancholy': 'frown', 'sick': 'tired', 'hungry': 'frown', 'hungry2': 'frown',
       'freezing': 'frown', 'glitch': 'crazed', 'sparkle': 'happy', 'thinking': 'away', 'bored': 'base', 'calm': 'base', 'glance_l': 'away', 'glance_r': 'away'}
n = 0
for f in glob.glob(CH + 'rhalix_face_*.png'):
    expr = os.path.basename(f)[len('rhalix_face_'):-4]
    V[MAP.get(expr, 'base')].save(CH + f'ultima_face_{expr}.png'); n += 1
print('faces', n)
# preview sheet of the main ones
from PIL import Image, ImageDraw, ImageFont
title, build, gloves, sleek = U.OPTIONS['A']
fit = U.Fit(); build(fit)
atlas = Image.open(CH + 'ultima_details.png').convert('RGBA'); skin = Image.open(CH + 'ultima.png').convert('RGBA')
import json; mj = json.load(open('/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/models/detail/ultima.json'))
Fn = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 15)
tiles = []
for k in ('base', 'blink', 'away', 'cold', 'annoyed', 'smirk', 'fang', 'shout', 'hurt', 'surprised'):
    im = U.preview(mj, skin, atlas, V[k], yaws=(0,), scale=14)
    W = im.width; im = im.crop((int(W * .2), int(im.height * .06), int(W * .8), int(im.height * .44)))
    d = ImageDraw.Draw(im); d.rectangle((0, 0, im.width, 22), fill=(20, 20, 28)); d.text((8, 3), 'default (8)' if k == 'base' else k, font=Fn, fill=(255, 255, 255))
    tiles.append(im)
tw, th = tiles[0].size
sheet = Image.new('RGB', (tw * 5, th * 2), (30, 30, 36))
for i, t in enumerate(tiles): sheet.paste(t, ((i % 5) * tw, (i // 5) * th))
sheet.save('/tmp/ultima_faces_ingame.png'); print(sheet.size)
