package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No red outline around Epic Fight's lock-on target (ours shows its own marker). */
@Pseudo
@Mixin(targets = "yesman.epicfight.api.client.camera.EpicFightCameraAPI", remap = false)
public abstract class EpicFightTargetOutlineMixin {
    @Inject(method = "shouldHighlightTarget", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void xenowarriors$noOutline(net.minecraft.world.entity.Entity e, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
