package com.xenowarriors.entity;

import com.xenowarriors.XenoWarriors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<KishiDomeEntity> KISHI_DOME = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            XenoWarriors.id("kishi_dome"),
            EntityType.Builder.<KishiDomeEntity>of(KishiDomeEntity::new, MobCategory.MISC)
                    .sized(KishiDomeEntity.RADIUS * 2F, KishiDomeEntity.HEIGHT)
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("kishi_dome"));

    private ModEntities() {}

    /** Called from the mod initializer so the static fields above get registered. */
    public static void init() {}
}
