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

    /** A character is gone for good (a deleted You): its DragonMineZ character goes with it. */
    public static void forget(net.minecraft.world.entity.player.Player p, String key) {
        CompoundTag keep = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (!keep.contains(ROOT)) return;
        keep.getCompound(ROOT).remove(key);
        p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, keep);
    }
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

    /** For the UI tests: count the current DMZ character as made, so its creation menu stops opening (and pausing the game). */
    public static void markCreatedForTest(ServerPlayer p) {
        if (!ready()) return;
        try {
            Object data = p.getCapability(cap).resolve().orElse(null);
            if (data == null) return;
            Object st = getStatus.invoke(data);
            st.getClass().getMethod("setHasCreatedCharacter", boolean.class).invoke(st, true);
            sync(p);
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("markCreatedForTest", t); }
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
        endView(p, "character switch");                                             // (a bot's DMZ menu still open: put it back first)
        if (DmzFusion.fused(p)) DmzFusion.end(p, "you switched characters", false);   // (or the fused look and stats would be saved with the old one for good)
        CompoundTag keep = persisted(p);
        String from = keep.getString(LIVE);
        if (from.isEmpty() && current != null && !current.isEmpty() && !current.equals(com.xenowarriors.character.CharacterType.NONE.getId())) from = current;
        if (from.isEmpty()) { keep.putString(LIVE, to); return !created(p); }     // first character ever: what they have goes with them
        if (from.equals(to)) return false;
        DmzLevelUp.poll(p);                        // (the one being put away gets any level-up bonus it's owed first)
        try {
            Object data = p.getCapability(cap).resolve().orElse(null);
            if (data == null) return false;
            CompoundTag all = keep.getCompound(ROOT);
            all.put(from, ((CompoundTag) save.invoke(data)).copy());
            CompoundTag next = all.contains(to) ? all.getCompound(to) : (CompoundTag) save.invoke(fresh.newInstance(p));
            boolean brandNew = !all.contains(to);
            load.invoke(data, next.copy());
            // DragonMineZ keeps the six stats in the player's attributes, so a "fresh" StatsData for the same player reads
            // (and saved) the old character's stats: a new character starts from zero (its race pick gives the starting stats)
            if (brandNew) {
                Object st = data.getClass().getMethod("getStats").invoke(data);
                for (String m : new String[]{"setStrength", "setStrikePower", "setResistance", "setVitality", "setKiPower", "setEnergy"})
                    st.getClass().getMethod(m, int.class).invoke(st, 0);
            }
            keep.put(ROOT, all);
            keep.putString(LIVE, to);
            sync(p);
            return !(Boolean) isCreated.invoke(getStatus.invoke(data));
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("Couldn't switch DragonMineZ data for " + p.getGameProfile().getName(), t);
            return false;
        }
    }

    /**
     * The saved DragonMineZ character for this key (a bot's character), read-only: the stored tag itself for a character
     * put away, or a snapshot of the live one when the key is the one being played. Null when it has none (or no DMZ).
     */
    public static CompoundTag saved(ServerPlayer owner, String key) {
        if (!ready() || owner == null || key == null) return null;
        CompoundTag keep = persisted(owner);
        if (key.equals(keep.getString(LIVE))) {
            try {
                Object data = owner.getCapability(cap).resolve().orElse(null);
                return data == null ? null : (CompoundTag) save.invoke(data);
            } catch (Throwable t) { return null; }
        }
        CompoundTag all = keep.getCompound(ROOT);
        return all.contains(key) ? all.getCompound(key) : null;
    }

    // ------------------------------------------------------------------ bots' DragonMineZ characters

    private static final String VIEW = "xw_dmz_view";
    /** Owners whose bot's DMZ character is in their capability right now (this server run), with when it started. */
    private static final java.util.Map<java.util.UUID, Long> VIEWING = new java.util.HashMap<>();

    /** The key a bot's DragonMineZ character is saved under (like its inventory: its character id, or "you&lt;slot&gt;"). */
    public static String botKey(com.xenowarriors.bot.PartyBotEntity b) {
        return b.charKey();
    }

    /** The bot character whose DMZ data is in the owner's capability right now (its menu is open), or null. */
    public static String viewing(Player p) {
        CompoundTag keep = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return keep.contains(VIEW) ? keep.getCompound(VIEW).getString("key") : null;
    }

    /** Whose DMZ data DmzLevelUp should book points for: the bot's while its menu is open, else the one being played. */
    public static String bookKey(Player p) {
        String v = viewing(p);
        return v != null ? v : p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getString(LIVE);
    }

    /** Is this key the character the owner is playing (so its DMZ data is the live one)? */
    public static boolean isLive(Player p, String key) {
        return key != null && key.equals(p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getString(LIVE));
    }

    /** Puts a whole DMZ character tag away under this key (a bot's, made off-screen). Never over the live or open one. */
    public static boolean store(ServerPlayer owner, String key, CompoundTag tag) {
        if (!ready() || owner == null || key == null || tag == null || isLive(owner, key) || key.equals(viewing(owner))) return false;
        CompoundTag keep = persisted(owner);
        CompoundTag all = keep.getCompound(ROOT);
        all.put(key, tag);
        keep.put(ROOT, all);
        return true;
    }

    /** The owner's live StatsData (the capability object), or null. */
    public static Object live(Player p) {
        if (!ready()) return null;
        try { return p.getCapability(cap).resolve().orElse(null); } catch (Throwable t) { return null; }
    }

    /** A blank StatsData for this (stand-in) player, for building a bot's character off to the side. */
    public static Object blank(Player standIn) {
        if (!ready()) return null;
        try { return fresh.newInstance(standIn); } catch (Throwable t) { return null; }
    }

    /** (For the perf test) how many whole DMZ characters were saved / loaded off-screen. */
    public static long savesForTest, loadsForTest;

    public static CompoundTag saveData(Object data) {
        savesForTest++;
        try { return (CompoundTag) save.invoke(data); } catch (Throwable t) { return null; }
    }

    public static boolean loadData(Object data, CompoundTag tag) {
        loadsForTest++;
        try { load.invoke(data, tag); return true; } catch (Throwable t) { return false; }
    }

    public static boolean createdData(Object data) {
        try { return (Boolean) isCreated.invoke(getStatus.invoke(data)); } catch (Throwable t) { return true; }
    }

    /**
     * The owner opens a bot's DragonMineZ menu: the bot's DMZ character goes into the owner's capability (the owner's
     * own is kept safe in their saved data until it comes back), so DragonMineZ's own screens show and change the
     * bot's character - points spent, missions taken. {@link #endView} puts everything back. True when it's swapped in.
     */
    public static boolean beginView(ServerPlayer p, com.xenowarriors.bot.PartyBotEntity b) {
        if (!ready() || p instanceof net.minecraftforge.common.util.FakePlayer) return false;
        if (DmzFusion.fused(p)) return false;                                   // (your fused DMZ data would be put away with the fusion still on - and stay)
        String key = botKey(b);
        if (key.equals(viewing(p))) return true;
        endView(p, "another bot");
        if (isLive(p, key)) return false;
        DmzBots.ensureRace(b);                                                  // (a bot always has one: picked at random if need be)
        CompoundTag keep = persisted(p);
        CompoundTag all = keep.getCompound(ROOT);
        if (!all.contains(key)) return false;
        try {
            Object data = p.getCapability(cap).resolve().orElse(null);
            if (data == null) return false;
            DmzLevelUp.poll(p);                                                 // (the owner's own book is up to date first)
            CompoundTag view = new CompoundTag();
            view.putString("key", key);
            view.put("own", ((CompoundTag) save.invoke(data)).copy());
            view.putFloat("hp", p.getHealth());
            keep.put(VIEW, view);                                               // saved first: a crash from here on still finds the owner's own
            load.invoke(data, all.getCompound(key).copy());
            VIEWING.put(p.getUUID(), p.level().getGameTime());
            sync(p);
            XenoWarriors.LOGGER.info("XW dmz bot menu: {} opened {}'s DragonMineZ character", p.getGameProfile().getName(), key);
            return true;
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("Couldn't open a bot's DragonMineZ character", t);
            endView(p, "failed");
            return false;
        }
    }

    /** Puts the owner's own DragonMineZ character back (saving the bot's, with whatever was changed). Safe to call any time. */
    public static void endView(ServerPlayer p, String why) {
        if (!ready()) return;
        CompoundTag keep = persisted(p);
        VIEWING.remove(p.getUUID());
        if (!keep.contains(VIEW)) return;
        CompoundTag view = keep.getCompound(VIEW);
        try {
            Object data = p.getCapability(cap).resolve().orElse(null);
            if (data == null) return;
            String key = view.getString("key");
            DmzLevelUp.poll(p);                                                 // (the bot's level-up bonus, booked under its key)
            CompoundTag all = keep.getCompound(ROOT);
            all.put(key, ((CompoundTag) save.invoke(data)).copy());
            keep.put(ROOT, all);
            load.invoke(data, view.getCompound("own").copy());
            keep.remove(VIEW);
            sync(p);
            p.setHealth(Math.min(p.getMaxHealth(), Math.max(1F, view.getFloat("hp"))));
            if (!why.equals("menu closed") && !why.equals("logged out") && !why.equals("server stopping") && p.connection != null)   // (its screen would show - and spend - your own now)
                com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.BotDmzPacket(com.xenowarriors.network.BotDmzPacket.CLOSED, key), p);
            XenoWarriors.LOGGER.info("XW dmz bot menu: {} back to their own DragonMineZ character ({})", p.getGameProfile().getName(), why);
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("Couldn't put a bot's DragonMineZ character back", t);
        }
    }

    /** (For the tests) as after a crash: the server forgets the menu was open (the swapped data stays until checkView). */
    public static void forgetOpenForTest(Player p) { VIEWING.remove(p.getUUID()); }

    /** Is a bot's data in this player's capability with its menu open (this server run)? */
    public static boolean viewOpen(Player p) { return VIEWING.containsKey(p.getUUID()); }

    /** Every second (DmzBots): a view left over from a crash or a lost close packet is put back; none lasts over 10 minutes. */
    static void checkView(ServerPlayer p) {
        if (viewing(p) == null) { VIEWING.remove(p.getUUID()); return; }
        Long at = VIEWING.get(p.getUUID());
        if (at == null) endView(p, "left over");                                // (from before a restart)
        else if (p.level().getGameTime() - at > 20 * 60 * 10 || p.level().getGameTime() < at) endView(p, "timed out");
    }

    /** Sends DragonMineZ's own stats sync for this player (after changing their stats or points). */
    public static void syncStats(ServerPlayer p) {
        if (!ready()) return;
        try { sendTracking.invoke(null, statsSync.newInstance(p), p); } catch (Throwable ignored) {}
    }

    /** Sends all of DragonMineZ's syncs for this player (stats, look, progression): after changing their look. */
    public static void syncAll(ServerPlayer p) { if (ready()) sync(p); }

    /** Tell everyone who can see them (and them) about their DMZ data. */
    private static void sync(ServerPlayer p) {
        for (Constructor<?> c : new Constructor<?>[]{statsSync, appearanceSync, progressionSync}) {
            try { sendTracking.invoke(null, c.newInstance(p), p); } catch (Throwable ignored) {}
        }
        p.refreshDimensions();
    }
}
