package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Epic Fight's lock-on skips the same things ours does: anything you can't hurt (for Mikiko, ordinary people...). */
@Pseudo
@Mixin(targets = "yesman.epicfight.api.client.camera.EpicFightCameraAPI", remap = false)
public abstract class EpicFightLockOnFilterMixin {
    @Inject(method = "predicateFocusableEntity", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void xenowarriors$onlyHurtable(net.minecraft.world.entity.Entity e, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && e instanceof net.minecraft.world.entity.LivingEntity l && !com.xenowarriors.client.LockOn.attackable(l))
            cir.setReturnValue(false);
    }
}
