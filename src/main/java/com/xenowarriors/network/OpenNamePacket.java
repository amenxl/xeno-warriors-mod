package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server -> owner: open the naming box for one of Rhalix's winged companions. */
public record OpenNamePacket(UUID pet, String name, boolean justTamed) {
    public static void encode(OpenNamePacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.pet);
        buf.writeUtf(m.name);
        buf.writeBoolean(m.justTamed);
    }

    public static OpenNamePacket decode(FriendlyByteBuf buf) {
        return new OpenNamePacket(buf.readUUID(), buf.readUtf(), buf.readBoolean());
    }

    public static void handle(OpenNamePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PacketScreens.openName(m.pet, m.name, m.justTamed));
    }
}
