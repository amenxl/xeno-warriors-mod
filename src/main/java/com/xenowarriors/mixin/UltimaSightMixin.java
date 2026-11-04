package com.xenowarriors.mixin;

import com.xenowarriors.client.UltimaSight;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ultima's eyes: invisible players and creatures still show for him, see-through like a spectator sees them (client only). */
@Mixin(Entity.class)
public abstract class UltimaSightMixin {
    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void xenowarriors$ultimaSees(Player viewer, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide && self != viewer && UltimaSight.sees(viewer)) cir.setReturnValue(false);
    }
}
