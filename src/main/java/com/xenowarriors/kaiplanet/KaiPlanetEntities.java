package com.xenowarriors.kaiplanet;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class KaiPlanetEntities {
    public static final RegistryObject<EntityType<KaiBuilderEntity>> BUILDER = ModEntities.ENTITY_TYPES.register("kai_builder",
            () -> EntityType.Builder.<KaiBuilderEntity>of(KaiBuilderEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).clientTrackingRange(10).build("kai_builder"));

    private KaiPlanetEntities() {}
    public static void init() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) { event.put(BUILDER.get(), KaiBuilderEntity.createAttributes().build()); }
}
