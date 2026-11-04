package com.xenowarriors.compat;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;

/**
 * With DragonMineZ: its structures (Kame House, Goku's house, the saga sites...) belong to its own worlds and the
 * overworld, not the Recarnet. The Recarnet is built on the overworld's biomes, so DragonMineZ planned a second set
 * of them there and its StructureRepairManager force-placed any it found missing - several seconds of server stall
 * each, the first time a player went to Aurora. The mixins in mixin/compat (DragonMineZStructure*Mixin and
 * ChunkGeneratorStructureMixin) ask here. Config: dragonminez.structuresInRecarnet.
 */
public final class DmzRecarnetStructures {
    private static int skipped;

    private DmzRecarnetStructures() {}

    private static boolean on() { return !com.xenowarriors.XwConfig.get(com.xenowarriors.XwConfig.DMZ_STRUCTURES_IN_RECARNET); }

    /** A chunk generator / structure state with this biome source is the Recarnet's. */
    public static boolean blocked(BiomeSource s) { return s instanceof com.xenowarriors.recarnet.RecarnetBiomeSource && on(); }

    public static boolean blocked(Level l) { return l != null && com.xenowarriors.recarnet.Recarnet.is(l) && on(); }

    public static void note(String what) {
        if (skipped++ < 3) com.xenowarriors.XenoWarriors.LOGGER.info("XW dmz structures: {} skipped in the Recarnet", what);
    }

    public static int skipped() { return skipped; }
}
