package com.xenowarriors.network;

import com.xenowarriors.client.RhalixWingsClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server -> clients: whether this player's wings are out (and whether to play the animation). */
public record WingsPacket(UUID player, boolean out, boolean animate) {
    public static void encode(WingsPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.player);
        buf.writeBoolean(m.out);
        buf.writeBoolean(m.animate);
    }

    public static WingsPacket decode(FriendlyByteBuf buf) {
        return new WingsPacket(buf.readUUID(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(WingsPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RhalixWingsClient.set(m.player, m.out, m.animate));
    }
}
