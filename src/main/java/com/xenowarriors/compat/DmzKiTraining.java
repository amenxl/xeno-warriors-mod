package com.xenowarriors.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * With DragonMineZ: practicing Karma Sensing trains your Ki too - every few sessions of practice adds a point to
 * your Energy stat (max Ki).
 */
public final class DmzKiTraining {
    private static final int EVERY = 3;
    private static Boolean loaded;
    private static boolean broken;
    private static Capability<?> cap;
    private static Method getStats, addEnergy, sendSync;
    private static java.lang.reflect.Constructor<?> syncPacket;

    private DmzKiTraining() {}

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (cap == null) {
            try {
                cap = (Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
                Class<?> data = Class.forName("com.dragonminez.common.stats.StatsData");
                getStats = data.getMethod("getStats");
                addEnergy = getStats.getReturnType().getMethod("addEnergy", int.class);
                Class<?> pkt = Class.forName("com.dragonminez.common.network.S2C.StatsSyncS2C");
                syncPacket = pkt.getConstructor(ServerPlayer.class);
                sendSync = Class.forName("com.dragonminez.common.network.NetworkHandler")
                        .getMethod("sendToTrackingEntityAndSelf", Object.class, net.minecraft.world.entity.Entity.class);
            } catch (Throwable t) {
                broken = true;
                return false;
            }
        }
        return true;
    }

    /** One more bit of Karma Sensing practice. */
    public static void practiced(ServerPlayer p) {
        if (!ready()) return;
        var tag = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        int n = tag.getInt("xw_ki_practice") + 1;
        boolean gain = n >= EVERY;
        tag.putInt("xw_ki_practice", gain ? 0 : n);
        p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, tag);
        if (!gain) return;
        try {
            Object data = p.getCapability(cap).resolve().orElse(null);
            if (data == null) return;
            addEnergy.invoke(getStats.invoke(data), 1);
            sendSync.invoke(null, syncPacket.newInstance(p), p);
            p.displayClientMessage(Component.literal("Karma training: +1 Energy (max Ki)").withStyle(ChatFormatting.AQUA), true);
        } catch (Throwable t) {
            broken = true;
        }
    }
}
