package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.render.EarthBulletRenderer;
import com.xenowarriors.client.render.EarthWaveRenderer;
import com.xenowarriors.client.render.GravityBarrageRenderer;
import com.xenowarriors.client.render.GravityCompressRenderer;
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
        event.registerEntityRenderer(ModEntities.GRAVITY_COMPRESS.get(), GravityCompressRenderer::new);
        event.registerEntityRenderer(ModEntities.EARTH_BULLET.get(), EarthBulletRenderer::new);
        event.registerEntityRenderer(ModEntities.GRAVITY_BARRAGE.get(), GravityBarrageRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : ModKeyMappings.SKILLS) event.register(key);
        event.register(ModKeyMappings.SUMMON_BLADE);
        event.register(ModKeyMappings.LOCK_ON);
        event.register(ModKeyMappings.KARMA_SENSE);
    }

    /** The hoodie's item icon shows its dye color (the trim layer stays as is). */
    @SubscribeEvent
    public static void registerItemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 0
                        ? ((com.xenowarriors.item.KarmaHoodieItem) stack.getItem()).getColor(stack) | 0xFF000000 : -1,
                com.xenowarriors.item.ModItems.KARMA_HOODIE.get());
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("skills", new SkillHudOverlay());
        event.registerAboveAll("cutscene", new UltimateCutscene.Overlay());
        event.registerAboveAll("stat_toasts", new StatToasts());
        event.registerAboveAll("lock_on", new LockOn.Overlay());
        event.registerAboveAll("karma_sense", new KarmaSense.Overlay());
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new CharacterDetailLayer(renderer));
                renderer.addLayer(new com.xenowarriors.client.render.CharacterArmorLayer(renderer));
                renderer.addLayer(new com.xenowarriors.client.mikiko.MikikoBladeLayer(renderer, event.getContext().getItemInHandRenderer()));
            }
        }
    }
}
