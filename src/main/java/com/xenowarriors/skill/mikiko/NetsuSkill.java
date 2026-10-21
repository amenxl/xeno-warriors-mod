package com.xenowarriors.skill.mikiko;

import com.xenowarriors.mikiko.Netsu;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.Skill;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Mikiko skill 3 - Netsu (see mikiko/Netsu). Press again while it burns to put it out early. */
public class NetsuSkill implements Skill {
    @Override
    public String getId() { return "netsu"; }

    @Override
    public int getCooldownTicks() { return 24 * 20; }

    @Override
    public int getKarmaCost() { return 30; }

    @Override
    public boolean tryRecast(ServerPlayer player) {
        if (!Netsu.active(player)) return false;
        Netsu.stop(player);
        return true;
    }

    @Override
    public boolean use(ServerPlayer player) {
        Netsu.start(player);
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(player.getId(), SkillAnimPacket.NETSU, false), player);
        player.displayClientMessage(Component.translatable("message.xenowarriors.netsu.cast"), true);
        return true;
    }
}
