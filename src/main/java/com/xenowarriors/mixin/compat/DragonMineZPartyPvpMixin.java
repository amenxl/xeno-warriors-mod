package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two players in a DragonMineZ party can't hurt each other (unless its party PvP is on). In a Xeno Warriors PvP battle
 * they can: for DragonMineZ the two aren't "in the same party" while they're fighting each other.
 */
@Pseudo
@Mixin(targets = "com.dragonminez.common.quest.PartyManager", remap = false)
public abstract class DragonMineZPartyPvpMixin {
    @Inject(method = "areInSameParty", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$battle(net.minecraft.world.entity.player.Player a, net.minecraft.world.entity.player.Player b, CallbackInfoReturnable<Boolean> cir) {
        if (a == null || b == null || a.level().isClientSide) return;
        try {
            if (com.xenowarriors.party.Duels.battling(a, b)) { cir.setReturnValue(false); com.xenowarriors.party.Duels.dmzPartyLetThrough++; }
        } catch (Throwable ignored) {}
    }
}
