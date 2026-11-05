package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No black-and-white screen while sensing: DragonMineZ's Ki Sense search (which our Karma Sensing also drives, see
 * DmzKiSenseLink) greys out the whole world with a post-processing pass. That pass is skipped - the auras, outlines
 * and power readings all stay, the world just keeps its colors.
 */
@Pseudo
@Mixin(targets = "com.dragonminez.client.render.shader.SearchGrayscaleManager", remap = false)
public abstract class DragonMineZNoGreyMixin {
    @Inject(method = "process(FZ)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$noGrey(float partial, boolean keepDepth, CallbackInfo ci) {
        com.xenowarriors.client.compat.DmzKiSenseLink.greySkipped++;
        ci.cancel();
    }
}
