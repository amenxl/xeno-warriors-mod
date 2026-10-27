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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Queen Kyrie: rapier, the boxing Rhalix taught her, and the Gravity element (Kishi is shared with him). */
public final class KyrieSkills {
    private KyrieSkills() {}
    static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.55F, 0.3F, 0.9F), 1.2F);

    /** Rapier Thrust: a lunging thrust that runs through everything in a line. */
    public static class RapierThrust implements Skill {
        @Override public String getId() { return "rapier_thrust"; }
        @Override public int getCooldownTicks() { return 7 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1F, 1.6F);
            SpUtil.dash(p, 1.4, 5, ParticleTypes.CRIT, e -> {
                e.hurt(p.damageSources().playerAttack(p), 9F);
                p.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + 1, e.getZ(), 10, 0.2, 0.3, 0.2, 0.2);
                p.level().playSound(null, e.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1F, 1.3F);
            });
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            return true;
        }
    }

    /** Gravity Fetch: pulls what she's looking at (or every dropped item nearby) straight to her. */
    public static class GravityFetch implements Skill {
        @Override public String getId() { return "gravity_fetch"; }
        @Override public int getCooldownTicks() { return 9 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            ServerLevel l = p.serverLevel();
            LivingEntity t = SpUtil.front(p, 20);
            if (t != null) {
                Vec3 pull = p.position().subtract(t.position());
                t.setDeltaMovement(pull.normalize().scale(Math.min(2.2, pull.length() * 0.25)).add(0, 0.35, 0)); t.hurtMarked = true;
                SpUtil.line(l, p.getEyePosition(), t.position().add(0, 1, 0), PURPLE, 24);
            }
            int items = 0;
            for (ItemEntity it : l.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16))) {
                it.teleportTo(p.getX(), p.getY() + 0.5, p.getZ()); it.setNoPickUpDelay(); items++;
                l.sendParticles(PURPLE, it.getX(), it.getY(), it.getZ(), 4, 0.1, 0.1, 0.1, 0);
            }
            if (t == null && items == 0) { p.displayClientMessage(Component.literal("Nothing to fetch").withStyle(ChatFormatting.GRAY), true); return false; }
            l.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);
            return true;
        }
    }

    /** Bullet Stop: a gravity field around her for 6 seconds - arrows and blasts stop dead in the air. */
    public static class BulletStop implements Skill {
        @Override public String getId() { return "bullet_stop"; }
        @Override public int getCooldownTicks() { return 18 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            SpState.GRAVITY_FIELD.put(p.getUUID(), SpState.now(p) + 120);
            p.level().playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1F, 1.4F);
            return true;
        }
    }

    /** Boxing Stance: the stance she copied from Rhalix - for 12 seconds her blows hit 60% harder and she's quicker. */
    public static class BoxingStance implements Skill {
        @Override public String getId() { return "boxing_stance"; }
        @Override public int getCooldownTicks() { return 25 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            SpState.BOXING.put(p.getUUID(), SpState.now(p) + 240);
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 240, 1, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 240, 0, false, false));
            p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1F, 0.8F);
            p.displayClientMessage(Component.literal("Boxing stance - just like Rhalix taught her").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return true;
        }
    }

    /** Jail Without Any Milk!: the Queen's punishment - the target is pinned in a gravity cell, weak and unable to move. */
    public static class NoMilk implements Skill {
        @Override public String getId() { return "no_milk"; }
        @Override public int getCooldownTicks() { return 30 * 20; }
        @Override public int getKarmaCost() { return 30; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SpUtil.front(p, 18);
            if (t == null) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 9));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 2));
            t.addEffect(new MobEffectInstance(MobEffects.JUMP, 120, 128));
            ServerLevel l = p.serverLevel();
            for (int i = 0; i < 6; i++) {
                int ii = i;
                ServerScheduler.later(i * 20, () -> { if (!t.isAlive()) return;
                    for (int k = 0; k < 4; k++) { double a = k * Math.PI / 2 + ii * 0.3;
                        for (double y = 0; y < 2.4; y += 0.3) l.sendParticles(PURPLE, t.getX() + Math.cos(a) * 0.9, t.getY() + y, t.getZ() + Math.sin(a) * 0.9, 1, 0, 0, 0, 0); } });
            }
            l.playSound(null, t.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 1F, 0.7F);
            p.displayClientMessage(Component.literal("Jail... WITHOUT ANY MILK!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
            return true;
        }
    }

    /** Ultimate - Royal Gravity: everything around her is lifted into the air and slammed back down. */
    public static class RoyalGravity implements Skill {
        @Override public String getId() { return "royal_gravity"; }
        @Override public int getCooldownTicks() { return 90 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            ServerLevel l = p.serverLevel();
            var targets = SpUtil.around(p, p.position(), 9);
            for (LivingEntity e : targets) { e.setDeltaMovement(0, 1.2, 0); e.hurtMarked = true; }
            l.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.6F);
            for (int r = 1; r <= 9; r++) { int rr = r; ServerScheduler.later(r, () -> {
                for (int i = 0; i < 24; i++) { double a = i * Math.PI / 12; l.sendParticles(PURPLE, p.getX() + Math.cos(a) * rr, p.getY() + 0.2, p.getZ() + Math.sin(a) * rr, 1, 0, 0, 0, 0); } }); }
            ServerScheduler.later(16, () -> {
                for (LivingEntity e : targets) {
                    if (!e.isAlive()) continue;
                    e.setDeltaMovement(0, -2.5, 0); e.hurtMarked = true;
                    e.hurt(p.damageSources().playerAttack(p), 18F);
                    l.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY(), e.getZ(), 1, 0, 0, 0, 0);
                }
                l.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2F, 0.5F);
                com.xenowarriors.XwFx.send(l, com.xenowarriors.network.FxPacket.SHOCK, p.getX(), p.getY(), p.getZ(), 0x8A4AE8, 2.5F);
            });
            return true;
        }
    }
}
