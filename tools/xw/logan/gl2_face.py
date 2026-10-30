"""Logan's Gacha Life 2 face (from the user's reference): big dark round eyes with a white shine, heavy upper lashes,
black crescent marks under the outer corners, a wide shark-tooth grin, blonde fringe with a lock falling over his left
eye, and gold hair clips. Drawn at 4x and shrunk into the 128x128 'drawn face' area of a 256x256 texture."""
import sys, os
from PIL import Image, ImageDraw, ImageFilter

SS = 4
N = 128
INK = (22, 14, 18)
SKIN = (226, 176, 150)
HAIR = [(126, 92, 36), (170, 130, 52), (206, 166, 74), (232, 198, 108)]
GOLD = (236, 196, 64)


def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Face:
    def __init__(self, v):
        self.v = v
        self.im = Image.new('RGBA', (N * SS, N * SS), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    def P(self, pts): return [(x * SS, y * SS) for x, y in pts]
    def poly(self, pts, fill, im=None): ImageDraw.Draw(im or self.im).polygon(self.P(pts), fill=fill)
    def ell(self, cx, cy, rx, ry, fill, im=None, outline=None, w=0):
        ImageDraw.Draw(im or self.im).ellipse(((cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS), fill=fill,
                                              outline=outline, width=int(w * SS))
    def line(self, pts, col=INK, w=1.6, im=None):
        d = ImageDraw.Draw(im or self.im)
        d.line(self.P(pts), fill=col, width=max(1, int(w * SS)), joint='curve')
        for x, y in (pts[0], pts[-1]):
            r = w * SS / 2
            d.ellipse((x * SS - r, y * SS - r, x * SS + r, y * SS + r), fill=col)
    def arc(self, cx, cy, rx, ry, a0, a1, col=INK, w=1.6, im=None):
        ImageDraw.Draw(im or self.im).arc(((cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS), a0, a1, fill=col, width=int(w * SS))
    def soft(self, layer, r): self.im.alpha_composite(layer.filter(ImageFilter.GaussianBlur(r * SS)))

    def skin(self):
        top, bot = lerp(SKIN, (170, 110, 90), 0.35), lerp(SKIN, (250, 214, 190), 0.25)
        for y in range(N * SS):
            self.d.line((0, y, N * SS, y), fill=lerp(top, bot, y / (N * SS)) + (255,))
        sh = Image.new('RGBA', self.im.size, (0, 0, 0, 0))          # the hood and fringe shade the top and the sides
        self.poly([(0, 0), (128, 0), (128, 128), (116, 128), (112, 60), (100, 40), (64, 36), (28, 40), (16, 60), (12, 128), (0, 128)], (70, 30, 34, 90), sh)
        self.soft(sh, 6)
        bl = Image.new('RGBA', self.im.size, (0, 0, 0, 0))          # a touch of blush
        self.ell(30, 86, 9, 4, (232, 120, 120, 70), bl); self.ell(98, 86, 9, 4, (232, 120, 120, 70), bl)
        self.soft(bl, 2)

    def eye(self, cx, cy, right):
        v = self.v
        rx, ry = 13, 12.5
        iris, ring = ((30, 24, 28), (66, 58, 64)) if not v.get('red') else ((140, 14, 18), (200, 40, 40))
        under = Image.new('RGBA', self.im.size, (0, 0, 0, 0))    # tired shadow under the eye
        self.ell(cx, cy + 10, 13, 5, (80, 50, 70, 120), under); self.soft(under, 1.5)
        self.ell(cx, cy, rx, ry, (246, 242, 242, 255))              # white
        self.ell(cx + (1 if right else -1), cy + 1, 11, 11.5, ring + (255,))
        self.ell(cx + (1 if right else -1), cy + 1.5, 9.5, 10, iris + (255,))
        self.ell(cx + (1 if right else -1), cy + 2, 6, 6.5, (14, 8, 12, 255))   # big pupil
        if v.get('red'): self.ell(cx + (1 if right else -1), cy + 2, 3, 3.3, (60, 0, 4, 255))
        # shine
        self.ell(cx - 3.5, cy - 3, 3, 3, (255, 255, 255, 255)); self.ell(cx + 4, cy + 5, 1.5, 1.5, (255, 255, 255, 230))
        # lids: heavy top lash with an outer flick, thin lower lid
        s = 1 if right else -1
        self.arc(cx, cy + 1, rx + 1, ry + 0.5, 190, 350, INK, 3.6)
        self.line([(cx + s * (rx - 0.5), cy - 3), (cx + s * (rx + 2.5), cy - 4)], INK, 2.6)     # one small outer lash
        self.arc(cx, cy, rx + 0.5, ry + 0.5, 20, 160, INK, 1.4)                             # full dark outline below
        self.arc(cx, cy + 2, rx - 2, ry, 40, 140, (110, 80, 96), 1.0)
        # brow (thin, mostly hidden under the fringe)
        self.line([(cx - 9, cy - 19 + (2 if right else 0)), (cx + 9, cy - 19 + (0 if right else 2))], (110, 70, 40), 1.4)

    def crescents(self):
        if self.v.get('no_marks'): return
        self.arc(26, 84, 7, 6, 70, 200, INK, 2.6)       # "(" under his right eye's outer corner
        self.arc(102, 84, 7, 6, -20, 110, INK, 2.6)     # ")" under his left eye's

    def mouth(self):
        v = self.v
        x0, x1, y = (46, 82, 98) if not v.get('small') else (52, 76, 100)
        h = 12 if not v.get('small') else 7
        top = [(x0, y)] + [(x0 + (x1 - x0) * t / 10, y + 2.5 * (1 - (2 * t / 10 - 1) ** 2)) for t in range(1, 10)] + [(x1, y)]
        bot = [(x1, y)] + [(x1 - (x1 - x0) * t / 10, y + h * (1 - (2 * t / 10 - 1) ** 2) ** 0.8) for t in range(1, 10)] + [(x0, y)]
        self.poly(top + bot, (70, 18, 26, 255))
        # shark teeth: upper row pointing down, lower row pointing up
        n = 7 if not v.get('small') else 5
        w = (x1 - x0) / n
        for i in range(n):
            a = x0 + i * w
            yt = y + 2.5 * (1 - (2 * ((a + w / 2 - x0) / (x1 - x0)) - 1) ** 2)
            self.poly([(a, yt - 0.5), (a + w, yt - 0.5), (a + w / 2, yt + h * (0.62 if not v.get('closed') else 0.55))], (250, 250, 248, 255))
        for i in range(n - 1):
            a = x0 + w / 2 + i * w
            t = (a + w / 2 - x0) / (x1 - x0)
            yb = y + h * (1 - (2 * t - 1) ** 2) ** 0.8
            self.poly([(a, yb + 0.5), (a + w, yb + 0.5), (a + w / 2, yb - h * (0.55 if not v.get('closed') else 0.6))], (238, 236, 234, 255))
        self.line(top, INK, 1.8); self.line(bot, INK, 1.6)
        self.line([(x0 - 2, y - 1.5), (x0 + 1, y + 1)], INK, 1.4); self.line([(x1 + 2, y - 1.5), (x1 - 1, y + 1)], INK, 1.4)   # dimples

    def nose(self):
        self.line([(65, 84), (63, 87)], (170, 110, 96), 1.1)

    def hair(self):
        v = self.v
        L = Image.new('RGBA', self.im.size, (0, 0, 0, 0))
        # fringe: a few chunky clumps with a dark outline (Gacha Life 2 style), lighter streak down each
        clumps = [(-6, 20, 30), (12, 34, 40), (32, 50, 33), (48, 68, 44), (64, 82, 36), (80, 100, 42), (96, 116, 34), (110, 134, 28)]
        for i, (a, b, tip) in enumerate(clumps):
            m = (a + b) / 2 + (3 if i % 2 else -3)
            pts = [(a - 2, -2), (b + 2, -2), (b, tip * 0.45), (m + 2, tip), (a + 1, tip * 0.5)]
            self.poly(pts, HAIR[2] + (255,), L)
            self.poly([(m - 2, -2), (m + 3, -2), (m + 1, tip * 0.8)], HAIR[3] + (255,), L)
            self.poly([(a - 2, -2), (a + 3, -2), (a + 2, tip * 0.5)], HAIR[1] + (255,), L)
            self.line(pts[1:] , (96, 66, 26), 1.1, L)
        if not v.get('no_lock'):
            # the long lock falling over his left eye (viewer's right), diagonal like the reference
            lock = [(66, 10), (80, 8), (92, 34), (98, 58), (94, 80), (89, 62), (80, 38)]
            self.poly(lock, HAIR[2] + (255,), L)
            self.poly([(72, 10), (80, 8), (90, 36), (94, 58), (86, 38)], HAIR[3] + (255,), L)
            self.line(lock[1:] + [lock[0]], (96, 66, 26), 1.1, L)
        # shade the hair's edge onto the face, then the hair itself
        shade = Image.new('RGBA', self.im.size, (0, 0, 0, 0))
        shade.paste((60, 26, 30, 120), (0, 0), L.split()[3])
        self.im.alpha_composite(shade.filter(ImageFilter.GaussianBlur(2 * SS)), (0, 2 * SS))
        self.im.alpha_composite(L)
        # gold hair clips
        if not v.get('no_clips'):
            for (x, y, a) in ((30, 40, -10), (92, 38, 12)):
                self.line([(x - 7, y + a / 10), (x + 7, y - a / 10)], (120, 90, 20), 3.2)
                self.line([(x - 7, y + a / 10), (x + 7, y - a / 10)], GOLD, 2.0)

    def draw(self):
        self.skin(); self.crescents(); self.nose()
        self.eye(42, 64, False); self.eye(86, 64, True)
        self.mouth(); self.hair()
        small = self.im.resize((N, N), Image.LANCZOS)
        out = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); out.paste(small, (0, 0))
        return out


VARIANTS = {
    'v1': {},                                   # faithful to the Gacha Life 2 look
    'v2': {'red': True},                        # same, with his red eyes from the show's current skin
    'v3': {'no_lock': True},                    # no lock over his eye (both eyes clear)
    'v4': {'small': True, 'closed': True},      # toned-down grin
}

if __name__ == '__main__':
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for k, v in VARIANTS.items():
        Face(v).draw().save(os.path.join(out, 'logan_drawn_face_' + k + '.png'))
    print('ok')
