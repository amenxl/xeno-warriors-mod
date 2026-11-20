package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DragonMineZ: no structure plan for the Recarnet (see compat.DmzRecarnetStructures). */
@Pseudo
@Mixin(targets = "com.dragonminez.server.world.structure.placement.StructureSpawnPlanner", remap = false)
public abstract class DragonMineZStructurePlanMixin {
    @Inject(method = "onLevelLoad", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$notInRecarnet(net.minecraft.server.level.ServerLevel level, CallbackInfo ci) {
        if (com.xenowarriors.compat.DmzRecarnetStructures.blocked(level)) { com.xenowarriors.compat.DmzRecarnetStructures.note("structure plan"); ci.cancel(); }
    }

    /**
     * A new world sat 30 seconds at "Preparing spawn area" while DragonMineZ planned its structures (the test logs: the
     * plan never finished in that time anyway, and carried on in the background). Waits a few seconds instead.
     */
    @ModifyConstant(method = "precomputeAndWait", constant = @Constant(longValue = 30L), remap = false, require = 0)
    private static long xenowarriors$shorterWait(long secs) {
        return Math.round(com.xenowarriors.XwConfig.get(com.xenowarriors.XwConfig.DMZ_STRUCTURE_WAIT));
    }
}
