package com.xenowarriors.network;

import com.xenowarriors.client.mikiko.MikikoAnimations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: play the Hekublade summon effect on a player. */
public record HekubladeFxPacket(int entityId) {
    public static void encode(HekubladeFxPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
    }

    public static HekubladeFxPacket decode(FriendlyByteBuf buf) {
        return new HekubladeFxPacket(buf.readVarInt());
    }

    public static void handle(HekubladeFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MikikoAnimations.summonFx(msg.entityId));
    }
}
