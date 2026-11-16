package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Our borrowed moves never play slower than their own speed (see compat/epicfight/WomMoves.playSpeed); faster ones stay fast. */
@Pseudo
@Mixin(targets = "yesman.epicfight.api.animation.types.AttackAnimation", remap = false)
public abstract class EpicFightAttackSpeedMixin {
    @Inject(method = "getPlaySpeed", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void xenowarriors$speed(yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch<?> patch,
                                    yesman.epicfight.api.animation.types.DynamicAnimation anim, CallbackInfoReturnable<Float> cir) {
        Float s = com.xenowarriors.compat.epicfight.WomMoves.playSpeed(patch, this);
        if (s != null && cir.getReturnValue() != null && cir.getReturnValue() < s) cir.setReturnValue(s);
    }
}
