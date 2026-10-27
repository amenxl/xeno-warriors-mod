"""Ultima's drawn faces, round 2 - matched to the user's reference sheet: deep shadow from the hair over the top of the
face, dark teal hair locks hanging over it, sharp angular anime eyes with glowing green irises, the glowing lightning
crack over his right eye, warm skin with a soft gradient, shonen mouths (sharp grin, shout, gritted teeth)."""
import math, random
from PIL import Image, ImageDraw, ImageFilter, ImageChops

SS = 4
N = 128
INK = (20, 12, 18)
HAIR = [(10, 26, 34), (16, 40, 50), (28, 66, 78)]


def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


class F:
    def __init__(self, skin=(232, 170, 112), iris=(60, 242, 170)):
        self.S, self.I = skin, iris
        self.im = Image.new('RGBA', (N * SS, N * SS), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    def P(self, pts): return [(x * SS, y * SS) for x, y in pts]

    def poly(self, pts, fill, im=None):
        ImageDraw.Draw(im or self.im).polygon(self.P(pts), fill=fill)

    def line(self, pts, col=INK, w=1.6, im=None):
        d = ImageDraw.Draw(im or self.im)
        d.line(self.P(pts), fill=col, width=max(1, int(w * SS)), joint='curve')
        for x, y in (pts[0], pts[-1]):
            r = w * SS / 2
            d.ellipse((x * SS - r, y * SS - r, x * SS + r, y * SS + r), fill=col)

    def ell(self, cx, cy, rx, ry, fill, im=None):
        ImageDraw.Draw(im or self.im).ellipse(((cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS), fill=fill)

    def blur_layer(self, layer, r):
        self.im.alpha_composite(layer.filter(ImageFilter.GaussianBlur(r * SS)))

    # ------------------------------------------------------------------ skin + shadow
    def skin(self, shade=1.0):
        S = self.S
        top, bot = lerp(S, (150, 86, 50), 0.35), lerp(S, (255, 220, 170), 0.15)
        for y in range(N * SS):
            t = y / (N * SS)
            self.d.line((0, y, N * SS, y), fill=lerp(top, bot, t) + (255,))
        # deep shadow cast by the hair: dark over the top, ragged lower edge, deeper at the sides
        sh = Image.new('RGBA', self.im.size, (0, 0, 0, 0))
        r = random.Random(11)
        pts = [(0, 0)]
        x = 0
        while x <= 128:
            side = abs(x - 64) / 64
            base = 46 + 26 * side * side
            pts += [(x, (base + r.uniform(-3, 2)) * (0.75 + 0.25 * shade)), (x + 5, (base + 8 + r.uniform(-2, 6)) * (0.75 + 0.25 * shade))]
            x += 10
        pts += [(128, 0)]
        self.poly(pts, (60, 28, 22, int(225 * min(1, shade))), sh)
        self.blur_layer(sh, 1.2)
        # soft jaw shading + under-chin
        jw = Image.new('RGBA', self.im.size, (0, 0, 0, 0))
        self.poly([(0, 92), (14, 104), (26, 128), (0, 128)], (120, 60, 40, 90), jw)
        self.poly([(128, 92), (114, 104), (102, 128), (128, 128)], (120, 60, 40, 90), jw)
        self.blur_layer(jw, 1.5)

    # ------------------------------------------------------------------ hair locks over the face
    def locks(self):
        r = random.Random(3)
        spec = [(-4, 0, 30, 46, 14), (8, 0, 22, 40, 12), (20, 0, 34, 52, 13), (40, 0, 56, 74, 12),      # (x0 top, , x1, bottom y, width)
                (54, 0, 48, 58, 10), (70, 0, 74, 50, 12), (84, 0, 96, 44, 12), (100, 0, 112, 54, 13), (116, 0, 132, 66, 14),
                (-8, 20, -2, 96, 12), (136, 20, 130, 96, 12)]
        for x0, _, x1, by, w in spec:
            col = HAIR[r.randrange(2)]
            tip = (x1, by)
            pts = [(x0 - w / 2, -2), (x0 + w / 2, -2), (x1 + w * 0.18, by - 10), tip, (x1 - w * 0.25, by - 12)]
            self.poly(pts, col + (255,))
            self.line([(x0 + w * 0.1, 2), (x1 + w * 0.05, by - 12)], HAIR[2], 0.8)              # a sheen line
            self.line([(x0 - w / 2, -2), (x1 - w * 0.25, by - 12), tip], INK, 0.9)             # ink edge
            self.line([tip, (x1 + w * 0.18, by - 10)], INK, 0.9)

    # ------------------------------------------------------------------ eyes
    def eye(self, cx, cy, side, kind='sharp', look=0.0, glow=True, scar_eye=False):
        """side -1 = his right (viewer left). kinds: sharp, wide, calm, glare, bored, closed, wink_closed."""
        o = side
        I = self.I
        if kind in ('closed', 'wink_closed'):
            self.line([(cx - o * 17, cy + 3), (cx - o * 4, cy - 4), (cx + o * 10, cy - 4), (cx + o * 19, cy + 2)], INK, 3.2)
            self.line([(cx + o * 19, cy + 2), (cx + o * 24, cy - 2)], INK, 2.0)
            return
        h_top = {'sharp': 10, 'wide': 14.5, 'calm': 8, 'glare': 7.5, 'bored': 5}[kind]
        h_bot = {'sharp': 7, 'wide': 10.5, 'calm': 6.5, 'glare': 6, 'bored': 6}[kind]
        tilt = {'sharp': 4.5, 'wide': 2, 'calm': 2.5, 'glare': 7, 'bored': 0.8}[kind]       # outer corner higher -> fierce
        inner, outer = (cx - o * 17, cy + 2), (cx + o * 20, cy - tilt + 1.5)
        top_mid = (cx + o * 1, cy - h_top)
        bot_mid = (cx + o * 0, cy + h_bot)
        shape = [inner, (cx - o * 9.5, cy - h_top * 0.8), top_mid, (cx + o * 12, cy - h_top * 0.85 - tilt * 0.4), outer,
                 (cx + o * 12, cy + h_bot * 0.75), bot_mid, (cx - o * 9.5, cy + h_bot * 0.6)]
        mask = Image.new('L', self.im.size, 0); ImageDraw.Draw(mask).polygon(self.P(shape), fill=255)
        lay = Image.new('RGBA', self.im.size, (0, 0, 0, 0)); ld = ImageDraw.Draw(lay)
        ld.rectangle((0, 0, *self.im.size), fill=(238, 240, 236, 255))
        self.poly([(cx - 20, cy - 20), (cx + 20, cy - 20), (cx + 20, cy - h_top + 3.5), (cx - 20, cy - h_top + 3.5)], (170, 176, 186, 255), lay)   # lid shadow
        ix, iy, ir = cx + look * 6 + o * 1.5, cy + 0.8, (10.2 if kind != 'wide' else 8.8)
        for k in range(20):                                          # iris: dark green top -> bright, glowing bottom
            t = k / 19
            c = lerp(lerp(I, (0, 40, 30), 0.65), lerp(I, (220, 255, 230), 0.35), t)
            band = Image.new('L', self.im.size, 0); bd = ImageDraw.Draw(band)
            bd.ellipse(((ix - ir) * SS, (iy - ir) * SS, (ix + ir) * SS, (iy + ir) * SS), fill=255)
            bd.rectangle((0, 0, self.im.width, (iy - ir + 2 * ir * t) * SS), fill=0)
            lay.paste(c + (255,), (0, 0), band)
        ld.ellipse(((ix - ir) * SS, (iy - ir) * SS, (ix + ir) * SS, (iy + ir) * SS), outline=(6, 40, 30, 255), width=int(1.1 * SS))
        ld.ellipse(((ix - ir * 0.62) * SS, (iy - ir * 0.62) * SS, (ix + ir * 0.62) * SS, (iy + ir * 0.62) * SS), outline=lerp(I, (255, 255, 255), 0.5) + (180,), width=int(0.6 * SS))
        ld.ellipse(((ix - 2.2) * SS, (iy - 5.2) * SS, (ix + 2.2) * SS, (iy + 5.2) * SS), fill=(4, 24, 18, 255))       # slit pupil
        ld.ellipse(((ix - 6.4) * SS, (iy - 7.0) * SS, (ix - 2.0) * SS, (iy - 2.6) * SS), fill=(255, 255, 255, 255))   # highlights
        ld.ellipse(((ix + 3.0) * SS, (iy + 2.8) * SS, (ix + 5.2) * SS, (iy + 5.0) * SS), fill=(255, 255, 255, 220))
        self.im.paste(lay, (0, 0), mask)
        if glow:                                                     # the eyes glow a little
            g = Image.new('RGBA', self.im.size, (0, 0, 0, 0))
            self.ell(ix, iy, ir * (1.05 if scar_eye else 0.9), ir * (1.05 if scar_eye else 0.9), I + (90 if scar_eye else 40,), g)
            self.blur_layer(g, 3 if scar_eye else 2)
        # ink: heavy upper lid that overshoots the outer corner, thin lower lid
        self.line([inner, (cx - o * 9.5, cy - h_top * 0.8), top_mid, (cx + o * 12, cy - h_top * 0.85 - tilt * 0.4), outer], INK, 3.8)
        self.poly([outer, (outer[0] + o * 7, outer[1] - 4.5), (outer[0] + o * 1, outer[1] + 2.8)], INK + (255,))
        self.line([(cx + o * 15, cy + h_bot * 0.6), bot_mid, (cx - o * 8, cy + h_bot * 0.55)], INK, 1.4)
        self.line([inner, (inner[0] + o * 2.5, inner[1] + 1.5)], INK, 1.4)

    def brow(self, cx, cy, side, kind='angry'):
        o = side
        shape = {'angry': [(-12, 6), (2, 0.5), (18, -5)], 'flat': [(-12, 2), (2, 0), (18, -1)], 'raised': [(-12, -1), (2, -5), (18, -3)],
                 'furious': [(-12, 9), (2, 2), (18, -6)], 'soft': [(-12, -2), (2, -1), (18, 2)]}[kind]
        pts = []
        for i in range(len(shape) - 1):
            (x0, y0), (x1, y1) = shape[i], shape[i + 1]
            for k in range(6): t = k / 6; pts.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t))
        pts.append(shape[-1])
        for i in range(len(pts) - 1):
            w = 3.8 - 2.4 * i / (len(pts) - 1)
            self.line([(cx + o * pts[i][0], cy + pts[i][1]), (cx + o * pts[i + 1][0], cy + pts[i + 1][1])], INK, w)

    def nose(self):
        self.line([(66, 84), (64, 90), (61, 91)], (130, 70, 50), 1.1)
        sh = Image.new('RGBA', self.im.size, (0, 0, 0, 0)); self.poly([(64, 80), (66, 90), (62, 91)], (150, 80, 56, 110), sh); self.blur_layer(sh, 0.8)

    def mouth(self, kind):
        lip = (150, 70, 60)
        if kind == 'line':  self.line([(56, 106), (64, 105.4), (72, 106)], INK, 1.5)
        elif kind == 'frown': self.line([(56, 107), (64, 105), (72, 107)], INK, 1.5)
        elif kind == 'small': self.line([(59, 106), (69, 106)], INK, 1.3)
        elif kind == 'smug_grin':                       # wide sharp grin, one side higher
            pts = [(46, 99), (64, 102), (82, 96), (78, 104), (64, 110), (50, 105)]
            self.poly(pts, (250, 248, 246, 255))
            for x in range(50, 80, 4): self.line([(x, 101.5 - (x - 46) * 0.05), (x + 2, 104 - (x - 46) * 0.02)], (150, 150, 160), 0.6)
            self.line([(46, 99), (64, 102), (82, 96)], INK, 1.8)
            self.line([(50, 105), (64, 110), (78, 104)], INK, 1.4)
            self.line([(46, 99), (44, 97)], INK, 1.2); self.line([(82, 96), (85, 93.5)], INK, 1.2)
            self.line([(58, 114), (70, 114)], lip, 0.8)
        elif kind == 'grin':
            pts = [(50, 100), (78, 100), (74, 107), (64, 110), (54, 107)]
            self.poly(pts, (250, 248, 246, 255)); self.line(pts + [pts[0]], INK, 1.4)
            self.line([(51, 103.5), (77, 103.5)], (160, 160, 170), 0.7)
        elif kind == 'shout':
            pts = [(52, 97), (76, 97), (73, 111), (64, 115), (55, 111)]
            self.poly(pts, (110, 26, 34, 255))
            self.poly([(53, 97.5), (75, 97.5), (74, 101), (54, 101)], (250, 248, 246, 255))
            self.poly([(56, 112), (72, 112), (64, 115)], (250, 248, 246, 255))
            self.ell(64, 108, 6, 2.6, (210, 90, 96, 255))
            self.line(pts + [pts[0]], INK, 1.6)
        elif kind == 'o':
            self.ell(64, 106, 3.6, 4.4, INK + (255,)); self.ell(64, 106.8, 2.5, 3.0, (120, 34, 44, 255))
        elif kind == 'grit':
            pts = [(50, 101), (78, 101), (76, 109), (52, 109)]
            self.poly(pts, (250, 248, 246, 255)); self.line(pts + [pts[0]], INK, 1.5)
            for x in range(54, 76, 4): self.line([(x, 101.5), (x, 108.5)], (150, 150, 160), 0.8)
            self.line([(51, 105), (77, 105)], (150, 150, 160), 0.8)
        elif kind == 'open_happy':
            pts = [(52, 100), (76, 100), (72, 109), (64, 112), (56, 109)]
            self.poly(pts, (110, 26, 34, 255)); self.poly([(53, 100.5), (75, 100.5), (74, 103), (54, 103)], (250, 248, 246, 255))
            self.ell(64, 109, 5, 2.4, (210, 90, 96, 255)); self.line(pts + [pts[0]], INK, 1.5)

    def scar(self, col=(70, 255, 170)):
        """The glowing lightning crack: from the hair down across his right eye (viewer left)."""
        pts = [(2, 20), (12, 32), (9, 39), (21, 48), (18, 55), (30, 60), (35, 66)]
        g = Image.new('RGBA', self.im.size, (0, 0, 0, 0))
        self.line(pts, col + (220,), 6.5, g)
        self.blur_layer(g, 3.2)
        self.line(pts, col, 2.2)
        self.line(pts, (225, 255, 240), 0.9)
        for a, b in ((2, (26, 46)), (4, (24, 60))):                       # little branches
            self.line([pts[a], b], col, 1.0)

    def blush(self):
        b = Image.new('RGBA', self.im.size, (0, 0, 0, 0))
        for cx in (30, 98):
            self.ell(cx, 86, 10, 4.5, (240, 110, 120, 120), b)
            for k in range(3): self.line([(cx - 5 + k * 4, 84), (cx - 7 + k * 4, 88)], (220, 90, 100, 160), 0.7, b)
        self.blur_layer(b, 0.8)

    def texture(self):
        small = self.im.resize((N, N), Image.LANCZOS)
        out = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); out.paste(small, (0, 0))
        return out


EXPRESSIONS = {
    #             eyes(left/right)          brows       mouth         extras
    'smug':      (('sharp', 'sharp'),      'angry',    'smug_grin',  {}),
    'serious':   (('glare', 'glare'),      'angry',    'line',       {}),
    'shout':     (('sharp', 'sharp'),      'furious',  'shout',      {}),
    'wink':      (('sharp', 'wink_closed'), 'flat',    'smug_grin',  {}),
    'surprised': (('wide', 'wide'),        'raised',   'o',          {}),
    'glare':     (('glare', 'glare'),      'furious',  'frown',      {'look': -0.4}),
    'calm':      (('calm', 'calm'),        'flat',     'line',       {}),
    'gritted':   (('glare', 'glare'),      'furious',  'grit',       {}),
    'bored':     (('bored', 'bored'),      'flat',     'small',      {}),
    'happy':     (('closed', 'closed'),    'soft',     'open_happy', {'blush': True}),
}


def build(expr='smug', scar=True, iris=(60, 242, 170), skin=(232, 170, 112)):
    eyes, brows, mouth, ex = EXPRESSIONS[expr]
    f = F(skin, iris)
    f.skin(1.0)
    for i, (side, cx) in enumerate(((-1, 35), (1, 93))):
        f.brow(cx, 46, side, brows)
        f.eye(cx, 66, side, eyes[i], ex.get('look', 0.0), True, scar and side == -1)
    f.nose(); f.mouth(mouth)
    if ex.get('blush'): f.blush()
    f.locks()
    if scar: f.scar()
    return f.texture()
