package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.MenuSeenPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who has a menu open right now (told by their own client). Passed on to everyone who can see them, so Mikiko's idle
 * sit-down doesn't play on a player who's only standing still because they're busy in a menu.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InMenu {
    private static final Set<UUID> OPEN = new HashSet<>();

    private InMenu() {}

    public static boolean open(ServerPlayer p) { return OPEN.contains(p.getUUID()); }

    public static void set(ServerPlayer p, boolean open) {
        boolean was = open ? !OPEN.add(p.getUUID()) : !OPEN.remove(p.getUUID());
        if (was) return;                                        // no change
        ModNetwork.sendToTrackingAndSelf(new MenuSeenPacket(p.getId(), open), p);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer && OPEN.contains(target.getUUID()))
            ModNetwork.sendToPlayer(new MenuSeenPacket(target.getId(), true), viewer);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OPEN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        OPEN.remove(event.getEntity().getUUID());               // (a new player on their side: their client tells us again)
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        OPEN.remove(event.getEntity().getUUID());
    }
}
