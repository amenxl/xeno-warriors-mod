package com.xenowarriors.lunar;

import com.xenowarriors.karma.Karma;
import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

/**
 * Moon Fall (Rhalix's super slot in Lunar form): he raises a hand and a moon comes down on the spot he's looking at,
 * like the one that wrecked F3. Two seconds to fall, then a huge silver blast. Doesn't break blocks.
 */
public final class MoonFall {
    public static final int KARMA = 60, FALL_TICKS = 40;
    public static final float DAMAGE = 30F, RADIUS = 7F;

    private MoonFall() {}

    public static boolean use(ServerPlayer p) {
        if (!Karma.has(p, KARMA)) { Karma.notEnough(p, KARMA); return false; }
        LivingEntity target = SkillTargeting.aimed(p, 48, 8);
        Vec3 spot;
        if (target != null) spot = target.position();
        else {
            Vec3 eye = p.getEyePosition(), end = eye.add(p.getViewVector(1F).scale(48));
            var hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            spot = hit.getLocation();
        }
        if (!p.isCreative()) Karma.add(p, -KARMA);
        ServerLevel s = p.serverLevel();
        p.displayClientMessage(Component.literal("MOON FALL!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
        s.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.5F);
        com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.KA_BREAK, FALL_TICKS);
        final Vec3 at = spot;
        for (int t = 0; t <= FALL_TICKS; t += 2) {
            final int tt = t;
            ServerScheduler.later(t, () -> {
                if (!p.isAlive()) return;
                double f = tt / (double) FALL_TICKS;
                double r = 3.5;
                Vec3 c = at.add(0, 26 * (1 - f * f) + r, 0);
                for (int i = 0; i < 70; i++) {                               // a silver ball of moonlight, with a few craters
                    double u = s.random.nextDouble() * 2 - 1, a = s.random.nextDouble() * Math.PI * 2, q = Math.sqrt(1 - u * u);
                    boolean crater = s.random.nextInt(6) == 0;
                    s.sendParticles(new DustParticleOptions(crater ? new org.joml.Vector3f(.55F, .58F, .66F) : LunarForm.SILVER, 2.4F),
                            c.x + q * Math.cos(a) * r, c.y + u * r, c.z + q * Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                if (tt % 6 == 0) s.sendParticles(ParticleTypes.END_ROD, c.x, c.y + r, c.z, 6, 1.5, 0.5, 1.5, 0.02);
                if (tt % 10 == 0) s.playSound(null, c.x, c.y, c.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.6F, 0.5F);
                if (tt == FALL_TICKS) impact(p, s, at);
            });
        }
        return true;
    }

    private static void impact(ServerPlayer p, ServerLevel s, Vec3 at) {
        s.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.5F, 0.6F);
        s.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2F, 0.5F);
        s.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 2, 1, 0.5, 1, 0);
        s.sendParticles(new DustParticleOptions(LunarForm.SILVER, 3F), at.x, at.y + 1, at.z, 200, RADIUS * .6, 1.5, RADIUS * .6, 0);
        s.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 80, 2, 2, 2, 0.4);
        for (LivingEntity e : s.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(RADIUS),
                e -> e != p && e.isAlive() && !(e instanceof TamableAnimal ta && ta.isOwnedBy(p)))) {
            double d = e.position().distanceTo(at);
            if (d > RADIUS) continue;
            float dmg = (float) (DAMAGE * (1 - 0.5 * d / RADIUS));
            e.hurt(p.damageSources().indirectMagic(p, p), dmg);
            Vec3 k = e.position().subtract(at).normalize();
            e.push(k.x * 1.4, 0.6, k.z * 1.4); e.hurtMarked = true;
        }
    }
}
