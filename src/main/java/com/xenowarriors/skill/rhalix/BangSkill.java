package com.xenowarriors.skill.rhalix;

import com.xenowarriors.entity.EarthBulletEntity;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Rhalix skill 4 - Bang.
 * He points one finger like a gun and fires a bullet of earth at the target. Tap it as often as
 * you like - it only has a tiny cooldown and costs a little Karma per shot.
 */
public class BangSkill implements Skill {
    private static final int COOLDOWN = 7;
    public static final int KARMA_COST = 5;
    public static final double AIM_RANGE = 40;

    @Override
    public String getId() { return "bang"; }

    @Override
    public int getCooldownTicks() { return COOLDOWN; }

    @Override
    public int getKarmaCost() { return KARMA_COST; }

    @Override
    public boolean use(ServerPlayer player) {
        LivingEntity target = SkillTargeting.aimed(player, AIM_RANGE, 6);
        Vec3 look = player.getViewVector(1F);
        float yaw = player.getYRot() * ((float) Math.PI / 180F);
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 finger = player.getEyePosition().add(look.scale(0.9)).add(right.scale(0.35)).add(0, -0.25, 0);
        Vec3 dir = target != null ? target.getBoundingBox().getCenter().subtract(finger) : look;

        player.level().addFreshEntity(EarthBulletEntity.create(player.level(), player, finger, dir, target));
        ServerLevel server = player.serverLevel();
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                finger.x, finger.y, finger.z, 6, 0.05, 0.05, 0.05, 0.12);
        server.sendParticles(ParticleTypes.SMOKE, finger.x, finger.y, finger.z, 3, 0.03, 0.03, 0.03, 0.02);
        server.playSound(null, finger.x, finger.y, finger.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.9F, 0.7F + player.getRandom().nextFloat() * 0.15F);
        server.playSound(null, finger.x, finger.y, finger.z, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1.0F, 0.6F);
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(player.getId(), SkillAnimPacket.BANG, false), player);
        return true;
    }

    @Override
    public int clashColor() { return 0xA060FF; }
}
