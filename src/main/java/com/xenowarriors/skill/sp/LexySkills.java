package com.xenowarriors.skill.sp;

import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
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

/** Lexy (Maynaya): freakishly strong, Karma off the charts, and Persephone sharing her body. */
public final class LexySkills {
    private LexySkills() {}
    static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.9F, 0.05F, 0.1F), 1.4F);

    static void crack(ServerLevel l, Vec3 at, int n) {
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, l.getBlockState(net.minecraft.core.BlockPos.containing(at).below())), at.x, at.y + 0.1, at.z, n, 0.8, 0.1, 0.8, 0.1);
    }

    /** Freak Punch: one punch with all her strength - enough to break a table (or send someone flying). */
    public static class FreakPunch implements Skill {
        @Override public String getId() { return "freak_punch"; }
        @Override public int getCooldownTicks() { return 7 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SpUtil.front(p, 4);
            if (t == null) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            Vec3 dir = t.position().subtract(p.position()).multiply(1, 0, 1).normalize();
            t.hurt(p.damageSources().playerAttack(p), 12F);
            t.setDeltaMovement(dir.scale(3.0).add(0, 0.6, 0)); t.hurtMarked = true;
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            p.serverLevel().sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 1, t.getZ(), 2, 0.2, 0.2, 0.2, 0);
            p.level().playSound(null, t.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.6F);
            return true;
        }
    }

    /** Table Breaker: leaps and smashes the ground - a shockwave throws everything nearby into the air. */
    public static class TableBreaker implements Skill {
        @Override public String getId() { return "table_breaker"; }
        @Override public int getCooldownTicks() { return 12 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            p.setDeltaMovement(p.getDeltaMovement().add(0, 0.9, 0)); p.hurtMarked = true;
            ServerScheduler.later(10, () -> { p.setDeltaMovement(0, -2.0, 0); p.hurtMarked = true; });
            ServerScheduler.later(16, () -> {
                if (!p.isAlive()) return;
                ServerLevel l = p.serverLevel();
                p.fallDistance = 0;
                for (LivingEntity e : SpUtil.around(p, p.position(), 5.5)) {
                    e.hurt(p.damageSources().playerAttack(p), 9F);
                    Vec3 out = e.position().subtract(p.position()).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(out.scale(0.8).add(0, 0.9, 0)); e.hurtMarked = true;
                }
                for (int r = 1; r <= 5; r++) crack(l, p.position(), 20 * r);
                l.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1F, 0.7F);
                com.xenowarriors.XwFx.send(l, com.xenowarriors.network.FxPacket.SHOCK, p.getX(), p.getY(), p.getZ(), 0x3A4AD0, 2F);
            });
            return true;
        }
    }

    /** Karma Surge: her Karma is off the charts - she refills her own and shares some with her party nearby. */
    public static class KarmaSurge implements Skill {
        @Override public String getId() { return "karma_surge"; }
        @Override public int getCooldownTicks() { return 40 * 20; }
        @Override public int getKarmaCost() { return 0; }
        @Override public boolean use(ServerPlayer p) {
            com.xenowarriors.karma.Karma.add(p, 45F);
            for (ServerPlayer m : com.xenowarriors.party.Parties.members(p))
                if (m != p && m.distanceToSqr(p) < 16 * 16) { com.xenowarriors.karma.Karma.add(m, 20F);
                    m.displayClientMessage(Component.literal("Lexy shared her Karma (+20)").withStyle(ChatFormatting.AQUA), true); }
            ServerLevel l = p.serverLevel();
            l.sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 0.9, 0.6, 0.05);
            l.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1F, 1.2F);
            com.xenowarriors.XwFx.send(l, com.xenowarriors.network.FxPacket.SKILL, p.getX(), p.getY(), p.getZ(), 0x6A7AF0, 1.5F);
            return true;
        }
    }

    /** Wing Rush: a wing-powered charge that slams into the first thing in her way. */
    public static class WingRush implements Skill {
        @Override public String getId() { return "wing_rush"; }
        @Override public int getCooldownTicks() { return 9 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            Vec3 look = p.getLookAngle();
            p.level().playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1F, 1.4F);
            SpUtil.dash(p, 1.6, 8, ParticleTypes.WHITE_ASH, e -> {
                e.hurt(p.damageSources().playerAttack(p), 8F);
                e.setDeltaMovement(look.scale(1.8).add(0, 0.4, 0)); e.hurtMarked = true;
            });
            return true;
        }
    }

    /** Iron Grip: grabs and crushes the target - held still for 2 seconds, then thrown. */
    public static class IronGrip implements Skill {
        @Override public String getId() { return "iron_grip"; }
        @Override public int getCooldownTicks() { return 16 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SpUtil.front(p, 4);
            if (t == null) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            for (int i = 0; i < 40; i += 2) {
                int ii = i;
                ServerScheduler.later(i, () -> {
                    if (!t.isAlive() || !p.isAlive()) return;
                    Vec3 hold = p.getEyePosition().add(p.getLookAngle().scale(1.4)).subtract(0, t.getBbHeight() * 0.5, 0);
                    t.teleportTo(hold.x, hold.y, hold.z); t.setDeltaMovement(Vec3.ZERO); t.hurtMarked = true;
                    if (ii % 10 == 0) { t.invulnerableTime = 0; t.hurt(p.damageSources().playerAttack(p), 3F);
                        p.level().playSound(null, t.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.4F, 1.6F); }
                });
            }
            ServerScheduler.later(42, () -> { if (!t.isAlive()) return;
                t.setDeltaMovement(p.getLookAngle().scale(2.4).add(0, 0.5, 0)); t.hurtMarked = true;
                p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true); });
            return true;
        }
    }

    /** Ultimate - Persephone: the demon takes over for 20 seconds - red eyes, terrifying strength, much tougher. Lexy is drained after. */
    public static class Persephone implements Skill {
        @Override public String getId() { return "persephone"; }
        @Override public int getCooldownTicks() { return 90 * 20; }
        @Override public int getKarmaCost() { return 50; }
        @Override public boolean use(ServerPlayer p) {
            SpState.PERSEPHONE.put(p.getUUID(), SpState.now(p) + 400);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1, false, false));
            ServerLevel l = p.serverLevel();
            for (int i = 0; i < 400; i += 5) {
                int ii = i;
                ServerScheduler.later(i, () -> {
                    if (!p.isAlive()) return;
                    l.sendParticles(RED, p.getX(), p.getY() + 1, p.getZ(), 4, 0.4, 0.8, 0.4, 0);
                    if (ii == 395) {
                        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 1));
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0));
                        p.displayClientMessage(Component.literal("Lexy fights her way back... she's drained").withStyle(ChatFormatting.GRAY), true);
                    }
                });
            }
            l.playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6F, 1.4F);
            p.displayClientMessage(Component.literal("Persephone takes over!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
            return true;
        }
    }
}
