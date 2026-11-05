package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Epic Fight: with a summoned gun in hand the attack button only shoots - no Epic Fight swing / combo. Its battle mode
 * plays a basic attack (and eats the vanilla attack click) only when this says yes, so it says no while a gun is held.
 * (HEAD inject on Epic Fight's own method: nothing else hooks it; skipped quietly when Epic Fight isn't there.)
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch", remap = false)
public abstract class EpicFightGunAttackMixin {
    @Inject(method = "canPlayAttackAnimation", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void xenowarriors$gunOnlyShoots(CallbackInfoReturnable<Boolean> cir) {
        if (com.xenowarriors.client.forms.GunClient.held(net.minecraft.client.Minecraft.getInstance().player) != null) {
            com.xenowarriors.client.forms.GunClient.efSwingsBlocked++;
            cir.setReturnValue(false);
        }
    }
}
