"""All of Rhalix's faces in the chosen 'Mix B' style: low, dark brows; heavy half lids with a lash line under red
eyes; his side smirk with the stick. Overrides the shared face parts, then writes every expression."""
import sys
sys.argv = ['restyle.py', 'rhalix']
src = open('/home/claude/xw/faces/restyle.py').read().split("if __name__ == '__main__':")[0]
ns = {'__name__': 'x'}; exec(src, ns)
EYES, BROWS, MOUTHS, P, SPEC = ns['EYES'], ns['BROWS'], ns['MOUTHS'], ns['P'], ns['SPEC']
blank, put, pair = ns['blank'], ns['put'], ns['pair']
DST = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
P['k'] = (14, 8, 9, 255)            # lashes and brows: near-black

BROWS.update({
    'conf': (['..kkkkk..', 'kk.....k.'], 11), 'relax': (['..kkkkk..', 'kk.......'], 11), 'flat': (['.kkkkkkk.'], 12),
    'angry': (['kk.......', '..kkk....', '.....kkkk'], 10), 'sad': (['......kkk', '...kkk...', 'kk.......'], 10),
    'raised': (['..kkkkk..', '.k.....k.'], 9),
})
EYES.update({
    'open': ['', 'kkkkkkkkk', '.kkkkkkkk', '.wddoodw.', '..woppo..', '...kkkk..'],
    'half': ['', '', 'kkkkkkkkk', '.kkkkkkkk', '.wddoodw.', '...kkkk..'],
    'closed': ['', '', '', 'kkkkkkkk.', '.kkkkkk..'],
    'happy': ['', '', '..kkkkk..', '.k.....k.', 'k.......k'],
    'wide': ['..kkkkk..', '.kwwwwwk.', 'kwwoopwwk', 'kwwOpowwk', 'kwwwwwwwk', '.kwwwwwk.', '..kkkkk..'],
    'fierce': ['hhh......', 'kkkhhh...', '.kkkkkkk.', '.wdoopdk.', '..wOOOw..', '...kkk...'],
    'up': ['', 'kkkkkkkkk', '.wooppow.', '..wdddw..', '...kkkk..'],
    'sparkle': ['', 'kkkkkkkkk', '.wdWoWdw.', '..wOWOw..', '...kkkk..'],
    'sad': ['', '..kkkkk..', '.kkkkkkk.', '.wddoodw.', '..woppo..', '...kkkk..'],
})
LOOK_L = ['', 'kkkkkkkkk', '.kkkkkkkk', '.dowwwww.', '..opwww..', '...kkkk..']
LOOK_R = ['', 'kkkkkkkkk', '.kkkkkkkk', '.wwwwwod.', '..wwwpo..', '...kkkk..']
MOUTHS['smile'] = (['........kkkk', 'kkkkkkkkttkk', '........kkk.'], 11, 23)     # his side smirk, stick in the corner
MOUTHS['smirk'] = (['.......k', '.kkkkkk.'], 12, 24)

def face(brow='conf', eyes='open', mouth='smile', right_eye=None, tint=None, extras=()):
    im = blank(tint)
    b, by = BROWS[brow]
    pair(im, b, 4, by)
    if eyes == 'look_l':
        put(im, LOOK_L, 4, 14); put(im, LOOK_L, 19, 14)
    elif eyes == 'look_r':
        put(im, LOOK_R, 4, 14); put(im, LOOK_R, 19, 14)
    else:
        e = [r if r else '.........' for r in EYES[eyes]]
        re = [r if r else '.........' for r in EYES[right_eye]] if right_eye else None
        pair(im, e, 4, 14, re)
    m, mx, my = MOUTHS[mouth]
    put(im, m, mx, my)
    # extras drawn by the shared generator's code (tears, sweat, notes...)
    tmp = ns['face'](brow, eyes, mouth, right_eye, tint, extras)
    if extras:
        base = ns['face'](brow, eyes, mouth, right_eye, tint, ())
        for y in range(32):
            for x in range(32):
                if tmp.getpixel((x, y)) != base.getpixel((x, y)): im.putpixel((x, y), tmp.getpixel((x, y)))
    return im

made = 0
for expr, spec in SPEC.items():
    if expr == 'annoyed_rain_base': continue
    im = face(**spec)
    if expr in ('robot_l', 'robot_r'):
        shifted = blank(); shifted.paste(im.crop((0, 0, 32, 32)), (0, 0))
        for y in range(14, 20):
            row = [im.getpixel((x, y)) for x in range(32)]
            for x in range(3, 29): shifted.putpixel((x, y), row[x + (1 if expr == 'robot_l' else -1)])
        im = shifted
    im.save(DST + ('rhalix_face.png' if expr is None else f'rhalix_face_{expr}.png')); made += 1
print(made, 'faces')

# contact sheet
from PIL import Image, ImageDraw, ImageFont
names = [k for k in SPEC if k != 'annoyed_rain_base']
cols = 10; S = 4
sheet = Image.new('RGBA', (cols * (32 * S + 6) + 6, ((len(names) + cols - 1) // cols) * (32 * S + 20) + 6), (34, 34, 42, 255))
dr = ImageDraw.Draw(sheet); font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 11)
for i, n in enumerate(names):
    im = Image.open(DST + ('rhalix_face.png' if n is None else f'rhalix_face_{n}.png')).crop((0, 0, 32, 32)).resize((32 * S, 32 * S), Image.NEAREST)
    x = 6 + (i % cols) * (32 * S + 6); y = 6 + (i // cols) * (32 * S + 20)
    sheet.paste(im, (x, y + 14)); dr.text((x, y), str(n or 'normal'), fill=(230, 230, 235), font=font)
sheet.save('/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/faces/rhalix_all.png')
