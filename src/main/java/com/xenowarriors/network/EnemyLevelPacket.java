package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: an enemy's level (shown on the lock-on bar). */
public record EnemyLevelPacket(int entityId, int level) {
    public static void encode(EnemyLevelPacket m, FriendlyByteBuf b) { b.writeVarInt(m.entityId); b.writeVarInt(m.level); }

    public static EnemyLevelPacket decode(FriendlyByteBuf b) { return new EnemyLevelPacket(b.readVarInt(), b.readVarInt()); }

    public static void handle(EnemyLevelPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.LockOn.LEVELS.put(m.entityId, m.level));
        ctx.get().setPacketHandled(true);
    }
}
