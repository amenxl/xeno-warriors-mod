package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: I opened / closed a menu (so other players see me standing, not sat down idling). */
public record MenuStatePacket(boolean open) {
    public static void encode(MenuStatePacket m, FriendlyByteBuf buf) { buf.writeBoolean(m.open); }

    public static MenuStatePacket decode(FriendlyByteBuf buf) { return new MenuStatePacket(buf.readBoolean()); }

    public static void handle(MenuStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) com.xenowarriors.mikiko.InMenu.set(p, m.open);
    }
}
