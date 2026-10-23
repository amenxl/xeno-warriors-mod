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

    public static final RegistryObject<EntityType<SkylanderEntity>> SKYLANDER = ModEntities.ENTITY_TYPES.register("skylander",
            () -> EntityType.Builder.<SkylanderEntity>of(SkylanderEntity::new, MobCategory.CREATURE).sized(0.6F, 1.8F)
                    .clientTrackingRange(12).build("skylander"));

    public static final RegistryObject<EntityType<NpcThrownEntity>> THROWN = ModEntities.ENTITY_TYPES.register("npc_thrown",
            () -> EntityType.Builder.<NpcThrownEntity>of(NpcThrownEntity::new, MobCategory.MISC).sized(0.25F, 0.25F)
                    .clientTrackingRange(6).updateInterval(10).build("npc_thrown"));
    public static final RegistryObject<EntityType<KiBlastEntity>> KI_BLAST = ModEntities.ENTITY_TYPES.register("ki_blast",
            () -> EntityType.Builder.<KiBlastEntity>of(KiBlastEntity::new, MobCategory.MISC).sized(0.4F, 0.4F)
                    .clientTrackingRange(6).updateInterval(2).build("ki_blast"));

    private ModNpcs() {}

    public static void init() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(HUMAN.get(), HumanNpcEntity.createAttributes().build());
        event.put(DARK_POSSESSED.get(), DarkPossessedEntity.createAttributes().build());
        event.put(SKYLANDER.get(), SkylanderEntity.createAttributes().build());
    }
}
