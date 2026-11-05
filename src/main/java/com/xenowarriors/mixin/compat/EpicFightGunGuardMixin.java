package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Epic Fight: its guard is on the right mouse button too - with a summoned gun in hand that button aims down the sights,
 * so no guard stance. (HEAD inject on Epic Fight's own private method; skipped quietly when Epic Fight isn't there.)
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.client.events.engine.ControlEngine", remap = false)
public abstract class EpicFightGunGuardMixin {
    @Inject(method = "maybeGuard", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void xenowarriors$gunAimsNotGuards(CallbackInfo ci) {
        if (com.xenowarriors.client.forms.GunClient.held(net.minecraft.client.Minecraft.getInstance().player) != null) {
            com.xenowarriors.client.forms.GunClient.efGuardsBlocked++;
            ci.cancel();
        }
    }
}
