package com.xenowarriors.mixin;

import com.xenowarriors.client.forms.GunClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Aiming a summoned gun down the sights turns the mouse down with the zoom (like TACZ guns). Optional
 * ({@code require = 0}): if another mod changes this spot, aiming still works, just at full sensitivity.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerGunMixin {
    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0, require = 0)
    private double xenowarriors$gunYaw(double yaw) { return yaw * GunClient.sensitivity(); }

    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1, require = 0)
    private double xenowarriors$gunPitch(double pitch) { return pitch * GunClient.sensitivity(); }
}
