package com.xenowarriors.story;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import static com.xenowarriors.story.StoryScenes.*;

/**
 * Story places drawn from the user's reference pictures (Season 2 and some Season 1 places redone): the Royal Capital's
 * city gate and colonnade courtyard, the Royal Palace's gate (Kyrie and the queens), Mikiko's school, what's left of F2,
 * the moonlit palace balcony and the night sky above the clouds (Kyrie / Ava), Aurora's mall street (the Konbini),
 * the Underclass City and the clock window in the SR cells. North (-z) is the way the party faces when they arrive.
 */
final class StoryPlaces2 {
    private StoryPlaces2() {}

    private static BlockState leaves(Block k) { return k.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true); }
    private static BlockState stair(Block k, Direction d, boolean top) { return k.defaultBlockState().setValue(StairBlock.FACING, d).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM); }
    private static BlockState slab(Block k, boolean top) { return k.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM); }
    private static BlockState hanging(Block k) { return k.defaultBlockState().setValue(LanternBlock.HANGING, true); }

    private static void sign(ServerLevel l, BlockPos p, Block wallSign, Direction facing, String... lines) {
        set(l, p, wallSign.defaultBlockState().setValue(WallSignBlock.FACING, facing));
        if (l.getBlockEntity(p) instanceof SignBlockEntity s) {
            SignText t = new SignText();
            for (int i = 0; i < Math.min(4, lines.length); i++) t = t.setMessage(i, Component.literal(lines[i]));
            s.setText(t, true);
            s.setWaxed(true);
        }
    }

    private static void bush(ServerLevel l, BlockPos c, int x, int z, RandomSource r) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            set(l, c.offset(x + dx, 1, z + dz), leaves(r.nextInt(3) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
            if (Math.abs(dx) + Math.abs(dz) <= 1) set(l, c.offset(x + dx, 2, z + dz), leaves(Blocks.AZALEA_LEAVES));
        }
    }

    /** A crescent moon standing in the sky (in the x-y plane at z), lit from inside. */
    private static void moon(ServerLevel l, BlockPos c, int mx, int my, int mz, int rad) {
        for (int x = -rad; x <= rad; x++) for (int y = -rad; y <= rad; y++) {
            double d = Math.sqrt(x * x + y * y), cut = Math.sqrt((x - rad * 0.45) * (x - rad * 0.45) + (y - rad * 0.3) * (y - rad * 0.3));
            if (d > rad + 0.3 || cut < rad * 0.85) continue;
            set(l, c.offset(mx + x, my + y, mz), b(d < rad - 1.2 ? Blocks.SEA_LANTERN : Blocks.WHITE_CONCRETE));
        }
    }

    // ------------------------------------------------------------------ the Royal Capital: the city gate and the courtyard

    /**
     * The Royal Capital (the user's pictures): the old stone city wall with its pointed archway and the portcullis drawn
     * up, a strip of grass along the foot of the wall and the reddish road outside; inside, a colonnade along the wall
     * (white fluted columns under a roof, dark stone wall behind, pale tiled floor), a lawn with bushes, and the mauve
     * paved walk up to the palace, lined with big columns.
     */
    static void capital(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("royal_capital");
        for (int x = -RR; x <= RR; x++) for (int z = -RR; z <= RR; z++) {
            if (!inside(x, z, RR)) continue;
            BlockState f;
            if (z >= 18) f = Math.abs(x) <= 4 || z >= 20 ? b(r.nextInt(7) == 0 ? Blocks.BROWN_TERRACOTTA : Blocks.TERRACOTTA) : z == 18 ? b(Blocks.GRASS_BLOCK) : b(Blocks.SMOOTH_STONE);
            else if (z <= -15) continue;                                                  // (the palace front keeps its quartz)
            else if (Math.abs(x) <= 1) f = b(Blocks.LIGHT_GRAY_TERRACOTTA);
            else if (Math.abs(x) == 2) f = b(Blocks.POLISHED_ANDESITE);
            else if (z >= 12) f = b(((x + z) & 1) == 0 ? Blocks.SMOOTH_STONE : Blocks.STONE);   // (under the colonnade)
            else f = b(Blocks.GRASS_BLOCK);
            set(l, c.offset(x, 0, z), f);
            if (z == 18 && Math.abs(x) > 4 && (x & 1) == 0) set(l, c.offset(x, 1, z), b(Blocks.GRASS));
            if (z == 19 && Math.abs(x) > 4) set(l, c.offset(x, 1, z), slab(Blocks.SMOOTH_STONE_SLAB, false));   // (the curb)
        }
        // the wall: rough grey stone outside, dark brick inside, with the archway
        for (int x = -RR; x <= RR; x++) for (int z = 16; z <= 17; z++) {
            if (!inside(x, z, RR - 1)) continue;
            for (int y = 1; y <= 10; y++) {
                if (gateOpen(x, y)) { set(l, c.offset(x, y, z), b(Blocks.AIR)); continue; }
                BlockState s;
                int ring = gateRing(x, y);
                if (ring == 1) s = b(Blocks.POLISHED_ANDESITE);
                else if (ring == 2) s = b(Blocks.ANDESITE);
                else if (z == 17) { int k = r.nextInt(10); s = b(k < 5 ? Blocks.STONE_BRICKS : k < 7 ? Blocks.CRACKED_STONE_BRICKS : k < 8 && y < 4 ? Blocks.MOSSY_STONE_BRICKS : Blocks.COBBLESTONE); }
                else s = b(r.nextInt(5) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS);
                set(l, c.offset(x, y, z), s);
            }
            if ((x & 1) == 0 && z == 17) set(l, c.offset(x, 11, z), b(Blocks.STONE_BRICKS));   // (battlements)
        }
        // the portcullis, drawn up: a wooden lattice in the top of the arch, its spikes hanging just below
        for (int x = -3; x <= 3; x++) for (int y = 6; y <= 9; y++) {
            if (!gateOpen(x, y)) continue;
            if (y == 6 && (x & 1) == 0) continue;
            set(l, c.offset(x, y, 17), b(Blocks.DARK_OAK_FENCE));
        }
        // the colonnade along the inside of the wall: columns, a roof, lanterns
        for (int x = -RR; x <= RR; x++) {
            int ax = Math.abs(x);
            if (ax < 6 || !inside(x, 15, RR - 2)) continue;
            for (int z = 11; z <= 15; z++) set(l, c.offset(x, 6, z), z == 11 ? slab(Blocks.SMOOTH_STONE_SLAB, false) : b(Blocks.SMOOTH_STONE));
            if ((ax - 6) % 4 == 0) {
                set(l, c.offset(x, 1, 12), b(Blocks.CHISELED_QUARTZ_BLOCK));
                fill(l, c, x, 2, 12, x, 5, 12, b(Blocks.QUARTZ_PILLAR));
            }
            if ((ax - 8) % 8 == 0) set(l, c.offset(x, 5, 14), hanging(Blocks.LANTERN));
        }
        // big columns along the walk, bushes on the lawn, a couple of lamps
        for (int z : new int[]{-13, -7, -1, 5}) for (int s : new int[]{-4, 4}) {
            set(l, c.offset(s, 1, z), b(Blocks.SMOOTH_QUARTZ));
            fill(l, c, s, 2, z, s, 7, z, b(Blocks.QUARTZ_PILLAR));
            set(l, c.offset(s, 8, z), b(Blocks.CHISELED_QUARTZ_BLOCK));
        }
        for (int[] p : new int[][]{{-9, -4}, {10, -3}, {-15, 6}, {15, 7}, {-20, -9}, {21, -8}, {-12, -12}, {13, -12}}) bush(l, c, p[0], p[1], r);
        for (int s : new int[]{-10, 10}) lamp(l, c, s, 3);
        // the palace front
        box(l, c, -6, -20, 6, -16, 10, b(Blocks.QUARTZ_BRICKS), b(Blocks.GOLD_BLOCK), b(Blocks.YELLOW_STAINED_GLASS_PANE));
        fill(l, c, -2, 1, -16, 2, 5, -16, b(Blocks.AIR));
        for (int s : new int[]{-3, 3}) { fill(l, c, s, 1, -15, s, 6, -15, b(Blocks.QUARTZ_PILLAR)); set(l, c.offset(s, 7, -15), b(Blocks.GOLD_BLOCK)); }
        for (int i = 0; i < 14; i++) { double a = i * Math.PI * 2 / 14; set(l, c.offset((int) (Math.cos(a) * (RR + 3)), -3, (int) (Math.sin(a) * (RR + 3))), b(Blocks.WHITE_WOOL)); }   // clouds
    }

    /** The archway: straight sides up to 6, then pointed. */
    private static boolean gateOpen(int x, int y) {
        int ax = Math.abs(x);
        if (y < 1) return false;
        if (y <= 6) return ax <= 3;
        return y == 7 ? ax <= 2 : y == 8 ? ax <= 1 : y == 9 && ax == 0;
    }

    /** 1: the stone band round the arch, 2: the second band, 0: wall. */
    private static int gateRing(int x, int y) {
        if (Math.abs(x) > 6 || y > 12) return 0;
        for (int k = 1; k <= 2; k++)
            for (int dx = -k; dx <= k; dx++) for (int dy = -k; dy <= k; dy++) if (gateOpen(x + dx, y + dy)) return k;
        return 0;
    }

    // ------------------------------------------------------------------ the Royal Palace (Kyrie and the queens)

    /**
     * The Royal Palace's gate at night (the user's picture): a white balustrade with a tall white spiked fence on it
     * (red ribbons wound through), two white gate pillars with winged birds on top and crescent moons on their black
     * feet, the black iron gates standing open, a purple carpet to a tiered fountain, a round blue crest set in the
     * ground in front of the gate - and the palace behind, where the queens hold court.
     */
    static void royalPalace(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("royal_palace");
        // the crest in the ground before the gate
        for (int x = -5; x <= 5; x++) for (int z = -1; z <= 9; z++) {
            double d = Math.sqrt(x * x + (z - 4) * (z - 4));
            BlockState s = null;
            if (Math.abs(x) + Math.abs(z - 4) <= 1) s = b(Blocks.WHITE_CONCRETE);
            else if (d <= 2.6) s = b(Blocks.LIGHT_BLUE_CONCRETE);
            else if (d <= 3.6) s = b(Blocks.BLUE_TERRACOTTA);
            else if (d <= 4.6 && Math.abs(z - 4) <= 2 && Math.abs(x) >= 4) s = b(Blocks.GOLD_BLOCK);
            if (s != null) set(l, c.offset(x, 0, z), s);
        }
        // the fence: balustrade, white spiked fence, ribbons
        for (int x = -RR; x <= RR; x++) {
            int ax = Math.abs(x);
            if (ax < 6 || !inside(x, 0, RR - 1)) continue;
            set(l, c.offset(x, 1, 0), b(Blocks.SMOOTH_QUARTZ));
            set(l, c.offset(x, 2, 0), b(Blocks.DIORITE_WALL));
            set(l, c.offset(x, 3, 0), slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
            fill(l, c, x, 4, 0, x, 6, 0, b(Blocks.BIRCH_FENCE));
            if ((x & 1) == 0) set(l, c.offset(x, 7, 0), b(Blocks.END_ROD));
            if (ax % 5 == 0) set(l, c.offset(x, 5, 0), b(Blocks.RED_WOOL));
        }
        // the gate pillars with their birds
        for (int sx : new int[]{-1, 1}) {
            for (int x = 4; x <= 5; x++) for (int z = -1; z <= 0; z++) {
                int px = sx * x;
                set(l, c.offset(px, 1, z), b(Blocks.POLISHED_BLACKSTONE));
                set(l, c.offset(px, 2, z), b(Blocks.CHISELED_QUARTZ_BLOCK));
                fill(l, c, px, 3, z, px, 7, z, b(Blocks.QUARTZ_PILLAR));
                set(l, c.offset(px, 8, z), b(Blocks.CHISELED_QUARTZ_BLOCK));
            }
            set(l, c.offset(sx * 4, 1, 1), b(Blocks.SEA_LANTERN));                       // (the crescent on its foot, glowing)
            int bx = sx * 5;
            set(l, c.offset(bx, 9, 0), b(Blocks.CALCITE)); set(l, c.offset(bx, 9, -1), b(Blocks.CALCITE));   // the bird: body,
            set(l, c.offset(sx * 4, 10, 0), b(Blocks.CALCITE));                                               // head (watching the gate),
            set(l, c.offset(bx, 10, 1), stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH, true));                  // wings up
            set(l, c.offset(bx, 10, -2), stair(Blocks.QUARTZ_STAIRS, Direction.NORTH, true));
            // the gate, swung open inward
            fill(l, c, sx * 3, 1, -4, sx * 3, 5, -1, b(Blocks.IRON_BARS));
            set(l, c.offset(sx * 3, 6, -1), b(Blocks.POLISHED_BLACKSTONE_WALL));
        }
        // the purple carpet up to the fountain
        for (int z = -12; z <= 0; z++) for (int x = -2; x <= 2; x++) set(l, c.offset(x, 0, z), b(Math.abs(x) <= 1 ? Blocks.PURPLE_WOOL : Blocks.PURPLE_CONCRETE));
        // the tiered fountain
        int fz = -8;
        for (int x = -4; x <= 4; x++) for (int z = fz - 4; z <= fz + 4; z++) {
            double d = Math.sqrt(x * x + (z - fz) * (z - fz));
            if (d <= 2.7) { set(l, c.offset(x, 0, z), b(Blocks.WATER)); set(l, c.offset(x, -1, z), b(d <= 1.5 ? Blocks.SEA_LANTERN : Blocks.SMOOTH_QUARTZ)); }
            else if (d <= 3.6) set(l, c.offset(x, 1, z), b(Blocks.QUARTZ_BRICKS));
        }
        fill(l, c, 0, 0, fz, 0, 2, fz, b(Blocks.QUARTZ_PILLAR));
        fill(l, c, -1, 3, fz - 1, 1, 3, fz + 1, b(Blocks.QUARTZ_BRICKS));
        set(l, c.offset(0, 4, fz), b(Blocks.QUARTZ_PILLAR));
        set(l, c.offset(0, 5, fz), b(Blocks.WATER));
        for (int[] p : new int[][]{{-3, -4}, {3, -5}, {-2, -12}}) set(l, c.offset(p[0], 1, p[1]), b(Blocks.POPPY));   // (rose petals)
        // the palace: white quartz, purple glass, the three queens' thrones inside
        box(l, c, -14, -27, 14, -15, 13, b(Blocks.QUARTZ_BRICKS), b(Blocks.SMOOTH_QUARTZ), b(Blocks.PURPLE_STAINED_GLASS));
        fill(l, c, -2, 1, -15, 2, 6, -15, b(Blocks.AIR));
        for (int s : new int[]{-3, 3}) { fill(l, c, s, 1, -14, s, 8, -14, b(Blocks.QUARTZ_PILLAR)); set(l, c.offset(s, 9, -14), b(Blocks.SEA_LANTERN)); }
        for (int z = -26; z <= -15; z++) for (int x = -1; x <= 1; x++) set(l, c.offset(x, 0, z), b(Blocks.PURPLE_WOOL));
        for (int tx : new int[]{-4, 0, 4}) {
            set(l, c.offset(tx, 1, -25), b(Blocks.GOLD_BLOCK)); set(l, c.offset(tx, 2, -25), b(Blocks.PURPLE_CARPET));
            fill(l, c, tx, 2, -26, tx, 4, -26, b(Blocks.GOLD_BLOCK));
            set(l, c.offset(tx, 5, -26), b(Blocks.AMETHYST_BLOCK));
        }
        for (int x = -12; x <= 12; x += 6) set(l, c.offset(x, 12, -21), hanging(Blocks.LANTERN));
        for (int s : new int[]{-10, 10}) { lamp(l, c, s, -8); lamp(l, c, s, 6); }
        for (int i = 0; i < 16; i++) { double a = i * Math.PI * 2 / 16; set(l, c.offset((int) (Math.cos(a) * (RR + 3)), -3, (int) (Math.sin(a) * (RR + 3))), b(Blocks.PURPLE_WOOL)); }
        moon(l, c, 14, 19, -RR - 6, 6);
    }

    // ------------------------------------------------------------------ Mikiko's school

    /**
     * The Elementalist School from the street (the user's picture): an orange two-storey school with white-framed
     * windows and brown roofs, the taller middle block with a peach gable and a clock, the SCHOOL sign over a little
     * porch and the double doors; a forecourt with bushes; light grey gate pillars on a low brown wall with a black iron
     * fence; the pavement and the road with its crossing. Inside: the classroom (desks, blackboard) - and the cobwebs.
     */
    static void school(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("school");
        BlockState wall = b(Blocks.ORANGE_TERRACOTTA), peach = b(Blocks.WHITE_TERRACOTTA), roof = b(Blocks.BRICKS), glass = b(Blocks.LIGHT_BLUE_STAINED_GLASS);
        // grounds: forecourt, pavement, road with lane lines and the crossing
        for (int x = -RR; x <= RR; x++) for (int z = -RR; z <= RR; z++) {
            if (!inside(x, z, RR) || z < 0) continue;
            BlockState f;
            if (z <= 9) f = Math.abs(x) <= 3 ? b(Blocks.LIGHT_GRAY_CONCRETE) : b(Blocks.SMOOTH_STONE);
            else if (z <= 13 || z >= 23) f = b(Math.floorMod(x, 6) == 0 ? Blocks.STONE : Blocks.LIGHT_GRAY_CONCRETE);
            else if (z == 15 || z == 21) f = b(Math.floorMod(x, 4) < 2 ? Blocks.WHITE_CONCRETE : Blocks.GRAY_CONCRETE);
            else if (z >= 16 && z <= 20 && Math.abs(x) <= 4 && (z & 1) == 0) f = b(Blocks.WHITE_CONCRETE);
            else f = b(Blocks.GRAY_CONCRETE);
            set(l, c.offset(x, 0, z), f);
        }
        // the building: x -18..18, z -21..-1; the middle block (|x| <= 7) is taller
        for (int x = -18; x <= 18; x++) for (int z = -21; z <= -1; z++) {
            boolean mid = Math.abs(x) <= 7;
            int h = mid ? 12 : 8;
            boolean side = x == -18 || x == 18 || z == -21 || z == -1;
            if (side) for (int y = 1; y <= h; y++) set(l, c.offset(x, y, z), y == h ? peach : wall);
            if (!mid && Math.abs(x) == 8) for (int y = 9; y <= 12; y++) set(l, c.offset(x, y, z), wall);   // (the middle block's sides above the wings)
            // wing roofs: hipped in two steps; the middle block: pitched, ridge front to back
            if (!mid) {
                set(l, c.offset(x, 9, z), roof);
                if (Math.abs(x) >= 9 && Math.abs(x) <= 17 && z >= -20 && z <= -2) set(l, c.offset(x, 10, z), roof);
                if (Math.abs(x) >= 10 && Math.abs(x) <= 16 && z >= -19 && z <= -3) set(l, c.offset(x, 11, z), slab(Blocks.BRICK_SLAB, false));
            } else {
                int k = 7 - Math.abs(x);                                                 // 0 at the edge, 7 at the ridge
                set(l, c.offset(x, 13 + k, z), roof);
                if (z == -1 || z == -21) for (int y = 13; y < 13 + k; y++) set(l, c.offset(x, y, z), peach);   // the gables
            }
        }
        for (int x = -18; x <= 18; x++) if (Math.abs(x) > 7) set(l, c.offset(x, 9, 0), slab(Blocks.BRICK_SLAB, true));   // (eaves)
        // windows: two rows, white sills
        for (int x = -17; x <= 17; x++) {
            int ax = Math.abs(x);
            boolean wingWin = ax >= 9 && (ax - 9) % 3 != 2, midWin = ax <= 6 && ax >= 2 && (ax - 2) % 3 != 2;
            for (int[] row : ax > 7 ? new int[][]{{2, 3}, {5, 6}} : new int[][]{{2, 4}, {7, 9}}) {
                boolean win = ax > 7 ? wingWin : midWin && !(row[0] == 2 && ax <= 3);
                if (!win) continue;
                for (int y = row[0]; y <= row[1]; y++) set(l, c.offset(x, y, -1), glass);
                set(l, c.offset(x, row[0] - 1, 0), slab(Blocks.SMOOTH_QUARTZ_SLAB, true));
            }
            if (ax > 7 && ax < 18 && wingWin) for (int[] row : new int[][]{{2, 3}, {5, 6}}) for (int y = row[0]; y <= row[1]; y++) set(l, c.offset(x, y, -21), glass);
        }
        // the doors, the porch, the sign, the clock
        fill(l, c, -1, 1, -1, 1, 3, -1, b(Blocks.AIR));
        set(l, c.offset(-1, 4, -1), b(Blocks.MANGROVE_PLANKS)); set(l, c.offset(0, 4, -1), b(Blocks.MANGROVE_PLANKS)); set(l, c.offset(1, 4, -1), b(Blocks.MANGROVE_PLANKS));
        fill(l, c, -3, 5, 0, 3, 5, 1, b(Blocks.SMOOTH_SANDSTONE));
        for (int s : new int[]{-3, 3}) fill(l, c, s, 1, 1, s, 4, 1, b(Blocks.STRIPPED_BIRCH_LOG));
        fill(l, c, -2, 6, -1, 2, 6, -1, b(Blocks.SPRUCE_PLANKS));                       // the sign board
        sign(l, c.offset(0, 6, 0), Blocks.SPRUCE_WALL_SIGN, Direction.SOUTH, "", "SCHOOL", "", "");
        var room = new net.minecraft.world.phys.AABB(c.offset(-3, 12, -2)).expandTowards(6, 8, 4);
        for (var f : l.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, room)) f.discard();
        set(l, c.offset(0, 16, -1), b(Blocks.WHITE_CONCRETE));
        var frame = new net.minecraft.world.entity.decoration.ItemFrame(l, c.offset(0, 16, 0), Direction.SOUTH);
        frame.setItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CLOCK), false);
        net.minecraft.nbt.CompoundTag t = frame.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        t.putBoolean("Fixed", true); t.putBoolean("Invulnerable", true);
        frame.load(t);
        l.addFreshEntity(frame);
        for (int s : new int[]{-1, 1}) for (int x = 5; x <= 7; x++) set(l, c.offset(s * x, 1, 0), leaves(Blocks.AZALEA_LEAVES));   // bushes by the doors
        // the front wall: low brown wall, black iron fence, light grey gate pillars
        for (int x = -RR; x <= RR; x++) {
            int ax = Math.abs(x);
            if (ax < 7 || !inside(x, 10, RR - 1)) continue;
            fill(l, c, x, 1, 10, x, 2, 10, b(Blocks.BROWN_TERRACOTTA));
            set(l, c.offset(x, 3, 10), b(ax % 6 == 1 ? Blocks.POLISHED_BLACKSTONE_WALL : Blocks.IRON_BARS));
            set(l, c.offset(x, 4, 10), b(ax % 6 == 1 ? Blocks.POLISHED_BLACKSTONE_WALL : Blocks.IRON_BARS));
        }
        for (int s : new int[]{-1, 1}) {
            for (int x = 5; x <= 6; x++) { fill(l, c, s * x, 1, 10, s * x, 5, 11, b(Blocks.SMOOTH_STONE)); set(l, c.offset(s * x, 6, 10), slab(Blocks.SMOOTH_STONE_SLAB, false)); set(l, c.offset(s * x, 6, 11), slab(Blocks.SMOOTH_STONE_SLAB, false)); }
            set(l, c.offset(s * 5, 3, 12), b(Blocks.LIGHT_GRAY_CONCRETE));
        }
        for (int s : new int[]{-14, 14}) lamp(l, c, s, 12);
        classroom(l, c, r);
        bookRoom(l, c, r);
        for (int i = 0; i < 10; i++) set(l, c.offset(-16 + r.nextInt(24), 6 + r.nextInt(2), -19 + r.nextInt(16)), b(Blocks.COBWEB));   // (and whatever's haunting it)
    }

    /**
     * Mikiko's classroom (the user's picture): a grey floor, big windows along the back with blue frames, blinds and
     * pot plants on the sills over a wood-panelled wall, cream pillars between them, rows of desks (peach tops, dark
     * legs) with chairs, a TV on the wall, a blue notice board with papers, a tall plant and the teacher's lectern.
     */
    private static void classroom(ServerLevel l, BlockPos c, RandomSource r) {
        for (int x = -17; x <= 7; x++) for (int z = -20; z <= -2; z++) set(l, c.offset(x, 0, z), b(Blocks.LIGHT_GRAY_CONCRETE));
        for (int x = -17; x <= 7; x++) {
            boolean pillar = Math.floorMod(x + 17, 6) == 0;
            set(l, c.offset(x, 1, -20), b(pillar ? Blocks.SMOOTH_SANDSTONE : Blocks.STRIPPED_SPRUCE_LOG));   // wood panels
            set(l, c.offset(x, 2, -20), b(pillar ? Blocks.SMOOTH_SANDSTONE : Blocks.SMOOTH_STONE_SLAB));     // sills
            if (!pillar && (x & 1) == 0) set(l, c.offset(x, 3, -20), b(Blocks.POTTED_AZALEA));
            for (int y = 3; y <= 7; y++) set(l, c.offset(x, y, -21), pillar ? b(Blocks.SMOOTH_SANDSTONE) : y == 7 ? b(Blocks.WHITE_STAINED_GLASS) : b(Math.floorMod(x, 3) == 0 ? Blocks.CYAN_TERRACOTTA : Blocks.LIGHT_BLUE_STAINED_GLASS));
        }
        for (int x = -14; x <= 4; x += 3) for (int z = -16; z <= -6; z += 3) {           // desks and chairs (facing the board on the west wall)
            set(l, c.offset(x, 1, z), b(Blocks.POLISHED_BLACKSTONE_WALL));
            set(l, c.offset(x, 2, z), slab(Blocks.CHERRY_SLAB, false));
            set(l, c.offset(x + 1, 1, z), stair(Blocks.CHERRY_STAIRS, Direction.EAST, false));
        }
        fill(l, c, -17, 2, -16, -17, 5, -6, b(Blocks.LIGHT_BLUE_CONCRETE));             // the notice board
        for (int[] p : new int[][]{{-14, 3}, {-12, 4}, {-9, 3}, {-8, 4}}) set(l, c.offset(-17, p[1], p[0]), b(Blocks.WHITE_CONCRETE));
        fill(l, c, -17, 5, -19, -17, 6, -18, b(Blocks.BLACK_CONCRETE));                  // the TV
        set(l, c.offset(-16, 1, -4), b(Blocks.LECTERN));
        set(l, c.offset(-16, 1, -17), b(Blocks.FLOWERING_AZALEA));
        for (int x = -15; x <= 5; x += 5) set(l, c.offset(x, 8, -11), b(Blocks.SEA_LANTERN));   // ceiling lights
    }

    /**
     * The school's book room (the user's picture), in the east wing behind its own door: dark walls lined with full
     * bookshelves, colourful shelves of books, a desk with books piled on it and more piles on the floor. (Where Mikiko
     * found the book that moves a corrupted soul.)
     */
    private static void bookRoom(ServerLevel l, BlockPos c, RandomSource r) {
        fill(l, c, 8, 1, -20, 8, 8, -2, b(Blocks.DARK_OAK_PLANKS));                       // its wall
        fill(l, c, 8, 1, -9, 8, 2, -8, b(Blocks.AIR));                                   // its door
        for (int x = 9; x <= 17; x++) for (int z = -20; z <= -2; z++) set(l, c.offset(x, 0, z), b(Blocks.LIGHT_GRAY_CONCRETE));
        for (int z = -19; z <= -3; z++) for (int y = 1; y <= 5; y++) set(l, c.offset(17, y, z), b(y == 5 ? Blocks.SPRUCE_SLAB : Math.floorMod(z, 4) == 0 ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF));
        for (int x = 9; x <= 16; x++) {
            set(l, c.offset(x, 1, -20), b(Blocks.BOOKSHELF)); set(l, c.offset(x, 2, -20), b(Blocks.BOOKSHELF));
            BlockState[] books = {b(Blocks.RED_CONCRETE), b(Blocks.YELLOW_CONCRETE), b(Blocks.LIME_CONCRETE), b(Blocks.PINK_CONCRETE), b(Blocks.LIGHT_BLUE_CONCRETE), b(Blocks.ORANGE_CONCRETE)};
            for (int y : new int[]{4, 6}) { set(l, c.offset(x, y, -20), books[r.nextInt(books.length)]); set(l, c.offset(x, y - 1, -20), slab(Blocks.SPRUCE_SLAB, true)); }
        }
        fill(l, c, 11, 1, -14, 14, 1, -13, b(Blocks.SPRUCE_PLANKS));                     // the desk
        set(l, c.offset(11, 2, -14), b(Blocks.CHISELED_BOOKSHELF)); set(l, c.offset(14, 2, -13), b(Blocks.LECTERN));
        for (int[] p : new int[][]{{10, -11}, {15, -10}, {12, -6}, {16, -15}}) set(l, c.offset(p[0], 1, p[1]), b(r.nextBoolean() ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF));   // book piles
        set(l, c.offset(13, 7, -11), hanging(Blocks.LANTERN));
    }

    // ------------------------------------------------------------------ what's left of F2

    /**
     * F2 after the blast (the user's picture): a red-orange waste of sand and baked clay, jagged red rock thrown up
     * in shards and leaning slabs, a scorched crater where the facility stood, broken concrete and twisted bars - and
     * the hatch to the basement Yuri hid in.
     */
    static void f2Ruins(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("f2_ruins");
        for (int x = -RR; x <= RR; x++) for (int z = -RR; z <= RR; z++) {
            if (!inside(x, z, RR) || r.nextInt(4) != 0) continue;
            set(l, c.offset(x, 0, z), b(r.nextBoolean() ? Blocks.TERRACOTTA : Blocks.RED_TERRACOTTA));
        }
        // the crater
        for (int x = -8; x <= 8; x++) for (int z = -18; z <= -2; z++) {
            double d = Math.sqrt(x * x + (z + 10) * (z + 10));
            if (d > 7.5) continue;
            set(l, c.offset(x, 0, z), b(d < 4 ? Blocks.BLACKSTONE : r.nextInt(3) == 0 ? Blocks.COARSE_DIRT : Blocks.BASALT));
            if (r.nextInt(14) == 0) set(l, c.offset(x, 1, z), b(Blocks.CAMPFIRE));
        }
        // red rock shards, leaning
        BlockState[] rock = {b(Blocks.RED_TERRACOTTA), b(Blocks.TERRACOTTA), b(Blocks.GRANITE), b(Blocks.ORANGE_TERRACOTTA)};
        int[][] spires = {{-20, -12, 10, 3}, {16, -16, 13, -3}, {23, -2, 9, -2}, {-24, 6, 7, 2}, {6, -24, 8, 2}, {-12, -22, 11, 3}, {20, 12, 6, -2}};
        for (int[] sp : spires) {
            int h = sp[2], lean = sp[3];
            for (int k = 0; k <= h; k++) {
                int ox = sp[0] + Math.round((float) k * lean / h), w = k < h / 3 ? 2 : k < h * 2 / 3 ? 1 : 0;
                for (int dx = -w; dx <= w; dx++) for (int dz = -w; dz <= w; dz++)
                    if (Math.abs(dx) + Math.abs(dz) <= w + 1) set(l, c.offset(ox + dx, 1 + k, sp[1] + dz), rock[r.nextInt(rock.length)]);
            }
        }
        // flat broken slabs of rock
        for (int[] sl : new int[][]{{-18, -2, 9, 3}, {10, -8, 7, 2}, {-6, -26, 10, 2}, {14, 4, 6, 2}}) {
            for (int i = 0; i < sl[2]; i++) for (int j = 0; j < sl[3]; j++) {
                int y = 1 + (i > sl[2] / 2 ? 1 : 0);
                set(l, c.offset(sl[0] + i, y, sl[1] + j), rock[r.nextInt(rock.length)]);
            }
        }
        // what's left of the building: broken walls, bars, concrete
        for (int i = 0; i < 22; i++) {
            int x = r.nextInt(30) - 15, z = -r.nextInt(24) - 2;
            if (!inside(x, z, RR - 3)) continue;
            BlockState s = b(r.nextInt(3) == 0 ? Blocks.CRACKED_STONE_BRICKS : r.nextBoolean() ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.WHITE_CONCRETE);
            fill(l, c, x, 1, z, x, 1 + r.nextInt(3), z, s);
            if (r.nextInt(3) == 0) set(l, c.offset(x + 1, 1, z), b(Blocks.IRON_BARS));
        }
        // the hatch to the basement
        for (int x = 3; x <= 5; x++) for (int z = -5; z <= -3; z++) set(l, c.offset(x, 0, z), b(Blocks.POLISHED_ANDESITE));
        set(l, c.offset(4, 1, -4), b(Blocks.IRON_TRAPDOOR));
        // keep the way in clear
        fill(l, c, -5, 1, 0, 5, 12, RR, b(Blocks.AIR));
    }

    // ------------------------------------------------------------------ the palace balcony and the night sky (Kyrie / Ava)

    /**
     * The moonlit balcony (the user's picture): a room of purple marble behind white columns hung with sheer
     * curtains, a pale pink walk out to a round balcony with a white railing, the sky going from deep blue to pink
     * over a sea of purple cloud, and a crescent moon.
     */
    static void balcony(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("palace_balcony");
        int bz = -10, br = 9;
        for (int x = -RR; x <= RR; x++) for (int z = -RR; z <= RR; z++) {
            if (!inside(x, z, RR)) continue;
            double d = Math.sqrt(x * x + (z - bz) * (z - bz));
            if (d <= br || (Math.abs(x) <= 2 && z <= 6 && z > bz)) set(l, c.offset(x, 0, z), b(Blocks.PINK_TERRACOTTA));
            else if (z < 6) set(l, c.offset(x, 0, z), b(Blocks.PURPLE_STAINED_GLASS));   // (the cloud sea below)
            if (z < 6 && d > br && !(Math.abs(x) <= 2 && z > bz)) set(l, c.offset(x, -1, z), b(r.nextInt(6) == 0 ? Blocks.MAGENTA_WOOL : Blocks.PURPLE_WOOL));
        }
        // the railing round the balcony (open where the walk comes in)
        for (int x = -br - 1; x <= br + 1; x++) for (int z = bz - br - 1; z <= bz + br + 1; z++) {
            double d = Math.sqrt(x * x + (z - bz) * (z - bz));
            if (d > br + 0.4 || d < br - 0.6 || (Math.abs(x) <= 2 && z > bz)) continue;
            set(l, c.offset(x, 1, z), b(Blocks.DIORITE_WALL));
            set(l, c.offset(x, 2, z), slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
        }
        // the room: white columns and the curtains
        for (int s : new int[]{-1, 1}) {
            for (int z : new int[]{6, 14}) {
                set(l, c.offset(s * 7, 1, z), b(Blocks.CHISELED_QUARTZ_BLOCK));
                fill(l, c, s * 7, 2, z, s * 7, 8, z, b(Blocks.QUARTZ_PILLAR));
                set(l, c.offset(s * 7, 9, z), b(Blocks.CHISELED_QUARTZ_BLOCK));
            }
            fill(l, c, s * 6, 3, 6, s * 6, 8, 6, b(Blocks.WHITE_STAINED_GLASS));
            fill(l, c, s * 5, 6, 6, s * 5, 8, 6, b(Blocks.WHITE_STAINED_GLASS));
            set(l, c.offset(s * 4, 8, 6), b(Blocks.PINK_STAINED_GLASS));
        }
        fill(l, c, -7, 10, 6, 7, 10, 14, b(Blocks.SMOOTH_QUARTZ));
        for (int x = -6; x <= 6; x += 4) set(l, c.offset(x, 9, 10), hanging(Blocks.LANTERN));
        for (int i = 0; i < 18; i++) { double a = i * Math.PI * 2 / 18; set(l, c.offset((int) (Math.cos(a) * (RR + 3)), -3, (int) (Math.sin(a) * (RR + 3))), b(i % 3 == 0 ? Blocks.PINK_WOOL : Blocks.PURPLE_WOOL)); }
        moon(l, c, 6, 16, -RR - 5, 6);
    }

    /**
     * Up in the night sky over the clouds (the user's picture): a shining lavender cloud floor with ripples spreading
     * out where something touched down, stars all around, white swirls of wind and a crescent moon.
     */
    static void nightSky(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("night_sky");
        for (int x = -RR; x <= RR; x++) for (int z = -RR; z <= RR; z++) {
            if (!inside(x, z, RR)) continue;
            double d = Math.sqrt(x * x + (z + 4) * (z + 4));
            if (Math.abs(d - 3) < 0.5 || Math.abs(d - 5.5) < 0.5 || Math.abs(d - 8.5) < 0.5) set(l, c.offset(x, 0, z), b(Blocks.WHITE_STAINED_GLASS));
            if (r.nextInt(16) == 0) set(l, c.offset(x, -1, z), b(Blocks.SEA_LANTERN));      // (sparkles under the cloud floor)
        }
        for (int i = 0; i < 70; i++) {                                                     // stars
            int x = r.nextInt(2 * RR + 6) - RR - 3, z = r.nextInt(2 * RR + 6) - RR - 3, y = 9 + r.nextInt(17);
            set(l, c.offset(x, y, z), b(r.nextInt(4) == 0 ? Blocks.SEA_LANTERN : Blocks.END_ROD));
        }
        for (int[] sw : new int[][]{{-18, 17, -RR - 2}, {19, 15, -RR - 2}, {12, 10, -RR - 4}}) {   // swirls of wind
            for (double t = 0; t < 4 * Math.PI; t += 0.12) {
                double rad = 1 + t * 0.45;
                set(l, c.offset(sw[0] + (int) Math.round(Math.cos(t) * rad), sw[1] + (int) Math.round(Math.sin(t) * rad), sw[2]), b(Blocks.WHITE_STAINED_GLASS));
            }
        }
        for (int i = 0; i < 20; i++) { double a = i * Math.PI * 2 / 20; set(l, c.offset((int) (Math.cos(a) * (RR + 3)), -3, (int) (Math.sin(a) * (RR + 3))), b(Blocks.PURPLE_WOOL)); }
        moon(l, c, -2, 17, -RR - 7, 7);
    }

    // ------------------------------------------------------------------ Aurora: the mall street

    /**
     * Aurora's mall street at night (the user's picture): the Konbini's glass front under its blue sign, the
     * crossing in front of it, bollards, and neon all round - glowing billboards, pink paper lanterns, glass towers.
     */
    static void mall(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("aurora_mall");
        // the crossing and the pavements
        for (int x = -RR; x <= RR; x++) for (int z = -RR; z <= RR; z++) {
            if (!inside(x, z, RR)) continue;
            if (z <= -9 && z >= -11) set(l, c.offset(x, 0, z), b(Blocks.LIGHT_GRAY_CONCRETE));
            else if (z > -9 && z <= 7 && Math.abs(x) <= 6 && (z & 1) == 0) set(l, c.offset(x, 0, z), b(Blocks.WHITE_CONCRETE));
        }
        // the Konbini
        int x0 = -9, x1 = 9, zf = -12, zb = -22;
        for (int x = x0; x <= x1; x++) for (int z = zb; z <= zf; z++) for (int y = 1; y <= 15; y++) {
            boolean side = x == x0 || x == x1 || z == zb || z == zf;
            if (!side) { if (y == 6) set(l, c.offset(x, y, z), b(Blocks.SMOOTH_STONE)); continue; }
            BlockState s;
            if (z == zf && y <= 4) s = b(x == -3 || x == 4 ? Blocks.BLUE_CONCRETE : Blocks.LIGHT_BLUE_STAINED_GLASS);
            else if (z == zf && y == 5) s = b(x < 1 ? Blocks.WHITE_CONCRETE : Blocks.BLUE_CONCRETE);
            else if (y >= 7 && y <= 13 && (x & 1) == 0) s = b(Blocks.LIGHT_BLUE_STAINED_GLASS);
            else s = b(y == 6 || y == 14 ? Blocks.SEA_LANTERN : Blocks.BLUE_TERRACOTTA);
            set(l, c.offset(x, y, z), s);
        }
        fill(l, c, x0, 16, zb, x1, 16, zf, b(Blocks.BLUE_TERRACOTTA));
        fill(l, c, -1, 1, zf, 1, 3, zf, b(Blocks.AIR));                                  // the doors
        sign(l, c.offset(4, 5, zf + 1), Blocks.DARK_OAK_WALL_SIGN, Direction.SOUTH, "", "KONBINI", "", "");
        for (int x = -7; x <= 7; x += 3) { set(l, c.offset(x, 1, -16), b(Blocks.BARREL)); set(l, c.offset(x, 2, -16), b(Blocks.BARREL)); }   // shelves
        fill(l, c, 5, 1, -14, 7, 1, -14, b(Blocks.SMOOTH_QUARTZ));                       // the counter
        for (int x = x0 + 1; x < x1; x += 4) set(l, c.offset(x, 5, -17), b(Blocks.SEA_LANTERN));
        for (int x : new int[]{-7, -4, 5, 8}) set(l, c.offset(x, 1, -11), b(Blocks.POLISHED_BLACKSTONE_WALL));   // bollards
        // left: a tall block of neon billboards and pink lanterns
        for (int x = -26; x <= -12; x++) for (int z = -22; z <= -8; z++) for (int y = 1; y <= 20; y++) {
            if (!inside(x, z, RR - 1)) continue;
            boolean side = x == -26 || x == -12 || z == -22 || z == -8;
            if (!side) continue;
            BlockState s = b(Blocks.BLACK_CONCRETE);
            if (x == -12 || z == -8) {
                if (y >= 8 && y <= 15) s = b((x + y) % 7 < 3 ? Blocks.PINK_CONCRETE : (x + y) % 7 < 5 ? Blocks.CYAN_CONCRETE : Blocks.WHITE_CONCRETE);
                else if (y == 7 || y == 16) s = b(Blocks.SEA_LANTERN);
                else if (y <= 4 && (x & 1) == 0) s = b(Blocks.MAGENTA_STAINED_GLASS);
            }
            set(l, c.offset(x, y, z), s);
        }
        for (int x = -18; x <= -12; x += 2) set(l, c.offset(x, 6, -7), b(Blocks.PEARLESCENT_FROGLIGHT));   // paper lanterns
        for (int z = -14; z <= -8; z += 2) set(l, c.offset(-11, 5, z), b(Blocks.PEARLESCENT_FROGLIGHT));
        // right: a glass tower with a pink-on-white billboard
        for (int x = 12; x <= 26; x++) for (int z = -22; z <= -8; z++) for (int y = 1; y <= 24; y++) {
            if (!inside(x, z, RR - 1)) continue;
            boolean side = x == 12 || x == 26 || z == -22 || z == -8;
            if (!side) continue;
            BlockState s = (x + z) % 3 == 0 ? b(Blocks.GRAY_CONCRETE) : b(Blocks.LIGHT_BLUE_STAINED_GLASS);
            if (x == 12 && y >= 10 && y <= 17) s = b(y % 4 == 0 ? Blocks.MAGENTA_CONCRETE : Blocks.WHITE_CONCRETE);
            set(l, c.offset(x, y, z), s);
        }
        for (int z = -20; z <= -9; z += 4) set(l, c.offset(11, 3, z), b(Blocks.SEA_LANTERN));
        // across the street behind you: lower shops with neon
        for (int x = -24; x <= 24; x++) for (int y = 1; y <= 6; y++) {
            if (!inside(x, 20, RR - 1) || Math.abs(x) <= 6) continue;
            set(l, c.offset(x, y, 20), b(y == 5 ? ((x & 3) == 0 ? Blocks.OCHRE_FROGLIGHT : Blocks.PURPLE_CONCRETE) : y <= 3 && (x & 1) == 0 ? Blocks.BLUE_STAINED_GLASS : Blocks.BLACK_CONCRETE));
        }
        for (int s : new int[]{-1, 1}) for (int z : new int[]{-6, 8}) lamp(l, c, s * 9, z);
    }

    // ------------------------------------------------------------------ the Underclass City

    /**
     * The Underclass City at night (the user's picture): a street of run-down, patched-up concrete houses two and
     * three storeys high, boarded and barred windows with warm light behind some, ladders and fire escapes, satellite
     * dishes and aerials, a bar with its neon sign, a wrecked car, a knocked-over bin, a bench and orange street lamps.
     */
    static void underclass(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("underclass_city");
        for (int x = -RR; x <= RR; x++) for (int z = -RR; z <= RR; z++) {
            if (!inside(x, z, RR)) continue;
            if (z >= -9 && z <= -7) set(l, c.offset(x, 0, z), b(r.nextInt(5) == 0 ? Blocks.COBBLESTONE : Blocks.LIGHT_GRAY_CONCRETE));   // pavement
            else if (z > -7 && z < 12) set(l, c.offset(x, 0, z), b(z == 2 && Math.floorMod(x, 5) < 2 ? Blocks.WHITE_CONCRETE : r.nextInt(9) == 0 ? Blocks.GRAY_CONCRETE_POWDER : Blocks.BLACK_CONCRETE));
        }
        BlockState[] walls = {b(Blocks.CYAN_TERRACOTTA), b(Blocks.LIGHT_GRAY_TERRACOTTA), b(Blocks.GRAY_TERRACOTTA), b(Blocks.STONE_BRICKS)};
        int[][] houses = {{-27, 7, 13}, {-19, 9, 16}, {-9, 6, 9}, {-2, 7, 11}, {6, 8, 12}, {15, 9, 15}};
        for (int i = 0; i < houses.length; i++) {
            int hx = houses[i][0], w = houses[i][1], h = houses[i][2], zf = -10, zb = -10 - 8;
            BlockState wall = walls[r.nextInt(walls.length)];
            for (int x = hx; x <= hx + w; x++) for (int z = zb; z <= zf; z++) {
                if (!inside(x, z, RR - 1)) continue;
                boolean side = x == hx || x == hx + w || z == zb || z == zf;
                for (int y = 1; y <= h; y++) {
                    if (!side) { if (y % 4 == 0) set(l, c.offset(x, y, z), b(Blocks.SPRUCE_PLANKS)); continue; }
                    BlockState s = r.nextInt(9) == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : r.nextInt(12) == 0 ? b(Blocks.MOSSY_COBBLESTONE) : wall;
                    if (z == zf && y % 4 >= 2 && (x - hx) % 3 == 1) {                     // windows: lit, dark, barred or boarded
                        int k = r.nextInt(4);
                        s = k == 0 ? b(Blocks.SHROOMLIGHT) : k == 1 ? b(Blocks.IRON_BARS) : k == 2 ? b(Blocks.SPRUCE_TRAPDOOR) : b(Blocks.BLACK_STAINED_GLASS);
                    }
                    set(l, c.offset(x, y, z), s);
                }
                set(l, c.offset(x, h + 1, z), b(Blocks.POLISHED_ANDESITE_SLAB));
            }
            fill(l, c, hx + w / 2, 1, zf, hx + w / 2, 2, zf, b(Blocks.AIR));            // the door
            if (r.nextBoolean()) fill(l, c, hx + w / 2, h + 2, zf - 3, hx + w / 2, h + 4, zf - 3, b(Blocks.LIGHTNING_ROD));   // aerial
            if (i % 2 == 0) set(l, c.offset(hx + w - 1, h - 2, zf + 1), b(Blocks.DAYLIGHT_DETECTOR));   // a satellite dish (ish)
            if (i % 3 == 1) for (int y = 1; y <= h - 2; y++) set(l, c.offset(hx + 1, y, zf + 1), b(Blocks.LADDER).setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH));
        }
        // the bar
        for (int y = 2; y <= 5; y++) set(l, c.offset(13, y, -9), b(Blocks.RED_NETHER_BRICKS));
        sign(l, c.offset(13, 4, -8), Blocks.CRIMSON_WALL_SIGN, Direction.SOUTH, "", "BAR", "", "");
        set(l, c.offset(13, 6, -9), b(Blocks.REDSTONE_LAMP).setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
        // the wrecked car, a knocked-over bin, a bench, rubbish
        fill(l, c, -12, 1, -5, -8, 1, -4, b(Blocks.GRAY_CONCRETE));
        fill(l, c, -11, 2, -5, -9, 2, -4, b(Blocks.BLACK_STAINED_GLASS));
        for (int[] w : new int[][]{{-12, -6}, {-8, -6}, {-12, -3}, {-8, -3}}) set(l, c.offset(w[0], 1, w[1]), b(Blocks.BLACK_CONCRETE));
        set(l, c.offset(18, 1, -8), b(Blocks.CAULDRON));
        set(l, c.offset(19, 1, -8), b(Blocks.COMPOSTER));
        set(l, c.offset(22, 1, -8), stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        set(l, c.offset(23, 1, -8), stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        for (int i = 0; i < 14; i++) { int x = r.nextInt(50) - 25, z = r.nextInt(18) - 9; if (inside(x, z, RR - 2) && Math.abs(x) > 3) set(l, c.offset(x, 1, z), b(Blocks.WHITE_CARPET)); }
        // orange street lamps
        for (int x = -24; x <= 24; x += 12) {
            fill(l, c, x, 1, -7, x, 5, -7, b(Blocks.POLISHED_BLACKSTONE_WALL));
            set(l, c.offset(x, 6, -7), b(Blocks.OCHRE_FROGLIGHT));
        }
        // the far side of the street: more of the same, lower
        for (int x = -26; x <= 26; x++) {
            if (!inside(x, 14, RR - 1) || Math.abs(x) <= 4) continue;
            int h = 4 + Math.floorMod(x * 7 / 9, 3);
            for (int y = 1; y <= h; y++) set(l, c.offset(x, y, 14), y % 3 == 2 && (x & 3) == 1 ? b(r.nextBoolean() ? Blocks.SHROOMLIGHT : Blocks.BLACK_STAINED_GLASS) : walls[Math.floorMod(x / 6, walls.length)]);
        }
    }

    // ------------------------------------------------------------------ the SR cells: the clock window

    /**
     * Where Rhalix found Sam (the user's picture): the middle of the cells' back wall opens on a huge round clock
     * window - a moonlit face of pale glass with dark spokes, hour marks and hands - gears and chains on the wall round
     * it, a wooden floor and an iron railing in front, and outside, the crescent moon and the stars.
     */
    static void clockWindow(ServerLevel l, BlockPos c, RandomSource r) {
        int wz = -24, cy = 7, rad = 6;
        for (int x = -rad - 1; x <= rad + 1; x++) for (int y = cy - rad - 1; y <= cy + rad + 1; y++) {
            if (y < 1) continue;
            double d = Math.sqrt(x * x + (y - cy) * (y - cy));
            if (d > rad + 0.5) continue;
            BlockState s;
            if (d > rad - 0.6) s = b(Blocks.POLISHED_BLACKSTONE);                            // the frame
            else if (x == 0 || y == cy || (Math.abs(Math.abs(x) - Math.abs(y - cy)) == 0 && d > 2)) s = b(Blocks.BLACK_STAINED_GLASS);   // spokes
            else if (d > rad - 1.6 && Math.round(Math.atan2(y - cy, x) / (Math.PI / 6) * 1000) % 1000 == 0) s = b(Blocks.BLUE_STAINED_GLASS);
            else if (d > rad - 1.6) s = b(Blocks.LIGHT_BLUE_STAINED_GLASS);                  // the ring of numerals
            else s = b(Blocks.WHITE_STAINED_GLASS);
            set(l, c.offset(x, y, wz), s);
        }
        for (int y = cy; y <= cy + 4; y++) set(l, c.offset(0, y, wz + 1), b(Blocks.CHAIN));   // the hands (12 and 2)
        set(l, c.offset(1, cy + 1, wz + 1), b(Blocks.CHAIN)); set(l, c.offset(2, cy + 1, wz + 1), b(Blocks.CHAIN)); set(l, c.offset(3, cy + 2, wz + 1), b(Blocks.CHAIN));
        // gears and chains on the wall
        for (int[] g : new int[][]{{-11, 9, 3}, {11, 10, 3}, {-9, 3, 2}, {10, 3, 2}}) {
            for (int x = -g[2] - 1; x <= g[2] + 1; x++) for (int y = -g[2] - 1; y <= g[2] + 1; y++) {
                double d = Math.sqrt(x * x + y * y);
                boolean tooth = d > g[2] + 0.2 && d <= g[2] + 1.2 && Math.floorMod((int) Math.round(Math.atan2(y, x) * 4), 2) == 0;
                if ((d <= g[2] + 0.2 && d > g[2] - 1) || tooth || d < 0.8) set(l, c.offset(g[0] + x, g[1] + y, wz + 1), b(Blocks.CUT_COPPER));
            }
        }
        for (int x : new int[]{-14, -7, 7, 14}) for (int y = 9; y <= 13; y++) set(l, c.offset(x, y, wz + 2), b(Blocks.CHAIN));
        // the wooden floor and the railing
        for (int x = -10; x <= 10; x++) for (int z = wz + 1; z <= wz + 8; z++) set(l, c.offset(x, 0, z), b(((x + z) & 3) == 0 ? Blocks.SPRUCE_PLANKS : Blocks.DARK_OAK_PLANKS));
        for (int x = -8; x <= 8; x++) set(l, c.offset(x, 1, wz + 2), b(Blocks.IRON_BARS));
        // outside: no floor, the moon and stars
        for (int x = -34; x <= 34; x++) for (int z = -38; z < wz; z++) for (int y = -4; y <= 0; y++) set(l, c.offset(x, y, z), b(Blocks.AIR));
        moon(l, c, -2, 9, wz - 9, 6);
        for (int i = 0; i < 30; i++) set(l, c.offset(r.nextInt(30) - 15, 1 + r.nextInt(16), wz - 4 - r.nextInt(8)), b(Blocks.END_ROD));
    }

    // ------------------------------------------------------------------ the Mountain Village downtown

    /**
     * The Mountain Village's downtown at sunset (the user's picture; where Rhalix and Mikiko held off the corrupted
     * souls): a wide street with a pavement, benches and tall street lamps, and a row of mixed buildings - an old brick
     * block with arched windows, a grey-blue office, a narrow purple one, a teal glass tower, a beige shop block - with
     * taller towers behind.
     */
    static void villageCity(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("village_city");
        for (int x = -RR; x <= RR; x++) for (int z = -RR; z <= RR; z++) {
            if (!inside(x, z, RR)) continue;
            if (z >= -8 && z <= -5) set(l, c.offset(x, 0, z), b(Math.floorMod(x, 5) == 0 ? Blocks.STONE : Blocks.LIGHT_GRAY_CONCRETE));
            else if (z > -5 && z < 14) set(l, c.offset(x, 0, z), b(z == 4 && Math.floorMod(x, 6) < 3 ? Blocks.WHITE_CONCRETE : Blocks.GRAY_CONCRETE));
        }
        // the row: {x0, width, height, kind}
        int[][] row = {{-28, 4, 22, 0}, {-23, 9, 14, 1}, {-13, 8, 10, 2}, {-4, 4, 11, 3}, {1, 10, 18, 4}, {12, 10, 9, 5}, {23, 4, 16, 0}};
        for (int[] bd : row) {
            int x0 = bd[0], w = bd[1], h = bd[2], kind = bd[3], zf = -9, zb = -20;
            BlockState wall = switch (kind) { case 1 -> b(Blocks.GRANITE); case 2 -> b(Blocks.CYAN_TERRACOTTA); case 3 -> b(Blocks.PURPLE_TERRACOTTA); case 4 -> b(Blocks.CYAN_CONCRETE); case 5 -> b(Blocks.BROWN_TERRACOTTA); default -> b(Blocks.GRAY_TERRACOTTA); };
            for (int x = x0; x <= x0 + w; x++) for (int z = zb; z <= zf; z++) {
                if (!inside(x, z, RR - 1)) continue;
                boolean side = x == x0 || x == x0 + w || z == zb || z == zf;
                for (int y = 1; y <= h; y++) {
                    if (!side) continue;
                    BlockState s = wall;
                    int k = x - x0;
                    if (z == zf && y > 1) {
                        if (kind == 1 && y % 4 != 1 && k % 2 == 1) s = y % 4 == 0 ? stair(Blocks.GRANITE_STAIRS, Direction.SOUTH, true) : b(Blocks.LIGHT_BLUE_STAINED_GLASS);   // arched windows
                        else if (kind == 1 && y % 4 == 1) s = b(Blocks.SMOOTH_SANDSTONE);
                        else if (kind == 4 && k % 2 == 1) s = b(Blocks.LIGHT_BLUE_STAINED_GLASS);   // the teal tower's glass strips
                        else if ((kind == 2 || kind == 5 || kind == 0) && y % 3 != 1 && k % 2 == 1) s = b(r.nextInt(5) == 0 ? Blocks.YELLOW_STAINED_GLASS : Blocks.BLUE_STAINED_GLASS);
                        else if (kind == 3 && y % 3 == 0) s = b(Blocks.MAGENTA_TERRACOTTA);
                        else if (kind == 5 && y == h - 1) s = b(Blocks.WHITE_TERRACOTTA);
                    }
                    set(l, c.offset(x, y, z), s);
                }
                set(l, c.offset(x, h + 1, z), b(Blocks.POLISHED_ANDESITE_SLAB));
            }
            fill(l, c, x0 + w / 2, 1, zf, x0 + w / 2, 2, zf, b(Blocks.SPRUCE_PLANKS));    // its door
        }
        for (int[] t : new int[][]{{-18, -26, 30}, {-4, -27, 24}, {10, -26, 26}}) {      // the towers behind
            for (int x = t[0]; x <= t[0] + 7; x++) for (int z = t[1]; z <= t[1] + 4; z++) {
                if (!inside(x, z, RR + 2)) continue;
                for (int y = 1; y <= t[2]; y++) set(l, c.offset(x, y, z), b(z == t[1] + 4 && y % 3 != 0 && (x & 1) == 1 ? Blocks.LIGHT_BLUE_STAINED_GLASS : Blocks.PURPLE_TERRACOTTA));
            }
        }
        for (int x = -22; x <= 22; x += 11) {                                             // street lamps
            fill(l, c, x, 1, -5, x, 6, -5, b(Blocks.ANDESITE_WALL));
            set(l, c.offset(x, 7, -5), b(Blocks.ANDESITE_WALL));
            set(l, c.offset(x, 7, -4), b(Blocks.OCHRE_FROGLIGHT));
        }
        for (int x : new int[]{-26, -16, 16}) { set(l, c.offset(x, 1, -6), stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false)); set(l, c.offset(x + 1, 1, -6), stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false)); }   // benches
    }

    // ------------------------------------------------------------------ the Royal Capital arcade

    /**
     * An arcade in the Royal Capital (the user's picture; where Sam and Max asked a Skylander the way to Kyrie): a
     * long walk of sandstone arches on stout columns with blocky capitals, a low carved wall between them with urns of
     * purple flowers, dark wooden beams overhead and a cream wall behind - and a garden of trees and bushes outside.
     */
    static void arcade(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("capital_arcade");
        for (int z = -RR; z <= RR; z++) for (int x = -5; x <= 6; x++) {
            if (!inside(x, z, RR)) continue;
            set(l, c.offset(x, 0, z), b(((x + z) & 3) == 0 ? Blocks.CUT_SANDSTONE : Blocks.SMOOTH_SANDSTONE));
            set(l, c.offset(x, 7, z), b(Blocks.SMOOTH_SANDSTONE));                       // the roof
            if (Math.floorMod(z, 3) == 0) set(l, c.offset(x, 6, z), b(Blocks.DARK_OAK_LOG).setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));   // beams
            if (x == 6) { fill(l, c, x, 1, z, x, 6, z, b(Blocks.WHITE_TERRACOTTA)); set(l, c.offset(x, 2, z), b(Blocks.SMOOTH_SANDSTONE)); }   // the wall behind
        }
        for (int z = -RR; z <= RR; z++) {
            if (!inside(-6, z, RR)) continue;
            boolean col = Math.floorMod(z, 4) == 0;
            set(l, c.offset(-6, 0, z), b(Blocks.SMOOTH_SANDSTONE));
            set(l, c.offset(-6, 7, z), b(Blocks.SMOOTH_SANDSTONE));
            if (col) {
                set(l, c.offset(-6, 1, z), b(Blocks.CUT_SANDSTONE));
                fill(l, c, -6, 2, z, -6, 4, z, b(Blocks.SANDSTONE_WALL));
                set(l, c.offset(-6, 5, z), b(Blocks.CHISELED_SANDSTONE));
                set(l, c.offset(-6, 6, z), b(Blocks.SMOOTH_SANDSTONE));
            } else {
                int k = Math.floorMod(z, 4);
                set(l, c.offset(-6, 1, z), slab(Blocks.SMOOTH_SANDSTONE_SLAB, false));   // the low wall
                set(l, c.offset(-6, 6, z), k == 2 ? b(Blocks.SMOOTH_SANDSTONE) : stair(Blocks.SANDSTONE_STAIRS, k == 1 ? Direction.NORTH : Direction.SOUTH, true));   // the arches
                if (k == 2 && Math.floorMod(z, 8) == 2) set(l, c.offset(-6, 2, z), b(Blocks.POTTED_ALLIUM));   // urns of flowers
            }
        }
        // the garden outside
        for (int[] t : new int[][]{{-14, -14}, {-20, 2}, {-13, 12}, {-24, -8}}) {
            int h = 5 + r.nextInt(2);
            fill(l, c, t[0], 1, t[1], t[0], h, t[1], b(Blocks.OAK_LOG));
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -1; dy <= 1; dy++)
                if (dx * dx + dz * dz + dy * dy * 3 <= 10) set(l, c.offset(t[0] + dx, h + dy, t[1] + dz), leaves(Blocks.OAK_LEAVES));
        }
        for (int[] p : new int[][]{{-9, -6}, {-10, 6}, {-17, -20}, {-8, 18}, {-19, 14}}) bush(l, c, p[0], p[1], r);
        for (int i = 0; i < 40; i++) { int x = -8 - r.nextInt(20), z = r.nextInt(2 * RR) - RR; if (inside(x, z, RR - 1)) set(l, c.offset(x, 1, z), b(i % 5 == 0 ? Blocks.ALLIUM : Blocks.GRASS)); }
        for (int z = -RR; z <= RR; z++) for (int x = 7; x <= RR; x++) if (inside(x, z, RR)) set(l, c.offset(x, 0, z), b(Blocks.SMOOTH_SANDSTONE));   // (the palace side)
    }

    // ------------------------------------------------------------------ Kitsune's Farm Store and Mikiko's room

    /**
     * Kitsune's Farm Store (the user's picture): warm walls and a wooden floor, the counter with the till and trays of
     * vegetables, shelves of sauces, eggs, milk and cup noodles behind it, crates of pineapples, a pot plant, a mop and
     * bucket, the glass door - and the stairs up to Mikiko's room: peach walls, a wooden bed with blue sheets, the
     * nightstand with a lamp and his game console, the window with blinds onto the trees, a big wardrobe with a mirror,
     * his desk with the computer and chair, sticky notes, a rug.
     */
    static void farmStore(ServerLevel l, BlockPos c, RandomSource r) {
        int x0 = 4, x1 = 17, z0 = -18, z1 = -6;
        BlockState wall = b(Blocks.SMOOTH_SANDSTONE), wood = b(Blocks.SPRUCE_PLANKS);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean side = x == x0 || x == x1 || z == z0 || z == z1;
            for (int y = 0; y <= 10; y++) {
                if (y == 0 || y == 5) set(l, c.offset(x, y, z), side ? wood : b(y == 0 ? Blocks.OAK_PLANKS : Blocks.ACACIA_PLANKS));
                else if (y == 10) set(l, c.offset(x, y, z), b(Blocks.RED_TERRACOTTA));
                else if (side) set(l, c.offset(x, y, z), y > 5 ? b(Blocks.WHITE_TERRACOTTA) : wall);
                else set(l, c.offset(x, y, z), b(Blocks.AIR));
            }
        }
        // the door (glass) and windows
        fill(l, c, 9, 1, z1, 10, 2, z1, b(Blocks.AIR));
        fill(l, c, 9, 3, z1, 10, 3, z1, b(Blocks.GLASS));
        fill(l, c, 13, 2, z1, 15, 3, z1, b(Blocks.GLASS));
        // the stairs up (west side, going north)
        for (int k = 0; k < 4; k++) { set(l, c.offset(x0 + 1, 1 + k, z1 - 2 - k), stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false)); set(l, c.offset(x0 + 1, 5, z1 - 2 - k), b(Blocks.AIR)); }
        set(l, c.offset(x0 + 1, 5, z1 - 6), b(Blocks.AIR));
        for (int k = 0; k < 4; k++) set(l, c.offset(x0 + 2, 2 + k, z1 - 2 - k), b(Blocks.SPRUCE_FENCE));
        // the counter, the till, the trays
        fill(l, c, 9, 1, -12, 14, 1, -12, b(Blocks.STRIPPED_SPRUCE_WOOD));
        set(l, c.offset(11, 2, -12), b(Blocks.LOOM));
        set(l, c.offset(9, 2, -12), b(Blocks.POTTED_FERN));
        set(l, c.offset(13, 2, -12), b(Blocks.COMPOSTER));
        // the shelves behind (sauces, eggs, milk, cup noodles)
        for (int x = 8; x <= 15; x++) {
            set(l, c.offset(x, 2, z0 + 1), slab(Blocks.SPRUCE_SLAB, true));
            set(l, c.offset(x, 3, z0 + 1), b(x % 3 == 0 ? Blocks.RED_CANDLE : x % 3 == 1 ? Blocks.TURTLE_EGG : Blocks.WHITE_CANDLE));
            set(l, c.offset(x, 3, z0 + 1).above(), b(Blocks.AIR));
            set(l, c.offset(x, 4, z0 + 1), slab(Blocks.SPRUCE_SLAB, false));
        }
        // crates of pineapples, the mop and bucket
        for (int z = -11; z <= -9; z++) { set(l, c.offset(x0 + 3, 1, z), b(Blocks.BARREL)); set(l, c.offset(x0 + 3, 2, z), b(Blocks.MELON)); }
        set(l, c.offset(16, 1, -16), b(Blocks.CAULDRON)); set(l, c.offset(16, 1, -15), b(Blocks.LIGHTNING_ROD));
        set(l, c.offset(10, 4, -10), hanging(Blocks.LANTERN));
        // upstairs: Mikiko's room
        for (int x = x0 + 4; x < x1; x++) for (int z = z0 + 1; z < z1; z++) if (x >= 9 && x <= 13 && z >= -14 && z <= -10) set(l, c.offset(x, 5, z), b(Blocks.PINK_TERRACOTTA));   // the rug (in the floor)
        set(l, c.offset(7, 6, -16), b(Blocks.LIGHT_BLUE_BED).setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH).setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        set(l, c.offset(7, 6, -15), b(Blocks.LIGHT_BLUE_BED).setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH).setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        set(l, c.offset(8, 6, -17), b(Blocks.MANGROVE_PLANKS)); set(l, c.offset(8, 7, -17), b(Blocks.LANTERN));   // nightstand and lamp
        set(l, c.offset(9, 6, -17), b(Blocks.MANGROVE_PLANKS)); set(l, c.offset(9, 7, -17), slab(Blocks.BLACKSTONE_SLAB, false));   // (his game console)
        fill(l, c, 10, 7, z0, 13, 8, z0, b(Blocks.GLASS));                               // the window
        for (int x = 10; x <= 13; x++) set(l, c.offset(x, 8, z0 + 1), b(Blocks.SPRUCE_TRAPDOOR).setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, Half.TOP));   // blinds, half down
        fill(l, c, 15, 6, -17, 16, 8, -16, b(Blocks.ACACIA_PLANKS));                     // the wardrobe
        set(l, c.offset(15, 7, -15), b(Blocks.WHITE_STAINED_GLASS));                     // its mirror
        fill(l, c, 15, 6, -12, 16, 6, -9, b(Blocks.SMOOTH_SANDSTONE));                   // the desk
        set(l, c.offset(16, 7, -11), b(Blocks.BLACK_CONCRETE)); set(l, c.offset(16, 7, -10), b(Blocks.BLACK_CONCRETE));   // the computer
        set(l, c.offset(14, 6, -10), stair(Blocks.POLISHED_ANDESITE_STAIRS, Direction.WEST, false));   // the chair
        set(l, c.offset(16, 8, -12), b(Blocks.LIME_CONCRETE)); set(l, c.offset(16, 8, -9), b(Blocks.ORANGE_CONCRETE));   // sticky notes
        set(l, c.offset(11, 9, -12), b(Blocks.SHROOMLIGHT));                             // the ceiling light
        // the store's sign
        sign(l, c.offset(12, 4, z1 + 1), Blocks.OAK_WALL_SIGN, Direction.SOUTH, "Kitsune's", "Farm Store", "", "");
    }

    // ------------------------------------------------------------------ the Government gas room

    /**
     * The room where Technica and Max set up the gases (the user's picture): dark metal walls with grey pipes running
     * along them, junction boxes with glowing bulbs, vents lit cyan, and a metal plate floor.
     */
    static void gasRoom(ServerLevel l, BlockPos c, RandomSource r) {
        for (int x = -17; x <= 17; x++) for (int z = -17; z <= -7; z++) set(l, c.offset(x, 0, z), b(Math.floorMod(x + z, 5) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE));
        for (int x = -18; x <= 18; x++) for (int y = 1; y <= 7; y++) set(l, c.offset(x, y, -18), b(Blocks.POLISHED_DEEPSLATE));
        for (int z = -17; z <= -7; z++) for (int y = 1; y <= 7; y++) { set(l, c.offset(-18, y, z), b(Blocks.POLISHED_DEEPSLATE)); set(l, c.offset(18, y, z), b(Blocks.POLISHED_DEEPSLATE)); }
        // pipes along the back wall (in front of it)
        for (int x = -17; x <= 17; x++) { set(l, c.offset(x, 6, -17), b(Blocks.POLISHED_ANDESITE)); if (x % 7 != 0) set(l, c.offset(x, 5, -17), b(Blocks.AIR)); }
        for (int x : new int[]{-10, -3, 4, 11}) fill(l, c, x, 5, -17, x, 5, -17, b(Blocks.POLISHED_ANDESITE));
        for (int x = -17; x <= 17; x += 12) fill(l, c, x, 1, -17, x, 6, -17, b(Blocks.POLISHED_ANDESITE));   // pipes coming down
        // junction boxes with bulbs, vents with cyan light (on the side walls)
        for (int s : new int[]{-17, 17}) {
            for (int z : new int[]{-15, -12}) { fill(l, c, s, 2, z, s, 4, z, b(Blocks.IRON_BLOCK)); set(l, c.offset(s, 3, z), b(Blocks.OCHRE_FROGLIGHT)); set(l, c.offset(s, 4, z), b(Blocks.OCHRE_FROGLIGHT)); }
            for (int z = -10; z <= -8; z++) { set(l, c.offset(s, 5, z), b(Blocks.SEA_LANTERN)); set(l, c.offset(s, 6, z), b(Blocks.IRON_BLOCK)); }
        }
        for (int x = -8; x <= 8; x += 8) set(l, c.offset(x, 7, -18), b(Blocks.SEA_LANTERN));
    }

    // ------------------------------------------------------------------ the Akuma Squad's hideout

    /**
     * The Akuma Squad's HQ (the user's pictures): a riveted metal-walled basement strung with bare bulbs - bunk beds,
     * yellow lockers, a worn armchair, a filing cabinet with a radio and an old TV, a hot plate with a kettle, the
     * fridge, shelves of tins and boxes, a generator, a gas bottle, a blue drum, papers pinned all over - and through
     * the wall, the room upstairs: grey brick, a desk with radio sets, a cork board, a clock, a TV on a bracket, a shelf
     * of boxes, more lockers, strip lights and the hatch down.
     */
    static void akumaHq(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("akuma_hq");
        BlockState metal = b(Blocks.GRAY_CONCRETE), rivet = b(Blocks.IRON_BLOCK), brick = b(Blocks.STONE_BRICKS);
        // the basement (west) and the upstairs room (east), side by side
        box(l, c, -24, -18, 2, 6, 7, metal, b(Blocks.POLISHED_DEEPSLATE), null);
        box(l, c, 2, -18, 22, 6, 7, brick, b(Blocks.SMOOTH_STONE), null);
        fill(l, c, -2, 1, 6, 2, 3, 6, b(Blocks.AIR));                                     // the way in
        fill(l, c, 2, 1, -6, 2, 3, -4, b(Blocks.AIR));                                    // through to the office
        for (int x = -23; x <= 1; x++) for (int z = -17; z <= 5; z++) set(l, c.offset(x, 0, z), b(((x ^ z) & 3) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE));
        for (int x = 3; x <= 21; x++) for (int z = -17; z <= 5; z++) set(l, c.offset(x, 0, z), b(((x + z) & 1) == 0 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.GRAY_CONCRETE));
        for (int x = -24; x <= 2; x += 4) for (int y = 1; y <= 7; y++) set(l, c.offset(x, y, -18), rivet);   // riveted ribs
        // bare bulbs on a wire
        for (int x = -21; x <= -1; x += 5) set(l, c.offset(x, 7, -8), hanging(Blocks.LANTERN));
        // bunk beds, lockers, armchair
        for (int y : new int[]{1, 3}) {
            set(l, c.offset(-23, y, -16), b(y == 1 ? Blocks.PURPLE_BED : Blocks.GREEN_BED).setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH).setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
            set(l, c.offset(-23, y, -15), b(y == 1 ? Blocks.PURPLE_BED : Blocks.GREEN_BED).setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH).setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        }
        fill(l, c, -22, 2, -16, -22, 2, -15, b(Blocks.IRON_BARS));
        for (int x = -21; x <= -19; x++) fill(l, c, x, 1, -17, x, 3, -17, b(Blocks.YELLOW_CONCRETE));   // lockers
        set(l, c.offset(-16, 1, -14), stair(Blocks.ACACIA_STAIRS, Direction.SOUTH, false));   // the armchair
        set(l, c.offset(-17, 1, -14), b(Blocks.ACACIA_TRAPDOOR).setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true).setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.WEST));
        set(l, c.offset(-15, 1, -14), b(Blocks.ACACIA_TRAPDOOR).setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true).setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.EAST));
        // filing cabinet with the radio and TV, papers
        fill(l, c, -13, 1, -17, -10, 2, -17, b(Blocks.SMOOTH_STONE));
        set(l, c.offset(-12, 3, -17), b(Blocks.NOTE_BLOCK)); set(l, c.offset(-11, 3, -17), b(Blocks.OBSERVER).setValue(net.minecraft.world.level.block.ObserverBlock.FACING, Direction.NORTH));
        for (int[] p : new int[][]{{-14, 5}, {-12, 6}, {-9, 5}, {-11, 4}, {-6, 6}}) set(l, c.offset(p[0], p[1], -17), b(Blocks.WHITE_CONCRETE));
        // the hot plate and kettle, fridge, shelves, generator, gas, drum, ladder, stool
        set(l, c.offset(-7, 1, -17), b(Blocks.SPRUCE_FENCE)); set(l, c.offset(-7, 2, -17), b(Blocks.SMOKER)); set(l, c.offset(-7, 3, -17), b(Blocks.CAMPFIRE).setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
        fill(l, c, -5, 1, -17, -5, 3, -17, b(Blocks.WHITE_CONCRETE));                    // the fridge
        for (int y = 1; y <= 5; y += 2) fill(l, c, -3, y, -17, -1, y, -17, b(Blocks.BARREL));   // shelves of tins
        fill(l, c, -3, 2, -17, -1, 2, -17, b(Blocks.IRON_BARS)); fill(l, c, -3, 4, -17, -1, 4, -17, b(Blocks.IRON_BARS));
        set(l, c.offset(-3, 1, -15), b(Blocks.REDSTONE_BLOCK));                          // the generator
        set(l, c.offset(-1, 1, -15), b(Blocks.RED_CONCRETE)); set(l, c.offset(-1, 2, -15), b(Blocks.LIGHTNING_ROD));   // the gas bottle
        fill(l, c, 0, 1, -16, 0, 2, -16, b(Blocks.BLUE_CONCRETE));                       // the drum
        for (int y = 1; y <= 6; y++) set(l, c.offset(1, y, -12), b(Blocks.LADDER).setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST));
        set(l, c.offset(-6, 1, -12), b(Blocks.CRIMSON_FENCE)); set(l, c.offset(-6, 2, -12), slab(Blocks.CRIMSON_SLAB, false));
        // upstairs: desk with radio sets, cork board, clock, TV, shelf, lockers, strip lights, the hatch
        fill(l, c, 5, 1, -17, 8, 1, -16, b(Blocks.ACACIA_PLANKS));
        set(l, c.offset(5, 2, -17), b(Blocks.LANTERN)); set(l, c.offset(6, 2, -17), b(Blocks.JUKEBOX)); set(l, c.offset(7, 2, -17), b(Blocks.TARGET));
        set(l, c.offset(7, 1, -15), stair(Blocks.ACACIA_STAIRS, Direction.NORTH, false));
        fill(l, c, 5, 3, -18, 9, 5, -18, b(Blocks.ORANGE_TERRACOTTA));                    // the cork board
        set(l, c.offset(6, 4, -17), b(Blocks.WHITE_CONCRETE)); set(l, c.offset(8, 4, -17), b(Blocks.YELLOW_CONCRETE));
        set(l, c.offset(11, 4, -17), b(Blocks.TARGET));                                  // the clock
        fill(l, c, 13, 4, -17, 14, 5, -17, b(Blocks.LIME_STAINED_GLASS));                // the TV
        for (int y = 1; y <= 5; y++) fill(l, c, 16, y, -17, 17, y, -17, y % 2 == 1 ? slab(Blocks.SMOOTH_STONE_SLAB, false) : b(Blocks.BARREL));
        fill(l, c, 19, 1, -17, 20, 3, -17, b(Blocks.YELLOW_TERRACOTTA));                 // lockers
        for (int x = 6; x <= 18; x += 6) set(l, c.offset(x, 7, -6), b(Blocks.SHROOMLIGHT));
        for (int x = 3; x <= 21; x++) set(l, c.offset(x, 6, -17), b(Blocks.POLISHED_ANDESITE));   // the pipe along the wall
        fill(l, c, 10, 0, -6, 13, 0, -4, b(Blocks.IRON_TRAPDOOR));                       // the hatch
        for (int[] p : new int[][]{{9, -10}, {11, -9}}) set(l, c.offset(p[0], 1, p[1]), b(Blocks.SMOOTH_SANDSTONE));   // cardboard boxes
    }

    // ------------------------------------------------------------------ the lab hallway (the sliding door)

    /**
     * A hallway in the lab (the user's picture; Gracie and Euphoria sat by this door while Yuri worked on her brother):
     * light grey metal panel walls, a big metal sliding door with teal windows and a yellow-and-black hazard stripe
     * across it, light strips each side and over it, a keypad beside it, vents along the floor - and the lab through it.
     */
    static void labHallway(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("lab_hallway");
        BlockState panel = b(Blocks.LIGHT_GRAY_CONCRETE), seam = b(Blocks.SMOOTH_STONE), frame = b(Blocks.GRAY_CONCRETE);
        // the hallway: walls each side and the door wall at the north end
        for (int z = -RR; z <= RR; z++) for (int x : new int[]{-8, 8}) {
            if (!inside(x, z, RR)) continue;
            for (int y = 1; y <= 7; y++) set(l, c.offset(x, y, z), y == 1 && Math.floorMod(z, 2) == 0 ? b(Blocks.IRON_TRAPDOOR) : Math.floorMod(z, 5) == 0 ? seam : panel);
        }
        for (int x = -8; x <= 8; x++) for (int z = -RR; z <= RR; z++) { if (inside(x, z, RR)) { set(l, c.offset(x, 0, z), b(Math.floorMod(z, 4) == 0 ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE)); set(l, c.offset(x, 8, z), panel); } }
        for (int z = -RR + 2; z <= RR - 2; z += 6) set(l, c.offset(0, 8, z), b(Blocks.SEA_LANTERN));
        int dz = -10;
        for (int x = -8; x <= 8; x++) for (int y = 1; y <= 7; y++) set(l, c.offset(x, y, dz), Math.abs(x) % 5 == 0 ? seam : panel);
        // the door: frame, teal windows, hazard stripe, light strips, keypad
        for (int x = -4; x <= 4; x++) for (int y = 1; y <= 6; y++) {
            BlockState s;
            if (Math.abs(x) == 4 || y == 6) s = frame;
            else if (y == 3) s = b(Math.floorMod(x + y, 2) == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE);
            else if (x == 0) s = b(Blocks.IRON_BLOCK);
            else s = b(y == 1 ? Blocks.IRON_BLOCK : Blocks.CYAN_STAINED_GLASS);
            set(l, c.offset(x, y, dz), s);
        }
        for (int x : new int[]{-5, 5}) fill(l, c, x, 2, dz + 1, x, 4, dz + 1, b(Blocks.END_ROD));
        fill(l, c, -2, 7, dz + 1, 2, 7, dz + 1, b(Blocks.SEA_LANTERN));
        set(l, c.offset(6, 3, dz + 1), b(Blocks.POLISHED_BLACKSTONE_BUTTON).setValue(net.minecraft.world.level.block.ButtonBlock.FACING, Direction.SOUTH));
        set(l, c.offset(6, 4, dz), b(Blocks.LIGHT_BLUE_CONCRETE));
        // somewhere to sit by the door
        for (int x : new int[]{-6, -5}) set(l, c.offset(x, 1, dz + 2), stair(Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH, false));
        // the lab beyond (glimpsed through the windows): Ultima's table and screens
        fill(l, c, -2, 1, dz - 6, 2, 1, dz - 4, b(Blocks.IRON_BLOCK));
        fill(l, c, -3, 2, dz - 9, 3, 4, dz - 9, b(Blocks.CYAN_STAINED_GLASS));
        for (int x = -6; x <= 6; x += 6) set(l, c.offset(x, 7, dz - 5), b(Blocks.SEA_LANTERN));
        fill(l, c, -8, 1, dz - 12, 8, 7, dz - 12, panel);
    }

    // ------------------------------------------------------------------ the Keepers' Palace

    /**
     * The Keepers' Palace (the user's pictures). Outside: a long tan mansion behind a white compound wall with red
     * trim - arched bays and green shuttered windows below, a balcony with white balusters all along the upper floor,
     * red-and-cream bands, a spiked parapet and a small tower room on top, steps and a dirt path up to the open green
     * doors, trees each side. Inside: a tall hall with a grey floor, white columns with banded brown feet, a gallery
     * with balusters round three sides, green doors, peach walls with red skirting and a chandelier.
     */
    static void keepersPalace(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("keepers_palace");
        BlockState tan = b(Blocks.SMOOTH_SANDSTONE), band = b(Blocks.RED_TERRACOTTA), cream = b(Blocks.WHITE_TERRACOTTA), green = b(Blocks.GREEN_TERRACOTTA);
        // the yard: grass, a dirt path to the steps, the compound wall
        for (int x = -RR; x <= RR; x++) for (int z = -RR; z <= RR; z++) {
            if (!inside(x, z, RR)) continue;
            if (Math.abs(x) <= 2 && z > -2) set(l, c.offset(x, 0, z), b(Blocks.DIRT_PATH));
            else if (z > -2 && r.nextInt(3) == 0) set(l, c.offset(x, 0, z), b(Blocks.COARSE_DIRT));
        }
        for (int x = -RR; x <= RR; x++) for (int z = -RR; z <= RR; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > RR - 1.5 || d < RR - 2.5) continue;
            if (z > 10 && Math.abs(x) <= 3) continue;                                   // (the gate)
            set(l, c.offset(x, 1, z), band); set(l, c.offset(x, 2, z), b(Blocks.WHITE_CONCRETE)); set(l, c.offset(x, 3, z), band);
        }
        // the house: x -24..24, z -24..-4; ground floor y1..6, upper y7..12, parapet 13; the middle part is deeper (porch)
        int x0 = -24, x1 = 24, zb = -24, zf = -4;
        for (int x = x0; x <= x1; x++) for (int z = zb; z <= zf; z++) {
            if (!inside(x, z, RR - 1)) continue;
            boolean side = x == x0 || x == x1 || z == zb || z == zf;
            set(l, c.offset(x, 0, z), b(((x + z) & 1) == 0 ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE));   // (the hall floor)
            for (int y = 1; y <= 13; y++) {
                BlockState s = null;
                if (y == 7 || y == 13) s = side ? band : (y == 7 && (Math.abs(x) > 14 || z > -10 || z < -18) ? b(Blocks.SPRUCE_PLANKS) : y == 13 ? cream : null);   // floors: the gallery round the hall, the roof
                else if (side) s = y == 6 || y == 12 ? cream : tan;
                if (s != null) set(l, c.offset(x, y, z), s);
            }
            if (side && (x + z) % 2 == 0) set(l, c.offset(x, 14, z), b(Blocks.SANDSTONE_WALL));   // the spiked parapet
        }
        // the front: arched bays with green shutters below, the balcony above
        for (int x = x0 + 1; x < x1; x++) {
            int k = Math.floorMod(x - x0, 6);
            if (k == 0 && Math.abs(x) > 3) { fill(l, c, x, 1, zf + 1, x, 6, zf + 1, b(Blocks.QUARTZ_PILLAR)); set(l, c.offset(x, 1, zf + 1), b(Blocks.STRIPPED_SPRUCE_LOG)); }
            if (k == 2 || k == 4) {                                                      // shuttered windows, both floors
                for (int y : new int[]{3, 9}) { set(l, c.offset(x, y, zf), b(Blocks.SPRUCE_TRAPDOOR)); set(l, c.offset(x, y + 1, zf), b(Blocks.SPRUCE_TRAPDOOR)); }
                set(l, c.offset(x - 1, 3, zf), green); set(l, c.offset(x + 1, 3, zf), green); set(l, c.offset(x - 1, 9, zf), green); set(l, c.offset(x + 1, 9, zf), green);
            }
            if (k == 3) set(l, c.offset(x, 5, zf), stair(Blocks.SANDSTONE_STAIRS, Direction.SOUTH, true));   // (the arch tops)
            set(l, c.offset(x, 7, zf + 1), b(Blocks.SMOOTH_SANDSTONE_SLAB));             // the balcony floor
            set(l, c.offset(x, 8, zf + 2), b(Blocks.DIORITE_WALL));                      // its balusters
            set(l, c.offset(x, 7, zf + 2), b(Blocks.SMOOTH_SANDSTONE_SLAB));
        }
        // the doors (open, green), the steps, the tower room on top
        fill(l, c, -2, 1, zf, 2, 4, zf, b(Blocks.AIR));
        for (int s : new int[]{-3, 3}) fill(l, c, s, 1, zf + 1, s, 4, zf + 1, green);
        fill(l, c, -2, 7, zf, 2, 10, zf, b(Blocks.AIR));
        for (int x = -3; x <= 3; x++) set(l, c.offset(x, 1, zf + 1), x == -3 || x == 3 ? green : slab(Blocks.SPRUCE_SLAB, false));
        for (int x = -8; x <= 8; x++) for (int z = -18; z <= -10; z++) {
            boolean side = x == -8 || x == 8 || z == -18 || z == -10;
            for (int y = 14; y <= 19; y++) if (side) set(l, c.offset(x, y, z), y == 19 ? band : y == 18 ? cream : tan);
            set(l, c.offset(x, 20, z), cream);
        }
        fill(l, c, -1, 14, -10, 1, 16, -10, b(Blocks.GREEN_TERRACOTTA));
        // inside: the hall - columns, gallery railing, green doors, red skirting, sconces, the chandelier
        for (int x = -14; x <= 14; x += 7) for (int z : new int[]{-18, -10}) {
            set(l, c.offset(x, 1, z), b(Blocks.STRIPPED_SPRUCE_LOG));
            fill(l, c, x, 2, z, x, 12, z, b(Blocks.QUARTZ_PILLAR));
        }
        for (int x = -14; x <= 14; x++) for (int z = -18; z <= -10; z++) {
            boolean edge = Math.abs(x) == 14 || z == -18 || z == -10;
            if (edge && !(Math.abs(x) % 7 == 0 && (z == -18 || z == -10))) set(l, c.offset(x, 8, z), b(Blocks.DIORITE_WALL));   // the gallery railing
        }
        for (int x = x0 + 1; x < x1; x++) { set(l, c.offset(x, 1, zb + 1), band); }
        for (int z = zb + 1; z < zf; z++) { set(l, c.offset(x0 + 1, 1, z), band); set(l, c.offset(x1 - 1, 1, z), band); }
        for (int s : new int[]{-1, 1}) { fill(l, c, s * 23, 1, -16, s * 23, 4, -13, green); fill(l, c, s * 23, 1, -15, s * 23, 4, -14, b(Blocks.AIR)); }
        fill(l, c, -1, 1, zb + 1, 1, 4, zb + 1, green);
        for (int x : new int[]{-6, 6}) set(l, c.offset(x, 4, zb + 1), b(Blocks.SOUL_LANTERN));
        for (int y = 9; y <= 12; y++) set(l, c.offset(0, y, -14), b(Blocks.CHAIN));
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (Math.abs(dx) + Math.abs(dz) >= 2 && Math.abs(dx) + Math.abs(dz) <= 3) set(l, c.offset(dx, 8, -14 + dz), b(Blocks.LANTERN));
        set(l, c.offset(0, 8, -14), b(Blocks.GOLD_BLOCK));
        // trees in the yard
        for (int[] t : new int[][]{{-22, 6}, {22, 6}, {-18, 14}, {17, 15}}) {
            int h = 5 + r.nextInt(3);
            fill(l, c, t[0], 1, t[1], t[0], h, t[1], b(Blocks.JUNGLE_LOG));
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 1; dy++)
                if (Math.abs(dx) + Math.abs(dz) <= 3 - dy) set(l, c.offset(t[0] + dx, h + dy, t[1] + dz), leaves(Blocks.JUNGLE_LEAVES));
        }
    }

    // ------------------------------------------------------------------ the Highway of Broken Dreams

    /**
     * The Highway of Broken Dreams (the user's picture; where Rhalix and Mikiko fought Ultima at the end of Season 1
     * and the start of Season 2): red-brown earth under a broken elevated highway on concrete legs - its deck snapped
     * in the middle, the rebar hanging out - gutted grey tower blocks behind, abandoned tanks, rubble, a road barrier, a
     * smashed guard booth, a wrecked car, a dry yellow tree, and red rock cliffs on either side.
     */
    static void highway(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("battlefield");
        for (int x = -RR; x <= RR; x++) for (int z = -RR; z <= RR; z++) {
            if (!inside(x, z, RR)) continue;
            int k = r.nextInt(6);
            set(l, c.offset(x, 0, z), b(k == 0 ? Blocks.COARSE_DIRT : k == 1 ? Blocks.BROWN_TERRACOTTA : Blocks.TERRACOTTA));
        }
        // the elevated highway, east-west across the north, broken in the middle
        for (int x = -RR; x <= RR; x++) {
            if (Math.abs(x - 3) <= 3) continue;                                          // (the gap)
            for (int z = -14; z <= -8; z++) {
                if (!inside(x, z, RR)) continue;
                set(l, c.offset(x, 8, z), b(z == -11 && Math.floorMod(x, 4) < 2 ? Blocks.YELLOW_CONCRETE : Blocks.GRAY_CONCRETE));
                set(l, c.offset(x, 7, z), b(Blocks.LIGHT_GRAY_CONCRETE));
                if (z == -14 || z == -8) set(l, c.offset(x, 9, z), slab(Blocks.SMOOTH_STONE_SLAB, false));
            }
            if (Math.floorMod(x, 8) == 0) for (int z : new int[]{-13, -9}) if (inside(x, z, RR)) fill(l, c, x, 1, z, x, 6, z, b(Blocks.LIGHT_GRAY_CONCRETE));   // legs
        }
        for (int z = -14; z <= -8; z += 2) { set(l, c.offset(-1, 7, z), b(Blocks.IRON_BARS)); set(l, c.offset(7, 7, z), b(Blocks.IRON_BARS)); set(l, c.offset(-1, 6, z), b(Blocks.CHAIN)); }   // rebar
        // gutted tower blocks behind
        for (int[] t : new int[][]{{-20, -26, 16, 7}, {-8, -28, 26, 9}, {12, -26, 12, 6}}) {
            for (int x = t[0]; x <= t[0] + t[3]; x++) for (int z = t[1]; z <= t[1] + 4; z++) {
                if (!inside(x, z, RR + 3)) continue;
                int top = t[2] - Math.abs(Math.floorMod(x * 7, 5) - 2) * 2;               // (a ragged top)
                for (int y = 1; y <= top; y++) {
                    boolean win = z == t[1] + 4 && y % 3 == 2 && (x - t[0]) % 3 == 1;
                    set(l, c.offset(x, y, z), win ? b(r.nextBoolean() ? Blocks.AIR : Blocks.BLACK_CONCRETE) : b(r.nextInt(8) == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.GRAY_CONCRETE));
                }
            }
        }
        // red rock cliffs on both sides
        for (int s2 : new int[]{-1, 1}) for (int i = 0; i < 40; i++) {
            int x = s2 * (24 + r.nextInt(9)), z = r.nextInt(50) - 30, h = 3 + r.nextInt(9);
            if (!inside(x, z, RR - 1)) continue;
            fill(l, c, x, 1, z, x, h, z, b(r.nextBoolean() ? Blocks.TERRACOTTA : Blocks.BROWN_TERRACOTTA));
        }
        // three abandoned tanks
        for (int[] t : new int[][]{{-18, -4, 1}, {-12, -7, -1}, {-14, 1, 1}}) {
            fill(l, c, t[0] - 2, 1, t[1] - 1, t[0] + 2, 1, t[1] + 1, b(Blocks.BLACK_CONCRETE));   // tracks
            fill(l, c, t[0] - 2, 2, t[1] - 1, t[0] + 2, 2, t[1] + 1, b(Blocks.GREEN_TERRACOTTA));
            fill(l, c, t[0] - 1, 3, t[1], t[0] + 1, 3, t[1], b(Blocks.GREEN_TERRACOTTA));    // the turret
            fill(l, c, t[0] + 2 * t[2], 3, t[1], t[0] + 4 * t[2], 3, t[1], b(Blocks.POLISHED_BLACKSTONE_WALL));   // the gun
        }
        // rubble, the barrier, the guard booth, the car, the tree
        for (int i = 0; i < 26; i++) {
            int x = r.nextInt(44) - 22, z = r.nextInt(30) - 18;
            if (!inside(x, z, RR - 3) || (Math.abs(x) < 4 && z > 0)) continue;
            set(l, c.offset(x, 1, z), b(r.nextBoolean() ? Blocks.COBBLESTONE : Blocks.GRAY_CONCRETE));
            if (r.nextInt(3) == 0) set(l, c.offset(x, 2, z), b(Blocks.COBBLESTONE_SLAB));
        }
        set(l, c.offset(-5, 1, -3), b(Blocks.RED_CONCRETE)); set(l, c.offset(-4, 1, -3), b(Blocks.WHITE_CONCRETE)); set(l, c.offset(-3, 1, -3), b(Blocks.RED_CONCRETE));
        box(l, c, 8, -5, 11, -2, 3, b(Blocks.WHITE_CONCRETE), b(Blocks.LIGHT_GRAY_CONCRETE), b(Blocks.GLASS));
        fill(l, c, 8, 1, -3, 8, 2, -3, b(Blocks.AIR));
        set(l, c.offset(10, 4, -4), b(Blocks.IRON_BARS)); set(l, c.offset(10, 5, -4), b(Blocks.IRON_BARS)); set(l, c.offset(10, 6, -4), b(Blocks.IRON_BARS));   // the bent mast
        fill(l, c, 17, 1, 1, 19, 1, 2, b(Blocks.BROWN_TERRACOTTA)); fill(l, c, 17, 2, 1, 18, 2, 2, b(Blocks.BLACK_STAINED_GLASS));   // the car
        fill(l, c, 21, 1, -1, 21, 4, -1, b(Blocks.STRIPPED_OAK_LOG));
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 4; dy <= 5; dy++) set(l, c.offset(21 + dx, dy, -1 + dz), b(Blocks.HAY_BLOCK));
        fill(l, c, -14, 1, 14, -10, 2, 16, b(Blocks.IRON_BLOCK));                         // a wrecked Government truck
        set(l, c.offset(-12, 3, 15), b(Blocks.CAMPFIRE));
    }

    // ------------------------------------------------------------------ the Government hall (the last fight with Ultima)

    /**
     * The last fight with Ultima (the user's pictures): a tall, empty Government hall - mauve walls with deep red trim
     * at floor and ceiling, big recessed panels between square pillars, arches in the side walls, a pale tiled floor.
     * {@code stage} 1: Rhalix has blasted a hole high in the far wall to see the moon (a full moon among clouds, and a
     * pool of moonlight on the floor); 2: a second hole in the right wall, where Kyrie and Zack burst in - the city's
     * lights beyond it.
     */
    static void govHall(ServerLevel l, BlockPos c, RandomSource r, int stage) {
        int x0 = -22, x1 = 22, zb = -24, zf = 14, h = 14;
        BlockState wall = b(Blocks.PINK_TERRACOTTA), panel = b(Blocks.LIGHT_GRAY_TERRACOTTA), trim = b(Blocks.RED_TERRACOTTA), pillar = b(Blocks.MAGENTA_TERRACOTTA);
        for (int x = x0; x <= x1; x++) for (int z = zb; z <= zf; z++) {
            set(l, c.offset(x, 0, z), b(Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0 ? Blocks.WHITE_TERRACOTTA : Blocks.SMOOTH_QUARTZ));
            set(l, c.offset(x, h + 1, z), b(Blocks.PINK_TERRACOTTA));
            boolean side = x == x0 || x == x1 || z == zb || z == zf;
            if (!side) continue;
            for (int y = 1; y <= h; y++) {
                BlockState st = y == 1 || y == h ? trim : wall;
                boolean along = z == zb || z == zf;
                int k = along ? x : z;
                if (y > 1 && y < h - 1 && Math.floorMod(k, 10) != 0) st = (along && z == zb) ? panel : st;   // the back wall's recessed panels
                if (Math.floorMod(k, 10) == 0 && y > 1 && y < h) st = pillar;
                set(l, c.offset(x, y, z), st);
            }
        }
        for (int z = zb + 3; z <= zf - 3; z += 12) for (int s2 : new int[]{x0, x1}) {   // arches in the side walls
            for (int y = 2; y <= 9; y++) for (int dz = -3; dz <= 3; dz++) {
                double top = 6 + Math.sqrt(Math.max(0, 9 - dz * dz));
                if (y <= top) set(l, c.offset(s2, y, z + dz), panel);
            }
        }
        fill(l, c, -2, 1, zf, 2, 4, zf, b(Blocks.AIR));                                  // the way in
        for (int x = -16; x <= 16; x += 8) set(l, c.offset(x, h, -4), b(Blocks.SEA_LANTERN));
        if (stage >= 1) {
            // the hole in the far wall, the moon and clouds beyond, the moonlight on the floor
            for (int x = -9; x <= 9; x++) for (int y = 5; y <= h + 1; y++) {
                double dy = (y - 10) / 1.1, rr = 8.5 + ((x * 31 + y * 17) % 3 - 1) * 0.6;
                if (x * x + dy * dy * 1.6 <= rr * rr) set(l, c.offset(x, y, zb), b(Blocks.AIR));
            }
            for (int x = -10; x <= 10; x++) for (int y = 3; y <= 20; y++) for (int z = zb - 8; z < zb; z++) set(l, c.offset(x, y, z), b(Blocks.AIR));
            for (int x = -4; x <= 4; x++) for (int y = -4; y <= 4; y++) if (x * x + y * y <= 17) set(l, c.offset(x, 12 + y, zb - 8), b(x * x + y * y <= 8 ? Blocks.SEA_LANTERN : Blocks.WHITE_CONCRETE));
            for (int[] cl : new int[][]{{-7, 8}, {-5, 7}, {6, 9}, {7, 8}, {4, 6}, {-8, 13}, {8, 13}}) set(l, c.offset(cl[0], cl[1], zb - 6), b(Blocks.BLUE_WOOL));
            for (int i = 0; i < 12; i++) set(l, c.offset(r.nextInt(18) - 9, 6 + r.nextInt(14), zb - 7), b(Blocks.END_ROD));
            for (int x = -8; x <= 8; x++) for (int z = zb + 1; z <= zb + 12; z++) {
                if (x * x / 64.0 + (z - zb - 6) * (z - zb - 6) / 36.0 > 1 + ((x * 7 + z * 3) % 3) * 0.1) continue;
                set(l, c.offset(x, 0, z), b(Blocks.WHITE_CONCRETE));
                if (((x + z) & 3) == 0) set(l, c.offset(x, 1, z), b(Blocks.LIGHT).setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 12));
            }
        }
        if (stage >= 2) {
            // the hole in the right wall, and the city at night through it
            for (int z = -10; z <= 0; z++) for (int y = 3; y <= 11; y++) {
                double dz = z + 5, dy = (y - 7) * 1.2;
                if (dz * dz + dy * dy <= 26 + ((z * 13 + y * 7) % 4)) set(l, c.offset(x1, y, z), b(Blocks.AIR));
            }
            for (int[] t : new int[][]{{-9, 10}, {-6, 14}, {-2, 8}, {1, 12}}) {
                int tz = t[0], th = t[1];
                for (int dz = 0; dz <= 2; dz++) for (int y = 1; y <= th; y++) set(l, c.offset(x1 + 7, y, tz + dz), b(y % 3 == 0 && dz == 1 ? Blocks.SHROOMLIGHT : Blocks.BLACK_CONCRETE));
            }
            for (int z = -10; z <= 0; z++) set(l, c.offset(x1 + 1, 2, z), b(Blocks.PINK_TERRACOTTA));   // (rubble at the edge)
        }
    }

    // ------------------------------------------------------------------ the Tenjos' village (Year 90)

    /**
     * Where the Tenjos hid (the user's picture; Kyle and Technica hunting Yuri's family, Year 90): an old village of
     * cottages - cream and tan stucco, one of brick, brown shingle roofs and chimneys, ivy on the walls - along a
     * winding dirt road with grass at its edges, bushes, and tall trees.
     */
    static void tenjoVillage(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("tenjo_village");
        for (int x = -RR; x <= RR; x++) for (int z = -RR; z <= RR; z++) {
            if (!inside(x, z, RR)) continue;
            double mid = Math.sin(z * 0.12) * 4;                                         // the road winds
            if (Math.abs(x - mid) <= 3.5) set(l, c.offset(x, 0, z), b(r.nextInt(5) == 0 ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH));
        }
        // {x, z, w, d, h, wall kind, chimney}: the cottages
        int[][] hs = {{-24, -6, 9, 8, 5, 0, 1}, {-16, -24, 7, 7, 6, 1, 1}, {-2, -28, 9, 7, 5, 2, 0}, {10, -14, 8, 9, 6, 3, 1}, {12, 2, 12, 9, 7, 1, 1}, {-22, 8, 8, 7, 4, 3, 0}};
        for (int[] hh : hs) {
            int hx = hh[0], hz = hh[1], w = hh[2], d = hh[3], h = hh[4];
            BlockState wall = switch (hh[5]) { case 0 -> b(Blocks.WHITE_TERRACOTTA); case 1 -> b(Blocks.CALCITE); case 2 -> b(Blocks.BRICKS); default -> b(Blocks.SMOOTH_SANDSTONE); };
            for (int x = hx; x <= hx + w; x++) for (int z = hz; z <= hz + d; z++) {
                if (!inside(x, z, RR - 1)) continue;
                boolean side = x == hx || x == hx + w || z == hz || z == hz + d;
                for (int y = 1; y <= h; y++) if (side) {
                    BlockState st = wall;
                    if (y == 3 && (x - hx) % 3 == 1 && (z == hz || z == hz + d)) st = b(Blocks.GLASS_PANE);
                    if (r.nextInt(14) == 0 && y < h) st = b(Blocks.MOSS_BLOCK);              // (ivy)
                    set(l, c.offset(x, y, z), st);
                }
                // the roof: ridge along x, sloping down to the front and back
                int fromEdge = Math.min(z - hz, hz + d - z);
                if (fromEdge <= d / 2) set(l, c.offset(x, h + 1 + fromEdge, z), b(Blocks.DARK_OAK_PLANKS));
                if ((x == hx || x == hx + w) && fromEdge > 0) for (int y = h + 1; y <= h + fromEdge; y++) set(l, c.offset(x, y, z), wall);   // the gables
            }
            int dx = hx + w / 2, front = Math.abs(hz + d) < Math.abs(hz) ? hz + d : hz;     // the door faces the road (the middle)
            fill(l, c, dx, 1, front, dx, 2, front, b(Blocks.SPRUCE_PLANKS));
            set(l, c.offset(dx, 2, front), b(Blocks.SPRUCE_TRAPDOOR));
            if (hh[6] == 1) fill(l, c, hx + 1, h + 1, hz + d / 2, hx + 1, h + d / 2 + 3, hz + d / 2, b(Blocks.BRICKS));   // the chimney
            bush(l, c, hx + w + 1, front + (front == hz ? -1 : 1), r);
        }
        for (int[] t : new int[][]{{-12, -14}, {4, -20}, {-27, 0}, {24, -4}}) {
            int h = 7 + r.nextInt(3);
            fill(l, c, t[0], 1, t[1], t[0], h, t[1], b(Blocks.OAK_LOG));
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 1; dy++)
                if (dx * dx + dz * dz + dy * dy * 2 <= 6) set(l, c.offset(t[0] + dx, h + dy, t[1] + dz), leaves(Blocks.OAK_LEAVES));
        }
    }

    // ------------------------------------------------------------------ inside the Tenjos' hideaway house

    /**
     * Inside the house the Tenjos ran to (the user's picture): a dusty hall with warm cracked plaster walls, a
     * wooden floor with litter, a boarded-up door, windows, a broken mirror, a framed cactus picture, a bench and a
     * little cabinet, an open doorway glowing yellow, a wooden staircase going up - and cobwebs everywhere.
     */
    static void tenjoHouse(ServerLevel l, BlockPos c, RandomSource r) {
        int x0 = -16, x1 = 16, zb = -16, zf = 12, h = 7;
        BlockState wall = b(Blocks.WHITE_TERRACOTTA), wood = b(Blocks.SPRUCE_PLANKS);
        for (int x = x0; x <= x1; x++) for (int z = zb; z <= zf; z++) {
            set(l, c.offset(x, 0, z), b(((x + z * 3) & 7) == 0 ? Blocks.STRIPPED_ACACIA_WOOD : Blocks.ACACIA_PLANKS));
            set(l, c.offset(x, h + 1, z), b(Blocks.TERRACOTTA));
            boolean side = x == x0 || x == x1 || z == zb || z == zf;
            if (side) for (int y = 1; y <= h; y++) set(l, c.offset(x, y, z), y == 1 || y == h ? wood : r.nextInt(12) == 0 ? b(Blocks.TERRACOTTA) : wall);
        }
        fill(l, c, -1, 1, zf, 1, 3, zf, b(Blocks.AIR));                                  // the way in
        // the boarded-up door (west wall)
        fill(l, c, x0, 1, -4, x0, 3, -3, b(Blocks.DARK_OAK_PLANKS));
        for (int y : new int[]{1, 3}) { set(l, c.offset(x0 + 1, y + 0, -4), slab(Blocks.SPRUCE_SLAB, true)); set(l, c.offset(x0 + 1, y, -3), slab(Blocks.SPRUCE_SLAB, true)); }
        // windows, the mirror, the cactus picture
        for (int x : new int[]{-11, -10, 10, 11}) fill(l, c, x, 3, zb, x, 4, zb, b(Blocks.GLASS));
        fill(l, c, x1, 3, -6, x1, 4, -5, b(Blocks.GLASS));
        set(l, c.offset(-5, 4, zb + 1), b(Blocks.WHITE_STAINED_GLASS));
        set(l, c.offset(6, 4, zb + 1), b(Blocks.POTTED_CACTUS));
        // the yellow doorway
        fill(l, c, -1, 1, zb, 1, 4, zb, b(Blocks.YELLOW_CONCRETE));
        fill(l, c, -2, 1, zb + 1, -2, 4, zb + 1, wood); fill(l, c, 2, 1, zb + 1, 2, 4, zb + 1, wood); fill(l, c, -2, 5, zb + 1, 2, 5, zb + 1, wood);
        set(l, c.offset(0, 1, zb + 1), b(Blocks.LIGHT).setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 10));
        // the cabinet, the bench, the fallen broom, litter
        fill(l, c, -9, 1, zb + 1, -7, 1, zb + 1, b(Blocks.BARREL));
        set(l, c.offset(5, 1, -6), stair(Blocks.SPRUCE_STAIRS, Direction.WEST, false)); set(l, c.offset(5, 1, -5), stair(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        set(l, c.offset(3, 1, -12), b(Blocks.LIGHTNING_ROD));
        for (int i = 0; i < 8; i++) set(l, c.offset(r.nextInt(28) - 14, 1, r.nextInt(24) - 14), b(Blocks.WHITE_CARPET));
        // the staircase up the east wall
        for (int k = 0; k < h - 1; k++) { set(l, c.offset(x1 - 2, 1 + k, 6 - k), stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false)); set(l, c.offset(x1 - 1, 1 + k, 6 - k), stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false)); set(l, c.offset(x1 - 3, 2 + k, 6 - k), b(Blocks.SPRUCE_FENCE)); }
        fill(l, c, x1 - 2, h + 1, -2, x1 - 1, h + 1, 6, b(Blocks.AIR));
        // the lamp and the cobwebs
        set(l, c.offset(0, h, -2), hanging(Blocks.LANTERN));
        for (int i = 0; i < 16; i++) set(l, c.offset(r.nextInt(30) - 15, 4 + r.nextInt(3), r.nextInt(26) - 15), b(Blocks.COBWEB));
    }

    // ------------------------------------------------------------------ Kyle's office (the new facility)

    /**
     * Kyle's office in the new facility (the user's picture): pale blue-grey metal panels, ceiling light strips and
     * vents, a big wall screen with glowing readouts, the long white desk with test tubes and a microscope, a white
     * chair, a computer stand, a cold store, a wall of sample drawers, the glowing cryo tube, a round scanner pod, and a
     * robot arm hanging from the ceiling.
     */
    static void kyleOffice(ServerLevel l, BlockPos c, RandomSource r) {
        int x0 = -18, x1 = 18, zb = -16, zf = 12, h = 9;
        BlockState panel = b(Blocks.LIGHT_GRAY_CONCRETE), white = b(Blocks.WHITE_CONCRETE), glow = b(Blocks.SEA_LANTERN);
        for (int x = x0; x <= x1; x++) for (int z = zb; z <= zf; z++) {
            set(l, c.offset(x, 0, z), b(((x ^ z) & 3) == 0 ? Blocks.LIGHT_BLUE_TERRACOTTA : Blocks.LIGHT_GRAY_CONCRETE));
            set(l, c.offset(x, h + 1, z), Math.floorMod(z, 5) == 0 && Math.abs(x) < 12 ? glow : b(Blocks.SMOOTH_STONE));
            boolean side = x == x0 || x == x1 || z == zb || z == zf;
            if (side) for (int y = 1; y <= h; y++) set(l, c.offset(x, y, z), Math.floorMod(x + z, 6) == 0 ? b(Blocks.LIGHT_BLUE_TERRACOTTA) : panel);
        }
        fill(l, c, -1, 1, zf, 1, 3, zf, b(Blocks.AIR));
        // the big screen
        fill(l, c, -12, 4, zb + 1, -2, 7, zb + 1, b(Blocks.BLACK_STAINED_GLASS));
        for (int[] p : new int[][]{{-10, 6}, {-9, 5}, {-6, 6}, {-5, 6}, {-4, 5}, {-7, 4}, {-6, 4}, {-5, 4}}) set(l, c.offset(p[0], p[1], zb), b(Blocks.CYAN_CONCRETE));
        fill(l, c, -12, 3, zb + 1, -2, 3, zb + 1, b(Blocks.IRON_BLOCK));
        // the desk, test tubes, the microscope, the chair, the computer stand
        fill(l, c, -12, 1, -8, -3, 1, -7, white);
        fill(l, c, -12, 2, -8, -3, 2, -8, slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
        set(l, c.offset(-8, 2, -7), b(Blocks.BREWING_STAND)); set(l, c.offset(-5, 2, -7), b(Blocks.END_ROD));
        set(l, c.offset(-6, 1, -5), stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH, false));
        fill(l, c, -16, 1, zb + 2, -15, 2, zb + 2, white); set(l, c.offset(-16, 3, zb + 2), b(Blocks.CYAN_STAINED_GLASS));
        // the cold store and the sample drawers
        fill(l, c, 0, 1, zb + 1, 1, 5, zb + 1, white);
        for (int x = 2; x <= 5; x++) for (int y = 1; y <= 5; y++) set(l, c.offset(x, y, zb + 1), b(y % 2 == 0 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.QUARTZ_BRICKS));
        // the cryo tube and the scanner pod
        for (int y = 1; y <= 6; y++) for (int x = 8; x <= 10; x++) for (int z = zb + 1; z <= zb + 3; z++) {
            boolean edge = x == 8 || x == 10 || z == zb + 1 || z == zb + 3;
            set(l, c.offset(x, y, z), y == 1 || y == 6 ? white : edge ? b(Blocks.LIGHT_BLUE_STAINED_GLASS) : glow);
        }
        for (int y = 1; y <= 4; y++) for (int z = -10; z <= -6; z++) {
            double d = Math.sqrt((y - 2.5) * (y - 2.5) + (z + 8) * (z + 8));
            if (d <= 2.3) set(l, c.offset(15, y, z), d > 1.4 ? white : b(Blocks.CYAN_STAINED_GLASS));
        }
        // the robot arm
        fill(l, c, 14, h, -12, 14, h - 2, -12, b(Blocks.IRON_BLOCK)); fill(l, c, 14, h - 3, -12, 14, h - 3, -10, b(Blocks.IRON_BLOCK));
        set(l, c.offset(14, h - 4, -10), b(Blocks.LIGHTNING_ROD).setValue(net.minecraft.world.level.block.LightningRodBlock.FACING, Direction.DOWN));
        // a side table and a shelf
        fill(l, c, 16, 1, -2, 17, 2, -2, b(Blocks.LIGHT_GRAY_CONCRETE));
        for (int z = 2; z <= 8; z += 3) set(l, c.offset(x0 + 1, 2, z), b(Blocks.IRON_TRAPDOOR));
    }

    // ------------------------------------------------------------------ Yuri's VR room (empty)

    /**
     * Yuri's VR room with nothing projected (the user's picture): a long dark-blue chamber, walls and ceiling of
     * jutting blocky panels, slanted struts down both sides with white light strips, small blue lights scattered in
     * the panels, a smooth dark floor with a seam down the middle, and darkness at the far end.
     */
    static void vrRoom(ServerLevel l, BlockPos c, RandomSource r) {
        int RR = radius("vr_room"), w = 10, h = 11;
        BlockState[] blocks = {b(Blocks.BLUE_TERRACOTTA), b(Blocks.CYAN_TERRACOTTA), b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_TILES)};
        for (int z = -RR; z <= RR; z++) {
            if (!inside(0, z, RR)) continue;
            for (int x = -w; x <= w; x++) {
                set(l, c.offset(x, 0, z), b(x == 0 ? Blocks.POLISHED_BLACKSTONE : Blocks.BLUE_CONCRETE));
                set(l, c.offset(x, h, z), blocks[r.nextInt(blocks.length)]);
                if (r.nextInt(4) == 0 && Math.abs(x) < w) set(l, c.offset(x, h - 1, z), blocks[r.nextInt(blocks.length)]);   // (the blocky ceiling)
            }
            for (int s2 : new int[]{-w, w}) for (int y = 1; y < h; y++) {
                set(l, c.offset(s2, y, z), r.nextInt(25) == 0 ? b(Blocks.LIGHT_BLUE_STAINED_GLASS) : blocks[r.nextInt(blocks.length)]);
                if (r.nextInt(5) == 0) set(l, c.offset(s2 - Integer.signum(s2), y, z), blocks[r.nextInt(blocks.length)]);   // (panels jutting out)
            }
            if (Math.floorMod(z, 6) == 0) for (int s2 : new int[]{-1, 1}) {           // the slanted struts with their light strips
                for (int k = 0; k < 4; k++) set(l, c.offset(s2 * (w - 1 - k), 1 + k / 2, z), b(Blocks.POLISHED_DEEPSLATE));
                for (int y = 3; y < h - 1; y++) set(l, c.offset(s2 * (w - 1), y, z), b(Blocks.END_ROD));
                set(l, c.offset(s2 * (w - 4), 1, z), b(Blocks.SEA_LANTERN));
            }
        }
        for (int x = -w; x <= w; x++) for (int y = 1; y < h; y++) set(l, c.offset(x, y, -RR + 1), b(Blocks.BLACK_CONCRETE));   // the dark far end
    }

    // ------------------------------------------------------------------ Kyrie's room

    /**
     * Kyrie's room at night (the user's picture): magenta and deep-purple walls, tall dark window columns with violet
     * light running up them, a great pointed-arch window onto the stars with the balcony railing beyond, sheer pink
     * curtains hanging in it, and dark urns of glowing pink flowers either side.
     */
    static void kyrieRoom(ServerLevel l, BlockPos c, RandomSource r) {
        int x0 = -14, x1 = 14, zb = -14, zf = 12, h = 14;
        for (int x = x0; x <= x1; x++) for (int z = zb; z <= zf; z++) {
            set(l, c.offset(x, 0, z), b(((x + z) & 1) == 0 ? Blocks.MAGENTA_STAINED_GLASS : Blocks.PURPLE_STAINED_GLASS));
            set(l, c.offset(x, -1, z), b(Blocks.PURPLE_CONCRETE));
            set(l, c.offset(x, h + 1, z), b(Blocks.PURPLE_TERRACOTTA));
            boolean side = x == x0 || x == x1 || z == zb || z == zf;
            if (side) for (int y = 1; y <= h; y++) set(l, c.offset(x, y, z), b(y < 3 ? Blocks.MAGENTA_CONCRETE : Blocks.MAGENTA_TERRACOTTA));
        }
        fill(l, c, -1, 1, zf, 1, 3, zf, b(Blocks.AIR));
        for (int x : new int[]{-9, -8, 8, 9}) {                                          // the window columns, lit up their sides
            fill(l, c, x, 1, zb + 1, x, h, zb + 1, b(Blocks.BLACK_CONCRETE));
        }
        for (int x : new int[]{-10, -7, 7, 10}) fill(l, c, x, 1, zb + 1, x, h, zb + 1, b(Blocks.PURPLE_STAINED_GLASS));
        for (int x : new int[]{-11, 11}) fill(l, c, x, 2, zb + 1, x, h - 1, zb + 1, b(Blocks.END_ROD));
        // the great arched window onto the stars, and the balcony beyond
        for (int x = -5; x <= 5; x++) for (int y = 1; y <= h; y++) {
            double top = 8 + Math.sqrt(Math.max(0, 36 - x * x)) * 0.9;
            if (y <= top) set(l, c.offset(x, y, zb), b(Blocks.AIR));
            else if (y <= top + 1) set(l, c.offset(x, y, zb), b(Blocks.GOLD_BLOCK));
        }
        for (int x = -6; x <= 6; x++) for (int z = zb - 4; z < zb; z++) set(l, c.offset(x, 0, z), b(Blocks.PURPLE_CONCRETE));
        for (int x = -6; x <= 6; x++) { set(l, c.offset(x, 1, zb - 4), b(x % 3 == 0 ? Blocks.AMETHYST_BLOCK : Blocks.PURPLE_STAINED_GLASS)); set(l, c.offset(x, 2, zb - 4), b(Blocks.PURPLE_STAINED_GLASS)); }
        for (int x = -6; x <= 6; x++) for (int y = 0; y <= 20; y++) for (int z = zb - 12; z < zb - 4; z++) set(l, c.offset(x, y, z), b(Blocks.AIR));
        for (int i = 0; i < 25; i++) set(l, c.offset(r.nextInt(16) - 8, 4 + r.nextInt(16), zb - 10 - r.nextInt(3)), b(Blocks.END_ROD));
        // the sheer curtains
        for (int s2 : new int[]{-1, 1}) for (int x = 3; x <= 5; x++) for (int y = 3; y <= h - 1; y++) if (y > 6 || x == 5) set(l, c.offset(s2 * x, y, zb + 1), b(Blocks.PINK_STAINED_GLASS));
        // urns of glowing pink flowers
        for (int s2 : new int[]{-1, 1}) {
            int ux = s2 * 9, uz = zb + 4;
            set(l, c.offset(ux, 1, uz), b(Blocks.BLACKSTONE)); set(l, c.offset(ux, 2, uz), b(Blocks.POLISHED_BLACKSTONE)); set(l, c.offset(ux, 3, uz), b(Blocks.BLACKSTONE_WALL));
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(l, c.offset(ux + dx, 4, uz + dz), leaves(Blocks.FLOWERING_AZALEA_LEAVES));
            set(l, c.offset(ux, 5, uz), b(Blocks.PEARLESCENT_FROGLIGHT));
            set(l, c.offset(ux + s2, 1, uz + 1), b(Blocks.PINK_PETALS));
        }
        for (int x = -10; x <= 10; x += 5) set(l, c.offset(x, h, 0), hanging(Blocks.SOUL_LANTERN));
    }
}
