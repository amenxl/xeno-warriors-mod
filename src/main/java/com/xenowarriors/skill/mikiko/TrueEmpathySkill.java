package com.xenowarriors.skill.mikiko;

import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * True Empathy: he feels what someone else just did and does it himself - copies the last skill anyone used near him
 * (within 20 seconds; never an ultimate). With nothing to copy, he reads the next attack instead: for 3 seconds the
 * next hit he takes is dodged and sent back (MoveEffects). Overusing it hurts: a third use inside 90 seconds leaves
 * him weak, slow and dazed, losing himself a little like in the show.
 */
public class TrueEmpathySkill implements Skill {
    public static final String READ_UNTIL = "xenowarriors_empathy_read_until";
    private static final String STRAIN = "xenowarriors_empathy_strain";

    @Override public String getId() { return "true_empathy"; }
    @Override public int getCooldownTicks() { return 10 * 20; }
    @Override public int getKarmaCost() { return 30; }

    @Override
    public boolean use(ServerPlayer p) {
        long now = p.level().getGameTime();
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(p.getId(), SkillAnimPacket.EMPATHY, false), p);
        p.serverLevel().sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.6, p.getZ(), 30, 0.4, 0.4, 0.4, 0.6);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1F, 1.3F);
        strain(p, now);
        Object[] seen = SkillManager.WITNESSED.get(p.getUUID());
        if (seen != null && now - (long) seen[1] < 20 * 20 && seen[0] instanceof Skill copy) {
            p.displayClientMessage(Component.literal("True Empathy - ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(copy.getName().copy().withStyle(ChatFormatting.WHITE)), true);
            SkillManager.WITNESSED.remove(p.getUUID());
            copy.use(p);
            return true;
        }
        p.getPersistentData().putLong(READ_UNTIL, now + 60);
        p.displayClientMessage(Component.literal("True Empathy - reading their next move...").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    private static void strain(ServerPlayer p, long now) {
        var tag = p.getPersistentData();
        long[] uses = tag.getLongArray(STRAIN);
        long[] keep = java.util.Arrays.stream(uses).filter(t -> now - t < 90 * 20).toArray();
        long[] next = java.util.Arrays.copyOf(keep, keep.length + 1);
        next[keep.length] = now;
        tag.putLongArray(STRAIN, next);
        if (next.length >= 3) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
            p.sendSystemMessage(Component.literal("You're overusing True Empathy... careful not to lose yourself.").withStyle(ChatFormatting.DARK_RED));
        }
    }
}
