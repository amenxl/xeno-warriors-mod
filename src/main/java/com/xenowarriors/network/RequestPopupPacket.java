package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: show a request card (party invite, battle challenge, team ultimate) with accept/decline keys. */
public record RequestPopupPacket(String title, String text, String accept, String decline, int ticks, int color) {
    public static void encode(RequestPopupPacket m, FriendlyByteBuf buf) {
        buf.writeUtf(m.title);
        buf.writeUtf(m.text);
        buf.writeUtf(m.accept);
        buf.writeUtf(m.decline);
        buf.writeVarInt(m.ticks);
        buf.writeInt(m.color);
    }

    public static RequestPopupPacket decode(FriendlyByteBuf buf) {
        return new RequestPopupPacket(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readInt());
    }

    /**
     * Tells the client a request was answered some other way (its chat button, a typed command, the ultimate key), so
     * the card showing it goes instead of hanging on until it times out. {@code command} is the accept or decline
     * command, as on the card ("/xeno party accept").
     */
    public static void dismiss(net.minecraft.server.level.ServerPlayer p, String command) {
        ModNetwork.sendToPlayer(new RequestPopupPacket("", "", command, "", 0, 0), p);
    }

    public static void handle(RequestPopupPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.RequestPopups.show(m));
        ctx.get().setPacketHandled(true);
    }
}
