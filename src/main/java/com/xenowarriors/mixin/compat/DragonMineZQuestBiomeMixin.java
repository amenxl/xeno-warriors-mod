package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DragonMineZ quests (the quest tree, the NPCs): no "you have to be in this biome" or "at this structure" (the Classic
 * Saga's Kami's Lookout, Roshi's house, Goku's house...) requirement to start them. A BIOME / STRUCTURE start condition
 * is answered "yes" before DragonMineZ looks at the level or position (so also where it has none, like the bots'
 * checks); "go to" objectives aren't start conditions and still need you there. The biome test itself is waived by
 * DragonMineZQuestBiomeMatchMixin. Off with missionsIgnoreBiomes / missionsIgnoreStructures=false in config/xenowarriors-common.toml.
 */
@Pseudo
@Mixin(targets = "com.dragonminez.common.quest.QuestAvailabilityChecker", remap = false)
public abstract class DragonMineZQuestBiomeMixin {
    @Inject(method = "evaluateCondition", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$anyBiome(@Coerce Object condition, @Coerce Object context, @Coerce Object options,
                                              CallbackInfoReturnable<Boolean> cir) {
        try {
            Object type = condition.getClass().getMethod("getType").invoke(condition);
            if (type == null) return;
            if ("BIOME".equals(type.toString()) && com.xenowarriors.XwConfig.get(com.xenowarriors.XwConfig.DMZ_MISSIONS_IGNORE_BIOMES)) {
                cir.setReturnValue(true);
                com.xenowarriors.compat.DmzQuests.noteBiomeWaived("start requirement", String.valueOf(condition.getClass().getMethod("getBiomeId").invoke(condition)));
            } else if ("STRUCTURE".equals(type.toString()) && com.xenowarriors.XwConfig.get(com.xenowarriors.XwConfig.DMZ_MISSIONS_IGNORE_STRUCTURES)) {
                cir.setReturnValue(true);
                com.xenowarriors.compat.DmzQuests.noteBiomeWaived("structure start requirement", String.valueOf(condition.getClass().getMethod("getStructureId").invoke(condition)));
            }
        } catch (Throwable ignored) {}
    }
}
