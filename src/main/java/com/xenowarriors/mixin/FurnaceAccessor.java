package com.xenowarriors.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceAccessor {
    @Accessor("cookingProgress")
    int xenowarriors$getCookingProgress();

    @Accessor("cookingProgress")
    void xenowarriors$setCookingProgress(int value);

    @Accessor("cookingTotalTime")
    int xenowarriors$getCookingTotalTime();

    @Accessor("litTime")
    int xenowarriors$getLitTime();
}
