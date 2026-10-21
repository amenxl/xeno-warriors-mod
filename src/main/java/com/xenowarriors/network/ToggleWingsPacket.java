package com.xenowarriors.network;

import com.xenowarriors.character.RhalixWings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: Rhalix takes out / puts away his wings. */
public record ToggleWingsPacket() {
    public static void encode(ToggleWingsPacket m, FriendlyByteBuf buf) {}

    public static ToggleWingsPacket decode(FriendlyByteBuf buf) {
        return new ToggleWingsPacket();
    }

    public static void handle(ToggleWingsPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) RhalixWings.toggle(p);
    }
}
