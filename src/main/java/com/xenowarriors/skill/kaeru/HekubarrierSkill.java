package com.xenowarriors.skill.kaeru;

import com.xenowarriors.skill.Skill;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kaeru - Hekubarrier: a ring of Hekublades spins up around her for 7 seconds - it swats projectiles out of the air,
 * shoves back anything that comes close, and she (and allies inside) take far less damage. Needs the Hekublade free.
 */
@Mod.EventBusSubscriber(modid = com.xenowarriors.XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HekubarrierSkill implements Skill {
    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();
    public static final double R = 3.0;

    @Override public String getId() { return "hekubarrier"; }
    @Override public int getCooldownTicks() { return 22 * 20; }
    @Override public int getKarmaCost() { return 35; }

    @Override
    public boolean use(ServerPlayer p) {
        if (!com.xenowarriors.kaeru.KaeruSoul.bladeFreeFor(p)) return false;
        ACTIVE.put(p.getUUID(), 140);
        p.level().playSound(null, p.blockPosition(), com.xenowarriors.ModSounds.HEKUBLADE_SUMMON.get(), SoundSource.PLAYERS, 1F, 1.2F);
        com.xenowarriors.XwFx.send(p.serverLevel(), com.xenowarriors.network.FxPacket.SHOCK, p.getX(), p.getY(), p.getZ(), 0xE8701C, 2F);
        return true;
    }

    public static boolean active(LivingEntity e) { return ACTIVE.containsKey(e.getUUID()); }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Integer left = ACTIVE.get(p.getUUID());
        if (left == null) return;
        if (left <= 0 || !p.isAlive()) { ACTIVE.remove(p.getUUID()); return; }
        ACTIVE.put(p.getUUID(), left - 1);
        ServerLevel l = p.serverLevel();
        double t = p.tickCount * 0.35;
        for (int i = 0; i < 8; i++) {
            double a = t + i * Math.PI / 4;
            l.sendParticles(i % 2 == 0 ? ParticleTypes.CRIT : ParticleTypes.ENCHANTED_HIT, p.getX() + Math.cos(a) * R, p.getY() + 1.0 + Math.sin(t * 2 + i) * 0.4, p.getZ() + Math.sin(a) * R, 1, 0, 0, 0, 0);
        }
        if (p.tickCount % 2 == 0) l.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + Math.cos(t) * R, p.getY() + 1, p.getZ() + Math.sin(t) * R, 1, 0, 0, 0, 0);
        for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(R + 1), pr -> pr.getOwner() != p)) {
            if (pr.distanceToSqr(p) < (R + 0.5) * (R + 0.5)) {
                l.sendParticles(ParticleTypes.CRIT, pr.getX(), pr.getY(), pr.getZ(), 6, .1, .1, .1, .2);
                pr.discard();
            }
        }
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(R))) {
            if (e == p) continue;
            if (com.xenowarriors.skill.SkillTargeting.canTarget(p, e)) {
                if (p.tickCount % 10 == 0) e.hurt(p.damageSources().playerAttack(p), 3F);
                e.knockback(0.6, p.getX() - e.getX(), p.getZ() - e.getZ());
            } else e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 1, false, false));
        }
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 2, false, false));
    }
}
