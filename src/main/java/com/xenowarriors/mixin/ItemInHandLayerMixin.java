package com.xenowarriors.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.client.mikiko.MikikoAnimations;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While Mikiko has tossed his Hekublade in the air or slung it on his back, don't also draw it in his hand. */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void xenowarriors$hideTossedBlade(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                              PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (arm == entity.getMainArm() && (MikikoAnimations.bladeAwayFromHand(entity) || MikikoAnimations.swordShouldered(entity))) ci.cancel();
    }
}
