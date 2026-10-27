package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: your Money balance. */
public record MoneyPacket(long money) {
    public static void encode(MoneyPacket m, FriendlyByteBuf b) { b.writeVarLong(m.money); }
    public static MoneyPacket decode(FriendlyByteBuf b) { return new MoneyPacket(b.readVarLong()); }
    public static void handle(MoneyPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.ClientMoney.set(m.money));
    }
}
