package com.xenowarriors.client.you;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkinFilePacket;
import com.xenowarriors.you.YouSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drawing your own characters in their own skins (see you.YouSkins): a player's skin looked up by name, or a skin file
 * - from your skins folder ({@code <game folder>/xenowarriors/skins}) or, someone else's, fetched from the server once.
 * Until a skin has arrived (or if it can't), the character wears its owner's skin.
 */
public final class YouSkinClient {
    private YouSkinClient() {}

    /** A loaded skin: its texture and whether it's the slim-armed model. */
    public record Look(ResourceLocation tex, boolean slim) {}

    private static final Map<String, Look> READY = new ConcurrentHashMap<>();
    /** Looked up / asked for already (each only once a session; a failed one stays on the fallback). */
    private static final Set<String> ASKED = ConcurrentHashMap.newKeySet();
    /** (Tests) what was looked up / received. */
    public static int namesLoaded, filesLoaded;
    public static String lastProblem = "";

    public static Path folder() { return Minecraft.getInstance().gameDirectory.toPath().resolve(XenoWarriors.MOD_ID).resolve("skins"); }

    /** This skin's look, or null while it's loading (or for "", your own skin). */
    public static Look look(String skin) {
        if (skin == null || skin.isEmpty() || !YouSkins.valid(skin)) return null;
        Look l = READY.get(skin);
        if (l != null) return l;
        if (ASKED.add(skin)) {
            try {
                if (skin.startsWith("p:")) byName(skin, skin.substring(2));
                else byFile(skin, skin.substring(2));
            } catch (Throwable t) { lastProblem = skin + ": " + t; XenoWarriors.LOGGER.warn("Couldn't load the skin {}", skin, t); }
        }
        return null;
    }

    private static void byName(String key, String name) {
        net.minecraft.world.level.block.entity.SkullBlockEntity.updateGameprofile(new GameProfile(null, name), profile -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (profile == null || profile.getId() == null) { lastProblem = "no player called " + name; return; }
                mc.getSkinManager().registerSkins(profile, (type, loc, tex) -> {
                    if (type != MinecraftProfileTexture.Type.SKIN) return;
                    READY.put(key, new Look(loc, "slim".equals(tex.getMetadata("model"))));
                    namesLoaded++;
                }, true);
            });
        });
    }

    private static void byFile(String key, String sha) {
        byte[] local = localFile(sha);
        if (local != null) { make(key, local); return; }
        ModNetwork.sendToServer(new SkinFilePacket(sha, new byte[0]));                      // the server has it (someone uploaded it)
    }

    /** The server's answer to a request. */
    public static void received(String sha, byte[] data) {
        if (data.length == 0) { lastProblem = "the server hasn't got skin " + sha; return; }
        if (!YouSkins.sha1(data).equals(sha)) { lastProblem = "skin " + sha + " didn't match"; return; }
        Minecraft.getInstance().execute(() -> make("f:" + sha, data));
    }

    private static void make(String key, byte[] data) {
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(data));
            if (img.getWidth() != 64 || img.getHeight() != 64) { img.close(); lastProblem = key + " isn't 64x64"; return; }
            boolean slim = (img.getPixelRGBA(54, 20) >>> 24) == 0;                       // (slim arms: the right arm is 3 pixels wide)
            ResourceLocation loc = Minecraft.getInstance().getTextureManager().register("xw_youskin_" + key.substring(2, Math.min(key.length(), 14)).toLowerCase(Locale.ROOT), new DynamicTexture(img));
            READY.put(key, new Look(loc, slim));
            filesLoaded++;
        } catch (Throwable t) { lastProblem = key + ": " + t; }
    }

    // ---------------------------------------------------------------- your skins folder

    /** A skin file in your skins folder: its name, sha1 and bytes. */
    public record LocalSkin(String name, String sha, byte[] data, String problem) {}

    /** The PNGs in your skins folder (made if it isn't there). */
    public static List<LocalSkin> localSkins() {
        List<LocalSkin> out = new ArrayList<>();
        try {
            Path dir = folder();
            Files.createDirectories(dir);
            try (var s = Files.list(dir)) {
                for (Path f : s.sorted().toList()) {
                    String n = f.getFileName().toString();
                    if (!n.toLowerCase(Locale.ROOT).endsWith(".png") || !Files.isRegularFile(f)) continue;
                    byte[] b = Files.size(f) > YouSkins.MAX_BYTES * 4L ? new byte[0] : Files.readAllBytes(f);
                    String why = YouSkins.check(b);
                    out.add(new LocalSkin(n.substring(0, n.length() - 4), why == null ? YouSkins.sha1(b) : "", b, why));
                    if (out.size() >= 40) break;
                }
            }
        } catch (Exception e) { lastProblem = "skins folder: " + e; }
        return out;
    }

    private static byte[] localFile(String sha) {
        for (LocalSkin s : localSkins()) if (s.sha().equals(sha)) return s.data();
        return null;
    }

    /** Sends one of your skin files to the server and gives it to a slot (or, slot -1, just uploads it). Returns the skin key. */
    public static String use(LocalSkin s) {
        ModNetwork.sendToServer(new SkinFilePacket(s.sha(), s.data()));
        make("f:" + s.sha(), s.data());
        return "f:" + s.sha();
    }

    /** Opens the skins folder in your file browser. */
    public static void openFolder() {
        try { Files.createDirectories(folder()); net.minecraft.Util.getPlatform().openFile(folder().toFile()); } catch (Exception ignored) {}
    }
}
