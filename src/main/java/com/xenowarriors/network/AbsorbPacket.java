package com.xenowarriors.network;

import com.xenowarriors.client.mikiko.BladeAbsorbClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: this mob was defeated by Mikiko and gets pulled into his Hekublade. */
public record AbsorbPacket(int victim, int killer) {
    public static void encode(AbsorbPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.victim);
        buf.writeVarInt(msg.killer);
    }

    public static AbsorbPacket decode(FriendlyByteBuf buf) {
        return new AbsorbPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(AbsorbPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BladeAbsorbClient.start(msg.victim, msg.killer));
    }
}
