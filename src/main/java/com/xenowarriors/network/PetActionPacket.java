package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A Pets menu button: rename, change skin, open bag, or whistle (see PetCare). */
public record PetActionPacket(int kind, int index, int action, int arg, String text) {
    public static void encode(PetActionPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.kind); b.writeVarInt(m.index); b.writeVarInt(m.action); b.writeVarInt(m.arg); b.writeUtf(m.text, 64);
    }

    public static PetActionPacket decode(FriendlyByteBuf b) {
        return new PetActionPacket(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(64));
    }

    public static void handle(PetActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) com.xenowarriors.pet.PetCare.action(p, m.kind, m.index, m.action, m.arg, m.text);
    }
}
