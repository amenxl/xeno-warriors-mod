"""Story menu banners (textures/gui/story/<path>.png, 512x256): the storyline's characters on a gradient in its colours.
Rhalix's and Mikiko's are the user's own art; the finale has both of them; the Sky Rebellion and the Government
(no art of Senju or Kyle yet) use their HUD portraits with the faction logos.
usage: python3 banners.py <dir with the user's art: rhalix.png (Illustration 11), mikiko.png (Illustration 08)>"""
import sys, os
from PIL import Image, ImageDraw, ImageFilter
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
GUI = ROOT + '/src/main/resources/assets/xenowarriors/textures/gui/'
OUT = GUI + 'story/'
W, H = 512, 256

def grad(c1, c2):
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    for x in range(W):
        t = x / (W - 1)
        d.line([(x, 0), (x, H)], fill=tuple(int(a + (b - a) * t) for a, b in zip(c1, c2)) + (255,))
    # soft diagonal light streaks
    over = Image.new('RGBA', (W, H), (0, 0, 0, 0)); o = ImageDraw.Draw(over)
    for k in range(5):
        x = 60 + k * 100
        o.polygon([(x, 0), (x + 30, 0), (x - 70, H), (x - 100, H)], fill=(255, 255, 255, 14))
    return Image.alpha_composite(im, over)

def fit(im, h):
    im = im.convert('RGBA'); bb = im.getbbox()
    if bb: im = im.crop(bb)
    w = int(im.width * h / im.height)
    return im.resize((w, h), Image.LANCZOS)

def shadow(im):
    s = Image.new('RGBA', im.size, (0, 0, 0, 0)); a = im.split()[3].point(lambda v: int(v * 0.55))
    s.putalpha(a); return s.filter(ImageFilter.GaussianBlur(6))

def place(bg, im, cx, bottom):
    x, y = int(cx - im.width / 2), int(bottom - im.height)
    sh = shadow(im); bg.alpha_composite(Image.new('RGBA', bg.size, (0, 0, 0, 0)), (0, 0))
    tmp = Image.new('RGBA', bg.size, (0, 0, 0, 0)); tmp.paste(sh, (x + 6, y + 4), sh); bg.alpha_composite(tmp)
    tmp = Image.new('RGBA', bg.size, (0, 0, 0, 0)); tmp.paste(im, (x, y), im); bg.alpha_composite(tmp)

def vignette(bg):
    v = Image.new('RGBA', (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(v)
    for i in range(40): d.rectangle([i, i, W - 1 - i, H - 1 - i], outline=(0, 0, 0, int(90 * (1 - i / 40))))
    return Image.alpha_composite(bg, v)

def portrait_card(bg, who, logo, cx):
    p = Image.open(GUI + f'hud/portrait_{who}_normal.png').convert('RGBA').resize((200, 200), Image.LANCZOS)
    if logo:
        lg = Image.open(GUI + logo + '.png').convert('RGBA'); lg.thumbnail((150, 150))
        a = lg.split()[3].point(lambda v: int(v * 0.35)); lg.putalpha(a)
        bg.alpha_composite(lg, (int(cx + 70), int(H / 2 - lg.height / 2)))
    bg.alpha_composite(p, (int(cx - 100), int(H / 2 - 100)))

def main(art):
    os.makedirs(OUT, exist_ok=True)
    rh = Image.open(os.path.join(art, 'rhalix.png')); mk = Image.open(os.path.join(art, 'mikiko.png'))
    b = grad((10, 12, 30), (70, 40, 110)); place(b, fit(rh, 248), 330, 256); vignette(b).save(OUT + 'rhalix.png')
    b = grad((70, 24, 8), (232, 128, 26)); place(b, fit(mk, 250), 330, 256); vignette(b).save(OUT + 'mikiko.png')
    b = grad((40, 10, 12), (150, 40, 40)); portrait_card(b, 'senju', 'logo_sky_rebellion', 300); vignette(b).save(OUT + 'sr.png')
    b = grad((20, 14, 50), (106, 58, 200)); portrait_card(b, 'kyle', 'logo_government', 300); vignette(b).save(OUT + 'gov.png')
    b = grad((60, 10, 40), (31, 140, 90))                                     # the finale: the two of them, Ultima's green behind
    place(b, fit(rh, 236), 410, 256); place(b, fit(mk, 222), 175, 252); vignette(b).save(OUT + 'finale.png')
    print('banners ->', OUT)

if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else '.')
