package com.xenowarriors.network;

import com.xenowarriors.you.YouSlot;
import com.xenowarriors.you.YouSlots;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the You slots screen - OPEN the list, PLAY a slot, CREATE a character in a slot, DELETE a slot. */
public record YouActionPacket(int action, int slot, YouSlot data) {
    public static final int OPEN = 0, PLAY = 1, CREATE = 2, DELETE = 3;

    public static void encode(YouActionPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.action); buf.writeVarInt(m.slot);
        buf.writeBoolean(m.data != null);
        if (m.data != null) m.data.write(buf);
    }
    public static YouActionPacket decode(FriendlyByteBuf buf) {
        int a = buf.readVarInt(), s = buf.readVarInt();
        return new YouActionPacket(a, s, buf.readBoolean() ? YouSlot.read(buf) : null);
    }
    public static void handle(YouActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) YouSlots.act(p, m.action, m.slot, m.data);
        ctx.get().setPacketHandled(true);
    }
}
