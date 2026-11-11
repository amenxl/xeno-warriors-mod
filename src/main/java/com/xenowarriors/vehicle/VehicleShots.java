package com.xenowarriors.vehicle;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Shooting out of a vehicle (players, bots, NPCs): nothing a rider fires - a gun's bullet, a Blaster's blast and its
 * splash, an arrow - hurts the vehicle they're in or anyone riding in it with them. (Our own guns' rays and skills
 * already skip them: SkillTargeting; projectiles pass through them while leaving, as vanilla does for its own.)
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VehicleShots {
    private VehicleShots() {}

    /** (tests) hits stopped. */
    public static int blocked;

    /** Riding the same vehicle (one of them may be the vehicle itself). */
    public static boolean together(Entity a, Entity b) {
        if (a == null || b == null || a == b) return false;
        Entity ra = a.getRootVehicle(), rb = b.getRootVehicle();
        return ra == rb && ra instanceof VehicleEntity;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity by = event.getSource().getEntity();
        if (by != null && by.isPassenger() && together(by, event.getEntity())) {
            event.setCanceled(true);
            blocked++;
        }
    }
}
