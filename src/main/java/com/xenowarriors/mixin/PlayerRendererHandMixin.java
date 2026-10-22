package com.xenowarriors.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.client.LimbBend;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The first-person hand stays straight (no elbow bend). */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererHandMixin {
    @Inject(method = "renderHand", at = @At("HEAD"))
    private void xenowarriors$handStart(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                        ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        LimbBend.firstPerson = true;
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void xenowarriors$handEnd(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                      ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        LimbBend.firstPerson = false;
    }
}
