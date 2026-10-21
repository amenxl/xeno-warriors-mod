package com.xenowarriors.fox;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Wild foxes can't resist Mikiko, the Fox King: they run up to him and stay close, unless he already has all
 * the foxes he can keep.
 */
public class FoxAttractGoal extends Goal {
    private final Fox fox;
    private Player mikiko;
    private int check;

    public FoxAttractGoal(Fox fox) {
        this.fox = fox;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private static boolean wants(Player p) {
        return p.isAlive() && !p.isSpectator() && ModCapabilities.getType(p) == CharacterType.MIKIKO
                && FoxPets.count(p) < FoxPets.MAX_FOXES;
    }

    @Override
    public boolean canUse() {
        if (--check > 0 || FoxPets.ownerOf(fox) != null || fox.isBaby() && fox.getRandom().nextBoolean()) return false;
        check = 10;
        Player best = null;
        double bestD = 16 * 16;
        for (Player p : fox.level().players()) {
            double d = p.distanceToSqr(fox);
            if (d < bestD && wants(p)) {
                bestD = d;
                best = p;
            }
        }
        mikiko = best;
        return best != null;
    }

    @Override
    public boolean canContinueToUse() {
        return mikiko != null && FoxPets.ownerOf(fox) == null && wants(mikiko) && mikiko.distanceToSqr(fox) < 24 * 24;
    }

    @Override
    public void start() {
        FoxPets.wake(fox);
    }

    @Override
    public void stop() {
        mikiko = null;
        fox.getNavigation().stop();
    }

    @Override
    public void tick() {
        fox.getLookControl().setLookAt(mikiko, 30F, 30F);
        if (fox.distanceToSqr(mikiko) > 2.2 * 2.2) {
            if (fox.tickCount % 10 == 0) fox.getNavigation().moveTo(mikiko, 1.3);
        } else {
            fox.getNavigation().stop();
        }
    }
}
