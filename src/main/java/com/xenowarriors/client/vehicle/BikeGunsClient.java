package com.xenowarriors.client.vehicle;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.vehicle.BikeFirePacket;
import com.xenowarriors.vehicle.VehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Driving Technica's motorcycle: attack fires its Twin Blasters (hold to keep firing), use fires a rocket. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BikeGunsClient {
    private BikeGunsClient() {}

    private static int nextGun, nextRocket, ticks;

    private static boolean driving() {
        var p = Minecraft.getInstance().player;
        return p != null && p.getVehicle() instanceof VehicleEntity v && v.kind.technica() && v.getFirstPassenger() == p;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBikeClick(InputEvent.InteractionKeyMappingTriggered e) {
        if (!driving()) return;
        if (e.isAttack()) { if (ticks >= nextGun) { ModNetwork.sendToServer(new BikeFirePacket(0)); nextGun = ticks + com.xenowarriors.vehicle.TechnicaBike.GUN_CD; } }
        else if (e.isUseItem()) { if (ticks >= nextRocket) { ModNetwork.sendToServer(new BikeFirePacket(1)); nextRocket = ticks + 10; } }
        else return;
        e.setSwingHand(false);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBikeTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !driving()) return;
        if (mc.options.keyAttack.isDown() && ticks >= nextGun) { ModNetwork.sendToServer(new BikeFirePacket(0)); nextGun = ticks + com.xenowarriors.vehicle.TechnicaBike.GUN_CD; }
    }
}
