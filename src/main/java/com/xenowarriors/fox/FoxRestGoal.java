package com.xenowarriors.fox;

import com.xenowarriors.mixin.FoxAccessor;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;

import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Keeping Mikiko company: when he stands around for a bit the fox sits down next to him, and when he goes
 * to bed it curls up and sleeps at his side.
 */
public class FoxRestGoal extends Goal {
    private final Fox fox;
    private Player owner;
    private boolean sleep, petted;
    private int time;

    public FoxRestGoal(Fox fox) {
        this.fox = fox;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player p = FoxPets.owner(fox);
        if (p == null || fox.getTarget() != null || fox.isLeashed() || !fox.onGround()) return false;
        double d = fox.distanceToSqr(p);
        if (p.isSleeping() && d < 16 * 16) {
            sleep = true;
        } else if (FoxPets.stillTicks(p) >= 200 && !FoxPets.isPlaying(p) && d < 6 * 6) {
            sleep = false;
            petted = false;
        } else if (wantsPets(p) && d < 10 * 10) {
            sleep = false;
            petted = true;
        } else {
            return false;
        }
        owner = p;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (owner == null || !owner.isAlive() || owner.level() != fox.level() || fox.getTarget() != null) return false;
        if (sleep) return owner.isSleeping();
        return FoxPets.stillTicks(owner) > 0 && !FoxPets.isPlaying(owner) && fox.distanceToSqr(owner) < 10 * 10;
    }

    @Override
    public void start() {
        time = 0;
    }

    @Override
    public void stop() {
        FoxPets.wake(fox);
        owner = null;
    }

    /** Mikiko has sat down on the floor, and this is the fox closest to him. */
    private boolean wantsPets(Player p) {
        if (com.xenowarriors.character.ModCapabilities.getType(p) != com.xenowarriors.character.CharacterType.MIKIKO) return false;
        if (FoxPets.stillTicks(p) < 600 || FoxPets.isPlaying(p) || p.isSleeping()) return false;
        Fox closest = null;
        double best = Double.MAX_VALUE;
        for (Fox f : p.level().getEntitiesOfClass(Fox.class, p.getBoundingBox().inflate(10), f -> FoxPets.isOwnedBy(f, p))) {
            double d = f.distanceToSqr(p);
            if (d < best) {
                best = d;
                closest = f;
            }
        }
        return closest == fox;
    }

    private static Vec3 petSpot(Player p) {
        float yaw = p.yBodyRot * ((float) Math.PI / 180F);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        return p.position().add(right.scale(0.85)).add(fwd.scale(0.15));
    }

    @Override
    public void tick() {
        time++;
        FoxAccessor a = (FoxAccessor) fox;
        if (sleep) {
            if (fox.distanceToSqr(owner) > 1.8 * 1.8) {
                if (time % 10 == 1) fox.getNavigation().moveTo(owner, 1.0);
            } else {
                fox.getNavigation().stop();
                if (!fox.isSleeping()) a.xenowarriors$setSleeping(true);
            }
        } else {
            // Mikiko sitting on the floor: the nearest fox trots up to his side to be petted
            if (!petted && wantsPets(owner)) petted = true;
            if (petted) {
                Vec3 spot = petSpot(owner);
                if (fox.position().distanceToSqr(spot) > 0.3 * 0.3 && time < 200) {
                    if (fox.isSitting()) a.xenowarriors$setSitting(false);
                    double d2 = fox.position().distanceToSqr(spot);
                    if (d2 > 1.5 * 1.5) {
                        if (time % 8 == 1) fox.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.9);
                    } else {
                        fox.getNavigation().stop();
                        fox.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 0.6);   // the last step, exactly
                    }
                    return;
                }
                fox.getNavigation().stop();
                fox.setYRot(owner.yBodyRot);
                fox.setYBodyRot(owner.yBodyRot);
                fox.setYHeadRot(owner.yBodyRot - 50F);
            } else {
                fox.getNavigation().stop();
            }
            if (!fox.isSitting()) a.xenowarriors$setSitting(true);
            if (!petted && time % 80 < 30) fox.getLookControl().setLookAt(owner, 20F, 20F);
        }
    }
}
