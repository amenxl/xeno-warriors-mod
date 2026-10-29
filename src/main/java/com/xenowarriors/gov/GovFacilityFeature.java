package com.xenowarriors.gov;

import com.mojang.serialization.Codec;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.sky.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.RegistryObject;

/**
 * A Government facility (like F2-F6 in the show): a grey concrete block with blue window strips outside; inside, a
 * dark corridor with light strips in the floor and ceiling, a tall lab under a blue arched glass roof with a row of
 * healing pods and consoles, a storage room of Government supplies, workers and C2 guards with modifications.
 */
public class GovFacilityFeature extends Feature<NoneFeatureConfiguration> {
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> GOV_FACILITY = com.xenowarriors.world.SkyIslandFeature.FEATURES.register("gov_facility",
            () -> new GovFacilityFeature(NoneFeatureConfiguration.CODEC));
    private static final ResourceLocation LOOT = XenoWarriors.id("chests/gov_facility");
    static final int HX = 11, HZ = 8, H = 7;                         // half-size (23 x 17) and wall height

    public GovFacilityFeature(Codec<NoneFeatureConfiguration> c) { super(c); }

    public static void init() {}

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!(ctx.level().getLevel() instanceof net.minecraft.server.level.ServerLevel sl0) || !com.xenowarriors.recarnet.Recarnet.is(sl0)) return false;   // (Recarnet only: nothing of the show's world is built in the overworld)
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos o = ctx.origin();
        BlockPos c = new BlockPos((o.getX() & ~15) + 8, 0, (o.getZ() & ~15) + 8);
        // flat enough ground, not in water
        int lo = 999, hi = -999;
        for (int dx = -HX; dx <= HX; dx += 4) for (int dz = -HZ; dz <= HZ; dz += 4) {
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, c.getX() + dx, c.getZ() + dz);
            lo = Math.min(lo, y); hi = Math.max(hi, y);
        }
        if (hi - lo > 4) return false;
        BlockPos base = new BlockPos(c.getX(), lo, c.getZ());
        if (!level.getBlockState(base.below()).getFluidState().isEmpty()) return false;
        build(level, r, base);
        return true;
    }

    private static void set(WorldGenLevel l, BlockPos p, BlockState s) { if (com.xenowarriors.world.Clip.ok(p)) l.setBlock(p, s, 2); }

    public static void build(WorldGenLevel l, RandomSource r, BlockPos b) {
        BlockState wall = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), trim = Blocks.GRAY_CONCRETE.defaultBlockState(),
                inner = Blocks.GRAY_CONCRETE.defaultBlockState(), dark = Blocks.BLACK_CONCRETE.defaultBlockState(),
                floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), lamp = Blocks.SEA_LANTERN.defaultBlockState(),
                glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), band = Blocks.WHITE_CONCRETE.defaultBlockState(),
                air = Blocks.AIR.defaultBlockState(), arch = Blocks.CYAN_CONCRETE.defaultBlockState(), roof = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
        // foundation, shell, roof
        for (int dx = -HX; dx <= HX; dx++) for (int dz = -HZ; dz <= HZ; dz++) {
            for (int y = -3; y < 0; y++) set(l, b.offset(dx, y, dz), Blocks.STONE.defaultBlockState());
            set(l, b.offset(dx, -1, dz), floor);
            boolean edge = Math.abs(dx) == HX || Math.abs(dz) == HZ;
            for (int y = 0; y < H; y++) {
                BlockState s = air;
                if (edge) s = y == H - 1 ? trim : (y == 3 && (dx + dz) % 2 == 0 && Math.abs(dx) < HX - 1 && Math.abs(dz) != HZ - 0 || y == 3 && Math.abs(dz) < HZ - 1) ? glass : y == 1 ? trim : wall;
                set(l, b.offset(dx, y, dz), s);
            }
            for (int y = H; y < H + 4; y++) set(l, b.offset(dx, y, dz), air);
            set(l, b.offset(dx, H, dz), Math.abs(dx) == HX || Math.abs(dz) == HZ ? trim : roof);
        }
        // entrance on the south side (+z), with a lit frame
        for (int dx = -1; dx <= 1; dx++) for (int y = 0; y < 3; y++) set(l, b.offset(dx, y, HZ), air);
        for (int y = 0; y < 4; y++) { set(l, b.offset(-2, y, HZ), dark); set(l, b.offset(2, y, HZ), dark); }
        for (int dx = -2; dx <= 2; dx++) set(l, b.offset(dx, 3, HZ), dark);
        set(l, b.offset(0, 3, HZ), lamp);
        // the corridor: dark walls with a white band, light strips in floor and ceiling (z from HZ-1 to 1)
        for (int dz = 1; dz < HZ; dz++) {
            for (int s : new int[]{-2, 2}) for (int y = 0; y < 4; y++) set(l, b.offset(s, y, dz), y == 2 ? band : inner);
            for (int dx = -1; dx <= 1; dx++) { set(l, b.offset(dx, 4, dz), dark); set(l, b.offset(dx, -1, dz), dx == 0 && dz % 2 == 0 ? lamp : dark); }
            set(l, b.offset(0, 4, dz), dz % 2 == 1 ? lamp : dark);
        }
        // storage room (west of the corridor): shelves of barrels and chests
        for (int dx = -HX + 1; dx <= -3; dx++) for (int dz = 1; dz < HZ; dz++) for (int y = 0; y < 4; y++) set(l, b.offset(dx, y, dz), air);
        for (int dx = -HX + 1; dx <= -3; dx++) for (int dz = 1; dz < HZ; dz++) set(l, b.offset(dx, 4, dz), dark);
        set(l, b.offset(-2, 0, 4), air); set(l, b.offset(-2, 1, 4), air);           // doorway
        for (int dx = -HX + 1; dx <= -4; dx += 2) {
            set(l, b.offset(dx, 0, HZ - 1), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
            set(l, b.offset(dx, 1, HZ - 1), Blocks.IRON_BLOCK.defaultBlockState());
        }
        for (int i = 0; i < 3; i++) chest(l, r, b.offset(-HX + 2 + i * 3, 0, 1), Direction.SOUTH);
        set(l, b.offset(-6, 3, 4), lamp);
        // east room: a security office with a console
        for (int dx = 3; dx <= HX - 1; dx++) for (int dz = 1; dz < HZ; dz++) for (int y = 0; y < 4; y++) set(l, b.offset(dx, y, dz), air);
        for (int dx = 3; dx <= HX - 1; dx++) for (int dz = 1; dz < HZ; dz++) set(l, b.offset(dx, 4, dz), dark);
        set(l, b.offset(2, 0, 4), air); set(l, b.offset(2, 1, 4), air);
        for (int dz = 2; dz <= 5; dz++) { set(l, b.offset(HX - 1, 0, dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState()); set(l, b.offset(HX - 1, 1, dz), dz % 2 == 0 ? Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true) : Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState()); }
        chest(l, r, b.offset(4, 0, HZ - 1), Direction.NORTH);
        set(l, b.offset(6, 3, 4), lamp);
        // the lab (north half): tall, under a blue arched glass roof on cyan ribs, light strips down the floor
        for (int dx = -HX + 1; dx <= HX - 1; dx++) for (int dz = -HZ + 1; dz <= 0; dz++) {
            for (int y = 0; y < H; y++) set(l, b.offset(dx, y, dz), air);
            set(l, b.offset(dx, -1, dz), Math.abs(dz + 4) == 3 && dx % 2 == 0 ? lamp : Blocks.SMOOTH_QUARTZ.defaultBlockState());
        }
        for (int dx = -HX; dx <= HX; dx++) {                 // the arch: rises toward the middle
            int ax = Math.abs(dx);
            int y = H + (ax < 3 ? 3 : ax < 6 ? 2 : ax < 9 ? 1 : 0);
            for (int dz = -HZ; dz <= 0; dz++) {
                boolean rib = dz % 3 == 0;
                set(l, b.offset(dx, y, dz), rib ? arch : glass);
                for (int yy = H; yy < y; yy++) if (Math.abs(dz) == HZ || dz == 0) set(l, b.offset(dx, yy, dz), arch);
            }
        }
        for (int dz = -HZ + 1; dz <= 0; dz += 3) for (int s : new int[]{-HX + 1, HX - 1}) for (int y = 0; y < H; y++) set(l, b.offset(s, y, dz), arch);   // pillars
        set(l, b.offset(0, 0, 0), air); set(l, b.offset(0, 1, 0), air); set(l, b.offset(-1, 0, 0), air); set(l, b.offset(1, 0, 0), air); set(l, b.offset(-1, 1, 0), air); set(l, b.offset(1, 1, 0), air);
        // a row of healing pods along the back wall, facing into the room
        BlockState pod = ModBlocks.HEALING_POD.get().defaultBlockState().setValue(HealingPodBlock.FACING, Direction.SOUTH);
        for (int dx = -8; dx <= 8; dx += 4) {
            set(l, b.offset(dx, 0, -HZ + 2), pod.setValue(HealingPodBlock.HALF, DoubleBlockHalf.LOWER));
            set(l, b.offset(dx, 1, -HZ + 2), pod.setValue(HealingPodBlock.HALF, DoubleBlockHalf.UPPER));
            set(l, b.offset(dx, 0, -HZ + 4), Blocks.IRON_BLOCK.defaultBlockState());                     // its console
            set(l, b.offset(dx, 1, -HZ + 4), Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
        }
        // the vault in the back corner (a Gov Keycard opens it)
        set(l, b.offset(-HX + 2, 0, -HZ + 1), GovItems.VAULT.get().defaultBlockState().setValue(GovVaultBlock.FACING, Direction.SOUTH));
        set(l, b.offset(-HX + 2, 1, -HZ + 1), Blocks.IRON_BLOCK.defaultBlockState());
        // a supply terminal in the security office
        set(l, b.offset(5, 0, 1), GovItems.TERMINAL.get().defaultBlockState().setValue(GovTerminalBlock.FACING, Direction.SOUTH));
        // a lab bench in the middle
        for (int dx = -3; dx <= 3; dx++) set(l, b.offset(dx, 0, -2), Blocks.SMOOTH_STONE.defaultBlockState());
        set(l, b.offset(-2, 1, -2), Blocks.BREWING_STAND.defaultBlockState()); set(l, b.offset(2, 1, -2), Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState());
        chest(l, r, b.offset(0, 1, -2), Direction.SOUTH);
        // staff
        for (int i = 0; i < 4 + r.nextInt(3); i++) {
            GovAgentEntity g = GovEntities.GOV_AGENT.get().create(l.getLevel());
            if (g == null) continue;
            boolean guard = i > 1;
            BlockPos at = guard ? b.offset(r.nextInt(5) - 2, 0, 2 + r.nextInt(HZ - 3)) : b.offset(r.nextInt(13) - 6, 0, -3 - r.nextInt(2));
            g.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360F, 0);
            g.finalizeSpawn(l, l.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null, null);
            g.setup(guard, r.nextInt(2), b);
            if (com.xenowarriors.world.Clip.ok(at)) l.addFreshEntityWithPassengers(g);
        }
        // an armored truck parked out front, a guard at the wheel (he drives patrols around the facility)
        BlockPos park = b.offset(HX + 5, 0, HZ - 2);
        if (com.xenowarriors.world.Clip.ok(park) && r.nextFloat() < 0.75F) {
            var truck = com.xenowarriors.vehicle.Vehicles.TYPES.get(com.xenowarriors.vehicle.VehicleKind.GOV_TRUCK).get().create(l.getLevel());
            GovAgentEntity driver = GovEntities.GOV_AGENT.get().create(l.getLevel());
            if (truck != null && driver != null) {
                int gy = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, park.getX(), park.getZ());
                truck.moveTo(park.getX() + 0.5, gy, park.getZ() + 0.5, 180F, 0);
                driver.moveTo(park.getX() + 0.5, gy, park.getZ() + 0.5, 180F, 0);
                driver.finalizeSpawn(l, l.getCurrentDifficultyAt(park), MobSpawnType.STRUCTURE, null, null);
                driver.setup(true, r.nextInt(2), b);
                driver.startRiding(truck, true);
                l.addFreshEntityWithPassengers(truck);
            }
        }
    }

    private static void chest(WorldGenLevel l, RandomSource r, BlockPos p, Direction facing) {
        set(l, p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
        if (com.xenowarriors.world.Clip.ok(p) && l.getBlockEntity(p) instanceof RandomizableContainerBlockEntity be) be.setLootTable(LOOT, r.nextLong());
    }
}
