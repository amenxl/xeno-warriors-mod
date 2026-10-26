"""Distortion (Season 3): a swirling black/purple tear with red sparks; C-souls drift out of it."""
import math, random
from PIL import Image, ImageDraw
W, H = 420, 420; rnd = random.Random(2)
frames = []
souls = []
for f in range(48):
    im = Image.new('RGB', (W, H)); d = ImageDraw.Draw(im, 'RGBA')
    for y in range(H):
        t = y / H; d.line([(0, y), (W, y)], fill=(int(22 + 30 * t), int(26 + 40 * t), int(40 + 30 * t)))
    d.rectangle([0, 330, W, H], fill=(70, 96, 52))
    cx, cy = 210, 200
    t = f * 0.25
    for i in range(260):                                     # the tear
        a = t + i * 0.37; rr = 30 + (i % 40) * 1.6
        x = cx + math.cos(a) * rr * 0.45; y = cy + math.sin(a) * rr * 1.5
        c = (120, 30, 180, 200) if i % 3 == 0 else (10, 5, 16, 230)
        s = 3 if i % 3 else 2
        d.ellipse([x - s, y - s, x + s, y + s], fill=c)
    d.ellipse([cx - 14, cy - 60, cx + 14, cy + 60], fill=(4, 2, 8, 255))
    for k in range(8):                                       # red sparks
        a = rnd.random() * 6.28; rr = rnd.uniform(20, 70)
        d.point((cx + math.cos(a) * rr * .5, cy + math.sin(a) * rr * 1.4), fill=(220, 40, 60))
    if f % 8 == 0: souls.append([cx, cy, rnd.uniform(-2.5, 2.5), rnd.uniform(-1.2, 0.2), 0])
    for s in souls:                                          # C-souls: glowing wisps with dark trails
        s[0] += s[2]; s[1] += s[3] + math.sin(s[4] * .3) * .6; s[4] += 1
        for tr in range(5): d.ellipse([s[0] - s[2] * tr * 2 - 5 + tr, s[1] - s[3] * tr * 2 - 5 + tr, s[0] - s[2] * tr * 2 + 5 - tr, s[1] - s[3] * tr * 2 + 5 - tr], fill=(20, 10, 30, 160 - tr * 30))
        d.ellipse([s[0] - 7, s[1] - 7, s[0] + 7, s[1] + 7], fill=(230, 220, 255, 230)); d.ellipse([s[0] - 3, s[1] - 2, s[0] - 1, s[1]], fill=(220, 30, 50)); d.ellipse([s[0] + 1, s[1] - 2, s[0] + 3, s[1]], fill=(220, 30, 50))
    d.text((12, 12), 'DISTORTION (Season 3)', fill=(230, 210, 255))
    frames.append(im.convert('P', palette=Image.ADAPTIVE))
frames[0].save('/mnt/user-data/outputs/distortion.gif', save_all=True, append_images=frames[1:], duration=70, loop=0)
print(len(frames))
