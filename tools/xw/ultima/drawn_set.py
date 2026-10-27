"""Writes Ultima's whole drawn face set (ultima_drawn_face*.png, 256x256) to the mod - one for every pixel face he
has, plus the drawn talking mouths and battle-damage overlays."""
import sys, random
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/ultima')
import drawn_faces2 as D
from PIL import Image, ImageDraw
R = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
SCAR = True
# name: (eyes L, eyes R, brows, mouth, options)
SET = {
    '':            ('sharp', 'sharp', 'angry', 'smug_grin', {}),
    'annoyed':     ('glare', 'glare', 'angry', 'frown', {}),
    'blink':       ('closed_flat', 'closed_flat', 'flat', 'line', {}),
    'bored':       ('bored', 'bored', 'flat', 'small', {}),
    'calm':        ('calm', 'calm', 'flat', 'line', {}),
    'concentrate': ('glare', 'glare', 'angry', 'line', {'look': 0.2}),
    'cool':        ('bored', 'bored', 'flat', 'smirk', {}),
    'crying':      ('calm', 'calm', 'soft', 'frown', {'tears': True}),
    'dizzy':       ('dizzy', 'dizzy', 'raised', 'wavy', {'sweat': True}),
    'eat':         ('closed', 'closed', 'soft', 'chew', {'blush': True}),
    'eat2':        ('closed', 'closed', 'soft', 'o', {'blush': True}),
    'excited':     ('wide', 'wide', 'raised', 'open_happy', {'sparkle': True}),
    'exhausted':   ('bored', 'bored', 'soft', 'o', {'sweat': True}),
    'exhausted2':  ('closed_flat', 'closed_flat', 'soft', 'o', {'sweat': True}),
    'fierce':      ('sharp', 'sharp', 'furious', 'grit', {}),
    'focused':     ('glare', 'glare', 'angry', 'line', {}),
    'freezing':    ('calm', 'calm', 'soft', 'grit', {'tint': (90, 150, 255)}),
    'glance_l':    ('calm', 'calm', 'flat', 'line', {'look': -0.9}),
    'glance_r':    ('calm', 'calm', 'flat', 'line', {'look': 0.9}),
    'glitch':      ('sharp', 'sharp', 'angry', 'line', {'glitch': True}),
    'grin':        ('sharp', 'sharp', 'angry', 'smug_grin', {}),
    'happy':       ('closed', 'closed', 'soft', 'open_happy', {'blush': True}),
    'headbang':    ('closed', 'closed', 'furious', 'open_happy', {}),
    'hum':         ('closed', 'closed', 'soft', 'whistle', {}),
    'hungry':      ('bored', 'bored', 'soft', 'o', {}),
    'hungry2':     ('bored', 'bored', 'soft', 'o', {'drool': True}),
    'hurt':        ('wince', 'glare', 'furious', 'grit', {'sweat': True}),
    'look_up':     ('calm', 'calm', 'raised', 'line', {'lookY': -1.0}),
    'melancholy':  ('calm', 'calm', 'soft', 'frown', {'look': -0.4, 'lookY': 0.6}),
    'overload':    ('sharp', 'sharp', 'furious', 'shout', {'overload': True}),
    'overload_eyes': ('sharp', 'sharp', 'furious', 'grit', {'overload': True}),
    'panic':       ('wide', 'wide', 'soft', 'shout', {'sweat': True}),
    'rage':        ('sharp', 'sharp', 'furious', 'shout', {'veins': True}),
    'rain':        ('calm', 'calm', 'flat', 'line', {'rain': 0}),
    'rain2':       ('calm', 'calm', 'flat', 'line', {'rain': 1}),
    'rain3':       ('calm', 'calm', 'flat', 'line', {'rain': 2}),
    'robot':       ('robot', 'robot', 'flat', 'line', {}),
    'robot_l':     ('robot', 'robot', 'flat', 'line', {'look': -1}),
    'robot_r':     ('robot', 'robot', 'flat', 'line', {'look': 1}),
    'sad':         ('calm', 'calm', 'soft', 'frown', {'lookY': 0.6}),
    'scared':      ('wide', 'wide', 'soft', 'o', {'sweat': True}),
    'sense':       ('closed_flat', 'closed_flat', 'angry', 'line', {}),
    'shout':       ('sharp', 'sharp', 'furious', 'shout', {}),
    'sick':        ('bored', 'bored', 'soft', 'wavy', {'tint': (110, 200, 90), 'sweat': True}),
    'sing':        ('closed', 'closed', 'soft', 'open_happy', {}),
    'sleep':       ('closed_flat', 'closed_flat', 'flat', 'small', {}),
    'sleepy':      ('bored', 'bored', 'soft', 'o', {}),
    'smug':        ('sharp', 'sharp', 'angry', 'smug_grin', {}),
    'snore':       ('closed_flat', 'closed_flat', 'flat', 'o', {}),
    'sparkle':     ('wide', 'wide', 'raised', 'open_happy', {'sparkle': True}),
    'strained':    ('glare', 'glare', 'furious', 'grit', {'sweat': True}),
    'surprised':   ('wide', 'wide', 'raised', 'o', {}),
    'thinking':    ('calm', 'calm', 'raised', 'smirk', {'look': -0.6, 'lookY': -0.8}),
    'tongue':      ('sharp', 'wink_closed', 'flat', 'tongue', {}),
    'whistle':     ('closed', 'closed', 'flat', 'whistle', {}),
    'wink':        ('sharp', 'wink_closed', 'flat', 'smug_grin', {}),
    'yawn':        ('closed_flat', 'closed_flat', 'soft', 'yawn', {'tears': 'drop'}),
}

def face(eL, eR, brows, mouth, o):
    f = D.F()
    f.skin(1.0)
    for i, (side, cx) in enumerate(((-1, 35), (1, 93))):
        f.brow(cx, 46, side, brows)
        f.eye(cx, 66, side, (eL, eR)[i], o.get('look', 0.0), True, SCAR and side == -1, o.get('lookY', 0.0))
    f.nose(); f.mouth(mouth)
    if o.get('blush'): f.blush()
    if o.get('tint'): f.tint(o['tint'])
    if o.get('tears'): f.tears(stream=o['tears'] is True)
    if o.get('drool'): f.drool()
    if o.get('overload'): f.overload()
    f.locks()
    if SCAR: f.scar()
    if o.get('sweat'): f.sweat()
    if o.get('sparkle'): f.sparkle()
    if o.get('veins'): f.veins()
    if o.get('glitch'): f.glitch()
    if 'rain' in o: f.rain(o['rain'])
    return f.texture()

def overlay(draw):
    f = D.F(); draw(f); return f.texture()

if __name__ == '__main__':
    for name, (eL, eR, b, m, o) in SET.items():
        face(eL, eR, b, m, o).save(R + 'ultima_drawn_face' + (('_' + name) if name else '') + '.png')
    # talking mouths: a patch of skin over his mouth, then the mouth shape (drawn over whatever face is showing)
    def mouth_frame(kind):
        def draw(f):
            base = D.F(); base.skin(1.0)
            patch = base.im.crop((44 * 4, 94 * 4, 84 * 4, 118 * 4))
            m = Image.new('L', patch.size, 0); ImageDraw.Draw(m).ellipse((0, 0, patch.width, patch.height), fill=255)
            f.im.paste(patch, (44 * 4, 94 * 4), m)
            f.mouth(kind)
        return draw
    for i, kind in enumerate(['small', 'o', 'grin', 'open_happy', 'shout']):
        overlay(mouth_frame(kind)).save(R + f'ultima_drawn_mouth_{i + 1}.png')
    # battle damage: scratches and a bandage
    def dmg(level):
        def draw(f):
            for (x0, y0, x1, y1) in [(98, 84, 110, 92), (100, 88, 108, 96)][:level]:
                f.line([(x0, y0), (x1, y1)], (180, 40, 46), 1.1)
            f.poly([(14, 94), (30, 90), (31, 96), (15, 100)], (236, 222, 200, 255))
            f.line([(14, 94), (30, 90), (31, 96), (15, 100), (14, 94)], (150, 120, 100), 0.6)
            if level > 1:
                b = Image.new('RGBA', f.im.size, (0, 0, 0, 0)); f.ell(92, 80, 10, 6, (120, 60, 120, 70), b); f.blur_layer(b, 2)
                f.line([(56, 40), (62, 52)], (180, 40, 46), 1.0)
        return draw
    for lv in (1, 2): overlay(dmg(lv)).save(R + f'ultima_drawn_face_damage{lv}.png')
    print('wrote', len(SET), 'faces')
