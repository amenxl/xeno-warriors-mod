package com.xenowarriors.character;

import net.minecraft.server.level.ServerPlayer;

/**
 * One character per player: you can't pick a character someone else on the server is already playing - unless every
 * character you could pick is taken, then doubling up is allowed so nobody gets stuck.
 */
public final class CharacterLock {
    private CharacterLock() {}

    /** Another (real) player on the server playing this character, or null. */
    public static ServerPlayer takenBy(ServerPlayer asker, CharacterType t) {
        if (t == CharacterType.NONE) return null;
        for (ServerPlayer o : asker.server.getPlayerList().getPlayers())
            if (o != asker && ModCapabilities.getType(o) == t && !com.xenowarriors.journey.Journey.inRun(o)) return o;   // (journey characters don't count)
        return null;
    }

    /** Whether there's any character this player could pick that nobody else is playing. */
    public static boolean anyFree(ServerPlayer asker) {
        for (CharacterType t : CharacterType.values()) {
            if (t == CharacterType.NONE || !Seasons.unlocked(asker.server, t.season())) continue;
            if (takenBy(asker, t) == null) return true;
        }
        return false;
    }

    /** Who's playing each character (by ordinal; "" = free), for the select screen. */
    public static String[] names(ServerPlayer asker) {
        CharacterType[] all = CharacterType.values();
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            ServerPlayer o = takenBy(asker, all[i]);
            out[i] = o == null ? "" : o.getGameProfile().getName();
        }
        return out;
    }
}
