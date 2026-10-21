package com.xenowarriors.mixin;

import com.xenowarriors.client.fox.FoxClientFx;
import net.minecraft.client.model.FoxModel;
import net.minecraft.world.entity.animal.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extra moves for pet foxes (tail wagging, head tilts) on top of the vanilla pose. */
@Mixin(FoxModel.class)
public abstract class FoxModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/animal/Fox;FFFFF)V", at = @At("TAIL"))
    private void xenowarriors$petAnim(Fox fox, float limbSwing, float limbAmount, float age, float yaw, float pitch, CallbackInfo ci) {
        FoxClientFx.animate((FoxModel<?>) (Object) this, fox, limbSwing, limbAmount, age);
    }
}
