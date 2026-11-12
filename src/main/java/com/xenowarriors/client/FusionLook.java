package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Fusion looks (original designs, see tools/xw/fusion/fusion_looks.py): the leader's own model in a mix of both
 * characters' colours - its skin and detail sheet recoloured, its hair tinted. Previewed for now (the tests), to be
 * picked by the user before it goes on real fusions.
 */
public final class FusionLook {
    private FusionLook() {}

    public record Look(String key, String name, int hair) {
        public ResourceLocation skin() { return XenoWarriors.id("textures/entity/character/fusion/" + key + ".png"); }
        public ResourceLocation details() { return XenoWarriors.id("textures/entity/character/fusion/" + key + "_details.png"); }
    }

    public static final Map<String, Look> LOOKS = new HashMap<>();
    static {
        for (Look l : new Look[]{
                new Look("rhalix_mikiko_a", "Rhaliko (crimson coat)", 0x9A1C26),
                new Look("rhalix_mikiko_b", "Mikalix (navy coat)", 0x2B2F6E),
                new Look("rhalix_mikiko_c", "Rhamiko (fire hair)", 0xE0641E),
                new Look("mikiko_rhalix_a", "Mikalix (violet jacket)", 0x6A2AA8),
                new Look("mikiko_rhalix_b", "Rhaliko (gold jacket)", 0x8E2A6E),
                new Look("mikiko_yuri_a", "Mikuri (white jacket)", 0x1E1E22),
                new Look("mikiko_yuri_b", "Yukiko (green jacket)", 0x2E5A34),
                new Look("yuri_mikiko_a", "Yurimi (orange and navy)", 0xB02020)}) LOOKS.put(l.key(), l);
    }

    /** (Tests) shown on the local player. */
    public static Look preview;
    private static final Map<Integer, Look> ACTIVE = new HashMap<>();

    public static void set(int entityId, String key) { if (key == null || !LOOKS.containsKey(key)) ACTIVE.remove(entityId); else ACTIVE.put(entityId, LOOKS.get(key)); }

    public static Look of(AbstractClientPlayer p) {
        if (preview != null && p == net.minecraft.client.Minecraft.getInstance().player) return preview;
        return ACTIVE.isEmpty() ? null : ACTIVE.get(p.getId());
    }
}
