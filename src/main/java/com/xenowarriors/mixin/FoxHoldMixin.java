package com.xenowarriors.mixin;

import com.xenowarriors.fox.FoxPets;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A pet fox holding its sword won't drop it to grab berries or other items. */
@Mixin(Fox.class)
public abstract class FoxHoldMixin {
    @Inject(method = "canHoldItem", at = @At("HEAD"), cancellable = true)
    private void xenowarriors$keepSword(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Fox fox = (Fox) (Object) this;
        if (fox.getMainHandItem().getItem() instanceof SwordItem && FoxPets.ownerOf(fox) != null) cir.setReturnValue(false);
    }
}
