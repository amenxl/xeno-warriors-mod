package com.xenowarriors.mixin;

import com.xenowarriors.client.SkillAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds skill poses (Kishi cast, Earth Wave stomp) on top of the normal player animation. This runs at the end of
 * HumanoidModel.setupAnim, so PlayerModel still copies the pose onto the sleeves and pants
 * afterwards, and armor and the 3D details copy it from the player model.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void xenowarriors$skillPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                        float netHeadYaw, float headPitch, CallbackInfo ci) {
        Object self = this;
        if (self instanceof PlayerModel<?> && entity instanceof Player) {
            if (!SkillAnimations.apply((HumanoidModel<?>) self, entity, ageInTicks)) {
                com.xenowarriors.client.mikiko.MikikoAnimations.apply((HumanoidModel<?>) self, (Player) entity, ageInTicks);
                com.xenowarriors.client.RhalixWingsClient.applyBody((HumanoidModel<?>) self, (Player) entity, ageInTicks);
                com.xenowarriors.client.JumpAnimations.apply((HumanoidModel<?>) self, (Player) entity, ageInTicks);
                com.xenowarriors.client.party.PartyClient.applyPose((HumanoidModel<?>) self, (Player) entity, ageInTicks);
                com.xenowarriors.client.ChargePose.apply((HumanoidModel<?>) self, (Player) entity, ageInTicks);
            }
        }
    }
}
