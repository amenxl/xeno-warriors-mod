package com.xenowarriors.network;

import com.xenowarriors.fox.FoxPets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> server: open my fox's bag. */
public record OpenFoxBagPacket(UUID fox) {
    public static void encode(OpenFoxBagPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.fox);
    }

    public static OpenFoxBagPacket decode(FriendlyByteBuf buf) {
        return new OpenFoxBagPacket(buf.readUUID());
    }

    public static void handle(OpenFoxBagPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) FoxPets.openBag(p, m.fox);
    }
}
