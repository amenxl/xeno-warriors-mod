"""Team logos for the teams that didn't have one yet (256x128, like the others in textures/gui/logo_*.png).
Run: python3 tools/xw/logos/team_logos.py
"""
import math, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), '../../../src/main/resources/assets/xenowarriors/textures/gui')
S = 4
W, H = 256 * S, 128 * S
FONT = '/usr/share/fonts/truetype/google-fonts/Poppins-BoldItalic.ttf'
INK = (14, 10, 20, 255)


def new():
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def P(pts): return [(x * S, y * S) for x, y in pts]


def outlined_poly(d, pts, fill, w=3):
    d.polygon(P(pts), fill=fill)
    d.line(P(pts + [pts[0]]), fill=INK, width=w * S, joint='curve')


def word(im, text, cx, cy, size, fill_top, fill_bot, stroke=4):
    """A chunky wordmark with a dark outline and a top-to-bottom colour fade."""
    f = ImageFont.truetype(FONT, size * S)
    d = ImageDraw.Draw(im)
    l, t, r, b = d.textbbox((0, 0), text, font=f, stroke_width=stroke * S)
    x, y = cx * S - (r - l) / 2 - l, cy * S - (b - t) / 2 - t
    d.text((x, y), text, font=f, fill=INK, stroke_width=stroke * S, stroke_fill=INK)
    mask = Image.new('L', im.size, 0)
    ImageDraw.Draw(mask).text((x, y), text, font=f, fill=255)
    grad = Image.new('RGBA', im.size)
    gd = ImageDraw.Draw(grad)
    y0, y1 = y + t, y + b
    for yy in range(int(y0), int(y1) + 1):
        k = (yy - y0) / max(1, y1 - y0)
        gd.line([(0, yy), (W, yy)], fill=tuple(int(fill_top[i] + (fill_bot[i] - fill_top[i]) * k) for i in range(3)) + (255,))
    im.paste(grad, (0, 0), mask)


def glow(im, color, radius=10):
    a = im.getchannel('A').filter(ImageFilter.GaussianBlur(radius * S))
    g = Image.new('RGBA', im.size, color + (0,))
    g.putalpha(a.point(lambda v: int(v * 0.55)))
    return Image.alpha_composite(g, im)


def save(im, name):
    im = im.resize((256, 128), Image.LANCZOS)
    im.save(os.path.join(OUT, name + '.png'))


# ---------------------------------------------------------------- Sky Rebellion: a torn wing behind crossed swords
def sky_rebellion():
    im, d = new()
    # torn wing (left), feathers with ragged ends
    for k in range(6):
        a = math.radians(200 + k * 14)
        L = 96 - k * 6
        base = (128, 60)
        tip = (base[0] + math.cos(a) * L, base[1] + math.sin(a) * L * 0.55)
        nx, ny = -math.sin(a) * 7, math.cos(a) * 7
        pts = [(base[0] + nx * 0.4, base[1] + ny * 0.4), (tip[0] + nx * 0.5, tip[1] + ny * 0.5), (tip[0] - 4, tip[1] + 6 - (k % 2) * 8),
               (tip[0] - nx * 0.5, tip[1] - ny * 0.5), (base[0] - nx * 0.4, base[1] - ny * 0.4)]
        outlined_poly(d, pts, (200 - k * 12, 200 - k * 12, 215 - k * 10, 255), 2)
    # mirrored wing, darker and cut short (torn)
    for k in range(6):
        a = math.radians(-20 - k * 14)
        L = 72 - k * 5
        base = (128, 60)
        tip = (base[0] + math.cos(a) * L, base[1] + math.sin(a) * L * 0.55)
        nx, ny = -math.sin(a) * 7, math.cos(a) * 7
        pts = [(base[0] + nx * 0.4, base[1] + ny * 0.4), (tip[0] + nx * 0.5, tip[1] + ny * 0.5), (tip[0] + 5, tip[1] + 3),
               (tip[0] - nx * 0.5, tip[1] - ny * 0.5), (base[0] - nx * 0.4, base[1] - ny * 0.4)]
        outlined_poly(d, pts, (120 - k * 8, 30, 36, 255), 2)
    # crossed swords
    for sgn in (-1, 1):
        a = math.radians(90 + sgn * 38)
        c = (128, 58)
        L = 64
        p0 = (c[0] - math.cos(a) * L * 0.55, c[1] + math.sin(a) * L * 0.55)
        p1 = (c[0] + math.cos(a) * L * 0.6, c[1] - math.sin(a) * L * 0.6)
        d.line(P([p0, p1]), fill=INK, width=9 * S)
        d.line(P([p0, p1]), fill=(225, 228, 238, 255), width=5 * S)
        gx, gy = math.sin(a) * 8, math.cos(a) * 8
        q = (p0[0] + (p1[0] - p0[0]) * 0.18, p0[1] + (p1[1] - p0[1]) * 0.18)
        d.line(P([(q[0] - gx, q[1] - gy), (q[0] + gx, q[1] + gy)]), fill=INK, width=7 * S)
        d.line(P([(q[0] - gx, q[1] - gy), (q[0] + gx, q[1] + gy)]), fill=(200, 150, 50, 255), width=4 * S)
    word(im, 'SKY REBELLION', 128, 104, 22, (255, 120, 110), (160, 30, 36))
    save(glow(im, (180, 30, 40)), 'logo_sky_rebellion')


# ---------------------------------------------------------------- Disco Dojo: a disco ball over a dojo roof
def disco_dojo():
    im, d = new()
    # dojo roof (curved eaves)
    roof = [(70, 70), (128, 46), (186, 70), (176, 72), (128, 56), (80, 72)]
    outlined_poly(d, [(60, 74), (80, 66), (128, 44), (176, 66), (196, 74), (176, 72), (128, 56), (80, 72)], (60, 40, 110, 255), 3)
    d.rectangle(P([(92, 72), (164, 84)]), fill=(140, 40, 50, 255), outline=INK, width=2 * S)
    for x in range(98, 162, 12): d.rectangle(P([(x, 74), (x + 6, 84)]), fill=(250, 230, 200, 255))
    # disco ball
    cx, cy, r = 128, 28, 20
    d.ellipse(P([(cx - r - 2, cy - r - 2), (cx + r + 2, cy + r + 2)]), fill=INK)
    for yy in range(-r, r, 5):
        for xx in range(-r, r, 5):
            if xx * xx + yy * yy < r * r:
                shade = 150 + int(80 * ((xx + yy + 2 * r) % 15) / 15)
                tint = (shade, int(shade * 0.85), 255) if (xx // 5 + yy // 5) % 3 else (255, 255, 255)
                d.rectangle(P([(cx + xx, cy + yy), (cx + xx + 4, cy + yy + 4)]), fill=tint + (255,))
    d.line(P([(cx, 0), (cx, cy - r)]), fill=(200, 200, 210, 255), width=2 * S)
    for a in range(0, 360, 30):   # sparkles
        rr = math.radians(a)
        x, y = cx + math.cos(rr) * 34, cy + math.sin(rr) * 26
        if y < 44: d.polygon(P([(x, y - 3), (x + 1, y - 1), (x + 3, y), (x + 1, y + 1), (x, y + 3), (x - 1, y + 1), (x - 3, y), (x - 1, y - 1)]), fill=(255, 240, 160, 255))
    word(im, 'DISCO DOJO', 128, 105, 24, (240, 170, 255), (130, 60, 220))
    save(glow(im, (160, 90, 230)), 'logo_disco_dojo')


# ---------------------------------------------------------------- Ai-oni's Demons: an oni mask with horns and demon wings
def demons():
    im, d = new()
    for sgn in (-1, 1):   # bat wings
        pts = [(128 + sgn * 18, 40), (128 + sgn * 70, 14), (128 + sgn * 92, 36), (128 + sgn * 78, 44), (128 + sgn * 70, 62), (128 + sgn * 56, 52),
               (128 + sgn * 46, 70), (128 + sgn * 34, 56), (128 + sgn * 20, 66)]
        outlined_poly(d, pts, (90, 14, 30, 255), 3)
    # mask
    face = [(104, 26), (152, 26), (160, 48), (150, 76), (128, 86), (106, 76), (96, 48)]
    outlined_poly(d, face, (196, 30, 48, 255), 3)
    for sgn in (-1, 1):   # horns
        outlined_poly(d, [(128 + sgn * 14, 28), (128 + sgn * 30, 2), (128 + sgn * 24, 28)], (240, 225, 200, 255), 2)
        outlined_poly(d, [(128 + sgn * 6, 46), (128 + sgn * 20, 40), (128 + sgn * 18, 50)], (255, 230, 90, 255), 2)   # eyes
        d.line(P([(128 + sgn * 4, 38), (128 + sgn * 22, 34)]), fill=INK, width=3 * S)               # brows
    d.polygon(P([(114, 64), (142, 64), (138, 72), (118, 72)]), fill=INK)
    for x in (118, 136): d.polygon(P([(x, 64), (x + 4, 64), (x + 2, 70)]), fill=(250, 250, 250, 255))   # fangs
    word(im, "AI-ONI'S DEMONS", 128, 106, 20, (255, 110, 110), (150, 10, 30))
    save(glow(im, (200, 20, 40)), 'logo_demons')


# ---------------------------------------------------------------- Wanderers: a compass rose on a winding road
def wanderers():
    im, d = new()
    d.line(P([(50, 92), (90, 70), (120, 80), (160, 60), (206, 70)]), fill=(140, 110, 70, 255), width=8 * S, joint='curve')
    cx, cy = 128, 40
    d.ellipse(P([(cx - 30, cy - 30), (cx + 30, cy + 30)]), fill=(30, 40, 70, 255), outline=INK, width=3 * S)
    d.ellipse(P([(cx - 24, cy - 24), (cx + 24, cy + 24)]), outline=(150, 180, 240, 255), width=2 * S)
    for a, L, col in ((0, 26, (240, 80, 70)), (180, 26, (230, 230, 240)), (90, 18, (180, 200, 240)), (270, 18, (180, 200, 240))):
        rr = math.radians(a - 90)
        tip = (cx + math.cos(rr) * L, cy + math.sin(rr) * L)
        side = (math.cos(rr + math.pi / 2) * 5, math.sin(rr + math.pi / 2) * 5)
        outlined_poly(d, [tip, (cx + side[0], cy + side[1]), (cx - side[0], cy - side[1])], col + (255,), 1)
    word(im, 'WANDERERS', 128, 104, 24, (180, 210, 255), (80, 110, 210))
    save(glow(im, (100, 140, 220)), 'logo_wanderers')


if __name__ == '__main__':
    sky_rebellion(); disco_dojo(); demons(); wanderers()
    print('ok')
