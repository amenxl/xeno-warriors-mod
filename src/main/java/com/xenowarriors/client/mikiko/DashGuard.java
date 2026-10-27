package com.xenowarriors.client.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.LockOn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Combo dash-ins that stop at the enemy instead of carrying Mikiko through it: locked on, a dash heads straight for
 * the target and is cut short so he ends up just in front of it, and any leftover momentum toward it is killed once
 * he's there.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DashGuard {
    /** How close he stops (gap between his body and the target's, in blocks). */
    private static final double STOP = 0.35;
    /** Rough distance one tick of dash speed carries him (ground friction and the little hop). */
    private static final double CARRY = 4.5;
    private static int targetId = -1;
    private static long until;

    private DashGuard() {}

    private static LivingEntity target(LocalPlayer p) {
        LivingEntity t = LockOn.target();
        if (t != null && t.isAlive() && t.distanceTo(p) < 12) return t;
        return com.xenowarriors.client.combo.ComboClient.assistTarget(p);   // not locked on: whoever the combo is hitting
    }

    /** Space left between the two of them, flat. */
    private static double gap(LocalPlayer p, LivingEntity t) {
        double d = Math.hypot(t.getX() - p.getX(), t.getZ() - p.getZ());
        return d - (p.getBbWidth() + t.getBbWidth()) / 2.0;
    }

    /** A combo dash forward ({@code fwd} speed, {@code up} hop) - aimed at and stopping short of the lock-on target. */
    public static void dash(LocalPlayer p, double fwd, double up) {
        Vec3 v = p.getDeltaMovement();
        LivingEntity t = target(p);
        if (t == null) {
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            p.setDeltaMovement(v.x * 0.3 - Mth.sin(yaw) * fwd, up, v.z * 0.3 + Mth.cos(yaw) * fwd);
            return;
        }
        Vec3 dir = new Vec3(t.getX() - p.getX(), 0, t.getZ() - p.getZ());
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, p.getYRot()) : dir.normalize();
        double room = Math.max(0, gap(p, t) - STOP);
        double speed = Math.min(fwd, room / CARRY);                 // only as far as there's room
        p.setDeltaMovement(dir.x * speed, up, dir.z * speed);
        targetId = t.getId();
        until = p.level().getGameTime() + 14;
    }

    /** Arrived: no more sliding toward (or into) the target. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof LocalPlayer p) || p != Minecraft.getInstance().player) return;
        LivingEntity t = null;
        if (targetId >= 0 && p.level().getGameTime() <= until && p.level().getEntity(targetId) instanceof LivingEntity e && e.isAlive()) t = e;
        else targetId = -1;
        if (t == null) { LivingEntity lock = LockOn.target(); if (lock != null && lock.isAlive() && lock.distanceTo(p) < 12) t = lock; }   // (any lock-on dash, the aerial one too)
        if (t == null) return;
        Vec3 to = new Vec3(t.getX() - p.getX(), 0, t.getZ() - p.getZ());
        if (to.lengthSqr() < 1.0E-4) return;
        Vec3 dir = to.normalize();
        Vec3 v = p.getDeltaMovement();
        double toward = v.x * dir.x + v.z * dir.z;
        if (toward <= 0) return;
        double room = gap(p, t) - STOP;
        // close enough: whatever's still carrying him at it goes (sideways and up/down movement stay)
        if (room <= toward * 1.2) {
            double keep = Math.max(0, room) / 1.2;
            double cut = toward - keep;
            p.setDeltaMovement(v.x - dir.x * cut, v.y, v.z - dir.z * cut);
        }
    }
}
