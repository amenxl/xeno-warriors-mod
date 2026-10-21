package com.xenowarriors.network;

import com.xenowarriors.client.ClientSkillState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: your current and max Karma, for the HUD. */
public record KarmaSyncPacket(float karma, float max) {
    public static void encode(KarmaSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.karma);
        buf.writeFloat(msg.max);
    }

    public static KarmaSyncPacket decode(FriendlyByteBuf buf) {
        return new KarmaSyncPacket(buf.readFloat(), buf.readFloat());
    }

    public static void handle(KarmaSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSkillState.setKarma(msg.karma, msg.max));
    }
}
