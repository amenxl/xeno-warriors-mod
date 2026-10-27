package com.xenowarriors.vehicle;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/** Vehicles: one entity type per kind. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Vehicles {
    public static final Map<VehicleKind, RegistryObject<EntityType<VehicleEntity>>> TYPES = new EnumMap<>(VehicleKind.class);

    static {
        for (VehicleKind k : VehicleKind.values())
            TYPES.put(k, ModEntities.ENTITY_TYPES.register(k.id, () -> EntityType.Builder.<VehicleEntity>of(VehicleEntity::new, MobCategory.MISC)
                    .sized(k.width, k.height).clientTrackingRange(10).updateInterval(1).build(k.id)));
    }

    private Vehicles() {}

    public static void init() {}

    public static VehicleKind kindOf(EntityType<?> type) {
        for (var e : TYPES.entrySet()) if (e.getValue().isPresent() && e.getValue().get() == type) return e.getKey();
        return VehicleKind.CAR;
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        for (var e : TYPES.entrySet()) event.put(e.getValue().get(), VehicleEntity.attributes(e.getKey()).build());
    }
}
