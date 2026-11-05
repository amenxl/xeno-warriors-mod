package com.xenowarriors.mixin;

import com.xenowarriors.client.forms.GunClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aiming a summoned gun down the sights turns the mouse down with the zoom (like TACZ guns).
 * Scales the mouse movement gathered for this frame at the start of turnPlayer, so it never touches
 * the LocalPlayer.turn call that other mods (e.g. Epic Fight) redirect - an @ModifyArg there crashed
 * the game on load in a real modpack. Optional: if it can't apply, aiming works at full sensitivity.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerGunMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), require = 0)
    private void xenowarriors$gunSensitivity(CallbackInfo ci) {
        double s = GunClient.sensitivity();
        if (s != 1.0) {
            this.accumulatedDX *= s;
            this.accumulatedDY *= s;
        }
    }
}
