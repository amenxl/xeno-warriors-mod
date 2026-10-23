package com.xenowarriors.network;

import com.xenowarriors.client.KarmaSense;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the entities you sensed, and for how long they glow. */
public record KarmaSensedPacket(int[] ids, int[] levels, int duration, boolean showLevels, int[] souls, int[] damage, int[] armor, boolean first, float radius) {
    public static void encode(KarmaSensedPacket msg, FriendlyByteBuf buf) {
        buf.writeVarIntArray(msg.ids);
        buf.writeVarIntArray(msg.levels);
        buf.writeVarInt(msg.duration);
        buf.writeBoolean(msg.showLevels);
        buf.writeVarIntArray(msg.souls);
        buf.writeVarIntArray(msg.damage);
        buf.writeVarIntArray(msg.armor);
        buf.writeBoolean(msg.first);
        buf.writeFloat(msg.radius);
    }

    public static KarmaSensedPacket decode(FriendlyByteBuf buf) {
        return new KarmaSensedPacket(buf.readVarIntArray(), buf.readVarIntArray(), buf.readVarInt(), buf.readBoolean(), buf.readVarIntArray(),
                buf.readVarIntArray(), buf.readVarIntArray(), buf.readBoolean(), buf.readFloat());
    }

    public static void handle(KarmaSensedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> KarmaSense.sensed(msg.ids, msg.levels, msg.duration, msg.showLevels, msg.souls, msg.damage, msg.armor, msg.first));
    }
}
