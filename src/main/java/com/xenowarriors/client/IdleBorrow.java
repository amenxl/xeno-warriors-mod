package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Borrowed idles (see YouIdles): a character you made that picked a show character's idle (bond 100) plays that
 * character's idle - only the idle, never their fighting, flying or other moves.
 */
public final class IdleBorrow {
    private IdleBorrow() {}

    /** This player is a made character using {@code owner}'s idle. */
    public static boolean borrows(Player p, CharacterType owner) {
        if (!(p instanceof AbstractClientPlayer)) return false;
        var s = ClientCharacterData.you(p);
        return s != null && owner.getId().equals(s.idle);
    }

    /** Plays {@code owner}'s idles: is them, or borrows theirs. */
    public static boolean idlesAs(Player p, CharacterType owner) {
        return p instanceof AbstractClientPlayer a && (ClientCharacterData.get(a) == owner || borrows(p, owner));
    }

    /** Whose idles this player plays: their own character's, or the one their made character borrowed. */
    public static CharacterType idleType(Player p) {
        if (!(p instanceof AbstractClientPlayer a)) return CharacterType.NONE;
        CharacterType t = ClientCharacterData.get(a);
        if (t != CharacterType.YOU) return t;
        var s = ClientCharacterData.you(p);
        return s == null || s.idle.isEmpty() ? t : CharacterType.byId(s.idle);
    }
}
