package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DragonMineZ quests: the biome you're in doesn't matter. Every biome test of its quest system goes through
 * QuestLocationHelper.matchesBiome - the BIOME start requirement (QuestAvailabilityChecker.evaluateCondition) and the
 * "go to biome X" objectives (BiomeObjective, via isLocationConditionMet: DragonMineZ's own tick and the bots'
 * missions, compat/DmzBotQuests) - so answering "yes" here lets you start and finish them anywhere. Structure and
 * dimension tests are separate methods and untouched here. Off with missionsIgnoreBiomes=false.
 */
@Pseudo
@Mixin(targets = "com.dragonminez.common.quest.QuestLocationHelper", remap = false)
public abstract class DragonMineZQuestBiomeMatchMixin {
    @Inject(method = "matchesBiome", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$anyBiome(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, String biome, CallbackInfoReturnable<Boolean> cir) {
        if (biome != null && !biome.isBlank() && com.xenowarriors.XwConfig.get(com.xenowarriors.XwConfig.DMZ_MISSIONS_IGNORE_BIOMES)) {
            cir.setReturnValue(true);
            com.xenowarriors.compat.DmzQuests.noteBiomeWaived("biome test", biome);
        }
    }
}
