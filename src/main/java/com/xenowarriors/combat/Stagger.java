package com.xenowarriors.combat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Hitstun from combo hits: while staggered, a mob stops in its tracks and nobody (mob or player) can land a melee hit,
 * so a string links together. To keep it fair nobody can be locked forever: after 9 combo hits in 3 seconds they
 * shrug it off and are immune to stagger for 2 seconds; bosses only flinch from finishers and heavies.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Stagger {
    private static final int LOCK_HITS = 9, LOCK_WINDOW = 60, IMMUNE = 40;
    /** id -> {stagger until, window start, hits in window, immune until} (game time). */
    private static final Map<Integer, long[]> S = new HashMap<>();

    private Stagger() {}

    public static boolean boss(LivingEntity e) {
        return e.getMaxHealth() >= 150F || e.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES);
    }

    /** A combo hit landed. big = a finisher or heavy. */
    public static void hit(LivingEntity t, int ticks, boolean big) {
        long now = t.level().getGameTime();
        long[] s = S.computeIfAbsent(t.getId(), k -> new long[]{0, now, 0, 0});
        if (now < s[3]) return;                                         // shrugging it off
        if (now - s[1] > LOCK_WINDOW) { s[1] = now; s[2] = 0; }
        if (++s[2] >= LOCK_HITS) { s[3] = now + IMMUNE; s[0] = 0; s[2] = 0; return; }
        if (boss(t)) { if (!big) return; ticks /= 2; }
        s[0] = Math.max(s[0], now + ticks);
    }

    public static boolean staggered(Entity e) {
        long[] s = S.get(e.getId());
        return s != null && e.level().getGameTime() < s[0];
    }

    /** Free them (a finisher sends them flying instead). */
    public static void clear(Entity e) {
        long[] s = S.get(e.getId());
        if (s != null) s[0] = 0;
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !(e instanceof Mob m) || !staggered(e)) return;
        m.getNavigation().stop();
        m.setZza(0F); m.setXxa(0F);
    }

    /** Staggered: can't land a melee hit. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity a && event.getSource().getDirectEntity() == a && staggered(a)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 200 != 0) return;
        long now = event.getServer().overworld().getGameTime();
        S.values().removeIf(s -> now - Math.max(s[0], Math.max(s[1] + LOCK_WINDOW, s[3])) > 200);
    }
}
