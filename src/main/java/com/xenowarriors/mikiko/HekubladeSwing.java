package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Hekublade cuts in a wide arc: a fully charged swing hits everything in front of Mikiko, not just the one
 * thing under the crosshair - and a swing at the air still catches enemies standing close in front of him.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HekubladeSwing {
    public static final double RANGE = 4.5, ARC_DEGREES = 110;
    private static final int MAX_TARGETS = 6;

    private HekubladeSwing() {}

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !Hekublade.isBlade(p.getMainHandItem())) return;
        if (p.getAttackStrengthScale(0.5F) < 0.85F) return;
        sweep(p, event.getTarget(), 0.7F);
    }

    /** Called when Mikiko swings at nothing (the client tells us). */
    public static void swingAtAir(ServerPlayer p) {
        if (!Hekublade.isBlade(p.getMainHandItem()) || p.getAttackStrengthScale(0.5F) < 0.85F) return;
        if (sweep(p, null, 0.85F)) p.resetAttackStrengthTicker();
    }

    private static boolean sweep(ServerPlayer p, Entity primary, float scale) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-4) flat = Vec3.directionFromRotation(0, p.getYRot());
        flat = flat.normalize();
        double cos = Math.cos(Math.toRadians(ARC_DEGREES / 2));
        AABB box = p.getBoundingBox().inflate(RANGE, 2, RANGE);
        float damage = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * scale;
        int hit = 0;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != primary && SkillTargeting.canTarget(p, e))) {
            if (hit >= MAX_TARGETS) break;
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            Vec3 toFlat = new Vec3(to.x, 0, to.z);
            double dist = toFlat.length();
            if (dist > RANGE + e.getBbWidth() * 0.5 || Math.abs(to.y) > 2.5) continue;
            if (dist > 0.8 && toFlat.scale(1 / dist).dot(flat) < cos) continue;
            if (!SkillTargeting.hasLineOfSight(p, eye, e.getBoundingBox().getCenter())) continue;
            if (e.hurt(p.damageSources().playerAttack(p), damage)) {
                e.knockback(0.4, -flat.x, -flat.z);
                Hekublade.rollFire(e, 3);
                hit++;
            }
        }
        if (hit > 0 && p.level() instanceof ServerLevel server) {
            Vec3 at = p.position().add(flat.scale(2)).add(0, p.getBbHeight() * 0.55, 0);
            server.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            server.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 8, 1.0, 0.2, 1.0, 0.02);
            server.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.1F);
        }
        return hit > 0;
    }
}
