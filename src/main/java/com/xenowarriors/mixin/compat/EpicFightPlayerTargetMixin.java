package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No lock-on target: the enemy right in front counts, so dashing attacks stop at it (EfTargetAssist). Server side. */
@Pseudo
@Mixin(targets = "yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch", remap = false)
public abstract class EpicFightPlayerTargetMixin {
    @Inject(method = "getTarget", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void xenowarriors$assist(CallbackInfoReturnable<net.minecraft.world.entity.LivingEntity> cir) {
        if (cir.getReturnValue() != null) return;
        Object self = this;
        if (self instanceof yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch<?> pp && pp.getOriginal() != null && pp.isEpicFightMode())
            cir.setReturnValue(com.xenowarriors.compat.EfTargetAssist.target(pp.getOriginal()));
    }
}
