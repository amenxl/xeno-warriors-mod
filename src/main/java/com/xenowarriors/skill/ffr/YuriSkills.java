package com.xenowarriors.skill.ffr;

import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Yuri Tenjo: the hacker genius. Everything runs off her green holographic laptop. */
public final class YuriSkills {
    private YuriSkills() {}
    static final DustParticleOptions CODE = new DustParticleOptions(new Vector3f(0.25F, 0.95F, 0.35F), 0.8F);

    static List<LivingEntity> around(ServerPlayer p, Vec3 c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r)))
            if (e != p && SkillTargeting.canTarget(p, e) && e.position().distanceTo(c) <= r) out.add(e);
        return out;
    }

    static LivingEntity target(ServerPlayer p, double range) {
        LivingEntity t = SkillTargeting.aimed(p, range, 12);
        if (t == null) p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true);
        return t;
    }

    static void say(ServerPlayer p, String line) {
        Component c = Component.literal(p.getName().getString() + ": ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(line).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        for (ServerPlayer o : p.serverLevel().players()) if (o.distanceToSqr(p) < 40 * 40) o.displayClientMessage(c, true);
    }

    static void typing(ServerPlayer p) {
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, 1.8F);
        p.serverLevel().sendParticles(CODE, p.getX(), p.getY() + 1.1, p.getZ(), 12, 0.4, 0.2, 0.4, 0);
    }

    /** Camera Hack: every camera around is hers - everything within 32 blocks lights up for 12 seconds and stumbles. */
    public static class CameraHack implements Skill {
        @Override public String getId() { return "camera_hack"; }
        @Override public int getCooldownTicks() { return 20 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            typing(p);
            int n = 0;
            for (LivingEntity e : around(p, p.position(), 32)) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 240, 0, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
                n++;
            }
            p.displayClientMessage(Component.literal("Cameras hacked: " + n + " targets on screen").withStyle(ChatFormatting.GREEN), true);
            return true;
        }
    }

    /** Firewall: a green wall of code in front of her for 6 seconds - it deletes projectiles and shoves back anything that walks into it. */
    public static class Firewall implements Skill {
        @Override public String getId() { return "firewall"; }
        @Override public int getCooldownTicks() { return 20 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            Vec3 f = p.getLookAngle().multiply(1, 0, 1).normalize();
            Vec3 side = new Vec3(-f.z, 0, f.x);
            Vec3 c = p.position().add(f.scale(3));
            ServerLevel l = p.serverLevel();
            typing(p);
            for (int i = 0; i < 30; i++) {
                ServerScheduler.later(i * 4, () -> {
                    for (int k = -7; k <= 7; k++) for (int h = 0; h < 4; h++) {
                        Vec3 q = c.add(side.scale(k * 0.5)).add(0, h * 0.8 + l.random.nextDouble() * 0.4, 0);
                        l.sendParticles(CODE, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0);
                    }
                    AABB box = new AABB(c, c).inflate(Math.abs(side.x) * 3.6 + 0.6, 0, Math.abs(side.z) * 3.6 + 0.6).expandTowards(0, 3.2, 0);
                    for (Projectile pr : l.getEntitiesOfClass(Projectile.class, box.inflate(0.5), pr -> pr.getOwner() != p)) {
                        l.sendParticles(ParticleTypes.ELECTRIC_SPARK, pr.getX(), pr.getY(), pr.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
                        pr.discard();
                    }
                    for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && SkillTargeting.canTarget(p, e))) {
                        double s = e.position().subtract(c).dot(f) >= 0 ? 1 : -1;
                        e.setDeltaMovement(f.scale(0.8 * s).add(0, 0.2, 0)); e.hurtMarked = true;
                        e.hurt(p.damageSources().indirectMagic(p, p), 1.5F);
                    }
                });
            }
            l.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
            return true;
        }
    }

    /** Elevator Hack: the same trick that launched Gale's rooms - the ground under her target shoots up and drops them. */
    public static class ElevatorHack implements Skill {
        @Override public String getId() { return "elevator_hack"; }
        @Override public int getCooldownTicks() { return 12 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = target(p, 24);
            if (t == null) return false;
            typing(p);
            ServerLevel l = p.serverLevel();
            l.sendParticles(CODE, t.getX(), t.getY() + 0.1, t.getZ(), 30, 0.6, 0.05, 0.6, 0);
            ServerScheduler.later(8, () -> {
                if (!t.isAlive()) return;
                t.hurt(p.damageSources().indirectMagic(p, p), 4F);
                t.setDeltaMovement(0, 1.5, 0); t.hurtMarked = true;
                l.sendParticles(ParticleTypes.CLOUD, t.getX(), t.getY(), t.getZ(), 20, 0.5, 0.1, 0.5, 0.05);
                l.playSound(null, t.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1F, 0.6F);
            });
            return true;
        }
    }

    /** The Tenjo Medical Technique: "God loves us... NOW WAKE UP OR ELSE WE'RE ALL GONNA DIE!" - heals her party and jolts them back up. */
    public static class TenjoMedical implements Skill {
        @Override public String getId() { return "tenjo_medical"; }
        @Override public int getCooldownTicks() { return 45 * 20; }
        @Override public int getKarmaCost() { return 30; }
        @Override public boolean use(ServerPlayer p) {
            say(p, "God loves us... NOW WAKE UP OR ELSE WE'RE ALL GONNA DIE!");
            for (ServerPlayer m : com.xenowarriors.party.Parties.members(p)) {
                if (m.level() != p.level() || m.distanceToSqr(p) > 14 * 14) continue;
                m.heal(8F);
                m.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1));
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0));
                m.removeEffect(MobEffects.MOVEMENT_SLOWDOWN); m.removeEffect(MobEffects.CONFUSION); m.removeEffect(MobEffects.BLINDNESS);
                p.serverLevel().sendParticles(ParticleTypes.HEART, m.getX(), m.getY() + 2, m.getZ(), 4, 0.3, 0.2, 0.3, 0);
            }
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1F, 1.6F);
            return true;
        }
    }

    /** Virus Upload: she hacks her target - it glows, weakens, and the virus eats at it every second for 8 seconds. */
    public static class VirusUpload implements Skill {
        @Override public String getId() { return "virus_upload"; }
        @Override public int getCooldownTicks() { return 18 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = target(p, 24);
            if (t == null) return false;
            typing(p);
            t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0, false, false));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
            for (int i = 1; i <= 8; i++) ServerScheduler.later(i * 20, () -> {
                if (!t.isAlive()) return;
                t.invulnerableTime = 0;
                t.hurt(p.damageSources().indirectMagic(p, p), 1.5F);
                p.serverLevel().sendParticles(CODE, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 10, 0.3, 0.4, 0.3, 0);
            });
            return true;
        }
    }

    /** Ultimate - "Computer: Open sky ground!": the ground under every enemy near her opens to the sky - they float, helpless, then slam down. */
    public static class OpenSkyGround implements Skill {
        @Override public String getId() { return "open_sky_ground"; }
        @Override public int getCooldownTicks() { return 90 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            say(p, "Computer: Open sky ground!");
            ServerLevel l = p.serverLevel();
            List<LivingEntity> hit = around(p, p.position(), 16);
            for (LivingEntity e : hit) {
                e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 50, 2, false, false));
                l.sendParticles(CODE, e.getX(), e.getY(), e.getZ(), 30, 0.6, 0.1, 0.6, 0);
            }
            for (ServerPlayer m : com.xenowarriors.party.Parties.members(p))
                if (m.level() == l && m.distanceToSqr(p) < 16 * 16) m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 1));
            ServerScheduler.later(55, () -> {
                for (LivingEntity e : hit) {
                    if (!e.isAlive()) continue;
                    e.removeEffect(MobEffects.LEVITATION);
                    e.setDeltaMovement(0, -2.5, 0); e.hurtMarked = true;
                    e.invulnerableTime = 0;
                    e.hurt(p.damageSources().indirectMagic(p, p), 10F);
                    l.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY(), e.getZ(), 1, 0, 0, 0, 0);
                }
                l.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.2F);
            });
            l.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1F, 0.6F);
            return true;
        }
    }
}
