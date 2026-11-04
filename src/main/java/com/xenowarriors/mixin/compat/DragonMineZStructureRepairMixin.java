package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DragonMineZ: its "missing structure" repair doesn't force-place anything in the Recarnet (see compat.DmzRecarnetStructures). */
@Pseudo
@Mixin(targets = "com.dragonminez.server.world.structure.placement.StructureRepairManager", remap = false)
public abstract class DragonMineZStructureRepairMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$notInRecarnet(net.minecraft.server.level.ServerLevel level, CallbackInfo ci) {
        if (com.xenowarriors.compat.DmzRecarnetStructures.blocked(level)) ci.cancel();
    }
}
