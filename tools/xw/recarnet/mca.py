"""Minimal Minecraft region (.mca) + NBT reader for 1.18+ chunks: blocks(x,y,z) -> name."""
import struct, zlib, io, math, os, glob

def _nbt(f, t):
    if t == 1: return struct.unpack('>b', f.read(1))[0]
    if t == 2: return struct.unpack('>h', f.read(2))[0]
    if t == 3: return struct.unpack('>i', f.read(4))[0]
    if t == 4: return struct.unpack('>q', f.read(8))[0]
    if t == 5: return struct.unpack('>f', f.read(4))[0]
    if t == 6: return struct.unpack('>d', f.read(8))[0]
    if t == 7: n = struct.unpack('>i', f.read(4))[0]; return f.read(n)
    if t == 8: n = struct.unpack('>H', f.read(2))[0]; return f.read(n).decode('utf-8', 'replace')
    if t == 9:
        et = f.read(1)[0]; n = struct.unpack('>i', f.read(4))[0]
        return [_nbt(f, et) for _ in range(n)]
    if t == 10:
        d = {}
        while True:
            tt = f.read(1)[0]
            if tt == 0: return d
            nl = struct.unpack('>H', f.read(2))[0]; name = f.read(nl).decode('utf-8', 'replace')
            d[name] = _nbt(f, tt)
    if t == 11: n = struct.unpack('>i', f.read(4))[0]; return list(struct.unpack('>%di' % n, f.read(4 * n)))
    if t == 12: n = struct.unpack('>i', f.read(4))[0]; return list(struct.unpack('>%dq' % n, f.read(8 * n)))
    raise ValueError(t)

def read_nbt(b):
    f = io.BytesIO(b); t = f.read(1)[0]; nl = struct.unpack('>H', f.read(2))[0]; f.read(nl)
    return _nbt(f, t)

class World:
    def __init__(self, region_dir):
        self.dir = region_dir; self.chunks = {}
    def chunk(self, cx, cz):
        k = (cx, cz)
        if k in self.chunks: return self.chunks[k]
        rx, rz = cx >> 5, cz >> 5
        p = os.path.join(self.dir, f'r.{rx}.{rz}.mca')
        res = None
        if os.path.exists(p):
            with open(p, 'rb') as fh:
                hdr = fh.read(4096); i = 4 * ((cx & 31) + (cz & 31) * 32)
                off = (hdr[i] << 16) | (hdr[i + 1] << 8) | hdr[i + 2]
                if off:
                    fh.seek(off * 4096); ln = struct.unpack('>i', fh.read(4))[0]; comp = fh.read(1)[0]
                    data = fh.read(ln - 1)
                    raw = zlib.decompress(data) if comp == 2 else data
                    root = read_nbt(raw)
                    if root.get('Status', '').endswith('full'):
                        secs = {}
                        for s in root.get('sections', []):
                            bs = s.get('block_states')
                            if not bs: continue
                            pal = [e['Name'] for e in bs['palette']]
                            secs[s['Y']] = (pal, bs.get('data'))
                        res = secs
        self.chunks[k] = res
        return res
    def block(self, x, y, z):
        c = self.chunk(x >> 4, z >> 4)
        if c is None: return None
        s = c.get(y >> 4)
        if s is None: return 'minecraft:air'
        pal, data = s
        if data is None or len(pal) == 1: return pal[0]
        bits = max(4, math.ceil(math.log2(len(pal)))); per = 64 // bits
        idx = ((y & 15) * 256) + ((z & 15) * 16) + (x & 15)
        v = data[idx // per] & ((1 << 64) - 1)
        return pal[(v >> ((idx % per) * bits)) & ((1 << bits) - 1)]
