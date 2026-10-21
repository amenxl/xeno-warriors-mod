package com.xenowarriors.mixin.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DragonMineZ compatibility: DragonMineZ draws the first-person arm with its own race skin. For Xeno Warriors
 * characters, draw the normal arm instead (with Mikiko's / Rhalix's skin).
 */
@Pseudo
@Mixin(targets = "com.dragonminez.client.render.DMZRenderHand", remap = false)
public abstract class DragonMineZHandMixin {
    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void xenowarriors$rightHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, CallbackInfo ci) {
        PlayerRenderer r = xenowarriors$vanilla(player);
        if (r != null) {
            r.renderRightHand(pose, buffers, light, player);
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void xenowarriors$leftHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, CallbackInfo ci) {
        PlayerRenderer r = xenowarriors$vanilla(player);
        if (r != null) {
            r.renderLeftHand(pose, buffers, light, player);
            ci.cancel();
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static PlayerRenderer xenowarriors$vanilla(AbstractClientPlayer player) {
        if (ClientCharacterData.get(player) == CharacterType.NONE) return null;
        return Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer pr ? pr : null;
    }
}
