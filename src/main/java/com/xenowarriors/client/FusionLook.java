package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.render.DetailModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * A fused player's look (original designs, tools/xw/fusion): Rhaliko (Rhalix + Mikiko) and Mikuri (Mikiko + Yuri) -
 * their own model, skin, details and faces, drawn instead of the leader's own (see compat.FusionLooks).
 */
public final class FusionLook {
    private FusionLook() {}

    public static final class Look {
        public final String id, name;
        private DetailModel model;
        private boolean loaded;
        Look(String id, String name) { this.id = id; this.name = name; }
        public ResourceLocation skin() { return XenoWarriors.id("textures/entity/character/" + id + ".png"); }
        public ResourceLocation details() { return XenoWarriors.id("textures/entity/character/" + id + "_details.png"); }
        public DetailModel model() {
            if (!loaded) { loaded = true; model = DetailModel.loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/detail/" + id + ".json"); }
            return model;
        }
        public String modelPath() { return "/assets/" + XenoWarriors.MOD_ID + "/models/detail/" + id + ".json"; }
    }

    public static final Map<String, Look> LOOKS = Map.of(
            "fusion_rhaliko", new Look("fusion_rhaliko", "Rhaliko"),
            "fusion_mikuri", new Look("fusion_mikuri", "Mikuri"));

    /** (Tests) shown on the local player. */
    public static Look preview;
    private static final Map<Integer, Look> ACTIVE = new HashMap<>();

    public static void set(int entityId, String key) { if (key == null || !LOOKS.containsKey(key)) ACTIVE.remove(entityId); else ACTIVE.put(entityId, LOOKS.get(key)); }

    public static Look of(AbstractClientPlayer p) {
        if (preview != null && p == net.minecraft.client.Minecraft.getInstance().player) return preview;
        return ACTIVE.isEmpty() ? null : ACTIVE.get(p.getId());
    }
}
