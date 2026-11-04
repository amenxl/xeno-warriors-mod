package com.xenowarriors.client.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.MenuStatePacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Whether a player is in a menu: your own screen, and other players' as told by the server. While someone is in a
 * menu Mikiko doesn't sit down for his idle (they're only standing still because they're busy in it).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MenuStateClient {
    private static final Set<Integer> OPEN = new HashSet<>();
    private static boolean sent;
    private static Player sentFor;

    private MenuStateClient() {}

    /** Your own client: a menu (any screen but the chat box) is open. */
    public static boolean localOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)
                && !(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen);
    }

    /** This player is in a menu right now (you: your screen; others: what the server said). */
    public static boolean inMenu(Player p) {
        if (p == Minecraft.getInstance().player) return localOpen();
        return OPEN.contains(p.getId());
    }

    public static void seen(int entityId, boolean open) {
        if (open) OPEN.add(entityId); else OPEN.remove(entityId);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) { OPEN.clear(); sentFor = null; sent = false; return; }
        boolean open = localOpen();
        if (open != sent || sentFor != mc.player) {
            sent = open;
            sentFor = mc.player;
            ModNetwork.sendToServer(new MenuStatePacket(open));
        }
        if (mc.level != null && mc.player.tickCount % 100 == 0) OPEN.removeIf(id -> mc.level.getEntity(id) == null);
    }
}
