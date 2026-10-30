package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side copy of which character every visible player is using. */
public final class ClientCharacterData {
    private static final Map<UUID, CharacterType> CHARACTERS = new ConcurrentHashMap<>();
    private static CharacterType preview = null;
    private static final Map<UUID, Integer> OUTFITS = new ConcurrentHashMap<>();
    private static final Map<UUID, com.xenowarriors.you.YouSlot> YOU = new ConcurrentHashMap<>();

    public static void setYou(UUID playerId, com.xenowarriors.you.YouSlot slot) {
        if (slot == null) YOU.remove(playerId); else YOU.put(playerId, slot);
    }

    /** The You character this player is playing, or null. */
    public static com.xenowarriors.you.YouSlot you(Player player) {
        return get(player) == CharacterType.YOU ? YOU.get(player.getUUID()) : null;
    }

    /** Wings: a winged show character, or a winged You race (not a demon in human form). */
    public static boolean winged(Player player) {
        CharacterType t = get(player);
        if (t != CharacterType.YOU) return t.skylander();
        var s = YOU.get(player.getUUID());
        return s != null && s.race.wings() && !(s.race == com.xenowarriors.character.Race.DEMON && s.humanForm);
    }

    /** What to call this player's character on screen. */
    public static String name(Player player) {
        var s = you(player);
        return s != null ? s.name : get(player).getDisplayName().getString();
    }

    /** The character's colour (a You character's element colour). */
    public static int color(Player player) {
        var s = you(player);
        return s != null ? 0xFF000000 | s.color() : get(player).getColor();
    }
    public static final int RECARNET_1 = 0, ORIGINS_1 = 1, ORIGINS_2 = 2, SEASON_3 = 3, SEASON_3_TIED = 4;

    public static void setOutfit(UUID playerId, int outfit) {
        if (outfit <= 0) OUTFITS.remove(playerId);
        else OUTFITS.put(playerId, outfit);
    }

    /** Which outfit this player's character is wearing (0 = Recarnet 1, the normal one). */
    public static int outfit(Player player) {
        CharacterType t = get(player);
        if (t == CharacterType.NONE) return 0;
        int o = OUTFITS.getOrDefault(player.getUUID(), 0);
        return o < t.outfitKeys().length ? o : 0;
    }

    /** Mikiko's outfit number (constants above), 0 for anyone else. */
    public static int mikikoOutfit(Player player) {
        return get(player) == CharacterType.MIKIKO ? outfit(player) : 0;
    }

    /** Rhalix's outfit: 0 normal, 1 Origins 1, 2 Origins 2, 3 Recarnet Season 3; 0 for anyone else. */
    public static int rhalixOutfit(Player player) {
        return get(player) == CharacterType.RHALIX ? outfit(player) : 0;
    }

    public static final int RHALIX_ORIGINS_1 = 1, RHALIX_ORIGINS_2 = 2, RHALIX_SEASON_3 = 3;

    /** Texture/model suffix of this player's outfit ("" = normal look). */
    public static String outfitKey(Player player) {
        return get(player).outfitKeys()[outfit(player)];
    }

    private ClientCharacterData() {}

    public static void set(UUID playerId, CharacterType type) {
        if (type == CharacterType.NONE) CHARACTERS.remove(playerId);
        else CHARACTERS.put(playerId, type);
    }

    public static CharacterType get(Player player) {
        if (preview != null && player == Minecraft.getInstance().player) return preview;
        if (com.xenowarriors.client.compat.ReplayCompat.isReplayCamera(player)) return CharacterType.NONE;
        return CHARACTERS.getOrDefault(player.getUUID(), CharacterType.NONE);
    }

    public static CharacterType getLocal() {
        Player player = Minecraft.getInstance().player;
        return player == null ? CharacterType.NONE : CHARACTERS.getOrDefault(player.getUUID(), CharacterType.NONE);
    }

    /** Used by the selection screen to draw the local player as each character. */
    public static void beginPreview(CharacterType type) { preview = type; }
    public static void endPreview() { preview = null; }
    public static boolean isPreviewing() { return preview != null; }

    /** Set while a small head-and-shoulders card is drawn: a plain standing pose, no idles (Mikiko sitting down hid his face). */
    public static boolean standStill;

    public static void clear() {
        CHARACTERS.clear();
        OUTFITS.clear();
        YOU.clear();
        preview = null;
    }
}
