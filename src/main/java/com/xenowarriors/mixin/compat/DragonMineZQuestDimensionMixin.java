package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DragonMineZ quests: the dimension you're in doesn't matter. Every dimension check in its quest system goes through
 * QuestLocationHelper.isInDimension - the saga/side quests' start requirements ("be on Earth / Namek / the Sacred
 * World", QuestAvailabilityChecker's DIMENSION condition) and the "go to that dimension" objectives (DimensionObjective,
 * ticked by QuestEvents) - so answering "yes" there lets you take and progress quests from anywhere (the Recarnet,
 * the Tower...). Kill, item, talk and coordinate objectives never checked the dimension. Off with
 * questsAnyDimension=false in config/xenowarriors-common.toml.
 */
@Pseudo
@Mixin(targets = "com.dragonminez.common.quest.QuestLocationHelper", remap = false)
public abstract class DragonMineZQuestDimensionMixin {
    @Inject(method = "isInDimension", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$anyDimension(net.minecraft.world.level.Level level, String dimension, CallbackInfoReturnable<Boolean> cir) {
        if (level != null && dimension != null && !dimension.isBlank()
                && com.xenowarriors.XwConfig.get(com.xenowarriors.XwConfig.DMZ_QUESTS_ANY_DIMENSION)) {
            cir.setReturnValue(true);
            com.xenowarriors.compat.DmzQuests.noteDimensionWaived(level, dimension);
        }
    }
}
