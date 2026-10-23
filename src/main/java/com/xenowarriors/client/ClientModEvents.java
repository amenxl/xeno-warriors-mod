package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.render.BlazingWindRenderer;
import com.xenowarriors.client.render.EarthBulletRenderer;
import com.xenowarriors.client.render.FireBlitzRenderer;
import com.xenowarriors.client.render.HekubladeShardRenderer;
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
        event.registerEntityRenderer(ModEntities.HEKUBLADE_SHARD.get(), HekubladeShardRenderer::new);
        event.registerEntityRenderer(ModEntities.THROWN_HEKUBLADE.get(), com.xenowarriors.client.render.ThrownHekubladeRenderer::new);
        event.registerEntityRenderer(ModEntities.FIRE_BLITZ.get(), FireBlitzRenderer::new);
        event.registerEntityRenderer(ModEntities.BLAZING_WIND.get(), BlazingWindRenderer::new);
        event.registerEntityRenderer(com.xenowarriors.corrupted.ModCorrupted.CORRUPTED_VILLAGER.get(),
                com.xenowarriors.client.corrupted.CorruptedVillagerRenderer::new);
        event.registerEntityRenderer(com.xenowarriors.corrupted.ModCorrupted.CORRUPTED_SOUL.get(),
                com.xenowarriors.client.corrupted.CorruptedSoulRenderer::new);
        event.registerEntityRenderer(com.xenowarriors.corrupted.ModCorrupted.C_SOUL.get(),
                com.xenowarriors.client.corrupted.CSoulRenderer::new);
        event.registerEntityRenderer(com.xenowarriors.corrupted.ModCorrupted.SOUL_BOLT.get(),
                com.xenowarriors.client.corrupted.SoulBoltRenderer::new);
    }

    @SubscribeEvent
    public static void registerSkies(net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
        event.register(HekubladeSky.ID, new HekubladeSky());
    }

    @SubscribeEvent
    public static void registerLayerDefs(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(com.xenowarriors.client.corrupted.CorruptedSoulModel.LAYER,
                com.xenowarriors.client.corrupted.CorruptedSoulModel::create);
        event.registerLayerDefinition(com.xenowarriors.client.compat.FoxTailLayer.LAYER,
                com.xenowarriors.client.compat.FoxTailLayer::create);
        event.registerLayerDefinition(com.xenowarriors.client.corrupted.CSoulModel.LAYER,
                com.xenowarriors.client.corrupted.CSoulModel::create);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : ModKeyMappings.SKILLS) event.register(key);
        event.register(ModKeyMappings.SUMMON_BLADE);
        event.register(ModKeyMappings.TOGGLE_WINGS);
        event.register(ModKeyMappings.LOCK_ON);
        event.register(ModKeyMappings.KARMA_SENSE);
        event.register(ModKeyMappings.STATS_MENU);
        event.register(ModKeyMappings.FOX_KING);
    }

    /** The hoodie's item icon shows its dye color (the trim layer stays as is). */
    @SubscribeEvent
    public static void registerItemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 0
                        ? ((com.xenowarriors.item.KarmaHoodieItem) stack.getItem()).getColor(stack) | 0xFF000000 : -1,
                com.xenowarriors.item.ModItems.KARMA_HOODIE.get());
    }

    /** Extra Hekublade models (the sword and each crystal, drawn separately so the crystals can move). */
    @SubscribeEvent
    public static void registerModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(com.xenowarriors.client.mikiko.HekubladeRenderer.BASE);
        event.register(com.xenowarriors.client.mikiko.HekubladeRenderer.ORIGINS_2);
        event.register(com.xenowarriors.client.mikiko.HekubladeRenderer.SEASON_3);
        event.register(com.xenowarriors.client.mikiko.HekubladeRenderer.SEASON_3_GLOBE);
        for (var rl : com.xenowarriors.client.mikiko.HekubladeRenderer.CRYSTALS) event.register(rl);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("skills", new SkillHudOverlay());
        event.registerAboveAll("cutscene", new UltimateCutscene.Overlay());
        event.registerAboveAll("stat_toasts", new StatToasts());
        event.registerAboveAll("lock_on", new LockOn.Overlay());
        event.registerAboveAll("karma_sense", new KarmaSense.Overlay());
        event.registerAboveAll("party", new com.xenowarriors.client.party.PartyHud());
        event.registerAboveAll("character_level", new LevelHud());
    }

    @SubscribeEvent
    public static void clientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                com.xenowarriors.fox.ModMenus.FOX.get(), com.xenowarriors.client.fox.FoxInventoryScreen::new));
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        net.minecraft.client.renderer.entity.EntityRenderer<?> foxRenderer = event.getRenderer(net.minecraft.world.entity.EntityType.FOX);
        if (foxRenderer instanceof net.minecraft.client.renderer.entity.FoxRenderer fr) {
            fr.addLayer(new com.xenowarriors.client.fox.FoxArmorLayer(fr, event.getEntityModels()));
            fr.addLayer(new com.xenowarriors.client.fox.FoxSwordLayer(fr));
        }
        for (net.minecraft.world.entity.EntityType<?> type : java.util.List.of(net.minecraft.world.entity.EntityType.BAT,
                net.minecraft.world.entity.EntityType.PARROT, net.minecraft.world.entity.EntityType.CHICKEN,
                net.minecraft.world.entity.EntityType.BEE)) {
            addWingSkin(event.getRenderer((net.minecraft.world.entity.EntityType) type));
        }
        // possessed animals: every animal renderer gets the corruption wash
        for (net.minecraft.world.entity.EntityType<?> type : net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValues()) {
            if (type.getCategory() != net.minecraft.world.entity.MobCategory.CREATURE
                    && type.getCategory() != net.minecraft.world.entity.MobCategory.WATER_CREATURE
                    && type.getCategory() != net.minecraft.world.entity.MobCategory.AXOLOTLS
                    && type.getCategory() != net.minecraft.world.entity.MobCategory.MONSTER
                    && type.getCategory() != net.minecraft.world.entity.MobCategory.MISC) continue;
            addTint(event.getRenderer((net.minecraft.world.entity.EntityType) type));
        }
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new CharacterDetailLayer(renderer));
                renderer.addLayer(new com.xenowarriors.client.compat.FoxTailLayer(renderer, event.getEntityModels()));
                renderer.addLayer(new com.xenowarriors.client.render.CharacterArmorLayer(renderer));
                renderer.addLayer(new com.xenowarriors.client.fox.FoxBackLayer(renderer, event.getEntityModels()));
                renderer.addLayer(new com.xenowarriors.client.mikiko.MikikoBladeLayer(renderer, event.getContext().getItemInHandRenderer()));
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addTint(net.minecraft.client.renderer.entity.EntityRenderer<?> r) {
        if (r instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer lr) {
            lr.addLayer(new com.xenowarriors.client.corrupted.CorruptionTintLayer(lr, false));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addWingSkin(net.minecraft.client.renderer.entity.EntityRenderer<?> r) {
        if (r instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer lr) {
            lr.addLayer(new com.xenowarriors.client.wing.WingSkinLayer(lr));
        }
    }
}
