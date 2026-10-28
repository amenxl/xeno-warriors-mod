package com.xenowarriors.you;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.Race;
import net.minecraft.world.entity.player.Player;

import java.util.function.Function;

/** Side-neutral lookups for You characters: works on the server (capability) and the client (synced copy). */
public final class You {
    private You() {}

    /** Set by the client: how to read a player's synced You slot there. */
    public static Function<Player, YouSlot> clientSlot = p -> null;

    public static boolean is(Player p) { return ModCapabilities.getType(p) == CharacterType.YOU || (p.level().isClientSide && clientIs(p)); }

    private static boolean clientIs(Player p) { YouSlot s = clientSlot.apply(p); return s != null && s.exists(); }

    /** The You character this player is playing, or null when they're a show character. */
    public static YouSlot slot(Player p) {
        if (p.level().isClientSide) { YouSlot s = clientSlot.apply(p); return s != null && s.exists() ? s : null; }
        if (ModCapabilities.getType(p) != CharacterType.YOU) return null;
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.you()).orElse(null);
    }

    /** This player's race (their You character's, or their show character's). */
    public static Race race(Player p) {
        YouSlot s = slot(p);
        if (s != null) return s.race;
        return Race.of(p.level().isClientSide ? clientType(p) : ModCapabilities.getType(p));
    }

    /** Set by the client: the synced character type. */
    public static Function<Player, CharacterType> clientType = p -> CharacterType.NONE;

    /** What to call this player's character: the You character's name, or the show character's. */
    public static String name(Player p) {
        YouSlot s = slot(p);
        if (s != null) return s.name;
        CharacterType t = p.level().isClientSide ? clientType.apply(p) : ModCapabilities.getType(p);
        return t.getDisplayName().getString();
    }

    /** The character's colour (element colour for You characters). */
    public static int color(Player p) {
        YouSlot s = slot(p);
        if (s != null) return 0xFF000000 | s.color();
        return (p.level().isClientSide ? clientType.apply(p) : ModCapabilities.getType(p)).getColor();
    }

    /** Wings right now: a winged race, and not a demon hiding in human form. */
    public static boolean wings(Player p) {
        YouSlot s = slot(p);
        return s != null && s.race.wings() && !(s.race == Race.DEMON && s.humanForm);
    }
}
