package com.xenowarriors.modes;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * When you can't change characters (from the select screen or your You slots), and why:
 * on a Journey (it has its own team picks), during a Tower floor (between floors is fine), while you're down,
 * mid-duel, sealed in the Hekublade as a prisoner, in a VR challenge, during a boss fight (a Tower boss, a mini-boss,
 * the Wither or the Ender Dragon close by), driving a mod vehicle, inside a healing pod, and for 8 seconds after an
 * enemy hits you. The mode code itself (Journey tag-ins, tests, /xeno set) still switches characters directly.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SwitchRules {
    /** How long after an enemy hits you that you count as in combat (ticks). */
    public static final int COMBAT_TICKS = 8 * 20;
    private static final Map<UUID, Long> HIT = new HashMap<>();

    private SwitchRules() {}

    /** Why this player can't switch characters right now, or null if they can. */
    public static String why(ServerPlayer p) {
        if (com.xenowarriors.journey.Journey.inRun(p)) return "You can't switch characters on a Journey - pick your team before each battle";
        if (com.xenowarriors.tower.Tower.fighting(p)) return "You can't switch characters during a Tower floor - wait until it's cleared";
        if (com.xenowarriors.party.Duels.isKnockedOut(p)) return "You can't switch characters while you're down";
        if (com.xenowarriors.party.Duels.inDuel(p)) return "You can't switch characters in the middle of a duel";
        if (com.xenowarriors.mikiko.HekubladeRealm.isPrisoner(p)) return "You can't switch characters while you're sealed in the Hekublade";
        if (com.xenowarriors.vr.Vr.inVr(p)) return "You can't switch characters in a VR challenge";
        if (com.xenowarriors.gov.HealingPods.inPod(p)) return "You can't switch characters inside a healing pod";
        if (p.getVehicle() instanceof com.xenowarriors.vehicle.VehicleEntity) return "Get out of the vehicle to switch characters";
        if (p.isCreative() || p.isSpectator()) return null;
        if (bossNear(p)) return "You can't switch characters during a boss fight";
        Long t = HIT.get(p.getUUID());
        long left = t == null ? 0 : t + COMBAT_TICKS - p.level().getGameTime();
        if (left > 0) return "You're in combat - you can switch characters in " + Math.max(1, (left + 19) / 20) + "s";
        return null;
    }

    private static boolean bossNear(ServerPlayer p) {
        return !p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(40), m -> m.isAlive()
                && (com.xenowarriors.tower.TowerBosses.isBoss(m) || com.xenowarriors.boss.MiniBosses.is(m)
                    || m instanceof net.minecraft.world.entity.boss.wither.WitherBoss || m instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon)
                && (m.getTarget() == p || m.distanceToSqr(p) < 24 * 24)).isEmpty();
    }

    /** An enemy hit you: in combat for a few seconds. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer p) || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        if (event.getSource().getEntity() instanceof LivingEntity src && src != p && com.xenowarriors.bot.PartyBots.botOf(src) == null) HIT.put(p.getUUID(), p.level().getGameTime());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { HIT.remove(event.getEntity().getUUID()); }

    /** Died and back at spawn: out of that fight (you can pick someone else straight away). */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) { if (!event.isEndConquered()) HIT.remove(event.getEntity().getUUID()); }

    /** (tests) forget the combat timer. */
    public static void clearCombat(ServerPlayer p) { HIT.remove(p.getUUID()); }
}
