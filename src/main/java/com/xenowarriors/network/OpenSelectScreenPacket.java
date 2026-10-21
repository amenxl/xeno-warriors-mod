package com.xenowarriors.network;

import com.xenowarriors.client.ClientEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: "Show the character selection screen." */
public record OpenSelectScreenPacket() {
    public static void encode(OpenSelectScreenPacket msg, FriendlyByteBuf buf) {}

    public static OpenSelectScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenSelectScreenPacket();
    }

    public static void handle(OpenSelectScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientEvents::requestSelectScreen);
    }
}
