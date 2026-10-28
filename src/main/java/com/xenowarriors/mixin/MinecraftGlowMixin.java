package com.xenowarriors.mixin;

import com.xenowarriors.client.KarmaSense;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Karma Sensing: sensed entities glow (outline through walls) for you only. */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void xenowarriors$karmaGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (KarmaSense.isSensed(entity) || com.xenowarriors.client.ScannerGlow.has(entity)) cir.setReturnValue(true);
    }
}
