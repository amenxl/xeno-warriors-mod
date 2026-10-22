package com.xenowarriors.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.client.LimbBend;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Elbows and knees on the player model (see LimbBend). */
@Mixin(PlayerModel.class)
public abstract class PlayerModelBendMixin {
    @Inject(method = "createMesh", at = @At("RETURN"))
    private static void xenowarriors$splitLimbs(CubeDeformation deformation, boolean slim, CallbackInfoReturnable<MeshDefinition> cir) {
        LimbBend.splitPlayerMesh(cir.getReturnValue(), deformation, slim);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void xenowarriors$bend(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                   float netHeadYaw, float headPitch, CallbackInfo ci) {
        LimbBend.apply((PlayerModel<?>) (Object) this, entity);
    }

    /** Held items follow the bent forearm into the hand. */
    @Inject(method = "translateToHand", at = @At("TAIL"))
    private void xenowarriors$toHand(HumanoidArm arm, PoseStack pose, CallbackInfo ci) {
        PlayerModel<?> self = (PlayerModel<?>) (Object) this;
        LimbBend.toForearm(arm == HumanoidArm.RIGHT ? self.rightArm : self.leftArm, pose);
    }
}
