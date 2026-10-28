package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.combat.Grounded;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** While Grounded: no sprinting (even holding the key) and no flying, on your own side too. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GroundedClient {
    private GroundedClient() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var p = Minecraft.getInstance().player;
        if (p == null || !Grounded.is(p) || p.isCreative() || p.isSpectator()) return;
        p.setSprinting(false);
        p.getAbilities().flying = false;
        if (p.isFallFlying()) p.stopFallFlying();
    }
}
