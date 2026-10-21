package com.xenowarriors.mixin;

import com.xenowarriors.client.ClientSkillState;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Netsu drains health a little every tick; normally each tiny drop plays the "got hurt" camera jolt, which shook the
 * whole screen. While Netsu burns, the health just goes down quietly.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerNetsuMixin {
    @Inject(method = "hurtTo", at = @At("HEAD"), cancellable = true)
    private void xenowarriors$quietDrain(float health, CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (ClientSkillState.netsuActive(self.getId()) && health < self.getHealth() && self.getHealth() - health < 1F) {
            self.setHealth(health);
            ci.cancel();
        }
    }
}
