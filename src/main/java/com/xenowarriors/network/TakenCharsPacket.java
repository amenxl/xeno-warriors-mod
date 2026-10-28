package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Both ways: the client asks (empty); the server answers who's playing each character (by ordinal, "" = free) and whether any are free. */
public record TakenCharsPacket(String[] names, boolean anyFree) {
    public static String[] CLIENT = new String[0];
    public static boolean CLIENT_ANY_FREE = true;

    public static void encode(TakenCharsPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.names.length);
        for (String s : m.names) b.writeUtf(s, 64);
        b.writeBoolean(m.anyFree);
    }
    public static TakenCharsPacket decode(FriendlyByteBuf b) {
        String[] n = new String[Math.min(256, b.readVarInt())];
        for (int i = 0; i < n.length; i++) n[i] = b.readUtf(64);
        return new TakenCharsPacket(n, b.readBoolean());
    }
    public static void handle(TakenCharsPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) ModNetwork.sendToPlayer(new TakenCharsPacket(com.xenowarriors.character.CharacterLock.names(p), com.xenowarriors.character.CharacterLock.anyFree(p)), p);
        else { CLIENT = m.names; CLIENT_ANY_FREE = m.anyFree; }
        ctx.get().setPacketHandled(true);
    }

    /** Client: who's playing this character ("" if nobody else). */
    public static String who(com.xenowarriors.character.CharacterType t) {
        return t.ordinal() < CLIENT.length ? CLIENT[t.ordinal()] : "";
    }
}
