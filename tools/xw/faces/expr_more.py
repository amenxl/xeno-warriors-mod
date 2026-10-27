"""More expressions, and proper faces for every expression name the game uses, for the characters made from the face
template (Kaeru, the Sky Patrol, Ai-oni, Technica, Stevie, Garry, Yuri, Zane). Mikiko, Rhalix and Ultima (hand-made HD
faces) get the new expressions put together from their own faces. New expressions: laugh, blush, love, shocked,
determined, pout, smirk."""
import sys, os, glob
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/sp'); sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/kaeru')
from PIL import Image
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'

# eyes (9 wide, 7 tall; the right eye is mirrored)
E = {
    'shut':   ['.........', '.........', '.........', '.........', 'kkkkkkkkk', '.........', '.........'],
    'happy':  ['.........', '.........', '..kkkkk..', '.k.....k.', 'k.......k', '.........', '.........'],
    'wide':   ['..kkkkkk.', 'kkkkkkkkk', '.wwOOOww.', '.wwOpOww.', '.wwOOOww.', '..wwwww..', '..kkkkk..'],
    'narrow': ['.........', 'kkk......', '.kkkkkkkk', '.wOOpoOwk', '..wOOOw..', '..kkkkk..', '.........'],
    'half':   ['.........', '.........', 'kkkkkkkkk', '.wOOpoOw.', '..wOOOw..', '..kkkkk..', '.........'],
    'heart':  ['.........', '.rr...rr.', 'rrrr.rrrr', 'rrrrrrrrr', '.rrrrrrr.', '..rrrrr..', '....r....'],
    'spiral': ['..kkkkk..', '.k.....k.', 'k..kkk..k', 'k.k..k..k', 'k..k...k.', '.k..kkk..', '..k......'],
    'pain':   ['.........', 'kk.......', '..kkk....', '....kkkk.', '..kkk....', 'kk.......', '.........'],
    'tiny':   ['..kkkkkk.', 'kkkkkkkkk', '.wwwwwww.', '.wwwpwww.', '.wwwwwww.', '..wwwww..', '..kkkkk..'],
    'star':   ['..kkkkkk.', 'kkkkkkkkk', '.wOOWOOw.', '.wOWWWOw.', '.wOOWOOw.', '..wOOOw..', '..kkkkk..'],
}
M = {
    'smile': ['k......k', '.kkkkkk.'], 'grin': ['kwwwwwwk', '.kwwwwk.', '..kkkk..'], 'open': ['.kkkk.', 'k....k', 'k....k', '.kkkk.'],
    'laugh': ['kkkkkkkk', 'kwwwwwwk', 'k.rrrr.k', '.kkkkkk.'], 'frown': ['..kkkk..', '.k....k.'], 'flat': ['..kkkk..'],
    'o': ['.kk.', 'k..k', '.kk.'], 'tongue': ['k......k', '.kkkkkk.', '...rr...'], 'chew': ['.kkk.', 'k...k', '.kkk.'],
    'chew2': ['.kkkk.'], 'teeth': ['kkkkkkkk', 'kwkwkwkk', 'kkkkkkkk'], 'wavy': ['.k.k.k.k', 'k.k.k.k.'],
    'smirk': ['......kk', 'kkkkkk..'], 'pout': ['..kk..', '.k..k.', '..kk..'], 'fierce': ['kkkkkkkk', 'kwkwwkwk', '.kkkkkk.'],
    'whistle': ['.k.', 'k.k', '.k.'], 'sing': ['.kkkk.', 'k.rr.k', '.kkkk.'], 'yawn': ['.kkkk.', 'k....k', 'k.rr.k', 'k....k', '.kkkk.'],
}
B = {'flat': ['.........', '.kkkkkk..', 'k........'], 'mad': ['......kkk', '...kkk...', 'kkk......'], 'up': ['..kkkkk..', 'kk.....k.', '.........'],
     'worried': ['kkk......', '...kkk...', '......kkk']}

def tint(im, rgb, amt):
    out = im.copy(); px = out.load()
    for y in range(32):
        for x in range(32):
            r, g, b, a = px[x, y]
            if a: px[x, y] = (int(r + (rgb[0] - r) * amt), int(g + (rgb[1] - g) * amt), int(b + (rgb[2] - b) * amt), a)
    return out

def paint(im, pts, c):
    px = im.load()
    for x, y in pts:
        if 0 <= x < 32 and 0 <= y < 32: px[x, y] = c
    return im

BLUSH = [(4, 21), (5, 21), (6, 21), (5, 22), (25, 21), (26, 21), (27, 21), (26, 22)]
TEARS = [(5, 21), (5, 22), (5, 23), (6, 24), (6, 25), (26, 21), (26, 22), (26, 23), (25, 24), (25, 25)]
SWEAT = [(28, 9), (28, 10), (27, 11), (28, 11), (29, 11)]
DROPS = [(8, 8), (8, 9), (22, 7), (22, 8), (14, 12), (26, 23), (5, 25)]

def template_faces(key, spec, facefn, masked=False):
    """Every expression the game asks for, for a face-template character."""
    eye0 = spec['eye']
    def f(brow=None, eye=None, mouth=None, mx=None, my=None, wink=False, look=0, up=False):
        kw = {}
        if brow: kw['brow'] = B[brow]
        if eye: kw['eye'] = E[eye] if isinstance(eye, str) else eye
        if mouth and not masked:
            kw['mouth'] = M[mouth]; kw['mouth_x'] = mx if mx is not None else 16 - len(M[mouth][0]) // 2; kw['mouth_y'] = my if my is not None else spec.get('mouth_y', 24)
        heart = kw.get('eye') is E['heart']
        if heart: kw['eye'] = E['shut']
        if 'mouth' in kw and getattr(facefn, 'plain', False): kw['mouth'] = [r.replace('r', 'm') for r in kw['mouth']]
        im = facefn(spec, **kw)
        if heart:
            px = im.load(); base = facefn(spec).load()
            for x0 in (3, 20):
                for yy in range(14, 21):
                    for xx in range(x0, x0 + 9): px[xx, yy] = base[2, 16]
                for j, row in enumerate(E['heart']):
                    for i, ch in enumerate(row):
                        if ch == 'r': px[x0 + i, 14 + j] = (230, 60, 90, 255)
        if wink:                                                     # one eye shut (her left)
            px = im.load()
            for y in range(13, 22):
                for x in range(20, 29): px[x, y] = facefn(spec).getpixel((2, 16))
            for x in range(20, 29): px[x, 18] = (25, 18, 18, 255)
        if look or up:                                               # pupils off to one side / up
            src = im.copy(); px = im.load(); sp = src.load()
            for x0 in (3, 20):
                for y in range(15, 20):
                    for x in range(x0 + 1, x0 + 8):
                        sx, sy = x - look, y + (1 if up else 0)
                        if x0 + 1 <= sx < x0 + 8 and 15 <= sy < 20: px[x, y] = sp[sx, sy]
        return im
    pink, tear, sick, cold = (236, 120, 140, 255), (120, 190, 255, 255), (150, 210, 120), (150, 190, 255)
    V = {}
    V['base'] = f(); V['calm'] = f(); V['blink'] = f(eye='shut')
    V['happy'] = f(eye='happy', mouth='grin'); V['grin'] = f(brow='mad', eye='narrow', mouth='grin'); V['excited'] = f(brow='up', eye='star', mouth='laugh')
    V['smug'] = f(brow='flat', eye='half', mouth='smirk'); V['cool'] = f(brow='flat', eye='half', mouth='smile'); V['wink'] = f(mouth='grin', wink=True)
    V['fierce'] = f(brow='mad', eye=eye0, mouth='fierce'); V['rage'] = f(brow='mad', eye='narrow', mouth='teeth'); V['focused'] = f(brow='mad', eye='narrow', mouth='flat')
    V['concentrate'] = f(brow='mad', eye='shut', mouth='teeth'); V['sense'] = f(brow='flat', eye='shut', mouth='flat')
    V['hurt'] = f(brow='worried', eye='pain', mouth='teeth'); V['strained'] = paint(f(brow='worried', eye='half', mouth='teeth'), SWEAT, tear)
    V['dizzy'] = f(eye='spiral', mouth='wavy'); V['exhausted'] = paint(f(brow='worried', eye='half', mouth='open'), SWEAT, tear); V['exhausted2'] = paint(f(brow='worried', eye='half', mouth='o'), SWEAT, tear)
    V['panic'] = paint(f(brow='worried', eye='tiny', mouth='open'), SWEAT, tear); V['scared'] = f(brow='worried', eye='wide', mouth='wavy'); V['surprised'] = f(brow='up', eye='wide', mouth='o')
    V['shout'] = f(brow='mad', eye=eye0, mouth='open'); V['headbang'] = f(brow='mad', eye='shut', mouth='open'); V['sing'] = f(brow='up', eye='shut', mouth='sing')
    V['hum'] = f(eye='shut', mouth='smile'); V['whistle'] = f(brow='up', eye='half', mouth='whistle'); V['tongue'] = f(eye='happy', mouth='tongue')
    V['annoyed'] = f(brow='mad', eye='half', mouth='flat'); V['sad'] = f(brow='worried', eye=eye0, mouth='frown'); V['melancholy'] = f(brow='worried', eye='half', mouth='frown')
    V['crying'] = paint(f(brow='worried', eye='shut', mouth='frown'), TEARS, tear)
    V['sleep'] = f(eye='shut', mouth='flat'); V['snore'] = f(eye='shut', mouth='o'); V['sleepy'] = f(eye='half', mouth='flat'); V['yawn'] = f(eye='shut', mouth='yawn')
    V['eat'] = f(eye='happy', mouth='chew'); V['eat2'] = f(eye='happy', mouth='chew2')
    V['hungry'] = f(brow='worried', eye='half', mouth='o'); V['hungry2'] = f(brow='worried', eye='half', mouth='wavy')
    V['sick'] = tint(f(brow='worried', eye='half', mouth='wavy'), sick, 0.35); V['freezing'] = tint(f(brow='worried', eye='half', mouth='teeth'), cold, 0.3)
    V['rain'] = paint(f(brow='worried', eye='half', mouth='flat'), DROPS, tear); V['rain2'] = paint(f(brow='worried', eye='half', mouth='flat'), [(x, y + 1) for x, y in DROPS], tear)
    V['rain3'] = paint(f(brow='worried', eye='shut', mouth='flat'), [(x, y + 2) for x, y in DROPS], tear)
    V['sparkle'] = f(brow='up', eye='star', mouth='grin'); V['glitch'] = f(eye='tiny', mouth='wavy')
    V['thinking'] = f(brow='up', eye=eye0, mouth='flat', look=2, up=True); V['bored'] = f(eye='half', mouth='flat')
    V['look_up'] = f(eye=eye0, up=True); V['glance_l'] = f(eye=eye0, look=-2); V['glance_r'] = f(eye=eye0, look=2)
    V['robot'] = f(eye='happy', mouth='flat'); V['robot_l'] = f(eye=eye0, look=-2, mouth='flat'); V['robot_r'] = f(eye=eye0, look=2, mouth='flat')
    # the new ones
    V['laugh'] = f(brow='up', eye='happy', mouth='laugh'); V['blush'] = paint(f(eye='happy', mouth='smile'), BLUSH, pink)
    V['love'] = paint(f(eye='heart', mouth='grin'), BLUSH, pink); V['shocked'] = paint(f(brow='up', eye='tiny', mouth='open'), SWEAT, tear)
    V['determined'] = f(brow='mad', eye='narrow', mouth='grin'); V['pout'] = paint(f(brow='mad', eye='half', mouth='pout'), BLUSH, pink)
    V['smirk'] = f(brow='flat', eye=eye0, mouth='smirk')
    return V

def composite(key):
    """New expressions for the hand-made HD faces, put together from their own faces (eyes from one, mouth from another)."""
    def load(n):
        p = CH + f'{key}_face_{n}.png'
        return Image.open(p).convert('RGBA') if os.path.exists(p) else Image.open(CH + f'{key}_face.png').convert('RGBA')
    def mix(top, bottom, split=22):
        a, b = load(top), load(bottom); out = a.copy()
        out.paste(b.crop((0, split, 32, 32)), (0, split)); return out
    pink, tear = (236, 120, 140, 255), (120, 190, 255, 255)
    V = {'laugh': mix('happy', 'shout'), 'blush': paint(load('happy'), BLUSH, pink), 'shocked': paint(mix('surprised', 'shout'), SWEAT, tear),
         'determined': mix('focused', 'grin'), 'pout': paint(mix('annoyed', 'surprised'), BLUSH, pink), 'smirk': load('smug')}
    love = load('happy').copy()
    heart = E['heart']
    px = love.load()
    for x0 in (3, 20):
        for j, row in enumerate(heart):
            for i, ch in enumerate(row):
                if ch == 'r': px[x0 + i, 14 + j] = (230, 60, 90, 255)
    V['love'] = paint(love, BLUSH, pink)
    return V

TEMPLATE = ['kyrie', 'lexy', 'max', 'sam', 'technica', 'aioni', 'stevie', 'garry', 'yuri', 'zane', 'kaeru']
HD = ['mikiko', 'rhalix', 'ultima']

if __name__ == '__main__':
    import sp_chars as S
    names = sorted({os.path.basename(p)[len('rhalix_face_'):-4] for p in glob.glob(CH + 'rhalix_face_*.png')} - {'damage1', 'damage2'})
    NEW = ['laugh', 'blush', 'love', 'shocked', 'determined', 'pout', 'smirk']
    for key in TEMPLATE:
        if key == 'kaeru':
            import kaeru as K, npcs
            spec = dict(K.SPEC); spec['mouth'] = [r.replace('r', 'm') for r in spec['mouth']]
            facefn = lambda sp, **kw: npcs.face_img(dict(sp, **kw))
            facefn.plain = True
        else:
            spec = S.CHARS[key]; facefn = lambda sp, **kw: S.face(sp, **kw)
        V = template_faces(key, spec, facefn, masked=(key == 'lexy'))
        for n in names + NEW:
            V.get(n, V['base']).save(CH + f'{key}_face_{n}.png')
        print(key, len(names + NEW))
    for key in HD:
        for n, im in composite(key).items(): im.save(CH + f'{key}_face_{n}.png')
        print(key, 'new', len(NEW))
