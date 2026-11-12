package com.xenowarriors.combat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A player who takes no damage from a hit - or the smallest bit (half a heart or less, after armor and the rest) -
 * isn't knocked back by it: no flinch for a hit that did nothing.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NoChipKnockback {
    private NoChipKnockback() {}

    public static final float MAX_CHIP = 1.0F;
    /** player -> {game time of the hit, damage it really did} */
    private static final Map<UUID, float[]> LAST = new HashMap<>();
    /** (Tests) knockbacks stopped. */
    public static int stopped;

    /** player -> game time a hit on them started (LivingAttackEvent) */
    private static final Map<UUID, Long> HIT = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (e.getEntity() instanceof Player p && !p.level().isClientSide && !e.isCanceled()) HIT.put(p.getUUID(), p.level().getGameTime());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent e) {
        if (e.getEntity() instanceof Player p && !p.level().isClientSide)
            LAST.put(p.getUUID(), new float[]{p.level().getGameTime(), e.isCanceled() ? 0F : e.getAmount()});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onKnockback(LivingKnockBackEvent e) {
        if (!(e.getEntity() instanceof Player p) || p.level().isClientSide || e.isCanceled()) return;
        long now = p.level().getGameTime();
        Long hit = HIT.get(p.getUUID());
        if (hit == null || hit != now) return;                                   // (a push that isn't from a hit - a skill's shove, wind: left alone)
        float[] h = LAST.get(p.getUUID());
        boolean dealt = h != null && (long) h[0] == now;                        // (no damage event: the hit was soaked up to nothing)
        if (!dealt || h[1] <= MAX_CHIP) { e.setCanceled(true); stopped++; }
    }
}
