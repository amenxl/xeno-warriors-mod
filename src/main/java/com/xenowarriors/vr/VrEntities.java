package com.xenowarriors.vr;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VrEntities {
    public static final RegistryObject<EntityType<VrUltimaEntity>> VR_ULTIMA = ModEntities.ENTITY_TYPES.register("vr_ultima",
            () -> EntityType.Builder.<VrUltimaEntity>of(VrUltimaEntity::new, MobCategory.MONSTER).sized(0.6F, 1.8F).clientTrackingRange(10).build("vr_ultima"));

    private VrEntities() {}

    public static void init() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) { event.put(VR_ULTIMA.get(), VrUltimaEntity.createAttributes().build()); }
}
