package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Mikiko with the Metal Pole: the last hit of his combo whips out a gust of wind that rushes forward about 12 blocks,
 * shoving everything in its path back and up (a little damage too). With Epic Fight the client says when the final
 * combo hit plays; without it, every third full-strength hit in a row counts as the finisher.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PoleGust {
    private static final int LIFE = 16, COOLDOWN = 14;
    private static final double SPEED = 0.8, RADIUS = 1.4;
    private static final float DAMAGE = 3F;
    private static final List<Gust> GUSTS = new ArrayList<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Map<UUID, int[]> COMBO = new HashMap<>();     // {hits, gameTime of last hit}

    private record Gust(ServerPlayer owner, ServerLevel level, Vec3[] pos, Vec3 dir, int[] age, Set<Integer> hit) {}

    private PoleGust() {}

    public static boolean holding(ServerPlayer p) {
        return ModCapabilities.getType(p) == CharacterType.MIKIKO && p.getMainHandItem().is(ModItems.METAL_POLE.get());
    }

    /** Sends one out (if he's still holding the pole and it isn't too soon after the last). */
    public static void fire(ServerPlayer p) {
        if (!holding(p)) return;
        long now = p.level().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < COOLDOWN) return;
        LAST.put(p.getUUID(), now);
        Vec3 dir = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 start = p.position().add(0, 1.0, 0).add(dir.scale(1.2));
        GUSTS.add(new Gust(p, p.serverLevel(), new Vec3[]{start}, dir, new int[1], new HashSet<>()));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.35F, 1.8F);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || GUSTS.isEmpty()) return;
        for (Iterator<Gust> it = GUSTS.iterator(); it.hasNext(); ) {
            Gust g = it.next();
            if (++g.age()[0] > LIFE || !g.owner().isAlive()) { it.remove(); continue; }
            Vec3 pos = g.pos()[0] = g.pos()[0].add(g.dir().scale(SPEED));
            float grow = 0.6F + g.age()[0] / (float) LIFE;
            g.level().sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 4, 0.35 * grow, 0.3 * grow, 0.35 * grow, 0.02);
            g.level().sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y, pos.z, 1, 0.2, 0.1, 0.2, 0);
            if (g.age()[0] % 3 == 0)
                g.level().sendParticles(ParticleTypes.POOF, pos.x, pos.y - 0.6, pos.z, 2, 0.4, 0.05, 0.4, 0.01);
            double r = RADIUS * grow;
            for (LivingEntity e : g.level().getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(r, 1.2, r))) {
                if (e == g.owner() || !g.hit().add(e.getId())) continue;
                e.hurt(g.owner().damageSources().playerAttack(g.owner()), DAMAGE);
                e.push(g.dir().x * 1.4, 0.45, g.dir().z * 1.4);
                e.hurtMarked = true;
            }
        }
    }

    /** Without Epic Fight: every third full-strength pole hit in quick succession is the finisher. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (ModList.get().isLoaded("epicfight") || !(event.getSource().getEntity() instanceof ServerPlayer p) || !holding(p)) return;
        if (event.getSource().getDirectEntity() != p || p.getAttackStrengthScale(0.5F) < 0.85F) return;
        long now = p.level().getGameTime();
        int[] c = COMBO.computeIfAbsent(p.getUUID(), k -> new int[2]);
        if (now - c[1] > 40) c[0] = 0;
        c[1] = (int) now;
        if (++c[0] >= 3) {
            c[0] = 0;
            fire(p);
        }
    }
}
