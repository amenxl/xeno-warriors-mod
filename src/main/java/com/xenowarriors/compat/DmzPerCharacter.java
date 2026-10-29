package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * With DragonMineZ: every character has their own DragonMineZ character - race, stats, forms, techniques, everything.
 * Switching characters puts the old one's DMZ data away and brings the new one's out (keyed like the inventories:
 * show character id or "you&lt;slot&gt;"). A character who's never had one starts fresh, and DragonMineZ's race picker
 * opens so you choose their Dragon Ball race. The first switch keeps what you had with the character you were playing.
 * Done by reflection against DragonMineZ 2.1.x (StatsData save/load, its sync packets), so nothing breaks without it.
 */
public final class DmzPerCharacter {
    private DmzPerCharacter() {}

    private static final String ROOT = "xw_dmz_chars", LIVE = "xw_dmz_live";
    private static Boolean loaded;
    private static boolean broken;
    private static Capability<?> cap;
    private static Method save, load, getStatus, isCreated, sendTracking;
    private static Constructor<?> fresh, statsSync, appearanceSync, progressionSync;

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (cap != null) return true;
        try {
            cap = (Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
            Class<?> data = Class.forName("com.dragonminez.common.stats.StatsData");
            save = data.getMethod("save");
            load = data.getMethod("load", CompoundTag.class);
            getStatus = data.getMethod("getStatus");
            isCreated = Class.forName("com.dragonminez.common.stats.character.Status").getMethod("isHasCreatedCharacter");
            fresh = data.getConstructor(Player.class);
            statsSync = Class.forName("com.dragonminez.common.network.S2C.StatsSyncS2C").getConstructor(ServerPlayer.class);
            appearanceSync = Class.forName("com.dragonminez.common.network.S2C.AppearanceSyncS2C").getConstructor(ServerPlayer.class);
            progressionSync = Class.forName("com.dragonminez.common.network.S2C.ProgressionSyncS2C").getConstructor(ServerPlayer.class);
            sendTracking = Class.forName("com.dragonminez.common.network.NetworkHandler").getMethod("sendToTrackingEntityAndSelf", Object.class, net.minecraft.world.entity.Entity.class);
            return true;
        } catch (Throwable t) {
            broken = true;
            cap = null;
            XenoWarriors.LOGGER.warn("DragonMineZ data can't be swapped per character (its API changed?) - every character shares one", t);
            return false;
        }
    }

    private static CompoundTag persisted(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Has this player's current DMZ character been made (race picked)? */
    public static boolean created(ServerPlayer p) {
        if (!ready()) return true;
        try {
            Object data = p.getCapability(cap).resolve().orElse(null);
            return data == null || (Boolean) isCreated.invoke(getStatus.invoke(data));
        } catch (Throwable t) { return true; }
    }

    /**
     * Switch DMZ data from the character being played ({@code current}) to {@code to}. Returns true when the new
     * character has no DMZ character yet (the caller opens DragonMineZ's race picker).
     */
    public static boolean switchTo(ServerPlayer p, String current, String to) {
        if (!ready() || p instanceof net.minecraftforge.common.util.FakePlayer) return false;
        if (com.xenowarriors.journey.Journey.inLobbyOrRun(p)) return false;          // (a Journey swaps characters for battles only)
        CompoundTag keep = persisted(p);
        String from = keep.getString(LIVE);
        if (from.isEmpty() && current != null && !current.isEmpty() && !current.equals(com.xenowarriors.character.CharacterType.NONE.getId())) from = current;
        if (from.isEmpty()) { keep.putString(LIVE, to); return !created(p); }     // first character ever: what they have goes with them
        if (from.equals(to)) return false;
        try {
            Object data = p.getCapability(cap).resolve().orElse(null);
            if (data == null) return false;
            CompoundTag all = keep.getCompound(ROOT);
            all.put(from, ((CompoundTag) save.invoke(data)).copy());
            CompoundTag next = all.contains(to) ? all.getCompound(to) : (CompoundTag) save.invoke(fresh.newInstance(p));
            load.invoke(data, next.copy());
            keep.put(ROOT, all);
            keep.putString(LIVE, to);
            sync(p);
            return !(Boolean) isCreated.invoke(getStatus.invoke(data));
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("Couldn't switch DragonMineZ data for " + p.getGameProfile().getName(), t);
            return false;
        }
    }

    /** Tell everyone who can see them (and them) about their DMZ data. */
    private static void sync(ServerPlayer p) {
        for (Constructor<?> c : new Constructor<?>[]{statsSync, appearanceSync, progressionSync}) {
            try { sendTracking.invoke(null, c.newInstance(p), p); } catch (Throwable ignored) {}
        }
        p.refreshDimensions();
    }
}
