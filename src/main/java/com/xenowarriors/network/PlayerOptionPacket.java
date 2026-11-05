package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * An on/off player option (stats/PlayerOptions), both ways. Client -> server: turn it on/off (Gravity Magnet from
 * Rhalix's menu, auto step-up from the settings). Server -> client: what it's set to.
 */
public record PlayerOptionPacket(String key, boolean on) {
    public static void encode(PlayerOptionPacket m, FriendlyByteBuf buf) { buf.writeUtf(m.key, 32); buf.writeBoolean(m.on); }

    public static PlayerOptionPacket decode(FriendlyByteBuf buf) { return new PlayerOptionPacket(buf.readUtf(32), buf.readBoolean()); }

    public static void handle(PlayerOptionPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) { com.xenowarriors.stats.PlayerOptions.set(sender, m.key, m.on); return; }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.ClientOptions.received(m.key, m.on));
    }
}
