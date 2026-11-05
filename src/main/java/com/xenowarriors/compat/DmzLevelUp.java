package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * With DragonMineZ: its levels are worth far more. A DragonMineZ level is reached by raising stats (bought with TP,
 * or from training and rewards); every stat point raised that way earns (multiplier - 1) extra points, paid out as
 * DragonMineZ's own free attribute points each time the level goes up - so with the default x3 a level brings three
 * times the stats. Points spent from that free pool don't earn more (no runaway). Checked once a second per player,
 * per character: with DmzPerCharacter each character keeps their own DragonMineZ data, so the bookkeeping is kept
 * per character key too. Reflection against DragonMineZ 2.1.x (StatsData/Stats/Resources), synced with its own packet.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzLevelUp {
    private DmzLevelUp() {}

    private static final String ROOT = "xw_dmz_lvlup";
    private static Boolean loaded;
    private static boolean broken;
    private static Capability<?> cap;
    private static Method getStats, getResources, getLevel, getRemaining, totalStats, getPending, addPending, getStatus, isCreated;

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (cap != null) return true;
        try {
            cap = (Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
            Class<?> data = Class.forName("com.dragonminez.common.stats.StatsData");
            getStats = data.getMethod("getStats");
            getResources = data.getMethod("getResources");
            getLevel = data.getMethod("getLevel");
            getRemaining = data.getMethod("getRemainingAssignableStats");
            getStatus = data.getMethod("getStatus");
            totalStats = Class.forName("com.dragonminez.common.stats.character.Stats").getMethod("getTotalStats");
            Class<?> res = Class.forName("com.dragonminez.common.stats.character.Resources");
            getPending = res.getMethod("getPendingAttributePoints");
            addPending = res.getMethod("addPendingAttributePoints", int.class);
            isCreated = Class.forName("com.dragonminez.common.stats.character.Status").getMethod("isHasCreatedCharacter");
            return true;
        } catch (Throwable t) {
            broken = true;
            cap = null;
            XenoWarriors.LOGGER.warn("DragonMineZ level-up bonus off (its API changed?)", t);
            return false;
        }
    }

    /** {level, total stats, free points, room left} of the player's current DragonMineZ character, or null. */
    public static int[] read(ServerPlayer p) { return read(p, false); }

    /** (any = also for a DragonMineZ character not made yet) */
    public static int[] read(ServerPlayer p, boolean any) {
        if (!ready()) return null;
        try {
            Object d = p.getCapability(cap).resolve().orElse(null);
            if (d == null || (!any && !(Boolean) isCreated.invoke(getStatus.invoke(d)))) return null;
            Object res = getResources.invoke(d);
            return new int[]{(Integer) getLevel.invoke(d), (Integer) totalStats.invoke(getStats.invoke(d)), (Integer) getPending.invoke(res),
                    (Integer) getRemaining.invoke(d)};
        } catch (Throwable t) { return null; }
    }

    private static String key(ServerPlayer p) {
        String k = DmzPerCharacter.bookKey(p);                                    // (a bot's while its DMZ menu is open)
        return k.isEmpty() ? "_" : k;
    }

    private static CompoundTag book(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag keep = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!keep.contains(ROOT)) keep.put(ROOT, new CompoundTag());
        return keep.getCompound(ROOT);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 7
                || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        poll(p);
    }

    /** Catches up on the current character's stat changes (also called right before DmzPerCharacter swaps characters). Returns points given. */
    public static int poll(ServerPlayer p) {
        int[] now = read(p);
        if (now == null) return 0;
        CompoundTag book = book(p);
        String k = key(p);
        if (!book.contains(k)) { book.put(k, record(now, 0)); return 0; }      // first look at this character: start counting from here
        CompoundTag r = book.getCompound(k);
        int lvl = now[0], total = now[1], pending = now[2];
        double owed = r.getDouble("owed");
        int spentFree = Math.max(0, r.getInt("p") - pending);                       // free points put into stats
        int raised = total - r.getInt("t") - spentFree;                             // stats raised the normal way (TP, training)
        double mult = com.xenowarriors.XwConfig.get(com.xenowarriors.XwConfig.DMZ_LEVEL_UP_MULTIPLIER);
        if (raised > 0) owed += raised * (mult - 1.0);
        if (total < r.getInt("t") && lvl <= 1) owed = 0;                           // (the character was reset)
        int given = 0;
        if (lvl > r.getInt("l") && owed >= 1) {
            int give = (int) Math.min(owed, Math.max(0, now[3] - pending));        // (never past DragonMineZ's stat cap)
            if (give > 0) {
                try {
                    Object d = p.getCapability(cap).resolve().orElse(null);
                    addPending.invoke(getResources.invoke(d), give);
                    pending += give;
                    owed -= give;
                    given = give;
                    DmzPerCharacter.syncStats(p);
                    p.displayClientMessage(Component.literal("DragonMineZ level " + lvl + "! +" + give + " bonus stat points to spend")
                            .withStyle(ChatFormatting.GOLD), true);
                    XenoWarriors.LOGGER.info("XW dmz level up: {} level {} -> {}, +{} free points (x{} stats)", p.getGameProfile().getName(), r.getInt("l"), lvl, give, mult);
                } catch (Throwable t) { XenoWarriors.LOGGER.warn("DragonMineZ bonus points failed", t); }
            }
        }
        book.put(k, record(new int[]{lvl, total, pending}, owed));
        return given;
    }

    private static CompoundTag record(int[] v, double owed) {
        CompoundTag t = new CompoundTag();
        t.putInt("l", v[0]); t.putInt("t", v[1]); t.putInt("p", v[2]); t.putDouble("owed", owed);
        return t;
    }
}
