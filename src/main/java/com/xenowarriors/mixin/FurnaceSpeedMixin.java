package com.xenowarriors.mixin;

import com.xenowarriors.stats.Crafting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smelting skill: furnaces cook faster for whoever used them last (more cooking progress each tick). */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceSpeedMixin {
    @Unique
    private float xenowarriors$extra;

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void xenowarriors$faster(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        FurnaceAccessor f = (FurnaceAccessor) furnace;
        int progress = f.xenowarriors$getCookingProgress(), total = f.xenowarriors$getCookingTotalTime();
        if (f.xenowarriors$getLitTime() <= 0 || progress <= 0 || total <= 0) return;
        FurnaceSpeedMixin self = (FurnaceSpeedMixin) (Object) furnace;
        self.xenowarriors$extra += Crafting.furnaceBonus(furnace);
        if (self.xenowarriors$extra >= 1F) {
            int add = (int) self.xenowarriors$extra;
            self.xenowarriors$extra -= add;
            f.xenowarriors$setCookingProgress(Math.min(total - 1, progress + add));   // vanilla finishes it next tick
        }
    }
}
