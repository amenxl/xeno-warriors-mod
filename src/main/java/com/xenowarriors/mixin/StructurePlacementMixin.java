package com.xenowarriors.mixin;

import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DragonMineZ's own placement types (one-per-world "unique" sites) answer "not here" for the Recarnet's chunks
 * without running DragonMineZ's world-wide site search for it (see compat.DmzRecarnetStructures).
 */
@Mixin(StructurePlacement.class)
public abstract class StructurePlacementMixin {
    @Inject(method = "isStructureChunk", at = @At("HEAD"), cancellable = true, require = 0)
    private void xenowarriors$noDmzInRecarnet(ChunkGeneratorStructureState state, int x, int z, CallbackInfoReturnable<Boolean> cir) {
        if (((Object) this).getClass().getName().startsWith("com.dragonminez.")
                && com.xenowarriors.compat.DmzRecarnetStructures.blocked(((ChunkGeneratorStructureStateAccessor) state).xenowarriors$biomeSource()))
            cir.setReturnValue(false);
    }
}
