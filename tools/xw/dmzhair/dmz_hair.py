"""Hair built the DragonMineZ way: strands of shrinking cubes rooted on a grid over the head (FRONT 4, BACK/LEFT/RIGHT/TOP 4x4),
each with a direction, a per-segment curve, cube size, length and colour - the same maths as DMZ's HairRenderer
(cube i is 0.85^i the size of the one before, each segment turns by the curve). The strands are then baked into our own
detail model (one node per cube), so the hair works without DMZ installed.
Space: Blockbench-style, y up, front -z, +x = the character's right; head pivot (0, 24, 0); DMZ grid y is +24."""
import math, json, sys
import numpy as np

DECAY = 0.85
GRID = [-3.0, -1.0, 1.0, 3.0]
YOFF = [0.0, -1.5, -3.0, -4.5]

def base_pos(face, i):
    row, col = divmod(i, 4)
    gx, gz, yo = GRID[col], GRID[row], YOFF[row]
    return {'front': (gx, 7.25, -4.0), 'back': (gx, 7.25 + yo, 4.0), 'left': (-3.95, 7.25 + yo, gx),
            'right': (3.95, 7.25 + yo, -gx), 'top': (gx, 7.85, gz)}[face]

def Rx(a):
    a = math.radians(a); c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
def Ry(a):
    a = math.radians(a); c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
def Rz(a):
    a = math.radians(a); c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])

def rot_for(d):
    """DMZ strand rotation (x, z degrees) that points the strand's +y along direction d."""
    d = np.array(d, float); d /= np.linalg.norm(d)
    c = math.degrees(math.asin(max(-1, min(1, -d[0]))))
    cc = math.cos(math.radians(c))
    a = math.degrees(math.atan2(d[2], d[1])) if cc > 1e-6 else 0.0
    return a, c

class Strand:
    def __init__(self, face, i, direction, length, w=2.0, h=2.0, d=2.0, curve=(0, 0, 0), stretch=1.0, colors=('#7a2ab0',), twist=0.0):
        self.face, self.i, self.dir, self.length = face, i, direction, length
        self.w, self.h, self.d, self.curve, self.stretch, self.colors, self.twist = w, h, d, curve, stretch, colors, twist

    def cubes(self):
        """[(center_base(3), R(3x3), w, h, d, overlap, colour)] - each cube's base point, orientation and size."""
        p = np.array(base_pos(self.face, self.i), float) + np.array([0, 24, 0])
        a, c = rot_for(self.dir)
        R = Rx(a) @ Ry(self.twist) @ Rz(c)
        out, size, prev_h = [], 1.0, 0.0
        for k in range(self.length):
            w, h, d = self.w * size, self.h * size * self.stretch, self.d * size
            ov = 0.0
            if k > 0:
                p = p + R @ np.array([0, prev_h, 0])
                R = R @ Rx(self.curve[0]) @ Ry(self.curve[1]) @ Rz(self.curve[2])
                ov = w / 2 * abs(math.sin(math.radians(self.curve[2]))) + d / 2 * abs(math.sin(math.radians(self.curve[0]))) + 0.015 * 16
            col = self.colors[min(len(self.colors) - 1, int(k / max(1, self.length) * len(self.colors)))]
            out.append((p.copy(), R.copy(), w, h, d, ov, col))
            prev_h = h; size *= DECAY
        return out

# ------------------------------------------------------------------ baking into our detail model
S = np.diag([-1.0, -1.0, 1.0])            # Blockbench -> Minecraft model space (x and y flipped)

def zyx(R):
    """Euler angles (x, y, z radians) so that R = Rz(z) Ry(y) Rx(x) (Minecraft's ModelPart order)."""
    sy = -R[2, 0]
    y = math.asin(max(-1, min(1, sy)))
    if abs(math.cos(y)) > 1e-6:
        x = math.atan2(R[2, 1], R[2, 2]); z = math.atan2(R[1, 0], R[0, 0])
    else:
        x = math.atan2(-R[1, 2], R[1, 1]); z = 0.0
    return x, y, z

def bake(strands, swatches, name='dmzhair'):
    """Head child nodes for the strands. `swatches`: colour -> (u, v) of a solid 16x16 block on the atlas."""
    nodes = []
    for si, s in enumerate(strands):
        for k, (p, R, w, h, d, ov, col) in enumerate(s.cubes()):
            Rm = S @ R @ S
            x, y, z = zyx(Rm)
            piv = [-(p[0]), -(p[1] - 24.0), p[2]]
            u, v = swatches[col]
            nodes.append({'name': f'{name}{si}_{k}', 'pivot': [round(c, 4) for c in piv], 'rot': [round(x, 4), round(y, 4), round(z, 4)],
                          'cubes': [{'uv': [u, v], 'from': [round(-w / 2, 4), round(-h, 4), round(-d / 2, 4)], 'size': [round(w, 4), round(h + ov, 4), round(d, 4)]}],
                          'children': []})
    return nodes
