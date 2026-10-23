import sys, json, math
sys.path.insert(0, '/home/claude/xw/outfits')
from kit import *
from PIL import Image, ImageDraw, ImageFont

def head_color(skin, face, n, p):
    """Colour of the original 8x8x8 head at surface point p (mod space) on a face with normal n."""
    x, y, z = p
    def S(u, v):
        return skin.getpixel((int(min(max(u, 0), 31.99)), int(min(max(v, 0), 15.99))))
    u = lambda a: min(max(a, 0), 7.999)
    if n == (0, 0, -1):
        fu, fv = u(x + 4), u(y + 8)
        fc = face.getpixel((int(fu * 4), int(fv * 4)))
        return fc[:3] if fc[3] > 0 else S(8 + fu, 8 + fv)[:3]
    if n == (0, 0, 1):  return S(24 + u(4 - x), 8 + u(y + 8))[:3]
    if n == (-1, 0, 0): return S(0 + u(z + 4), 8 + u(y + 8))[:3]   # his right side
    if n == (1, 0, 0):  return S(16 + u(4 - z), 8 + u(y + 8))[:3]
    if n == (0, -1, 0): return S(8 + u(x + 4), 0 + u(z + 4))[:3]
    return S(16 + u(x + 4), 0 + u(z + 4))[:3]

def head_quads(boxes, skin, face, M, T, res=4):
    out = []
    light = np.array([-0.3, -0.8, -0.5]); light /= np.linalg.norm(light)
    for (x0, y0, z0, x1, y1, z1) in boxes:
        F = [((0, 0, -1), lambda a, b: (x0 + (x1 - x0) * a, y0 + (y1 - y0) * b, z0)),
             ((0, 0, 1), lambda a, b: (x1 - (x1 - x0) * a, y0 + (y1 - y0) * b, z1)),
             ((-1, 0, 0), lambda a, b: (x0, y0 + (y1 - y0) * b, z1 - (z1 - z0) * a)),
             ((1, 0, 0), lambda a, b: (x1, y0 + (y1 - y0) * b, z0 + (z1 - z0) * a)),
             ((0, -1, 0), lambda a, b: (x0 + (x1 - x0) * a, y0, z1 - (z1 - z0) * b)),
             ((0, 1, 0), lambda a, b: (x0 + (x1 - x0) * a, y1, z0 + (z1 - z0) * b))]
        for nrm, fn in F:
            nn = M @ np.array(nrm, float)
            if nn[2] > 0.01: continue
            sh = 0.6 + 0.4 * max(0, nn @ light)
            w = {(0, 0, -1): x1 - x0, (0, 0, 1): x1 - x0, (-1, 0, 0): z1 - z0, (1, 0, 0): z1 - z0}.get(nrm, x1 - x0)
            h = {(0, -1, 0): z1 - z0, (0, 1, 0): z1 - z0}.get(nrm, y1 - y0)
            nx, ny = max(1, int(round(w * res))), max(1, int(round(h * res)))
            for j in range(ny):
                for i in range(nx):
                    cen = fn((i + .5) / nx, (j + .5) / ny)
                    col = head_color(skin, face, nrm, cen)
                    pts = [M @ np.array(fn(a / nx, b / ny), float) + T for a, b in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1))]
                    out.append((np.mean([q[2] for q in pts]), [(q[0], q[1]) for q in pts], mul(col, sh)))
    return out

SHAPES = {
    'A  Classic cube (now)': ([(-4, -8, -4, 4, 0, 4)], 1.0),
    'B  Rounded corners': ([(-4, -7.4, -3.5, 4, -0.6, 3.5), (-3.5, -7.4, -4, 3.5, -0.6, 4), (-3.4, -8, -3.4, 3.4, -7.4, 3.4),
                           (-3.5, -0.6, -3.5, 3.5, 0, 3.5)], 1.0),
    'C  Anime jaw (tapered chin)': ([(-4, -8, -4, 4, -2.2, 4), (-3.6, -2.2, -4, 3.6, -1.1, 3.6), (-2.6, -1.1, -3.7, 2.6, 0, 3.0)], 1.0),
    'D  Chibi (bigger head)': ([(-4, -8, -4, 4, 0, 4)], 1.22),
    'E  Oval (taller, slimmer)': ([(-3.7, -8.4, -3.9, 3.7, 0, 3.9), (-3.2, -8.9, -3.4, 3.2, -8.4, 3.4)], 1.0),
    'F  Soft cheeks + round top': ([(-4, -7.5, -4, 4, 0, 4), (-3.5, -8, -3.5, 3.5, -7.5, 3.5), (-4.35, -3.2, -3.6, -4, -0.8, 1.5),
                                   (4, -3.2, -3.6, 4.35, -0.8, 1.5)], 1.0),
}

def render(char, label, boxes, scale, yaw):
    skin = Image.open(CHAR + f'{char}.png').convert('RGBA')
    face = Image.open(CHAR + f'{char}_face.png').convert('RGBA')
    det = Image.open(CHAR + f'{char}_details.png').convert('RGBA')
    mj = json.load(open(RES + f'models/detail/{char}.json'))
    V = Rzyx([0.12, math.radians(yaw), 0]) * scale
    quads = head_quads(boxes, skin, face, V, np.zeros(3))
    def walk(nd, M, T):
        out = []
        for c in nd['cubes']: out += box_quads(c['from'], c['size'], c['uv'], det, M, T)
        for ch in nd.get('children', []):
            M2 = M @ Rzyx(ch.get('rot', [0, 0, 0])); T2 = M @ np.array(ch['pivot'], float) + T
            out += walk(ch, M2, T2)
        return out
    quads += walk(mj['bones']['head'], V, np.zeros(3))
    quads.sort(key=lambda q: -q[0])
    S = 13; W, H = 15 * S, 15 * S
    img = Image.new('RGB', (W, H), (40, 44, 52)); dr = ImageDraw.Draw(img)
    for _, pts, col in quads:
        dr.polygon([(W / 2 + x * S, H * 0.62 + (y + 3.5) * S) for x, y in pts], fill=col)
    return img

font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 15)
cols = list(SHAPES.items())
cw, ch = 2 * 195 + 10, 195
sheet = Image.new('RGB', (3 * (cw + 16) + 16, 2 * (2 * ch + 46) + 10), (28, 28, 34))
dr = ImageDraw.Draw(sheet)
for i, (label, (boxes, sc)) in enumerate(cols):
    gx = 16 + (i % 3) * (cw + 16); gy = 10 + (i // 3) * (2 * ch + 46)
    dr.text((gx, gy), label, fill=(235, 235, 240), font=font)
    for r, char in enumerate(('mikiko', 'rhalix')):
        for k, yaw in enumerate((0, 35)):
            sheet.paste(render(char, label, boxes, sc, yaw), (gx + k * 200, gy + 24 + r * ch))
sheet.save('/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/outfit/head_shapes.png')
print('ok')
