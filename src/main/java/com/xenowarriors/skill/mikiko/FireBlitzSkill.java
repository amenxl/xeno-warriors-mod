package com.xenowarriors.skill.mikiko;

import com.xenowarriors.entity.FireBlitzEntity;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Mikiko skill 4 - Fire Blitz. He cups a ball of fire in his hands and throws it. It sticks to
 * whoever it hits, keeps them burning for 12 seconds, and lets him see them through walls.
 */
public class FireBlitzSkill implements Skill {
    @Override
    public String getId() { return "fire_blitz"; }

    @Override
    public int getCooldownTicks() { return 15 * 20; }

    @Override
    public int getKarmaCost() { return 25; }

    @Override
    public boolean use(ServerPlayer player) {
        LivingEntity target = SkillTargeting.aimed(player, 30, 6);
        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.8)).add(0, -0.3, 0);
        Vec3 dir = target != null
                ? target.getBoundingBox().getCenter().add(0, target.distanceTo(player) * 0.02, 0).subtract(from)
                : player.getLookAngle();
        player.level().addFreshEntity(FireBlitzEntity.create(player, from, dir));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1F, 0.9F);
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(player.getId(), SkillAnimPacket.BLITZ, false), player);
        player.displayClientMessage(Component.translatable("message.xenowarriors.blitz.cast"), true);
        return true;
    }

    @Override
    public int clashColor() { return 0xFF6A1F; }
}
