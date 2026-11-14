package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Flying (skill / creative-style flight), Epic Fight counted you as mid-jump and only ever gave you its one air attack:
 * now you fight with the same combo as on the ground (Mikiko's punches, Rhalix's Ender Blaster strikes...) in the air.
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.skill.BasicAttack", remap = false)
public abstract class EpicFightFlyingComboMixin {
    @Redirect(method = "executeOnServer", remap = false, require = 0,
            at = @At(value = "INVOKE", remap = false, target = "Lnet/minecraft/world/entity/player/Player;m_20096_()Z"))   // (onGround - the production name)
    private boolean xenowarriors$flyingCombo(net.minecraft.world.entity.player.Player p) {
        return p.onGround() || p.getAbilities().flying;
    }
}
