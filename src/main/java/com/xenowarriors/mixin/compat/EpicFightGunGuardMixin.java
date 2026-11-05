package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Epic Fight: its guard is on the right mouse button too - with a summoned gun in hand that button aims down the sights,
 * with Rhalix's wires it binds, so no guard stance; and nobody knocked out, in a clash or held in a wire bind guards
 * or dodges (see client/OurClicks). (HEAD injects on Epic Fight's own private methods; skipped quietly when Epic Fight
 * isn't there.)
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.client.events.engine.ControlEngine", remap = false)
public abstract class EpicFightGunGuardMixin {
    @Inject(method = "maybeGuard", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void xenowarriors$gunAimsNotGuards(CallbackInfo ci) {
        if (com.xenowarriors.client.OurClicks.guard(net.minecraft.client.Minecraft.getInstance().player)) ci.cancel();
    }

    @Inject(method = "maybeDodge", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void xenowarriors$noDodgeWhenDown(CallbackInfo ci) {
        if (com.xenowarriors.client.OurClicks.dodge(net.minecraft.client.Minecraft.getInstance().player)) ci.cancel();
    }
}
