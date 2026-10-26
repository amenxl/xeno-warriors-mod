package com.xenowarriors.recarnet;

import com.xenowarriors.world.Clip;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Aurora's named places from Freedom Fighters Recarnet:
 * <ul>
 *   <li><b>Gale's Hotel</b> - a fancy house on the outside, a big luxury hotel inside (the floors go down underground),
 *   with Gale's own house on the roof and the elevator shaft that got hacked.</li>
 *   <li><b>Yuri's HQ</b> - underground under the outskirts: a computer lab and the VR room, reached through the
 *   "sky ground" hatch.</li>
 * </ul>
 * Built chunk by chunk (each chunk draws its own slice, via {@link Clip}).
 */
public final class AuroraLandmarks {
    /** Gale's Hotel stands on this city block. */
    public static final int HOTEL_CX = 1, HOTEL_CZ = -2;
    /** Yuri's HQ: underground just outside the wall. */
    public static final int YURI_X = -160, YURI_Z = 160, HATCH_X = -160, HATCH_Z = 146;
    private static final int B = Recarnet.BASE_Y;

    private final WorldGenLevel l;
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

    private AuroraLandmarks(WorldGenLevel l) { this.l = l; }

    private static BlockState b(Block k) { return k.defaultBlockState(); }

    private void set(int x, int y, int z, BlockState s) {
        mp.set(x, y, z);
        if (Clip.ok(mp)) l.setBlock(mp, s, 2);
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) set(x, y, z, s);
    }

    private void sign(int x, int y, int z, Direction facing, String... lines) {
        mp.set(x, y, z);
        if (!Clip.ok(mp)) return;
        set(x, y, z, b(Blocks.DARK_OAK_WALL_SIGN).setValue(WallSignBlock.FACING, facing));
        if (l.getBlockEntity(mp.set(x, y, z)) instanceof SignBlockEntity s) {
            SignText t = new SignText();
            for (int i = 0; i < Math.min(4, lines.length); i++) t = t.setMessage(i, Component.literal(lines[i]));
            SignText.DIRECT_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, t).result().ifPresent(tag -> {
                net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
                nbt.put("front_text", tag);
                s.load(nbt);
            });
        }
    }

    private boolean touches(int x0, int z0, int x1, int z1, int cx0, int cz0) {
        return x1 >= cx0 && x0 <= cx0 + 15 && z1 >= cz0 && z0 <= cz0 + 15;
    }

    /** Called for every Aurora chunk after the streets are laid. */
    public static void build(WorldGenLevel level, int x0, int z0) {
        AuroraLandmarks a = new AuroraLandmarks(level);
        Clip.set(x0, z0, x0 + 15, z0 + 15);
        try {
            int hx = HOTEL_CX * AuroraCityFeature.CELL + 27, hz = HOTEL_CZ * AuroraCityFeature.CELL + 27;
            if (a.touches(hx - 20, hz - 20, hx + 20, hz + 20, x0, z0)) a.hotel(hx, hz);
            if (a.touches(YURI_X - 16, HATCH_Z - 2, YURI_X + 16, YURI_Z + 14, x0, z0)) a.yuri();
        } finally { Clip.clear(); }
    }

    // ------------------------------------------------------------------------------------------------ Gale's Hotel

    private void hotel(int cx, int cz) {
        final int H = 15;                                 // half width of the house
        int x0 = cx - H, x1 = cx + H, z0 = cz - H, z1 = cz + H;
        BlockState wall = b(Blocks.SMOOTH_QUARTZ), trim = b(Blocks.DARK_OAK_PLANKS), pillar = b(Blocks.QUARTZ_PILLAR);
        BlockState win = b(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE), floor = b(Blocks.POLISHED_DIORITE), carpet = b(Blocks.RED_CARPET);
        // front garden + gold-tipped fence
        for (int x = cx - 19; x <= cx + 19; x++) for (int z = cz - 19; z <= cz + 19; z++) {
            boolean in = x >= x0 && x <= x1 && z >= z0 && z <= z1;
            if (!in) {
                set(x, B, z, Math.abs(x - cx) <= 2 && z < z0 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.GRASS_BLOCK));
                boolean edge = Math.abs(x - cx) == 19 || Math.abs(z - cz) == 19;
                if (edge && !(Math.abs(x - cx) <= 2 && z < cz)) set(x, B + 1, z, (x + z) % 6 == 0 ? b(Blocks.GOLD_BLOCK) : b(Blocks.DARK_OAK_FENCE));
                else if (!edge && (x + z * 3) % 11 == 0 && Math.abs(x - cx) > 3) set(x, B + 1, z, b(Blocks.ROSE_BUSH));
            }
        }
        // the hotel floors underground: 3 floors of rooms off a long hall
        for (int f = 0; f < 3; f++) {
            int fy = B - 6 * (f + 1);
            fill(x0, fy - 1, z0, x1, fy + 5, z1, b(Blocks.STONE_BRICKS));
            fill(x0 + 1, fy, z0 + 1, x1 - 1, fy + 4, z1 - 1, b(Blocks.AIR));
            fill(x0 + 1, fy - 1, z0 + 1, x1 - 1, fy - 1, z1 - 1, b(Blocks.POLISHED_DIORITE));
            for (int x = x0 + 1; x <= x1 - 1; x++) for (int z = cz - 2; z <= cz + 2; z++) set(x, fy, z, carpet);
            for (int x = x0 + 1; x <= x1 - 1; x += 6) {
                for (int side = -1; side <= 1; side += 2) {
                    int zw = cz + side * 3;                                       // room wall along the hall
                    for (int z = Math.min(zw, zw + side * 11); z <= Math.max(zw, zw + side * 11); z++) for (int y = 0; y <= 4; y++) set(x, fy + y, z, b(Blocks.STRIPPED_DARK_OAK_LOG));
                    if (x + 3 >= x1) continue;
                    for (int dx = 0; dx <= 5; dx++) for (int y = 0; y <= 4; y++) set(x + dx, fy + y, zw, dx == 3 && y <= 1 ? b(Blocks.AIR) : b(Blocks.WHITE_CONCRETE));
                    Direction face = side < 0 ? Direction.SOUTH : Direction.NORTH;
                    set(x + 3, fy, zw, b(Blocks.DARK_OAK_DOOR).setValue(DoorBlock.FACING, face).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                    set(x + 3, fy + 1, zw, b(Blocks.DARK_OAK_DOOR).setValue(DoorBlock.FACING, face).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                    // inside: a bed, a lamp, a carpet
                    int bz = zw + side * 8;
                    for (int dx = 1; dx <= 5; dx++) for (int dz = 1; dz <= 10; dz++) set(x + dx, fy, zw + side * dz, b(Blocks.LIGHT_GRAY_CARPET));
                    Direction bedFace = side < 0 ? Direction.NORTH : Direction.SOUTH;
                    set(x + 2, fy, bz, b(Blocks.RED_BED).setValue(BedBlock.FACING, bedFace).setValue(BedBlock.PART, BedPart.FOOT));
                    set(x + 2, fy, bz + side, b(Blocks.RED_BED).setValue(BedBlock.FACING, bedFace).setValue(BedBlock.PART, BedPart.HEAD));
                    set(x + 4, fy, bz + side, b(Blocks.SPRUCE_PLANKS));
                    set(x + 4, fy + 1, bz + side, b(Blocks.LANTERN));
                    set(x + 3, fy + 4, bz - side * 3, b(Blocks.GLOWSTONE));
                }
            }
            for (int x = x0 + 3; x <= x1 - 3; x += 5) set(x, fy + 4, cz, b(Blocks.SEA_LANTERN));
            sign(x0 + 1, fy + 2, cz + 2, Direction.EAST, "Floor B" + (f + 1), "Gale's Hotel", "Rooms", "");
        }
        // the house above ground
        fill(x0, B, z0, x1, B, z1, floor);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
            for (int y = 1; y <= 14; y++) {
                BlockState s = b(Blocks.AIR);
                if (edge) {
                    if (corner) s = pillar;
                    else if (y == 7 || y == 14) s = trim;                          // floor bands
                    else if ((y >= 3 && y <= 5 || y >= 9 && y <= 12) && (x + z) % 4 != 0) s = win;
                    else s = wall;
                } else if (y == 7) s = (x > cx - 6 && x < cx + 6 && z > cz - 6 && z < cz + 6) ? b(Blocks.AIR) : b(Blocks.DARK_OAK_PLANKS);  // open lobby atrium
                else if (y == 14) s = b(Blocks.SMOOTH_QUARTZ);                     // roof terrace
                set(x, B + y, z, s);
            }
        }
        // grand front door + steps
        fill(cx - 1, B + 1, z0, cx + 1, B + 4, z0, b(Blocks.AIR));
        for (int x = cx - 2; x <= cx + 2; x++) set(x, B + 5, z0, b(Blocks.GOLD_BLOCK));
        sign(cx, B + 6, z0 - 1, Direction.NORTH, "", "GALE'S HOTEL", "~ Gale Empire ~", "");
        for (int x = cx - 3; x <= cx + 3; x += 6) for (int y = 1; y <= 5; y++) set(x, B + y, z0 - 1, pillar);
        // lobby: red carpet to the front desk, chandelier in the atrium, couches
        for (int z = z0 + 1; z <= cz + 6; z++) for (int x = cx - 1; x <= cx + 1; x++) set(x, B + 1, z, carpet);
        for (int x = cx - 4; x <= cx + 4; x++) set(x, B + 1, cz + 8, b(Blocks.QUARTZ_BRICKS));
        set(cx - 2, B + 2, cz + 8, b(Blocks.BELL));
        sign(cx + 2, B + 2, cz + 7, Direction.NORTH, "Front Desk", "Welcome!", "Excusez-moi?", "");
        for (int y = 9; y <= 13; y++) set(cx, B + y, cz, b(Blocks.CHAIN));
        set(cx, B + 8, cz, b(Blocks.GOLD_BLOCK));
        for (Direction d : Direction.Plane.HORIZONTAL) set(cx + d.getStepX(), B + 8, cz + d.getStepZ(), b(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
        for (int s = -1; s <= 1; s += 2) for (int z = cz - 4; z <= cz + 2; z += 3) {
            set(cx + s * 9, B + 1, z, b(Blocks.RED_WOOL));
            set(cx + s * 10, B + 1, z, b(Blocks.DARK_OAK_STAIRS).setValue(StairBlock.FACING, s < 0 ? Direction.EAST : Direction.WEST));
        }
        // the elevator (the one that got hacked): a glass shaft at the back, from the bottom floor to Gale's house
        int ex = cx, ez = z1 - 2;
        for (int y = B - 18; y <= B + 20; y++) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) set(ex, y, ez, b(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.NORTH));
                else set(ex + dx, y, ez + dz, (y - B) % 6 == 1 && dz == -1 && dx == 0 ? b(Blocks.AIR) : b(Blocks.GLASS));
            }
        }
        for (int f = 0; f < 3; f++) { int fy = B - 6 * (f + 1); fill(ex, fy, ez - 1, ex, fy + 1, ez - 1, b(Blocks.AIR)); }
        fill(ex, B + 1, ez - 1, ex, B + 2, ez - 1, b(Blocks.AIR));
        fill(ex, B + 8, ez - 1, ex, B + 9, ez - 1, b(Blocks.AIR));
        fill(ex, B + 15, ez - 1, ex, B + 16, ez - 1, b(Blocks.AIR));
        sign(ex + 2, B + 2, ez - 2, Direction.NORTH, "ELEVATOR", "(fixed... mostly)", "", "");
        // second floor: Gale's office + apartments for Yuri's crew
        for (int x = x0 + 1; x <= x1 - 1; x++) for (int z = z0 + 1; z <= z1 - 1; z++)
            if (!(x > cx - 6 && x < cx + 6 && z > cz - 6 && z < cz + 6) && (x == cx - 8 || x == cx + 8) && z != cz) for (int y = 8; y <= 13; y++) set(x, B + y, z, b(Blocks.WHITE_CONCRETE));
        sign(cx - 8, B + 9, cz - 1, Direction.EAST, "Apartments", "(the rooms that", "flew - rebuilt)", "");
        // Gale's house on the roof
        int g = 7;
        for (int x = cx - g; x <= cx + g; x++) for (int z = cz - g; z <= cz + g; z++) {
            boolean edge = Math.abs(x - cx) == g || Math.abs(z - cz) == g;
            for (int y = 15; y <= 21; y++) {
                BlockState s = b(Blocks.AIR);
                if (y == 21) s = b(Blocks.GOLD_BLOCK);
                else if (edge) s = y >= 17 && y <= 19 ? b(Blocks.YELLOW_STAINED_GLASS_PANE) : b(Blocks.QUARTZ_BRICKS);
                else if (y == 15) s = b(Blocks.YELLOW_CARPET);
                set(x, B + y, z, s);
            }
        }
        fill(cx - 1, B + 15, cz - g, cx + 1, B + 17, cz - g, b(Blocks.AIR));
        set(cx, B + 16, cz + 4, b(Blocks.GOLD_BLOCK));
        set(cx, B + 17, cz + 4, b(Blocks.PLAYER_HEAD));
        sign(cx + 1, B + 16, cz + 3, Direction.NORTH, "Gale Manchester", "Aurora", "Hohohoho!", "Future President");
        for (int x = cx - 5; x <= cx + 5; x += 10) for (int z = cz - 5; z <= cz + 5; z += 10) set(x, B + 15, z, b(Blocks.POTTED_AZALEA));
        // roof terrace rail
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
            if (x == x0 || x == x1 || z == z0 || z == z1) set(x, B + 15, z, b(Blocks.QUARTZ_SLAB).setValue(SlabBlock.TYPE, SlabType.BOTTOM));
    }

    // ------------------------------------------------------------------------------------------------ Yuri's HQ

    private void yuri() {
        int fy = B - 24;                                   // HQ floor
        int x0 = YURI_X - 14, x1 = YURI_X + 14, z0 = YURI_Z - 12, z1 = YURI_Z + 12;
        BlockState wall = b(Blocks.GRAY_CONCRETE), lab = b(Blocks.LIGHT_GRAY_CONCRETE);
        // shell
        fill(x0 - 1, fy - 1, z0 - 1, x1 + 1, fy + 9, z1 + 1, wall);
        fill(x0, fy, z0, x1, fy + 8, z1, b(Blocks.AIR));
        fill(x0, fy - 1, z0, x1, fy - 1, z1, b(Blocks.POLISHED_DEEPSLATE));
        // dividing wall: lab (west) | VR room (east)
        int div = YURI_X + 1;
        for (int z = z0; z <= z1; z++) for (int y = 0; y <= 8; y++) set(div, fy + y, z, Math.abs(z - YURI_Z) <= 1 && y <= 2 ? b(Blocks.AIR) : b(Blocks.WHITE_CONCRETE));
        set(div, fy + 3, YURI_Z, b(Blocks.REDSTONE_LAMP).setValue(RedstoneLampBlock.LIT, true));
        sign(div - 1, fy + 2, YURI_Z + 2, Direction.WEST, "VR ROOM", "Training: VR", "Ultima (20%)", "");
        // computer lab: desks of monitors, server racks, Yuri's laptop
        for (int x = x0 + 2; x < div - 2; x += 4) for (int z = z0 + 2; z <= z1 - 2; z++) {
            if (Math.abs(z - YURI_Z) <= 1) continue;
            set(x, fy, z, b(Blocks.SMOOTH_STONE_SLAB).setValue(SlabBlock.TYPE, SlabType.TOP));
            set(x, fy + 1, z, z % 2 == 0 ? b(Blocks.BLACK_STAINED_GLASS) : b(Blocks.DAYLIGHT_DETECTOR));
            set(x + 1, fy, z, b(Blocks.POLISHED_BLACKSTONE_STAIRS).setValue(StairBlock.FACING, Direction.EAST));
        }
        for (int z = z0; z <= z1; z++) for (int y = 0; y <= 4; y++) set(x0, fy + y, z, (z + y) % 3 == 0 ? b(Blocks.REDSTONE_LAMP).setValue(RedstoneLampBlock.LIT, true) : b(Blocks.OBSERVER).setValue(ObserverBlock.FACING, Direction.WEST));
        set(x0 + 4, fy, YURI_Z, b(Blocks.SMOOTH_QUARTZ));
        set(x0 + 4, fy + 1, YURI_Z, b(Blocks.SCULK_SENSOR));          // "the laptop" - hums when you walk near
        sign(x0 + 4, fy + 1, YURI_Z - 1, Direction.NORTH, "Yuri's Laptop", "Hacking / VR /", "bed making", "DON'T TOUCH");
        for (int x = x0 + 3; x < div; x += 5) for (int z = z0 + 3; z <= z1; z += 6) set(x, fy + 8, z, b(Blocks.SEA_LANTERN));
        for (int x = x0 + 1; x < div; x++) for (int z = z0; z <= z1; z++) if (l.getBlockState(mp.set(x, fy - 1, z)).is(Blocks.POLISHED_DEEPSLATE) && (x + z) % 2 == 0) set(x, fy - 1, z, lab);
        // the VR room: bright white grid that becomes anywhere
        for (int x = div + 1; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            set(x, fy - 1, z, (x % 4 == 0 || z % 4 == 0) ? b(Blocks.SEA_LANTERN) : b(Blocks.WHITE_CONCRETE));
            set(x, fy + 8, z, (x % 4 == 0 && z % 4 == 0) ? b(Blocks.SEA_LANTERN) : b(Blocks.WHITE_CONCRETE));
        }
        for (int z = z0; z <= z1; z++) for (int y = 0; y <= 7; y++) set(x1 + 1, fy + y, z, b(Blocks.WHITE_CONCRETE));
        set(div + 2, fy, YURI_Z + 3, b(Blocks.TARGET));                // the VR console
        sign(div + 2, fy + 1, YURI_Z + 3, Direction.EAST, "VR CONSOLE", "\"VR Ultima is", "down - You Win!\"", "");
        // bunks
        for (int z = z1 - 2; z <= z1; z += 2) {
            set(x0 + 1, fy, z, b(Blocks.LIGHT_BLUE_BED).setValue(BedBlock.FACING, Direction.WEST).setValue(BedBlock.PART, BedPart.HEAD));
            set(x0 + 2, fy, z, b(Blocks.LIGHT_BLUE_BED).setValue(BedBlock.FACING, Direction.WEST).setValue(BedBlock.PART, BedPart.FOOT));
        }
        // the "sky ground" hatch: a corridor north to a ladder shaft that comes up under a trapdoor
        for (int z = HATCH_Z; z < z0; z++) {
            fill(HATCH_X - 2, fy - 1, z, HATCH_X + 2, fy + 4, z, wall);
            fill(HATCH_X - 1, fy, z, HATCH_X + 1, fy + 3, z, b(Blocks.AIR));
            if (z % 4 == 0) set(HATCH_X, fy + 3, z, b(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
        }
        fill(HATCH_X - 1, fy, z0 - 1, HATCH_X + 1, fy + 3, z0 - 1, b(Blocks.AIR));
        if (!Clip.ok(mp.set(HATCH_X, 0, HATCH_Z))) return;
        int top = fy + 4;
        for (int y = B + 40; y > fy + 4; y--) {
            BlockState s = l.getBlockState(mp.set(HATCH_X, y, HATCH_Z));
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.is(net.minecraft.tags.BlockTags.LEAVES) && !s.canBeReplaced()) { top = y; break; }
        }
        for (int y = fy; y <= top; y++) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) set(HATCH_X + dx, y, HATCH_Z + dz, y >= top - 1 ? b(Blocks.MOSSY_COBBLESTONE) : wall);
            set(HATCH_X, y, HATCH_Z, b(Blocks.AIR));
            set(HATCH_X, y, HATCH_Z + 1, y >= top - 1 ? b(Blocks.MOSSY_COBBLESTONE) : wall);
        }
        for (int y = fy; y <= top; y++) set(HATCH_X, y, HATCH_Z, b(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.NORTH));
        set(HATCH_X, top + 1, HATCH_Z, b(Blocks.IRON_TRAPDOOR).setValue(TrapDoorBlock.HALF, Half.BOTTOM).setValue(TrapDoorBlock.FACING, Direction.NORTH));
        set(HATCH_X + 1, top + 1, HATCH_Z, b(Blocks.FERN));
        set(HATCH_X - 1, top + 1, HATCH_Z + 1, b(Blocks.SWEET_BERRY_BUSH));
    }
}
