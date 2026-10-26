package com.xenowarriors.skill;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** One character skill. Each character has 5 slots; slot 5 is the ultimate. */
public interface Skill {
    /** Used for the name in the lang file: skill.xenowarriors.<id> */
    String getId();

    int getCooldownTicks();

    /** Karma it takes to use the skill. Cooldowns still apply on top of this. */
    default int getKarmaCost() {
        return 20;
    }

    /** The cost for this player right now (some skills change it - KA Release costs what the enemy's KA took). */
    default int getKarmaCost(ServerPlayer player) {
        return getKarmaCost();
    }

    /**
     * Called every time the key is pressed, even during cooldown, before {@link #use}.
     * Return true if the press was used up here (for example, powering up a skill that's already active).
     */
    default boolean tryRecast(ServerPlayer player) {
        return false;
    }

    /** Use the skill. Return true if it was used, which starts the cooldown. */
    boolean use(ServerPlayer player);

    default Component getName() {
        return Component.translatable("skill.xenowarriors." + getId());
    }
}
