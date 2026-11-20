package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Soulslike Universe (slu): its knights (Knight, Dark / Elite / Hollow / Noble / Mad... Knight) came in crowds
 * (the user's report). It adds them to every biome of every dimension, so where few other mobs spawn (the Nether, the
 * End, other mods' dimensions, the Recarnet) they filled the place. Natural and chunk-generation spawns of them are
 * cut to about one in twenty-five in the Overworld, one in a hundred in other dimensions and one in a hundred and fifty
 * in the Recarnet - and never more than two near each other anywhere (one in other dimensions). Spawn eggs, spawners,
 * commands and their own structures' summons are left alone.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SluSpawns {
    /** The share of their natural spawns that still happen: the Overworld (was 1 in 8: the user - still a lot). */
    public static final float KEEP = 0.04F;
    /** Other dimensions (the Nether, the End, other mods'): they had little else spawning there. */
    public static final float KEEP_OTHER = 0.01F;
    /** In the Recarnet (few other mobs there, so they filled it). */
    public static final float KEEP_RECARNET = 0.007F;
    /** No more than this many near each other (48 blocks); one in other dimensions and the Recarnet. */
    public static final int NEAR = 2, RECARNET_NEAR = 1;

    private SluSpawns() {}

    public static boolean knight(net.minecraft.world.entity.EntityType<?> t) {
        var id = ForgeRegistries.ENTITY_TYPES.getKey(t);
        return id != null && id.getNamespace().equals("slu") && id.getPath().contains("knight") && !id.getPath().startsWith("boss_");
    }

    /** (Tests) the share kept in this dimension. */
    public static float keep(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        if (dim == com.xenowarriors.recarnet.Recarnet.LEVEL) return KEEP_RECARNET;
        return dim == net.minecraft.world.level.Level.OVERWORLD ? KEEP : KEEP_OTHER;
    }

    @SubscribeEvent
    public static void onSpawn(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType why = event.getSpawnType();
        if (why != MobSpawnType.NATURAL && why != MobSpawnType.CHUNK_GENERATION) return;
        if (!knight(event.getEntity().getType())) return;
        var e = event.getEntity();
        var dim = e.level().dimension();
        if (e.getRandom().nextFloat() >= keep(dim)) { event.setSpawnCancelled(true); return; }
        int near = dim == net.minecraft.world.level.Level.OVERWORLD ? NEAR : RECARNET_NEAR;
        if (event.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, e.getBoundingBox().inflate(48), m -> knight(m.getType())).size() >= near)
            event.setSpawnCancelled(true);
    }
}
