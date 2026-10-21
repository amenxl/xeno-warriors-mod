package com.xenowarriors.network;

import com.xenowarriors.client.ClientSkillState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: a Mikiko's Netsu started (ticks > 0) or ended (0), for his flame aura. */
public record NetsuPacket(int entityId, int ticks) {
    public static void encode(NetsuPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.ticks);
    }

    public static NetsuPacket decode(FriendlyByteBuf buf) {
        return new NetsuPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(NetsuPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSkillState.setNetsu(msg.entityId, msg.ticks));
    }
}
