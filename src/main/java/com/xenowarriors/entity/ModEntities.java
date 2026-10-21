package com.xenowarriors.entity;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, XenoWarriors.MOD_ID);

    public static final RegistryObject<EntityType<KishiDomeEntity>> KISHI_DOME = ENTITY_TYPES.register("kishi_dome",
            () -> EntityType.Builder.<KishiDomeEntity>of(KishiDomeEntity::new, MobCategory.MISC)
                    .sized(KishiDomeEntity.RADIUS * 2F, KishiDomeEntity.HEIGHT)
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("kishi_dome"));

    private ModEntities() {}
}
