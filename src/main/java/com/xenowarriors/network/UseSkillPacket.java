package com.xenowarriors.network;

import com.xenowarriors.skill.SkillManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: "I pressed skill key N." */
public record UseSkillPacket(int slot) {
    public static void encode(UseSkillPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.slot);
    }

    public static UseSkillPacket decode(FriendlyByteBuf buf) {
        return new UseSkillPacket(buf.readVarInt());
    }

    public static void handle(UseSkillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        if (msg.slot == com.xenowarriors.skill.UltCharge.RELEASE) { com.xenowarriors.skill.UltCharge.release(player); return; }   // (the ultimate key let go)
        // the ultimate key (a player pressing it - not sneaking for a team ultimate, not answering a request): a teammate
        // charging one nearby is joined (a team ultimate); otherwise it charges first (skill.UltCharge)
        if (msg.slot == SkillManager.ULTIMATE_SLOT && !player.isShiftKeyDown() && com.xenowarriors.party.TeamUltimate.pending(player) == null
                && com.xenowarriors.skill.UltCharge.keyPressed(player)) return;
        SkillManager.useSkill(player, msg.slot);
    }
}
