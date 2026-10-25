package com.xenowarriors.npc;

import com.xenowarriors.XenoWarriors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Townsfolk react to what's going on around them: they duck and cover from explosions and lightning, stare in awe at
 * Mikiko's and Rhalix's skills, dance when music is playing, shield their heads in the rain, wave at players who look
 * at them, put their hands up when someone walks up waving a weapon, and cheer when a raid is beaten.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HumanReactions {
    public static final int COWER = 1, AWE = 2, DANCE = 3, RAIN = 4, WAVE = 5, NERVOUS = 6, CHEER = 7;

    private HumanReactions() {}

    static int priority(int r) {
        return switch (r) {
            case COWER -> 5;
            case CHEER, NERVOUS -> 4;
            case AWE -> 3;
            case DANCE -> 2;
            default -> 1;
        };
    }

    private static Iterable<HumanNpcEntity> near(Level level, Vec3 at, double r) {
        return level.getEntitiesOfClass(HumanNpcEntity.class, new AABB(at, at).inflate(r), h -> h.isAlive() && !h.carriesSoul() && !h.raider());
    }

    /** Big things happening nearby. */
    public static void everyone(Level level, Vec3 at, double r, int type, int ticks) {
        for (HumanNpcEntity h : near(level, at, r)) {
            h.react(type, ticks);
            if (type == COWER && !h.fighter()) {                 // and get away from it
                Vec3 away = h.position().subtract(at).normalize().scale(8);
                h.getNavigation().moveTo(h.getX() + away.x, h.getY(), h.getZ() + away.z, 1.35);
            }
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!event.getLevel().isClientSide) everyone(event.getLevel(), event.getExplosion().getPosition(), 18, COWER, 70);
    }

    @SubscribeEvent
    public static void onLightning(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LightningBolt bolt) everyone(event.getLevel(), bolt.position(), 24, COWER, 50);
    }

    /** Someone used a skill (Mikiko's / Rhalix's): the people watching are amazed. */
    public static void skillUsed(Player p) {
        everyone(p.level(), p.position(), 14, AWE, 50);
    }

    /** Their own little checks, once a second. */
    static void look(HumanNpcEntity h) {
        if (h.carriesSoul() || h.raider() || h.getTarget() != null) return;
        Level level = h.level();
        BlockPos pos = h.blockPosition();
        // music playing nearby: dance
        if (musicNear(level, pos)) { h.react(DANCE, 40); return; }
        for (Player p : level.getEntitiesOfClass(Player.class, h.getBoundingBox().inflate(6), p -> !p.isSpectator())) {
            double d = p.distanceTo(h);
            boolean armed = p.getMainHandItem().getItem() instanceof SwordItem || p.getMainHandItem().getItem() instanceof AxeItem;
            if (armed && d < 3.5 && !h.fighter() && !p.isCrouching()) { h.react(NERVOUS, 40); h.getLookControl().setLookAt(p); return; }
            // looked at: a wave hello now and then
            Vec3 look = p.getViewVector(1F), to = h.getEyePosition().subtract(p.getEyePosition()).normalize();
            if (d < 6 && look.dot(to) > 0.95 && h.getRandom().nextInt(6) == 0) {
                h.react(WAVE, 30);
                h.getLookControl().setLookAt(p);
                h.getNavigation().stop();
                return;
            }
        }
        if (level.isRainingAt(pos.above()) && h.getRandom().nextInt(3) == 0) h.react(RAIN, 40);
    }

    private static boolean musicNear(Level level, BlockPos pos) {
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) {
            var chunk = level.getChunkSource().getChunkNow((pos.getX() >> 4) + cx, (pos.getZ() >> 4) + cz);
            if (chunk == null) continue;
            for (var be : chunk.getBlockEntities().values())
                if (be instanceof JukeboxBlockEntity j && j.isRecordPlaying() && be.getBlockPos().closerThan(pos, 14)) return true;
        }
        return false;
    }
}
