package com.xenowarriors.mixin.compat;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DragonMineZ's Ki Sense auras turn dark purple while it's really Karma Sensing (see DmzKiSenseLink). */
@Pseudo
@Mixin(targets = "com.dragonminez.client.render.effects.KiSenseAuraRenderer", remap = false)
public abstract class DragonMineZKiSenseMixin {
    @Inject(method = "auraColors", at = @At("RETURN"), remap = false, require = 0)
    private static void xenowarriors$karmaColors(LivingEntity e, double d, CallbackInfoReturnable<float[][]> cir) {
        com.xenowarriors.client.compat.DmzKiSenseLink.recolor(e, cir.getReturnValue());
    }
}
