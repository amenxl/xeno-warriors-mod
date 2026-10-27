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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Sam (Dylan): his bat, the rare Clear Space element, and his time bombs. "Let's beat some heads!" */
public final class SamSkills {
    private SamSkills() {}
    static final DustParticleOptions CLEAR = new DustParticleOptions(new Vector3f(0.6F, 1F, 0.9F), 1.3F);

    static void launch(ServerPlayer p, LivingEntity t, float dmg, double power) {
        Vec3 dir = t.position().subtract(p.position()).multiply(1, 0, 1).normalize();
        t.hurt(p.damageSources().playerAttack(p), dmg);
        t.setDeltaMovement(dir.scale(power).add(0, power * 0.45, 0)); t.hurtMarked = true;
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        p.serverLevel().sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 1, t.getZ(), 1, 0, 0, 0, 0);
        p.level().playSound(null, t.blockPosition(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 1F, 1.3F);
    }

    /** Batter Up!: a full swing of the bat that sends them flying. */
    public static class BatterUp implements Skill {
        @Override public String getId() { return "batter_up"; }
        @Override public int getCooldownTicks() { return 7 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SpUtil.front(p, 4.5);
            if (t == null) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            launch(p, t, 9F, 2.4);
            p.displayClientMessage(Component.literal("Batter up!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
            return true;
        }
    }

    /** Clear Space: blinks straight across the space to whoever he's looking at. Sneak: pulls THEM to right in front of him. */
    public static class ClearSpace implements Skill {
        @Override public String getId() { return "clear_space"; }
        @Override public int getCooldownTicks() { return 9 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = com.xenowarriors.skill.SkillTargeting.aimed(p, 32, 8);
            if (t == null) { p.displayClientMessage(Component.literal("Look at someone to clear the space").withStyle(ChatFormatting.GRAY), true); return false; }
            ServerLevel l = p.serverLevel();
            Vec3 from = p.position();
            SpUtil.line(l, p.getEyePosition(), t.getEyePosition(), CLEAR, 30);
            if (p.isShiftKeyDown()) {
                Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6));
                t.teleportTo(at.x, p.getY(), at.z);
                l.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, p.getY() + 1, at.z, 30, 0.3, 0.6, 0.3, 0.05);
            } else {
                Vec3 back = t.position().subtract(from).multiply(1, 0, 1).normalize();
                Vec3 at = t.position().subtract(back.scale(1.4));
                l.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.6, 0.3, 0.05);
                p.teleportTo(at.x, t.getY(), at.z);
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
                p.fallDistance = 0;
                l.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, t.getY() + 1, at.z, 30, 0.3, 0.6, 0.3, 0.05);
            }
            l.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1F, 1.4F);
            return true;
        }
    }

    /** Ticking Time Bomb: a bat strike that plants a bomb in them - it goes off 10 seconds later. */
    public static class TickingTimeBomb implements Skill {
        @Override public String getId() { return "ticking_time_bomb"; }
        @Override public int getCooldownTicks() { return 15 * 20; }
        @Override public int getKarmaCost() { return 30; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SpUtil.front(p, 4.5);
            if (t == null) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            t.hurt(p.damageSources().playerAttack(p), 4F);
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            SpState.plantBomb(p, t, 200, 16F);
            p.level().playSound(null, t.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1F, 1.4F);
            p.displayClientMessage(Component.literal("Tick... tick... (10s)").withStyle(ChatFormatting.RED), true);
            return true;
        }
    }

    /** Knockout Mode: for 15 seconds, a blow that would finish an enemy knocks them out cold instead. */
    public static class KnockoutMode implements Skill {
        @Override public String getId() { return "knockout_mode"; }
        @Override public int getCooldownTicks() { return 30 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            SpState.KNOCKOUT.put(p.getUUID(), SpState.now(p) + 300);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.8F);
            p.displayClientMessage(Component.literal("Bat: knockout mode").withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
    }

    /** Batter Batter and Shoot: three swings, the last sending them flying - and setting off any bomb in them early, harder. */
    public static class BatterShoot implements Skill {
        @Override public String getId() { return "batter_shoot"; }
        @Override public int getCooldownTicks() { return 16 * 20; }
        @Override public int getKarmaCost() { return 30; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SpUtil.front(p, 4.5);
            if (t == null) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            for (int i = 0; i < 3; i++) {
                int ii = i;
                ServerScheduler.later(i * 6, () -> {
                    if (!t.isAlive() || t.distanceTo(p) > 7) return;
                    t.invulnerableTime = 0;
                    if (ii < 2) { t.hurt(p.damageSources().playerAttack(p), 4F); p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                        p.level().playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1F, 1.2F); }
                    else { launch(p, t, 8F, 2.8); if (SpState.detonate(t, 1.5F)) p.displayClientMessage(Component.literal("...and SHOOT!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true); }
                });
            }
            return true;
        }
    }

    /** Ultimate - Psychotic Time Bomb: plants bombs in every enemy around him; they all go off 5 seconds later. */
    public static class PsychoticTimeBomb implements Skill {
        @Override public String getId() { return "psychotic_time_bomb"; }
        @Override public int getCooldownTicks() { return 90 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            var targets = SpUtil.around(p, p.position(), 12);
            if (targets.isEmpty()) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            for (LivingEntity t : targets) {
                SpState.plantBomb(p, t, 100, 20F);
                SpUtil.line(p.serverLevel(), p.getEyePosition(), t.getEyePosition(), CLEAR, 16);
            }
            p.level().playSound(null, p.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1.5F, 0.8F);
            p.displayClientMessage(Component.literal("Psychotic Time Bomb! (" + targets.size() + " planted)").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            return true;
        }
    }
}
