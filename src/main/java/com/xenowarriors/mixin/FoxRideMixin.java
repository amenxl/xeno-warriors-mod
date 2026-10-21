package com.xenowarriors.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A fox riding a player sits on top of his head, facing the way he faces (and leaning with him in fast flight). */
@Mixin(Entity.class)
public abstract class FoxRideMixin {
    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("HEAD"), cancellable = true)
    private void xenowarriors$foxOnHead(Entity passenger, Entity.MoveFunction move, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof Player p) || !(passenger instanceof Fox fox)) return;
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
        float fast = (float) Mth.clamp((Math.sqrt(dx * dx + dz * dz) - 0.15) / 0.45, 0.0, 1.0);
        float lean = fast * 60F * Mth.DEG_TO_RAD;
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double up = 0.9 + 0.9 * Mth.cos(lean) + (p.isCrouching() ? -0.3 : 0);
        double fwd = 0.9 * Mth.sin(lean);
        move.accept(fox, p.getX() - Mth.sin(yaw) * fwd, p.getY() + up, p.getZ() + Mth.cos(yaw) * fwd);
        fox.setYRot(p.yBodyRot);
        fox.setYBodyRot(p.yBodyRot);
        fox.setYHeadRot(p.getYHeadRot());
        ci.cancel();
    }
}
