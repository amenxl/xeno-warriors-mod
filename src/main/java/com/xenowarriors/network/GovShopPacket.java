package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: open the Gov terminal (your Money, your discount %). Client -> server: buy offer `buy`. */
public record GovShopPacket(long money, int discount, int buy) {
    public static void encode(GovShopPacket m, FriendlyByteBuf b) { b.writeVarLong(m.money); b.writeVarInt(m.discount); b.writeVarInt(m.buy + 1); }
    public static GovShopPacket decode(FriendlyByteBuf b) { return new GovShopPacket(b.readVarLong(), b.readVarInt(), b.readVarInt() - 1); }
    public static void handle(GovShopPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.gov.GovShop.buy(p, m.buy);
        else DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.screen.GovShopScreen.open(m.money, m.discount));
        ctx.get().setPacketHandled(true);
    }
}
