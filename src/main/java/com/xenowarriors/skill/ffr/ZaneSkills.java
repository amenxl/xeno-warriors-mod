package com.xenowarriors.skill.ffr;

import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Zane the Magical Mage: magic, not karma elements - Fire, Ice and Thunder spells from his Magical Orb (which holds his mana
 * and the spells of past mages), and his broom staff. His mana uses the Karma bar.
 */
public final class ZaneSkills {
    private ZaneSkills() {}
    static final DustParticleOptions MANA = new DustParticleOptions(new Vector3f(0.7F, 0.4F, 1F), 1F);

    static void line(ServerLevel l, Vec3 a, Vec3 b, ParticleOptions o, int n) {
        for (int i = 0; i <= n; i++) { Vec3 q = a.lerp(b, i / (double) n); l.sendParticles(o, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0); }
    }

    static void cast(ServerPlayer p) {
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        p.serverLevel().sendParticles(MANA, p.getX(), p.getY() + 1.2, p.getZ(), 10, 0.3, 0.3, 0.3, 0);
    }

    static void fire(ServerPlayer p, LivingEntity t, float dmg) {
        ServerLevel l = p.serverLevel();
        Vec3 from = p.getEyePosition().add(p.getLookAngle().scale(0.8)).subtract(0, 0.3, 0);
        line(l, from, t.position().add(0, t.getBbHeight() / 2, 0), ParticleTypes.FLAME, 18);
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().indirectMagic(p, p), dmg);
        t.setSecondsOnFire(4);
        l.playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    static void ice(ServerPlayer p, LivingEntity e, float dmg) {
        e.invulnerableTime = 0;
        e.hurt(p.damageSources().indirectMagic(p, p), dmg);
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
        e.setTicksFrozen(Math.max(e.getTicksFrozen(), 180));
        p.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + 1, e.getZ(), 20, 0.4, 0.5, 0.4, 0.02);
    }

    static void thunder(ServerPlayer p, Vec3 at, float dmg, double r) {
        ServerLevel l = p.serverLevel();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
        if (bolt != null) { bolt.moveTo(at); bolt.setVisualOnly(true); l.addFreshEntity(bolt); }
        for (LivingEntity e : YuriSkills.around(p, at, r)) { e.invulnerableTime = 0; e.hurt(p.damageSources().indirectMagic(p, p), dmg); }
        l.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 30, r / 2, 0.5, r / 2, 0.2);
    }

    /** Fire Spell: a stream of fire from his orb into his target. */
    public static class FireSpell implements Skill {
        @Override public String getId() { return "fire_spell"; }
        @Override public int getCooldownTicks() { return 6 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public int clashColor() { return 0xFF6A1A; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = YuriSkills.target(p, 24);
            if (t == null) return false;
            cast(p);
            for (int i = 0; i < 3; i++) ServerScheduler.later(i * 4, () -> { if (t.isAlive()) fire(p, t, 2.5F); });
            return true;
        }
    }

    /** Ice Spell: a burst of frost around him - everything close freezes and slows. */
    public static class IceSpell implements Skill {
        @Override public String getId() { return "ice_spell"; }
        @Override public int getCooldownTicks() { return 10 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            cast(p);
            for (LivingEntity e : YuriSkills.around(p, p.position(), 6)) ice(p, e, 4F);
            p.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 90, 3, 1, 3, 0.05);
            p.level().playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1F, 1.6F);
            return true;
        }
    }

    /** Thunder Spell: calls lightning down where he points. */
    public static class ThunderSpell implements Skill {
        @Override public String getId() { return "thunder_spell"; }
        @Override public int getCooldownTicks() { return 12 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            cast(p);
            LivingEntity t = SkillTargeting.aimed(p, 30, 10);
            Vec3 at;
            if (t != null) at = t.position();
            else { Vec3 eye = p.getEyePosition(); at = p.serverLevel().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(30)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getLocation(); }
            thunder(p, at, 8F, 2.5);
            return true;
        }
    }

    /** Broom Smack: a big swing of his broom staff - knocks everything in front of him away. */
    public static class BroomSmack implements Skill {
        @Override public String getId() { return "broom_smack"; }
        @Override public int getCooldownTicks() { return 5 * 20; }
        @Override public int getKarmaCost() { return 10; }
        @Override public boolean use(ServerPlayer p) {
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            Vec3 f = p.getLookAngle().multiply(1, 0, 1).normalize();
            for (LivingEntity e : YuriSkills.around(p, p.position().add(f.scale(2)), 2.6)) {
                e.hurt(p.damageSources().playerAttack(p), 5F);
                e.setDeltaMovement(f.scale(1.3).add(0, 0.45, 0)); e.hurtMarked = true;
            }
            p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + f.x * 2, p.getY() + 1, p.getZ() + f.z * 2, 1, 0, 0, 0, 0);
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1F, 0.8F);
            return true;
        }
    }

    /** Mana Barrier: a shell of stored mana soaks up the next hits for 10 seconds. */
    public static class ManaBarrier implements Skill {
        @Override public String getId() { return "mana_barrier"; }
        @Override public int getCooldownTicks() { return 30 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 2));
            p.serverLevel().sendParticles(MANA, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 0.9, 0.6, 0);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1F, 1.2F);
            return true;
        }
    }

    /** Ultimate - Spells of the Past Mages: the orb lets loose every spell it holds - fire, ice and thunder rain on everything around him. */
    public static class PastMages implements Skill {
        @Override public String getId() { return "past_mages"; }
        @Override public int getCooldownTicks() { return 90 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            Component c = Component.literal(p.getName().getString() + ": ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("ZANE THE MAGICAL MAGE!!!!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            for (ServerPlayer o : p.serverLevel().players()) if (o.distanceToSqr(p) < 40 * 40) o.displayClientMessage(c, true);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1, false, false));
            for (int i = 0; i < 10; i++) {
                int ii = i;
                ServerScheduler.later(i * 10, () -> {
                    if (!p.isAlive()) return;
                    p.serverLevel().sendParticles(MANA, p.getX(), p.getY() + 2.2, p.getZ(), 20, 0.5, 0.3, 0.5, 0);
                    for (LivingEntity e : YuriSkills.around(p, p.position(), 14)) {
                        switch (ii % 3) {
                            case 0 -> fire(p, e, 3F);
                            case 1 -> ice(p, e, 3F);
                            default -> { if ((e.getId() + ii) % 2 == 0) thunder(p, e.position(), 4F, 1.5); }
                        }
                    }
                });
            }
            return true;
        }
    }
}
