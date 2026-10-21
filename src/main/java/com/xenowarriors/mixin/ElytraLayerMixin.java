package com.xenowarriors.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rhalix has his own wings - an elytra he wears stays hidden (it still works for gliding). */
@Mixin(ElytraLayer.class)
public abstract class ElytraLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void xenowarriors$hideOnRhalix(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity,
                                           float a, float b, float c, float d, float e, float f, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer p && ClientCharacterData.get(p) == CharacterType.RHALIX) ci.cancel();
    }
}
