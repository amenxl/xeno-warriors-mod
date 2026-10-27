package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: this mob has an element aura of this colour (refreshed every second while it lasts). */
public record AuraMarkPacket(int entityId, int rgb) {
    public static void encode(AuraMarkPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeInt(msg.rgb);
    }

    public static AuraMarkPacket decode(FriendlyByteBuf buf) {
        return new AuraMarkPacket(buf.readVarInt(), buf.readInt());
    }

    public static void handle(AuraMarkPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.DbzAura.mark(msg.entityId, msg.rgb));
    }
}
