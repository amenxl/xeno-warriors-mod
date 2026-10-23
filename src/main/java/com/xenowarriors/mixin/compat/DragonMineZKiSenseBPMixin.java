package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While it's really Karma Sensing, DragonMineZ sizes its Ki Sense auras by Karma strength: each sensed thing's level
 * against yours (its aura grows with how much stronger it is).
 */
@Pseudo
@Mixin(targets = "com.dragonminez.client.systems.kisense.KiSenseScan", remap = false)
public abstract class DragonMineZKiSenseBPMixin {
    @Inject(method = "getMyBP", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$myKarma(CallbackInfoReturnable<Float> cir) {
        if (com.xenowarriors.client.KarmaSense.active()) cir.setReturnValue(com.xenowarriors.client.LevelHud.level() * 100F);
    }

    @Inject(method = "getCachedBP", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$theirKarma(int id, CallbackInfoReturnable<Float> cir) {
        if (!com.xenowarriors.client.KarmaSense.active()) return;
        int lv = com.xenowarriors.client.KarmaSense.level(id);
        if (lv > 0) cir.setReturnValue(lv * 100F);
    }
}
