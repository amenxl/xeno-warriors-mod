package com.xenowarriors.client.vehicle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.vehicle.VehicleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.HashMap;
import java.util.Map;

/**
 * Headlights, tail lights, dials and the Mk II trim glowing: the model drawn again with its _glowmask texture as
 * vanilla's "eyes" (full bright, added on top - like a spider's eyes). GeckoLib's own AutoGlowingGeoLayer drew those
 * parts solid black here (its glow texture cut them out of the paint, and its emissive pass came out black).
 */
public class VehicleGlowLayer extends GeoRenderLayer<VehicleEntity> {
    private static final Map<ResourceLocation, ResourceLocation> MASKS = new HashMap<>();

    public VehicleGlowLayer(GeoRenderer<VehicleEntity> renderer) { super(renderer); }

    @Override
    public void render(PoseStack pose, VehicleEntity e, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                       VertexConsumer buffer, float partialTick, int light, int overlay) {
        if (e.isInvisible()) return;
        ResourceLocation mask = MASKS.computeIfAbsent(getTextureResource(e),
                t -> new ResourceLocation(t.getNamespace(), t.getPath().replace(".png", "_glowmask.png")));
        RenderType glow = RenderType.eyes(mask);
        getRenderer().reRender(model, pose, buffers, e, glow, buffers.getBuffer(glow), partialTick, 15728880,
                OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
    }
}
