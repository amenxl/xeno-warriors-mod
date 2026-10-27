package com.xenowarriors.client.vehicle;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.vehicle.VehicleControls;
import com.xenowarriors.vehicle.VehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The driver's extra keys: helicopter climb (jump) and descend (sprint key) - the same keys you already use. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VehicleClient {
    private VehicleClient() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        boolean driving = mc.player != null && mc.player.getVehicle() instanceof VehicleEntity v && v.getControllingPassenger() == mc.player;
        VehicleControls.up = driving && mc.screen == null && mc.options.keyJump.isDown();
        VehicleControls.down = driving && mc.screen == null && mc.options.keySprint.isDown();
    }
}
