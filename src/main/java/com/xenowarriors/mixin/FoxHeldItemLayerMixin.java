package com.xenowarriors.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.client.fox.FoxSwordLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.FoxHeldItemLayer;
import net.minecraft.world.entity.animal.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A sword in a fox's mouth is drawn as a full 3D sword by FoxSwordLayer instead of the flat item. */
@Mixin(FoxHeldItemLayer.class)
public abstract class FoxHeldItemLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/animal/Fox;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void xenowarriors$swordIn3d(PoseStack pose, MultiBufferSource buffers, int light, Fox fox, float limbSwing,
                                        float limbAmount, float partialTick, float age, float yaw, float pitch, CallbackInfo ci) {
        if (FoxSwordLayer.handles(fox.getMainHandItem())) ci.cancel();
    }
}
