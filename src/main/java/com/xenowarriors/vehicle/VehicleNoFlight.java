package com.xenowarriors.vehicle;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * No flying while you're in a vehicle: your flight (wings, wind flight, DragonMineZ's Fly) is off while you ride one,
 * and comes back when you get out.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class VehicleNoFlight {
    private VehicleNoFlight() {}

    private static final String KEY = "xw_vehicle_noflight";

    public static boolean inVehicle(Player p) { return p.getVehicle() instanceof VehicleEntity; }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) return;
        if (inVehicle(p)) {
            boolean changed = p.getAbilities().flying || p.getAbilities().mayfly;
            if (changed) {
                p.getPersistentData().putBoolean(KEY, true);
                p.getAbilities().flying = false;
                p.getAbilities().mayfly = false;
                if (p.isFallFlying()) p.stopFallFlying();
                p.onUpdateAbilities();
            }
        } else if (p.getPersistentData().getBoolean(KEY)) {                      // out again: their flight back (if they still have it)
            p.getPersistentData().remove(KEY);
            if (!com.xenowarriors.combat.Grounded.is(p) && (com.xenowarriors.mikiko.WindFlight.canFly(p) || com.xenowarriors.character.RhalixFlight.canFly(p))) {
                p.getAbilities().mayfly = true;
                p.onUpdateAbilities();
            }
        }
    }

    /** (client) the local player's fly keys do nothing in a vehicle - DragonMineZ's Fly key too. */
    @Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class VehicleNoFlightClient {
        private static net.minecraft.client.KeyMapping dmzFly;
        private static boolean looked;

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.START) return;
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || !inVehicle(mc.player)) return;
            if (!looked) {
                looked = true;
                try { dmzFly = (net.minecraft.client.KeyMapping) Class.forName("com.dragonminez.client.util.KeyBinds").getField("FLY_KEY").get(null); } catch (Throwable ignored) {}
            }
            if (dmzFly != null) { while (dmzFly.consumeClick()) { } dmzFly.setDown(false); }
            if (mc.player.getAbilities().flying && !mc.player.isCreative()) mc.player.getAbilities().flying = false;
        }
    }
}
