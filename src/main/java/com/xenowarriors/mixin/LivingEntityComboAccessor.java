package com.xenowarriors.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Combo combat: a combo hit always lands at full charge (the combo's own timing replaces the vanilla cooldown). */
@Mixin(LivingEntity.class)
public interface LivingEntityComboAccessor {
    @Accessor("attackStrengthTicker")
    void xenowarriors$setAttackStrengthTicker(int ticks);
}
