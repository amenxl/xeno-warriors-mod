package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/** Bot teammates (for testing parties before more people are playing). */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BotEntities {
    public static final RegistryObject<EntityType<PartyBotEntity>> BOT = ModEntities.ENTITY_TYPES.register("party_bot",
            () -> EntityType.Builder.<PartyBotEntity>of(PartyBotEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).clientTrackingRange(10).build("party_bot"));

    private BotEntities() {}

    public static void init() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(BOT.get(), PartyBotEntity.createAttributes().build());
    }
}
