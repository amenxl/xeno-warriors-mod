"""Material textures (16x16, patterned like the in-house heku_gun_* / wire_gun_* ones) for the new 3D tool / weapon models.
usage: python3 -I gen_textures.py <assets/xenowarriors dir>"""
import os, sys, random
from PIL import Image

OUT = os.path.join(sys.argv[1], 'textures', 'item', '3d')
os.makedirs(OUT, exist_ok=True)


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def save(name, f):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), f(x, y))
    im.save(os.path.join(OUT, name + '.png'))


def diag_shade(c0, c1, c2, seed, stripe=4):
    """Soft diagonal sheen: base c0, light streaks c1 every `stripe`, dark specks c2 (metal / crystal)."""
    r = random.Random(seed)
    noise = [[r.random() for _ in range(16)] for _ in range(16)]
    def f(x, y):
        d = (x + y) % (stripe * 2)
        c = mix(c0, c1, 0.55) if d == 0 else mix(c0, c1, 0.25) if d == 1 else c0
        n = noise[y][x]
        if n < 0.08: c = mix(c, c2, 0.5)
        elif n > 0.94: c = mix(c, c1, 0.35)
        return c
    return f


def stripes_h(c0, c1, period=3, width=1):
    return lambda x, y: c1 if y % period < width else c0


def wrap(c0, c1, c2):
    """Handle wrap: diagonal binding bands with a dark gap between them."""
    def f(x, y):
        d = (x - y) % 6
        return c1 if d in (0, 1) else c2 if d == 2 else c0
    return f


def wood(c0, c1, seed):
    r = random.Random(seed)
    cols = [r.random() for _ in range(16)]
    def f(x, y):
        g = cols[x]
        c = mix(c0, c1, 0.35 if g > 0.7 else 0.0)
        if (y + int(g * 7)) % 7 == 0: c = mix(c, (0, 0, 0, 255), 0.18)
        return c
    return f


def veins(c0, c1, c2, seed):
    """Corruption: dark base, glowing cracks."""
    r = random.Random(seed)
    grid = [[0] * 16 for _ in range(16)]
    for _ in range(5):
        x, y = r.randrange(16), r.randrange(16)
        for _ in range(9):
            grid[y % 16][x % 16] = 1
            x += r.choice((-1, 0, 1)); y += 1
    def f(x, y):
        if grid[y][x]: return c1
        if (x + y) % 5 == 0: return mix(c0, c2, 0.4)
        return c0
    return f


def glow_core(c0, c1):
    """Emissive core: bright middle line, deeper edges."""
    return lambda x, y: c1 if 6 <= y <= 9 else mix(c0, c1, 0.4) if y in (4, 5, 10, 11) else c0


def tech_panel(c0, c1, c2):
    """Panel lines + rivets (DMZ guns)."""
    def f(x, y):
        if x % 8 == 0 or y % 8 == 0: return c2
        if (x % 8, y % 8) in ((2, 2), (6, 6)): return c1
        if (x + y) % 9 == 0: return mix(c0, c1, 0.3)
        return c0
    return f


# --- Sky (light blue crystal, silver, tan handle)
save('sky_crystal', diag_shade(hx('6ebef5'), hx('d2f5ff'), hx('28568c'), 1, 3))
save('sky_edge', diag_shade(hx('d2f5ff'), hx('ffffff'), hx('6ebef5'), 2, 3))
save('sky_handle', wood(hx('96783c'), hx('b8975a'), 3))
save('sky_metal', diag_shade(hx('b8c2d2'), hx('e1e6f0'), hx('28568c'), 4, 4))
save('sky_dark', diag_shade(hx('28568c'), hx('4c86c0'), hx('163250'), 5, 4))
save('sky_glow', glow_core(hx('4fd7ff'), hx('e8fdff')))
# --- Corrupted (black-purple, violet, lilac glow)
save('corrupted_head', veins(hx('4a1a6e'), hx('cd78ff'), hx('7828aa'), 6))
save('corrupted_dark', veins(hx('1e0c28'), hx('5a1f86'), hx('281a22'), 7))
save('corrupted_edge', diag_shade(hx('9b4ad6'), hx('e4b6ff'), hx('4a1a6e'), 8, 3))
save('corrupted_bone', wrap(hx('46323c'), hx('6a525e'), hx('1e0c28')))
save('corrupted_glow', glow_core(hx('b03cff'), hx('f3d2ff')))
# --- Demonite (deep red, hot edge, black-brown grip)
save('demonite_blade', veins(hx('8a0c14'), hx('ff5a4e'), hx('c8141e'), 9))
save('demonite_edge', diag_shade(hx('ff786e'), hx('ffd2c8'), hx('c8141e'), 10, 3))
save('demonite_dark', diag_shade(hx('281818'), hx('50281e'), hx('100808'), 11, 4))
save('demonite_grip', wrap(hx('3a1c16'), hx('6e0a10'), hx('140a0a')))
save('demonite_glow', glow_core(hx('ff3a1e'), hx('ffd27a')))
# --- DragonMineZ guns (gunmetal, tech panels, per-gun glow)
save('dmz_gun_steel', tech_panel(hx('4a505e'), hx('8c93a3'), hx('2c3038')))
save('dmz_gun_dark', diag_shade(hx('23262e'), hx('3a3f4c'), hx('121418'), 12, 4))
save('dmz_gun_grip', wrap(hx('3a2a20'), hx('5a4232'), hx('1c140e')))
save('dmz_glow_cyan', glow_core(hx('0b8cd6'), hx('c8f8ff')))
save('dmz_glow_green', glow_core(hx('2e9a0a'), hx('dcffc0')))
save('dmz_glow_orange', glow_core(hx('d25a00'), hx('ffe0a0')))
print('textures ->', OUT, sorted(os.listdir(OUT)))
