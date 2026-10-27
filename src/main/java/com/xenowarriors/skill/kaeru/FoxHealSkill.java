package com.xenowarriors.skill.kaeru;

import com.xenowarriors.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Kaeru - Fox Heal: revives a downed or knocked-out teammate nearby (no karma cost for them, no crouching wait);
 * if nobody's down, heals the most hurt ally near her (or herself) a lot, and cleanses poison and wither.
 */
public class FoxHealSkill implements Skill {
    @Override public String getId() { return "fox_heal"; }
    @Override public int getCooldownTicks() { return 40 * 20; }
    @Override public int getKarmaCost() { return 40; }

    @Override
    public boolean use(ServerPlayer p) {
        var server = p.getServer();
        if (server != null) for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            if (o == p || o.level() != p.level() || o.distanceToSqr(p) > 8 * 8) continue;
            if (com.xenowarriors.party.Duels.isDowned(o) && com.xenowarriors.party.Parties.sameParty(p, o)) {
                com.xenowarriors.party.Duels.foxRevive(o, p);
                fx(o);
                return true;
            }
        }
        LivingEntity best = p;
        float worst = p.getHealth() / p.getMaxHealth();
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8))) {
            boolean ally = (e instanceof ServerPlayer o && com.xenowarriors.party.Parties.sameParty(p, o)) || com.xenowarriors.party.Parties.petOwner(e) != null
                    && p.getUUID().equals(com.xenowarriors.party.Parties.petOwner(e));
            if (!ally || e == p) continue;
            float f = e.getHealth() / e.getMaxHealth();
            if (f < worst) { worst = f; best = e; }
        }
        if (best.getHealth() >= best.getMaxHealth()) {
            p.displayClientMessage(Component.literal("Nobody needs healing.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        best.heal(best.getMaxHealth() * 0.6F);
        best.removeEffect(MobEffects.POISON); best.removeEffect(MobEffects.WITHER);
        best.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        fx(best);
        return true;
    }

    private static void fx(LivingEntity e) {
        var l = (net.minecraft.server.level.ServerLevel) e.level();
        l.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + 1.2, e.getZ(), 8, .5, .5, .5, 0);
        l.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + 0.5, e.getZ(), 30, .6, .8, .6, .02);
        l.playSound(null, e.blockPosition(), SoundEvents.FOX_AMBIENT, SoundSource.PLAYERS, 1F, 1.3F);
        l.playSound(null, e.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1F);
        com.xenowarriors.XwFx.send(l, com.xenowarriors.network.FxPacket.SKILL, e.getX(), e.getY(), e.getZ(), 0xFF9A3A, 2F);
    }
}
