package com.xenowarriors.mixin;

import com.xenowarriors.client.KarmaSense;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Karma Sensing outline colors (client only). */
@Mixin(Entity.class)
public abstract class EntityTeamColorMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void xenowarriors$karmaColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide && KarmaSense.isSensed(self)) cir.setReturnValue(KarmaSense.color(self));
    }
}
