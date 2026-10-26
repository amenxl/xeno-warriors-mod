package com.xenowarriors.s3;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/** Season 3 entities: Distortions and the Order of Assassins. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class S3Entities {
    public static final RegistryObject<EntityType<DistortionEntity>> DISTORTION = ModEntities.ENTITY_TYPES.register("distortion",
            () -> EntityType.Builder.<DistortionEntity>of(DistortionEntity::new, MobCategory.MISC).sized(2.4F, 4.0F).fireImmune().clientTrackingRange(12).build("distortion"));
    public static final RegistryObject<EntityType<OrderAssassinEntity>> ORDER_ASSASSIN = ModEntities.ENTITY_TYPES.register("order_assassin",
            () -> EntityType.Builder.<OrderAssassinEntity>of(OrderAssassinEntity::new, MobCategory.CREATURE).sized(0.6F, 1.8F).clientTrackingRange(10).build("order_assassin"));

    private S3Entities() {}

    public static void init() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(DISTORTION.get(), DistortionEntity.createAttributes().build());
        event.put(ORDER_ASSASSIN.get(), OrderAssassinEntity.createAttributes().build());
    }
}
