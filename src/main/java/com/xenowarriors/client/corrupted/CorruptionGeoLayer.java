package com.xenowarriors.client.corrupted;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The purple corruption wash for mobs drawn with GeckoLib (e.g. DragonMineZ's enemies), which don't use the vanilla
 * renderers our normal tint layer hooks into. Added to a renderer the first time a possessed mob using it shows up.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CorruptionGeoLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    private static final Set<Object> PATCHED = Collections.newSetFromMap(new IdentityHashMap<>());

    public CorruptionGeoLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack pose, T animatable, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                       VertexConsumer buffer, float partialTick, int light, int overlay) {
        if (!(animatable instanceof Entity e) || e.isInvisible() || !CorruptionClient.is(e.getId())) return;
        float pulse = 0.38F + 0.12F * (float) Math.sin((e.tickCount + partialTick) * 0.12F);
        RenderType wash = RenderType.entityTranslucent(getTextureResource(animatable));
        getRenderer().reRender(model, pose, buffers, animatable, wash, buffers.getBuffer(wash), partialTick, light,
                OverlayTexture.NO_OVERLAY, 0.55F, 0.12F, 0.9F, pulse);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.level.getGameTime() % 10 != 0) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!CorruptionClient.is(e.getId())) continue;
            var r = mc.getEntityRenderDispatcher().getRenderer(e);
            if (r instanceof GeoEntityRenderer geo && PATCHED.add(geo)) {
                geo.addRenderLayer(new CorruptionGeoLayer(geo));
            }
        }
    }
}
