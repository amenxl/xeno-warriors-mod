package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: whether a Rhalix is in Lunar form, and whether Moon Reflect is up. */
public record LunarStatePacket(int entityId, boolean active, boolean reflect) {
    public static void encode(LunarStatePacket m, FriendlyByteBuf b) { b.writeVarInt(m.entityId); b.writeBoolean(m.active); b.writeBoolean(m.reflect); }

    public static LunarStatePacket decode(FriendlyByteBuf b) { return new LunarStatePacket(b.readVarInt(), b.readBoolean(), b.readBoolean()); }

    public static void handle(LunarStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.LunarClient.set(m.entityId, m.active, m.reflect));
        ctx.get().setPacketHandled(true);
    }
}
