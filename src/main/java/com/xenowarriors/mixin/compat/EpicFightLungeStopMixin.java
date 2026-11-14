package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A player's attack lunge stops at the enemy instead of carrying them through it (see EfTargetAssist.stopAtTarget). */
@Pseudo
@Mixin(targets = "yesman.epicfight.api.animation.types.ActionAnimation", remap = false)
public abstract class EpicFightLungeStopMixin {
    @Redirect(method = "move", remap = false, require = 0,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/world/entity/LivingEntity;m_6478_(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void xenowarriors$stopAtTarget(net.minecraft.world.entity.LivingEntity e, net.minecraft.world.entity.MoverType type, net.minecraft.world.phys.Vec3 step) {
        if (e instanceof net.minecraft.world.entity.player.Player p && (Object) this instanceof yesman.epicfight.api.animation.types.AttackAnimation)
            step = com.xenowarriors.compat.EfTargetAssist.stopAtTarget(p, step);
        e.move(type, step);
    }
}
