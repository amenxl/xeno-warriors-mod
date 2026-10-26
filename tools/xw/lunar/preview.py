import sys, json, math
sys.path.insert(0, '/home/claude/xw/ultima'); sys.path.insert(0, '/home/claude/xw/outfits'); sys.path.insert(0, '/home/claude/xw/npcs')
import ultima as U
from PIL import Image, ImageDraw, ImageFont
A = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'; CH = A + 'textures/entity/character/'
mj = json.load(open(A + 'models/detail/rhalix.json'))
skin = Image.open(CH + 'rhalix.png').convert('RGBA')
F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 18)
def P_CH_SKIN(lunar): return CH + ('rhalix_lunar.png' if lunar else 'rhalix.png')
def backdrop(w, h):
    im = Image.new('RGB', (w, h)); d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / h; d.line([(0, y), (w, y)], fill=(int(30 + 70 * t), int(8 + 10 * t), int(30 + 20 * t)))
    return im
def crescent(d, cx, cy, R):
    d.ellipse([cx - R, cy - R, cx + R, cy + R], fill=(14, 10, 18), outline=(200, 120, 220), width=3)
    d.ellipse([cx - R * .55, cy - R * 1.05, cx + R * 1.35, cy + R * .85], fill=None)
def star(d, cx, cy, r, col=(232, 222, 236)):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5; rr = r if i % 2 == 0 else r * .45
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    d.polygon(pts, fill=col, outline=(90, 60, 100))
def shot(lunar, yaw, expr='', pose=None, scale=8):
    det = Image.open(CH + ('rhalix_lunar_details.png' if lunar else 'rhalix_details.png')).convert('RGBA')
    fc = Image.open(CH + (('rhalix_lunar_face' if lunar else 'rhalix_face') + expr + '.png')).convert('RGBA')
    sk = Image.open(P_CH_SKIN(lunar)).convert('RGBA')
    im = U.preview(mj, sk, det, fc, yaws=(yaw,), scale=scale, pose=pose).convert('RGBA')
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            if px[x, y][:3] == (40, 44, 52): px[x, y] = (0, 0, 0, 0)
    bg = backdrop(im.width, im.height).convert('RGBA')
    if lunar:
        d = ImageDraw.Draw(bg); W, H = im.size
        cx, cy, R = W * .5, H * .36, W * .42                 # black crescent moon behind him
        mask = Image.new('L', bg.size, 0); md = ImageDraw.Draw(mask)
        md.ellipse([cx - R, cy - R, cx + R, cy + R], fill=255); md.ellipse([cx - R * .35, cy - R * 1.1, cx + R * 1.45, cy + R * .9], fill=0)
        glow = Image.new('RGBA', bg.size, (255, 90, 160, 0)); gd = ImageDraw.Draw(glow)
        moon = Image.new('RGBA', bg.size, (16, 10, 20, 255))
        edge = mask.filter(__import__('PIL.ImageFilter', fromlist=['x']).MaxFilter(7))
        bg.paste(Image.new('RGBA', bg.size, (210, 80, 170, 255)), (0, 0), edge); bg.paste(moon, (0, 0), mask)
        for (sx, sy, sr) in [(.82, .18, 24), (.86, .5, 14), (.12, .62, 16), (.2, .2, 10)]: star(d, W * sx, H * sy, sr)
    bg.alpha_composite(im)
    return bg.convert('RGB')
if __name__ == '__main__':
    tiles = [(shot(False, 25), 'Normal'), (shot(True, 25), 'Lunar Form'), (shot(True, 0, '_fierce'), 'Lunar (front)'), (shot(True, 180), 'Lunar (back - lavender tips)')]
    w, h = tiles[0][0].size
    sheet = Image.new('RGB', (w * 4, h), (0, 0, 0))
    for i, (t, lab) in enumerate(tiles):
        ImageDraw.Draw(t).text((8, 8), lab, font=F, fill=(255, 255, 255)); sheet.paste(t, (i * w, 0))
    sheet.save('/mnt/user-data/outputs/lunar_rhalix.png'); print(sheet.size)
    big = Image.open(CH + 'rhalix_lunar_face.png').crop((0, 0, 32, 32)).resize((256, 256), 0); big.save('/tmp/lf.png')
