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
 * DragonMineZ compatibility: DragonMineZ takes over left-click with its own melee combo system and animations,
 * which replaced Mikiko's and Rhalix's attacks. For Xeno Warriors characters (on the client), report "no
 * DragonMineZ attack" so left-click falls back to the normal attack - which our combos, damage and attack
 * animations are built on. DragonMineZ handles a missing attack safely everywhere it asks.
 */
@Pseudo
@Mixin(targets = "com.dragonminez.common.combat.logic.player.PlayerAttackHelper", remap = false)
public abstract class DragonMineZAttackMixin {
    @Inject(method = "getCurrentAttack", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$ourAttacks(Player player, int combo, CallbackInfoReturnable<Object> cir) {
        if (player != null && player.level().isClientSide && player instanceof AbstractClientPlayer p
                && ClientCharacterData.get(p) != CharacterType.NONE && !com.xenowarriors.client.compat.DmzForms.dmzDraws(p)) {
            cir.setReturnValue(null);
        }
    }
}
