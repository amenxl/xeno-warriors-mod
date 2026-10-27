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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Stevie and Garry, the Disco Dojo brothers: karma blasts, the war cry, good karma, dance moves and freestyle bars. */
public final class DojoSkills {
    private DojoSkills() {}

    static void blast(ServerPlayer p, LivingEntity t, float dmg, int rgb) {
        ServerLevel l = p.serverLevel();
        DustParticleOptions c = new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F), 1.6F);
        SpUtil.line(l, p.getEyePosition().subtract(0, 0.3, 0), t.position().add(0, t.getBbHeight() / 2, 0), c, 20);
        t.hurt(p.damageSources().playerAttack(p), dmg);
        Vec3 k = t.position().subtract(p.position()).multiply(1, 0, 1).normalize();
        t.setDeltaMovement(k.scale(0.9).add(0, 0.3, 0)); t.hurtMarked = true;
        l.sendParticles(ParticleTypes.FLASH, t.getX(), t.getY() + 1, t.getZ(), 1, 0, 0, 0, 0);
        l.playSound(null, p.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1F, 0.8F);
    }

    /** Karma Blast: a blast of raw karma at what they're looking at. */
    public static class KarmaBlast implements Skill {
        private final int rgb;
        public KarmaBlast(int rgb) { this.rgb = rgb; }
        @Override public String getId() { return "karma_blast"; }
        @Override public int getCooldownTicks() { return 6 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SpUtil.front(p, 22);
            if (t == null) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            blast(p, t, 8F, rgb);
            return true;
        }
    }

    /** War Cry: "RARRRR!!!" - stronger and tougher for 12 seconds, and nearby enemies flinch. */
    public static class WarCry implements Skill {
        @Override public String getId() { return "war_cry"; }
        @Override public int getCooldownTicks() { return 30 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 240, 0));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 240, 0));
            for (LivingEntity e : SpUtil.around(p, p.position(), 6)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            p.level().playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8F, 1.4F);
            for (ServerPlayer o : p.serverLevel().players()) if (o.distanceToSqr(p) < 32 * 32)
                o.displayClientMessage(Component.literal(p.getName().getString() + ": RARRRR!!!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            return true;
        }
    }

    /** Good Karma: a strike so full of good karma it knocks them out of whatever form they're in - their buffs are gone. */
    public static class GoodKarma implements Skill {
        @Override public String getId() { return "good_karma"; }
        @Override public int getCooldownTicks() { return 25 * 20; }
        @Override public int getKarmaCost() { return 30; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SpUtil.front(p, 4.5);
            if (t == null) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            int n = 0;
            for (MobEffectInstance e : new java.util.ArrayList<>(t.getActiveEffects()))
                if (e.getEffect().isBeneficial()) { t.removeEffect(e.getEffect()); n++; }
            t.hurt(p.damageSources().playerAttack(p), 6F);
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, t.getX(), t.getY() + 1, t.getZ(), 25, 0.3, 0.6, 0.3, 0.08);
            p.level().playSound(null, t.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1F, 1.4F);
            p.displayClientMessage(Component.literal("Good karma! (" + n + " effects knocked off)").withStyle(ChatFormatting.AQUA), true);
            return true;
        }
    }

    /** Disco Kick: a spinning dance kick that hits everything around. */
    public static class DiscoKick implements Skill {
        @Override public String getId() { return "disco_kick"; }
        @Override public int getCooldownTicks() { return 8 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            for (LivingEntity e : SpUtil.around(p, p.position(), 3.5)) {
                e.hurt(p.damageSources().playerAttack(p), 6F);
                Vec3 out = e.position().subtract(p.position()).multiply(1, 0, 1).normalize();
                e.setDeltaMovement(out.scale(1.0).add(0, 0.35, 0)); e.hurtMarked = true;
            }
            ServerLevel l = p.serverLevel();
            for (int i = 0; i < 16; i++) { double a = i * Math.PI / 8;
                l.sendParticles(ParticleTypes.NOTE, p.getX() + Math.cos(a) * 2.5, p.getY() + 0.6, p.getZ() + Math.sin(a) * 2.5, 1, 0, 0, 0, i / 16.0); }
            l.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1F, 1.2F);
            return true;
        }
    }

    /** Body Slam (Garry): jumps on the target and slams them into the ground. */
    public static class BodySlam implements Skill {
        @Override public String getId() { return "body_slam"; }
        @Override public int getCooldownTicks() { return 10 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SpUtil.front(p, 6);
            if (t == null) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            p.teleportTo(t.getX(), t.getY() + 1.5, t.getZ());
            ServerScheduler.later(4, () -> { if (!t.isAlive()) return;
                t.hurt(p.damageSources().playerAttack(p), 10F);
                t.setDeltaMovement(0, -1.2, 0); t.hurtMarked = true; p.fallDistance = 0;
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                p.serverLevel().sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY(), t.getZ(), 1, 0, 0, 0, 0);
                p.level().playSound(null, t.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 1.2F); });
            return true;
        }
    }

    /** Freestyle Bars: drops some bars - his party nearby gets faster and karma comes back. */
    public static class FreestyleBars implements Skill {
        @Override public String getId() { return "freestyle_bars"; }
        @Override public int getCooldownTicks() { return 40 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            for (ServerPlayer m : com.xenowarriors.party.Parties.members(p)) {
                if (m.distanceToSqr(p) > 16 * 16) continue;
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 1));
                com.xenowarriors.karma.Karma.add(m, 15F);
            }
            p.serverLevel().sendParticles(ParticleTypes.NOTE, p.getX(), p.getY() + 2.2, p.getZ(), 12, 0.6, 0.3, 0.6, 1);
            p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1F, 1.3F);
            p.displayClientMessage(Component.literal("Freestyle bars! The crew's hyped").withStyle(ChatFormatting.GREEN), true);
            return true;
        }
    }

    /** Bar Thief (Stevie): steals his brother's bars - copies a random good effect from someone nearby onto himself. */
    public static class BarThief implements Skill {
        @Override public String getId() { return "bar_thief"; }
        @Override public int getCooldownTicks() { return 30 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(12), x -> x != p))
                for (MobEffectInstance ef : e.getActiveEffects()) if (ef.getEffect().isBeneficial()) {
                    p.addEffect(new MobEffectInstance(ef.getEffect(), Math.min(400, ef.getDuration()), ef.getAmplifier()));
                    p.displayClientMessage(Component.literal("Stole " + e.getName().getString() + "'s " + ef.getEffect().getDisplayName().getString() + "!").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                    p.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1F, 0.8F);
                    return true;
                }
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
            p.displayClientMessage(Component.literal("Nothing to steal... he steals a head start instead").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
    }

    /** Ultimate - Brother Blast: a huge karma blast with his brother's name on it (twice as strong with the other brother nearby). */
    public static class BrotherBlast implements Skill {
        private final int rgb;
        public BrotherBlast(int rgb) { this.rgb = rgb; }
        @Override public String getId() { return "brother_blast"; }
        @Override public int getCooldownTicks() { return 90 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            boolean bro = com.xenowarriors.party.Synergy.has(p, com.xenowarriors.party.Synergy.BROTHERS);
            Vec3 look = p.getLookAngle(), eye = p.getEyePosition();
            DustParticleOptions c = new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F), 2.6F);
            ServerLevel l = p.serverLevel();
            for (int i = 0; i < 40; i++) { Vec3 q = eye.add(look.scale(i * 0.6)); l.sendParticles(c, q.x, q.y, q.z, 3, 0.3, 0.3, 0.3, 0); }
            for (LivingEntity e : SpUtil.around(p, p.position(), 24)) {
                Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
                if (to.normalize().dot(look) < 0.94) continue;
                e.hurt(p.damageSources().playerAttack(p), bro ? 30F : 18F);
                e.setDeltaMovement(look.scale(2.0).add(0, 0.5, 0)); e.hurtMarked = true;
            }
            l.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.7F);
            p.displayClientMessage(Component.literal(bro ? "BROTHER BLAST!! (together!)" : "Brother Blast!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            return true;
        }
    }
}
