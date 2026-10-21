package com.xenowarriors.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/** Finding who a skill should hit. */
public final class SkillTargeting {
    private SkillTargeting() {}

    /** Whether a skill used by this player is allowed to hit this entity. */
    public static boolean canTarget(ServerPlayer owner, Entity e) {
        if (!(e instanceof LivingEntity living) || !living.isAlive() || e == owner || e instanceof ArmorStand) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (e instanceof TamableAnimal pet && owner.getUUID().equals(pet.getOwnerUUID())) return false;
        if (com.xenowarriors.fox.FoxPets.isOwnedBy(e, owner)) return false;   // Mikiko's own foxes
        if (com.xenowarriors.wing.WingPets.isOwnedBy(e, owner)) return false;  // Rhalix's winged companions
        if (e instanceof Player other && com.xenowarriors.party.Parties.sameParty(owner, other)
                && !com.xenowarriors.party.Duels.dueling(owner, other)) return false;   // party members
        if (e instanceof Player down && com.xenowarriors.party.Duels.isKnockedOut(down)) return false;
        return true;
    }

    /**
     * The living thing closest to where the player is aiming, within a narrow cone (so you don't need
     * pixel-perfect aim) and with a clear line of sight. Null if there's nothing.
     */
    public static LivingEntity aimed(ServerPlayer player, double range, double coneDegrees) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1F);
        double cos = Math.cos(Math.toRadians(coneDegrees));
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(3);
        LivingEntity best = null;
        double bestScore = -1;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box, e -> canTarget(player, e))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.01) continue;
            double dot = to.scale(1 / dist).dot(look);
            // entities right under the crosshair always count, even if big and close
            boolean direct = e.getBoundingBox().inflate(0.3).clip(eye, eye.add(look.scale(range))).isPresent();
            if (!direct && dot < cos) continue;
            if (!hasLineOfSight(player, eye, e.getBoundingBox().getCenter())) continue;
            double score = (direct ? 2 : dot) - dist / (range * 4);
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    /**
     * For homing attacks: what you're aiming at, else anything roughly in front of you, else the nearest
     * hostile around you. Null only if there's truly nothing to hit.
     */
    public static LivingEntity homing(ServerPlayer player, double range) {
        LivingEntity t = aimed(player, range, 12);
        if (t == null) t = aimed(player, range, 35);
        if (t == null) {
            t = around(player, player.position(), range * 0.5, 8, 16).stream()
                    .filter(e -> e instanceof net.minecraft.world.entity.monster.Enemy)
                    .filter(e -> hasLineOfSight(player, player.getEyePosition(), e.getBoundingBox().getCenter()))
                    .findFirst().orElse(null);
        }
        return t;
    }

    /** Everything a skill could hit around a point, nearest first. */
    public static List<LivingEntity> around(ServerPlayer player, Vec3 center, double radius, double height, int max) {
        AABB box = new AABB(center.x - radius, center.y - height, center.z - radius, center.x + radius, center.y + height, center.z + radius);
        return player.level().getEntitiesOfClass(LivingEntity.class, box, e -> canTarget(player, e)
                        && e.position().distanceToSqr(center.x, e.getY(), center.z) <= radius * radius)
                .stream()
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(center)))
                .limit(max)
                .toList();
    }

    public static boolean hasLineOfSight(ServerPlayer player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }
}
