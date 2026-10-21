package com.xenowarriors.mixin;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Lets us add the Music Box to the village house pools. */
@Mixin(StructureTemplatePool.class)
public interface TemplatePoolAccessor {
    @Accessor("templates")
    ObjectArrayList<StructurePoolElement> xenowarriors$templates();

    @Accessor("rawTemplates")
    List<Pair<StructurePoolElement, Integer>> xenowarriors$rawTemplates();

    @Mutable
    @Accessor("rawTemplates")
    void xenowarriors$setRawTemplates(List<Pair<StructurePoolElement, Integer>> list);
}
