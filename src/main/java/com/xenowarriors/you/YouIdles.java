package com.xenowarriors.you;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.PlayerCharacter;
import net.minecraft.world.entity.player.Player;

/**
 * Borrowed idles: a character you made can use a show character's idle animation once its bond with them reaches 100
 * (picked on the Outfits tab of Character Customization). One at a time, saved per character (YouSlot.idle).
 * Characters with an idle of their own are listed here; more join as they get idles.
 */
public final class YouIdles {
    public static final int NEED = 100;
    /** Whose idles can be borrowed (only characters that have one). */
    public static final CharacterType[] OWNERS = {CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.GARRY, CharacterType.KEITA, CharacterType.YURI, CharacterType.KAERU};

    private YouIdles() {}

    /** What each idle is (for the menu). */
    public static String what(CharacterType t) {
        return switch (t) {
            case MIKIKO -> "Stretches, sits with a fire in his palm, dances to songs";
            case RHALIX -> "Pulls out a book and reads";
            case GARRY -> "Beatboxes into a mic";
            case KEITA -> "Nods off on their feet";
            case YURI -> "Opens a holo laptop and types";
            case KAERU -> "Stretches: arms over her head, side to side, arms across";
            default -> "";
        };
    }

    public static boolean owner(String id) {
        for (CharacterType t : OWNERS) if (t.getId().equals(id)) return true;
        return false;
    }

    /** That made character's bond with this idle's owner (0-100). */
    public static int bond(Player p, int slot, CharacterType owner) {
        return com.xenowarriors.party.Bonds.levelAs(p, PlayerCharacter.key(CharacterType.YOU, slot), owner.getId());
    }

    /** Can that made character use this idle? ("" - none - always.) */
    public static boolean allowed(Player p, int slot, String id) {
        if (id == null || id.isEmpty()) return true;
        if (!owner(id)) return false;
        return bond(p, slot, CharacterType.byId(id)) >= NEED;
    }
}
