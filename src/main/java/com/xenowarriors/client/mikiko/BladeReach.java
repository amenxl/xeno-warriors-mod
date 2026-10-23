package com.xenowarriors.client.mikiko;

import com.xenowarriors.client.LockOn;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Mikiko's fist combo only summons the Hekublade when whoever he's going for is just out of reach of his fists: the
 * blade's slash reaches them (up to {@link #REACH} blocks). Up close he keeps it to punches and kicks.
 */
public final class BladeReach {
    public static final double REACH = 6.0;

    private BladeReach() {}

    /** The one he's swinging at if they're past fist range but within the blade's reach; null if close or nobody. */
    public static LivingEntity farTarget(LocalPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof EntityHitResult) return null;            // in reach of his fists already
        LivingEntity lock = LockOn.target();
        if (lock != null && lock.isAlive() && p.distanceTo(lock) <= REACH && p.hasLineOfSight(lock)) return lock;
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1F).scale(REACH));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(p.getViewVector(1F).scale(REACH)).inflate(1.0),
                e -> e instanceof LivingEntity && e.isPickable() && !e.isSpectator() && e != p, REACH * REACH);
        if (hit != null && hit.getEntity() instanceof LivingEntity l && p.hasLineOfSight(l)) return l;
        return null;
    }

    public static void strike(LivingEntity target) {
        ModNetwork.sendToServer(new com.xenowarriors.network.BladeReachPacket(target.getId()));
    }
}
