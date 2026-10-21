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

    private ClientCharacterData() {}

    public static void set(UUID playerId, CharacterType type) {
        if (type == CharacterType.NONE) CHARACTERS.remove(playerId);
        else CHARACTERS.put(playerId, type);
    }

    public static CharacterType get(Player player) {
        if (preview != null && player == Minecraft.getInstance().player) return preview;
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
        preview = null;
    }
}
