package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: your clash meter (0 = you're being pushed all the way back, 1 = you've won). */
public record ClashStatePacket(boolean active, float pos, int ticks, String opponent) {
    public static void encode(ClashStatePacket m, FriendlyByteBuf b) { b.writeBoolean(m.active); b.writeFloat(m.pos); b.writeVarInt(m.ticks); b.writeUtf(m.opponent); }

    public static ClashStatePacket decode(FriendlyByteBuf b) { return new ClashStatePacket(b.readBoolean(), b.readFloat(), b.readVarInt(), b.readUtf()); }

    public static void handle(ClashStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.ClashClient.set(m.active, m.pos, m.ticks, m.opponent));
        ctx.get().setPacketHandled(true);
    }
}
