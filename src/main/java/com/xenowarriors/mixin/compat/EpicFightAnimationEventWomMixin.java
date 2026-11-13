package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Weapons of Miracles' attacks played through our combos (compat/epicfight/WomMoves): some of their animation events
 * expect WOM's own weapon (its passive skill's data - e.g. the Satsujin's sheath) and throw on anything else, which
 * would crash the game. Only for a WOM animation on a player whose combo we swapped: such an event is skipped (the move
 * itself still plays). Anything else is left exactly as it was.
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.api.animation.property.AnimationEvent", remap = false)
public abstract class EpicFightAnimationEventWomMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = {"execute", "executeWithNewParams"}, remap = false, require = 0,
            at = @At(value = "INVOKE", remap = false, target = "Lyesman/epicfight/api/animation/property/AnimationEvent$Event;fire(Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;Lyesman/epicfight/api/asset/AssetAccessor;Lyesman/epicfight/api/animation/property/AnimationParameters;)V"))
    private void xenowarriors$womGuard(yesman.epicfight.api.animation.property.AnimationEvent.Event event, yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch patch,
                                       yesman.epicfight.api.asset.AssetAccessor anim, yesman.epicfight.api.animation.property.AnimationParameters params) {
        try {
            event.fire(patch, anim, params);
        } catch (RuntimeException e) {
            if (!com.xenowarriors.compat.epicfight.WomMoves.guard(patch, anim, e)) throw e;
        }
    }
}
