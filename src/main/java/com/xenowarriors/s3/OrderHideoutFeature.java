package com.xenowarriors.s3;

import com.mojang.serialization.Codec;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.Seasons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.RegistryObject;

/**
 * An Order of Assassins cell (Season 3): a plain, dark little building with a flyer board out front, a training room,
 * the contract wall (only members can take from it), two bunks, weapons and a supply chest. Two assassins.
 */
public class OrderHideoutFeature extends Feature<NoneFeatureConfiguration> {
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> ORDER_HIDEOUT = com.xenowarriors.world.SkyIslandFeature.FEATURES.register("order_hideout",
            () -> new OrderHideoutFeature(NoneFeatureConfiguration.CODEC));
    private static final ResourceLocation LOOT = XenoWarriors.id("chests/order_hideout");
    static final int HX = 6, HZ = 5, H = 5;

    public OrderHideoutFeature(Codec<NoneFeatureConfiguration> c) { super(c); }

    public static void init() {}

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!(ctx.level().getLevel() instanceof net.minecraft.server.level.ServerLevel sl0) || !com.xenowarriors.recarnet.Recarnet.is(sl0)) return false;   // (Recarnet only: nothing of the show's world is built in the overworld)
        WorldGenLevel level = ctx.level();
        if (level.getServer() == null || !Seasons.unlocked(level.getServer(), 3)) return false;
        BlockPos o = ctx.origin();
        BlockPos c = new BlockPos((o.getX() & ~15) + 8, 0, (o.getZ() & ~15) + 8);
        int lo = 999, hi = -999;
        for (int dx = -HX; dx <= HX; dx += 3) for (int dz = -HZ; dz <= HZ; dz += 3) {
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, c.getX() + dx, c.getZ() + dz);
            lo = Math.min(lo, y); hi = Math.max(hi, y);
        }
        if (hi - lo > 3) return false;
        BlockPos base = new BlockPos(c.getX(), lo, c.getZ());
        if (!level.getBlockState(base.below()).getFluidState().isEmpty()) return false;
        build(level, ctx.random(), base);
        return true;
    }

    private static void set(WorldGenLevel l, BlockPos p, BlockState s) { if (com.xenowarriors.world.Clip.ok(p)) l.setBlock(p, s, 2); }

    public static void build(WorldGenLevel l, RandomSource r, BlockPos b) {
        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), crack = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
                trim = Blocks.POLISHED_BLACKSTONE.defaultBlockState(), floor = Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                air = Blocks.AIR.defaultBlockState(), bars = Blocks.IRON_BARS.defaultBlockState(), carpet = Blocks.BLACK_CARPET.defaultBlockState();
        for (int dx = -HX; dx <= HX; dx++) for (int dz = -HZ; dz <= HZ; dz++) {
            for (int y = -3; y < 0; y++) set(l, b.offset(dx, y, dz), Blocks.STONE.defaultBlockState());
            set(l, b.offset(dx, -1, dz), floor);
            boolean edge = Math.abs(dx) == HX || Math.abs(dz) == HZ, corner = Math.abs(dx) == HX && Math.abs(dz) == HZ;
            for (int y = 0; y < H; y++) {
                BlockState s = air;
                if (edge) s = corner || y == H - 1 ? trim : y == 2 && (dx % 3 == 0 && Math.abs(dz) == HZ || dz % 3 == 0 && Math.abs(dx) == HX) ? bars : r.nextInt(6) == 0 ? crack : wall;
                set(l, b.offset(dx, y, dz), s);
            }
            for (int y = H; y < H + 4; y++) set(l, b.offset(dx, y, dz), air);
            set(l, b.offset(dx, H, dz), edge ? Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState() : Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
            if (!edge && (dx + dz) % 2 == 0 && Math.abs(dx) < 3) set(l, b.offset(dx, 0, dz), carpet);
        }
        // door (south) and a lantern over it
        BlockPos door = b.offset(0, 0, HZ);
        set(l, door, Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(l, door.above(), Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        set(l, door.offset(0, 2, 1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        set(l, door.offset(0, 2, 1).below(), air);
        // flyer board outside: "Order of Assassins - criminals only"
        sign(l, door.offset(2, 1, 1), Direction.SOUTH, "THE ORDER", "of Assassins", "Criminals only.", "Members only.");
        // contract wall (north) - members only
        for (int dx = -3; dx <= 3; dx++) set(l, b.offset(dx, 1, -HZ + 1), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
        sign(l, b.offset(-1, 1, -HZ + 2), Direction.SOUTH, "CONTRACTS", "~ members only ~", "Ask Chase", "or Zack.");
        sign(l, b.offset(1, 1, -HZ + 2), Direction.SOUTH, "TARGET:", "[REDACTED]", "Status:", "OPEN");
        set(l, b.offset(0, 2, -HZ + 1), Blocks.TARGET.defaultBlockState());
        // bunks (west)
        for (int k : new int[]{-2, 1}) bed(l, b.offset(-HX + 1, 0, k), Direction.EAST);
        set(l, b.offset(-HX + 1, 0, 3), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 3));
        // training corner (east): grindstone, anvil, a target, hay "dummy"
        set(l, b.offset(HX - 1, 0, -3), Blocks.GRINDSTONE.defaultBlockState());
        set(l, b.offset(HX - 1, 0, -1), Blocks.ANVIL.defaultBlockState());
        set(l, b.offset(HX - 1, 0, 2), Blocks.HAY_BLOCK.defaultBlockState());
        set(l, b.offset(HX - 1, 1, 2), Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, Direction.WEST));
        set(l, b.offset(HX - 2, 0, 3), Blocks.SMITHING_TABLE.defaultBlockState());
        // table and supplies
        set(l, b.offset(0, 0, 0), Blocks.DARK_OAK_FENCE.defaultBlockState());
        set(l, b.offset(0, 1, 0), Blocks.DARK_OAK_PRESSURE_PLATE.defaultBlockState());
        set(l, b.offset(-1, 0, 0), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        set(l, b.offset(1, 0, 0), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        set(l, b.offset(HX - 1, 0, 0), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        if (com.xenowarriors.world.Clip.ok(b.offset(HX - 1, 0, 0)) && l.getBlockEntity(b.offset(HX - 1, 0, 0)) instanceof ChestBlockEntity ch) ch.setLootTable(LOOT, r.nextLong());
        set(l, b.offset(-HX + 1, 0, -4), Blocks.BARREL.defaultBlockState());
        for (int[] p : new int[][]{{-3, -2}, {3, 2}, {-3, 3}, {3, -3}}) set(l, b.offset(p[0], H - 2, p[1]), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        for (int[] p : new int[][]{{-3, -2}, {3, 2}, {-3, 3}, {3, -3}}) set(l, b.offset(p[0], H - 1, p[1]), Blocks.CHAIN.defaultBlockState());
        // the two on duty
        for (int i = 0; i < 2; i++) {
            OrderAssassinEntity a = S3Entities.ORDER_ASSASSIN.get().create(l.getLevel());
            if (a == null) continue;
            BlockPos at = b.offset(i == 0 ? -2 : 2, 0, 1);
            a.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360F, 0);
            a.finalizeSpawn(l, l.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null, null);
            a.setup(i, at);
            if (com.xenowarriors.world.Clip.ok(at)) l.addFreshEntityWithPassengers(a);
        }
    }

    private static void bed(WorldGenLevel l, BlockPos foot, Direction facing) {
        BlockState bed = Blocks.BLACK_BED.defaultBlockState().setValue(BedBlock.FACING, facing);
        set(l, foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
        set(l, foot.relative(facing), bed.setValue(BedBlock.PART, BedPart.HEAD));
    }

    private static void sign(WorldGenLevel l, BlockPos p, Direction face, String... lines) {
        set(l, p, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, face));
        if (com.xenowarriors.world.Clip.ok(p) && l.getBlockEntity(p) instanceof SignBlockEntity s) {
            SignText t = new SignText();
            for (int i = 0; i < Math.min(4, lines.length); i++) t = t.setMessage(i, Component.literal(lines[i]));
            final SignText text = t;                                       // load() rather than setText(): no block update during worldgen
            SignText.DIRECT_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, text).result().ifPresent(tag -> {
                net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
                nbt.put("front_text", tag);
                s.load(nbt);
            });
        }
    }
}
