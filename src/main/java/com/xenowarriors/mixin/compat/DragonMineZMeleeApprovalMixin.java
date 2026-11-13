package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * DragonMineZ 2.2 has its own melee system: a punch only counts when it came in through its own attack request
 * ("approved" target); every other player attack on a living thing is cancelled. Epic Fight's attacks (and its
 * mining-mode clicks) never go through that request, so with Epic Fight in the pack nobody could hurt anything except
 * with skills. With Epic Fight loaded we treat the hit as approved - DragonMineZ still adds its crits, sounds and
 * particles - unless it's a friend: yourself, a teammate / party member, or one of DragonMineZ's masters.
 */
@Pseudo
@Mixin(targets = "com.dragonminez.common.events.ForgeCommonEvents", remap = false)
public abstract class DragonMineZMeleeApprovalMixin {
    @Redirect(method = "onPlayerAttack", remap = false, require = 0,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lcom/dragonminez/common/combat/logic/player/TargetHelper;isApprovedMeleeTarget(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean xenowarriors$epicFightHits(net.minecraft.world.entity.Entity target,
                                                      net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (com.xenowarriors.compat.DmzMelee.approved(target)) return true;
        return com.xenowarriors.compat.DmzMelee.epicFightHit(event.getEntity(), target);
    }
}
