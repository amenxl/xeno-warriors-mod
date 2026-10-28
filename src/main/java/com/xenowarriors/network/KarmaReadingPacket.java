package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the Karma Level of the entity you're looking at (-1 = nothing), how you took it, and whether the visor just broke. */
public record KarmaReadingPacket(int entity, long value, int mode, boolean broke) {
    public static void encode(KarmaReadingPacket m, FriendlyByteBuf b) { b.writeVarInt(m.entity); b.writeVarLong(m.value); b.writeVarInt(m.mode + 1); b.writeBoolean(m.broke); }
    public static KarmaReadingPacket decode(FriendlyByteBuf b) { return new KarmaReadingPacket(b.readVarInt(), b.readVarLong(), b.readVarInt() - 1, b.readBoolean()); }
    public static void handle(KarmaReadingPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.KarmaScanClient.receive(m.entity, m.value, m.mode, m.broke));
    }
}
