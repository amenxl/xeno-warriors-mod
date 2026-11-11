package com.xenowarriors.raid;

import com.xenowarriors.vehicle.VehicleEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The soldiers driving a raid's vehicles (each tick from the raid, through the vehicle's NPC driver API):
 * <ul>
 * <li>Helicopters fly in ~14 blocks over the ground, come down to ~6.5 over the target, and everyone but the pilot and
 * the door gunner drops out (slow-falling, "rappelling"). Then it circles the fight ~15 blocks out and ~11 up while
 * those two keep shooting.</li>
 * <li>Trucks and cars drive at the target and stop ~18 blocks short; everyone aboard shoots from their seats for
 * ~6 seconds (or until the target's within 7 blocks, or the vehicle's stuck), then they all pile out and fight.</li>
 * </ul>
 */
public final class RaidPilot {
    private RaidPilot() {}

    public static final String PHASE = "xw_raid_phase", TIME = "xw_raid_time", HALT = "xw_raid_halt", STUCK = "xw_raid_stuck";

    /** Steers a raid vehicle with a soldier at the wheel (every tick, from the raid; uses the vehicle's NPC driver API). */
    public static void drive(VehicleEntity v, ServerPlayer t) {
        if (!v.npcDriving()) return;
        CompoundTag tag = v.getPersistentData();
        if (t == null || t.level() != v.level()) { v.npcDriveTo(v.position(), 0); return; }
        int time = tag.getInt(TIME) + 1;
        tag.putInt(TIME, time);
        if (v.kind.heli()) heli(v, t, tag, time); else ground(v, t, tag, time);
    }

    private static void heli(VehicleEntity v, ServerPlayer t, CompoundTag tag, int time) {
        ServerLevel l = (ServerLevel) v.level();
        Vec3 to = t.position().subtract(v.position());
        double d = to.horizontalDistance();
        float yaw = v.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        double floor = Math.max(RaidSpawns.surface(l, v.getX(), v.getZ()), RaidSpawns.surface(l, v.getX() + fx * 8, v.getZ() + fz * 8));
        double cruise = Math.min(1.0, 0.55 / v.kind.maxSpeed);
        if (tag.getInt(PHASE) == 0) {
            double tg = Math.max(floor, RaidSpawns.surface(l, t.getX(), t.getZ()));
            double alt = d > 26 ? tg + 14 : tg + 6.5;
            v.npcDriveTo(new Vec3(t.getX(), alt, t.getZ()), cruise);
            double above = v.getY() - Math.max(floor, RaidSpawns.surface(l, t.getX(), t.getZ()));
            if ((d < 10 && above < 9) || time > 700) {
                unload(v, t, 2, true);
                tag.putInt(PHASE, 1);
            }
            return;
        }
        // circling the fight, the pilot and door gunner shooting
        Vec3 from = v.position().subtract(t.position());
        double ang = Math.atan2(from.z, from.x) + 0.6;
        Vec3 dest = t.position().add(Math.cos(ang) * 15, 0, Math.sin(ang) * 15);
        double alt = Math.max(floor, RaidSpawns.surface(l, dest.x, dest.z)) + 11;
        v.npcDriveTo(new Vec3(dest.x, alt, dest.z), Math.min(1.0, 0.3 / v.kind.maxSpeed));
    }

    private static void ground(VehicleEntity v, ServerPlayer t, CompoundTag tag, int time) {
        double d = t.position().subtract(v.position()).horizontalDistance();
        int halt = tag.getInt(HALT), stuck = tag.getInt(STUCK);
        if (d > 18) { v.npcDriveTo(t.position(), Math.min(1.0, 0.45 / v.kind.maxSpeed)); halt = 0; }
        else { v.npcDriveTo(v.position(), 0); halt++; }                   // pulled up: everyone shoots from their seats
        if (v.horizontalCollision && Math.abs(v.speed()) < 0.08 && d > 18) stuck++; else stuck = Math.max(0, stuck - 1);
        tag.putInt(HALT, halt);
        tag.putInt(STUCK, stuck);
        if (halt > 120 || d < 7 || stuck > 50 || time > 900) {
            unload(v, t, 0, false);
            v.npcDriveTo(null, 0);
            tag.putInt(PHASE, 1);
        }
    }

    /** Everyone out but the first {@code keep} (helicopter: they drop down, slow-falling). */
    static void unload(VehicleEntity v, ServerPlayer t, int keep, boolean drop) {
        List<Entity> ps = new ArrayList<>(v.getPassengers());
        ServerLevel l = (ServerLevel) v.level();
        float yaw = v.getYRot() * Mth.DEG_TO_RAD;
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        for (int i = keep; i < ps.size(); i++) {
            Entity e = ps.get(i);
            if (!FactionRaids.isRaider(e)) continue;
            e.stopRiding();
            if (drop) {
                double side = (i % 2 == 0 ? -1 : 1) * (1.6 + (i / 2) % 3 * 0.6);
                Vec3 p = v.position().add(rx * side, -0.8, rz * side);
                if (l.noCollision(e, e.getBoundingBox().move(p.subtract(e.position())))) e.moveTo(p.x, p.y, p.z, e.getYRot(), 0F);
                if (e instanceof Mob m) m.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 140, 0, false, false));
                l.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 1, e.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
            }
            e.fallDistance = 0;
            if (e instanceof Mob m) m.setTarget(t);
        }
        l.playSound(null, v.blockPosition(), drop ? SoundEvents.ARMOR_EQUIP_CHAIN : SoundEvents.IRON_DOOR_OPEN, SoundSource.HOSTILE, 1.2F, 0.9F);
    }
}
