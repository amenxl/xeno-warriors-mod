"""An anime-style face for the YSM model (32 x 32 px over the 8 x 8 face): big eyes, thin brows, a small mouth.
Mikiko: amber eyes, brown skin, a confident little smirk.  python3 -I anime_face.py <out.png>"""
import sys
from PIL import Image

SKIN = (150, 96, 62, 255)
SKIN_SH = (128, 80, 50, 255)
LASH = (28, 18, 16, 255)
BROW = (40, 24, 20, 255)
WHITE = (250, 246, 240, 255)
IRIS = [(150, 52, 8, 255), (196, 84, 14, 255), (232, 128, 26, 255), (248, 172, 48, 255), (255, 206, 92, 255)]
PUPIL = (60, 20, 8, 255)
SHINE = (255, 255, 255, 255)
MOUTH = (78, 36, 30, 255)
BLUSH = (170, 92, 70, 255)


def face(closed=False):
    im = Image.new('RGBA', (32, 32), SKIN)
    px = im.load()

    def rect(x0, y0, x1, y1, c):
        for x in range(x0, x1):
            for y in range(y0, y1):
                if 0 <= x < 32 and 0 <= y < 32: px[x, y] = c

    # soft shading under the hairline and at the jaw sides
    rect(0, 0, 32, 2, SKIN_SH)
    rect(0, 28, 32, 32, SKIN_SH)
    for side in (0, 1):
        # eyes sit low and big (YSM style): 8 wide x 11 tall, rows 13..24; 4 px apart at the nose bridge
        x0 = 4 if side == 0 else 20
        inner = x0 + 7 if side == 0 else x0
        outer = x0 if side == 0 else x0 + 7
        # brows: short and thin, set high, angled down to the middle (confident)
        for i in range(6):
            bx = (x0 + 1 + i) if side == 0 else (x0 + 6 - i)
            rect(bx, 9 + (1 if i >= 4 else 0), bx + 1, 10 + (1 if i >= 4 else 0), BROW)
        if closed:
            rect(x0, 19, x0 + 8, 20, LASH)
            continue
        rect(x0, 13, x0 + 8, 15, LASH)                     # thick upper lash
        rect(outer - (1 if side == 0 else -1), 12, outer - (1 if side == 0 else -1) + 1, 14, LASH)   # outer flick
        rect(x0, 15, x0 + 8, 24, WHITE)
        ix0 = x0 + 1
        for i, y in enumerate(range(15, 24)):
            c = IRIS[min(len(IRIS) - 1, i * len(IRIS) // 9)]
            rect(ix0, y, ix0 + 6, y + 1, c)
        rect(ix0 + 2, 17, ix0 + 4, 21, PUPIL)
        hx = ix0 if side == 0 else ix0 + 4
        rect(hx, 15, hx + 2, 17, SHINE)                    # big highlight
        rect(ix0 + (4 if side == 0 else 1), 21, ix0 + (5 if side == 0 else 2), 22, SHINE)
        rect(x0 + 1, 24, x0 + 7, 25, (96, 52, 36, 255))    # lower lash
    # nose: a single shade pixel, mouth: a small smirk
    rect(15, 25, 17, 26, SKIN_SH)
    rect(14, 28, 18, 29, MOUTH)
    rect(18, 27, 19, 28, MOUTH)
    return im


if __name__ == '__main__':
    out = sys.argv[1]
    face().save(out)
    face(True).save(out.replace('.png', '_closed.png'))
    print('ok')
