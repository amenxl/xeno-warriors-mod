package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server -> client: whose pet this is (foxes, wing pets, tamed animals) - so lock-on skips yours and your party's. */
public record PetOwnerPacket(int entityId, UUID owner) {
    public static void encode(PetOwnerPacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.entityId); buf.writeUUID(m.owner); }

    public static PetOwnerPacket decode(FriendlyByteBuf buf) { return new PetOwnerPacket(buf.readVarInt(), buf.readUUID()); }

    public static void handle(PetOwnerPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PetOwners.set(m.entityId, m.owner));
    }
}
