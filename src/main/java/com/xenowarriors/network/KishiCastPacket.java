package com.xenowarriors.network;

import com.xenowarriors.client.ClientSkillState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: a player just cast (or recast) Kishi, so play the cast animation. */
public record KishiCastPacket(int entityId, boolean airborne) {
    public static void encode(KishiCastPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.airborne);
    }

    public static KishiCastPacket decode(FriendlyByteBuf buf) {
        return new KishiCastPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(KishiCastPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientSkillState.startKishiCast(msg.entityId, msg.airborne));
    }
}
