package com.xenowarriors.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.client.SoulLook;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Inside the Hekublade, everyone but Mikiko is drawn as a pale, see-through soul. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererSoulMixin {
    @Unique
    private static LivingEntity xenowarriors$current;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void xenowarriors$start(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light,
                                    CallbackInfo ci) {
        xenowarriors$current = SoulLook.isSoul(entity) ? entity : null;
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void xenowarriors$end(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light,
                                  CallbackInfo ci) {
        xenowarriors$current = null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void xenowarriors$soulType(LivingEntity entity, boolean bodyVisible, boolean translucent, boolean glowing,
                                       CallbackInfoReturnable<RenderType> cir) {
        if (xenowarriors$current == entity && entity != null && bodyVisible) {
            cir.setReturnValue(RenderType.itemEntityTranslucentCull(((EntityRenderer) (Object) this).getTextureLocation(entity)));
        }
    }

    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void xenowarriors$soulColor(EntityModel<?> model, PoseStack pose, VertexConsumer buffer, int light, int overlay,
                                        float r, float g, float b, float a) {
        if (xenowarriors$current != null) {
            model.renderToBuffer(pose, buffer, light, overlay, r * SoulLook.R, g * SoulLook.G, b * SoulLook.B, a * SoulLook.A);
        } else {
            model.renderToBuffer(pose, buffer, light, overlay, r, g, b, a);
        }
    }
}
