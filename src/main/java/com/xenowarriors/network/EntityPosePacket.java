package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: an entity strikes a pose (see anim/Poses) for some ticks. */
public record EntityPosePacket(int entityId, int pose, int ticks) {
    public static void encode(EntityPosePacket m, FriendlyByteBuf b) { b.writeVarInt(m.entityId); b.writeVarInt(m.pose); b.writeVarInt(m.ticks); }

    public static EntityPosePacket decode(FriendlyByteBuf b) { return new EntityPosePacket(b.readVarInt(), b.readVarInt(), b.readVarInt()); }

    public static void handle(EntityPosePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.EntityPoses.set(m.entityId, m.pose, m.ticks));
        ctx.get().setPacketHandled(true);
    }
}
