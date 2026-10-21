package com.xenowarriors.network;

import com.xenowarriors.client.fox.FoxClientFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: a pet fox animation (level-up spin, happy greeting, running away). */
public record FoxFxPacket(int entityId, int kind) {
    public static final int LEVEL_UP = 0, GREET = 1, RUN_AWAY = 2;

    public static void encode(FoxFxPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeVarInt(m.kind);
    }

    public static FoxFxPacket decode(FriendlyByteBuf buf) {
        return new FoxFxPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(FoxFxPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FoxClientFx.play(m.entityId, m.kind));
    }
}
