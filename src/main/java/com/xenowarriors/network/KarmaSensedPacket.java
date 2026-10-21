package com.xenowarriors.network;

import com.xenowarriors.client.KarmaSense;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the entities you sensed, and for how long they glow. */
public record KarmaSensedPacket(int[] ids, int duration) {
    public static void encode(KarmaSensedPacket msg, FriendlyByteBuf buf) {
        buf.writeVarIntArray(msg.ids);
        buf.writeVarInt(msg.duration);
    }

    public static KarmaSensedPacket decode(FriendlyByteBuf buf) {
        return new KarmaSensedPacket(buf.readVarIntArray(), buf.readVarInt());
    }

    public static void handle(KarmaSensedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> KarmaSense.sensed(msg.ids, msg.duration));
    }
}
