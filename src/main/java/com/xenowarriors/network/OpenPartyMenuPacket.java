package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> player: open the party menu (members, bot modes, add bots). */
public record OpenPartyMenuPacket() {
    public static void encode(OpenPartyMenuPacket m, FriendlyByteBuf b) {}
    public static OpenPartyMenuPacket decode(FriendlyByteBuf b) { return new OpenPartyMenuPacket(); }
    public static void handle(OpenPartyMenuPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PacketScreens.openPartyMenu());
        ctx.get().setPacketHandled(true);
    }
}
