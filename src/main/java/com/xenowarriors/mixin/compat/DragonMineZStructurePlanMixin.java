package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
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
}
