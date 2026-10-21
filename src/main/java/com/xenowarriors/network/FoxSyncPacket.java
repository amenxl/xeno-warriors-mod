package com.xenowarriors.network;

import com.xenowarriors.client.fox.FoxClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Server -> owner: your foxes (id -> name), and optionally "name this one" right after taming it. */
public record FoxSyncPacket(Map<UUID, String> foxes, UUID prompt) {
    public static void encode(FoxSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeMap(msg.foxes, FriendlyByteBuf::writeUUID, FriendlyByteBuf::writeUtf);
        buf.writeBoolean(msg.prompt != null);
        if (msg.prompt != null) buf.writeUUID(msg.prompt);
    }

    public static FoxSyncPacket decode(FriendlyByteBuf buf) {
        Map<UUID, String> foxes = buf.readMap(HashMap::new, FriendlyByteBuf::readUUID, FriendlyByteBuf::readUtf);
        return new FoxSyncPacket(foxes, buf.readBoolean() ? buf.readUUID() : null);
    }

    public static void handle(FoxSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FoxClient.sync(msg.foxes, msg.prompt));
    }
}
