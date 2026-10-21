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

    private ModDamageTypes() {}

    public static DamageSource kishi(Level level, Entity dome, Entity owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(KISHI),
                dome, owner);
    }
}
