package com.xenowarriors.mixin.compat;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DragonMineZ compatibility: DragonMineZ draws every player with its own model, which would hide Mikiko and Rhalix.
 * When a player has picked a Xeno Warriors character, tell DragonMineZ it has no renderer for them, so the normal
 * player renderer (with all of our layers and animations) draws them. Everyone else still gets DragonMineZ's look.
 * Only applied when DragonMineZ is installed (@Pseudo).
 */
@Pseudo
@Mixin(targets = "com.dragonminez.client.render.DMZRendererCache", remap = false)
public abstract class DragonMineZRendererMixin {
    @org.spongepowered.asm.mixin.Unique
    private static boolean xenowarriors$ours(Player player) {
        return player instanceof AbstractClientPlayer p && ClientCharacterData.get(p) != CharacterType.NONE;
    }

    @Inject(method = "getRenderer", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$skipRenderer(Player player, CallbackInfoReturnable<Object> cir) {
        if (xenowarriors$ours(player)) cir.setReturnValue(null);
    }

    @Inject(method = "getTPRenderer", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$skipTPRenderer(Player player, CallbackInfoReturnable<Object> cir) {
        if (xenowarriors$ours(player)) cir.setReturnValue(null);
    }
}
