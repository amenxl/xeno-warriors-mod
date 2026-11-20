package com.xenowarriors.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * DragonMineZ's own hair (its 3D strands, physics and colours, as styled in its customization) on one of your own
 * characters drawn with our look - when that character has "HAIR: ON" on the You slots screen. Driven the way
 * DragonMineZ's player renderer drives its hair layer: the entity's base pose, then the head and body bones' poses
 * (here: where our model's head and body are this frame, in GeckoLib's bone space), then the layer draws.
 * All by name; if anything is missing it quietly does nothing ({@link #works()} turns false).
 */
public final class DmzHairBridge {
    private DmzHairBridge() {}

    private static boolean tried, broken;
    private static Method getTP, getAny, getGeoModel, getModelResource, getBakedModel, getBone, getRenderLayers, begin;
    private static Method renderForBone, render;
    private static Class<?> hairLayer;
    /** (Tests) hair drawn. */
    public static int drawn;
    public static String lastWhy = "";
    /** (Tests) drawn on Epic Fight's body / on our plain model; where the head was last drawn, from the feet. */
    public static int efDrawn, vanillaDrawn;
    public static String lastHead = "";

    private static boolean ready() {
        if (tried) return !broken;
        tried = true;
        try {
            Class<?> cache = Class.forName("com.dragonminez.client.render.DMZRendererCache");
            getTP = cache.getMethod("getTPRenderer", net.minecraft.world.entity.player.Player.class);
            getAny = cache.getMethod("getRenderer", net.minecraft.world.entity.player.Player.class);
            Class<?> geoRenderer = Class.forName("software.bernie.geckolib.renderer.GeoRenderer");
            getGeoModel = geoRenderer.getMethod("getGeoModel");
            getRenderLayers = geoRenderer.getMethod("getRenderLayers");
            Class<?> geoModel = Class.forName("software.bernie.geckolib.model.GeoModel");
            Class<?> animatable = Class.forName("software.bernie.geckolib.core.animatable.GeoAnimatable");
            getModelResource = geoModel.getMethod("getModelResource", animatable);
            getBakedModel = geoModel.getMethod("getBakedModel", ResourceLocation.class);
            Class<?> baked = Class.forName("software.bernie.geckolib.cache.object.BakedGeoModel");
            getBone = baked.getMethod("getBone", String.class);
            Class<?> bone = Class.forName("software.bernie.geckolib.cache.object.GeoBone");
            hairLayer = Class.forName("com.dragonminez.client.render.layer.DMZHairLayer");
            Class<?> layer = Class.forName("software.bernie.geckolib.renderer.layer.GeoRenderLayer");
            renderForBone = layer.getMethod("renderForBone", PoseStack.class, animatable, bone, RenderType.class, MultiBufferSource.class,
                    com.mojang.blaze3d.vertex.VertexConsumer.class, float.class, int.class, int.class);
            render = layer.getMethod("render", PoseStack.class, animatable, baked, RenderType.class, MultiBufferSource.class,
                    com.mojang.blaze3d.vertex.VertexConsumer.class, float.class, int.class, int.class);
            begin = Class.forName("com.dragonminez.client.render.hair.HairRenderCapture").getMethod("beginEntity", net.minecraft.world.entity.Entity.class, PoseStack.class);
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("XW: DragonMineZ hair bridge unavailable ({}) - no DragonMineZ hair on our look", t.toString());
        }
        return !broken;
    }

    public static boolean works() { return net.minecraftforge.fml.ModList.get().isLoaded("dragonminez") && ready(); }

    /** This player is one of your own characters with its DragonMineZ hair switched on (and drawn by us, not DragonMineZ). */
    public static boolean wants(AbstractClientPlayer p) {
        var you = com.xenowarriors.client.ClientCharacterData.you(p);
        return you != null && you.dmzHair && !you.dmzLook && works() && !DmzForms.dmzDraws(p);
    }

    private static PoseStack at(Matrix4f m) {
        PoseStack s = new PoseStack();
        s.last().pose().set(m);
        s.last().normal().set(new Matrix3f(m).invert().transpose());
        return s;
    }

    /**
     * Draws it. {@code base}: the entity's GeckoLib root (y up from the feet); {@code head}, {@code body}: those bones'
     * poses this frame (bind space to view, as GeckoLib has them when it reaches each bone).
     */
    public static boolean draw(AbstractClientPlayer p, Matrix4f base, Matrix4f head, Matrix4f body, MultiBufferSource buffers, float partial, int light) {
        if (!works()) return false;
        try {
            Object renderer;
            DmzAuraBridge.lookingUp = true;
            try { renderer = getTP.invoke(null, p); if (renderer == null) renderer = getAny.invoke(null, p); }
            finally { DmzAuraBridge.lookingUp = false; }
            if (renderer == null) { lastWhy = "no DragonMineZ renderer"; return false; }
            Object layer = null;
            for (Object l : (List<?>) getRenderLayers.invoke(renderer)) if (hairLayer.isInstance(l)) { layer = l; break; }
            if (layer == null) { lastWhy = "no hair layer"; return false; }
            Object model = getGeoModel.invoke(renderer);
            Object baked = getBakedModel.invoke(model, getModelResource.invoke(model, p));
            if (baked == null) { lastWhy = "no baked model"; return false; }
            Object hb = ((Optional<?>) getBone.invoke(baked, "head")).orElse(null), bb = ((Optional<?>) getBone.invoke(baked, "body")).orElse(null);
            if (hb == null) { lastWhy = "no head bone"; return false; }
            RenderType rt = RenderType.entityCutoutNoCull(p.getSkinTextureLocation());
            var vc = buffers.getBuffer(rt);
            int ov = OverlayTexture.NO_OVERLAY;
            begin.invoke(null, p, at(base));
            renderForBone.invoke(layer, at(head), p, hb, rt, buffers, vc, partial, light, ov);
            if (bb != null) renderForBone.invoke(layer, at(body), p, bb, rt, buffers, vc, partial, light, ov);
            render.invoke(layer, at(base), p, baked, rt, buffers, vc, partial, light, ov);
            drawn++;
            org.joml.Vector3f rel = new Matrix4f(base).invert().mul(head).getTranslation(new org.joml.Vector3f());
            lastHead = String.format("%.2f,%.2f,%.2f", rel.x, rel.y, rel.z);
            return true;
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("XW: DragonMineZ hair bridge failed ({}) - off for this session", t.toString());
            return false;
        }
    }

    /** Vanilla model space -> GeckoLib's: y up from the feet, x and y flipped (the inverse of what the player renderer does). */
    private static final Matrix4f G2V = new Matrix4f().scale(-1F, -1F, 1F).translate(0F, -1.501F, 0F);   // (GeckoLib space -> vanilla model space)

    /**
     * From our plain model (a layer's pose: the vanilla model root): the poses from the head and body parts.
     */
    public static boolean drawVanilla(AbstractClientPlayer p, PoseStack pose, net.minecraft.client.model.geom.ModelPart headPart, net.minecraft.client.model.geom.ModelPart bodyPart,
                                      MultiBufferSource buffers, float partial, int light) {
        Matrix4f root = new Matrix4f(pose.last().pose());
        Matrix4f base = new Matrix4f(root).mul(G2V);
        PoseStack s = new PoseStack(); s.last().pose().set(root);
        headPart.translateAndRotate(s);
        Matrix4f head = new Matrix4f(s.last().pose()).mul(G2V);
        // (GeckoLib's head bone pivots at the neck: bind space there = the vanilla head part's frame back in GeckoLib space)
        PoseStack b = new PoseStack(); b.last().pose().set(root);
        bodyPart.translateAndRotate(b);
        Matrix4f body = new Matrix4f(b.last().pose()).mul(G2V);
        boolean ok = draw(p, base, head, body, buffers, partial, light);
        if (ok) vanillaDrawn++;
        return ok;
    }
}
