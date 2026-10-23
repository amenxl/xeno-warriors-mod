package com.xenowarriors.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sneaking in Sky Boots doesn't stop you at the edge: you walk right off it, onto the sky. */
@Mixin(Player.class)
public abstract class PlayerSkyWalkMixin {
    @Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
    private void xenowarriors$skyWalk(Vec3 vec, MoverType type, CallbackInfoReturnable<Vec3> cir) {
        Player self = (Player) (Object) this;
        if (self.isShiftKeyDown() && self.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof com.xenowarriors.sky.SkyArmorItem) cir.setReturnValue(vec);
    }
}
