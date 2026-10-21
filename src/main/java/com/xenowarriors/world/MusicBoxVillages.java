package com.xenowarriors.world;

import com.mojang.datafixers.util.Pair;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.mixin.TemplatePoolAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the Music Box building to every kind of village: a little hall with a jukebox and a chest holding every
 * Xeno Warriors disc.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MusicBoxVillages {
    private static final String[] VILLAGES = {"plains", "desert", "savanna", "snowy", "taiga"};
    private static final int WEIGHT = 4;

    private MusicBoxVillages() {}

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        var access = event.getServer().registryAccess();
        Registry<StructureTemplatePool> pools = access.registry(Registries.TEMPLATE_POOL).orElse(null);
        Registry<StructureProcessorList> processors = access.registry(Registries.PROCESSOR_LIST).orElse(null);
        if (pools == null || processors == null) return;
        Holder<StructureProcessorList> empty = processors.getHolder(
                ResourceKey.create(Registries.PROCESSOR_LIST, new ResourceLocation("minecraft", "empty"))).orElse(null);
        if (empty == null) return;
        for (String village : VILLAGES) {
            add(pools, empty, new ResourceLocation("minecraft", "village/" + village + "/houses"));
        }
    }

    private static void add(Registry<StructureTemplatePool> pools, Holder<StructureProcessorList> processors, ResourceLocation poolId) {
        StructureTemplatePool pool = pools.get(poolId);
        if (pool == null) return;
        TemplatePoolAccessor acc = (TemplatePoolAccessor) pool;
        StructurePoolElement piece = SinglePoolElement.legacy(XenoWarriors.id("village/music_box").toString(), processors)
                .apply(StructureTemplatePool.Projection.RIGID);
        for (int i = 0; i < WEIGHT; i++) acc.xenowarriors$templates().add(piece);
        List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(acc.xenowarriors$rawTemplates());
        raw.add(Pair.of(piece, WEIGHT));
        acc.xenowarriors$setRawTemplates(raw);
    }
}
