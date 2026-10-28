package com.xenowarriors.you;

import com.xenowarriors.ka.Element;
import com.xenowarriors.ka.Elements;
import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillEvents;
import com.xenowarriors.skill.SkillTargeting;
import com.xenowarriors.skill.sp.SpUtil;
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

import java.util.List;

/** The skills You characters learn: the starter kit everyone gets, their element's skills, and their race's. */
public final class YouSkillSet {
    private YouSkillSet() {}

    static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F), size);
    }

    static boolean noTarget(ServerPlayer p) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }

    // ------------------------------------------------------------------ starter kit

    /** Karma Strike: a short dash that cuts through whoever's in the way. */
    public static final class KarmaStrike implements Skill {
        @Override public String getId() { return "you_strike"; }
        @Override public int getCooldownTicks() { return 7 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            int rgb = You.color(p) & 0xFFFFFF;
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1F, 1.1F);
            SpUtil.dash(p, 1.3, 6, dust(rgb, 1.2F), e -> {
                e.hurt(p.damageSources().playerAttack(p), 7F);
                e.setDeltaMovement(p.getLookAngle().scale(0.8).add(0, 0.3, 0)); e.hurtMarked = true;
            });
            return true;
        }
    }

    /** Focus: steady yourself - cleanse, then regenerate for 8 seconds. */
    public static final class Focus implements Skill {
        @Override public String getId() { return "you_focus"; }
        @Override public int getCooldownTicks() { return 35 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            p.removeEffect(MobEffects.POISON); p.removeEffect(MobEffects.WITHER); p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN); p.removeEffect(MobEffects.WEAKNESS);
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1));
            p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 18, 0.5, 0.6, 0.5, 0.02);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.2F);
            return true;
        }
    }

    /** Burst Step: a quick sidestep in the direction you're moving - untouchable for a moment. */
    public static final class BurstStep implements Skill {
        @Override public String getId() { return "you_step"; }
        @Override public int getCooldownTicks() { return 8 * 20; }
        @Override public int getKarmaCost() { return 10; }
        @Override public boolean use(ServerPlayer p) {
            Vec3 v = p.getDeltaMovement().multiply(1, 0, 1);
            Vec3 dir = v.lengthSqr() < 0.002 ? p.getLookAngle().multiply(-1, 0, -1).normalize() : v.normalize();
            p.setDeltaMovement(dir.scale(1.4).add(0, 0.25, 0)); p.hurtMarked = true;
            p.getPersistentData().putLong(SkillEvents.ULT_UNTIL, p.level().getGameTime() + 14);
            p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 10, 0.3, 0.05, 0.3, 0.05);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5F, 1.9F);
            return true;
        }
    }

    // ------------------------------------------------------------------ element skills

    /** Bolt: your element, cast at what you're looking at. */
    public static final class Bolt implements Skill {
        private final Element el;
        public Bolt(Element el) { this.el = el; }
        @Override public String getId() { return "you_bolt"; }
        @Override public Component getName() { return Component.literal(el.title() + " Bolt"); }
        @Override public int getCooldownTicks() { return 7 * 20; }
        @Override public int getKarmaCost() { return el.karma; }
        @Override public int clashColor() { return Elements.blast(el) ? el.color : -1; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SkillTargeting.aimed(p, 20, 12);
            if (t == null) return noTarget(p);
            Elements.castFrom(p, t, el);
            return true;
        }
    }

    /** Wave: your element at everyone around you. */
    public static final class Wave implements Skill {
        private final Element el;
        public Wave(Element el) { this.el = el; }
        @Override public String getId() { return "you_wave"; }
        @Override public Component getName() { return Component.literal(el.title() + " Wave"); }
        @Override public int getCooldownTicks() { return 24 * 20; }
        @Override public int getKarmaCost() { return el.karma * 2; }
        @Override public boolean use(ServerPlayer p) {
            List<LivingEntity> around = SpUtil.around(p, p.position(), 6);
            if (around.isEmpty()) return noTarget(p);
            ServerLevel l = p.serverLevel();
            l.sendParticles(dust(el.color, 1.5F), p.getX(), p.getY() + 1, p.getZ(), 60, 2.5, 0.6, 2.5, 0.02);
            l.playSound(null, p.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1F, 0.9F);
            for (LivingEntity t : around) Elements.castFrom(p, t, el);
            return true;
        }
    }

    /** Storm (ultimate): three waves of your element, each wider than the last. */
    public static final class Storm implements Skill {
        private final Element el; private final float scale;
        public Storm(Element el, float scale) { this.el = el; this.scale = scale; }
        @Override public String getId() { return "you_storm"; }
        @Override public Component getName() { return Component.literal(el.title() + " Storm"); }
        @Override public int getCooldownTicks() { return 90 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            ServerLevel l = p.serverLevel();
            p.getPersistentData().putLong(SkillEvents.ULT_UNTIL, l.getGameTime() + 50);
            l.playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6F, 1.6F);
            for (int i = 0; i < 3; i++) {
                double r = (5 + i * 2.5) * scale; int at = i * 15;
                ServerScheduler.later(at, () -> {
                    if (!p.isAlive()) return;
                    l.sendParticles(dust(el.color, 2F), p.getX(), p.getY() + 1, p.getZ(), 90, r * 0.5, 0.8, r * 0.5, 0.05);
                    l.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 1.5F);
                    for (LivingEntity t : SpUtil.around(p, p.position(), r)) { Elements.castFrom(p, t, el); t.hurt(p.damageSources().playerAttack(p), 4F * scale); }
                });
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ race skills

    /** Fox Kin: the Kitsune attack, tier I-III (harder and harder hitting). */
    public static final class KitsuneTier extends com.xenowarriors.skill.mikiko.KitsuneSkill {
        private final int tier;
        public KitsuneTier(int tier) { this.tier = tier; }
        @Override protected float power() { return tier == 3 ? 2.2F : tier == 2 ? 1.5F : 1F; }
        @Override public String getId() { return "kitsune"; }
        @Override public Component getName() { return Component.literal("Kitsune " + "I".repeat(tier)); }
        @Override public int getKarmaCost() { return 30 + tier * 5; }
    }

    /** Skylander: Lunar or Solar form - 30 seconds of power, strongest at the matching time of day. */
    public static final class SkyForm implements Skill {
        private final boolean lunar;
        public SkyForm(boolean lunar) { this.lunar = lunar; }
        @Override public String getId() { return lunar ? "you_lunar" : "you_solar"; }
        @Override public int getCooldownTicks() { return 60 * 20; }
        @Override public int getKarmaCost() { return 30; }
        @Override public boolean use(ServerPlayer p) {
            boolean night = p.level().isNight();
            boolean strong = lunar == night;
            int amp = strong ? 1 : 0, len = strong ? 600 : 400;
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, len, amp));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, len, amp));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, len, 0));
            if (strong) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, len, 0));
            YouRaces.form(p, lunar ? "lunar" : "solar", len);
            p.serverLevel().sendParticles(lunar ? ParticleTypes.END_ROD : ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 0.8, 0.6, 0.03);
            p.level().playSound(null, p.blockPosition(), lunar ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1F, lunar ? 1.4F : 0.8F);
            p.displayClientMessage(Component.literal(strong ? (lunar ? "The moon is with you." : "The sun is with you.") : (lunar ? "Lunar form - stronger at night." : "Solar form - stronger by day.")), true);
            return true;
        }
    }

    /** Seedling: roots burst up and hold everyone nearby for 3 seconds. */
    public static final class RootSnare implements Skill {
        @Override public String getId() { return "you_roots"; }
        @Override public int getCooldownTicks() { return 20 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            List<LivingEntity> around = SpUtil.around(p, p.position(), 5);
            if (around.isEmpty()) return noTarget(p);
            ServerLevel l = p.serverLevel();
            l.playSound(null, p.blockPosition(), SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 1F, 0.6F);
            for (LivingEntity t : around) {
                Vec3 at = t.position();
                t.hurt(p.damageSources().playerAttack(p), 3F);
                for (int i = 0; i < 60; i += 2) ServerScheduler.later(i, () -> { if (t.isAlive()) { com.xenowarriors.skill.Rooting.hold(t, at);
                    l.sendParticles(ParticleTypes.COMPOSTER, at.x, at.y + 0.3, at.z, 2, 0.3, 0.2, 0.3, 0); } });
            }
            return true;
        }
    }

    /** Seedling: Bloom - heal yourself and allies around you, more in sunlight. */
    public static final class Bloom implements Skill {
        @Override public String getId() { return "you_bloom"; }
        @Override public int getCooldownTicks() { return 40 * 20; }
        @Override public int getKarmaCost() { return 30; }
        @Override public boolean use(ServerPlayer p) {
            boolean sun = p.level().isDay() && p.level().canSeeSky(p.blockPosition());
            float amt = sun ? 10F : 6F;
            p.heal(amt);
            for (ServerPlayer o : p.serverLevel().players()) if (o != p && o.distanceTo(p) < 8 && com.xenowarriors.party.Parties.sameParty(p, o)) o.heal(amt);
            p.serverLevel().sendParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY() + 1, p.getZ(), 40, 1.5, 0.8, 1.5, 0.02);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.PLAYERS, 1F, 1F);
            return true;
        }
    }

    /** Angel: Divine Light - a pillar of light on your target and everything near it. */
    public static final class DivineLight implements Skill {
        @Override public String getId() { return "you_light"; }
        @Override public int getCooldownTicks() { return 12 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public int clashColor() { return 0xFFFDE0; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SkillTargeting.aimed(p, 24, 12);
            if (t == null) return noTarget(p);
            ServerLevel l = p.serverLevel();
            Vec3 at = t.position();
            for (int y = 0; y < 12; y++) l.sendParticles(ParticleTypes.END_ROD, at.x, at.y + y, at.z, 4, 0.4, 0.2, 0.4, 0);
            l.playSound(null, t.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5F, 1.8F);
            for (LivingEntity o : SpUtil.around(p, at, 3)) { o.hurt(p.damageSources().playerAttack(p), 12F); o.setSecondsOnFire(3); }
            return true;
        }
    }

    /** You race: Scan - everything around you glows, and the weakest is marked. */
    public static final class Scan implements Skill {
        @Override public String getId() { return "you_scan"; }
        @Override public int getCooldownTicks() { return 20 * 20; }
        @Override public int getKarmaCost() { return 10; }
        @Override public boolean use(ServerPlayer p) {
            List<LivingEntity> around = SpUtil.around(p, p.position(), 24);
            if (around.isEmpty()) return noTarget(p);
            for (LivingEntity t : around) t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
            p.displayClientMessage(Component.literal(around.size() + " signatures found"), true);
            return true;
        }
    }

    /** You race: Suppress - weakens and slows the target (what the capture will one day finish). */
    public static final class Suppress implements Skill {
        @Override public String getId() { return "you_suppress"; }
        @Override public int getCooldownTicks() { return 18 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SkillTargeting.aimed(p, 20, 12);
            if (t == null) return noTarget(p);
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
            t.hurt(p.damageSources().playerAttack(p), 4F);
            p.serverLevel().sendParticles(dust(0xC8A0FF, 1.4F), t.getX(), t.getY() + 1, t.getZ(), 30, 0.5, 0.7, 0.5, 0.02);
            p.level().playSound(null, t.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1F, 0.7F);
            return true;
        }
    }

    /** Human: Second Wind - when you're low, a burst of health and speed. */
    public static final class SecondWind implements Skill {
        @Override public String getId() { return "you_second_wind"; }
        @Override public int getCooldownTicks() { return 50 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            boolean low = p.getHealth() <= p.getMaxHealth() * 0.4F;
            p.heal(low ? p.getMaxHealth() * 0.35F : 4F);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, low ? 1 : 0));
            p.serverLevel().sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2, p.getZ(), low ? 12 : 4, 0.4, 0.3, 0.4, 0);
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, low ? 1.4F : 1.8F);
            return true;
        }
    }
}
