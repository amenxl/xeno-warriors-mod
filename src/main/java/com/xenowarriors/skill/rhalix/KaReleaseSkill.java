package com.xenowarriors.skill.rhalix;

import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.Rooting;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * KA Release: Rhalix breaks free of whatever is holding him - roots, gravity, slows, poisons, any harmful effect - with a
 * burst of gravity that throws enemies back, and for 4 seconds nothing can hold him again. Works even while rooted.
 */
public class KaReleaseSkill implements Skill {
    public static final String FREE_UNTIL = "xenowarriors_ka_free_until";

    @Override public String getId() { return "ka_release"; }
    @Override public int getCooldownTicks() { return 25 * 20; }
    @Override public int getKarmaCost() { return 30; }

    public static boolean free(LivingEntity e) {
        return e.getPersistentData().getLong(FREE_UNTIL) >= e.level().getGameTime();
    }

    @Override
    public boolean use(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        int cleared = 0;
        for (var inst : new java.util.ArrayList<>(p.getActiveEffects()))
            if (inst.getEffect().getCategory() == MobEffectCategory.HARMFUL) { p.removeEffect(inst.getEffect()); cleared++; }
        if (Rooting.isRooted(p)) cleared++;
        p.getPersistentData().remove(Rooting.ROOTED_UNTIL);
        p.getPersistentData().putLong(FREE_UNTIL, level.getGameTime() + 80);
        p.clearFire();
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(p.getId(), SkillAnimPacket.KA_RELEASE, false), p);
        p.displayClientMessage(Component.literal(cleared > 0 ? "KA Release!" : "KA Release - nothing can hold me.").withStyle(ChatFormatting.DARK_PURPLE), true);
        level.playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1F, 0.6F);
        level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5F, 1.6F);
        for (int a = 0; a < 40; a++) {
            double ang = a * Math.PI / 20;
            level.sendParticles(new DustParticleOptions(new Vector3f(.6F, .2F, 1F), 1.5F), p.getX() + Math.cos(ang) * 1.5, p.getY() + 1, p.getZ() + Math.sin(ang) * 1.5, 1, Math.cos(ang), 0, Math.sin(ang), 0.4);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(5), e -> SkillTargeting.canTarget(p, e))) {
            Vec3 away = e.position().subtract(p.position()).normalize().scale(1.4);
            e.push(away.x, 0.5, away.z);
            e.hurt(p.damageSources().playerAttack(p), 4F);
            e.hurtMarked = true;
        }
        return true;
    }
}
