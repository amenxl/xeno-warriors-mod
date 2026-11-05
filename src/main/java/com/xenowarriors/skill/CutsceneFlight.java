package com.xenowarriors.skill;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The flight an ultimate cutscene lends its player (Gravity Barrage, the team ultimates fly them along the
 * choreography). The cutscene takes it back when it ends - but if it ends while the player isn't there (logged out
 * mid-cutscene, went to another dimension, the server stopped) it can't, and the player kept creative-style flight in
 * survival for good. So the loan is marked in the player's save, and once no cutscene is running any more the flight
 * is taken back here (unless they can fly anyway: Mikiko's wind, a winged character).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CutsceneFlight {
    public static final String KEY = "xw_cutscene_flight";
    /** (tests) flights taken back by the watchdog (not by the cutscene itself). */
    public static int recovered;

    private CutsceneFlight() {}

    /** Lends flight for a cutscene; returns whether the player could already fly (give it to {@link #release}). */
    public static boolean lend(ServerPlayer p) {
        boolean had = p.getAbilities().mayfly;
        if (!had) {
            p.getAbilities().mayfly = true;
            p.onUpdateAbilities();
            p.getPersistentData().putBoolean(KEY, true);
        }
        return had;
    }

    /** The cutscene is over: the lent flight goes back. */
    public static void release(ServerPlayer p, boolean hadMayfly) {
        boolean lent = p.getPersistentData().getBoolean(KEY);
        p.getPersistentData().remove(KEY);
        if ((hadMayfly && !lent) || p.isCreative() || p.isSpectator()) return;
        if (canFlyAnyway(p)) return;
        p.getAbilities().mayfly = false;
        p.getAbilities().flying = false;
        p.onUpdateAbilities();
    }

    private static boolean canFlyAnyway(ServerPlayer p) {
        return com.xenowarriors.mikiko.WindFlight.canFly(p) || com.xenowarriors.character.RhalixFlight.canFly(p);
    }

    /** Watchdog: flight still lent but no cutscene running (it ended without them) - take it back. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        if (!p.getPersistentData().getBoolean(KEY)) return;
        if (p.getPersistentData().getLong(SkillEvents.ULT_UNTIL) >= p.level().getGameTime()) return;   // (still in it)
        p.getPersistentData().remove(KEY);
        if (p.isCreative() || p.isSpectator() || canFlyAnyway(p)) return;
        p.getAbilities().mayfly = false;
        p.getAbilities().flying = false;
        p.onUpdateAbilities();
        p.fallDistance = 0F;
        recovered++;
        XenoWarriors.LOGGER.info("XW cutscene flight taken back from {} (the cutscene ended without them)", p.getGameProfile().getName());
    }
}
