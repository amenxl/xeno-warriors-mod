package com.xenowarriors.corrupted;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModCorrupted {
    public static final RegistryObject<EntityType<CorruptedVillagerEntity>> CORRUPTED_VILLAGER = ModEntities.ENTITY_TYPES.register(
            "corrupted_villager", () -> EntityType.Builder.<CorruptedVillagerEntity>of(CorruptedVillagerEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).build("corrupted_villager"));

    public static final RegistryObject<EntityType<CorruptedSoulEntity>> CORRUPTED_SOUL = ModEntities.ENTITY_TYPES.register(
            "corrupted_soul", () -> EntityType.Builder.<CorruptedSoulEntity>of(CorruptedSoulEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.8F).fireImmune().clientTrackingRange(8).build("corrupted_soul"));

    public static final RegistryObject<EntityType<CSoulBossEntity>> C_SOUL = ModEntities.ENTITY_TYPES.register(
            "c_soul", () -> EntityType.Builder.<CSoulBossEntity>of(CSoulBossEntity::new, MobCategory.MONSTER)
                    .sized(2.2F, 3.0F).fireImmune().clientTrackingRange(12).build("c_soul"));

    public static final RegistryObject<EntityType<SoulBoltEntity>> SOUL_BOLT = ModEntities.ENTITY_TYPES.register(
            "soul_bolt", () -> EntityType.Builder.<SoulBoltEntity>of(SoulBoltEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(1).build("soul_bolt"));

    private ModCorrupted() {}

    /** Called from the mod constructor so the entries are registered before ENTITY_TYPES fires. */
    public static void init() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(CORRUPTED_VILLAGER.get(), CorruptedVillagerEntity.createAttributes().build());
        event.put(CORRUPTED_SOUL.get(), CorruptedSoulEntity.createAttributes().build());
        event.put(C_SOUL.get(), CSoulBossEntity.createAttributes().build());
    }

    /** Only out at night (natural spawns need the open sky and darkness); spawners/eggs work anywhere. */
    private static <T extends Monster> boolean nightOnly(EntityType<T> type, ServerLevelAccessor level, MobSpawnType reason,
                                                         BlockPos pos, RandomSource random) {
        if (!Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        return reason != MobSpawnType.NATURAL || (level.getLevel().isNight() && level.canSeeSky(pos));
    }

    @SubscribeEvent
    public static void spawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(CORRUPTED_VILLAGER.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModCorrupted::nightOnly, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(C_SOUL.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModCorrupted::nightOnly, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(CORRUPTED_SOUL.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModCorrupted::nightOnly, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
