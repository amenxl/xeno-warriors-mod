package com.xenowarriors.npc;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModNpcs {
    public static final RegistryObject<EntityType<HumanNpcEntity>> HUMAN = ModEntities.ENTITY_TYPES.register("human",
            () -> EntityType.Builder.<HumanNpcEntity>of(HumanNpcEntity::new, MobCategory.CREATURE).sized(0.6F, 1.8F)
                    .clientTrackingRange(10).build("human"));
    public static final RegistryObject<EntityType<DarkPossessedEntity>> DARK_POSSESSED = ModEntities.ENTITY_TYPES.register("dark_possessed",
            () -> EntityType.Builder.<DarkPossessedEntity>of(DarkPossessedEntity::new, MobCategory.MONSTER).sized(0.6F, 1.8F)
                    .clientTrackingRange(10).build("dark_possessed"));

    private ModNpcs() {}

    public static void init() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(HUMAN.get(), HumanNpcEntity.createAttributes().build());
        event.put(DARK_POSSESSED.get(), DarkPossessedEntity.createAttributes().build());
    }
}
