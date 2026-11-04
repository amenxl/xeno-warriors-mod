package com.xenowarriors.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No DragonMineZ structure starts in the Recarnet's chunks (see compat.DmzRecarnetStructures). */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorStructureMixin {
    @Shadow @Final protected BiomeSource biomeSource;

    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true, require = 0)
    private void xenowarriors$noDmzInRecarnet(StructureSet.StructureSelectionEntry entry, StructureManager structures, RegistryAccess access, RandomState random,
                                              StructureTemplateManager templates, long seed, ChunkAccess chunk, ChunkPos pos, SectionPos section,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (!com.xenowarriors.compat.DmzRecarnetStructures.blocked(biomeSource)) return;
        var key = entry.structure().unwrapKey();
        if (key.isPresent() && key.get().location().getNamespace().equals("dragonminez")) {
            com.xenowarriors.compat.DmzRecarnetStructures.note(key.get().location().toString());
            cir.setReturnValue(false);
        }
    }
}
