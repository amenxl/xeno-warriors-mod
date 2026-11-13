package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Epic Fight + Weapons of Miracles: our characters' bare-hand combos and our weapons' combos use WOM's attack
 * animations (see compat/epicfight/WomMoves). Skipped quietly without Epic Fight; a no-op without WOM.
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.world.capabilities.item.WeaponCapability", remap = false)
public abstract class EpicFightWeaponCapabilityWomMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getAutoAttackMotion", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void xenowarriors$wom(yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch<?> patch, CallbackInfoReturnable<java.util.List> cir) {
        java.util.List out = com.xenowarriors.compat.epicfight.WomMoves.override(patch, cir.getReturnValue());
        if (out != cir.getReturnValue()) cir.setReturnValue(out);
    }
}
