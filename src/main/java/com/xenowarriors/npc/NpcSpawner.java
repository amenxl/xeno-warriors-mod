package com.xenowarriors.npc;

import com.xenowarriors.XenoWarriors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * People turn up around players now and then: mostly in villages (by the villagers), sometimes out walking on their
 * own. At night the odd permanently possessed one prowls. Kept sparse - a few around at most.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcSpawner {
    private static final int EVERY = 400, MAX_NEAR = 4, MAX_DARK_NEAR = 1;

    private NpcSpawner() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % EVERY != 0) return;
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || p.isSpectator()) continue;
            AABB near = p.getBoundingBox().inflate(64);
            var r = level.random;
            int humans = level.getEntitiesOfClass(HumanNpcEntity.class, near).size();
            if (humans < MAX_NEAR) {
                var villagers = level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(48));
                if (!villagers.isEmpty() && r.nextFloat() < 0.6F) {
                    Villager v = villagers.get(r.nextInt(villagers.size()));
                    spawn(level, ModNpcs.HUMAN.get(), v.blockPosition().offset(r.nextInt(9) - 4, 0, r.nextInt(9) - 4), true);
                } else if (r.nextFloat() < 0.12F && level.isDay()) {
                    spawn(level, ModNpcs.HUMAN.get(), around(p.blockPosition(), r, 24, 40), false);
                }
            }
            if (level.isNight() && r.nextFloat() < 0.03F && level.getEntitiesOfClass(DarkPossessedEntity.class, near).size() < MAX_DARK_NEAR) {
                spawn(level, ModNpcs.DARK_POSSESSED.get(), around(p.blockPosition(), r, 30, 50), false);
            }
        }
    }

    private static BlockPos around(BlockPos c, net.minecraft.util.RandomSource r, int min, int max) {
        double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
        return c.offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d));
    }

    private static <T extends net.minecraft.world.entity.Mob> void spawn(ServerLevel level, net.minecraft.world.entity.EntityType<T> type, BlockPos at, boolean persistent) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        if (!level.getBlockState(top.below()).isSolidRender(level, top.below()) || !level.isEmptyBlock(top) || !level.isEmptyBlock(top.above())) return;
        T e = type.create(level);
        if (e == null) return;
        e.moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        e.finalizeSpawn(level, level.getCurrentDifficultyAt(top), MobSpawnType.NATURAL, null, null);
        if (persistent) e.setPersistenceRequired();
        level.addFreshEntity(e);
    }
}
