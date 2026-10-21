package com.xenowarriors.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Holding an entity in place for gravity skills: it can't walk, jump, fly, or be pushed out. */
public final class Rooting {
    public static final String ROOTED_UNTIL = "xenowarriors_rooted_until";

    private Rooting() {}

    /** Call every tick while the entity should be stuck at {@code anchor}. */
    public static void hold(LivingEntity target, Vec3 anchor) {
        target.getPersistentData().putLong(ROOTED_UNTIL, target.level().getGameTime() + 2);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 6, false, false, false));
        if (target instanceof Player player) {
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            if (player.isFallFlying()) player.stopFallFlying();
        }
        if (target instanceof Mob mob) mob.getNavigation().stop();
        Vec3 v = target.getDeltaMovement();
        target.setDeltaMovement(0, Math.min(v.y, 0), 0);
        target.hurtMarked = true;
        double dx = target.getX() - anchor.x, dz = target.getZ() - anchor.z;
        if (dx * dx + dz * dz > 0.09) {
            if (target instanceof ServerPlayer sp) sp.connection.teleport(anchor.x, target.getY(), anchor.z, sp.getYRot(), sp.getXRot());
            else target.teleportTo(anchor.x, target.getY(), anchor.z);
        }
    }

    public static boolean isRooted(LivingEntity e) {
        return e.getPersistentData().getLong(ROOTED_UNTIL) >= e.level().getGameTime();
    }
}
