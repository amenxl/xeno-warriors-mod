"""Skill icons: a little blocky character doing the move, plus the move's effect (fire, beams, gravity, wind...).
32x32 pixel art, one per skill id -> assets/xenowarriors/textures/gui/skill/<id>.png.
Run: python3 tools/xw/skillicons/icons.py   (re-run after adding a skill; add it to SPECS below)
"""
import math, os
from PIL import Image, ImageDraw

N = 32
OUT = os.path.join(os.path.dirname(__file__), '../../../src/main/resources/assets/xenowarriors/textures/gui/skill')

CH = {  # owner colours (CharacterType)
    'mikiko': (232, 128, 26), 'rhalix': (160, 44, 210), 'ultima': (31, 224, 122), 'kaeru': (232, 112, 28),
    'kyrie': (122, 74, 216), 'lexy': (58, 74, 208), 'max': (154, 210, 242), 'sam': (42, 168, 144),
    'technica': (236, 90, 160), 'stevie': (74, 42, 154), 'garry': (42, 168, 42), 'yuri': (58, 208, 74),
    'zane': (150, 70, 200), 'logan': (230, 190, 40), 'kill': (130, 40, 160), 'keita': (60, 70, 200), 'dojo': (110, 70, 200),
}
FIRE, FIRE2 = (255, 140, 30), (255, 220, 80)
GRAV, GRAV2 = (170, 70, 240), (230, 170, 255)
EARTH = (150, 110, 60)
LIGHT, LIGHT2 = (255, 240, 90), (255, 255, 220)
ICE = (150, 220, 255)
WIND = (210, 245, 255)
GREEN = (60, 255, 140)
CODE = (60, 255, 90)
SOUND = (255, 200, 120)
HEAL = (110, 255, 140)
DEVIL = (120, 30, 160)
SILVER = (220, 225, 240)
RED = (230, 50, 50)
INK = (12, 10, 18)
SKIN = (196, 140, 100)


def mix(a, b, f):
    return tuple(int(a[i] + (b[i] - a[i]) * f) for i in range(3))


class Icon:
    def __init__(self, owner):
        self.c = CH[owner]
        S = 4                                      # supersample, then down to 32 with nearest-ish look
        self.S = S
        self.im = Image.new('RGBA', (N * S, N * S), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)
        # background: rounded tile in the owner's colour, darker at the bottom
        top, bot = mix(self.c, (20, 20, 30), 0.45), mix(self.c, (8, 8, 14), 0.82)
        for y in range(N * S):
            col = mix(top, bot, y / (N * S))
            self.d.line([(0, y), (N * S, y)], fill=col + (255,))
        mask = Image.new('L', (N * S, N * S), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, N * S - 1, N * S - 1], radius=5 * S, fill=255)
        self.im.putalpha(mask)

    # -- see-through shapes: drawn on their own layer and blended in (ImageDraw would punch holes)
    def blend(self, draw_fn):
        layer = Image.new('RGBA', self.im.size, (0, 0, 0, 0))
        draw_fn(ImageDraw.Draw(layer))
        a = self.im.getchannel('A')
        self.im = Image.alpha_composite(self.im, layer)
        self.im.putalpha(a)
        self.d = ImageDraw.Draw(self.im)

    # -- primitives (coords in 32-px units)
    def P(self, x, y): return (x * self.S, y * self.S)

    def poly(self, pts, fill, outline=None, w=1):
        self.d.polygon([self.P(*p) for p in pts], fill=fill + (255,) if len(fill) == 3 else fill, outline=None)
        if outline: self.d.line([self.P(*p) for p in pts + [pts[0]]], fill=outline + (255,), width=int(w * self.S))

    def line(self, a, b, col, w=1.0, alpha=255):
        self.d.line([self.P(*a), self.P(*b)], fill=col + (alpha,), width=max(1, int(w * self.S)))

    def circle(self, x, y, r, fill=None, outline=None, w=1.0, alpha=255):
        box = [self.P(x - r, y - r), self.P(x + r, y + r)]
        if alpha < 255:
            self.blend(lambda d: d.ellipse(box, fill=(fill + (alpha,)) if fill else None, outline=(outline + (alpha,)) if outline else None, width=max(1, int(w * self.S))))
            return
        self.d.ellipse(box, fill=(fill + (alpha,)) if fill else None, outline=(outline + (alpha,)) if outline else None, width=max(1, int(w * self.S)))

    def arc(self, x, y, r, a0, a1, col, w=1.0, alpha=255):
        box = [self.P(x - r, y - r), self.P(x + r, y + r)]
        self.d.arc(box, a0, a1, fill=col + (alpha,), width=max(1, int(w * self.S)))

    def rect(self, x0, y0, x1, y1, col, alpha=255):
        if alpha < 255:
            self.blend(lambda d: d.rectangle([self.P(x0, y0), self.P(x1, y1)], fill=col + (alpha,)))
            return
        self.d.rectangle([self.P(x0, y0), self.P(x1, y1)], fill=col + (alpha,))

    def limb(self, x, y, ang, length, thick, col):
        """A block limb from (x,y), ang degrees: 0 = straight down, 90 = forward (right), 180 = up."""
        a = math.radians(ang)
        dx, dy = math.sin(a), math.cos(a)
        ex, ey = x + dx * length, y + dy * length
        nx, ny = -dy * thick / 2, dx * thick / 2
        pts = [(x + nx, y + ny), (ex + nx, ey + ny), (ex - nx, ey - ny), (x - nx, y - ny)]
        return pts, (ex, ey)

    def figure(self, x, y, pose, scale=1.0, col=None, lean=0, flip=False, ghost=False):
        """Blocky character standing with feet at (x, y). pose = dict of limb angles."""
        col = col or mix(self.c, (255, 255, 255), 0.25)
        dark = mix(col, (0, 0, 0), 0.35)
        s = scale
        p = dict(fa=15, ba=-15, fl=8, bl=-8)      # front arm, back arm, front leg, back leg
        p.update(pose)
        sg = -1 if flip else 1
        hip = (x + math.sin(math.radians(lean)) * 0, y - 8 * s)
        body_top = (x + sg * math.sin(math.radians(lean)) * 7 * s, y - 15 * s)
        parts = []
        # back limbs first (darker), then body, then front limbs
        pts, _ = self.limb(hip[0], hip[1], sg * p['bl'], 8 * s, 2.6 * s, None); parts.append((pts, dark))
        pts, _ = self.limb(body_top[0], body_top[1] + 0.5 * s, sg * p['ba'], 7 * s, 2.4 * s, None); parts.append((pts, dark))
        bx = body_top[0]
        body = [(bx - 2.2 * s, body_top[1]), (bx + 2.2 * s, body_top[1]), (hip[0] + 2.2 * s, hip[1]), (hip[0] - 2.2 * s, hip[1])]
        parts.append((body, col))
        pts, _ = self.limb(hip[0], hip[1], sg * p['fl'], 8 * s, 2.6 * s, None); parts.append((pts, col))
        hx, hy = body_top[0] + sg * 0.6 * s, body_top[1] - 3.4 * s
        head = [(hx - 3 * s, hy - 3 * s), (hx + 3 * s, hy - 3 * s), (hx + 3 * s, hy + 3 * s), (hx - 3 * s, hy + 3 * s)]
        parts.append((head, SKIN))
        pts, hand = self.limb(body_top[0], body_top[1] + 0.5 * s, sg * p['fa'], 7 * s, 2.4 * s, None); parts.append((pts, col))
        a = 150 if ghost else 255
        def draw(d, al):
            for pts, c in parts:   # ink outline
                d.line([self.P(*q) for q in pts + [pts[0]]], fill=INK + (al,), width=int(1.6 * self.S), joint='curve')
            for pts, c in parts:
                d.polygon([self.P(*q) for q in pts], fill=c + (al,))
        if ghost: self.blend(lambda d: draw(d, 140))
        else: draw(self.d, 255)
        # hair band + eye
        self.rect(hx - 3 * s, hy - 3 * s, hx + 3 * s, hy - 1.6 * s, mix(self.c, (0, 0, 0), 0.5), alpha=a)
        ex = hx + sg * 1.6 * s
        self.rect(ex - 0.5 * s, hy - 0.4 * s, ex + 0.5 * s, hy + 0.8 * s, INK, alpha=a)
        return hand

    # -- effects
    def fireball(self, x, y, r, c1=FIRE, c2=FIRE2):
        self.circle(x, y, r + 1.2, fill=mix(c1, (60, 0, 0), 0.4))
        self.circle(x, y, r, fill=c1)
        self.circle(x - r * 0.25, y - r * 0.25, r * 0.55, fill=c2)
        for k in range(3):
            self.poly([(x - r * 0.6 + k * r * 0.6, y - r * 0.7), (x - r * 0.3 + k * r * 0.6, y - r * 1.9 - (k % 2) * 1.2), (x + k * r * 0.6, y - r * 0.6)], c1)

    def flames(self, x, y, w, h, c1=FIRE, c2=FIRE2, n=5):
        for k in range(n):
            fx = x - w / 2 + (k + 0.5) * w / n
            hh = h * (0.6 + 0.4 * ((k * 7) % 3) / 2)
            self.poly([(fx - w / n * 0.7, y), (fx, y - hh), (fx + w / n * 0.7, y)], c1)
            self.poly([(fx - w / n * 0.35, y), (fx, y - hh * 0.55), (fx + w / n * 0.35, y)], c2)

    def beam(self, a, b, w, c1, c2=(255, 255, 255)):
        self.line(a, b, mix(c1, (0, 0, 0), 0.3), w + 2)
        self.line(a, b, c1, w)
        self.line(a, b, c2, max(1, w * 0.35))

    def burst(self, x, y, r, col, n=8, inner=0.4):
        pts = []
        for k in range(n * 2):
            rr = r if k % 2 == 0 else r * inner
            a = math.pi * k / n
            pts.append((x + math.cos(a) * rr, y + math.sin(a) * rr))
        self.poly(pts, col)

    def bolt(self, x, y0, y1, col=LIGHT, w=2.2):
        pts = [(x, y0), (x - 3, y0 + (y1 - y0) * 0.4), (x + 1.5, y0 + (y1 - y0) * 0.45), (x - 2, y1)]
        for i in range(len(pts) - 1): self.line(pts[i], pts[i + 1], mix(col, (90, 70, 0), 0.4), w + 1.6)
        for i in range(len(pts) - 1): self.line(pts[i], pts[i + 1], col, w)
        for i in range(len(pts) - 1): self.line(pts[i], pts[i + 1], LIGHT2, 0.8)

    def swirl(self, x, y, r, col=WIND, turns=2, w=1.2):
        for k in range(turns):
            self.arc(x, y, r * (1 - k * 0.3), 200 + k * 40, 340 + k * 40, col, w)

    def arcs(self, x, y, r, col=SOUND, n=3, a0=-40, a1=40):
        for k in range(n): self.arc(x, y, r + k * 3, a0, a1, col, 1.3)

    def ring(self, x, y, rx, ry, col, w=1.4):
        self.d.ellipse([self.P(x - rx, y - ry), self.P(x + rx, y + ry)], outline=col + (255,), width=int(w * self.S))

    def dome(self, x, y, r, col):
        self.blend(lambda d: d.pieslice([self.P(x - r, y - r), self.P(x + r, y + r)], 180, 360, fill=col + (110,)))
        self.d.pieslice([self.P(x - r, y - r), self.P(x + r, y + r)], 180, 360, fill=None, outline=col + (255,), width=int(1.2 * self.S))

    def sphere(self, x, y, r, col, arrows=True):
        self.circle(x, y, r, fill=col, alpha=120)
        self.circle(x, y, r, outline=mix(col, (255, 255, 255), 0.5), w=1.2)
        if arrows:
            for a in range(0, 360, 90):
                rad = math.radians(a + 45)
                ox, oy = x + math.cos(rad) * (r + 4), y + math.sin(rad) * (r + 4)
                ix, iy = x + math.cos(rad) * (r + 1), y + math.sin(rad) * (r + 1)
                self.line((ox, oy), (ix, iy), mix(col, (255, 255, 255), 0.6), 1)

    def dummy(self, x, y, s=0.8, hit=True):
        self.figure(x, y, dict(fa=-30, ba=30, fl=10, bl=-10), scale=s, col=(120, 120, 130), flip=True)
        if hit: self.burst(x - 1, y - 11 * s, 3.2, (255, 255, 255), 6, 0.35)

    def sword(self, hand, ang, length=10, col=SILVER, guard=(150, 90, 40)):
        a = math.radians(ang)
        tip = (hand[0] + math.sin(a) * length, hand[1] + math.cos(a) * length)
        self.line(hand, tip, INK, 2.6)
        self.line(hand, tip, col, 1.5)
        gx, gy = math.cos(a) * 1.8, -math.sin(a) * 1.8
        self.line((hand[0] - gx, hand[1] - gy), (hand[0] + gx, hand[1] + gy), guard, 1.4)
        return tip

    def gun(self, hand):
        self.rect(hand[0] - 0.5, hand[1] - 1.5, hand[0] + 4, hand[1] + 0.2, (40, 40, 48))
        self.rect(hand[0] - 0.2, hand[1] - 0.2, hand[0] + 1, hand[1] + 2, (40, 40, 48))

    def bat(self, hand, ang=150):
        a = math.radians(ang)
        tip = (hand[0] + math.sin(a) * 11, hand[1] + math.cos(a) * 11)
        self.line(hand, tip, INK, 3.4)
        self.line(hand, tip, (190, 140, 80), 2.2)

    def bomb(self, x, y, r=3):
        self.circle(x, y, r, fill=(30, 30, 36), outline=INK)
        self.circle(x - r * 0.35, y - r * 0.35, r * 0.3, fill=(110, 110, 120))
        self.line((x + r * 0.6, y - r * 0.6), (x + r + 1, y - r - 1), (180, 140, 90), 0.8)
        self.burst(x + r + 1.5, y - r - 1.5, 1.6, FIRE2, 5, 0.4)

    def heart(self, x, y, s, col):
        self.circle(x - s * 0.5, y, s * 0.55, fill=col)
        self.circle(x + s * 0.5, y, s * 0.55, fill=col)
        self.poly([(x - s * 1.05, y + 0.2), (x + s * 1.05, y + 0.2), (x, y + s * 1.4)], col)

    def cross(self, x, y, s, col):
        self.rect(x - s * 0.35, y - s, x + s * 0.35, y + s, col)
        self.rect(x - s, y - s * 0.35, x + s, y + s * 0.35, col)

    def up(self, x, y, col, s=1.0):
        self.poly([(x, y - 3 * s), (x + 2.2 * s, y), (x + 0.8 * s, y), (x + 0.8 * s, y + 2.5 * s), (x - 0.8 * s, y + 2.5 * s), (x - 0.8 * s, y), (x - 2.2 * s, y)], col)

    def down(self, x, y, col, s=1.0):
        self.poly([(x, y + 3 * s), (x + 2.2 * s, y), (x + 0.8 * s, y), (x + 0.8 * s, y - 2.5 * s), (x - 0.8 * s, y - 2.5 * s), (x - 0.8 * s, y), (x - 2.2 * s, y)], col)

    def moon(self, x, y, r, col=SILVER, bg=None):
        self.circle(x, y, r, fill=col)
        self.circle(x + r * 0.45, y - r * 0.3, r * 0.85, fill=bg or mix(self.c, (20, 20, 30), 0.55))

    def note(self, x, y, col=SOUND):
        self.circle(x, y, 1.3, fill=col)
        self.line((x + 1.1, y), (x + 1.1, y - 4.5), col, 0.8)
        self.line((x + 1.1, y - 4.5), (x + 3, y - 3.5), col, 0.8)

    def text(self, x, y, s, col):
        # tiny blocky letters (Z, z only + digits used)
        self.d.text(self.P(x, y), s, fill=col + (255,))

    def eye(self, x, y, r, col):
        self.d.ellipse([self.P(x - r, y - r * 0.55), self.P(x + r, y + r * 0.55)], fill=(255, 255, 255, 255), outline=col + (255,), width=self.S)
        self.circle(x, y, r * 0.38, fill=col)

    def code(self, x0, y0, x1, y1, col=CODE):
        self.rect(x0, y0, x1, y1, (0, 40, 10), alpha=200)
        for yy in range(int(y0) + 1, int(y1), 2):
            for xx in range(int(x0) + 1, int(x1), 2):
                if (xx * 7 + yy * 13) % 3: self.rect(xx, yy, xx + 0.8, yy + 0.8, col)

    def blocks(self, x0, y, n, col=EARTH, step=3.5, rise=1.6):
        for k in range(n):
            h = 2 + k * rise
            self.rect(x0 + k * step, y - h, x0 + k * step + 3, y, col)
            self.rect(x0 + k * step, y - h, x0 + k * step + 3, y - h + 0.8, mix(col, (255, 255, 255), 0.35))

    def portal(self, x, y, rx, ry, col=(160, 80, 255)):
        self.d.ellipse([self.P(x - rx, y - ry), self.P(x + rx, y + ry)], fill=(20, 0, 40, 255), outline=col + (255,), width=int(1.5 * self.S))
        self.ring(x, y, rx * 0.6, ry * 0.6, mix(col, (255, 255, 255), 0.4), 0.8)

    def speed(self, x, y, n=3, col=(255, 255, 255), length=6):
        for k in range(n): self.line((x - length, y + k * 2.5), (x, y + k * 2.5), col, 0.8)

    def ice(self, x, y, s=2.5):
        self.poly([(x, y - s * 1.4), (x + s * 0.6, y), (x, y + s * 1.4), (x - s * 0.6, y)], ICE)
        self.line((x, y - s * 1.4), (x, y + s * 1.4), (255, 255, 255), 0.6)

    def save(self, sid):
        im = self.im.resize((N, N), Image.LANCZOS)
        # crisp alpha edge
        px = im.load()
        for yy in range(N):
            for xx in range(N):
                r, g, b, a = px[xx, yy]
                px[xx, yy] = (r, g, b, 255 if a > 100 else 0)
        os.makedirs(OUT, exist_ok=True)
        im.save(os.path.join(OUT, sid + '.png'))


# ---------------------------------------------------------------- poses
STAND = dict()
PUNCH = dict(fa=90, ba=-40, fl=25, bl=-25)
KICK = dict(fa=40, ba=-60, fl=100, bl=-10)
CAST = dict(fa=85, ba=70, fl=20, bl=-20)
RAISE = dict(fa=170, ba=-20)
BOTH_UP = dict(fa=160, ba=-160, fl=12, bl=-12)
POINT = dict(fa=92, ba=-10, fl=10, bl=-10)
SLASH = dict(fa=125, ba=-40, fl=30, bl=-30)
THRUST = dict(fa=95, ba=-70, fl=45, bl=-35)
DASH = dict(fa=-70, ba=-80, fl=70, bl=-50)
GUARD = dict(fa=70, ba=55, fl=15, bl=-15)
CROUCH = dict(fa=60, ba=-40, fl=70, bl=-70)
STOMP = dict(fa=60, ba=-60, fl=50, bl=-5)
SWING = dict(fa=60, ba=40, fl=25, bl=-25)
SHOOT = dict(fa=88, ba=75, fl=18, bl=-18)
DANCE = dict(fa=165, ba=-30, fl=60, bl=-10)
CHEER = dict(fa=150, ba=-150, fl=20, bl=-20)
HIP = dict(fa=20, ba=-20, fl=12, bl=-12)
CROSS = dict(fa=60, ba=60, fl=8, bl=-8)

X, Y = 12, 28      # usual feet position: left-centre, facing right


def spec(sid, owner):
    def deco(f):
        SPECS.append((sid, owner, f))
        return f
    return deco


SPECS = []

# ---------------------------------------------------------------- Mikiko
@spec('hekublade_scatter', 'mikiko')
def _(i):
    i.figure(9, Y, CAST)
    for k, (bx, by) in enumerate([(17, 7), (22, 11), (19, 16), (25, 6), (26, 16)]):
        i.sword((bx, by), 90, 6, col=FIRE2, guard=RED)

@spec('kitsune', 'mikiko')
def _(i):
    i.speed(10, 14, 4, FIRE, 8)
    i.figure(18, Y, DASH, lean=25)
    i.dummy(27, Y)
    for k in range(4): i.fireball(5 + k * 3, 24 - k * 3, 1.2)

@spec('netsu', 'mikiko')
def _(i):
    i.flames(X + 1, Y, 18, 20, n=6)
    i.figure(X + 1, Y, HIP)
    i.up(25, 10, FIRE2)

@spec('fire_blitz', 'mikiko')
def _(i):
    h = i.figure(9, Y, CAST)
    i.fireball(23, 13, 4.5)

@spec('dance_kick', 'mikiko')
def _(i):
    i.arcs(16, 16, 8, FIRE2, 2, 180, 330)
    i.figure(16, Y - 4, DANCE)
    i.note(26, 8, FIRE2)

@spec('netsu_burst', 'mikiko')
def _(i):
    i.beam((2, 20), (30, 20), 5, FIRE, FIRE2)
    i.figure(20, Y, DASH, lean=30)

@spec('manifest_greatness', 'mikiko')
def _(i):
    i.circle(16, 17, 12, fill=(220, 30, 30), alpha=90)
    i.figure(16, Y, BOTH_UP, col=(230, 70, 60))
    for x in (5, 27): i.up(x, 14, (255, 90, 80))

@spec('true_empathy', 'mikiko')
def _(i):
    i.figure(22, Y, PUNCH, ghost=True, col=(150, 210, 255))
    i.figure(10, Y, PUNCH)
    i.heart(16, 6, 2.2, (255, 120, 160))

@spec('soul_splitter', 'mikiko')
def _(i):
    i.figure(10, Y, GUARD)
    i.figure(21, Y, GUARD, ghost=True, col=(170, 230, 255))
    i.line((15, 9), (19, 9), (200, 240, 255), 1)

@spec('blazing_wind', 'mikiko')
def _(i):
    for k in range(5): i.ring(16, 24 - k * 4.5, 4 + k * 2.4, 1.6 + k * 0.4, mix(FIRE, FIRE2, k / 5), 1.6)
    i.flames(16, 30, 26, 8, n=7)
    i.figure(16, 20, RAISE, scale=0.6)

# ---------------------------------------------------------------- Rhalix
@spec('kishi', 'rhalix')
def _(i):
    i.figure(8, Y, RAISE, scale=0.85)
    i.dome(22, Y, 9, GRAV)
    i.dummy(22, Y, 0.6, hit=False)
    i.down(22, 8, GRAV2)

@spec('earth_wave', 'rhalix')
def _(i):
    i.figure(7, Y, STOMP, scale=0.85)
    i.blocks(13, Y, 5)

@spec('gravity_compress', 'rhalix')
def _(i):
    i.figure(7, Y, CAST, scale=0.8)
    i.sphere(22, 15, 6.5, GRAV)
    i.dummy(22, 20, 0.45, hit=False)

@spec('bang', 'rhalix')
def _(i):
    h = i.figure(9, Y, POINT)
    i.circle(24, h[1], 2.6, fill=EARTH, outline=INK)
    i.speed(20, h[1] - 2, 3, (220, 200, 160), 4)

@spec('moon_reflect', 'rhalix')
def _(i):
    i.figure(13, Y, GUARD)
    i.arc(14, 17, 11, -70, 70, SILVER, 2.4)
    i.line((30, 8), (25, 12), (255, 255, 255), 1)
    i.line((25, 12), (30, 16), (255, 255, 255), 1)

@spec('ka_release', 'rhalix')
def _(i):
    i.figure(16, Y, BOTH_UP)
    for x, y in ((6, 10), (26, 10), (5, 22), (27, 22)):
        i.line((x - 1.5, y - 1.5), (x + 1.5, y + 1.5), (200, 200, 210), 1.2)
        i.line((x + 1.5, y - 1.5), (x - 1.5, y + 1.5), (200, 200, 210), 1.2)
    i.burst(16, 4, 3, GRAV2, 6)

@spec('gravity_barrage', 'rhalix')
def _(i):
    for k in range(6):
        a = k * 1.05
        i.sphere(16 + math.cos(a) * 9, 15 + math.sin(a) * 8, 2.6, GRAV, arrows=False)
    i.sphere(16, 15, 4.5, GRAV2, arrows=False)
    i.down(16, 4, GRAV2)

@spec('moon_fall', 'rhalix')
def _(i):
    i.circle(19, 9, 7, fill=(210, 210, 225), outline=INK)
    for cx, cy, r in ((17, 7, 1.4), (22, 11, 1.1), (20, 5, 0.8)): i.circle(cx, cy, r, fill=(170, 170, 190))
    i.figure(8, Y, RAISE, scale=0.8)
    i.speed(27, 18, 3, SILVER, 4)

@spec('xeno_blast', 'rhalix')
def _(i):
    i.figure(8, Y, CAST)
    i.beam((14, 15), (31, 13), 8, (200, 120, 255), (255, 230, 255))
    i.burst(14, 15, 4, (255, 255, 255), 8)

# ---------------------------------------------------------------- Ultima
@spec('attack_prediction', 'ultima')
def _(i):
    i.figure(11, Y, GUARD)
    i.eye(23, 9, 5, GREEN)
    i.arc(16, 16, 12, 200, 240, GREEN, 1)

@spec('energy_blast', 'ultima')
def _(i):
    i.figure(8, Y, CAST)
    for k, (x, y) in enumerate([(19, 13), (24, 10), (27, 16)]): i.circle(x, y, 2.4 - k * 0.3, fill=GREEN, outline=(200, 255, 220))

@spec('living_weapon', 'ultima')
def _(i):
    i.circle(14, 17, 11, outline=GREEN, w=1.2)
    i.figure(14, Y, PUNCH)
    i.burst(25, 13, 4, GREEN, 7)

@spec('output_surge', 'ultima')
def _(i):
    i.figure(10, Y, CHEER)
    for k in range(4): i.rect(21 + k * 2.5, 26 - k * 4, 23 + k * 2.5, 28, mix(GREEN, (255, 255, 255), k / 5))

@spec('full_output_beam', 'ultima')
def _(i):
    i.figure(7, Y, CAST)
    i.beam((12, 15), (31, 15), 9, GREEN, (230, 255, 240))

# ---------------------------------------------------------------- Kaeru
@spec('sound_burst', 'kaeru')
def _(i):
    i.figure(9, Y, CAST)
    i.arcs(14, 15, 5, SOUND, 4)

@spec('hekubarrier', 'kaeru')
def _(i):
    i.ring(16, 17, 12, 12, FIRE2, 1.6)
    for a in range(0, 360, 60):
        r = math.radians(a)
        i.sword((16 + math.cos(r) * 12, 17 + math.sin(r) * 12), a + 90, 3, col=FIRE2, guard=RED)
    i.figure(16, Y, GUARD, scale=0.8)

@spec('fox_heal', 'kaeru')
def _(i):
    i.figure(11, Y, CAST)
    i.cross(24, 11, 3.5, HEAL)
    i.heart(22, 22, 1.6, (255, 130, 160))

@spec('scent_track', 'kaeru')
def _(i):
    i.figure(9, Y, POINT)
    for k in range(3): i.arc(16 + k * 4, 12, 2.5, 180, 360, (230, 230, 200), 0.9)
    i.figure(27, Y, STAND, scale=0.6, col=(255, 240, 120), flip=True)

@spec('hekubeats_finale', 'kaeru')
def _(i):
    for k in range(4): i.ring(16, 16, 3 + k * 3.4, 3 + k * 3.4, mix(SOUND, FIRE, k / 4), 1.4)
    i.note(5, 8); i.note(25, 26)
    i.figure(16, 23, BOTH_UP, scale=0.55)

# ---------------------------------------------------------------- Kyrie
@spec('rapier_thrust', 'kyrie')
def _(i):
    h = i.figure(9, Y, THRUST)
    i.sword(h, 92, 13)
    i.speed(9, 12, 3, (230, 220, 255), 5)

@spec('gravity_fetch', 'kyrie')
def _(i):
    i.figure(9, Y, CAST)
    i.line((26, 14), (16, 14), GRAV2, 1)
    i.poly([(16, 12), (13, 14), (16, 16)], GRAV2)
    i.rect(24, 12, 28, 16, (200, 160, 90))

@spec('bullet_stop', 'kyrie')
def _(i):
    i.figure(9, Y, CAST)
    i.circle(22, 15, 7, fill=GRAV, alpha=90)
    for y in (11, 15, 19): i.line((20, y), (25, y), (230, 230, 230), 1); i.poly([(25, y - 1), (27, y), (25, y + 1)], (230, 230, 230))

@spec('boxing_stance', 'kyrie')
def _(i):
    i.figure(14, Y, dict(fa=110, ba=95, fl=28, bl=-25))
    i.up(26, 12, (255, 200, 90))

@spec('no_milk', 'kyrie')
def _(i):
    i.dummy(16, Y, 0.85, hit=False)
    for x in range(7, 27, 4): i.rect(x, 6, x + 1, Y, (160, 160, 175))
    i.rect(6, 5, 27, 6.5, (160, 160, 175)); i.rect(6, Y - 1, 27, Y, (160, 160, 175))
    i.circle(16, 17, 12, fill=GRAV, alpha=60)

@spec('royal_gravity', 'kyrie')
def _(i):
    i.figure(16, Y, BOTH_UP, scale=0.8)
    for x in (5, 27): i.dummy(x, 14, 0.5, hit=False); i.up(x, 20, GRAV2, 0.7)
    i.ring(16, 26, 13, 3, GRAV2, 1.2)

# ---------------------------------------------------------------- Lexy
@spec('freak_punch', 'lexy')
def _(i):
    i.figure(10, Y, PUNCH)
    i.burst(22, 13, 6, (255, 255, 255), 8)
    i.burst(22, 13, 3, (255, 210, 90), 6)

@spec('table_breaker', 'lexy')
def _(i):
    i.figure(16, 20, CROUCH)
    i.blocks(2, Y + 1, 3, (120, 90, 60), 3.2, 1.2)
    i.blocks(21, Y + 1, 3, (120, 90, 60), 3.2, 1.2)
    i.arc(16, 30, 13, 190, 350, (255, 255, 255), 1)

@spec('karma_surge', 'lexy')
def _(i):
    i.figure(16, Y, CHEER)
    for x, y in ((5, 9), (27, 9), (6, 21), (26, 21)): i.burst(x, y, 2.2, (120, 180, 255), 5)

@spec('wing_rush', 'lexy')
def _(i):
    i.poly([(14, 12), (4, 4), (6, 14)], (240, 240, 255))
    i.figure(17, Y, DASH, lean=25)
    i.speed(6, 18, 3)

@spec('iron_grip', 'lexy')
def _(i):
    h = i.figure(10, Y, dict(fa=100, ba=-30, fl=20, bl=-20))
    i.dummy(23, Y - 3, 0.7)
    i.arc(21, 14, 4, 300, 60, (255, 255, 255), 1.2)

@spec('persephone', 'lexy')
def _(i):
    i.circle(16, 15, 12, fill=(150, 20, 60), alpha=120)
    i.figure(16, Y, CHEER, col=(200, 40, 90))
    i.eye(16, 7, 3, (255, 40, 80))

# ---------------------------------------------------------------- Max
@spec('dagger_flurry', 'max')
def _(i):
    h = i.figure(9, Y, THRUST)
    for k in range(4): i.sword((15 + k * 2.5, 9 + k * 3.5), 95, 5)
    i.dummy(28, Y, 0.6)

@spec('wind_of_sorrow', 'max')
def _(i):
    i.swirl(12, 16, 9)
    i.figure(18, Y, DASH, lean=20)
    i.speed(9, 22, 3, WIND)

@spec('wind_wall', 'max')
def _(i):
    for k in range(3): i.ring(16, 17, 6 + k * 4, 11 + k, WIND, 1.2)
    i.figure(16, Y, GUARD, scale=0.8)

@spec('lunar_mind', 'max')
def _(i):
    i.moon(24, 8, 5)
    i.figure(12, Y, dict(fa=40, ba=-40))
    i.up(25, 22, (190, 220, 255))

@spec('rapid_vampire_slice', 'max')
def _(i):
    for k in range(4): i.line((18 + k, 6 + k * 4), (28 + k, 10 + k * 4), (255, 255, 255), 1)
    i.figure(9, Y, SLASH)
    for k in range(3): i.circle(20 + k * 3, 24 - k * 2, 0.9, fill=RED)

@spec('moonlit_gale', 'max')
def _(i):
    i.moon(26, 7, 4)
    for k in range(3): i.swirl(14, 18, 8 + k * 3, WIND, 1)
    i.figure(14, Y, RAISE, scale=0.75)

# ---------------------------------------------------------------- Sam
@spec('batter_up', 'sam')
def _(i):
    h = i.figure(10, Y, SWING)
    i.bat(h, 120)
    i.circle(26, 10, 2.2, fill=(255, 255, 255), outline=INK)
    i.speed(23, 9, 2, (255, 255, 255), 4)

@spec('clear_space', 'sam')
def _(i):
    i.figure(8, Y, STAND, ghost=True)
    i.speed(18, 14, 3, (180, 255, 230), 5)
    i.figure(23, Y, PUNCH)

@spec('ticking_time_bomb', 'sam')
def _(i):
    h = i.figure(9, Y, SWING)
    i.bat(h, 110)
    i.bomb(24, 18, 3.5)

@spec('knockout_mode', 'sam')
def _(i):
    h = i.figure(11, Y, SWING)
    i.bat(h, 160)
    for k in range(3): i.burst(22 + k * 3, 6 + (k % 2) * 3, 1.6, (255, 230, 90), 5)

@spec('batter_shoot', 'sam')
def _(i):
    h = i.figure(9, Y, SWING)
    i.bat(h, 120)
    i.bomb(25, 9, 2.5)
    i.speed(21, 9, 2, (255, 255, 255), 4)

@spec('psychotic_time_bomb', 'sam')
def _(i):
    for x, y in ((6, 9), (16, 6), (26, 9), (7, 20), (25, 20)): i.bomb(x, y, 2.4)
    i.figure(16, Y, CHEER, scale=0.7)

# ---------------------------------------------------------------- Technica
@spec('explosive_rounds', 'technica')
def _(i):
    h = i.figure(8, Y, SHOOT); i.gun(h)
    for k in range(3): i.circle(17 + k * 3, h[1] - 1, 0.7, fill=FIRE2)
    i.burst(27, h[1] - 1, 4, FIRE, 7)

@spec('gravity_rounds', 'technica')
def _(i):
    h = i.figure(8, Y, SHOOT); i.gun(h)
    for k in range(3): i.circle(17 + k * 3, h[1] - 1, 0.8, fill=GRAV2)
    i.sphere(27, h[1] - 1, 3, GRAV, arrows=False)

@spec('gravity_boots', 'technica')
def _(i):
    i.figure(16, 24, dict(fa=150, ba=-140, fl=40, bl=-40))
    for x in (12, 20): i.ring(x, 27, 3, 1, GRAV2, 1)
    i.up(27, 10, GRAV2)

@spec('visor_scan', 'technica')
def _(i):
    i.figure(9, Y, STAND)
    i.poly([(13, 10), (31, 4), (31, 20)], (120, 230, 255))
    i.circle(26, 12, 2, outline=(255, 255, 255))

@spec('cloak_device', 'technica')
def _(i):
    i.figure(16, Y, STAND, ghost=True, col=(170, 200, 230))
    for x, y in ((7, 9), (25, 12), (9, 22), (24, 24)): i.burst(x, y, 1.6, (255, 255, 255), 4)

@spec('rescue_platform', 'technica')
def _(i):
    i.rect(5, 22, 27, 24, (180, 190, 210)); i.rect(5, 24, 27, 25, (90, 100, 120))
    for x in (11, 21): i.figure(x, 22, STAND, scale=0.6)
    i.cross(16, 7, 2.6, HEAL)
    i.up(5, 14, (160, 230, 255), 0.7); i.up(27, 14, (160, 230, 255), 0.7)

@spec('tech_barrage', 'technica')
def _(i):
    h = i.figure(7, Y, SHOOT); i.gun(h)
    for k in range(7): i.line((14 + k * 2.3, h[1] - 3 + (k % 3) * 2), (15.5 + k * 2.3, h[1] - 3 + (k % 3) * 2), FIRE2, 0.9)
    i.burst(27, 12, 3, FIRE, 6); i.burst(25, 22, 2.6, GRAV2, 6)

# ---------------------------------------------------------------- Disco Dojo brothers (Stevie / Garry)
@spec('karma_blast', 'dojo')
def _(i):
    i.figure(8, Y, CAST)
    i.circle(23, 14, 5, fill=(150, 110, 255), outline=(230, 220, 255))
    i.speed(18, 12, 3, (220, 210, 255), 4)

@spec('war_cry', 'dojo')
def _(i):
    i.figure(12, Y, CHEER)
    i.arcs(16, 9, 7, (255, 120, 90), 3, -60, 60)

@spec('good_karma', 'dojo')
def _(i):
    i.figure(10, Y, PUNCH)
    i.dummy(24, Y, 0.75)
    for k in range(3): i.down(24 + (k - 1) * 4, 6, (120, 220, 255), 0.6)

@spec('disco_kick', 'dojo')
def _(i):
    i.circle(16, 6, 3, fill=(200, 200, 220), outline=INK)
    for a in range(0, 360, 45): i.line((16, 6), (16 + math.cos(math.radians(a)) * 5, 6 + math.sin(math.radians(a)) * 5), (255, 255, 255), 0.5)
    i.arcs(16, 20, 9, (255, 120, 220), 2, 200, 340)
    i.figure(16, Y, dict(fa=150, ba=-60, fl=85, bl=-10))

@spec('bar_thief', 'stevie')
def _(i):
    i.figure(9, Y, dict(fa=95, ba=-20))
    i.up(24, 14, (255, 210, 90))
    i.note(20, 8); i.note(27, 22)

@spec('body_slam', 'garry')
def _(i):
    i.figure(16, 18, dict(fa=150, ba=-150, fl=-30, bl=30), lean=60)
    i.dummy(16, Y + 1, 0.55, hit=True)
    i.arc(16, 31, 12, 190, 350, (255, 255, 255), 1)

@spec('freestyle_bars', 'garry')
def _(i):
    h = i.figure(11, Y, dict(fa=150, ba=40))
    i.circle(h[0], h[1], 1.6, fill=(60, 60, 70), outline=INK)
    for x, y in ((21, 9), (25, 16), (20, 20)): i.note(x, y, (120, 255, 140))

@spec('brother_blast', 'dojo')
def _(i):
    i.figure(6, Y, CAST, scale=0.8)
    i.figure(12, Y, CAST, scale=0.8, col=(120, 220, 120))
    i.beam((16, 16), (31, 16), 8, (160, 120, 255), (240, 230, 255))

# ---------------------------------------------------------------- Yuri
@spec('camera_hack', 'yuri')
def _(i):
    i.rect(18, 6, 26, 11, (60, 60, 70)); i.rect(26, 7, 28, 10, (40, 40, 50)); i.circle(19.5, 8.5, 1, fill=RED)
    i.figure(10, Y, dict(fa=70, ba=60))
    i.code(17, 17, 29, 25)

@spec('firewall', 'yuri')
def _(i):
    i.figure(8, Y, CAST)
    i.code(17, 4, 25, Y)

@spec('elevator_hack', 'yuri')
def _(i):
    i.figure(8, Y, dict(fa=70, ba=60))
    i.rect(18, 18, 28, Y, (130, 130, 145)); i.up(23, 11, CODE)
    i.dummy(23, 18, 0.5, hit=False)

@spec('tenjo_medical', 'yuri')
def _(i):
    i.figure(10, Y, CHEER)
    i.cross(24, 10, 3.6, HEAL)
    i.arcs(16, 9, 6, (255, 255, 255), 2, -50, 50)

@spec('virus_upload', 'yuri')
def _(i):
    i.figure(8, Y, POINT)
    i.dummy(25, Y, 0.75, hit=False)
    for k in range(5): i.rect(15 + k * 1.8, 13 + (k % 2), 16 + k * 1.8, 14 + (k % 2), CODE)
    i.circle(25, 13, 2.2, fill=(120, 30, 160), outline=CODE)

@spec('open_sky_ground', 'yuri')
def _(i):
    for x in (7, 16, 25): i.dummy(x, 15, 0.5, hit=False); i.up(x, 20, CODE, 0.6)
    i.code(2, 25, 30, 30)

# ---------------------------------------------------------------- Zane
@spec('fire_spell', 'zane')
def _(i):
    h = i.figure(8, Y, CAST)
    i.circle(h[0] + 1, h[1], 1.8, fill=(200, 160, 255), outline=INK)
    i.beam((h[0] + 2, h[1]), (28, 12), 3, FIRE, FIRE2)
    i.flames(27, 16, 6, 6, n=3)

@spec('ice_spell', 'zane')
def _(i):
    i.figure(16, Y, BOTH_UP, scale=0.8)
    for x, y in ((6, 10), (26, 10), (5, 22), (27, 22), (16, 4)): i.ice(x, y)

@spec('thunder_spell', 'zane')
def _(i):
    i.figure(8, Y, POINT)
    i.bolt(24, 2, Y)
    i.burst(23, Y - 1, 3, LIGHT2, 6)

@spec('broom_smack', 'zane')
def _(i):
    h = i.figure(10, Y, SWING)
    i.bat(h, 120)
    i.line((h[0] + math.sin(math.radians(120)) * 11, h[1] + math.cos(math.radians(120)) * 11), (h[0] + math.sin(math.radians(120)) * 14, h[1] + math.cos(math.radians(120)) * 14), (220, 190, 90), 3)
    i.arc(16, 16, 12, 280, 20, (255, 255, 255), 1)

@spec('mana_barrier', 'zane')
def _(i):
    i.circle(16, 17, 12, fill=(150, 90, 255), alpha=90)
    i.circle(16, 17, 12, outline=(210, 180, 255), w=1.4)
    i.figure(16, Y, GUARD, scale=0.8)

@spec('past_mages', 'zane')
def _(i):
    i.figure(16, Y, RAISE, scale=0.75)
    i.fireball(6, 9, 2.5)
    i.ice(16, 5, 2.6)
    i.bolt(26, 2, 14, w=1.6)

# ---------------------------------------------------------------- Logan
@spec('thunder_flash', 'logan')
def _(i):
    i.figure(8, Y, POINT)
    i.dummy(24, Y, 0.75)
    i.bolt(24, 1, 16)

@spec('knight_charge', 'logan')
def _(i):
    i.speed(8, 16, 3, (255, 240, 180), 7)
    h = i.figure(18, Y, dict(fa=95, ba=-70, fl=70, bl=-50), lean=25)
    i.sword(h, 95, 9)

@spec('royal_guard', 'logan')
def _(i):
    i.figure(12, Y, GUARD)
    i.poly([(19, 9), (27, 9), (27, 18), (23, 23), (19, 18)], (220, 190, 60), INK, 0.8)
    i.cross(23, 15, 2.2, (255, 250, 220))

@spec('portal_escape', 'logan')
def _(i):
    i.portal(9, 17, 5, 10)
    i.figure(23, Y, STAND)
    i.speed(19, 14, 3, (190, 140, 255), 5)

@spec('thunder_judgement', 'logan')
def _(i):
    i.figure(16, Y, RAISE, scale=0.75)
    for x in (5, 27): i.bolt(x, 1, Y, w=1.8)
    i.bolt(16, 1, 9, w=1.4)

# ---------------------------------------------------------------- Kill
@spec('impale', 'kill')
def _(i):
    h = i.figure(9, Y, THRUST)
    i.sword(h, 92, 14, col=(200, 200, 210))
    for k in range(3): i.circle(24 + k * 1.5, 22 + k, 0.8, fill=RED)

@spec('devil_dash', 'kill')
def _(i):
    i.poly([(14, 12), (5, 5), (8, 12), (3, 13), (10, 16)], DEVIL)
    h = i.figure(18, Y, dict(fa=95, ba=-70, fl=70, bl=-50), lean=25)
    i.sword(h, 95, 8)
    i.speed(8, 22, 2, (200, 120, 255))

@spec('play_weak', 'kill')
def _(i):
    i.figure(11, Y, dict(fa=-10, ba=10, fl=15, bl=-15))
    i.text(19, 4, '?', (255, 255, 255))
    i.up(25, 18, (255, 90, 90))
    i.up(28, 22, (255, 90, 90), 0.6)

@spec('devil_flame', 'kill')
def _(i):
    i.flames(16, Y + 1, 28, 18, DEVIL, (200, 90, 255), n=7)
    i.figure(16, Y, BOTH_UP, scale=0.85)

@spec('devil_unleashed', 'kill')
def _(i):
    i.poly([(14, 12), (2, 2), (5, 13), (1, 15), (10, 17)], DEVIL)
    i.poly([(18, 12), (30, 2), (27, 13), (31, 15), (22, 17)], DEVIL)
    i.figure(16, Y, CHEER, col=(90, 20, 110))
    i.eye(16, 9, 2, RED)

# ---------------------------------------------------------------- Keita
@spec('lights_out', 'keita')
def _(i):
    i.figure(8, Y, dict(fa=40, ba=-10))
    i.dummy(24, Y, 0.75, hit=False)
    i.rect(19, 7, 29, 10, INK)

@spec('kneel', 'keita')
def _(i):
    i.figure(8, Y, POINT)
    i.dummy(23, Y, 0.55, hit=False)
    for x in (19, 23, 27): i.down(x, 8, (180, 180, 255), 0.7)

@spec('lazy_slash', 'keita')
def _(i):
    h = i.figure(9, Y, SLASH)
    i.sword(h, 125, 11)
    i.arc(13, 18, 13, 290, 30, (255, 255, 255), 1.2)

@spec('power_nap', 'keita')
def _(i):
    i.rect(4, 24, 28, 27, (90, 70, 50))
    # lying body
    i.rect(6, 19, 22, 23, mix(CH['keita'], (255, 255, 255), 0.25)); i.rect(22, 18, 27, 23, SKIN)
    for k, (x, y) in enumerate(((22, 13), (25, 9), (28, 5))): i.text(x, y, 'z', (230, 230, 255))

@spec('mass_cut', 'keita')
def _(i):
    i.arc(16, 17, 13, 0, 360, (255, 255, 255), 1.4)
    i.arc(16, 17, 10, 30, 300, (190, 200, 255), 1)
    h = i.figure(16, Y, SLASH, scale=0.85)
    i.sword(h, 125, 9)

# ---------------------------------------------------------------- Mikiko: Xeno Blast (KA slot)
@spec('xeno_blast_mikiko', 'mikiko')
def _(i):
    i.figure(8, Y, CAST)
    i.beam((14, 15), (31, 13), 8, FIRE, (255, 245, 200))
    i.burst(14, 15, 4, (255, 255, 255), 8)


if __name__ == '__main__':
    for sid, owner, f in SPECS:
        ic = Icon(owner)
        f(ic)
        ic.save(sid)
    print(len(SPECS), 'icons ->', os.path.abspath(OUT))
