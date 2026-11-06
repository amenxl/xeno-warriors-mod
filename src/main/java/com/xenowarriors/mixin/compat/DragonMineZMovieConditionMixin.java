package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DragonMineZ movie missions: a "be at Kami's Lookout" start requirement counts as met anywhere. See compat/DmzMovieMissions. */
@Pseudo
@Mixin(targets = "com.dragonminez.common.quest.QuestAvailabilityChecker", remap = false)
public abstract class DragonMineZMovieConditionMixin {
    @Inject(method = "evaluateCondition", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$movieLookout(@Coerce Object condition, @Coerce Object context, @Coerce Object options,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (com.xenowarriors.compat.DmzMovieMissions.waiveCondition(condition, context)) cir.setReturnValue(true);
    }
}
