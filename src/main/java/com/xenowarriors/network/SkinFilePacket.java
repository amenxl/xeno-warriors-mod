package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A skin file for your own characters (see you.YouSkins). Client -> server: an upload (data) or a request for one
 * (data empty). Server -> client: the file (data), or empty when the server hasn't got it.
 */
public record SkinFilePacket(String sha, byte[] data) {
    public static void encode(SkinFilePacket m, FriendlyByteBuf b) {
        b.writeUtf(m.sha, 40);
        b.writeByteArray(m.data);
    }
    public static SkinFilePacket decode(FriendlyByteBuf b) {
        return new SkinFilePacket(b.readUtf(40), b.readByteArray(com.xenowarriors.you.YouSkins.MAX_BYTES));
    }
    public static void handle(SkinFilePacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) {
            if (m.data.length > 0) {                                                  // an upload
                String why = com.xenowarriors.you.YouSkins.upload(p, m.data);
                if (why != null) p.displayClientMessage(net.minecraft.network.chat.Component.literal(why).withStyle(net.minecraft.ChatFormatting.RED), false);
            } else {                                                                  // someone wants to draw it
                byte[] f = com.xenowarriors.you.YouSkins.read(p.server, m.sha);
                ModNetwork.sendToPlayer(new SkinFilePacket(m.sha, f == null ? new byte[0] : f), p);
            }
        } else DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.you.YouSkinClient.received(m.sha, m.data));
        ctx.get().setPacketHandled(true);
    }
}
