package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PlayerOptionPacket;
import com.xenowarriors.stats.PlayerOptions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** Your server-kept on/off options as the server last told us (Gravity Magnet), and sending your own settings (auto step-up). */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientOptions {
    private static final Map<String, Boolean> KNOWN = new HashMap<>();

    private ClientOptions() {}

    public static void received(String key, boolean on) { KNOWN.put(key, on); }

    public static boolean get(String key) { return KNOWN.getOrDefault(key, PlayerOptions.DEFAULTS.getOrDefault(key, false)); }

    /** Turn one on/off: shown right away, saved on the server. */
    public static void set(String key, boolean on) {
        KNOWN.put(key, on);
        if (net.minecraft.client.Minecraft.getInstance().getConnection() != null) ModNetwork.sendToServer(new PlayerOptionPacket(key, on));
    }

    @SubscribeEvent
    public static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        KNOWN.clear();
        ModNetwork.sendToServer(new PlayerOptionPacket(PlayerOptions.AUTO_STEP, ClientSettings.autoStep()));
    }
}
