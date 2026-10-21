package com.xenowarriors.damage;

import com.xenowarriors.XenoWarriors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Damage types are defined in data/xenowarriors/damage_type/. */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> KISHI =
            ResourceKey.create(Registries.DAMAGE_TYPE, XenoWarriors.id("kishi"));

    public static final ResourceKey<DamageType> EARTH_WAVE =
            ResourceKey.create(Registries.DAMAGE_TYPE, XenoWarriors.id("earth_wave"));

    public static final ResourceKey<DamageType> GRAVITY_COMPRESS =
            ResourceKey.create(Registries.DAMAGE_TYPE, XenoWarriors.id("gravity_compress"));
    public static final ResourceKey<DamageType> BANG =
            ResourceKey.create(Registries.DAMAGE_TYPE, XenoWarriors.id("bang"));
    public static final ResourceKey<DamageType> GRAVITY_BARRAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, XenoWarriors.id("gravity_barrage"));

    private ModDamageTypes() {}

    private static DamageSource of(Level level, ResourceKey<DamageType> key, Entity direct, Entity owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), direct, owner);
    }

    public static DamageSource gravityCompress(Level level, Entity direct, Entity owner) { return of(level, GRAVITY_COMPRESS, direct, owner); }

    public static DamageSource bang(Level level, Entity direct, Entity owner) { return of(level, BANG, direct, owner); }

    public static DamageSource gravityBarrage(Level level, Entity direct, Entity owner) { return of(level, GRAVITY_BARRAGE, direct, owner); }

    public static DamageSource earthWave(Level level, Entity wave, Entity owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(EARTH_WAVE),
                wave, owner);
    }

    public static DamageSource kishi(Level level, Entity dome, Entity owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(KISHI),
                dome, owner);
    }
}
