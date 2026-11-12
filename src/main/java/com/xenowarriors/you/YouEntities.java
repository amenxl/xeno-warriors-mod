package com.xenowarriors.you;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class YouEntities {
    public static final RegistryObject<EntityType<DevilShadowEntity>> DEVIL_SHADOW = ModEntities.ENTITY_TYPES.register("devil_shadow",
            () -> EntityType.Builder.<DevilShadowEntity>of(DevilShadowEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).clientTrackingRange(10).build("devil_shadow"));

    private YouEntities() {}
    public static void init() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) { event.put(DEVIL_SHADOW.get(), DevilShadowEntity.createAttributes().build()); }
}
