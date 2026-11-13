"""Renders a Bedrock geometry (YSM main.json + its texture) to preview pngs: front, 3/4, side, back.

python3 -I tools/xw/ysm/geo_preview.py <main.json> <texture.png> <out.png> [pose]

Same conventions as GeckoLib / YSM: bone rotations about their pivots (file space Rz(-z) Ry(y) Rx(-x)), box UV and
per-face UV laid out like Blockbench. pose: "rest" (default) or "fight" (arms and a leg moved, to check the joints).
"""
import json, math, sys
import numpy as np
from PIL import Image


def rot(rx, ry, rz):
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(-rx), math.sin(-rx), math.cos(ry), math.sin(ry), math.cos(-rz), math.sin(-rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def faces_of(c):
    (x0, y0, z0), (w, h, d) = c["origin"], c["size"]
    inf = c.get("inflate", 0)
    x0, y0, z0 = x0 - inf, y0 - inf, z0 - inf
    x1, y1, z1 = x0 + w + 2 * inf, y0 + h + 2 * inf, z0 + d + 2 * inf
    corners = {   # TL, TR, BR, BL seen from outside
        "north": [(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)],
        "south": [(x1, y1, z1), (x0, y1, z1), (x0, y0, z1), (x1, y0, z1)],
        "east": [(x0, y1, z1), (x0, y1, z0), (x0, y0, z0), (x0, y0, z1)],
        "west": [(x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)],
        "up": [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)],
        "down": [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
    }
    uv = c["uv"]
    w, h, d = c["size"]
    if isinstance(uv, list):
        u, v = uv
        rects = {"east": (u, v + d, d, h), "north": (u + d, v + d, w, h), "west": (u + d + w, v + d, d, h),
                 "south": (u + d + w + d, v + d, w, h), "up": (u + d, v, w, d), "down": (u + d + w, v + d, w, -d)}
    else:
        rects = {k: (f["uv"][0], f["uv"][1], f["uv_size"][0], f["uv_size"][1]) for k, f in uv.items()}
    return [(corners[k], r) for k, r in rects.items()]


def render(geo, tex, scale_px, pose=None, yaw=0.0, size=(520, 900)):
    g = geo["minecraft:geometry"][0]
    tw, th = g["description"]["texture_width"], g["description"]["texture_height"]
    T = np.asarray(tex.convert("RGBA")).astype(np.float32)
    ppu = T.shape[1] / tw
    bones = {b["name"]: b for b in g["bones"]}
    mats = {}

    def mat(name):
        if name in mats: return mats[name]
        b = bones[name]
        r = list(b.get("rotation", [0, 0, 0]))
        if pose and name in pose: r = [r[i] + pose[name][i] for i in range(3)]
        p = np.array(b["pivot"], float)
        R = rot(*r)
        M = np.eye(4); M[:3, :3] = R; M[:3, 3] = p - R @ p
        if b.get("parent") in bones: M = mat(b["parent"]) @ M
        mats[name] = M
        return M

    W, H = size
    img = np.zeros((H, W, 3), np.float32); img[:] = (235, 238, 245)
    zb = np.full((H, W), 1e9, np.float32)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    for b in g["bones"]:
        M = mat(b["name"])
        for c in b.get("cubes", []):
            for quad, (u, v, uw, vh) in faces_of(c):
                if abs(uw) < 1e-6 or abs(vh) < 1e-6: continue
                P = []
                for q in quad:
                    x, y, z = (M @ np.array([*q, 1.0]))[:3]
                    # view: screen x = file x for the front, turned by yaw about the body
                    X = x * cy - z * sy; Z = x * sy + z * cy
                    P.append((W / 2 + X * scale_px, H - 30 - y * scale_px, Z))
                UV = [(u, v), (u + uw, v), (u + uw, v + vh), (u, v + vh)]
                for tri in ((0, 1, 2), (0, 2, 3)):
                    raster(img, zb, T, ppu, [P[i] for i in tri], [UV[i] for i in tri])
    return Image.fromarray(img.clip(0, 255).astype(np.uint8))


def raster(img, zb, T, ppu, P, UV):
    (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = P
    den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
    if abs(den) < 1e-9: return
    xmin, xmax = int(max(0, math.floor(min(x0, x1, x2)))), int(min(img.shape[1] - 1, math.ceil(max(x0, x1, x2))))
    ymin, ymax = int(max(0, math.floor(min(y0, y1, y2)))), int(min(img.shape[0] - 1, math.ceil(max(y0, y1, y2))))
    if xmin > xmax or ymin > ymax: return
    xs, ys = np.meshgrid(np.arange(xmin, xmax + 1) + 0.5, np.arange(ymin, ymax + 1) + 0.5)
    a = ((y1 - y2) * (xs - x2) + (x2 - x1) * (ys - y2)) / den
    b = ((y2 - y0) * (xs - x2) + (x0 - x2) * (ys - y2)) / den
    c = 1 - a - b
    m = (a >= -1e-4) & (b >= -1e-4) & (c >= -1e-4)
    if not m.any(): return
    z = a * z0 + b * z1 + c * z2
    u = (a * UV[0][0] + b * UV[1][0] + c * UV[2][0]) * ppu
    v = (a * UV[0][1] + b * UV[1][1] + c * UV[2][1]) * ppu
    ui = np.clip(np.floor(u - 1e-3 * np.sign(u - u.mean())).astype(int), 0, T.shape[1] - 1)
    vi = np.clip(np.floor(v - 1e-3 * np.sign(v - v.mean())).astype(int), 0, T.shape[0] - 1)
    col = T[vi, ui]
    sub = zb[ymin:ymax + 1, xmin:xmax + 1]
    m &= (col[..., 3] > 127) & (z < sub)
    sub[m] = z[m]
    # simple shading by the face's facing
    n = np.cross(np.subtract(P[1], P[0]), np.subtract(P[2], P[0]))
    shade = 0.75 + 0.25 * abs(n[2]) / (np.linalg.norm(n) + 1e-9)
    img[ymin:ymax + 1, xmin:xmax + 1][m] = col[..., :3][m] * shade


if __name__ == "__main__":
    geo = json.load(open(sys.argv[1])); tex = Image.open(sys.argv[2])
    pose = None
    if len(sys.argv) > 4 and sys.argv[4] == "fight":
        pose = {"RightArm": [80, 10, 0], "RightForeArm": [40, 0, 0], "LeftArm": [-30, 0, -20], "LeftForeArm": [70, 0, 0],
                "LeftLeg": [35, 0, 0], "LeftLowerLeg": [-50, 0, 0], "Head": [0, 20, 0]}
    views = [render(geo, tex, 24, pose, yaw) for yaw in (0, 35, 90, 180)]
    out = Image.new("RGB", (sum(v.width for v in views), views[0].height), "white")
    x = 0
    for v in views: out.paste(v, (x, 0)); x += v.width
    out.save(sys.argv[3])
    print("ok", out.size)
