"""Writes the Music Box village building (structure .nbt): a little hall with a jukebox, a chest of every Xeno
Warriors disc, note blocks, banners of the characters' colours, lanterns, and a jigsaw at the door for villages."""
import gzip, struct, json, os

OUT = '/home/claude/xeno-warriors-mod/src/main/resources/data/xenowarriors/structures/village/music_box.nbt'

# ---------------- tiny NBT writer
def s(v): b = v.encode('utf-8'); return struct.pack('>H', len(b)) + b
class Int(int): pass
class Str(str): pass
def payload(v):
    if isinstance(v, bool): return 1, struct.pack('>b', 1 if v else 0)
    if isinstance(v, Int) or isinstance(v, int): return 3, struct.pack('>i', v)
    if isinstance(v, float): return 5, struct.pack('>f', v)
    if isinstance(v, str): return 8, s(v)
    if isinstance(v, list):
        if not v: return 9, struct.pack('>bi', 0, 0)
        tid = payload(v[0])[0]
        return 9, struct.pack('>bi', tid, len(v)) + b''.join(payload(x)[1] for x in v)
    if isinstance(v, dict):
        out = b''
        for k, x in v.items():
            tid, p = payload(x)
            out += struct.pack('>b', tid) + s(k) + p
        return 10, out + b'\x00'
    raise TypeError(v)
def write(path, root):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb') as f:
        f.write(b'\x0a' + s('') + payload(root)[1])

# ---------------- the building (x 0..8, y 0..6, z 0..8); door on the north side (z = 0)
W, H, D = 9, 7, 9
palette, index, blocks = [], {}, []
def st(name, **props):
    key = (name, tuple(sorted(props.items())))
    if key not in index:
        index[key] = len(palette)
        e = {'Name': name}
        if props: e['Properties'] = {k: str(v) for k, v in props.items()}
        palette.append(e)
    return index[key]
grid = {}
def put(x, y, z, name, nbt=None, **props):
    grid[(x, y, z)] = (st(name, **props), nbt)

for x in range(W):
    for z in range(D):
        put(x, 0, z, 'minecraft:stone_bricks' if x in (0, W - 1) or z in (0, D - 1) else 'minecraft:spruce_planks')
        for y in range(1, 5):
            edge = x in (0, W - 1) or z in (0, D - 1)
            corner = x in (0, W - 1) and z in (0, D - 1)
            if corner: put(x, y, z, 'minecraft:stripped_dark_oak_log', axis='y')
            elif edge:
                window = y in (2, 3) and ((x in (2, 6) and z in (0, D - 1)) or (z in (3, 5) and x in (0, W - 1)))
                put(x, y, z, 'minecraft:glass_pane' if window else ('minecraft:dark_oak_planks' if y in (1, 4) else 'minecraft:spruce_planks'))
            else:
                put(x, y, z, 'minecraft:air')
        # roof: stepped dark oak
        put(x, 5, z, 'minecraft:dark_oak_planks' if x in (0, W - 1) or z in (0, D - 1) else 'minecraft:dark_oak_slab', type='bottom')
        put(x, 6, z, 'minecraft:air')
for x in range(2, W - 2):
    for z in range(2, D - 2):
        put(x, 6, z, 'minecraft:dark_oak_slab', type='bottom')
# glass panes need connections: let the game fix them (they update on placement) - fine
# door + jigsaw underneath the doorway
put(4, 1, 0, 'minecraft:dark_oak_door', facing='north', half='lower', hinge='left', open='false', powered='false')
put(4, 2, 0, 'minecraft:dark_oak_door', facing='north', half='upper', hinge='left', open='false', powered='false')
put(4, 0, 0, 'minecraft:jigsaw', {'id': 'minecraft:jigsaw', 'name': 'minecraft:building_entrance', 'target': 'minecraft:empty',
                                  'pool': 'minecraft:empty', 'joint': 'rollable', 'final_state': 'minecraft:stone_bricks'},
    orientation='north_up')
# carpet: red / orange / purple stripes (Mikiko and Rhalix)
for x in range(1, W - 1):
    for z in range(1, D - 1):
        c = ['red', 'orange', 'purple'][(x + z) % 3] if 2 <= x <= 6 and 2 <= z <= 6 else None
        if c: put(x, 1, z, f'minecraft:{c}_carpet')
# the stage at the back: jukebox in the middle, the chest of discs next to it, note blocks either side
put(4, 1, 7, 'minecraft:jukebox', {'id': 'minecraft:jukebox'}, has_record='false')
put(3, 1, 7, 'minecraft:chest', {'id': 'minecraft:chest', 'LootTable': 'xenowarriors:chests/music_box'}, facing='north', type='single', waterlogged='false')
put(5, 1, 7, 'minecraft:note_block', instrument='harp', note='12', powered='false')
put(2, 1, 7, 'minecraft:note_block', instrument='harp', note='7', powered='false')
put(6, 1, 7, 'minecraft:note_block', instrument='harp', note='19', powered='false')
put(1, 1, 7, 'minecraft:bookshelf'); put(7, 1, 7, 'minecraft:bookshelf')
put(1, 2, 7, 'minecraft:bookshelf'); put(7, 2, 7, 'minecraft:bookshelf')
# lanterns + banners
for (x, z) in ((2, 2), (6, 2), (2, 6), (6, 6), (4, 4)):
    put(x, 4, z, 'minecraft:lantern', hanging='true', waterlogged='false')
put(3, 3, 7, 'minecraft:red_wall_banner', {'id': 'minecraft:banner'}, facing='north')
put(5, 3, 7, 'minecraft:purple_wall_banner', {'id': 'minecraft:banner'}, facing='north')
put(4, 3, 7, 'minecraft:orange_wall_banner', {'id': 'minecraft:banner'}, facing='north')
# benches to sit and listen
for x in (2, 6):
    put(x, 1, 3, 'minecraft:spruce_stairs', facing='south', half='bottom', shape='straight', waterlogged='false')
    put(x, 1, 4, 'minecraft:spruce_stairs', facing='south', half='bottom', shape='straight', waterlogged='false')
# flower pots by the door outside? keep inside the box: a pot each side of the entrance
put(1, 1, 1, 'minecraft:potted_poppy'); put(7, 1, 1, 'minecraft:potted_blue_orchid')

for (x, y, z), (state, nbt) in sorted(grid.items()):
    b = {'pos': [Int(x), Int(y), Int(z)], 'state': Int(state)}
    if nbt: b['nbt'] = nbt
    blocks.append(b)
write(OUT, {'DataVersion': Int(3465), 'size': [Int(W), Int(H), Int(D)], 'palette': palette, 'blocks': blocks, 'entities': []})
print('music box written', len(blocks), 'blocks', len(palette), 'states')
