package com.xenowarriors.skill.mikiko;

import com.xenowarriors.damage.ModDamageTypes;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillEvents;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.core.BlockPos;
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

/**
 * Mikiko skill 2 - Kitsune Attack: Tier 3.
 * He crouches, then blurs forward through the target in a streak of fox-fire, landing behind them in
 * his finishing pose while the hits land - a flurry of bone-cracking blows and a heavy finisher.
 * Broken bones: the target is badly slowed and weakened for a while.
 */
public class KitsuneSkill implements Skill {
    private static final int COOLDOWN = 16 * 20;
    private static final DustParticleOptions FOX = new DustParticleOptions(new Vector3f(1F, 0.55F, 0.1F), 1.3F);
    private static final DustParticleOptions FOX_WHITE = new DustParticleOptions(new Vector3f(1F, 0.95F, 0.85F), 1F);

    @Override
    public String getId() { return "kitsune"; }

    @Override
    public int getCooldownTicks() { return COOLDOWN; }

    @Override
    public int getKarmaCost() { return 35; }

    @Override
    public boolean use(ServerPlayer player) {
        LivingEntity target = SkillTargeting.aimed(player, 22, 10);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(player.getId(), SkillAnimPacket.KITSUNE, false), player);
        player.getPersistentData().putLong(SkillEvents.ULT_UNTIL, level.getGameTime() + 22);   // untouchable mid-dash
        player.displayClientMessage(Component.translatable("message.xenowarriors.kitsune.cast"), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FOX_SCREECH, SoundSource.PLAYERS, 0.9F, 1.3F);

        // the dash: a moment to crouch, then he's behind them
        ServerScheduler.later(4, () -> {
            if (!player.isAlive() || !target.isAlive()) return;
            Vec3 from = player.position();
            Vec3 flat = new Vec3(target.getX() - from.x, 0, target.getZ() - from.z);
            Vec3 dir = flat.lengthSqr() < 0.01 ? player.getLookAngle().multiply(1, 0, 1).normalize() : flat.normalize();
            Vec3 land = target.position().add(dir.scale(target.getBbWidth() / 2 + 1.4));
            if (!level.noCollision(player, player.getBoundingBox().move(land.subtract(from)))) {
                land = target.position().subtract(dir.scale(target.getBbWidth() / 2 + 1.2));   // no room behind: stop in front
            }
            // fox-fire streak along the path
            for (int i = 0; i <= 24; i++) {
                Vec3 p = from.lerp(land, i / 24.0).add(0, 1.0, 0);
                level.sendParticles(FOX, p.x, p.y, p.z, 2, 0.15, 0.3, 0.15, 0);
                level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.1, 0.2, 0.1, 0.01);
                if (i % 3 == 0) level.sendParticles(FOX_WHITE, p.x, p.y, p.z, 1, 0.05, 0.2, 0.05, 0);
            }
            level.sendParticles(ParticleTypes.CLOUD, from.x, from.y + 0.2, from.z, 12, 0.4, 0.05, 0.4, 0.08);
            Vec3 face = target.position().subtract(land);
            float yaw = (float) Math.toDegrees(Math.atan2(-face.x, face.z));
            player.teleportTo(level, land.x, land.y, land.z, yaw, player.getXRot());
            player.fallDistance = 0;
            level.playSound(null, land.x, land.y, land.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.7F);
            level.playSound(null, land.x, land.y, land.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5F, 1.8F);
        });
        // a flurry of hits landing after he's through, each cracking bone
        for (int h = 0; h < 5; h++) {
            int k = h;
            ServerScheduler.later(6 + h * 2, () -> {
                if (!target.isAlive()) return;
                target.invulnerableTime = 0;
                target.hurt(ModDamageTypes.source(level, ModDamageTypes.KITSUNE, player, player), 2.5F);
                target.setDeltaMovement(target.getDeltaMovement().multiply(0.2, 1, 0.2));
                Vec3 c = target.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 8, 0.3, 0.4, 0.3, 0.3);
                level.sendParticles(FOX, c.x, c.y, c.z, 4, 0.3, 0.4, 0.3, 0);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.2F, 0.7F + k * 0.12F);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8F, 0.8F + k * 0.1F);
            });
        }
        // the finisher: every bone breaks
        ServerScheduler.later(18, () -> {
            if (!target.isAlive()) return;
            target.invulnerableTime = 0;
            target.hurt(ModDamageTypes.source(level, ModDamageTypes.KITSUNE, player, player), 8F);
            Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0E-4) away = away.normalize();
            target.push(away.x * 0.6, 0.35, away.z * 0.6);   // knocked away from him
            target.hurtMarked = true;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 2));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 120, 1));
            Vec3 c = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(FOX, c.x, c.y, c.z, 30, 0.5, 0.6, 0.5, 0);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.SKELETON_DEATH, SoundSource.PLAYERS, 1F, 0.6F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 1.6F);
            if (target instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.xenowarriors.kitsune.broken"), true);
        });
        return true;
    }

    @SuppressWarnings("unused")
    private static boolean open(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
