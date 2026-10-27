package com.xenowarriors.skill.kaeru;

import com.xenowarriors.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Kaeru - Scent Tracking: catches the scent of what she's looking at (or the closest threat within 64 blocks) and keeps it glowing for 45 seconds, through walls. */
public class ScentTrackSkill implements Skill {
    @Override public String getId() { return "scent_track"; }
    @Override public int getCooldownTicks() { return 30 * 20; }
    @Override public int getKarmaCost() { return 15; }

    @Override
    public boolean use(ServerPlayer p) {
        LivingEntity t = com.xenowarriors.skill.SkillTargeting.aimed(p, 64, 8);
        if (t == null) {
            double best = Double.MAX_VALUE;
            for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(64))) {
                if (e == p || !(e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof ServerPlayer)) continue;
                double d = e.distanceToSqr(p);
                if (d < best) { best = d; t = e; }
            }
        }
        if (t == null) { p.displayClientMessage(Component.literal("No scent to follow.").withStyle(ChatFormatting.GRAY), true); return false; }
        t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 45 * 20, 0, false, false));
        p.displayClientMessage(Component.literal("Got their scent: " + t.getName().getString() + " (" + (int) t.distanceTo(p) + " blocks)").withStyle(ChatFormatting.GOLD), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.FOX_SNIFF, SoundSource.PLAYERS, 1F, 1.1F);
        return true;
    }
}
