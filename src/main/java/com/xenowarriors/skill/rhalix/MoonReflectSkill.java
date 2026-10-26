package com.xenowarriors.skill.rhalix;

import com.xenowarriors.lunar.LunarForm;
import com.xenowarriors.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Rhalix (Lunar form, Season 2) - Moon Reflect. A silver crescent mirror wraps around him for 5 seconds:
 * arrows and blasts bounce back at whoever threw them, and half of every blow is turned back on the attacker.
 */
public class MoonReflectSkill implements Skill {
    public static final int DURATION = 100;

    @Override public String getId() { return "moon_reflect"; }
    @Override public int getCooldownTicks() { return 20 * 18; }
    @Override public int getKarmaCost() { return 30; }

    @Override
    public boolean use(ServerPlayer p) {
        if (!LunarForm.active(p)) {
            p.displayClientMessage(Component.literal("Moon Reflect only works in Lunar Form (at night)").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        LunarForm.startReflect(p, DURATION);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.2F, 0.7F);
        p.serverLevel().sendParticles(new DustParticleOptions(LunarForm.SILVER, 1.5F), p.getX(), p.getY() + 1, p.getZ(), 40, 0.7, 1.0, 0.7, 0);
        com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.MIRROR, 12);
        return true;
    }
}
