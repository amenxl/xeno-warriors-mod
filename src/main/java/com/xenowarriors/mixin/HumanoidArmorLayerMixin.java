package com.xenowarriors.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.client.render.CharacterArmorLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the normal armor model on Mikiko and Rhalix - CharacterArmorLayer draws fitted armor instead. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void xenowarriors$hideForCharacters(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity,
                                                float a, float b, float c, float d, float e, float f, CallbackInfo ci) {
        if (CharacterArmorLayer.replacesVanilla(entity)) ci.cancel();
    }
}
