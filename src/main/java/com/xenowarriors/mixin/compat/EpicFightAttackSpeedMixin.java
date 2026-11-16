package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Our borrowed bare-hand moves play at their own speed (see compat/epicfight/WomMoves.playSpeed). */
@Pseudo
@Mixin(targets = "yesman.epicfight.api.animation.types.AttackAnimation", remap = false)
public abstract class EpicFightAttackSpeedMixin {
    @Inject(method = "getPlaySpeed", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void xenowarriors$speed(yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch<?> patch,
                                    yesman.epicfight.api.animation.types.DynamicAnimation anim, CallbackInfoReturnable<Float> cir) {
        Float s = com.xenowarriors.compat.epicfight.WomMoves.playSpeed(patch, this);
        if (s != null) cir.setReturnValue(s);
    }
}
