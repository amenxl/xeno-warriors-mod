package com.xenowarriors.you;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Your own characters' own skins. A slot's {@code skin} is "" (your own skin), "p:&lt;name&gt;" (that Minecraft player's
 * skin - each client looks it up itself) or "f:&lt;sha1&gt;" (a skin file you added: uploaded once, kept in the world's
 * save under xenowarriors/skins, and handed to every client that needs to draw it).
 */
public final class YouSkins {
    private YouSkins() {}

    /** Biggest skin file we take (a 64x64 skin is usually 1-8 KB; a client->server packet tops out near 32 KB). */
    public static final int MAX_BYTES = 30000;

    public static boolean valid(String s) {
        if (s == null || s.isEmpty()) return true;
        if (s.startsWith("p:")) return validName(s.substring(2));
        if (s.startsWith("f:")) return s.length() == 42 && s.substring(2).matches("[0-9a-f]{40}");
        return false;
    }

    public static boolean validName(String n) { return n != null && n.matches("[A-Za-z0-9_]{3,16}"); }

    public static String sha1(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** A PNG that's a Minecraft skin (64x64), small enough. Null if fine, else why not. */
    public static String check(byte[] data) {
        if (data == null || data.length < 8) return "That isn't a PNG file";
        if (data.length > MAX_BYTES) return "That skin file is too big (" + data.length / 1024 + " KB; up to " + MAX_BYTES / 1024 + " KB)";
        if ((data[0] & 0xFF) != 0x89 || data[1] != 'P' || data[2] != 'N' || data[3] != 'G') return "That isn't a PNG file";
        try {
            var img = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
            if (img == null) return "That PNG couldn't be read";
            if (img.getWidth() != 64 || img.getHeight() != 64) return "A skin is 64x64 pixels (that one is " + img.getWidth() + "x" + img.getHeight() + ")";
        } catch (Throwable t) { return "That PNG couldn't be read"; }
        return null;
    }

    private static Path dir(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve(XenoWarriors.MOD_ID).resolve("skins"); }

    public static Path file(MinecraftServer s, String sha) { return dir(s).resolve(sha + ".png"); }

    public static boolean has(MinecraftServer s, String sha) { return sha.matches("[0-9a-f]{40}") && Files.isRegularFile(file(s, sha)); }

    /** Uploads per player in a row, so nobody floods the save. */
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();
    /** (Tests) how many were stored. */
    public static int stored;

    /** A client uploaded a skin file: kept if it's a real skin. Null if stored (or there already), else why not. */
    public static String upload(ServerPlayer p, byte[] data) {
        String why = check(data);
        if (why != null) return why;
        long now = System.currentTimeMillis();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < 1500) return "Wait a moment before adding another skin";
        LAST.put(p.getUUID(), now);
        String sha = sha1(data);
        try {
            Path f = file(p.server, sha);
            if (!Files.isRegularFile(f)) { Files.createDirectories(f.getParent()); Files.write(f, data); stored++; }
        } catch (Exception e) {
            XenoWarriors.LOGGER.warn("Couldn't keep a skin file", e);
            return "The skin couldn't be saved on the server";
        }
        return null;
    }

    /** The stored file, or null. */
    public static byte[] read(MinecraftServer s, String sha) {
        if (!has(s, sha)) return null;
        try { byte[] b = Files.readAllBytes(file(s, sha)); return b.length <= MAX_BYTES ? b : null; } catch (Exception e) { return null; }
    }

    /** A skin a slot may be given: a name, "" or a file the server has. */
    public static boolean usable(MinecraftServer s, String skin) {
        if (!valid(skin)) return false;
        return !skin.startsWith("f:") || has(s, skin.substring(2));
    }

    /** "your skin", "Notch's skin", "a skin file". */
    public static String describe(String skin) {
        if (skin == null || skin.isEmpty()) return "your skin";
        if (skin.startsWith("p:")) return skin.substring(2) + "'s skin";
        return "a skin file";
    }
}
