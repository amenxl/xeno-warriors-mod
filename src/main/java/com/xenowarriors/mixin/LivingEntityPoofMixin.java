package com.xenowarriors.mixin;

import com.xenowarriors.client.mikiko.BladeAbsorbClient;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No death "poof" for mobs sealed into the Hekublade - they're already gone. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPoofMixin {
    @Inject(method = "makePoofParticles", at = @At("HEAD"), cancellable = true)
    private void xenowarriors$noPoof(CallbackInfo ci) {
        if (BladeAbsorbClient.isAbsorbing(((LivingEntity) (Object) this).getId())) ci.cancel();
    }
}
