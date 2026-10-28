package com.xenowarriors.combat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Grounded (Kitsune Attack's broken bones): for a while the target can't run (sprint) or fly - wings, wind flight,
 * elytra. Players are held down on both sides (the server and their own game, so sprinting doesn't flicker).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class Grounded {
    private static final String KEY = "xw_grounded_until";
    /** (client) when the local player's grounding ends, in System millis. */
    public static long clientUntil;

    private Grounded() {}

    public static void apply(LivingEntity e, int ticks) {
        long until = e.level().getGameTime() + ticks;
        e.getPersistentData().putLong(KEY, Math.max(until, e.getPersistentData().getLong(KEY)));
        e.setSprinting(false);
        if (e instanceof ServerPlayer p) {
            stopFlying(p);
            com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.GroundedPacket(ticks), p);
            p.displayClientMessage(Component.literal("Your legs won't carry you - no running or flying for " + ticks / 20 + "s!").withStyle(ChatFormatting.GOLD), true);
        }
    }

    public static boolean is(LivingEntity e) {
        if (e.level().isClientSide) return e instanceof Player && System.currentTimeMillis() < clientUntil;
        return e.getPersistentData().getLong(KEY) > e.level().getGameTime();
    }

    private static void stopFlying(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return;
        boolean changed = p.getAbilities().flying || p.getAbilities().mayfly;
        p.getAbilities().flying = false;
        p.getAbilities().mayfly = false;
        if (p.isFallFlying()) p.stopFallFlying();
        if (changed) p.onUpdateAbilities();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        long until = p.getPersistentData().getLong(KEY);
        if (until == 0) return;
        if (until > p.level().getGameTime()) {
            p.setSprinting(false);
            stopFlying(p);
        } else {                                   // over: give their flight back if they have it
            p.getPersistentData().remove(KEY);
            if (!p.isCreative() && !p.isSpectator() && (com.xenowarriors.mikiko.WindFlight.canFly(p) || com.xenowarriors.character.RhalixFlight.canFly(p))) {
                p.getAbilities().mayfly = true;
                p.onUpdateAbilities();
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || e instanceof Player || e.tickCount % 5 != 0 || !is(e)) return;
        e.setSprinting(false);
        if (e.isNoGravity() && !e.onGround() && e instanceof net.minecraft.world.entity.Mob) e.setDeltaMovement(e.getDeltaMovement().add(0, -0.12, 0));   // flyers sink
    }
}
