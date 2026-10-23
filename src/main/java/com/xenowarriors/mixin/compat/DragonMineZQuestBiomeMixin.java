package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DragonMineZ quests (the quest tree): no "you have to be in this biome" requirement to start them. */
@Pseudo
@Mixin(targets = "com.dragonminez.common.quest.QuestAvailabilityChecker", remap = false)
public abstract class DragonMineZQuestBiomeMixin {
    @Inject(method = "evaluateCondition", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$anyBiome(@Coerce Object condition, @Coerce Object context, @Coerce Object options,
                                              CallbackInfoReturnable<Boolean> cir) {
        try {
            Object type = condition.getClass().getMethod("getType").invoke(condition);
            if (type != null && "BIOME".equals(type.toString())) cir.setReturnValue(true);
        } catch (Throwable ignored) {}
    }
}
