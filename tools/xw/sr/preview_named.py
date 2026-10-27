import sys, json, copy
sys.path.insert(0, '/home/claude/xw/sr')
exec(open('/home/claude/xw/sr/sr_named.py').read().split("if __name__")[0])
from PIL import Image, ImageDraw
def tinted(img, t):
    out = img.copy(); p = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = p[x, y]
            if a: p[x, y] = (int(r * t[0]), int(g * t[1]), int(b * t[2]), a)
    return out
def render(key, yaws=(20, 200), scale=6, wings=True, wing_rot=None):
    spec = SPECS[key]
    mj = json.load(open(MOD + key + '.json'))
    det = Image.open(TEX + key + '_details.png').convert('RGBA')
    W = det.width
    if wings and spec.get('wing'):
        style, t = spec['wing']
        wj = json.load(open(MOD + 'wing_' + style + '.json'))
        wimg = tinted(Image.open(TEX + 'wing_' + style + '_details.png').convert('RGBA'), t)
        big = Image.new('RGBA', (W + wimg.width, max(det.height, wimg.height))); big.paste(det, (0, 0)); big.paste(wimg, (W, 0)); det = big
        def shift(n):
            for c in n.get('cubes', []): c['uv'] = [c['uv'][0] + W, c['uv'][1]]
            for ch in n.get('children', []): shift(ch)
        for ch in wj['bones']['body']['children']:
            shift(ch)
            if wing_rot: ch['rot'] = [a + b for a, b in zip(ch.get('rot', [0, 0, 0]), wing_rot(ch['name']))]
            mj['bones']['body'].setdefault('children', []).append(ch)
    return U.preview(mj, Image.open(TEX + key + '.png'), det, Image.open(TEX + key + '_face.png'), yaws=yaws, scale=scale, wide=46, tall=44, top=9)
if __name__ == '__main__':
    keys = sys.argv[1:] or list(SPECS)
    tiles = []
    for k in keys:
        im = render(k); ImageDraw.Draw(im).text((6, 6), SPECS[k]['name'], fill=(255, 255, 255)); tiles.append(im)
    tw, th = tiles[0].size; cols = 2 if len(tiles) > 3 else len(tiles)
    s = Image.new('RGB', (tw * cols, th * ((len(tiles) + cols - 1) // cols)), (40, 44, 52))
    for i, t in enumerate(tiles): s.paste(t, ((i % cols) * tw, (i // cols) * th))
    s.save('/tmp/sr_named_wings.png'); print(s.size)
