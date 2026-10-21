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
 * DragonMineZ compatibility: its "animated first person" hides the normal hands and draws its own body instead -
 * which we switch off for Xeno Warriors characters, so nothing was drawn at all (invisible hands). For them, turn
 * that mode off so the normal first-person hands come back.
 */
@Pseudo
@Mixin(targets = "com.dragonminez.client.render.firstperson.dto.FirstPersonManager", remap = false)
public abstract class DragonMineZFirstPersonMixin {
    @Inject(method = "shouldRenderFirstPerson", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$normalHands(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof AbstractClientPlayer p && ClientCharacterData.get(p) != CharacterType.NONE) cir.setReturnValue(false);
    }
}
