package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: your PvP battle opponent (entity id), to lock on to as it starts; -1 = the battle is over. */
public record DuelFoePacket(int foe) {
    public static void encode(DuelFoePacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.foe); }

    public static DuelFoePacket decode(FriendlyByteBuf buf) { return new DuelFoePacket(buf.readVarInt()); }

    public static void handle(DuelFoePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.LockOn.duelFoe(m.foe));
    }
}
