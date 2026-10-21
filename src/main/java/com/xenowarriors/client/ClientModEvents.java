package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.render.EarthWaveRenderer;
import com.xenowarriors.client.render.KishiDomeRenderer;
import com.xenowarriors.client.render.CharacterDetailLayer;
import com.xenowarriors.entity.ModEntities;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.KISHI_DOME.get(), KishiDomeRenderer::new);
        event.registerEntityRenderer(ModEntities.EARTH_WAVE.get(), EarthWaveRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : ModKeyMappings.SKILLS) event.register(key);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("skills", new SkillHudOverlay());
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new CharacterDetailLayer(renderer));
            }
        }
    }
}
