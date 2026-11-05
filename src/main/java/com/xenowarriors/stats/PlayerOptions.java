package com.xenowarriors.stats;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PlayerOptionPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * Per-player on/off options kept on the server (saved with the player, through death): Rhalix's Gravity Magnet (set
 * in his character menu) and auto step-up (a client setting the client tells us about on joining and when changed).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class PlayerOptions {
    public static final String MAGNET = "gravity_magnet", AUTO_STEP = "auto_step";
    /** Every option and its default; anything else a client sends is ignored. */
    public static final Map<String, Boolean> DEFAULTS = Map.of(MAGNET, true, AUTO_STEP, true);

    private PlayerOptions() {}

    private static CompoundTag tag(Player p) {
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!p.getPersistentData().contains(Player.PERSISTED_NBT_TAG)) p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        CompoundTag t = persisted.getCompound("xw_opts");
        if (!persisted.contains("xw_opts")) persisted.put("xw_opts", t);
        return t;
    }

    public static boolean get(Player p, String key) {
        Boolean def = DEFAULTS.get(key);
        if (def == null || p == null) return false;
        CompoundTag t = tag(p);
        return t.contains(key) ? t.getBoolean(key) : def;
    }

    /** Sets it and tells their client (so its menus show it). */
    public static void set(ServerPlayer p, String key, boolean on) {
        if (!DEFAULTS.containsKey(key)) return;
        tag(p).putBoolean(key, on);
        if (key.equals(AUTO_STEP)) AutoStep.apply(p);
        ModNetwork.sendToPlayer(new PlayerOptionPacket(key, on), p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) ModNetwork.sendToPlayer(new PlayerOptionPacket(MAGNET, get(p, MAGNET)), p);
    }
}
