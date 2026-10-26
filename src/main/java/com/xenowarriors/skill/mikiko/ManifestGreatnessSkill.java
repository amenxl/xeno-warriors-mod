package com.xenowarriors.skill.mikiko;

import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.joml.Vector3f;

/**
 * Manifest Greatness: he chants "Manifest... True... Greatness!" and, believing it, glows red - stronger, faster,
 * tougher, and his Karma comes back faster for 30 seconds (see MoveEffects for the glow and regen).
 */
public class ManifestGreatnessSkill implements Skill {
    public static final String UNTIL = "xenowarriors_manifest_until";
    public static final int DURATION = 30 * 20, CHANT = 30;

    @Override public String getId() { return "manifest_greatness"; }
    @Override public int getCooldownTicks() { return 70 * 20; }
    @Override public int getKarmaCost() { return 40; }

    @Override
    public boolean use(ServerPlayer p) {
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(p.getId(), SkillAnimPacket.MANIFEST, false), p);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CHANT, 2, false, false, false));
        String[] words = {"Manifest...", "True...", "GREATNESS!"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            ServerScheduler.later(i * 12, () -> {
                if (!p.isAlive()) return;
                Component line = Component.literal("Mikiko: ").withStyle(ChatFormatting.RED).append(Component.literal(words[k]).withStyle(k == 2 ? ChatFormatting.GOLD : ChatFormatting.WHITE));
                for (ServerPlayer o : p.serverLevel().players()) if (o.distanceToSqr(p) < 32 * 32) o.displayClientMessage(line, true);
                p.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(1F, .15F, .1F), 1.6F), p.getX(), p.getY() + 1, p.getZ(), 10 + k * 15, 0.5, 0.8, 0.5, 0);
                p.serverLevel().playSound(null, p.blockPosition(), k == 2 ? SoundEvents.TOTEM_USE : SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, k == 2 ? 0.7F : 0.8F, 0.8F + k * 0.2F);
            });
        }
        ServerScheduler.later(CHANT, () -> {
            if (!p.isAlive()) return;
            p.getPersistentData().putLong(UNTIL, p.level().getGameTime() + DURATION);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DURATION, 1, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 0, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION, 0, false, false, true));
        });
        return true;
    }
}
