package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Player -> server: 0 = open my Collection, 1 = enter the Tower, 2 = leave the Tower. */
public record CollectionActionPacket(int action) {
    public static void encode(CollectionActionPacket m, FriendlyByteBuf b) { b.writeVarInt(m.action); }
    public static CollectionActionPacket decode(FriendlyByteBuf b) { return new CollectionActionPacket(b.readVarInt()); }
    public static void handle(CollectionActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) switch (m.action) {
            case 0 -> com.xenowarriors.collection.Collection.open(p, true);
            case 1 -> com.xenowarriors.tower.Tower.enter(p);
            case 2 -> com.xenowarriors.tower.Tower.leave(p, "You left the Tower");
            default -> {}
        }
        ctx.get().setPacketHandled(true);
    }
}
