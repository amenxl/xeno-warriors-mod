"""Hand-drawn style faces (like DragonMineZ's art faces): a 256x256 face texture (the 32x32 face area becomes 128x128,
16 pixels per head pixel), drawn with smooth anti-aliased ink lines - big anime eyes with shaded irises and
highlights, tapered brows, the shadow the fringe casts, nose and mouth shading."""
import math, sys
from PIL import Image, ImageDraw, ImageFilter

SS = 4                      # supersampling
N = 128                     # face area size in the final texture

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))
def mul(c, f): return tuple(max(0, min(255, int(v * f))) for v in c[:3])
def hexc(h): h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

INK = (34, 20, 28)

class Face:
    def __init__(self, skin, iris):
        self.skin, self.iris = hexc(skin) if isinstance(skin, str) else skin, hexc(iris) if isinstance(iris, str) else iris
        self.im = Image.new('RGBA', (N * SS, N * SS), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    # coordinates are in 128-space
    def P(self, pts): return [(x * SS, y * SS) for x, y in pts]
    def poly(self, pts, fill=None, outline=None, w=1):
        self.d.polygon(self.P(pts), fill=fill)
        if outline: self.line(pts + [pts[0]], outline, w)
    def line(self, pts, col=INK, w=1.6):
        self.d.line(self.P(pts), fill=col, width=max(1, int(w * SS)), joint='curve')
        for x, y in (pts[0], pts[-1]):
            r = w * SS / 2; self.d.ellipse((x * SS - r, y * SS - r, x * SS + r, y * SS + r), fill=col)
    def ell(self, cx, cy, rx, ry, fill): self.d.ellipse(((cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS), fill=fill)

    def base(self, top=22, shadow=1.0):
        S = self.skin
        self.d.rectangle((0, top * SS, N * SS, N * SS), fill=S + (255,))
        # jaw / side shading (cel, hard-edged)
        sh = mul(S, 0.84)
        self.poly([(0, 96), (10, 104), (20, 128), (0, 128)], sh + (255,))
        self.poly([(128, 96), (118, 104), (108, 128), (128, 128)], sh + (255,))
        # the fringe's shadow: a dark band with jagged strand tips
        if shadow > 0:
            band = (int(S[0] * 0.55), int(S[1] * 0.48), int(S[2] * 0.55 + 22), 255)     # cool shadow under the dark fringe
            pts = [(0, top)]
            import random
            r = random.Random(5)
            x = 0
            while x < 128:
                depth = 14 + 9 * shadow + r.uniform(-3, 8)
                pts += [(x + 4, top + depth * 0.75), (x + 8, top + depth)]
                x += 9 + r.uniform(0, 5)
            pts += [(128, top + 30), (128, top)]
            self.poly(pts, band)
        return self

    def eye(self, cx, cy, side, kind='open', look=0.0, lid=0.0, glow=None):
        """side=-1 his right (viewer left), +1 his left. kind: open, narrow, sharp, wide, closed_happy, wink.
        lid 0..1 lowers the upper lid; look -1..1 moves the iris sideways."""
        S, I = self.skin, self.iris
        o = -side              # outer corner direction in x for this eye (outer = away from nose)
        o = side
        w, h = 20.5, 13 if kind != 'wide' else 16
        if kind == 'narrow': h = 8
        if kind == 'sharp': h = 10
        if kind in ('closed_happy', 'wink'):
            self.line([(cx - w, cy + 2), (cx - w * 0.4, cy - 5), (cx + w * 0.4, cy - 5), (cx + w, cy + 2)], INK, 2.6)
            self.line([(cx + o * w, cy + 2), (cx + o * (w + 4), cy - 1)], INK, 1.8)
            return
        top = cy - h + lid * h * 1.2
        # sclera (almond)
        scl = [(cx - w, cy), (cx - w * 0.55, cy - h), (cx + w * 0.55, cy - h), (cx + w, cy), (cx + w * 0.6, cy + h * 0.8), (cx - w * 0.6, cy + h * 0.8)]
        mask = Image.new('L', self.im.size, 0); md = ImageDraw.Draw(mask); md.polygon(self.P(scl), fill=255)
        md.rectangle((0, 0, self.im.width, top * SS), fill=0)                       # the lid hides the top
        layer = Image.new('RGBA', self.im.size, (0, 0, 0, 0)); ld = ImageDraw.Draw(layer)
        ld.rectangle((0, 0, *self.im.size), fill=(246, 244, 246, 255))
        ld.rectangle((0, (top - 1) * SS, self.im.width, (top + 4) * SS), fill=(196, 200, 214, 255))   # lid shadow on the white
        # iris: dark at the top, bright at the bottom
        ix, iy, ir = cx + look * 6, cy + 1.5, (9.8 if kind != 'wide' else 8.4)
        for k in range(24):
            t = k / 23
            c = lerp(mul(I, 0.35), lerp(I, (255, 255, 255), 0.35), t)
            y0 = iy - ir + 2 * ir * t
            ld.ellipse(((ix - ir) * SS, (iy - ir) * SS, (ix + ir) * SS, (iy + ir) * SS), fill=None)
            ld.chord(((ix - ir) * SS, (iy - ir) * SS, (ix + ir) * SS, (iy + ir) * SS), 0, 360, fill=None)
            # horizontal band of the gradient, clipped to the circle
            band = Image.new('L', self.im.size, 0); bd = ImageDraw.Draw(band)
            bd.ellipse(((ix - ir) * SS, (iy - ir) * SS, (ix + ir) * SS, (iy + ir) * SS), fill=255)
            bd.rectangle((0, 0, self.im.width, y0 * SS), fill=0)
            layer.paste(c + (255,), (0, 0), band)
        ld.ellipse(((ix - ir) * SS, (iy - ir) * SS, (ix + ir) * SS, (iy + ir) * SS), outline=mul(I, 0.25) + (255,), width=int(1.2 * SS))
        ld.ellipse(((ix - 3.2) * SS, (iy - 4.6) * SS, (ix + 3.2) * SS, (iy + 4.6) * SS), fill=mul(I, 0.12) + (255,))      # pupil
        if glow:
            ld.ellipse(((ix - 2) * SS, (iy - 2) * SS, (ix + 2) * SS, (iy + 2) * SS), fill=glow + (255,))
        ld.ellipse(((ix - 6.4) * SS, (iy - 6.6) * SS, (ix - 1.6) * SS, (iy - 1.8) * SS), fill=(255, 255, 255, 255))   # highlights
        ld.ellipse(((ix + 2.6) * SS, (iy + 2.4) * SS, (ix + 5.0) * SS, (iy + 4.8) * SS), fill=(255, 255, 255, 235))
        self.im.paste(layer, (0, 0), mask)
        # upper lash line: a thick lid line over the top of the eye, heavier toward the outer corner, with a flick
        lt = top
        inner, outer = (cx - o * w * 1.02, cy + 0.8), (cx + o * w * 1.05, cy - 0.5)
        lid_pts = [inner, (cx - o * w * 0.6, lt + 0.6), (cx, lt - 0.4), (cx + o * w * 0.6, lt + 0.2), outer]
        self.line(lid_pts[:3], INK, 2.4)
        self.line(lid_pts[2:], INK, 3.6)
        self.poly([(cx + o * w * 0.75, lt - 1.2), (cx + o * (w + 6), lt - 3.8), (cx + o * (w + 2.5), lt + 2.6), (cx + o * w * 0.9, lt + 2.2)], INK + (255,))
        # crease above
        if kind != 'narrow' and lid < 0.5:
            self.line([(cx - o * w * 0.5, lt - 4.2), (cx + o * w * 0.75, lt - 4.4), (cx + o * w, lt - 2.8)], mul(S, 0.6), 1.0)
        # lower lash (outer half) and a little shadow under the eye
        self.line([(cx + o * w * 0.85, cy + 2), (cx + o * w * 0.2, cy + h * 0.85)], INK, 1.2)
        self.line([(cx - o * w * 0.6, cy + h * 0.95), (cx + o * w * 0.1, cy + h * 1.05)], mul(S, 0.7), 0.9)

    def brow(self, cx, cy, side, kind='flat'):
        """A tapered brow: thick at the inner end (by the nose), thin at the outer end."""
        o = side
        shape = {'flat': [(-14, 1.2), (0, -0.6), (16, 0.4)], 'angry': [(-14, 7.0), (0, 1.0), (16, -4.0)],
                 'raised': [(-14, -0.5), (0, -4.2), (16, -1.0)], 'worried': [(-14, -3.2), (0, -0.8), (16, 2.6)],
                 'low': [(-14, 2.2), (0, 1.4), (16, 1.6)]}[kind]
        pts = []
        for i in range(len(shape) - 1):
            (x0, y0), (x1, y1) = shape[i], shape[i + 1]
            for k in range(6):
                t = k / 6
                pts.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t))
        pts.append(shape[-1])
        n = len(pts)
        for i in range(n - 1):
            wdt = 3.6 - 2.2 * (i / (n - 1))
            (x0, y0), (x1, y1) = pts[i], pts[i + 1]
            self.line([(cx - o * x0 * -1 if False else cx + o * -x0 * -1, cy + y0), (cx + o * x1, cy + y1)] if False else
                      [(cx + o * x0, cy + y0), (cx + o * x1, cy + y1)], INK, wdt)

    def nose(self):
        S = self.skin
        self.poly([(63, 80), (66, 90), (61, 91)], mul(S, 0.8) + (255,))
        self.line([(65, 88), (61.5, 90.5)], mul(S, 0.48), 1.2)

    def mouth(self, kind='line'):
        S = self.skin; m = mul(S, 0.45)
        if kind == 'line':  self.line([(57, 104), (64, 103.4), (71, 104)], INK, 1.5)
        elif kind == 'off': self.line([(60, 104.5), (67, 104), (73, 103)], INK, 1.5)
        elif kind == 'frown': self.line([(57, 105.5), (64, 103.6), (71, 105.5)], INK, 1.5)
        elif kind == 'smirk':
            self.line([(56, 104.5), (64, 104), (71, 102.5), (74, 100.5)], INK, 1.5)
            self.line([(72, 105), (74, 103.5)], mul(S, 0.55), 1.0)
        elif kind == 'grin':
            self.poly([(52, 100), (76, 100), (72, 107), (64, 109), (56, 107)], (250, 248, 248, 255), INK, 1.4)
            for x in (58, 64, 70): self.line([(x, 100.5), (x, 104.5)], (190, 190, 200), 0.7)
            self.line([(53, 103.5), (75, 103.5)], (190, 190, 200), 0.6)
        elif kind == 'shout':
            self.poly([(55, 98), (73, 98), (71, 110), (64, 113), (57, 110)], (120, 30, 40, 255), INK, 1.5)
            self.poly([(56, 98.5), (72, 98.5), (71, 101), (57, 101)], (250, 248, 248, 255))
            self.ell(64, 109, 5, 2.4, (214, 90, 100, 255))
        elif kind == 'o':
            self.ell(64, 105, 3.2, 4, INK + (255,)); self.ell(64, 105.6, 2.2, 2.8, (130, 40, 52, 255))
        elif kind == 'grit':
            self.poly([(54, 101), (74, 101), (74, 107), (54, 107)], (250, 248, 248, 255), INK, 1.4)
            for x in (58, 62, 66, 70): self.line([(x, 101.5), (x, 106.5)], (160, 160, 172), 0.8)
            self.line([(55, 104), (73, 104)], (160, 160, 172), 0.8)

    def lightning(self, col=(80, 255, 170)):
        """A glowing green lightning crack across his (left) eye, from the hairline down."""
        pts = [(92, 18), (99, 34), (90, 44), (100, 58), (93, 66), (101, 80)]
        glow = Image.new('RGBA', self.im.size, (0, 0, 0, 0)); gd = ImageDraw.Draw(glow)
        gd.line(self.P(pts), fill=col + (200,), width=int(6 * SS), joint='curve')
        glow = glow.filter(ImageFilter.GaussianBlur(3 * SS))
        self.im.alpha_composite(glow)
        self.line(pts, col, 2.0)
        self.line(pts, (230, 255, 240), 0.8)

    def blush(self):
        for cx in (30, 98):
            b = Image.new('RGBA', self.im.size, (0, 0, 0, 0)); bd = ImageDraw.Draw(b)
            bd.ellipse(((cx - 9) * SS, 82 * SS, (cx + 9) * SS, 90 * SS), fill=(240, 120, 130, 90))
            self.im.alpha_composite(b.filter(ImageFilter.GaussianBlur(2 * SS)))

    def texture(self):
        """The final 256x256 texture (face area in the top-left 128x128)."""
        small = self.im.resize((N, N), Image.LANCZOS)
        out = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); out.paste(small, (0, 0))
        return out


def build(skin, iris, eyes='open', brows='flat', mouth='line', look=0.0, lid=0.0, shadow=1.0, scar=False, blush=False, glow=None):
    f = Face(skin, iris).base(shadow=shadow)
    for side, cx in ((-1, 36), (1, 92)):
        k = eyes
        if eyes == 'wink' and side == 1: k = 'open'
        f.brow(cx, 46 if brows != 'angry' else 47, side, brows)
        f.eye(cx, 66, side, k, look, lid, glow)
    f.nose(); f.mouth(mouth)
    if blush: f.blush()
    if scar: f.lightning()
    return f.texture()
