package com.xenowarriors.skill.rhalix;

import com.xenowarriors.entity.GravityBarrageEntity;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Rhalix's ultimate - Gravity Barrage (cutscene).
 * He flies up, locks every nearby enemy in a gravity sphere, tears rocks out of the ground around
 * them and fires them all at once again and again, then flies down as the buried rocks explode.
 */
public class GravityBarrageSkill implements Skill {
    private static final int COOLDOWN = 60 * 20;
    public static final int KARMA_COST = 60;

    @Override
    public String getId() { return "gravity_barrage"; }

    @Override
    public int getCooldownTicks() { return COOLDOWN; }

    @Override
    public int getKarmaCost() { return KARMA_COST; }

    @Override
    public boolean use(ServerPlayer player) {
        List<LivingEntity> targets = SkillTargeting.around(player, player.position(),
                GravityBarrageEntity.TARGET_RADIUS, 10, GravityBarrageEntity.MAX_TARGETS);
        if (targets.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.xenowarriors.no_targets_nearby"), true);
            return false;
        }
        player.level().addFreshEntity(GravityBarrageEntity.create(player, targets));
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(player.getId(), SkillAnimPacket.BARRAGE, true), player);
        return true;
    }
}
