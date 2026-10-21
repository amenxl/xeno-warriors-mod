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
        if (player != null) SkillManager.useSkill(player, msg.slot);
    }
}
