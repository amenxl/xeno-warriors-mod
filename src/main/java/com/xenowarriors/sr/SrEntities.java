package com.xenowarriors.sr;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/** The Sky Rebellion (SR): its members and their camps. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SrEntities {
    public static final RegistryObject<EntityType<SrMemberEntity>> SR_MEMBER = ModEntities.ENTITY_TYPES.register("sr_member",
            () -> EntityType.Builder.<SrMemberEntity>of(SrMemberEntity::new, MobCategory.MONSTER).sized(0.6F, 1.8F).clientTrackingRange(10).build("sr_member"));

    private SrEntities() {}

    public static void init() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(SR_MEMBER.get(), SrMemberEntity.createAttributes().build());
    }
}
