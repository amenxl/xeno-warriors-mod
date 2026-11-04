package com.xenowarriors.pickup;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Health orbs: a defeated enemy (a hostile mob beaten by a player, their bot or pet) has a 5% chance to leave one.
 * Not in VR (it's a simulation). See {@link HealthOrbEntity}.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HealthOrbs {
    public static final float CHANCE = 0.05F;
    /** Tests: overrides the drop chance (negative = the normal 5%). */
    public static float testChance = -1F;
    /** Tests: counts. */
    public static int dropped, collectedCount;
    public static float lastHealFraction;

    private HealthOrbs() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel l) || dead instanceof Player || !(dead instanceof Enemy)) return;
        if (com.xenowarriors.vr.Vr.is(l)) return;
        ServerPlayer credit = com.xenowarriors.collection.Collection.credit(event.getSource().getEntity());
        if (credit == null) return;
        float chance = testChance >= 0 ? testChance : CHANCE;
        if (l.random.nextFloat() >= chance) return;
        drop(l, dead);
    }

    public static HealthOrbEntity drop(ServerLevel l, LivingEntity at) {
        HealthOrbEntity orb = ModEntities.HEALTH_ORB.get().create(l);
        if (orb == null) return null;
        orb.moveTo(at.getX(), at.getY() + at.getBbHeight() * 0.5, at.getZ(), 0, 0);
        orb.setDeltaMovement((l.random.nextDouble() - 0.5) * 0.15, 0.2, (l.random.nextDouble() - 0.5) * 0.15);
        l.addFreshEntity(orb);
        dropped++;
        return orb;
    }

    static void collected(HealthOrbEntity orb, LivingEntity who, float heal) {
        collectedCount++;
        lastHealFraction = heal / Math.max(1F, who.getMaxHealth());
    }
}
