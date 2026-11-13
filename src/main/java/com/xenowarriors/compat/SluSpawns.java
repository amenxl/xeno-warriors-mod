package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Super Lucky Update (slu): its knights (Knight, Dark / Elite / Hollow / Noble / Mad... Knight) came in crowds
 * (the user's report). Natural and chunk-generation spawns of them are cut to about one in eight; spawn eggs,
 * spawners, commands and their own structures' summons are left alone.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SluSpawns {
    /** The share of their natural spawns that still happen. */
    public static final float KEEP = 0.125F;

    private SluSpawns() {}

    public static boolean knight(net.minecraft.world.entity.EntityType<?> t) {
        var id = ForgeRegistries.ENTITY_TYPES.getKey(t);
        return id != null && id.getNamespace().equals("slu") && id.getPath().contains("knight") && !id.getPath().startsWith("boss_");
    }

    @SubscribeEvent
    public static void onSpawn(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType why = event.getSpawnType();
        if (why != MobSpawnType.NATURAL && why != MobSpawnType.CHUNK_GENERATION) return;
        if (!knight(event.getEntity().getType())) return;
        if (event.getEntity().getRandom().nextFloat() >= KEEP) event.setSpawnCancelled(true);
    }
}
