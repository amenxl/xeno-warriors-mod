package com.xenowarriors.recarnet;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Pine Cone City up in the hills, with the Mountain Village at its heart: a plaza with a well and a big pine, cottages
 * along gravel lanes, Kitsune's Farm (store with Mikiko's room upstairs, greenhouse, barn, fields), the Disco Dojo
 * (light-up dance floor, DJ booth, treadmills, mirror wall, disco ball) and the Mountain Village Element School with
 * the Collection Book in its front hall. Outskirts: more houses, shops and pine woods. Drawn one chunk at a time.
 * With MrCrayfish's Furniture Refurbished installed, houses get tables, chairs, sofas and fridges.
 */
public class PineConeCityFeature extends Feature<NoneFeatureConfiguration> {
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> PINE_CONE_CITY = com.xenowarriors.world.SkyIslandFeature.FEATURES.register("pine_cone_city",
            () -> new PineConeCityFeature(NoneFeatureConfiguration.CODEC));
    static final int CELL = 32, ROAD = 5, VX = Recarnet.VILLAGE_X, VZ = Recarnet.VILLAGE_Z, R = Recarnet.VILLAGE_R, B = Recarnet.VILLAGE_Y;

    public PineConeCityFeature(Codec<NoneFeatureConfiguration> c) { super(c); }

    public static void init() {}

    private WorldGenLevel l;
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
    private int x0c, z0c;

    private void set(int x, int y, int z, BlockState s) { l.setBlock(mp.set(x, y, z), s, 2); }
    private static BlockState b(Block k) { return k.defaultBlockState(); }

    private static BlockState modBlock(String id, BlockState fallback) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("refurbished_furniture")) return fallback;
        Block bl = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("refurbished_furniture", id));
        return bl == null || bl == Blocks.AIR ? fallback : bl.defaultBlockState();
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        l = ctx.level();
        x0c = ctx.origin().getX() & ~15; z0c = ctx.origin().getZ() & ~15;
        long seed = l.getSeed();
        boolean any = false;
        for (int x = x0c; x < x0c + 16; x++) for (int z = z0c; z < z0c + 16; z++) any |= column(seed, x, z);
        return any;
    }

    private boolean inChunk(int x, int z) { return x >= x0c && x < x0c + 16 && z >= z0c && z < z0c + 16; }

    private boolean column(long seed, int x, int z) {
        int ux = x - VX, uz = z - VZ;
        double d = Math.sqrt((double) ux * ux + (double) uz * uz);
        if (d > R + Recarnet.VILLAGE_BLEND) return false;
        int top = l.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        int ground = d <= R ? B : (int) Math.round(Mth.lerp(Math.pow((d - R) / Recarnet.VILLAGE_BLEND, 0.8), B, Math.max(top, 63)));
        for (int y = ground + 1; y <= Math.max(top, ground) + 3; y++) set(x, y, z, b(Blocks.AIR));
        for (int y = ground - 1; y >= ground - 50 && y > l.getMinBuildHeight(); y--) {
            BlockState s = l.getBlockState(mp.set(x, y, z));
            if (!s.isAir() && s.getFluidState().isEmpty() && y < top - 1) break;
            set(x, y, z, y > ground - 4 ? b(Blocks.DIRT) : b(Blocks.STONE));
        }
        set(x, ground, z, b(Blocks.GRASS_BLOCK));
        if (d > R) { if (d < R + Recarnet.VILLAGE_BLEND - 6) pines(seed, x, z, ground, 4); return true; }
        // the village square
        if (d < 18) { plaza(x, z, ux, uz, d); return true; }
        int cx = Math.floorDiv(ux, CELL), cz = Math.floorDiv(uz, CELL);
        int lx = Math.floorMod(ux, CELL), lz = Math.floorMod(uz, CELL);
        if (cx >= 1 && cx <= 2 && cz >= 0 && cz <= 1) { farm(ux - 32, uz, x, z); return true; }
        if (cx >= -1 && cx <= 0 && cz == -2 && lz >= ROAD) { school(ux + 32, lz - ROAD, x, z); return true; }
        if (lx < ROAD || lz < ROAD) { lane(x, z, lx, lz, seed); return true; }
        int a = lx - ROAD, bb = lz - ROAD;                                     // 0..26 inside the lot
        if (cx == -2 && cz == 0) { dojo(a, bb, x, z); return true; }
        if (cx >= -1 && cx <= 0 && cz >= -1 && cz <= 0) { garden(seed, x, z, a, bb); return true; }   // around the square: gardens
        long h = AuroraCityFeature.hash(seed, cx, cz, 21);
        int k = (int) (h % 100);
        if (d > 95 && k < 25) pines(seed, x, z, B, 12);
        else if (d > 95 && k < 45) shop(h, a, bb, x, z);
        else cottage(h, a, bb, x, z);
        return true;
    }

    // ------------------------------------------------------------------ pieces

    private void garden(long seed, int x, int z, int a, int bb) {
        long h = AuroraCityFeature.hash(seed, x, z, 31) % 17;
        if (h == 0) set(x, B + 1, z, b(Blocks.ROSE_BUSH));
        else if (h == 1) set(x, B + 1, z, b(Blocks.LILAC));
        else if (h < 5) set(x, B + 1, z, b(Blocks.GRASS));
        if (h == 0 && ((x + z) & 3) == 0) { set(x, B + 1, z, b(Blocks.AZALEA)); }
        if ((a == 13 || bb == 13) && h < 9) set(x, B, z, b(Blocks.DIRT_PATH));
        pines(seed, x, z, B, 14);
    }

    private void lane(int x, int z, int lx, int lz, long seed) {
        int across = lx < ROAD ? lx : lz, along = lx < ROAD ? lz : lx;
        set(x, B, z, across == 0 || across == ROAD - 1 ? b(Blocks.COARSE_DIRT) : (AuroraCityFeature.hash(seed, x, z, 5) % 5 == 0 ? b(Blocks.COBBLESTONE) : b(Blocks.GRAVEL)));
        set(x, B - 1, z, b(Blocks.DIRT));
        if (across == 0 && along % 12 == 7 && !(lx < ROAD && lz < ROAD)) {    // lantern posts
            for (int y = 1; y <= 3; y++) set(x, B + y, z, b(Blocks.SPRUCE_FENCE));
            set(x, B + 4, z, b(Blocks.LANTERN));
        }
    }

    private void plaza(int x, int z, int ux, int uz, double d) {
        set(x, B, z, ((int) d) % 3 == 0 ? b(Blocks.STONE_BRICKS) : b(Blocks.COBBLESTONE));
        if (d < 2.2) {                                                          // the well
            set(x, B, z, b(Blocks.WATER));
            set(x, B - 1, z, b(Blocks.WATER));
        } else if (d < 3.4) {
            set(x, B + 1, z, b(Blocks.COBBLESTONE_WALL));
            if (Math.abs(ux) == 2 && Math.abs(uz) == 2) { set(x, B + 2, z, b(Blocks.SPRUCE_FENCE)); set(x, B + 3, z, b(Blocks.SPRUCE_FENCE)); set(x, B + 4, z, b(Blocks.SPRUCE_SLAB)); }
        } else if (Math.abs(d - 12) < 0.7 && (ux + uz) % 3 == 0) {             // a ring of planters
            set(x, B + 1, z, b(Blocks.SPRUCE_TRAPDOOR));
            set(x, B, z, b(Blocks.GRASS_BLOCK));
            set(x, B + 1, z, ((ux * 7 + uz) & 1) == 0 ? b(Blocks.POPPY) : b(Blocks.CORNFLOWER));
        } else if (ux >= 9 && ux <= 11 && uz == -9) {
            set(x, B + 1, z, b(Blocks.SPRUCE_STAIRS));                          // benches
        }
        if (ux == 7 && uz == 7) sign(x, B + 1, z, true, "Mountain", "Village", "~ Pine Cone", "City ~");
        if (ux == -7 && uz == 7) set(x, B + 1, z, net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(com.xenowarriors.XenoWarriors.id("bounty_board")).defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.EAST));       // the village Bounty Board
    }

    /** A spruce with a cone of needles (column-wise, from a few hashed spots). */
    private void pines(long seed, int x, int z, int ground, int cell) {
        int gx = Math.floorDiv(x, cell), gz = Math.floorDiv(z, cell);
        for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) {
            long h = AuroraCityFeature.hash(seed, gx + ox, gz + oz, 9);
            if (h % 3 != 0) continue;
            int tx = (gx + ox) * cell + (int) ((h >> 4) % cell), tz = (gz + oz) * cell + (int) ((h >> 9) % cell);
            int hgt = 8 + (int) ((h >> 14) % 6);
            int dx = x - tx, dz = z - tz, dd = dx * dx + dz * dz;
            if (dd == 0) { for (int y = 1; y <= hgt; y++) set(x, ground + y, z, b(Blocks.SPRUCE_LOG)); set(x, ground + hgt + 1, z, b(Blocks.SPRUCE_LEAVES)); return; }
            for (int y = 3; y <= hgt; y++) {
                double rr = (hgt - y) * 0.45 + ((y % 2 == 0) ? 0.6 : 0);
                if (dd <= rr * rr && dd > 0) set(x, ground + y, z, b(Blocks.SPRUCE_LEAVES).setValue(LeavesBlock.PERSISTENT, true));
            }
        }
    }

    private void cottage(long h, int a, int bb, int x, int z) {
        int w = 9 + 2 * (int) (h % 3), d = 7 + 2 * (int) ((h >> 4) % 3);
        int x0 = (27 - w) / 2, z0 = (27 - d) / 2 + 2, x1 = x0 + w - 1, z1 = z0 + d - 1, mid = (x0 + x1) / 2;
        boolean in = a >= x0 && a <= x1 && bb >= z0 && bb <= z1;
        if (!in) {
            if (a == mid && bb < z0) set(x, B, z, b(Blocks.DIRT_PATH));
            else if ((h >> 20) % 7 == (a * 3 + bb) % 7 && (a < x0 - 1 || a > x1 + 1)) set(x, B + 1, z, ((a + bb) & 1) == 0 ? b(Blocks.OXEYE_DAISY) : b(Blocks.ALLIUM));
            return;
        }
        boolean light = ((h >> 8) & 1) == 0;
        BlockState wall = light ? b(Blocks.SPRUCE_PLANKS) : b(Blocks.WHITE_TERRACOTTA);
        boolean ex = a == x0 || a == x1, ez = bb == z0 || bb == z1, edge = ex || ez, corner = ex && ez;
        set(x, B, z, b(Blocks.SPRUCE_PLANKS));
        int half = (d - 1) / 2;
        int k = Math.min(bb - z0, z1 - bb);                                    // steps from the eaves
        int roofY = B + 5 + k;
        for (int y = 1; y <= 4; y++) {
            BlockState s = b(Blocks.AIR);
            if (edge) {
                if (bb == z0 && a == mid && y <= 2) s = y == 1 ? b(Blocks.SPRUCE_DOOR).setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                        : b(Blocks.SPRUCE_DOOR).setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
                else if (corner) s = b(Blocks.STRIPPED_SPRUCE_LOG);
                else if (y == 1) s = b(Blocks.COBBLESTONE);
                else if ((y == 2 || y == 3) && ((ex ? bb : a) % 3 == 1)) s = b(Blocks.GLASS_PANE);
                else s = wall;
            }
            set(x, B + y, z, s);
        }
        if (ex) for (int y = B + 5; y < roofY; y++) set(x, y, z, wall);          // gable ends
        if (k == half) set(x, roofY, z, b(Blocks.DARK_OAK_PLANKS));              // ridge
        else set(x, roofY, z, b(Blocks.DARK_OAK_STAIRS).setValue(StairBlock.FACING, bb - z0 < z1 - bb ? Direction.SOUTH : Direction.NORTH));
        if (((h >> 12) & 1) == 0 && a == x0 + 1 && bb == z1 - 1) for (int y = B + 1; y <= roofY + 2; y++) set(x, y, z, b(Blocks.COBBLESTONE));   // chimney
        if (edge) return;
        // inside: bed, table + chairs, fridge (furniture mod if installed), a lantern
        if (a == x0 + 1 && bb == z1 - 2) set(x, B + 1, z, b(Blocks.RED_BED).setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
        else if (a == x0 + 1 && bb == z1 - 1) set(x, B + 1, z, b(Blocks.RED_BED).setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
        else if (a == mid && bb == z0 + d / 2) set(x, B + 1, z, modBlock("oak_table", b(Blocks.CRAFTING_TABLE)));
        else if (a == mid + 1 && bb == z0 + d / 2) set(x, B + 1, z, modBlock("oak_chair", b(Blocks.SPRUCE_STAIRS).setValue(StairBlock.FACING, Direction.EAST)));
        else if (a == mid - 1 && bb == z0 + d / 2) set(x, B + 1, z, modBlock("oak_chair", b(Blocks.SPRUCE_STAIRS).setValue(StairBlock.FACING, Direction.WEST)));
        else if (a == x1 - 1 && bb == z1 - 1) {
            BlockState fridge = modBlock("light_fridge", b(Blocks.BARREL));
            set(x, B + 1, z, fridge);
            if (fridge.getBlock() != Blocks.BARREL) set(x, B + 2, z, modBlock("light_freezer", b(Blocks.AIR)));
        } else if (a == x1 - 1 && bb == z0 + 1) set(x, B + 1, z, modBlock("white_sofa", b(Blocks.SMOKER)));
        else if (a == x0 + 1 && bb == z0 + 1) set(x, B + 1, z, b(Blocks.LANTERN));
    }

    private void shop(long h, int a, int bb, int x, int z) {
        int x0 = 6, x1 = 20, z0 = 6, z1 = 18;
        boolean in = a >= x0 && a <= x1 && bb >= z0 && bb <= z1;
        set(x, B, z, in || bb < z0 ? b(Blocks.SMOOTH_STONE) : b(Blocks.GRASS_BLOCK));
        if (!in) return;
        BlockState wall = ((h >> 6) & 1) == 0 ? b(Blocks.LIGHT_GRAY_CONCRETE) : b(Blocks.ORANGE_TERRACOTTA);
        boolean edge = a == x0 || a == x1 || bb == z0 || bb == z1, corner = (a == x0 || a == x1) && (bb == z0 || bb == z1);
        for (int y = 1; y <= 6; y++) {
            BlockState s = b(Blocks.AIR);
            if (edge) {
                if (bb == z0 && y <= 3 && !corner) s = (a == 13 && y <= 2) ? b(Blocks.AIR) : b(Blocks.GLASS);        // shop window + door
                else if (y == 4 && bb == z0) s = ((h >> 10) & 1) == 0 ? b(Blocks.RED_WOOL) : b(Blocks.BLUE_WOOL);      // awning stripe
                else s = corner ? b(Blocks.GRAY_CONCRETE) : wall;
            } else if (y == 1 && (a == x0 + 1 || a == x1 - 1)) s = b(Blocks.BARREL);                                    // shelves
            set(x, B + y, z, s);
        }
        set(x, B + 7, z, edge ? b(Blocks.SMOOTH_STONE_SLAB) : b(Blocks.GRAY_CONCRETE));
    }

    // ------------------------------------------------------------------ Kitsune's Farm (64 x 64)

    private void farm(int fx, int fz, int x, int z) {
        set(x, B, z, b(Blocks.GRASS_BLOCK));
        boolean border = fx == 0 || fx == 63 || fz == 0 || fz == 63;
        if (border) { if (!(fz == 0 && Math.abs(fx - 11) <= 1)) set(x, B + 1, z, b(Blocks.OAK_FENCE)); return; }
        if (fx == 11 && fz < 4) { set(x, B, z, b(Blocks.DIRT_PATH)); return; }
        if (fz == 1 && fx == 14) sign(x, B + 1, z, true, "Kitsune's", "Farm", "Seeds - Tools", "Feed - Produce");
        // the store (Mikiko's room upstairs)
        if (fx >= 4 && fx <= 18 && fz >= 4 && fz <= 16) { store(fx - 4, fz - 4, x, z); return; }
        // greenhouse
        if (fx >= 24 && fx <= 34 && fz >= 4 && fz <= 12) {
            boolean edge = fx == 24 || fx == 34 || fz == 4 || fz == 12;
            set(x, B, z, edge ? b(Blocks.STONE_BRICKS) : b(Blocks.DIRT));
            for (int y = 1; y <= 4; y++) set(x, B + y, z, edge && !(fz == 4 && fx == 29 && y <= 2) ? b(Blocks.GLASS) : b(Blocks.AIR));
            set(x, B + 5, z, b(Blocks.GLASS));
            if (!edge) set(x, B + 1, z, ((fx + fz) % 3 == 0) ? b(Blocks.OAK_SAPLING) : (fx + fz) % 3 == 1 ? b(Blocks.PEONY) : b(Blocks.LILY_OF_THE_VALLEY));
            return;
        }
        // barn + animal pen
        if (fx >= 44 && fx <= 58 && fz >= 4 && fz <= 16) {
            boolean edge = fx == 44 || fx == 58 || fz == 4 || fz == 16;
            int peak = 5 + Math.min(fx - 44, 58 - fx) / 2;
            set(x, B, z, b(Blocks.SPRUCE_PLANKS));
            for (int y = 1; y < peak; y++) {
                BlockState s = b(Blocks.AIR);
                if (edge && !(fz == 16 && Math.abs(fx - 51) <= 1 && y <= 3)) s = y <= 4 ? ((fx + y) % 4 == 0 ? b(Blocks.WHITE_TERRACOTTA) : b(Blocks.RED_TERRACOTTA)) : b(Blocks.RED_TERRACOTTA);
                else if (!edge && y == 1 && (fx + fz) % 5 == 0) s = b(Blocks.HAY_BLOCK);
                set(x, B + y, z, s);
            }
            set(x, B + peak, z, b(Blocks.DARK_OAK_PLANKS));
            return;
        }
        if (fx >= 44 && fx <= 58 && fz >= 19 && fz <= 29) {
            if (fx == 44 || fx == 58 || fz == 19 || fz == 29) { if (!(fz == 19 && fx == 51)) set(x, B + 1, z, b(Blocks.OAK_FENCE)); }
            else if (fx == 47 && fz == 22 && inChunk(x, z)) animal(EntityType.COW, x, z);
            else if (fx == 54 && fz == 25 && inChunk(x, z)) animal(EntityType.CHICKEN, x, z);
            else if (fx == 51 && fz == 26 && inChunk(x, z)) animal(EntityType.SHEEP, x, z);
            else if (fx == 50 && fz == 23) set(x, B + 1, z, b(Blocks.COMPOSTER));
            return;
        }
        // fields: water every 9th row, crops in four kinds
        if (fx >= 4 && fx <= 58 && fz >= 34 && fz <= 60) {
            if ((fz - 34) % 9 == 4) { set(x, B, z, b(Blocks.WATER)); return; }
            set(x, B, z, b(Blocks.FARMLAND).setValue(FarmBlock.MOISTURE, 7));
            int kind = (fx - 4) / 14;
            if (fx == 30 && fz == 47) { set(x, B, z, b(Blocks.GRASS_BLOCK)); set(x, B + 1, z, b(Blocks.OAK_FENCE)); set(x, B + 2, z, b(Blocks.HAY_BLOCK)); set(x, B + 3, z, b(Blocks.CARVED_PUMPKIN).setValue(CarvedPumpkinBlock.FACING, Direction.NORTH)); return; }   // scarecrow
            BlockState crop = switch (kind) {
                case 0 -> b(Blocks.WHEAT).setValue(CropBlock.AGE, 7);
                case 1 -> b(Blocks.CARROTS).setValue(CarrotBlock.AGE, 7);
                case 2 -> b(Blocks.POTATOES).setValue(PotatoBlock.AGE, 7);
                default -> b(Blocks.BEETROOTS).setValue(BeetrootBlock.AGE, 3);
            };
            set(x, B + 1, z, crop);
            return;
        }
        if (fx == 11 && fz > 16 && fz < 34) set(x, B, z, b(Blocks.DIRT_PATH));
    }

    private void animal(EntityType<? extends net.minecraft.world.entity.Mob> type, int x, int z) {
        var e = type.create(l.getLevel());
        if (e == null) return;
        e.moveTo(x + 0.5, B + 1, z + 0.5, 0, 0);
        e.setPersistenceRequired();
        l.addFreshEntity(e);
    }

    private void store(int a, int bb, int x, int z) {             // 15 x 13, two floors
        boolean ex = a == 0 || a == 14, ez = bb == 0 || bb == 12, edge = ex || ez, corner = ex && ez;
        set(x, B, z, b(Blocks.OAK_PLANKS));
        for (int y = 1; y <= 9; y++) {
            BlockState s = b(Blocks.AIR);
            if (y == 5) s = (a == 13 && bb == 11) ? b(Blocks.AIR) : b(Blocks.OAK_PLANKS);               // upstairs floor (ladder hole)
            else if (edge) {
                if (bb == 0 && a == 7 && y <= 2) s = y == 1 ? b(Blocks.OAK_DOOR).setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                        : b(Blocks.OAK_DOOR).setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
                else if (corner) s = b(Blocks.STRIPPED_OAK_LOG);
                else if ((y == 2 || y == 3 || y == 7) && (ex ? bb : a) % 3 == 1) s = b(Blocks.GLASS_PANE);
                else s = y > 8 ? b(Blocks.AIR) : b(Blocks.RED_TERRACOTTA);
            } else if (y == 1) {
                if (bb == 6 && a >= 3 && a <= 11) s = a == 7 ? b(Blocks.DROPPER) : b(Blocks.OAK_SLAB).setValue(SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP);   // counter + register
                else if ((a == 1 || a == 13) && bb >= 2 && bb <= 10) s = bb % 2 == 0 ? b(Blocks.BARREL) : b(Blocks.HAY_BLOCK);
            } else if (y == 6) {                                         // Mikiko's room
                if (a == 2 && bb == 9) s = b(Blocks.RED_BED).setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
                else if (a == 2 && bb == 10) s = b(Blocks.RED_BED).setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD);
                else if (a == 4 && bb == 11) s = b(Blocks.CHEST);
                else if (a == 7 && bb == 11) s = b(Blocks.SPRUCE_SLAB).setValue(SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP);
                else if (a == 7 && bb == 10) s = b(Blocks.SPRUCE_STAIRS).setValue(StairBlock.FACING, Direction.SOUTH);
                else if (a == 10 && bb == 2) s = b(Blocks.LANTERN);
            }
            if (a == 13 && bb == 11 && y <= 5) s = b(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.NORTH);
            set(x, B + y, z, s);
        }
        int k = Math.min(bb, 12 - bb);                                   // gable roof
        set(x, B + 9 + k, z, k == 6 ? b(Blocks.SPRUCE_PLANKS) : b(Blocks.SPRUCE_STAIRS).setValue(StairBlock.FACING, bb < 6 ? Direction.SOUTH : Direction.NORTH));
        if (ex) for (int y = B + 9; y < B + 9 + k; y++) set(x, y, z, b(Blocks.RED_TERRACOTTA));
    }

    // ------------------------------------------------------------------ Disco Dojo

    private static final Block[] FLOOR = {Blocks.MAGENTA_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS, Blocks.LIME_STAINED_GLASS, Blocks.PINK_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS};

    private void dojo(int a, int bb, int x, int z) {
        int x0 = 1, x1 = 25, z0 = 3, z1 = 25;
        boolean in = a >= x0 && a <= x1 && bb >= z0 && bb <= z1;
        set(x, B, z, in ? b(Blocks.POLISHED_BLACKSTONE) : b(Blocks.SMOOTH_STONE));
        if (!in) {
            if (bb == 1 && a == 16) sign(x, B + 1, z, true, "~ DISCO DOJO ~", "Komatsu Norio", "We fight with", "our words!");
            return;
        }
        boolean ex = a == x0 || a == x1, ez = bb == z0 || bb == z1, edge = ex || ez;
        for (int y = 1; y <= 7; y++) {
            BlockState s = b(Blocks.AIR);
            if (y == 7) s = (a + bb) % 6 == 0 ? b(Blocks.REDSTONE_LAMP).setValue(RedstoneLampBlock.LIT, true) : b(Blocks.BLACK_CONCRETE);
            else if (edge) {
                if (bb == z0 && Math.abs(a - 13) <= 1 && y <= 3) s = b(Blocks.AIR);
                else if (bb == z0 && y == 6) s = (a % 2 == 0) ? b(Blocks.REDSTONE_LAMP).setValue(RedstoneLampBlock.LIT, true) : b(Blocks.MAGENTA_STAINED_GLASS);   // neon front
                else if (y == 3) s = b(Blocks.PURPLE_CONCRETE);
                else s = b(Blocks.BLACK_CONCRETE);
            } else if (a == x1 - 1 && y <= 4 && bb > z0 + 2 && bb < z1 - 2) s = b(Blocks.LIGHT_GRAY_STAINED_GLASS);        // mirror wall
            set(x, B + y, z, s);
        }
        if (edge) return;
        // light-up dance floor
        if (a >= 7 && a <= 19 && bb >= 8 && bb <= 19) {
            set(x, B, z, b(FLOOR[(((a - 7) / 2) + ((bb - 8) / 2) * 3) % FLOOR.length]));
            set(x, B - 1, z, b(Blocks.GLOWSTONE));
        }
        // treadmills (arrow terracotta belts with rails)
        if (a >= 3 && a <= 5 && bb >= 8 && bb <= 18) {
            set(x, B, z, b(Blocks.MAGENTA_GLAZED_TERRACOTTA).setValue(GlazedTerracottaBlock.FACING, Direction.SOUTH));
            if (bb == 8 || bb == 18) { set(x, B + 1, z, b(Blocks.IRON_BARS)); set(x, B + 2, z, b(Blocks.IRON_BARS)); }
        }
        // DJ booth
        if (bb >= 21 && bb <= 23 && a >= 9 && a <= 17) {
            set(x, B + 1, z, bb == 21 ? b(Blocks.QUARTZ_BLOCK) : a == 13 && bb == 22 ? b(Blocks.JUKEBOX) : b(Blocks.AIR));
            if (bb == 21 && (a == 10 || a == 16)) set(x, B + 2, z, b(Blocks.NOTE_BLOCK));
        }
        if ((a == 2 || a == x1 - 2) && (bb == 4 || bb == z1 - 1)) for (int y = 1; y <= 3; y++) set(x, B + y, z, b(Blocks.NOTE_BLOCK));      // speakers
        // disco ball
        if (a == 13 && bb == 14) { set(x, B + 6, z, b(Blocks.SEA_LANTERN)); set(x, B + 5, z, b(Blocks.WHITE_STAINED_GLASS)); }
        if ((Math.abs(a - 13) == 1 && bb == 14) || (a == 13 && Math.abs(bb - 14) == 1)) set(x, B + 6, z, b(Blocks.WHITE_STAINED_GLASS));
    }

    // ------------------------------------------------------------------ Mountain Village Element School (64 x 27)

    private void school(int sx, int sz, int x, int z) {
        int x0 = 4, x1 = 59, z0 = 3, z1 = 23;
        boolean in = sx >= x0 && sx <= x1 && sz >= z0 && sz <= z1;
        set(x, B, z, in ? b(Blocks.SMOOTH_STONE) : sz < z0 ? b(Blocks.STONE_BRICKS) : b(Blocks.GRASS_BLOCK));
        if (!in) {
            if (sx == 20 && sz == 1) { for (int y = 1; y <= 8; y++) set(x, B + y, z, b(Blocks.SPRUCE_FENCE)); set(x, B + 8, z, b(Blocks.WHITE_WOOL)); }
            if (sx == 21 && sz == 1) { set(x, B + 8, z, b(Blocks.ORANGE_WOOL)); set(x, B + 7, z, b(Blocks.WHITE_WOOL)); }
            if (sx == 36 && sz == 1) sign(x, B + 1, z, true, "Mountain Village", "Element School", "~ Karma ~", "Elements - Arts");
            return;
        }
        boolean ex = sx == x0 || sx == x1, ez = sz == z0 || sz == z1, edge = ex || ez, corner = ex && ez;
        boolean inner = !edge && (sx == 18 || sx == 45) && !(sz >= 9 && sz <= 11);            // classroom walls with doorways
        for (int y = 1; y <= 12; y++) {
            BlockState s = b(Blocks.AIR);
            if (y == 6 || y == 12) s = edge ? b(Blocks.LIGHT_BLUE_CONCRETE) : (sx == 6 && sz == 21 && y == 6) ? b(Blocks.AIR) : b(Blocks.WHITE_CONCRETE);
            else if (edge) {
                if (sz == z0 && (sx == 31 || sx == 32) && y <= 3) s = b(Blocks.AIR);
                else if (corner) s = b(Blocks.STONE_BRICKS);
                else if (y == 1) s = b(Blocks.TERRACOTTA);
                else if ((y >= 2 && y <= 4 || y >= 8 && y <= 10) && (ex ? sz : sx) % 3 != 0) s = b(Blocks.GLASS_PANE);
                else s = b(Blocks.WHITE_CONCRETE);
            } else if (inner && y < 12) s = b(Blocks.WHITE_CONCRETE);
            else if (y == 1 && sx > 18 && sx < 45 && sz == 7 && sx == 31) s = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(com.xenowarriors.XenoWarriors.id("collection_book")).defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);                                       // the Collection Book in the front hall
            else if (y == 1 && (sx < 18 || sx > 45) && sz > 5 && sz < 20) {                                            // classrooms: desks and chairs
                if (sz % 3 == 0 && sx % 3 != 0) s = b(Blocks.OAK_SLAB).setValue(SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP);
                else if (sz % 3 == 1 && sx % 3 != 0) s = b(Blocks.OAK_STAIRS).setValue(StairBlock.FACING, Direction.SOUTH);
            } else if ((y == 2 || y == 3) && sz == z1 - 1 && (sx < 17 || sx > 46) && sx > x0 + 1 && sx < x1 - 1) s = b(Blocks.BLACK_CONCRETE);   // chalkboards
            else if (y >= 7 && y <= 9 && (sz == z0 + 1 || sz == z1 - 1) && sx > 18 && sx < 45) s = b(Blocks.BOOKSHELF);              // library upstairs
            else if (y == 5 && (sx - x0) % 7 == 3 && (sz - z0) % 6 == 3) s = b(Blocks.LANTERN).setValue(LanternBlock.HANGING, true);
            if (sx == 6 && sz == 21 && y <= 6) s = b(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.NORTH);
            set(x, B + y, z, s);
        }
        if (sx == 34 && sz == 7) com.xenowarriors.journey.JourneyGuide.place(l, x + 0.5, B + 1, z + 0.5, 180F);   // the Journey Guide by the Collection Book
        if (edge && (sx + sz) % 2 == 0) set(x, B + 13, z, b(Blocks.STONE_BRICK_WALL));
    }

    private void sign(int x, int y, int z, boolean standing, String... lines) {
        set(x, y, z, b(Blocks.SPRUCE_SIGN));
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
}
