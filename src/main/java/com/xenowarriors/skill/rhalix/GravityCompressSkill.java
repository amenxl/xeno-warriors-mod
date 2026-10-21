package com.xenowarriors.skill.rhalix;

import com.xenowarriors.entity.GravityCompressEntity;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Rhalix skill 3 - Gravity Compress.
 * Traps the target in a gravity sphere that closes in, crushing them. They can't move until it implodes.
 */
public class GravityCompressSkill implements Skill {
    private static final int COOLDOWN = 18 * 20;
    public static final int KARMA_COST = 35;
    public static final double RANGE = 24;

    @Override
    public String getId() { return "gravity_compress"; }

    @Override
    public int getCooldownTicks() { return COOLDOWN; }

    @Override
    public int getKarmaCost() { return KARMA_COST; }

    @Override
    public boolean use(ServerPlayer player) {
        LivingEntity target = SkillTargeting.aimed(player, RANGE, 7);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true);
            return false;
        }
        player.level().addFreshEntity(GravityCompressEntity.create(player.level(), player, target));
        boolean airborne = player.getAbilities().flying || !player.onGround();
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(player.getId(), SkillAnimPacket.COMPRESS, airborne), player);
        player.displayClientMessage(Component.translatable("message.xenowarriors.gravity_compress.cast"), true);
        return true;
    }
}
