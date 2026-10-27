package com.xenowarriors.skill.sp;

import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Max (Sintrie): dagger work, the Wind element (Wind of Sorrow), and the Lunar path. His KA, Vampire Cut, is in VampireCut. */
public final class MaxSkills {
    private MaxSkills() {}
    static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.7F, 0.03F, 0.06F), 1.0F);

    static boolean night(ServerPlayer p) { long t = p.level().getDayTime() % 24000; return t > 13000 && t < 23000; }

    /** Dagger Flurry: five quick stabs on one target. */
    public static class DaggerFlurry implements Skill {
        @Override public String getId() { return "dagger_flurry"; }
        @Override public int getCooldownTicks() { return 6 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SpUtil.front(p, 4.5);
            if (t == null) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            for (int i = 0; i < 5; i++) {
                ServerScheduler.later(i * 4, () -> {
                    if (!t.isAlive() || t.distanceTo(p) > 6) return;
                    t.invulnerableTime = 0;
                    t.hurt(p.damageSources().playerAttack(p), 3.2F);
                    p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                    p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getY() + 1, t.getZ(), 1, 0.2, 0.2, 0.2, 0);
                    p.level().playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 1.6F);
                });
            }
            return true;
        }
    }

    /** Wind of Sorrow: rides the wind in a burst, shoving aside everything in the way. */
    public static class WindOfSorrow implements Skill {
        @Override public String getId() { return "wind_of_sorrow"; }
        @Override public int getCooldownTicks() { return 8 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            Vec3 look = p.getLookAngle();
            p.level().playSound(null, p.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.6F, 1.6F);
            SpUtil.dash(p, 1.7, 7, ParticleTypes.CLOUD, e -> {
                e.hurt(p.damageSources().playerAttack(p), 5F);
                e.knockback(1.6, -look.z, look.x);
            });
            return true;
        }
    }

    /** Wind Wall: a wall of wind spins round him for 6 seconds and blows arrows and blasts straight back. */
    public static class WindWall implements Skill {
        @Override public String getId() { return "wind_wall"; }
        @Override public int getCooldownTicks() { return 18 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            SpState.WIND_WALL.put(p.getUUID(), SpState.now(p) + 120);
            for (LivingEntity e : SpUtil.around(p, p.position(), 4)) {
                Vec3 out = e.position().subtract(p.position()).normalize();
                e.setDeltaMovement(out.scale(1.2).add(0, 0.3, 0)); e.hurtMarked = true;
            }
            p.level().playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1F, 1.3F);
            return true;
        }
    }

    /** Lunar Mind: the Lunar path - at night he sinks into his mind state (strong, fast, healing); by day only a little. */
    public static class LunarMind implements Skill {
        @Override public String getId() { return "lunar_mind"; }
        @Override public int getCooldownTicks() { return 45 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            boolean n = night(p);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, n ? 1 : 0));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, n ? 1 : 0));
            if (n) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.02);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 0.7F);
            p.displayClientMessage(Component.literal(n ? "Lunar mind state - the moon is with him" : "Lunar mind state (weaker by day)").withStyle(ChatFormatting.AQUA), true);
            return true;
        }
    }

    /** Rapid Vampire Slice: a storm of slashes that drink the target's blood back into him. */
    public static class RapidVampireSlice implements Skill {
        @Override public String getId() { return "rapid_vampire_slice"; }
        @Override public int getCooldownTicks() { return 20 * 20; }
        @Override public int getKarmaCost() { return 35; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SpUtil.front(p, 5);
            if (t == null) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            for (int i = 0; i < 8; i++) {
                ServerScheduler.later(i * 3, () -> {
                    if (!t.isAlive() || t.distanceTo(p) > 7) return;
                    t.invulnerableTime = 0;
                    if (t.hurt(p.damageSources().playerAttack(p), 2.5F)) p.heal(1.0F);
                    p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                    p.serverLevel().sendParticles(BLOOD, t.getX(), t.getY() + 1, t.getZ(), 8, 0.3, 0.4, 0.3, 0);
                    p.level().playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.8F);
                });
            }
            return true;
        }
    }

    /** Ultimate - Moonlit Gale: a cyclone of wind blades whirls round him for 4 seconds (stronger at night). */
    public static class MoonlitGale implements Skill {
        @Override public String getId() { return "moonlit_gale"; }
        @Override public int getCooldownTicks() { return 90 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            ServerLevel l = p.serverLevel();
            float dmg = night(p) ? 5F : 3.5F;
            for (int i = 0; i < 16; i++) {
                int ii = i;
                ServerScheduler.later(i * 5, () -> {
                    if (!p.isAlive()) return;
                    for (int k = 0; k < 12; k++) {
                        double a = ii * 0.8 + k * Math.PI / 6, r = 2 + (k % 3);
                        l.sendParticles(k % 2 == 0 ? ParticleTypes.SWEEP_ATTACK : ParticleTypes.CLOUD, p.getX() + Math.cos(a) * r, p.getY() + 0.5 + (k % 4) * 0.5, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                    }
                    for (LivingEntity e : SpUtil.around(p, p.position(), 5.5)) {
                        e.invulnerableTime = 0;
                        e.hurt(p.damageSources().playerAttack(p), dmg);
                        Vec3 tan = new Vec3(-(e.getZ() - p.getZ()), 0, e.getX() - p.getX()).normalize().scale(0.4);
                        e.setDeltaMovement(tan.add(0, 0.15, 0)); e.hurtMarked = true;
                    }
                    if (ii % 4 == 0) l.playSound(null, p.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.8F, 1.2F);
                });
            }
            return true;
        }
    }
}
