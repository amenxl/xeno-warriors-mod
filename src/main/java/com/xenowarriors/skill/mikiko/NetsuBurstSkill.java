package com.xenowarriors.skill.mikiko;

import com.xenowarriors.mikiko.Netsu;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
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

import java.util.HashSet;
import java.util.Set;

/**
 * Netsu Burst: everything into one burst of speed - he blasts forward in a line of fire, slamming through everyone in
 * the way. With Netsu burning it hits twice as hard but uses the Netsu up. He's winded for a moment after.
 */
public class NetsuBurstSkill implements Skill {
    private static final int TICKS = 8;

    @Override public String getId() { return "netsu_burst"; }
    @Override public int getCooldownTicks() { return 18 * 20; }
    @Override public int getKarmaCost() { return 35; }

    @Override
    public boolean use(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        boolean allOut = Netsu.active(p);
        if (allOut) Netsu.stop(p);
        float dmg = allOut ? 14F : 7F;
        Vec3 dir = p.getLookAngle().multiply(1, 0, 1).normalize();
        p.displayClientMessage(Component.literal(allOut ? "NETSU BURST - ALL OUT!" : "Netsu Burst!").withStyle(net.minecraft.ChatFormatting.RED), true);
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(p.getId(), SkillAnimPacket.NETSU_BURST, false), p);
        level.playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.7F);
        Set<Integer> hit = new HashSet<>();
        for (int i = 0; i < TICKS; i++) {
            ServerScheduler.later(i, () -> {
                if (!p.isAlive()) return;
                p.setDeltaMovement(dir.scale(allOut ? 2.4 : 1.9).add(0, Math.min(0, p.getDeltaMovement().y), 0));
                p.hurtMarked = true;
                p.fallDistance = 0;
                level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.8, p.getZ(), 12, 0.4, 0.5, 0.4, 0.03);
                level.sendParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 0.3, p.getZ(), 2, 0.3, 0.1, 0.3, 0);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.6, 0.8, 1.6), e -> SkillTargeting.canTarget(p, e))) {
                    if (!hit.add(e.getId())) continue;
                    e.hurt(p.damageSources().playerAttack(p), dmg);
                    e.setSecondsOnFire(allOut ? 6 : 4);
                    Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(e.position().subtract(p.position()).dot(new Vec3(-dir.z, 0, dir.x)) >= 0 ? 0.9 : -0.9);
                    e.push(side.x + dir.x * 0.6, 0.45, side.z + dir.z * 0.6);
                    e.hurtMarked = true;
                    level.playSound(null, e.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1F, 0.8F);
                }
            });
        }
        ServerScheduler.later(TICKS, () -> {
            p.setDeltaMovement(p.getDeltaMovement().scale(0.15));
            p.hurtMarked = true;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, allOut ? 80 : 50, 1, false, true, true));
        });
        return true;
    }
}
