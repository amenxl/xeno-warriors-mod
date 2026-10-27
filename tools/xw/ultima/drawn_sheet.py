import sys; sys.path.insert(0, '.')
import drawn_preview as P, drawn_faces as D, ultima as U
from PIL import Image, ImageDraw, ImageFont
F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 20)
S, E = U.SPEC['skin'], U.SPEC['eyes']
OPTS = [
    ('1. Calm', dict(eyes='open', brows='flat', mouth='line')),
    ('2. Distant (like now)', dict(eyes='narrow', brows='flat', mouth='off', lid=0.25, look=0.55)),
    ('3. Cold glare', dict(eyes='sharp', brows='angry', mouth='frown', shadow=1.6)),
    ('4. Smug', dict(eyes='open', brows='low', mouth='smirk', lid=0.4)),
    ('5. Intense (shadowed)', dict(eyes='sharp', brows='angry', mouth='grin', shadow=2.4, glow=(120, 255, 200))),
    ('6. Lightning scar', dict(eyes='open', brows='flat', mouth='line', scar=True)),
]
EXPR = [('grin', dict(eyes='closed_happy', brows='raised', mouth='grin', blush=True)), ('shout', dict(eyes='sharp', brows='angry', mouth='shout')),
        ('surprised', dict(eyes='wide', brows='raised', mouth='o')), ('wink', dict(eyes='wink', brows='flat', mouth='smirk')),
        ('gritted', dict(eyes='sharp', brows='angry', mouth='grit', shadow=1.8)), ('worried', dict(eyes='open', brows='worried', mouth='frown'))]
def sheet(items, path, cols=3):
    tiles = []
    for name, kw in items:
        im = P.shot(D.build(S, E, **kw), scale=30).convert('RGB')
        d = ImageDraw.Draw(im); d.rectangle((0, 0, im.width, 30), fill=(20, 20, 28)); d.text((10, 4), name, font=F, fill=(255, 255, 255))
        tiles.append(im)
    tw, th = tiles[0].size
    out = Image.new('RGB', (tw * cols, th * ((len(tiles) + cols - 1) // cols)), (30, 30, 36))
    for i, t in enumerate(tiles): out.paste(t, ((i % cols) * tw, (i // cols) * th))
    out.save(path); print(path, out.size)
sheet(OPTS, '/tmp/ultima_drawn_options.png')
sheet(EXPR, '/tmp/ultima_drawn_expressions.png')
