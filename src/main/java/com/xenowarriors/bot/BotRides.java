package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.vehicle.TechnicaBike;
import com.xenowarriors.vehicle.VehicleEntity;
import com.xenowarriors.vehicle.VehicleKind;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * No room in your vehicle: a bot that can't fit takes another ride and follows you - a seat in a teammate bot's
 * follow-vehicle, Technica's own motorcycle (a Technica bot summons it), or a free vehicle of yours parked nearby (it
 * drives). When you get out, they get out; a motorcycle a bot summoned goes away again. Technica's bike shoots at
 * what you're fighting.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class BotRides {
    private BotRides() {}

    static final String FOLLOWS = "xw_follows", SUMMONED = "xw_bot_summoned";
    /** (Tests) */
    public static int overflows, summons, drives;

    private static boolean follower(VehicleEntity v, ServerPlayer o) { return o.getUUID().toString().equals(v.getPersistentData().getString(FOLLOWS)); }

    private static boolean room(VehicleEntity v) { return v.getPassengers().size() < v.kind.seats; }

    /** The owner's vehicle is full: find this bot another ride. True if it got one (or is on its way). */
    static boolean overflow(PartyBotEntity b, ServerPlayer o) {
        if (!(b.level() instanceof ServerLevel l)) return false;
        // 1: a seat in a teammate bot's follow-vehicle
        for (VehicleEntity v : l.getEntitiesOfClass(VehicleEntity.class, b.getBoundingBox().inflate(24), x -> x.isAlive() && follower(x, o) && room(x))) {
            if (b.distanceToSqr(v) > 5 * 5) { b.getNavigation().moveTo(v, 1.3); return true; }
            if (b.startRiding(v, false)) { overflows++; return true; }
        }
        // 2: Technica calls her motorcycle
        if (b.character() == CharacterType.TECHNICA && !com.xenowarriors.modes.ModeRules.arena(l)) {
            VehicleEntity v = TechnicaBike.summon(o, b);
            if (v != null && v.getPassengers().isEmpty()) {
                v.getPersistentData().putBoolean(SUMMONED, true);
                if (drive(b, v, o)) { summons++; return true; }
            }
        }
        // 3: one of your vehicles (or an unowned one - not the Government's, nor a raid's) parked nearby, empty
        for (VehicleEntity v : l.getEntitiesOfClass(VehicleEntity.class, b.getBoundingBox().inflate(16), x -> x.isAlive() && x.getPassengers().isEmpty() && x != o.getVehicle()
                && (o.getUUID().equals(x.owner()) || x.owner() == null && x.kind.base() != VehicleKind.GOV_TRUCK && !com.xenowarriors.raid.FactionRaids.isRaider(x)))) {
            if (b.distanceToSqr(v) > 5 * 5) { b.getNavigation().moveTo(v, 1.3); return true; }
            if (drive(b, v, o)) { drives++; return true; }
        }
        return false;
    }

    /** (Tests) as if its owner's vehicle had no room for it. */
    public static boolean boardForTest(PartyBotEntity b, ServerPlayer o) { return PartyBots.boardWith(b, o); }

    private static boolean drive(PartyBotEntity b, VehicleEntity v, ServerPlayer o) {
        b.addTag(VehicleEntity.NPC_DRIVER_TAG);
        if (!b.startRiding(v, false) || v.getFirstPassenger() != b) { b.removeTag(VehicleEntity.NPC_DRIVER_TAG); return false; }
        v.getPersistentData().putString(FOLLOWS, o.getUUID().toString());
        overflows++;
        return true;
    }

    @SubscribeEvent
    public static void onRidesTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer o) || o.tickCount % 5 != 2) return;
        for (PartyBotEntity b : PartyBots.bots(o)) {
            if (!(b.getVehicle() instanceof VehicleEntity v) || !follower(v, o)) {
                if (b.getTags().contains(VehicleEntity.NPC_DRIVER_TAG) && !b.isPassenger()) b.removeTag(VehicleEntity.NPC_DRIVER_TAG);
                continue;
            }
            if (v.getFirstPassenger() != b) continue;                            // (the driver steers; passengers ride)
            if (o.getVehicle() == null || o.level() != v.level()) continue;      // (they get out with you: PartyBots.unboard)
            net.minecraft.world.entity.Entity ov = o.getVehicle();
            double d2 = v.distanceToSqr(ov);
            if (d2 > 64 * 64) {                                                  // left far behind: it catches up
                Vec3 back = ov.position().add(Vec3.directionFromRotation(0, ov.getYRot()).scale(-8));
                v.teleportTo(back.x, ov.getY() + 0.5, back.z);
                continue;
            }
            float yaw = ov.getYRot();
            Vec3 side = Vec3.directionFromRotation(0, yaw + 90F).scale(3 * (b.getId() % 2 == 0 ? 1 : -1));
            Vec3 behind = ov.position().add(Vec3.directionFromRotation(0, yaw).scale(-6)).add(side);
            v.npcDriveTo(behind, d2 > 20 * 20 ? 1.0 : 0.9);
            // Technica's bike shoots at what you're fighting
            if (v.kind.technica()) {
                LivingEntity t = b.getTarget() != null && b.getTarget().isAlive() ? b.getTarget()
                        : o.getLastHurtMob() != null && o.getLastHurtMob().isAlive() && o.tickCount - o.getLastHurtMobTimestamp() < 100 ? o.getLastHurtMob() : null;
                if (t != null && t.distanceToSqr(v) < 28 * 28 && b.hasLineOfSight(t)) {
                    TechnicaBike.fire(b, 0, t);
                    if (v.has(TechnicaBike.ROCKETS) && t.getHealth() > 30 && b.getRandom().nextInt(6) == 0) TechnicaBike.fire(b, 1, t);
                }
            }
        }
        // a motorcycle a bot summoned, now empty: it goes away
        if (o.tickCount % 20 == 2 && o.level() instanceof ServerLevel l) {
            for (VehicleEntity v : l.getEntitiesOfClass(VehicleEntity.class, o.getBoundingBox().inflate(64), x -> x.isAlive() && follower(x, o))) {
                if (!v.getPassengers().isEmpty()) continue;
                v.getPersistentData().remove(FOLLOWS);
                v.npcDriveTo(null, 0);
                if (v.getPersistentData().getBoolean(SUMMONED)) {
                    l.sendParticles(ParticleTypes.CLOUD, v.getX(), v.getY() + 0.6, v.getZ(), 12, 0.5, 0.3, 0.5, 0.02);
                    v.discard();
                }
            }
        }
    }

}
