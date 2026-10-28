package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: your You character's state for the HUD - Sun meter (-1 = not a Seedling), seed state, active form. */
public record YouStatePacket(int sun, boolean seed, String form) {
    public static void encode(YouStatePacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.sun); buf.writeBoolean(m.seed); buf.writeUtf(m.form, 16); }
    public static YouStatePacket decode(FriendlyByteBuf buf) { return new YouStatePacket(buf.readVarInt(), buf.readBoolean(), buf.readUtf(16)); }
    public static void handle(YouStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.you.YouClient.state(m.sun, m.seed, m.form));
        ctx.get().setPacketHandled(true);
    }
}
