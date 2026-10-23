"""Fox skins and fox armor, painted on the vanilla fox model's 48x32 UV layout."""
from PIL import Image
import random, os

OUT = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/fox/'
os.makedirs(OUT, exist_ok=True)

def hx(h):
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)

def box_faces(u, v, w, h, d):
    """name -> (x, y, width, height) for a Minecraft box UV."""
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
            'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}

HEAD = box_faces(1, 5, 8, 6, 6)
EAR_R = box_faces(8, 1, 2, 2, 1)
EAR_L = box_faces(15, 1, 2, 2, 1)
NOSE = box_faces(6, 18, 4, 2, 3)
BODY = box_faces(24, 15, 6, 11, 6)      # rotated: front = belly, back = his back, top = chest
TAIL = box_faces(30, 0, 4, 9, 5)        # bottom face = tip end
LEG_R = box_faces(4, 24, 2, 6, 2)
LEG_L = box_faces(13, 24, 2, 6, 2)

class Tex:
    def __init__(self):
        self.img = Image.new('RGBA', (48, 32), (0, 0, 0, 0))
    def px(self, x, y, c):
        if 0 <= x < 48 and 0 <= y < 32: self.img.putpixel((x, y), hx(c) if isinstance(c, str) else c)
    def rect(self, r, c, rows=None):
        x, y, w, h = r
        for yy in range(h):
            if rows and yy not in rows: continue
            for xx in range(w):
                self.px(x + xx, y + yy, c)
    def faces(self, box, c):
        for r in box.values(): self.rect(r, c)

def skin(p, sleeping=False, seed=1):
    t = Tex(); rnd = random.Random(seed)
    # head
    t.faces(HEAD, p['fur'])
    for side in ('right', 'left'):
        t.rect(HEAD[side], p['cheek'], rows=[4, 5])
    fx, fy, fw, fh = HEAD['front']
    t.rect((fx, fy + 4, 2, 2), p['cheek']); t.rect((fx + 6, fy + 4, 2, 2), p['cheek'])
    t.rect(HEAD['top'], p.get('crown', p['fur']))
    ey = fy + 2
    if sleeping:
        for ex in (fx + 1, fx + 5):
            t.px(ex, ey + 1, p['line']); t.px(ex + 1, ey + 1, p['line'])
    else:
        for ex, flip in ((fx + 1, False), (fx + 5, True)):
            t.px(ex + (1 if flip else 0), ey, '#0c0808')
            t.px(ex + (0 if flip else 1), ey, p['eye'])
    # muzzle + nose
    t.faces(NOSE, p['cheek'])
    nx, ny, nw, nh = NOSE['front']
    t.px(nx + 1, ny, '#141010'); t.px(nx + 2, ny, '#141010')
    t.rect(NOSE['top'], p['fur'], rows=[0])
    # ears: dark outside, inner color on the front
    for e in (EAR_R, EAR_L):
        t.faces(e, p['ear']); t.rect(e['front'], p['inner'])
    # body
    t.faces(BODY, p['fur'])
    t.rect(BODY['front'], p['belly'])
    t.rect(BODY['top'], p['belly'])
    for side in ('right', 'left'):
        x, y, w, h = BODY[side]
        t.rect((x, y, w, h), p['fur'])
        # side shading toward the belly edge
        edge = 0 if side == 'right' else w - 1
        for yy in range(h): t.px(x + edge, y + yy, p['belly2'])
    bx, by, bw, bh = BODY['back']
    for yy in range(bh):
        t.px(bx + 2, by + yy, p['stripe']); t.px(bx + 3, by + yy, p['stripe'])
    # tail: tip at the far end
    t.faces(TAIL, p['fur'])
    for side in ('right', 'front', 'left', 'back'):
        x, y, w, h = TAIL[side]
        t.rect((x, y + h - 3, w, 3), p['tip'])
        t.rect((x, y + h - 4, w, 1), p.get('tip2', p['tip']))
    t.rect(TAIL['bottom'], p['tip'])
    # legs: fur at the top, socks below
    for leg in (LEG_R, LEG_L):
        t.faces(leg, p['leg'])
        for side in ('right', 'front', 'left', 'back'):
            x, y, w, h = TAIL and leg[side]
            t.rect((x, y, w, 1), p['fur'])
    # sparkles / specks
    for (cx, cy, cw, ch) in [BODY['back'], BODY['right'], BODY['left'], TAIL['right'], TAIL['left'], HEAD['top']]:
        for _ in range(p.get('specks', 0)):
            t.px(cx + rnd.randrange(cw), cy + rnd.randrange(max(1, ch - 3)), p['speck'])
    if 'marks' in p:   # kitsune-style face/leg markings
        t.px(fx + 3, fy + 0, p['marks']); t.px(fx + 4, fy + 0, p['marks']); t.px(fx + 3, fy + 1, p['marks']); t.px(fx + 4, fy + 1, p['marks'])
        for side in ('right', 'left'):
            x, y, w, h = HEAD[side]; t.px(x + 2, y + 3, p['marks']); t.px(x + 3, y + 3, p['marks'])
        for leg in (LEG_R, LEG_L):
            x, y, w, h = leg['front']; t.px(x, y + 2, p['marks']); t.px(x + 1, y + 2, p['marks'])
    return t.img

SKINS = {
    'ember':     dict(fur='#b8361a', crown='#9a2a12', cheek='#f4e2cc', belly='#f0dcc4', belly2='#d2a88a', stripe='#7c1e0e', eye='#ffb020', line='#4a140a',
                      ear='#2a1410', inner='#e07a40', tip='#ffb43a', tip2='#ff7a1e', leg='#241412', specks=3, speck='#ff9a3a'),
    'midnight':  dict(fur='#26222c', crown='#1c1a22', cheek='#d8d4dc', belly='#3a3642', belly2='#302c38', stripe='#141218', eye='#ff2a2a', line='#0c0a0e',
                      ear='#121014', inner='#4a3a50', tip='#f2f0f4', leg='#121014'),
    'sakura':    dict(fur='#f2a6c2', crown='#e890b2', cheek='#fff4f8', belly='#fff0f4', belly2='#f6cfdc', stripe='#e07ca2', eye='#7a2a5a', line='#a04a70',
                      ear='#c86890', inner='#ffd8e6', tip='#ffffff', leg='#d87aa0', specks=3, speck='#ffffff'),
    'kitsune':   dict(fur='#f4f2ee', crown='#ece8e2', cheek='#ffffff', belly='#ffffff', belly2='#e4e0da', stripe='#e8e2da', eye='#ffb81c', line='#8a6a5a',
                      ear='#f0ece6', inner='#e83a3a', tip='#e8303a', tip2='#ff7a3a', leg='#f0ece6', marks='#e8303a'),
    'golden':    dict(fur='#e8b830', crown='#d8a420', cheek='#fff6d8', belly='#fff2c8', belly2='#f0d68a', stripe='#c08a14', eye='#6a3a08', line='#8a5a10',
                      ear='#a87410', inner='#ffe090', tip='#fffbe8', leg='#b88214', specks=4, speck='#fff8c0'),
    'celestial': dict(fur='#2a2a6a', crown='#22225a', cheek='#b8c4ff', belly='#4a4a9a', belly2='#3a3a82', stripe='#5a3a9a', eye='#7af0ff', line='#9aa8ff',
                      ear='#1a1a48', inner='#8a6aff', tip='#7af0ff', tip2='#b8a0ff', leg='#1a1a48', specks=7, speck='#fff8d0'),
    'fox_king':  dict(fur='#c4241e', crown='#141012', cheek='#f6e6d6', belly='#f2dcc8', belly2='#d6a890', stripe='#141012', eye='#ffc23c', line='#3a1010',
                      ear='#141012', inner='#e8501e', tip='#141012', tip2='#5a1410', leg='#141012', marks='#ffb020'),
}
for i, (name, pal) in enumerate(SKINS.items()):
    skin(pal, False, i + 3).save(OUT + f'{name}.png')
    skin(pal, True, i + 3).save(OUT + f'{name}_sleep.png')

# ------------------------------------------------------------ fox armor (helmet / chest), per material
MATS = {
    'leather': ('#8a5a34', '#6a4224', '#b07a4a'), 'chainmail': ('#8a8e96', '#5a5e66', '#c0c4cc'),
    'iron': ('#c8ccd2', '#8e949c', '#f4f6f8'), 'gold': ('#f2c63a', '#b88a14', '#fff2a0'),
    'diamond': ('#4ad8d0', '#2a9a96', '#b8fff8'), 'netherite': ('#4a4248', '#2c262c', '#7a7078'),
    'turtle': ('#4a9a3a', '#2e6a24', '#8ad06a'),
}
for name, (base, dark, light) in MATS.items():
    holes = name == 'chainmail'
    # helmet: covers the head (brow guard on the front so the eyes still show)
    h = Tex()
    for face in ('top', 'right', 'left', 'back'):
        h.rect(HEAD[face], base)
    for face in ('right', 'left', 'back'):
        x, y, w, hh = HEAD[face]; h.rect((x, y + hh - 1, w, 1), dark); h.rect((x, y, w, 1), light)
    fx, fy, fw, fh = HEAD['front']
    h.rect((fx, fy, fw, 2), base); h.rect((fx, fy, fw, 1), light); h.rect((fx + 3, fy + 2, 2, 2), dark)   # nose guard
    tx, ty, tw, th = HEAD['top']; h.rect((tx + 3, ty, 2, th), light)                                          # crest
    for e in (EAR_R, EAR_L):
        for face in ('right', 'left', 'back', 'top'):
            h.rect(e[face], dark)
    # chest: plates along the back and sides, belly left open
    c = Tex()
    for face in ('back', 'right', 'left', 'top', 'bottom'):
        c.rect(BODY[face], base)
    for face in ('back', 'right', 'left'):
        x, y, w, hh = BODY[face]
        for yy in range(0, hh, 3): c.rect((x, y + yy, w, 1), dark)
        c.rect((x, y, w, 1), light)
    bx, by, bw, bh = BODY['back']
    for yy in range(1, bh, 3): c.px(bx + 1, by + yy, light); c.px(bx + bw - 2, by + yy, light)
    if holes:
        for img in (h, c):
            for y in range(32):
                for x in range(48):
                    if (x + y) % 2 == 0 and img.img.getpixel((x, y))[3]: img.img.putpixel((x, y), hx(dark))
    h.img.save(OUT + f'armor_helmet_{name}.png')
    c.img.save(OUT + f'armor_chest_{name}.png')

# preview sheet
sheet = Image.new('RGBA', (48 * 8, 32 * 8 // 2 * 2), (60, 60, 70, 255))
for i, name in enumerate(SKINS):
    sheet.paste(Image.open(OUT + f'{name}.png').resize((96, 64), Image.NEAREST), ((i % 4) * 96, (i // 4) * 64), Image.open(OUT + f'{name}.png').resize((96, 64), Image.NEAREST))
sheet.save('/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/fox_skins.png')
print('fox textures ok')
