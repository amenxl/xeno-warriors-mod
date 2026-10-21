package com.xenowarriors.skill.mikiko;

import com.xenowarriors.entity.BlazingWindEntity;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.Skill;
import net.minecraft.server.level.ServerPlayer;

/** Mikiko's ultimate - Blazing Wind (cutscene): a giant fire tornado that drags in and burns everything nearby. */
public class BlazingWindSkill implements Skill {
    @Override
    public String getId() { return "blazing_wind"; }

    @Override
    public int getCooldownTicks() { return 60 * 20; }

    @Override
    public int getKarmaCost() { return 60; }

    @Override
    public boolean use(ServerPlayer player) {
        player.level().addFreshEntity(BlazingWindEntity.create(player));
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(player.getId(), SkillAnimPacket.BLAZING, false), player);
        return true;
    }
}
