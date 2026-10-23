package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the Corrupted Boots' extra air jump. */
public record DarkJumpPacket() {
    public static void encode(DarkJumpPacket m, FriendlyByteBuf buf) {}

    public static DarkJumpPacket decode(FriendlyByteBuf buf) { return new DarkJumpPacket(); }

    public static void handle(DarkJumpPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.sky.CorruptedArmorEffects.darkJump(p);
    }
}
