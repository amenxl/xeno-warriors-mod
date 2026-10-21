package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Owner -> server: open a companion's bag, or change its skin. */
public record WingPetActionPacket(UUID pet, int action, int arg) {
    public static final int BAG = 0, SKIN = 1;

    public static void encode(WingPetActionPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.pet);
        buf.writeVarInt(m.action);
        buf.writeVarInt(m.arg);
    }

    public static WingPetActionPacket decode(FriendlyByteBuf buf) {
        return new WingPetActionPacket(buf.readUUID(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(WingPetActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        var sender = ctx.get().getSender();
        if (sender != null) com.xenowarriors.wing.WingPets.action(sender, m.pet, m.action, m.arg);
    }
}
