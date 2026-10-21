package com.xenowarriors.skill.rhalix;

import com.xenowarriors.entity.EarthWaveEntity;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.Skill;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rhalix skill 2 - Earth Wave.
 * He stomps the ground and a big wave of earth rolls out wherever he's looking,
 * damaging and launching everything in its path. He has to be standing on the ground.
 */
public class EarthWaveSkill implements Skill {
    private static final int COOLDOWN = 10 * 20;
    public static final int KARMA_COST = 25;

    @Override
    public String getId() { return "earth_wave"; }

    @Override
    public int getCooldownTicks() { return COOLDOWN; }

    @Override
    public int getKarmaCost() { return KARMA_COST; }

    @Override
    public boolean use(ServerPlayer player) {
        if (!player.onGround() || player.getAbilities().flying || player.isFallFlying() || player.isInWater()) {
            player.displayClientMessage(Component.translatable("message.xenowarriors.earth_wave.ground"), true);
            return false;
        }
        player.level().addFreshEntity(EarthWaveEntity.create(player.level(), player));
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(player.getId(), SkillAnimPacket.STOMP, false), player);
        player.displayClientMessage(Component.translatable("message.xenowarriors.earth_wave.cast"), true);
        return true;
    }
}
