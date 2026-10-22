package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: Rhalix is starving (too hungry to fly), and whether his stomach just growled. */
public record RhalixHungerPacket(int entityId, boolean hungry, boolean growl) {
    public static void encode(RhalixHungerPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.hungry);
        buf.writeBoolean(msg.growl);
    }

    public static RhalixHungerPacket decode(FriendlyByteBuf buf) {
        return new RhalixHungerPacket(buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(RhalixHungerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.xenowarriors.client.RhalixHungerClient.set(msg.entityId, msg.hungry, msg.growl));
    }
}
