package com.xenowarriors.client.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Oculus / Iris shader packs. With a shader pack on, the "emissive" entity render types (the glowing see-through
 * shapes of our skill effects: Blazing Wind, the Kishi dome, gravity spheres...) go through the pack's spider-eyes
 * program, which adds every layer on top of the last with its bloom: stacked layers came out as a solid white blob
 * (MakeUp - Ultra Fast in the user's pack). While a pack is in use those shapes are drawn as plain see-through
 * entities instead (full bright, normal blending), which every pack shades with their own colors. Read by
 * reflection (Iris API v0, which Oculus ships); no shader mod = never on.
 */
public final class ShaderCompat {
    private ShaderCompat() {}

    private static java.lang.reflect.Method inUse;
    private static Object api;
    private static boolean looked;
    private static long checkedAt;
    private static boolean cached;
    /** (tests) pretend a shader pack is on. */
    public static boolean forceForTest;

    /** A shader pack is being used right now (checked at most every half second). */
    public static boolean packInUse() {
        if (forceForTest) return true;
        long now = System.currentTimeMillis();
        if (now - checkedAt < 500) return cached;
        checkedAt = now;
        if (!looked) {
            looked = true;
            try {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = c.getMethod("getInstance").invoke(null);
                inUse = c.getMethod("isShaderPackInUse");
                XenoWarriors.LOGGER.info("Shader mod found (Iris API): glowing skill effects switch to plain see-through drawing while a shader pack is on");
            } catch (Throwable t) {
                api = null;
            }
        }
        try {
            cached = api != null && (Boolean) inUse.invoke(api);
        } catch (Throwable t) {
            cached = false;
        }
        return cached;
    }

    /** The render type for a glowing see-through effect shape. */
    public static RenderType glow(ResourceLocation tex) {
        return packInUse() ? RenderType.entityTranslucent(tex) : RenderType.entityTranslucentEmissive(tex);
    }
}
