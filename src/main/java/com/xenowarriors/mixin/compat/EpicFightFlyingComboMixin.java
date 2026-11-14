package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flying (skill / creative-style flight), Epic Fight counted you as mid-jump and only ever gave you its one air attack:
 * now you fight with the same combo as on the ground (Mikiko's punches, Rhalix's Ender Blaster strikes...) in the air.
 * The player counts as standing for the moment the attack is picked (not a redirect: DragonMineZ's Epic Fight bridge
 * already redirects that check, for its own flight).
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.skill.BasicAttack", remap = false)
public abstract class EpicFightFlyingComboMixin {
    private static final ThreadLocal<net.minecraft.world.entity.player.Player> XW_LIFTED = new ThreadLocal<>();

    @Inject(method = "executeOnServer", at = @At("HEAD"), remap = false, require = 0)
    private void xenowarriors$standForCombo(yesman.epicfight.skill.SkillContainer container, net.minecraft.network.FriendlyByteBuf args, CallbackInfo ci) {
        try {
            var p = container.getExecutor().getOriginal();
            if (p != null && p.getAbilities().flying && !p.onGround()) { p.setOnGround(true); XW_LIFTED.set(p); }
        } catch (Throwable ignored) {}
    }

    @Inject(method = "executeOnServer", at = @At("RETURN"), remap = false, require = 0)
    private void xenowarriors$backInTheAir(yesman.epicfight.skill.SkillContainer container, net.minecraft.network.FriendlyByteBuf args, CallbackInfo ci) {
        var p = XW_LIFTED.get();
        if (p != null) { p.setOnGround(false); XW_LIFTED.remove(); }
    }
}
