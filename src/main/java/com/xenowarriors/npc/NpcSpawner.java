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
            if (!(p.level() instanceof ServerLevel level) || (level.dimension() != Level.OVERWORLD && !com.xenowarriors.recarnet.Recarnet.is(level)) || p.isSpectator()) continue;
            if (com.xenowarriors.recarnet.Recarnet.inAurora(level, p.blockPosition()) || com.xenowarriors.recarnet.Recarnet.inVillage(level, p.blockPosition())) {     // Aurora City / Pine Cone City: busy streets
                int people = level.getEntitiesOfClass(HumanNpcEntity.class, p.getBoundingBox().inflate(64)).size();
                for (int i = 0; i < 3 && people + i < 14; i++) if (level.random.nextFloat() < 0.7F) spawn(level, ModNpcs.HUMAN.get(), around(p.blockPosition(), level.random, 14, 40), false);
                continue;
            }
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
            // Skylanders flying overhead
            if (r.nextFloat() < 0.2F && level.getEntitiesOfClass(SkylanderEntity.class, p.getBoundingBox().inflate(96, 160, 96)).size() < 3) {
                BlockPos at = around(p.blockPosition(), r, 20, 50);
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ());
                BlockPos sky = new BlockPos(at.getX(), Math.min(level.getMaxBuildHeight() - 10, Math.max(surface + 25, 110 + r.nextInt(50))), at.getZ());
                if (level.isEmptyBlock(sky) && level.isEmptyBlock(sky.above())) {
                    SkylanderEntity sk = ModNpcs.SKYLANDER.get().create(level);
                    if (sk != null) {
                        sk.moveTo(sky.getX() + 0.5, sky.getY(), sky.getZ() + 0.5, r.nextFloat() * 360F, 0F);
                        sk.finalizeSpawn(level, level.getCurrentDifficultyAt(sky), MobSpawnType.NATURAL, null, null);
                        sk.setFlying(true);
                        level.addFreshEntity(sk);
                    }
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
