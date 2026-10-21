package com.xenowarriors.mixin;

import com.xenowarriors.client.UltimateCutscene;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the ultimate cutscene take over the camera. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At("TAIL"))
    private void xenowarriors$cutsceneCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                             float partialTick, CallbackInfo ci) {
        double[] cam = UltimateCutscene.camera(partialTick);
        if (cam == null) return;
        this.detached = true;
        setRotation((float) cam[3], (float) cam[4]);
        setPosition(cam[0], cam[1], cam[2]);
    }
}
