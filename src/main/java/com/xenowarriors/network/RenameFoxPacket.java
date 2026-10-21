package com.xenowarriors.network;

import com.xenowarriors.fox.FoxPets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> server: "call this fox of mine ..." */
public record RenameFoxPacket(UUID fox, String name) {
    public static void encode(RenameFoxPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.fox);
        buf.writeUtf(msg.name, 64);
    }

    public static RenameFoxPacket decode(FriendlyByteBuf buf) {
        return new RenameFoxPacket(buf.readUUID(), buf.readUtf(64));
    }

    public static void handle(RenameFoxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) FoxPets.rename(p, msg.fox, msg.name);
    }
}
