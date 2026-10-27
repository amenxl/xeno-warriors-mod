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

CHARS = {
    'ultima': dict(skin=(232, 170, 112), iris=(60, 242, 170), hair='spiky', shade=1.0, scar=True, glow=True, brow_y=46, eye_y=66, grin='smug_grin'),
    'mikiko': dict(skin=(132, 82, 52), iris=(250, 150, 30), hair='dreads', shade=0.55, scar=False, glow=True, brow_y=50, eye_y=67, grin='fang'),
}
# per-character tweaks on top of SET
EXTRA = {'mikiko': {'annoyed': {'anger': True}, 'rage': {'anger': True, 'glow_eyes': True}, 'fierce': {'anger': True}}}

def face(eL, eR, brows, mouth, o, c=None):
    c = c or CHARS['ultima']
    f = D.F(c['skin'], c['iris'], c['hair'])
    f.skin(c['shade'])
    for i, (side, cx) in enumerate(((-1, 35), (1, 93))):
        f.brow(cx, c['brow_y'], side, brows)
        f.eye(cx, c['eye_y'], side, (eL, eR)[i], o.get('look', 0.0), c['glow'], c['scar'] and side == -1, o.get('lookY', 0.0))
    f.nose()
    if mouth == 'smug_grin' and c['grin'] == 'fang': f.fang_grin()
    else: f.mouth(mouth)
    if o.get('blush'): f.blush()
    if o.get('tint'): f.tint(o['tint'])
    if o.get('tears'): f.tears(stream=o['tears'] is True)
    if o.get('drool'): f.drool()
    if o.get('overload'): f.overload()
    if o.get('glow_eyes'): f.glow_eyes()
    f.locks()
    if c['scar']: f.scar()
    if o.get('sweat'): f.sweat()
    if o.get('sparkle'): f.sparkle()
    if o.get('veins'): f.veins()
    if o.get('anger'): f.anger()
    if o.get('glitch'): f.glitch()
    if 'rain' in o: f.rain(o['rain'])
    return f.texture()

def overlay(draw):
    f = D.F(); draw(f); return f.texture()

if __name__ == '__main__':
    import sys as _s
    who = _s.argv[1] if len(_s.argv) > 1 else 'ultima'
    c = CHARS[who]
    names = [n for n in SET if who == 'ultima' or not n.startswith('overload')]
    for name in names:
        eL, eR, b, m, o = SET[name]
        o = dict(o, **EXTRA.get(who, {}).get(name, {}))
        face(eL, eR, b, m, o, c).save(R + f'{who}_drawn_face' + (('_' + name) if name else '') + '.png')
    def mouth_frame(kind):
        def draw(f):
            base = D.F(c['skin'], c['iris'], c['hair']); base.skin(c['shade'])
            patch = base.im.crop((44 * 4, 94 * 4, 84 * 4, 118 * 4))
            m = Image.new('L', patch.size, 0); ImageDraw.Draw(m).ellipse((0, 0, patch.width, patch.height), fill=255)
            f.im.paste(patch, (44 * 4, 94 * 4), m)
            f.mouth(kind)
        return draw
    def overlay2(draw):
        f = D.F(c['skin'], c['iris'], c['hair']); draw(f); return f.texture()
    for i, kind in enumerate(['small', 'o', 'grin', 'open_happy', 'shout']):
        overlay2(mouth_frame(kind)).save(R + f'{who}_drawn_mouth_{i + 1}.png')
    def dmg(level):
        def draw(f):
            for (x0, y0, x1, y1) in [(98, 84, 110, 92), (100, 88, 108, 96)][:level]:
                f.line([(x0, y0), (x1, y1)], (180, 40, 46), 1.1)
            f.poly([(14, 94), (30, 90), (31, 96), (15, 100)], (236, 222, 200, 255))
            f.line([(14, 94), (30, 90), (31, 96), (15, 100), (14, 94)], (150, 120, 100), 0.6)
            if level > 1:
                bb = Image.new('RGBA', f.im.size, (0, 0, 0, 0)); f.ell(92, 80, 10, 6, (120, 60, 120, 70), bb); f.blur_layer(bb, 2)
                f.line([(56, 44), (62, 54)], (180, 40, 46), 1.0)
        return draw
    for lv in (1, 2): overlay2(dmg(lv)).save(R + f'{who}_drawn_face_damage{lv}.png')
    print('wrote', len(names), 'faces for', who)
