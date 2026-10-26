package com.xenowarriors.network;

import com.xenowarriors.skill.SkillManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server from the skills screen: put pool skill {@code index} in slot 0-3. */
public record SetSkillSlotPacket(int slot, int index) {
    public static void encode(SetSkillSlotPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.slot);
        buf.writeVarInt(m.index);
    }

    public static SetSkillSlotPacket decode(FriendlyByteBuf buf) {
        return new SetSkillSlotPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SetSkillSlotPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) SkillManager.setSlot(p, m.slot, m.index);
        ctx.get().setPacketHandled(true);
    }
}
