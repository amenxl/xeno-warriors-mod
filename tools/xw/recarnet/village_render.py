import sys; sys.path.insert(0, '/home/claude/xw/recarnet')
import render as R, numpy as np
from mca import World
from PIL import Image
w = World('/tmp/st/w/region')
def topdown_at(ccx, ccz, r, out):
    n = (2 * r + 1) * 16; img = np.zeros((n, n, 3), np.uint8); img[:] = (30, 34, 40)
    for cx in range(ccx - r, ccx + r + 1):
        for cz in range(ccz - r, ccz + r + 1):
            d = R.dense(w, cx, cz)
            if d is None: continue
            alpha = d[..., 3] > 0; has = alpha.any(0)
            top = (R.Y1 - R.Y0 - 1) - np.argmax(alpha[::-1], axis=0)
            zz, xx = np.indices((16, 16)); cols = d[top, zz, xx, :3].astype(float)
            shade = 0.55 + 0.45 * (top / (R.Y1 - R.Y0 - 1)); cols = (cols * shade[..., None]).clip(0, 255).astype(np.uint8); cols[~has] = (30, 34, 40)
            ox, oz = (cx - ccx + r) * 16, (cz - ccz + r) * 16; img[oz:oz + 16, ox:ox + 16] = cols
    Image.fromarray(img).resize((n * 2, n * 2), Image.NEAREST).save(out)
def iso(x0, z0, size, out, scale, cut=90, roof=999):
    vol = np.zeros((R.Y1 - R.Y0, size, size, 3), np.uint8); solid = np.zeros((R.Y1 - R.Y0, size, size), bool)
    for cx in range(x0 >> 4, ((x0 + size - 1) >> 4) + 1):
        for cz in range(z0 >> 4, ((z0 + size - 1) >> 4) + 1):
            d = R.dense(w, cx, cz)
            if d is None: continue
            for lx in range(16):
                for lz in range(16):
                    X, Z = cx * 16 + lx - x0, cz * 16 + lz - z0
                    if 0 <= X < size and 0 <= Z < size: vol[:, Z, X] = d[:, lz, lx, :3]; solid[:, Z, X] = d[:, lz, lx, 3] > 0
    solid[:max(0, cut - R.Y0)] = False
    solid[max(0, roof - R.Y0):] = False
    R.iso_draw(vol, solid, size, out, scale)
if __name__ == '__main__':
    topdown_at(0, -48, 10, '/mnt/user-data/outputs/pine_cone_city_map.png')
    iso(32, -760, 64, '/tmp/v_farm.png', 6)
    iso(-66, -762, 34, '/tmp/v_dojo.png', 9, roof=99)
    iso(-34, -826, 68, '/tmp/v_school.png', 6)
    iso(-34, -826, 68, '/tmp/v_school_in.png', 6, roof=98)
    iso(34, -758, 18, '/tmp/v_store_in.png', 12, roof=101)
    iso(-40, -800, 80, '/tmp/v_center.png', 5)
