package com.xenowarriors.mixin.compat;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DragonMineZ gravity: a player caught in an enemy's Kishi dome feels its G on top of the world's, the way a DMZ
 * gravity machine adds to it (world + machine - 1). DragonMineZ then applies all of its own gravity rules to them
 * (slower movement and attacks, stat reduction, heavier falls, ki flight slowed or stopped, ki/stamina use). Runs on
 * both sides: the server's penalties, the client's jump/fall/flight. See compat.DmzKishiGravity.
 */
@Pseudo
@Mixin(targets = "com.dragonminez.server.util.GravityLogic", remap = false)
public abstract class DragonMineZGravityMixin {
    @Inject(method = "getGravityMultiplier", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$kishi(Player player, CallbackInfoReturnable<Double> cir) {
        try {
            com.xenowarriors.compat.DmzKishiGravity.mixinLive = true;
            double k = com.xenowarriors.compat.DmzKishiGravity.extraFor(player);
            if (k > 1) {
                if (!com.xenowarriors.compat.DmzKishiGravity.dmzGravityOn()) return;   // (DMZ gravity switched off: Kishi does it itself)
                Double v = cir.getReturnValue();
                double base = v == null ? 1 : v;
                cir.setReturnValue(Math.max(0, base) + k - 1);
            }
        } catch (Throwable ignored) {}
    }
}
