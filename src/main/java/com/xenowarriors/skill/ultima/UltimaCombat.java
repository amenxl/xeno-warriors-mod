package com.xenowarriors.skill.ultima;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.skill.SkillTargeting;
import com.xenowarriors.ultima.UltimaOutput;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Attack Prediction's dodges and Living Weapon's sweeping hits. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UltimaCombat {
    private UltimaCombat() {}
    private static boolean sweeping;

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !UltimaOutput.is(p)) return;
        var t = p.getPersistentData();
        if (t.getLong(AttackPredictionSkill.UNTIL) < p.level().getGameTime() || t.getInt(AttackPredictionSkill.COUNT) <= 0) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity att) || att == p) return;
        event.setCanceled(true);
        t.putInt(AttackPredictionSkill.COUNT, t.getInt(AttackPredictionSkill.COUNT) - 1);
        Vec3 side = new Vec3(-(att.getZ() - p.getZ()), 0, att.getX() - p.getX()).normalize().scale(p.getRandom().nextBoolean() ? 1.5 : -1.5);
        if (p.level().noCollision(p, p.getBoundingBox().move(side))) p.teleportTo(p.getX() + side.x, p.getY(), p.getZ() + side.z);
        att.hurt(p.damageSources().playerAttack(p), 4F + 2F * UltimaOutput.tier(p));
        p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
    }

    @SubscribeEvent
    public static void onHit(LivingHurtEvent event) {
        if (sweeping || !(event.getSource().getEntity() instanceof ServerPlayer p) || event.getSource().getDirectEntity() != p || !UltimaOutput.is(p)) return;
        if (p.getPersistentData().getLong(LivingWeaponSkill.UNTIL) < p.level().getGameTime()) return;
        int tier = UltimaOutput.tier(p);
        event.setAmount(event.getAmount() * (1.2F + 0.1F * tier));
        LivingEntity hit = event.getEntity();
        sweeping = true;
        try {
            for (LivingEntity o : p.serverLevel().getEntitiesOfClass(LivingEntity.class, hit.getBoundingBox().inflate(2.5), o -> o != hit && SkillTargeting.canTarget(p, o)))
                o.hurt(p.damageSources().indirectMagic(p, p), event.getAmount() * 0.5F);
        } finally { sweeping = false; }
        p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, hit.getX(), hit.getY() + 1, hit.getZ(), 1, 0, 0, 0, 0);
    }
}
