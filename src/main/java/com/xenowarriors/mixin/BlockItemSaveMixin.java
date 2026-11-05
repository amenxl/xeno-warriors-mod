package com.xenowarriors.mixin;

import com.xenowarriors.stats.Building;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Building level: a chance the placed block isn't used up. Only injects around {@code place} (no redirects), so other
 * mods' mixins on it still work; the item is given back before Forge's place event, which restores the old stack if
 * the placement gets cancelled - so a cancelled placement can't dupe.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemSaveMixin {
    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"))
    private void xenowarriors$beforePlace(BlockPlaceContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        Building.beforePlace(ctx);
    }

    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;", at = @At("RETURN"))
    private void xenowarriors$afterPlace(BlockPlaceContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        Building.afterPlace(ctx, cir.getReturnValue());
    }
}
