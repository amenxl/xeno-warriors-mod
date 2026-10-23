package com.xenowarriors.sky;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server side of the Sky Boots' sky walk: no fall damage, and not mistaken for flying. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkyWalkServer {
    private SkyWalkServer() {}

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (!p.isShiftKeyDown() || !SkyArmorItem.wearing(p, EquipmentSlot.FEET) || p.onGround()) return;
        p.fallDistance = 0;
        if (p.connection != null) ((com.xenowarriors.mixin.ServerGamePacketListenerAccessor) p.connection).xenowarriors$setAboveGroundTickCount(0);
    }
}
