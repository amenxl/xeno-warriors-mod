"""Rain faces: fed-up half-lidded look, water streaming down the face, drips off the brows, nose and chin.
Three frames (the water runs down), drawn over the 'annoyed' face; only skin pixels are painted, so the eyes,
brows and mouth stay clean."""
import sys
from PIL import Image
D = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
WATER, SHINE, DEEP = (150, 200, 240, 255), (225, 245, 255, 255), (105, 160, 215, 255)


def skin_of(im):
    return im.getpixel((15, 29))


def is_skin(p, skin, tol=14):
    return p[3] > 0 and all(abs(p[i] - skin[i]) <= tol for i in range(3))


def darker(c, f):
    return (int(c[0] * f), int(c[1] * f), int(c[2] * f), 255)


def make(char):
    base = Image.open(D + f'{char}_face_annoyed.png').convert('RGBA')
    skin = skin_of(base)
    # (x, start y, length) of each little stream; each frame they run 3 pixels further down (wrapping)
    streams = [(3, 2, 3), (9, 5, 2), (14, 0, 3), (21, 3, 2), (28, 1, 3),
               (5, 20, 3), (11, 23, 2), (22, 21, 3), (27, 24, 2)]
    for f in range(3):
        im = base.copy()
        # wet, cold sheen: a slightly darker band across the cheeks under the eyes
        for x, y0, ln in streams:
            top = 0 if y0 < 12 else 19
            span = 12 if y0 < 12 else 12          # forehead band (0-11) or cheek band (19-30)
            y = top + (y0 - top + f * 3) % span
            for i in range(ln):
                yy = y + i
                if yy >= top + span or not is_skin(im.getpixel((x, yy)), skin):
                    continue
                im.putpixel((x, yy), SHINE if i == 0 else WATER)
            # the drop at the end of the stream
            yy = y + ln
            if yy < top + span and is_skin(im.getpixel((x, yy)), skin):
                im.putpixel((x, yy), DEEP)
        # drips hanging off the brows (they swell, then fall)
        for bx in (6, 10, 20, 24):
            for yy in range(8, 15):
                if not is_skin(im.getpixel((bx, yy)), skin) and is_skin(im.getpixel((bx, yy + 1)), skin):
                    if (bx + f) % 3 != 0:
                        im.putpixel((bx, yy + 1), WATER)
                    if (bx + f) % 3 == 1 and is_skin(im.getpixel((bx, yy + 2)), skin):
                        im.putpixel((bx, yy + 2), DEEP)
                    break
        # a drip off the chin, swelling and dropping
        chin = [(16, 29), (16, 30), (16, 31)]
        for i, (x, y) in enumerate(chin[:f + 1]):
            if is_skin(im.getpixel((x, y)), skin):
                im.putpixel((x, y), DEEP if i == f else WATER)
        # frame 3: blinking water out of one eye (the lower lid row of the right eye closes over the iris)
        if f == 2:
            for y in range(13, 21):
                row = [im.getpixel((x, y)) for x in range(18, 28)]
                if any(p[3] > 0 and p[0] > 150 and p[1] < 90 and p[2] < 90 for p in row) or \
                        any(p[3] > 0 and p[0] > 180 and 90 < p[1] < 170 and p[2] < 80 for p in row):   # red / amber iris
                    for x in range(18, 28):
                        p = im.getpixel((x, y))
                        if not is_skin(p, skin):
                            im.putpixel((x, y), darker(skin, 0.75) if y > 16 else p)
        name = f'{char}_face_rain' + ('' if f == 0 else str(f + 1)) + '.png'
        im.save(D + name)
        print('wrote', name)


for c in sys.argv[1:] or ['mikiko', 'rhalix']:
    make(c)
