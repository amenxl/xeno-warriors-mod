package com.xenowarriors.story;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Story Mode's places, built in the Tower dimension far from the Tower floors and Journey's paths (one row of places per
 * party slot): the Sky Patrol hideout, Aurora's streets, the SR hideout, the Royal Capital, a sky island, Kitsune's Farm,
 * the train station, the Elementalist School, the Mountain Village, the Government facility and the battlefield.
 * Each is a round area about 44 blocks across with its own look; built the first time a party goes there (per server run).
 */
public final class StoryScenes {
    private StoryScenes() {}

    public static final List<String> PLACES = List.of("sp_hideout", "aurora_street", "sr_hideout", "royal_capital", "sky_island", "farm",
            "train_station", "school", "mountain_village", "gov_facility", "battlefield", "sr_tunnels", "sr_barracks", "sr_cells");
    public static final int R = 22, Y = 100, SPACING = 512, Z0 = 60000, PLACE_GAP = 160;

    /** How far a place reaches (its floor's radius): bigger places for the big fights, so a crowd doesn't all end up in one spot. */
    public static int radius(String place) {
        if (place == null) return R;
        return switch (place) {
            case "sr_hideout", "sr_tunnels", "sr_barracks", "sr_cells", "battlefield" -> 34;
            case "royal_capital", "aurora_street", "gov_facility", "school", "sp_hideout", "farm", "mountain_village" -> 30;
            default -> R;
        };
    }
    private static final Set<String> BUILT = new HashSet<>();

    public static String title(String place) {
        return switch (place) {
            case "sp_hideout" -> "Sky Patrol Hideout"; case "aurora_street" -> "City of Aurora"; case "sr_hideout" -> "Sky Rebellion Hideout";
            case "royal_capital" -> "Royal Capital"; case "sky_island" -> "Sky Island"; case "farm" -> "Kitsune's Farm";
            case "train_station" -> "Mountain Village Station"; case "school" -> "Elementalist School"; case "mountain_village" -> "Mountain Village";
            case "gov_facility" -> "Government Facility, Aurora"; case "battlefield" -> "The Battlefield";
            case "sr_tunnels" -> "SR Hideout - the Tunnels"; case "sr_barracks" -> "SR Hideout - the Barracks"; case "sr_cells" -> "SR Hideout - the Cells"; default -> place;
        };
    }

    /** The middle of a place's floor (stand on it at Y + 1). */
    public static BlockPos center(int slot, String place) {
        int i = Math.max(0, PLACES.indexOf(place));
        return new BlockPos(slot * SPACING + SPACING / 2, Y, Z0 + i * PLACE_GAP);
    }

    /** Where the party arrives (south edge, facing north into the place). */
    public static Vec3 entry(int slot, String place, int k) {
        BlockPos c = center(slot, place);
        return new Vec3(c.getX() + 0.5 + (k - 1.5) * 1.6, Y + 1, c.getZ() + 8.5);
    }

    /** Where the speakers of a scene stand (an arc in front of the party). */
    public static Vec3 npcSpot(int slot, String place, int i, int n) {
        BlockPos c = center(slot, place);
        double a = Math.PI * (n <= 1 ? 0.5 : 0.2 + 0.6 * i / (n - 1));
        return new Vec3(c.getX() + 0.5 + Math.cos(a) * 4.5, Y + 1, c.getZ() + 4.5 - Math.sin(a) * 3.2);
    }

    /** Where enemies come in. */
    public static Vec3 spawnSpot(int slot, String place, RandomSource r) {
        BlockPos c = center(slot, place);
        int rad = radius(place);
        double a = Math.PI * (1.0 + r.nextDouble()), d = 8 + r.nextDouble() * (rad - 12);
        return new Vec3(c.getX() + 0.5 + Math.cos(a) * d, Y + 1, c.getZ() + 0.5 + Math.sin(a) * d * 0.8);
    }

    public static boolean inArea(net.minecraft.world.entity.Entity e) {
        return e.level().dimension() == com.xenowarriors.tower.Tower.LEVEL && e.getZ() > Z0 - 200;
    }

    public static void ensure(ServerLevel l, int slot, String place) {
        if (BUILT.add(slot + "|" + place)) build(l, center(slot, place), place);
    }

    // ------------------------------------------------------------------ building

    private static void set(ServerLevel l, BlockPos p, BlockState s) { l.setBlock(p, s, 2); }

    private static void fill(ServerLevel l, BlockPos c, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) set(l, c.offset(x, y, z), s);
    }

    private static boolean inside(int x, int z, int r) { return x * x + z * z <= r * r; }

    static void build(ServerLevel l, BlockPos c, String place) {
        final int R = radius(place);                                             // (this place's own size)
        RandomSource rnd = RandomSource.create(place.hashCode());
        // clear the space
        for (int x = -R - 3; x <= R + 3; x++) for (int z = -R - 3; z <= R + 3; z++) for (int y = -6; y <= 26; y++) set(l, c.offset(x, y, z), Blocks.AIR.defaultBlockState());
        BlockState floor, floor2, edge;
        switch (place) {
            case "sp_hideout" -> { floor = Blocks.GRAY_CONCRETE.defaultBlockState(); floor2 = Blocks.GRAY_CONCRETE.defaultBlockState(); edge = Blocks.STONE_BRICKS.defaultBlockState(); }
            case "aurora_street", "gov_facility" -> { floor = Blocks.GRAY_CONCRETE.defaultBlockState(); floor2 = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(); edge = Blocks.SMOOTH_STONE.defaultBlockState(); }
            case "sr_hideout", "sr_tunnels", "sr_barracks", "sr_cells" -> { floor = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(); floor2 = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(); edge = Blocks.BLACKSTONE.defaultBlockState(); }
            case "royal_capital" -> { floor = Blocks.QUARTZ_BLOCK.defaultBlockState(); floor2 = Blocks.SMOOTH_QUARTZ.defaultBlockState(); edge = Blocks.GOLD_BLOCK.defaultBlockState(); }
            case "school" -> { floor = Blocks.STONE_BRICKS.defaultBlockState(); floor2 = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(); edge = Blocks.DEEPSLATE_BRICKS.defaultBlockState(); }
            case "train_station" -> { floor = Blocks.SMOOTH_STONE.defaultBlockState(); floor2 = Blocks.STONE.defaultBlockState(); edge = Blocks.STONE_BRICKS.defaultBlockState(); }
            case "battlefield" -> { floor = Blocks.COARSE_DIRT.defaultBlockState(); floor2 = Blocks.GRAVEL.defaultBlockState(); edge = Blocks.STONE.defaultBlockState(); }
            default -> { floor = Blocks.GRASS_BLOCK.defaultBlockState(); floor2 = Blocks.GRASS_BLOCK.defaultBlockState(); edge = Blocks.DIRT.defaultBlockState(); }
        }
        boolean floating = place.equals("sky_island") || place.equals("royal_capital");
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
            if (!inside(x, z, R)) continue;
            set(l, c.offset(x, 0, z), rnd.nextInt(5) == 0 ? floor2 : floor);
            int depth = floating ? 1 + Math.max(0, (R * R - x * x - z * z) / 60) : 3;
            for (int y = 1; y <= depth; y++) set(l, c.offset(x, -y, z), y == 1 ? edge : (floating ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState()));
        }
        // a low rim so nobody walks off by accident (open where it's an island)
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
            int d = x * x + z * z;
            if (d <= R * R && d > (R - 1) * (R - 1)) {
                set(l, c.offset(x, 1, z), floating ? Blocks.BARRIER.defaultBlockState() : edge);
                for (int y = 2; y <= 4; y++) set(l, c.offset(x, y, z), Blocks.BARRIER.defaultBlockState());
            }
        }
        switch (place) {
            case "sp_hideout" -> hideout(l, c, rnd);
            case "aurora_street" -> city(l, c, rnd, false);
            case "gov_facility" -> facility(l, c, rnd);
            case "sr_hideout" -> srHideout(l, c, rnd);
            case "sr_tunnels" -> srTunnels(l, c, rnd);
            case "sr_barracks" -> srBarracks(l, c, rnd);
            case "sr_cells" -> srCells(l, c, rnd);
            case "royal_capital" -> capital(l, c, rnd);
            case "sky_island" -> island(l, c, rnd);
            case "farm" -> farm(l, c, rnd);
            case "train_station" -> station(l, c, rnd);
            case "school" -> school(l, c, rnd);
            case "mountain_village" -> village(l, c, rnd);
            case "battlefield" -> battlefield(l, c, rnd);
            default -> {}
        }
    }

    private static void box(ServerLevel l, BlockPos c, int x0, int z0, int x1, int z1, int h, BlockState wall, BlockState roof, BlockState window) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = 1; y <= h; y++) {
            boolean side = x == x0 || x == x1 || z == z0 || z == z1;
            if (!side) continue;
            boolean win = window != null && y >= 2 && y <= h - 1 && (x + z) % 3 == 0 && !(x == x0 && z == z0);
            set(l, c.offset(x, y, z), win ? window : wall);
        }
        if (roof != null) fill(l, c, x0, h + 1, z0, x1, h + 1, z1, roof);
    }

    private static void tower(ServerLevel l, BlockPos c, int x, int z, int w, int h, BlockState wall, BlockState glass) {
        box(l, c, x, z, x + w, z + w, h, wall, wall, glass);
    }

    private static void lamp(ServerLevel l, BlockPos c, int x, int z) {
        fill(l, c, x, 1, z, x, 3, z, Blocks.IRON_BARS.defaultBlockState());
        set(l, c.offset(x, 4, z), Blocks.SEA_LANTERN.defaultBlockState());
    }

    private static void tree(ServerLevel l, BlockPos c, int x, int z, RandomSource r) {
        int h = 4 + r.nextInt(2);
        fill(l, c, x, 1, z, x, h, z, Blocks.OAK_LOG.defaultBlockState());
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = h - 1; dy <= h + 1; dy++)
            if (Math.abs(dx) + Math.abs(dz) + Math.max(0, dy - h) < 4 && !(dx == 0 && dz == 0 && dy < h + 1)) set(l, c.offset(x + dx, dy, z + dz), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
    }

    /**
     * The Sky Patrol hideout: a plain warehouse workroom - pale walls with wall vents, a grey plank floor, two dark
     * steel pillars, steel shelving full of cardboard boxes in the back corners, and the workbench against the back
     * wall with its pegboard of tools (knife, scissors, hammer, screwdriver) and a vise, under ceiling spotlights.
     */
    private static void hideout(ServerLevel l, BlockPos c, RandomSource r) {
        BlockState wall = Blocks.CALCITE.defaultBlockState(), steel = Blocks.GRAY_CONCRETE.defaultBlockState(), dark = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState box = Blocks.SMOOTH_SANDSTONE.defaultBlockState(), box2 = Blocks.CUT_SANDSTONE.defaultBlockState();
        box(l, c, -14, -14, 14, 2, 7, wall, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), null);
        fill(l, c, -2, 1, 2, 2, 3, 2, Blocks.AIR.defaultBlockState());                            // the door way
        // the floor: grey planks (dark seams every third row)
        for (int x = -13; x <= 13; x++) for (int z = -13; z <= 1; z++) set(l, c.offset(x, 0, z), z % 3 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : steel);
        // wall vents (iron grilles) either side of the bench
        BlockState vent = Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, net.minecraft.core.Direction.SOUTH);
        for (int x : new int[]{-12, -11, -10, -9, -8, 8, 9, 10, 11, 12}) for (int y = 3; y <= 4; y++) set(l, c.offset(x, y, -13), vent);
        // steel shelving with cardboard boxes, in both back corners
        for (int side : new int[]{-1, 1}) {
            int x0 = side < 0 ? -13 : 9, x1 = side < 0 ? -9 : 13;
            for (int x : new int[]{x0, x1}) fill(l, c, x, 1, -12, x, 6, -12, steel);             // the uprights (in front of the vents)
            for (int y : new int[]{2, 4, 6}) fill(l, c, x0, y, -12, x1, y, -12, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());   // shelves
            for (int x = x0 + 1; x < x1; x++) {
                if (r.nextInt(3) != 0) set(l, c.offset(x, 1, -12), r.nextBoolean() ? box : box2);
                if (r.nextInt(3) != 0) set(l, c.offset(x, 3, -12), r.nextBoolean() ? box : box2);
                if (r.nextInt(2) == 0) set(l, c.offset(x, 5, -12), box);
            }
        }
        // two dark steel pillars, floor to ceiling
        for (int x : new int[]{-7, 7}) fill(l, c, x, 1, -6, x, 7, -6, dark);
        // the workbench: a counter with cabinet ends, the pegboard over it in a steel frame
        fill(l, c, -5, 1, -13, 5, 1, -13, Blocks.PACKED_MUD.defaultBlockState());
        for (int x : new int[]{-5, 5}) set(l, c.offset(x, 1, -13), Blocks.MUD_BRICKS.defaultBlockState());
        fill(l, c, -4, 2, -14, 4, 4, -14, Blocks.BIRCH_PLANKS.defaultBlockState());               // the pegboard (set into the wall)
        for (int x : new int[]{-5, 5}) fill(l, c, x, 2, -14, x, 5, -14, steel);
        fill(l, c, -5, 5, -14, 5, 5, -14, steel);
        set(l, c.offset(-1, 2, -13), Blocks.ANVIL.defaultBlockState().setValue(net.minecraft.world.level.block.AnvilBlock.FACING, net.minecraft.core.Direction.EAST));   // the vise
        set(l, c.offset(1, 2, -13), Blocks.GRINDSTONE.defaultBlockState().setValue(net.minecraft.world.level.block.GrindstoneBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        // tools on the pegboard
        var room = new net.minecraft.world.phys.AABB(c.offset(-14, 0, -14)).expandTowards(28, 8, 16);
        for (var f : l.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, room)) f.discard();   // (built again: no doubles)
        net.minecraft.world.item.Item[] tools = {net.minecraft.world.item.Items.IRON_SWORD, net.minecraft.world.item.Items.SHEARS, net.minecraft.world.item.Items.IRON_AXE,
                net.minecraft.world.item.Items.FLINT_AND_STEEL};
        int[] at = {-4, -3, 3, 4};
        for (int i = 0; i < tools.length; i++) {
            var frame = new net.minecraft.world.entity.decoration.ItemFrame(l, c.offset(at[i], 3, -13), net.minecraft.core.Direction.SOUTH);
            frame.setItem(new net.minecraft.world.item.ItemStack(tools[i]), false);
            net.minecraft.nbt.CompoundTag t = frame.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            t.putBoolean("Fixed", true); t.putBoolean("Invisible", true); t.putBoolean("Invulnerable", true);
            frame.load(t);
            l.addFreshEntity(frame);
        }
        // ceiling spotlights: over the bench and the shelves, and a row down the room
        for (int[] p : new int[][]{{0, -11}, {-11, -10}, {11, -10}, {0, -4}, {-7, -2}, {7, -2}}) set(l, c.offset(p[0], 8, p[1]), Blocks.SEA_LANTERN.defaultBlockState());
        // a few boxes on the floor by the right-hand shelves (one tipped over)
        set(l, c.offset(12, 1, -10), box);
        set(l, c.offset(11, 1, -10), Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState());
        set(l, c.offset(-12, 1, -10), box2);
    }

    private static void city(ServerLevel l, BlockPos c, RandomSource r, boolean ruined) {
        for (int x = -R; x <= R; x++) for (int z = -3; z <= 3; z++) if (inside(x, z, R)) set(l, c.offset(x, 0, z), Blocks.BLACK_CONCRETE.defaultBlockState());
        for (int x = -R + 2; x <= R - 2; x += 4) set(l, c.offset(x, 0, 0), Blocks.YELLOW_CONCRETE.defaultBlockState());
        int[][] spots = {{-17, -14}, {-8, -17}, {4, -16}, {12, -12}, {-15, 9}, {9, 10}};
        BlockState[] walls = {Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), Blocks.WHITE_CONCRETE.defaultBlockState(), Blocks.CYAN_TERRACOTTA.defaultBlockState()};
        for (int[] s : spots) tower(l, c, s[0], s[1], 5, 10 + r.nextInt(12), walls[r.nextInt(walls.length)], Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState());
        for (int x = -16; x <= 16; x += 8) { lamp(l, c, x, -5); lamp(l, c, x, 5); }
    }

    private static void facility(ServerLevel l, BlockPos c, RandomSource r) {
        box(l, c, -18, -18, 18, -6, 7, Blocks.WHITE_CONCRETE.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState());
        fill(l, c, -3, 1, -6, 3, 4, -6, Blocks.AIR.defaultBlockState());
        for (int x = -15; x <= 15; x += 6) {
            fill(l, c, x, 1, -16, x + 1, 3, -15, Blocks.CYAN_STAINED_GLASS.defaultBlockState());   // gas tanks
            set(l, c.offset(x, 4, -16), Blocks.IRON_BLOCK.defaultBlockState());
        }
        for (int x = -12; x <= 12; x += 8) fill(l, c, x, 1, -10, x + 2, 1, -9, Blocks.IRON_BLOCK.defaultBlockState());    // lab tables
        for (int x = -16; x <= 16; x += 8) lamp(l, c, x, 6);
        fill(l, c, -18, 1, 14, 18, 3, 14, Blocks.IRON_BARS.defaultBlockState());                 // the fence
        fill(l, c, -3, 1, 14, 3, 3, 14, Blocks.AIR.defaultBlockState());
        set(l, c.offset(0, 6, -6), Blocks.REDSTONE_LAMP.defaultBlockState());
    }

    /** Outside the SR hideout's main hall: side halls and guard posts (the hideout's a lot bigger now). */
    private static void srOuter(ServerLevel l, BlockPos c, RandomSource r) {
        BlockState wall = Blocks.BLACKSTONE.defaultBlockState(), brick = Blocks.RED_NETHER_BRICKS.defaultBlockState();
        for (int side : new int[]{-1, 1}) {                                      // two side halls, open toward the middle
            int x0 = side < 0 ? -31 : 19, x1 = side < 0 ? -19 : 31;
            box(l, c, x0, -14, x1, 8, 6, wall, Blocks.BLACKSTONE_SLAB.defaultBlockState(), null);
            fill(l, c, side < 0 ? x1 : x0, 1, -6, side < 0 ? x1 : x0, 4, 0, Blocks.AIR.defaultBlockState());   // (the way in)
            fill(l, c, x0 + 2, 1, 6, x1 - 2, 3, 8, Blocks.AIR.defaultBlockState());
            for (int z = -10; z <= 4; z += 7) { set(l, c.offset((x0 + x1) / 2, 1, z), Blocks.BARREL.defaultBlockState()); set(l, c.offset((x0 + x1) / 2, 4, z), Blocks.SOUL_LANTERN.defaultBlockState()); }
        }
        for (int x = -12; x <= 12; x += 8) {                                      // guard posts by the front
            fill(l, c, x, 1, 14, x + 1, 3, 15, brick);
            set(l, c.offset(x, 4, 14), Blocks.SOUL_LANTERN.defaultBlockState());
        }
        for (int i = 0; i < 10; i++) {                                           // crates lying about
            int x = r.nextInt(50) - 25, z = r.nextInt(14) + 10;
            if (inside(x, z, 30) && Math.abs(x) > 2) set(l, c.offset(x, 1, z), r.nextBoolean() ? Blocks.BARREL.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
        }
    }

    /** The tunnels under the hideout: thick dark pillars and broken walls in a grid - lanes to fight down, corners to come round. */
    private static void srTunnels(ServerLevel l, BlockPos c, RandomSource r) {
        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), crack = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        for (int gx = -24; gx <= 24; gx += 8) for (int gz = -24; gz <= 8; gz += 8) {
            if (!inside(gx, gz, 28) || (Math.abs(gx) < 3 && gz > -4)) continue;   // (the way in stays open)
            fill(l, c, gx - 1, 1, gz - 1, gx + 1, 6, gz + 1, r.nextInt(4) == 0 ? crack : wall);
            if (r.nextInt(3) == 0) fill(l, c, gx + 2, 1, gz, gx + 5, 2 + r.nextInt(3), gz, wall);       // (a broken wall off it)
            set(l, c.offset(gx, 4, gz + 2), Blocks.SOUL_LANTERN.defaultBlockState());
        }
        for (int x = -26; x <= 26; x += 2) for (int z = -26; z <= 10; z += 2) if (inside(x, z, 30)) set(l, c.offset(x, 7, z), Blocks.DEEPSLATE_TILES.defaultBlockState());   // (a ceiling of beams)
    }

    /** The barracks: rows of bunks, weapon racks and supply crates. */
    private static void srBarracks(ServerLevel l, BlockPos c, RandomSource r) {
        box(l, c, -28, -24, 28, 6, 7, Blocks.BLACKSTONE.defaultBlockState(), Blocks.BLACKSTONE_SLAB.defaultBlockState(), null);
        fill(l, c, -3, 1, 6, 3, 4, 6, Blocks.AIR.defaultBlockState());
        for (int x = -24; x <= 24; x += 6) for (int z : new int[]{-20, -12, -4}) {
            if (Math.abs(x) < 3) continue;
            fill(l, c, x, 1, z, x + 1, 1, z + 2, Blocks.RED_WOOL.defaultBlockState());   // (bunks)
            fill(l, c, x, 3, z, x + 1, 3, z + 2, Blocks.DARK_OAK_SLAB.defaultBlockState());
        }
        for (int z = -22; z <= 2; z += 8) { set(l, c.offset(-27, 2, z), Blocks.SOUL_LANTERN.defaultBlockState()); set(l, c.offset(27, 2, z), Blocks.SOUL_LANTERN.defaultBlockState()); }
        for (int i = 0; i < 12; i++) { int x = r.nextInt(50) - 25, z = r.nextInt(26) - 22; if (Math.abs(x) > 2) set(l, c.offset(x, 1, z), Blocks.BARREL.defaultBlockState()); }
        fill(l, c, -2, 1, -23, 2, 3, -23, Blocks.RED_NETHER_BRICKS.defaultBlockState());   // (the armoury wall)
    }

    /** The cells: a long block of barred cells down both sides, where they keep their prisoners. */
    private static void srCells(ServerLevel l, BlockPos c, RandomSource r) {
        box(l, c, -28, -24, 28, 6, 7, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.BLACKSTONE_SLAB.defaultBlockState(), null);
        fill(l, c, -3, 1, 6, 3, 4, 6, Blocks.AIR.defaultBlockState());
        for (int x = -26; x <= 22; x += 6) {
            if (Math.abs(x) < 4 || Math.abs(x + 4) < 4) continue;
            for (int z0 : new int[]{-23, -6}) {                                  // a cell: three walls of stone, bars in front
                int zf = z0 == -23 ? z0 + 4 : z0 - 4;
                fill(l, c, x, 1, Math.min(z0, zf), x, 4, Math.max(z0, zf), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                fill(l, c, x + 1, 1, zf, x + 4, 4, zf, Blocks.IRON_BARS.defaultBlockState());
            }
        }
        for (int z = -20; z <= 0; z += 5) set(l, c.offset(0, 6, z), Blocks.SOUL_LANTERN.defaultBlockState());
    }

    private static void srHideout(ServerLevel l, BlockPos c, RandomSource r) {
        srOuter(l, c, r);
        box(l, c, -16, -16, 16, 4, 7, Blocks.BLACKSTONE.defaultBlockState(), Blocks.BLACKSTONE_SLAB.defaultBlockState(), null);
        fill(l, c, -3, 1, 4, 3, 4, 4, Blocks.AIR.defaultBlockState());
        for (int x = -12; x <= 12; x += 6) { fill(l, c, x, 1, -15, x, 5, -15, Blocks.RED_NETHER_BRICKS.defaultBlockState()); set(l, c.offset(x, 4, -14), Blocks.SOUL_LANTERN.defaultBlockState()); }
        fill(l, c, -4, 1, -14, 4, 1, -12, Blocks.CRIMSON_PLANKS.defaultBlockState());             // the leader's platform
        set(l, c.offset(0, 2, -14), Blocks.CRIMSON_STAIRS.defaultBlockState());
        for (int x = -10; x <= 10; x += 10) set(l, c.offset(x, 1, -6), Blocks.CAMPFIRE.defaultBlockState());
        set(l, c.offset(0, 4, -15), Blocks.RED_BANNER.defaultBlockState());
        fill(l, c, -15, 1, -3, -12, 2, 0, Blocks.CRYING_OBSIDIAN.defaultBlockState());   // (Aurora portal tech)
    }

    private static void capital(ServerLevel l, BlockPos c, RandomSource r) {
        for (int x = -12; x <= 12; x += 6) for (int z : new int[]{-14, 10}) {
            fill(l, c, x, 1, z, x, 7, z, Blocks.QUARTZ_PILLAR.defaultBlockState());
            set(l, c.offset(x, 8, z), Blocks.GOLD_BLOCK.defaultBlockState());
        }
        box(l, c, -6, -20, 6, -16, 10, Blocks.QUARTZ_BRICKS.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.YELLOW_STAINED_GLASS_PANE.defaultBlockState());   // the palace front
        fill(l, c, -2, 1, -16, 2, 5, -16, Blocks.AIR.defaultBlockState());
        for (int i = 0; i < 14; i++) { double a = i * Math.PI * 2 / 14; set(l, c.offset((int) (Math.cos(a) * (R + 4)), -3, (int) (Math.sin(a) * (R + 4))), Blocks.WHITE_WOOL.defaultBlockState()); }   // clouds
        for (int x = -16; x <= 16; x += 8) lamp(l, c, x, 0);
    }

    private static void island(ServerLevel l, BlockPos c, RandomSource r) {
        for (int i = 0; i < 6; i++) tree(l, c, -16 + r.nextInt(32), -16 + r.nextInt(12), r);
        for (int i = 0; i < 30; i++) { int x = r.nextInt(2 * R) - R, z = r.nextInt(2 * R) - R; if (inside(x, z, R - 2)) set(l, c.offset(x, 1, z), r.nextBoolean() ? Blocks.GRASS.defaultBlockState() : Blocks.POPPY.defaultBlockState()); }
        fill(l, c, 10, 1, -6, 14, 3, -2, Blocks.QUARTZ_BRICKS.defaultBlockState());              // a small shrine
        fill(l, c, 11, 1, -2, 13, 2, -2, Blocks.AIR.defaultBlockState());
    }

    private static void farm(ServerLevel l, BlockPos c, RandomSource r) {
        for (int x = -18; x <= -4; x++) for (int z = -16; z <= -2; z++) {
            if (!inside(x, z, R - 2)) continue;
            if (z % 4 == 0) { set(l, c.offset(x, 0, z), Blocks.WATER.defaultBlockState()); continue; }
            set(l, c.offset(x, 0, z), Blocks.FARMLAND.defaultBlockState());
            BlockState crop = (x + z) % 3 == 0 ? Blocks.CARROTS.defaultBlockState() : Blocks.WHEAT.defaultBlockState();
            set(l, c.offset(x, 1, z), crop.getBlock() == Blocks.WHEAT ? crop.setValue(net.minecraft.world.level.block.CropBlock.AGE, 7) : crop.setValue(net.minecraft.world.level.block.CarrotBlock.AGE, 7));
        }
        box(l, c, 5, -16, 15, -8, 4, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState());   // Kitsune's Farm Store
        fill(l, c, 9, 1, -8, 10, 2, -8, Blocks.AIR.defaultBlockState());
        fill(l, c, 6, 1, -15, 9, 1, -15, Blocks.BARREL.defaultBlockState());
        set(l, c.offset(12, 1, -14), Blocks.COMPOSTER.defaultBlockState());
        for (int x = -18; x <= 18; x++) if (inside(x, 12, R - 1)) set(l, c.offset(x, 1, 12), Blocks.OAK_FENCE.defaultBlockState());
        fill(l, c, -2, 1, 12, 2, 1, 12, Blocks.AIR.defaultBlockState());
        tree(l, c, 16, 4, r); tree(l, c, -16, 6, r);
        set(l, c.offset(4, 1, -6), Blocks.HAY_BLOCK.defaultBlockState());
    }

    private static void station(ServerLevel l, BlockPos c, RandomSource r) {
        for (int x = -R; x <= R; x++) for (int z = -12; z <= -9; z++) if (inside(x, z, R)) set(l, c.offset(x, 0, z), Blocks.GRAVEL.defaultBlockState());
        for (int x = -R; x <= R; x++) if (inside(x, -10, R - 1)) { set(l, c.offset(x, 1, -11), Blocks.RAIL.defaultBlockState()); set(l, c.offset(x, 1, -10), Blocks.RAIL.defaultBlockState()); }
        fill(l, c, -16, 1, -7, 16, 1, -6, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
        for (int x = -14; x <= 14; x += 7) { fill(l, c, x, 1, -4, x, 5, -4, Blocks.STONE_BRICK_WALL.defaultBlockState()); }
        fill(l, c, -15, 6, -6, 15, 6, -2, Blocks.SPRUCE_SLAB.defaultBlockState());                // the roof over the platform
        for (int x = -12; x <= 12; x += 6) set(l, c.offset(x, 1, -2), Blocks.OAK_STAIRS.defaultBlockState());   // benches
        lamp(l, c, -18, 4); lamp(l, c, 18, 4);
    }

    private static void school(ServerLevel l, BlockPos c, RandomSource r) {
        box(l, c, -18, -18, 18, 4, 9, Blocks.STONE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState());
        fill(l, c, -3, 1, 4, 3, 5, 4, Blocks.AIR.defaultBlockState());
        for (int x = -12; x <= 12; x += 4) for (int z = -10; z <= -4; z += 3) set(l, c.offset(x, 1, z), Blocks.OAK_SLAB.defaultBlockState());   // desks
        fill(l, c, -6, 2, -17, 6, 5, -17, Blocks.BLACK_CONCRETE.defaultBlockState());             // the blackboard
        for (int i = 0; i < 12; i++) set(l, c.offset(-16 + r.nextInt(32), 7 + r.nextInt(2), -16 + r.nextInt(18)), Blocks.COBWEB.defaultBlockState());
        for (int x = -15; x <= 15; x += 10) set(l, c.offset(x, 8, -6), Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
    }

    private static void village(ServerLevel l, BlockPos c, RandomSource r) {
        for (int x = -R; x <= R; x++) for (int z = -2; z <= 2; z++) if (inside(x, z, R)) set(l, c.offset(x, 0, z), Blocks.DIRT_PATH.defaultBlockState());
        int[][] houses = {{-18, -14}, {-6, -16}, {7, -14}, {-16, 7}, {8, 8}};
        for (int[] h : houses) {
            box(l, c, h[0], h[1], h[0] + 7, h[1] + 5, 4, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.SPRUCE_SLAB.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState());
            set(l, c.offset(h[0] + 3, 1, h[1] + 5), Blocks.AIR.defaultBlockState()); set(l, c.offset(h[0] + 3, 2, h[1] + 5), Blocks.AIR.defaultBlockState());
        }
        set(l, c.offset(-3, 1, -10), Blocks.RED_BANNER.defaultBlockState());                       // the ramen shop sign
        tree(l, c, 17, -4, r); tree(l, c, -1, 14, r);
        for (int x = -15; x <= 15; x += 10) lamp(l, c, x, 3);
    }

    private static void battlefield(ServerLevel l, BlockPos c, RandomSource r) {
        for (int i = 0; i < 9; i++) {
            int cx = r.nextInt(30) - 15, cz = r.nextInt(30) - 15, rr = 2 + r.nextInt(3);
            for (int x = -rr; x <= rr; x++) for (int z = -rr; z <= rr; z++) if (inside(x, z, rr) && inside(cx + x, cz + z, R - 2)) {
                set(l, c.offset(cx + x, 0, cz + z), Blocks.BLACKSTONE.defaultBlockState());
                if (r.nextInt(6) == 0 && cz + z < 2) set(l, c.offset(cx + x, 1, cz + z), Blocks.FIRE.defaultBlockState());   // (no fire where the party comes in)
            }
        }
        for (int i = 0; i < 6; i++) { int x = r.nextInt(36) - 18, z = r.nextInt(36) - 18; if (inside(x, z, R - 3)) fill(l, c, x, 1, z, x + 1, 1 + r.nextInt(3), z, Blocks.CRACKED_STONE_BRICKS.defaultBlockState()); }
        fill(l, c, -14, 1, 14, -10, 2, 16, Blocks.IRON_BLOCK.defaultBlockState());               // a wrecked Government truck
        set(l, c.offset(-12, 3, 15), Blocks.CAMPFIRE.defaultBlockState());
    }
}
