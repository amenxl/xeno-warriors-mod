package com.xenowarriors.fox;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

/** A pet fox trots over to food lying on the ground near its owner and tucks it into its bag. */
public class FoxFetchFoodGoal extends Goal {
    private final Fox fox;
    private ItemEntity target;
    private int cooldown, time;

    public FoxFetchFoodGoal(Fox fox) {
        this.fox = fox;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean wanted(ItemEntity e, Player owner) {
        ItemStack s = e.getItem();
        return e.isAlive() && !s.isEmpty() && s.getFoodProperties(fox) != null && e.distanceToSqr(owner) < 12 * 12
                && FoxPets.bagHasRoom(fox, s);
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) return false;
        cooldown = 20;
        Player owner = FoxPets.owner(fox);
        if (owner == null || fox.getTarget() != null || fox.isPassenger() || fox.isSleeping()) return false;
        ItemEntity best = null;
        double bestD = 10 * 10;
        for (ItemEntity e : fox.level().getEntitiesOfClass(ItemEntity.class, fox.getBoundingBox().inflate(10), e -> wanted(e, owner))) {
            double d = e.distanceToSqr(fox);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        target = best;
        return best != null;
    }

    @Override
    public boolean canContinueToUse() {
        Player owner = FoxPets.owner(fox);
        return target != null && owner != null && time < 200 && wanted(target, owner) && fox.getTarget() == null;
    }

    @Override
    public void start() {
        FoxPets.wake(fox);
        time = 0;
    }

    @Override
    public void stop() {
        target = null;
        fox.getNavigation().stop();
    }

    @Override
    public void tick() {
        time++;
        fox.getLookControl().setLookAt(target, 30F, 30F);
        if (fox.distanceToSqr(target) > 1.4 * 1.4) {
            if (time % 8 == 1) fox.getNavigation().moveTo(target, 1.05);
            return;
        }
        // got it: into the bag
        ItemStack picked = target.getItem().copy();
        ItemStack left = FoxPets.addToBag(fox, picked);
        int taken = picked.getCount() - left.getCount();
        if (!left.isEmpty() && fox.level() instanceof ServerLevel sl) {                  // tell the owner the bag is full
            java.util.UUID o = com.xenowarriors.party.Parties.petOwner(fox);
            if (o != null && sl.getPlayerByUUID(o) instanceof net.minecraft.server.level.ServerPlayer sp) com.xenowarriors.bot.PartyBots.notifyPetFull(fox, sp);
        }
        if (taken > 0) {
            if (left.isEmpty()) target.discard();
            else target.setItem(left);
            fox.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.4F);
            if (fox.level() instanceof ServerLevel lvl) {
                lvl.sendParticles(ParticleTypes.HAPPY_VILLAGER, fox.getX(), fox.getY() + 0.6, fox.getZ(), 3, 0.2, 0.1, 0.2, 0);
            }
        }
        target = null;
    }
}
