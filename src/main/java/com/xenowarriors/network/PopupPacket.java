package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A pop-up for the left-side feed (see client PopupFeed): who it's about (a player, bot or pet - shown as a picture),
 * a title, a line of text and a style. pet: -1 not a pet, 0 fox, 1 winged companion.
 */
public record PopupPacket(int style, UUID subject, int type, int pet, String title, String text, String face) {
    public static final int INFO = 0, DANGER = 1, REQUEST = 2, GOOD = 3, PET = 4;

    public static void send(ServerPlayer to, int style, UUID subject, int type, int pet, String title, String text, String face) {
        ModNetwork.sendToPlayer(new PopupPacket(style, subject, type, pet, title, text, face), to);
    }

    /** To everyone else in this player's party who's nearby (same world, within 96 blocks). */
    public static void toParty(ServerPlayer from, int style, String title, String text, String face) {
        int type = com.xenowarriors.character.ModCapabilities.getType(from).ordinal();
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(from))
            if (m != from && m.level() == from.level() && m.distanceTo(from) < 96) send(m, style, from.getUUID(), type, -1, title, text, face);
    }

    public static void encode(PopupPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.style); b.writeUUID(m.subject); b.writeVarInt(m.type); b.writeVarInt(m.pet + 1);
        b.writeUtf(m.title, 64); b.writeUtf(m.text, 160); b.writeUtf(m.face, 24);
    }

    public static PopupPacket decode(FriendlyByteBuf b) {
        return new PopupPacket(b.readVarInt(), b.readUUID(), b.readVarInt(), b.readVarInt() - 1, b.readUtf(64), b.readUtf(160), b.readUtf(24));
    }

    public static void handle(PopupPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PopupFeed.push(m.style, m.subject, m.type, m.pet, m.title, m.text, m.face));
    }
}
