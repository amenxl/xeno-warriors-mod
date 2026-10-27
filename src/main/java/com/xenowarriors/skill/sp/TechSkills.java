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

/** Technica ("Tech"): the inventor - twin Uzis (explosive and gravity rounds), gravity boots, her visor, a wing cloak and a rescue platform. */
public final class TechSkills {
    private TechSkills() {}
    static final DustParticleOptions TRACER = new DustParticleOptions(new Vector3f(1F, 0.55F, 0.8F), 0.6F);
    static final DustParticleOptions GRAV = new DustParticleOptions(new Vector3f(0.55F, 0.3F, 0.9F), 0.8F);

    /** One Uzi shot at a target: a pink tracer and a hit. */
    static void shot(ServerPlayer p, LivingEntity t, float dmg, boolean gravity) {
        ServerLevel l = p.serverLevel();
        Vec3 from = p.getEyePosition().add(p.getLookAngle().scale(0.6)).subtract(0, 0.3, 0);
        SpUtil.line(l, from, t.position().add(0, t.getBbHeight() * 0.6, 0), gravity ? GRAV : TRACER, 10);
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), dmg);
        if (gravity) { Vec3 k = t.position().subtract(p.position()).multiply(1, 0, 1).normalize(); t.setDeltaMovement(t.getDeltaMovement().add(k.scale(0.45)).add(0, 0.08, 0)); t.hurtMarked = true; }
        l.playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.5F, 1.8F + p.getRandom().nextFloat() * 0.2F);
    }

    /** Explosive Rounds: a burst from both Uzis; the last rounds go off. */
    public static class ExplosiveRounds implements Skill {
        @Override public String getId() { return "explosive_rounds"; }
        @Override public int getCooldownTicks() { return 7 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SpUtil.front(p, 24);
            if (t == null) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            for (int i = 0; i < 8; i++) {
                int ii = i;
                ServerScheduler.later(i * 2, () -> {
                    if (!t.isAlive()) return;
                    shot(p, t, 2.2F, false);
                    if (ii >= 6) { p.serverLevel().sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 1, t.getZ(), 1, 0.2, 0.2, 0.2, 0);
                        p.level().playSound(null, t.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4F, 1.6F); t.hurt(p.damageSources().playerAttack(p), 2.5F); }
                });
            }
            return true;
        }
    }

    /** Gravity Rounds: rounds that copy Kyrie's gravity - each one shoves the target back. */
    public static class GravityRounds implements Skill {
        @Override public String getId() { return "gravity_rounds"; }
        @Override public int getCooldownTicks() { return 9 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SpUtil.front(p, 24);
            if (t == null) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            for (int i = 0; i < 6; i++) ServerScheduler.later(i * 3, () -> { if (t.isAlive()) shot(p, t, 2.0F, true); });
            return true;
        }
    }

    /** Gravity Boots: 10 seconds of faster moves, higher jumps, soft landings - and kicks that knock things flying. */
    public static class GravityBoots implements Skill {
        @Override public String getId() { return "gravity_boots"; }
        @Override public int getCooldownTicks() { return 25 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, 200, 2, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
            SpState.BOXING.put(p.getUUID(), SpState.now(p) + 200);           // (her kicks hit harder, like Kyrie's boxing)
            p.serverLevel().sendParticles(GRAV, p.getX(), p.getY() + 0.1, p.getZ(), 30, 0.4, 0.05, 0.4, 0);
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.8F);
            return true;
        }
    }

    /** Visor Scan: her visor tracks movement - everything within 40 blocks glows for 15 seconds; reads what she's looking at. */
    public static class VisorScan implements Skill {
        @Override public String getId() { return "visor_scan"; }
        @Override public int getCooldownTicks() { return 25 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            int n = 0;
            for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(40), e -> e != p)) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0, false, false)); n++;
            }
            LivingEntity t = com.xenowarriors.skill.SkillTargeting.aimed(p, 40, 8);
            String read = t == null ? "" : " | " + t.getName().getString() + ": " + (int) t.getHealth() + "/" + (int) t.getMaxHealth() + " HP";
            p.displayClientMessage(Component.literal("Visor: " + n + " moving things tracked" + read).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1F, 2F);
            return true;
        }
    }

    /** Cloak Device: one of her inventions - she vanishes for 12 seconds. */
    public static class WingCloak implements Skill {
        @Override public String getId() { return "cloak_device"; }
        @Override public int getCooldownTicks() { return 35 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 240, 0, false, false));
            p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.6F);
            return true;
        }
    }

    /** Rescue Platform: her watch calls the platform down - her party gathers to her, healed and floating safely down. */
    public static class RescuePlatform implements Skill {
        @Override public String getId() { return "rescue_platform"; }
        @Override public int getCooldownTicks() { return 60 * 20; }
        @Override public int getKarmaCost() { return 35; }
        @Override public boolean use(ServerPlayer p) {
            int n = 0;
            for (ServerPlayer m : com.xenowarriors.party.Parties.members(p)) {
                if (m.level() != p.level() || m.distanceToSqr(p) > 48 * 48) continue;
                if (m != p) { m.teleportTo(p.getX() + p.getRandom().nextGaussian(), p.getY(), p.getZ() + p.getRandom().nextGaussian()); n++; }
                m.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1));
                m.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
                m.fallDistance = 0;
            }
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 3, p.getZ(), 40, 1.5, 0.1, 1.5, 0.01);
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1F, 1.4F);
            p.displayClientMessage(Component.literal("Rescue platform! (" + n + " teammates pulled in)").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return true;
        }
    }

    /** Ultimate - Tech Barrage: four seconds of non-stop fire from both Uzis into everything in front of her, explosive and gravity rounds mixed. */
    public static class TechBarrage implements Skill {
        @Override public String getId() { return "tech_barrage"; }
        @Override public int getCooldownTicks() { return 90 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            for (int i = 0; i < 40; i++) {
                int ii = i;
                ServerScheduler.later(i * 2, () -> {
                    if (!p.isAlive()) return;
                    Vec3 look = p.getLookAngle(), eye = p.getEyePosition();
                    for (LivingEntity e : SpUtil.around(p, p.position(), 20)) {
                        Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
                        if (to.normalize().dot(look) < 0.6 || (ii + e.getId()) % 3 != 0) continue;
                        shot(p, e, 2.0F, ii % 2 == 0);
                        if (ii % 8 == 0) p.serverLevel().sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
                    }
                });
            }
            p.displayClientMessage(Component.literal("Tech Barrage!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
            return true;
        }
    }
}
