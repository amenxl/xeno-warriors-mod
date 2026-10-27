package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Both ways: client asks (empty) for its level with every character; the server answers with them, by CharacterType ordinal. */
public record CharLevelsPacket(int[] levels) {
    public static final int[] CLIENT = new int[64];

    public static void encode(CharLevelsPacket m, FriendlyByteBuf b) { b.writeVarIntArray(m.levels); }
    public static CharLevelsPacket decode(FriendlyByteBuf b) { return new CharLevelsPacket(b.readVarIntArray()); }
    public static void handle(CharLevelsPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) {                                       // a request: answer it
            com.xenowarriors.character.CharacterType[] all = com.xenowarriors.character.CharacterType.values();
            int[] out = new int[all.length];
            for (int i = 0; i < all.length; i++) out[i] = all[i] == com.xenowarriors.character.CharacterType.NONE ? 0 : com.xenowarriors.bot.PartyBots.levelFor(p, all[i]);
            ModNetwork.sendToPlayer(new CharLevelsPacket(out), p);
        } else {
            System.arraycopy(m.levels, 0, CLIENT, 0, Math.min(CLIENT.length, m.levels.length));
        }
        ctx.get().setPacketHandled(true);
    }
}
