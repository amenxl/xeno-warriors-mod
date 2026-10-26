package com.xenowarriors.gov;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GovEntities {
    public static final RegistryObject<EntityType<GovAgentEntity>> GOV_AGENT = ModEntities.ENTITY_TYPES.register("gov_agent",
            () -> EntityType.Builder.<GovAgentEntity>of(GovAgentEntity::new, MobCategory.CREATURE).sized(0.6F, 1.8F).clientTrackingRange(10).build("gov_agent"));

    private GovEntities() {}

    public static void init() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(GOV_AGENT.get(), GovAgentEntity.createAttributes().build());
    }
}
