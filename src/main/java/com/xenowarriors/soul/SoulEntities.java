package com.xenowarriors.soul;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SoulEntities {
    public static final RegistryObject<EntityType<SoulEntity>> SOUL = ModEntities.ENTITY_TYPES.register("split_soul",
            () -> EntityType.Builder.<SoulEntity>of(SoulEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).clientTrackingRange(10).build("split_soul"));

    private SoulEntities() {}
    public static void init() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) { event.put(SOUL.get(), SoulEntity.createAttributes().build()); }
}
