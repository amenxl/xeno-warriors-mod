package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: this player has a menu open (or closed it). */
public record MenuSeenPacket(int entityId, boolean open) {
    public static void encode(MenuSeenPacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.entityId); buf.writeBoolean(m.open); }

    public static MenuSeenPacket decode(FriendlyByteBuf buf) { return new MenuSeenPacket(buf.readVarInt(), buf.readBoolean()); }

    public static void handle(MenuSeenPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.mikiko.MenuStateClient.seen(m.entityId, m.open));
    }
}
