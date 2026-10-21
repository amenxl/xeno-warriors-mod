package com.xenowarriors.network;

import com.xenowarriors.fox.FoxPets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> server: put this skin on my fox (if it's high enough level). */
public record SetFoxSkinPacket(UUID fox, int skin) {
    public static void encode(SetFoxSkinPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.fox);
        buf.writeVarInt(m.skin);
    }

    public static SetFoxSkinPacket decode(FriendlyByteBuf buf) {
        return new SetFoxSkinPacket(buf.readUUID(), buf.readVarInt());
    }

    public static void handle(SetFoxSkinPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) FoxPets.setSkin(p, m.fox, m.skin);
    }
}
