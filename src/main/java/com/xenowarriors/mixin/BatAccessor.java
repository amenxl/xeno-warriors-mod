package com.xenowarriors.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ambient.Bat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Rhalix's bats fly where he is instead of wherever they like. */
@Mixin(Bat.class)
public interface BatAccessor {
    @Accessor("targetPosition")
    void xenowarriors$setTargetPosition(BlockPos pos);
}
