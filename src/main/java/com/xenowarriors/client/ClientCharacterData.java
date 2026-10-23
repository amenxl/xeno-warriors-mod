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

    public static void clear() {
        CHARACTERS.clear();
        OUTFITS.clear();
        preview = null;
    }
}
