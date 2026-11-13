package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DragonMineZ movie missions: their "be at Kami's Lookout" objective counts as reached anywhere. Every location
 * objective (DragonMineZ's own tick, and the bots' missions) is tested by this one method of QuestLocationHelper.
 * See compat/DmzMovieMissions.
 */
@Pseudo
@Mixin(targets = "com.dragonminez.common.quest.QuestLocationHelper", remap = false)
public abstract class DragonMineZMovieLookoutMixin {
    @Inject(method = "isLocationConditionMet(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lcom/dragonminez/common/quest/QuestObjective;Z)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$movieLookout(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, @Coerce Object objective, boolean fallback,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (com.xenowarriors.compat.DmzMovieMissions.waiveObjective(objective)) cir.setReturnValue(true);
    }

    /** (DragonMineZ 2.2: the check takes the player now.) */
    @Inject(method = "isLocationConditionMet(Lnet/minecraft/world/entity/player/Player;Lcom/dragonminez/common/quest/QuestObjective;Z)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$movieLookout22(net.minecraft.world.entity.player.Player player, @Coerce Object objective, boolean fallback,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (com.xenowarriors.compat.DmzMovieMissions.waiveObjective(objective)) cir.setReturnValue(true);
    }
}
