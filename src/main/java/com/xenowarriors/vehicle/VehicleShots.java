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

    /**
     * Where a shot leaving `from` along `dir` first clears the shooter's vehicle and everyone riding it (plus `pad`), or
     * null when the shooter isn't in a vehicle. Projectiles that test their hits themselves (DragonMineZ's ki blasts)
     * start there, so they don't burst on the car or the people in it.
     */
    public static net.minecraft.world.phys.Vec3 clearOfVehicle(Entity shooter, net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 dir, double pad) {
        if (!(shooter.getRootVehicle() instanceof VehicleEntity v)) return null;
        net.minecraft.world.phys.AABB box = v.getBoundingBox();
        for (Entity r : v.getIndirectPassengers()) box = box.minmax(r.getBoundingBox());
        box = box.inflate(0.3 + pad);
        net.minecraft.world.phys.Vec3 d = dir.normalize(), far = from.add(d.scale(40));
        if (!box.contains(from)) return from;
        var exit = box.clip(far, from);                                    // (clipping back from far off finds where it leaves)
        return exit.map(e -> e.add(d.scale(0.2))).orElse(null);
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
