package com.xenowarriors.bot;

import com.xenowarriors.character.CharacterType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

/**
 * In the party, a Mikiko or Rhalix bot with the right food in its bag tames pets it comes across, for you - Mikiko
 * foxes (rabbit), Rhalix bats, parrots, chickens and bees (berries, melon, apples, seeds, flowers) - the same chance and
 * limits as when you feed them yourself. Only in quiet times, and only while there's room for another pet.
 */
public class BotTaming extends Goal {
    public static final int LOOK_EVERY = 100;
    public static final double RANGE = 16;
    /** (tests) */
    public static int tries;

    private final PartyBotEntity b;
    private Mob target;
    private int nextLook, t;

    public BotTaming(PartyBotEntity b) { this.b = b; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); nextLook = 60 + b.getRandom().nextInt(LOOK_EVERY); }

    private boolean tamer() { return b.character() == CharacterType.MIKIKO || b.character() == CharacterType.RHALIX; }

    private boolean wild(Mob m) {
        if (!m.isAlive()) return false;
        if (b.character() == CharacterType.MIKIKO) return m instanceof Fox f && com.xenowarriors.fox.FoxPets.ownerOf(f) == null;
        return com.xenowarriors.wing.WingPets.winged(m) && com.xenowarriors.wing.WingPets.ownerOf(m) == null && !(m instanceof Parrot p && p.isTame());
    }

    private int foodSlot(Mob m) {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (s.isEmpty()) continue;
            if (b.character() == CharacterType.MIKIKO ? com.xenowarriors.fox.FoxPets.tamingFood(s) : com.xenowarriors.wing.WingPets.tamingFood(m, s)) return i;
        }
        return -1;
    }

    private boolean room(ServerPlayer o) {
        int max = b.character() == CharacterType.MIKIKO ? com.xenowarriors.fox.FoxPets.MAX_FOXES : com.xenowarriors.wing.WingPets.MAX;
        int have = (b.character() == CharacterType.MIKIKO ? com.xenowarriors.fox.FoxPets.records(o) : com.xenowarriors.wing.WingPets.records(o)).size();
        return have < max && com.xenowarriors.party.Parties.petRoom(o);
    }

    @Override public boolean canUse() {
        if (b.tickCount < nextLook) return false;
        nextLook = b.tickCount + LOOK_EVERY;
        ServerPlayer o = b.owner();
        if (!tamer() || o == null || o.level() != b.level() || BotErrands.danger(b) != null || b.journey() || b.isPassenger() || !room(o)) return false;
        target = null;
        double best = RANGE * RANGE;
        for (Mob m : b.level().getEntitiesOfClass(Mob.class, b.getBoundingBox().inflate(RANGE), this::wild)) {
            if (foodSlot(m) < 0) continue;
            double d = m.distanceToSqr(b);
            if (d < best) { best = d; target = m; }
        }
        return target != null;
    }

    @Override public boolean canContinueToUse() { return target != null && wild(target) && t < 20 * 20 && BotErrands.danger(b) == null && b.owner() != null; }
    @Override public void start() { t = 0; }
    @Override public void stop() { target = null; b.getNavigation().stop(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void tick() {
        t++;
        b.getLookControl().setLookAt(target, 30F, 30F);
        if (b.distanceToSqr(target) > 2.5 * 2.5) { if (b.getNavigation().isDone() || t % 10 == 0) b.getNavigation().moveTo(target, 1.1); return; }
        if (t % 15 != 0) return;                                               // (a feed every so often, like a player would)
        int slot = foodSlot(target);
        ServerPlayer o = b.owner();
        if (slot < 0 || o == null) { target = null; return; }
        ItemStack food = b.inventory.getItem(slot);
        b.swing(InteractionHand.MAIN_HAND);
        tries++;
        if (target instanceof Fox f) com.xenowarriors.fox.FoxPets.tameFor(o, f, food);
        else com.xenowarriors.wing.WingPets.tameFor(o, target, food);
        if (food.isEmpty()) b.inventory.setItem(slot, ItemStack.EMPTY);
    }
}
