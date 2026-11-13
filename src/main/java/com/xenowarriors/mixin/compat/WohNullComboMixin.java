package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Weapons of Harmony looks at every held weapon's combo each tick and assumed there always is one - but Epic Fight
 * has none for some ways of holding things (a TaCZ gun, a weapon in an unusual style), so the whole world crashed
 * as soon as the player shot (the user's video and crash reports). An empty combo there instead; nothing else changes.
 */
@Pseudo
@Mixin(targets = "net.kenji.woh.gameasset.animation_types.BasisAttackAnimation$ClientEvents", remap = false)
public abstract class WohNullComboMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "lambda$onPLayerTick$0", at = @At(value = "INVOKE",
            target = "Lyesman/epicfight/world/capabilities/item/WeaponCapability;getAutoAttackMotion(Lyesman/epicfight/world/capabilities/entitypatch/player/PlayerPatch;)Ljava/util/List;"),
            remap = false, require = 0)
    private static java.util.List xenowarriors$neverNull(yesman.epicfight.world.capabilities.item.WeaponCapability cap,
                                                         yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch<?> patch) {
        java.util.List l = cap.getAutoAttackMotion(patch);
        return l != null ? l : java.util.List.of();
    }
}
