package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Super Lucky Update (slu): its knights (Knight, Dark / Elite / Hollow / Noble / Mad... Knight) came in crowds
 * (the user's report). Natural and chunk-generation spawns of them are cut to about one in eight (in the Recarnet one in
 * fifty, and no more than two near each other); spawn eggs,
 * spawners, commands and their own structures' summons are left alone.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SluSpawns {
    /** The share of their natural spawns that still happen. */
    public static final float KEEP = 0.125F;
    /** In the Recarnet (few other mobs there, so they filled it): far fewer still, and never more than a couple near each other. */
    public static final float KEEP_RECARNET = 0.02F;
    public static final int RECARNET_NEAR = 2;

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
        var e = event.getEntity();
        boolean recarnet = e.level() instanceof net.minecraft.world.level.Level l && l.dimension() == com.xenowarriors.recarnet.Recarnet.LEVEL;
        if (e.getRandom().nextFloat() >= (recarnet ? KEEP_RECARNET : KEEP)) { event.setSpawnCancelled(true); return; }
        if (recarnet && event.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, e.getBoundingBox().inflate(48), m -> knight(m.getType())).size() >= RECARNET_NEAR)
            event.setSpawnCancelled(true);
    }
}
