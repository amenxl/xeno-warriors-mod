package com.xenowarriors.client.cel;

import com.xenowarriors.client.ClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Cel-shading (Settings): characters use their cel-shaded textures (flat colors, hard shadow bands, ink lines on every
 * edge - made by tools/xw/cel) and get a black outline drawn around them.
 */
public final class Cel {
    private static final Map<ResourceLocation, ResourceLocation> CACHE = new HashMap<>();

    private Cel() {}

    /** Forces cel-shading on for a moment (the main menu's character art is always cel-shaded). */
    public static boolean force;

    public static boolean on() { return force || ClientSettings.celShading(); }

    /** The cel-shaded version of this texture (…_cel.png) when cel-shading is on and one exists. */
    public static ResourceLocation tex(ResourceLocation rl) {
        if (rl == null || !on()) return rl;
        return CACHE.computeIfAbsent(rl, r -> {
            String p = r.getPath();
            if (!p.endsWith(".png")) return r;
            ResourceLocation cel = new ResourceLocation(r.getNamespace(), p.substring(0, p.length() - 4) + "_cel.png");
            return Minecraft.getInstance().getResourceManager().getResource(cel).isPresent() ? cel : r;
        });
    }

    /** Resource reloads (packs changed): look again. */
    public static void clear() { CACHE.clear(); }
}
