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
 * Not in VR (it's a simulation), nor from Journey enemies (nothing heals you on a Journey). See {@link HealthOrbEntity}.
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
        if (dead.getPersistentData().getBoolean("xw_journey") || dead.getTags().contains("xw_journey")) return;   // (nothing heals you on a Journey: an orb there would only be wasted)
        ServerPlayer credit = com.xenowarriors.collection.Collection.credit(event.getSource().getEntity());
        if (credit == null) return;
        int luck = com.xenowarriors.apl.Apl.rank(credit, com.xenowarriors.apl.Apl.Ability.ORB_LUCK);
        float chance = testChance >= 0 ? testChance : com.xenowarriors.apl.Apl.Ability.ORB_CHANCE[Math.min(luck, 3)];   // (Orb Luck: more often)
        if (l.random.nextFloat() >= chance) return;
        HealthOrbEntity orb = drop(l, dead);
        if (orb == null) return;
        int magnet = com.xenowarriors.apl.Apl.live(credit, com.xenowarriors.apl.Apl.Ability.ORB_MAGNET);
        orb.setOwner(credit.getUUID(), com.xenowarriors.apl.Apl.Ability.MAGNET_RANGE[Math.min(magnet, 2)]);   // (Orb Magnet: it comes to you)
        if (com.xenowarriors.apl.Apl.live(credit, com.xenowarriors.apl.Apl.Ability.KARMA_ORBS) > 0) orb.setKarma(true);   // (Karma Orbs)
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
