package com.xenowarriors.skill.ultima;

import com.xenowarriors.skill.Skill;
import com.xenowarriors.ultima.UltimaOutput;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Attack Prediction: his chip reads their habits, fear and heartbeat. For a few seconds the next hits are read -
 * he steps out of the way and hits back. More hits and longer at higher output. (Handled in UltimaCombat.)
 */
public class AttackPredictionSkill implements Skill {
    public static final String UNTIL = "xw_ult_predict_until", COUNT = "xw_ult_predict_n";

    @Override public String getId() { return "attack_prediction"; }
    @Override public int getCooldownTicks() { return 16 * 20; }
    @Override public int getKarmaCost() { return 25; }

    @Override
    public boolean use(ServerPlayer p) {
        int t = UltimaOutput.tier(p);
        p.getPersistentData().putLong(UNTIL, p.level().getGameTime() + 120 + t * 40);
        p.getPersistentData().putInt(COUNT, 2 + t);
        p.displayClientMessage(Component.literal("Attack Prediction: reading " + (2 + t) + " moves").withStyle(ChatFormatting.AQUA), true);
        p.serverLevel().sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getEyeY(), p.getZ(), 30, 0.3, 0.3, 0.3, 0.5);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1F, 2F);
        com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.MIRROR, 10);
        return true;
    }
}
