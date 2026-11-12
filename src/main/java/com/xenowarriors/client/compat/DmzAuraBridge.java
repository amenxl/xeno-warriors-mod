package com.xenowarriors.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;

/**
 * DragonMineZ's own aura (its shader aura and lightning sparks) on a Xeno Warriors player we draw ourselves: queued
 * the way DragonMineZ's aura layer does it ({@code PlayerEffectQueue.addAura / addSpark} with the player's
 * DragonMineZ body model), whenever DragonMineZ would show it - the aura is on (powering up, a form's permanent aura).
 * All by name; if anything is missing it quietly does nothing, and {@link #works()} turns false.
 */
public final class DmzAuraBridge {
    private DmzAuraBridge() {}

    private static boolean tried, broken;
    /** (Tests / logs) why the last try didn't queue anything. */
    public static String lastWhy = "";
    /** While set, our renderer mixin lets DragonMineZ hand out its renderer for one of our players (only to read its model). */
    public static boolean lookingUp;
    private static Method getAny;
    private static Method getTP, getGeoModel, getModelResource, getBakedModel, addAura, addSpark;
    private static Method getStatus, isAuraActive, isPermanentAura;
    private static Object cap;
    private static Method getCapability;
    /** (For the tests) auras queued so far. */
    public static int queued;

    private static boolean ready() {
        if (tried) return !broken;
        tried = true;
        try {
            Class<?> cache = Class.forName("com.dragonminez.client.render.DMZRendererCache");
            getTP = cache.getMethod("getTPRenderer", net.minecraft.world.entity.player.Player.class);
            getAny = cache.getMethod("getRenderer", net.minecraft.world.entity.player.Player.class);
            Class<?> geoRenderer = Class.forName("software.bernie.geckolib.renderer.GeoRenderer");
            getGeoModel = geoRenderer.getMethod("getGeoModel");
            Class<?> geoModel = Class.forName("software.bernie.geckolib.model.GeoModel");
            Class<?> animatable = Class.forName("software.bernie.geckolib.core.animatable.GeoAnimatable");
            getModelResource = geoModel.getMethod("getModelResource", animatable);
            getBakedModel = geoModel.getMethod("getBakedModel", ResourceLocation.class);
            Class<?> baked = Class.forName("software.bernie.geckolib.cache.object.BakedGeoModel");
            Class<?> queue = Class.forName("com.dragonminez.client.render.util.PlayerEffectQueue");
            addAura = queue.getMethod("addAura", AbstractClientPlayer.class, baked, PoseStack.class, float.class, int.class);
            addSpark = queue.getMethod("addSpark", AbstractClientPlayer.class, baked, PoseStack.class, float.class, int.class);
            cap = Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
            Class<?> data = Class.forName("com.dragonminez.common.stats.StatsData");
            getStatus = data.getMethod("getStatus");
            isAuraActive = getStatus.getReturnType().getMethod("isAuraActive");
            try { isPermanentAura = getStatus.getReturnType().getMethod("isPermanentAura"); } catch (NoSuchMethodException ignored) {}
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("XW: DragonMineZ aura bridge unavailable ({}), falling back to our own aura", t.toString());
        }
        return !broken;
    }

    public static boolean works() { return ModList() && ready(); }

    private static boolean ModList() { return net.minecraftforge.fml.ModList.get().isLoaded("dragonminez"); }

    /** Would DragonMineZ show this player's aura now (aura on, or a form with a permanent aura)? */
    public static boolean auraOn(AbstractClientPlayer p) {
        if (!works()) return false;
        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object d = p.getCapability((net.minecraftforge.common.capabilities.Capability) cap).resolve().orElse(null);
            if (d == null) return false;
            Object s = getStatus.invoke(d);
            return (Boolean) isAuraActive.invoke(s) || isPermanentAura != null && (Boolean) isPermanentAura.invoke(s);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Queue DragonMineZ's aura (and sparks) around this player, posed at the entity's origin. True if queued. */
    public static boolean queue(AbstractClientPlayer p, PoseStack pose, float partial, int light) {
        if (!works()) return false;
        try {
            Object renderer;
            lookingUp = true;
            try { renderer = getTP.invoke(null, p); if (renderer == null) renderer = getAny.invoke(null, p); }
            finally { lookingUp = false; }
            if (renderer == null) { lastWhy = "no DragonMineZ renderer for this player"; return false; }
            Object model = getGeoModel.invoke(renderer);
            Object res = getModelResource.invoke(model, p);
            Object baked = getBakedModel.invoke(model, res);
            if (baked == null) { lastWhy = "no baked model for " + res; return false; }
            addAura.invoke(null, p, baked, pose, partial, light);
            addSpark.invoke(null, p, baked, pose, partial, light);
            queued++;
            return true;
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("XW: DragonMineZ aura bridge failed ({}), falling back to our own aura", t.toString());
            return false;
        }
    }
}
