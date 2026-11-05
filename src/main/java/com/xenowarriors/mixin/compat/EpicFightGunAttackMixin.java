package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Epic Fight: when the left click is ours (a summoned gun shoots, a clash push, a wire bind to break, Rhalix's wires
 * whip, a phone call, knocked out - see client/OurClicks) there's no Epic Fight swing / combo. Its battle mode plays a
 * basic attack (and eats the vanilla attack click) only when this says yes, so it says no then.
 * (HEAD inject on Epic Fight's own method: nothing else hooks it; skipped quietly when Epic Fight isn't there.)
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch", remap = false)
public abstract class EpicFightGunAttackMixin {
    @Inject(method = "canPlayAttackAnimation", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void xenowarriors$gunOnlyShoots(CallbackInfoReturnable<Boolean> cir) {
        if (com.xenowarriors.client.OurClicks.attack(net.minecraft.client.Minecraft.getInstance().player)) cir.setReturnValue(false);
    }
}
