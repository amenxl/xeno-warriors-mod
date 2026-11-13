package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterInventories;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.compat.DmzOffDuty;
import com.xenowarriors.party.Bonds;
import com.xenowarriors.stats.Building;
import com.xenowarriors.stats.Mining;
import com.xenowarriors.stats.Stat;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * "Off duty": characters that aren't on your team do things of their own, simulated a minute at a time while you're
 * online (no entity): training, relaxing, partying together, their own DragonMineZ missions, mining, chopping wood,
 * building. Each slowly levels its stat; what they mine or chop goes into their own saved inventory (never past a full
 * bag, extra tabs included). Busy ones can't be added unless your bond is high; low-bond teammates may wander off after
 * a long quiet spell; with a good bond you can give one a job. All of it lives in the owner's persisted data (saved with
 * the player, so a crash keeps it like the rest of the bot record). World settings: on/off and the party size limit.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OffDuty {
    private OffDuty() {}

    /** Bond needed: to call a busy one back anyway / to give one a job; under LEAVE_BOND a teammate may wander off. */
    public static final int OVERRIDE_BOND = 60, ASSIGN_BOND = 50, LEAVE_BOND = 30;
    public static final int QUIET_MINUTES = 20, LEAVE_COOLDOWN_MINUTES = 30, LONG_TEAM_MINUTES = 90, DOWNS_FOR_REST = 3, DOWN_WINDOW_MINUTES = 40;
    public static final long MINUTE = 1200;
    private static final String ROOT = "xw_offduty";

    public enum Act {
        NONE(""), TRAINING("Training"), RELAX_DOWNED("Relaxing - downed a lot lately"), RELAX_LONG("Relaxing - been with you a long time"),
        RELAX("Relaxing"), PARTY("Out at a party"), DMZ("On a DMZ mission"), MINING("Mining"), WOOD("Chopping wood"), BUILDING("Building"),
        DRAGON_BALLS("Searching for the Dragon Balls"), HUNTING("Hunting for food");
        public final String label;
        Act(String label) { this.label = label; }
        public boolean relaxing() { return this == RELAX || this == RELAX_DOWNED || this == RELAX_LONG; }
        public static Act of(int i) { return i >= 0 && i < values().length ? values()[i] : NONE; }
    }

    /** Jobs you can give (command words). */
    public static final Map<String, Act> TASKS = new LinkedHashMap<>();
    static {
        TASKS.put("mining", Act.MINING); TASKS.put("wood", Act.WOOD); TASKS.put("training", Act.TRAINING);
        TASKS.put("dmz", Act.DMZ); TASKS.put("building", Act.BUILDING); TASKS.put("relax", Act.RELAX);
    }

    // ---------------------------------------------------------------- world settings (on/off, party limit)

    public static final class World extends SavedData {
        boolean off; int limit = 4;
        static World load(CompoundTag t) { World w = new World(); w.off = t.getBoolean("off"); w.limit = t.contains("limit") ? clampLimit(t.getInt("limit")) : 4; return w; }
        @Override public CompoundTag save(CompoundTag t) { t.putBoolean("off", off); t.putInt("limit", limit); return t; }
    }

    private static World world(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(World::load, World::new, "xw_offduty");
    }

    private static MinecraftServer server() { return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer(); }

    public static int clampLimit(int n) { return Math.max(1, Math.min(16, n)); }

    /** The party size limit (players + bots) for this world: 4 unless changed with /xeno partylimit. */
    public static int partyLimit() {
        MinecraftServer s = server();
        return s == null ? 4 : world(s).limit;
    }

    public static void setPartyLimit(MinecraftServer s, int n) { World w = world(s); w.limit = clampLimit(n); w.setDirty(); }

    public static boolean enabled() { MinecraftServer s = server(); return s != null && !world(s).off; }

    public static void setEnabled(MinecraftServer s, boolean on) { World w = world(s); w.off = !on; w.setDirty(); }

    // ---------------------------------------------------------------- the records (owner's persisted data)

    private static CompoundTag root(Player p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(ROOT)) { pers.put(ROOT, new CompoundTag()); p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers); }
        return pers.getCompound(ROOT);
    }

    /** A character's record (live: changes stick). */
    static CompoundTag rec(Player p, CharacterType t) {
        CompoundTag r = root(p);
        if (!r.contains(t.getId())) r.put(t.getId(), new CompoundTag());
        return r.getCompound(t.getId());
    }

    public static Act act(Player p, CharacterType t) {
        if (!enabled()) return Act.NONE;
        return Act.of(rec(p, t).getInt("a"));
    }

    public static String task(Player p, CharacterType t) { return rec(p, t).getString("t"); }

    private static List<CharacterType> group(Player p, CharacterType t) {
        List<CharacterType> out = new ArrayList<>();
        for (String s : rec(p, t).getString("g").split(",")) { CharacterType c = CharacterType.byId(s); if (c != CharacterType.NONE && c != t) out.add(c); }
        return out;
    }

    private static String names(List<CharacterType> l) {
        List<String> n = new ArrayList<>(); for (CharacterType c : l) n.add(c.getDisplayName().getString());
        if (n.size() <= 1) return String.join("", n);
        return String.join(", ", n.subList(0, n.size() - 1)) + " & " + n.get(n.size() - 1);
    }

    /** What the pick screen shows for this character ("" = free). */
    public static String status(ServerPlayer p, CharacterType t) {
        Act a = act(p, t);
        if (a == Act.NONE) return "";
        String s = a.label;
        if (a == Act.PARTY) { List<CharacterType> g = group(p, t); if (!g.isEmpty()) s += " with " + names(g); }
        if ((a == Act.MINING || a == Act.WOOD) && rec(p, t).getBoolean("full")) s += " - bag's full";
        if (!task(p, t).isEmpty()) s += " (your orders)";
        return s;
    }

    // ---------------------------------------------------------------- who's off duty

    static boolean locked(MinecraftServer s, CharacterType t) { return com.xenowarriors.character.Seasons.highest(s) < t.season(); }

    /** On the team (a live bot, waiting outside the Hekublade, fused with you) or the one you're playing. */
    static boolean onTeam(ServerPlayer p, CharacterType t) {
        if (ModCapabilities.getType(p) == t || t.getId().equals(CharacterInventories.live(p))) return true;
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) return true;
        for (var e : PartyBots.LEFT_BEHIND) if (e.getKey().equals(p.getUUID()) && t.getId().equals(e.getValue().getString("char"))) return true;
        return t.getId().equals(com.xenowarriors.compat.DmzFusion.fusedChar(p));
    }

    public static boolean offDuty(ServerPlayer p, CharacterType t) { return !onTeam(p, t) && !locked(p.server, t); }

    // ---------------------------------------------------------------- the simulation

    public static boolean dmz(ServerPlayer p, CharacterType t) { return DmzOffDuty.available(p, t); }

    /** One simulated minute of every off-duty character of this owner. */
    public static void minute(ServerPlayer p, long now, RandomSource r) {
        if (!enabled()) return;
        for (CharacterType t : CharacterType.PLAYABLE) {
            if (onTeam(p, t)) continue;
            step(p, t, now, r);
        }
    }

    /** (Tests) N simulated minutes for one character, from game time {@code now}. */
    public static void simulate(ServerPlayer p, CharacterType t, int minutes, long now, RandomSource r) {
        SIMULATING = true;
        try { for (int i = 0; i < minutes; i++) step(p, t, now + i * MINUTE, r); }
        finally { SIMULATING = false; }
    }

    static void step(ServerPlayer p, CharacterType t, long now, RandomSource r) {
        if (!enabled()) return;
        CompoundTag c = rec(p, t);
        if (locked(p.server, t)) { if (c.getInt("a") != 0 || !c.getString("t").isEmpty()) { c.putInt("a", 0); c.remove("t"); c.remove("g"); } return; }
        Act a = Act.of(c.getInt("a"));
        String task = c.getString("t");
        if (!task.isEmpty()) {
            Act want = TASKS.getOrDefault(task, Act.NONE);
            if (want == Act.DMZ && !dmz(p, t)) want = Act.TRAINING;
            if (a != want) { a = want; c.putInt("a", a.ordinal()); }
        } else if (a != Act.NONE && now >= c.getLong("u")) {                        // (done: a few minutes to themselves)
            if (a == Act.DRAGON_BALLS) radarDone(p, t, c);
            leaveGroup(p, t);
            a = Act.NONE;
            c.putInt("a", 0);
            c.putLong("u", now + MINUTE * (3 + r.nextInt(6)));
            c.remove("full");
            if (SwitchQueue.queued(p) == t) c.putLong("u", now + MINUTE * 10);   // (you're waiting to switch to them: they don't start anything new)
            if (c.getBoolean(BotCalls.CALLED)) {                                       // (you phoned them: they come now, or ring to say they're ready)
                c.putLong("u", now + MINUTE * 10);                                  // (and wait for you a while instead of starting something new)
                BotCalls.done(p, t);
                return;
            }
        }
        if (a == Act.NONE && task.isEmpty() && now >= c.getLong("u") && r.nextFloat() < startChance(p, now)) a = choose(p, t, c, now, r);
        if (a != Act.NONE) work(p, t, c, a, r);
    }

    /**
     * The chance a free character starts something each minute: hardly any in a new world (the user: too many were
     * busy right away) - about one in thirty a minute for the first hour, rising to the usual one in three by the
     * end of the second.
     */
    private static boolean SIMULATING;

    static float startChance(ServerPlayer p, long now) {
        if (SIMULATING) return 0.35F;                    // (tests: the usual pace)
        CompoundTag r = root(p);
        if (!r.contains("start")) r.putLong("start", now);
        long since = now - r.getLong("start");
        if (since < 0) { r.putLong("start", now); since = 0; }
        float hours = since / (60F * MINUTE);
        if (hours < 1F) return 0.03F;
        if (hours < 2F) return 0.03F + (0.35F - 0.03F) * (hours - 1F);
        return 0.35F;
    }

    private static int recentDowns(CompoundTag c, long now) {
        int n = 0; for (long d : c.getLongArray("d")) if (now - d <= DOWN_WINDOW_MINUTES * MINUTE) n++;
        return n;
    }

    /** Ultimate weapons and Government-modified humans (Ultima) never get tired or hungry - they never need to relax. */
    public static boolean tireless(CharacterType t) {
        return t.race() == com.xenowarriors.character.Race.MODIFIED_HUMAN;
    }

    private static Act choose(ServerPlayer p, CharacterType t, CompoundTag c, long now, RandomSource r) {
        Act a;
        int len = 8 + r.nextInt(18);
        boolean tireless = tireless(t);
        if (tireless) { c.remove("d"); if (c.getInt("tm") >= LONG_TEAM_MINUTES) c.putInt("tm", 0); }   // never worn out
        if (!tireless && recentDowns(c, now) >= DOWNS_FOR_REST) { a = Act.RELAX_DOWNED; c.remove("d"); }
        else if (!tireless && c.getInt("tm") >= LONG_TEAM_MINUTES) { a = Act.RELAX_LONG; c.putInt("tm", 0); }
        else {
            boolean full = bagFull(p, t);
            List<Act> pool = new ArrayList<>();
            int[] w;
            String radar = radar(p, t);
            Act[] opts = {Act.TRAINING, Act.MINING, Act.WOOD, Act.BUILDING, Act.PARTY, Act.DMZ, Act.DRAGON_BALLS, Act.HUNTING};
            w = new int[]{25, full ? 0 : 15, full ? 0 : 15, 12, 10, dmz(p, t) ? 15 : 0, 0, full ? 0 : 14};   // (the Dragon Balls: only when you send them - right-click a bot with a Dragon Radar)
            if (tireless) { w[7] = 0; w[4] = 4; }                                  // no need to eat; not one for parties
            if (t == CharacterType.ULTIMA) w[0] = 110;                             // a weapon that trains (weights, mostly) almost all the time
            int sum = 0; for (int x : w) sum += x;
            int roll = r.nextInt(sum), i = 0;
            while (roll >= w[i]) roll -= w[i++];
            a = opts[i];
            if (a == Act.PARTY && !startParty(p, t, now, len, r)) a = Act.TRAINING;
            if (a == Act.DRAGON_BALLS) { len = searchMinutes(r, PartyBots.statLevel(p, t, Stat.SPEED)); c.putString("radar", radar); }
        }
        c.putInt("a", a.ordinal());
        if (a != Act.PARTY) c.putLong("u", now + len * MINUTE);
        return a;
    }

    /** A party: one or two other free characters come along (all busy the same time). False when nobody's free. */
    private static boolean startParty(ServerPlayer p, CharacterType t, long now, int len, RandomSource r) {
        List<CharacterType> free = new ArrayList<>();
        for (CharacterType o : CharacterType.PLAYABLE) {
            if (o == t || onTeam(p, o) || locked(p.server, o)) continue;
            CompoundTag oc = rec(p, o);
            if (oc.getInt("a") == 0 && oc.getString("t").isEmpty()) free.add(o);
        }
        if (free.isEmpty()) return false;
        Collections.shuffle(free, new Random(r.nextLong()));
        List<CharacterType> g = new ArrayList<>(free.subList(0, Math.min(free.size(), 1 + r.nextInt(2))));
        g.add(0, t);
        StringJoiner ids = new StringJoiner(",");
        for (CharacterType m : g) ids.add(m.getId());
        for (CharacterType m : g) {
            CompoundTag mc = rec(p, m);
            mc.putInt("a", Act.PARTY.ordinal()); mc.putLong("u", now + len * MINUTE); mc.putString("g", ids.toString());
        }
        return true;
    }

    /** Out of its party group (the others keep going). */
    private static void leaveGroup(ServerPlayer p, CharacterType t) {
        CompoundTag c = rec(p, t);
        for (CharacterType o : group(p, t)) {
            CompoundTag oc = rec(p, o);
            List<String> keep = new ArrayList<>();
            for (String s : oc.getString("g").split(",")) if (!s.equals(t.getId()) && !s.isEmpty()) keep.add(s);
            oc.putString("g", String.join(",", keep));
        }
        c.remove("g");
    }

    /** A minute of the activity. */
    private static void work(ServerPlayer p, CharacterType t, CompoundTag c, Act a, RandomSource r) {
        switch (a) {
            case TRAINING -> { float heavy = t == CharacterType.ULTIMA ? 1.5F : 1F;   // Ultima's heavy weight sessions
                charXp(p, t, Stat.ATTACK, 6F * heavy, c); charXp(p, t, Stat.DEFENSE, 5F * heavy, c); if (DmzOffDuty.addTp(p, t, 3F)) gain(c, "dmz_tp", 3); }
            case DMZ -> { charXp(p, t, Stat.ATTACK, 3F, c); charXp(p, t, Stat.DEFENSE, 3F, c); if (DmzOffDuty.addTp(p, t, 8F)) gain(c, "dmz_tp", 8); }
            case PARTY -> charXp(p, t, Stat.SPEED, 5F, c);
            case BUILDING -> { Building.addXp(p, key(p, t), 4F); gain(c, "building_xp", 4); }
            case MINING -> {
                if (bagFull(p, t)) { c.putBoolean("full", true); return; }      // (never past a full bag: no digging at all)
                c.remove("full");
                miningXp(p, 3F); gain(c, "mining_xp", 3);
                if (r.nextFloat() < 0.4F) find(p, t, c, ore(Mining.level(p), r));
            }
            case WOOD -> {
                if (bagFull(p, t)) { c.putBoolean("full", true); return; }
                c.remove("full");
                charXp(p, t, Stat.ATTACK, 1.5F, c);
                if (r.nextFloat() < 0.6F) find(p, t, c, new ItemStack(LOGS[r.nextInt(LOGS.length)], 1 + r.nextInt(3)));
            }
            case HUNTING -> {                                                       // food - and what its pets would take to tame
                if (bagFull(p, t)) { c.putBoolean("full", true); return; }
                c.remove("full");
                charXp(p, t, Stat.SPEED, 2F, c);
                if (r.nextFloat() < 0.45F) find(p, t, c, new ItemStack(MEAT[r.nextInt(MEAT.length)], 1 + r.nextInt(2)));
                Item[] tame = t == CharacterType.MIKIKO ? new Item[]{Items.RABBIT} : t == CharacterType.RHALIX ? new Item[]{Items.WHEAT_SEEDS, Items.SWEET_BERRIES, Items.MELON_SLICE, Items.APPLE} : null;
                if (tame != null && r.nextFloat() < 0.35F) find(p, t, c, new ItemStack(tame[r.nextInt(tame.length)], 1 + r.nextInt(2)));
            }
            default -> {}                                                            // (relaxing: nothing but rest)
        }
    }

    static final Item[] LOGS = {Items.OAK_LOG, Items.BIRCH_LOG, Items.SPRUCE_LOG, Items.DARK_OAK_LOG};
    static final Item[] MEAT = {Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT};

    /** Ores by Mining level: common ones always, the rarer ones from a level up and likelier the higher it is. */
    private record Ore(Item item, int minLevel, int weight, int max) {}
    static final Ore[] ORES = {
            new Ore(Items.COAL, 0, 30, 3), new Ore(Items.RAW_COPPER, 0, 25, 3), new Ore(Items.RAW_IRON, 0, 25, 2),
            new Ore(Items.RAW_GOLD, 10, 6, 2), new Ore(Items.REDSTONE, 10, 6, 4), new Ore(Items.LAPIS_LAZULI, 15, 5, 4),
            new Ore(Items.DIAMOND, 30, 2, 1), new Ore(Items.EMERALD, 45, 1, 1)};

    public static boolean rare(Item it) { return it == Items.DIAMOND || it == Items.EMERALD || it == Items.RAW_GOLD || it == Items.LAPIS_LAZULI || it == Items.REDSTONE; }

    static ItemStack ore(int level, RandomSource r) {
        float[] w = new float[ORES.length];
        float sum = 0;
        for (int i = 0; i < ORES.length; i++) {
            Ore o = ORES[i];
            w[i] = level < o.minLevel ? 0 : o.minLevel == 0 ? o.weight : o.weight * (1F + (level - o.minLevel) / 25F);
            sum += w[i];
        }
        float roll = r.nextFloat() * sum;
        for (int i = 0; i < ORES.length; i++) { if (roll < w[i]) return new ItemStack(ORES[i].item, 1 + r.nextInt(ORES[i].max)); roll -= w[i]; }
        return new ItemStack(Items.COAL);
    }

    private static String key(ServerPlayer p, CharacterType t) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.key(t)).orElse(t.getId());
    }

    private static void gain(CompoundTag c, String what, float n) {
        CompoundTag g = c.getCompound("gain"); g.putFloat(what, g.getFloat(what) + n); c.put("gain", g);
    }

    /** A stat's XP on the character's saved stats (as PartyBots.addXp, without an entity). */
    static void charXp(ServerPlayer o, CharacterType t, Stat stat, float amount, CompoundTag rec) {
        o.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            if (d.getType() == t) return;
            CompoundTag c = d.charStats.getCompound(t.getId());
            int[] lv = c.getIntArray("lv"); if (lv.length != 3) lv = new int[3];
            int i = stat.ordinal();
            float xp = c.getFloat("x" + i) + amount;
            int up = 0;
            while (lv[i] < Stat.MAX_LEVEL && xp >= stat.needed(lv[i])) { xp -= stat.needed(lv[i]); lv[i]++; up++; }
            c.putIntArray("lv", lv); c.putFloat("x" + i, xp);
            d.charStats.put(t.getId(), c);
            if (up > 0) gain(rec, stat.id() + "_lv", up);
        });
    }

    /** Mining is one level per player (every character mines at it, bots too): off-duty digging adds to it, quietly. */
    private static void miningXp(ServerPlayer p, float amount) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            if (d.miningLevel >= Mining.MAX_LEVEL) return;
            d.miningXp += amount;
            int before = d.miningLevel;
            while (d.miningLevel < Mining.MAX_LEVEL && d.miningXp >= Mining.needed(d.miningLevel)) { d.miningXp -= Mining.needed(d.miningLevel); d.miningLevel++; }
            if (d.miningLevel > before) com.xenowarriors.stats.HandMining.leveled(p, before, d.miningLevel);
        });
    }

    // ---------------------------------------------------------------- its bag (its saved inventory + extra tabs)

    private static BotInventory bag(ServerPlayer p, CharacterType t) {
        BotInventory inv = new BotInventory();
        inv.loadBag(CharacterInventories.mainItems(CharacterInventories.saved(p, t.getId())));
        CompoundTag tabs = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("xw_botbags");   // (BotBags' save)
        inv.loadExtra(tabs.getList(t.getId(), Tag.TAG_COMPOUND));
        return inv;
    }

    /** No room for even one more item (bag and extra tabs)? Also true when its things can't safely be written. */
    public static boolean bagFull(ServerPlayer p, CharacterType t) {
        if (!safeToWrite(p, t)) return true;
        BotInventory inv = bag(p, t);
        for (int i = 0; i < BotInventory.SIZE; i++) if (inv.getItem(i).isEmpty()) return false;
        return true;
    }

    /** Never into a save that a character switch would mistake for "never played" (nothing switched yet: no save of its own). */
    private static boolean safeToWrite(ServerPlayer p, CharacterType t) {
        return !CharacterInventories.live(p).isEmpty() || CharacterInventories.has(p, t.getId());
    }

    /** Into its bag (main slots first, then the extra tabs). Returns how many went in. */
    static int find(ServerPlayer p, CharacterType t, CompoundTag c, ItemStack s) {
        if (s.isEmpty() || !safeToWrite(p, t)) return 0;
        int want = s.getCount();
        CompoundTag saved = CharacterInventories.saved(p, t.getId());
        net.minecraft.world.SimpleContainer main = new net.minecraft.world.SimpleContainer(36);
        ListTag inv = saved.getList("inv", Tag.TAG_COMPOUND);
        ListTag rest = new ListTag();
        for (int i = 0; i < inv.size(); i++) {
            CompoundTag it = inv.getCompound(i);
            int slot = it.getByte("Slot") & 255;
            if (slot < 36) main.setItem(slot, ItemStack.of(it)); else rest.add(it);
        }
        ItemStack left = main.addItem(s.copy());
        if (left.getCount() < want) {
            ListTag out = new ListTag();
            for (int i = 0; i < 36; i++) if (!main.getItem(i).isEmpty()) { CompoundTag it = main.getItem(i).save(new CompoundTag()); it.putByte("Slot", (byte) i); out.add(it); }
            out.addAll(rest);
            saved.put("inv", out);
            CharacterInventories.putSaved(p, t.getId(), saved);
        }
        if (!left.isEmpty()) {                                                       // (then its extra tabs)
            BotInventory tabs = new BotInventory();
            ListTag had = BotBags.take(p, t.getId());
            tabs.loadExtra(had);
            for (int i = BotInventory.BAG; i < BotInventory.SIZE && !left.isEmpty(); i++) {
                ItemStack in = tabs.getItem(i);
                if (in.isEmpty()) { tabs.setItem(i, left.copy()); left = ItemStack.EMPTY; }
                else if (ItemStack.isSameItemSameTags(in, left) && in.getCount() < in.getMaxStackSize()) {
                    int n = Math.min(left.getCount(), in.getMaxStackSize() - in.getCount());
                    in.grow(n); left.shrink(n);
                }
            }
            BotBags.store(p, t.getId(), tabs.extraTag());
        }
        int in = want - left.getCount();
        if (in > 0) {
            CompoundTag h = c.getCompound("haul");
            String id = String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem()));
            h.putInt(id, h.getInt(id) + in);
            c.put("haul", h);
        }
        return in;
    }

    // ---------------------------------------------------------------- out with a Dragon Radar

    /**
     * How long a Dragon Ball search takes: 20-40 minutes (30 on average) for a slow one, and the higher its Speed, the
     * faster it gets them: Speed 100 about 80% of that, 1,000 about half, 10,000 a fifth, max Speed (30,000) an eighth -
     * never under 2 minutes.
     */
    static int searchMinutes(RandomSource r, int speed) { return Math.max(2, Math.round((20 + r.nextInt(21)) * speedFactor(speed))); }

    /** The share of a full-length search a bot with this Speed level takes (1 = slowest). */
    public static float speedFactor(int speed) { return (float) Math.min(1.0, 1.0 / Math.pow(1.0 + Math.max(0, speed) / 150.0, 0.4)); }

    /** The Dragon Radar in this off-duty character's bag (its item id), or null. */
    static String radar(ServerPlayer p, CharacterType t) {
        if (!safeToWrite(p, t)) return null;
        BotInventory inv = bag(p, t);
        for (int i = 0; i < BotInventory.SIZE; i++) { String id = com.xenowarriors.compat.DmzBotRadar.radarId(inv.getItem(i)); if (id != null) return id; }
        return null;
    }

    /** The search is over: the balls it found go into its bag. */
    static void radarDone(ServerPlayer p, CharacterType t, CompoundTag c) {
        String radar = c.getString("radar");
        c.remove("radar");
        var balls = com.xenowarriors.compat.DmzBotRadar.collect(p, radar);
        int n = 0;
        for (ItemStack s : balls) n += find(p, t, c, s);
        p.sendSystemMessage(Component.literal(t.getDisplayName().getString() + (n > 0 ? (n >= balls.size() ? " is back from searching with all " + (n >= 14 ? "14 Dragon Balls (both sets)" : "7 Dragon Balls") + " in their bag!" : " is back from searching with " + n + " Dragon Ball" + (n == 1 ? "" : "s") + " in their bag (no room for the rest)!")
                : " is back from searching for the Dragon Balls - empty-handed (no room in their bag).")).withStyle(n > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }

    /** (Tests) ends this character's Dragon Ball search now; returns the Dragon Balls in its bag after. */
    public static int finishSearchForTest(ServerPlayer p, CharacterType t) {
        radarDone(p, t, rec(p, t));
        int n = 0;
        ListTag inv = CharacterInventories.saved(p, t.getId()).getList("inv", Tag.TAG_COMPOUND);
        for (int i = 0; i < inv.size(); i++) { ItemStack s = ItemStack.of(inv.getCompound(i)); var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()); if (id != null && id.getPath().startsWith("dball") && !id.getPath().contains("radar")) n += s.getCount(); }
        return n;
    }

    /** A bot on your team with a Dragon Radar flies off to find the Dragon Balls (it leaves the team until it's done). */
    public static String sendSearching(ServerPlayer p, PartyBotEntity b) {
        String radar = null;
        for (int i = 0; i < b.inventory.getContainerSize() && radar == null; i++) radar = com.xenowarriors.compat.DmzBotRadar.radarId(b.inventory.getItem(i));
        if (radar == null) radar = com.xenowarriors.compat.DmzBotRadar.radarId(b.getMainHandItem());
        if (radar == null) radar = com.xenowarriors.compat.DmzBotRadar.radarId(b.getOffhandItem());
        if (radar == null) return b.character().getDisplayName().getString() + " has no Dragon Radar.";
        if (b.journey() || b.downed() || b.getTarget() != null) return b.character().getDisplayName().getString() + " can't go right now.";
        CharacterType t = b.character();
        if (b.canFly() && b.level() instanceof net.minecraft.server.level.ServerLevel l)
            l.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, b.getX(), b.getY() + 0.5, b.getZ(), 20, 0.4, 0.2, 0.4, 0.05);
        PartyBots.checkIn(b, p);
        b.discard();
        CompoundTag c = rec(p, t);
        int speed = PartyBots.statLevel(b, Stat.SPEED);
        int len = searchMinutes(p.getRandom(), speed);
        c.putInt("a", Act.DRAGON_BALLS.ordinal());
        c.putLong("u", p.level().getGameTime() + len * MINUTE);
        c.putString("radar", radar);
        c.remove("t");
        return t.getDisplayName().getString() + (b.canFly() ? " flies off" : " heads off") + " with the Dragon Radar to find the Dragon Balls (about " + len + " minutes" + (speedFactor(speed) < 0.95F ? " - quick thanks to Speed " + speed : "") + ").";
    }

    // ---------------------------------------------------------------- adding one to the team

    /**
     * May this character join right now? Free ones yes; busy ones only with a high bond (they drop what they're doing)
     * or when it was your job for them. Tells the player why not.
     */
    public static boolean mayJoin(ServerPlayer p, CharacterType t) {
        Act a = act(p, t);
        if (a == Act.NONE || !task(p, t).isEmpty()) return true;
        int bond = Bonds.level(p, t.getId());
        String what = status(p, t);
        if (bond >= OVERRIDE_BOND) {
            p.sendSystemMessage(Component.literal(t.getDisplayName().getString() + " stops " + verb(a) + " to join you (bond " + bond + ").").withStyle(ChatFormatting.LIGHT_PURPLE));
            return true;
        }
        p.sendSystemMessage(Component.literal(t.getDisplayName().getString() + " is busy: " + what + ". (A bond of " + OVERRIDE_BOND + "+ would bring them anyway - yours is " + bond + ".)").withStyle(ChatFormatting.RED));
        return false;
    }

    static String verbOf(Act a) { return verb(a); }

    private static String verb(Act a) {
        return switch (a) {
            case TRAINING -> "training"; case PARTY -> "partying"; case DMZ -> "their DMZ mission"; case MINING -> "mining";
            case WOOD -> "chopping wood"; case BUILDING -> "building"; case DRAGON_BALLS -> "searching for the Dragon Balls"; case HUNTING -> "hunting for food"; default -> "relaxing";
        };
    }

    /** It joined: it's on duty now; tells what it brought back and gained. */
    public static void joined(ServerPlayer p, CharacterType t) {
        CompoundTag c = rec(p, t);
        leaveGroup(p, t);
        c.putInt("a", 0); c.remove("t"); c.remove("full"); c.putLong("u", 0); c.remove(BotCalls.CALLED);
        List<String> parts = new ArrayList<>();
        CompoundTag h = c.getCompound("haul");
        for (String k : h.getAllKeys()) {
            Item it = ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(k));
            if (it != null) parts.add(h.getInt(k) + " " + new ItemStack(it).getHoverName().getString());
        }
        CompoundTag g = c.getCompound("gain");
        for (String k : g.getAllKeys()) if (k.endsWith("_lv")) parts.add("+" + (int) g.getFloat(k) + " " + cap(k.substring(0, k.length() - 3)) + " lv");
        if (g.getFloat("building_xp") > 0) parts.add("+" + (int) g.getFloat("building_xp") + " Building XP");
        if (g.getFloat("dmz_tp") > 0) parts.add("+" + (int) g.getFloat("dmz_tp") + " DMZ TP");
        c.remove("haul"); c.remove("gain");
        if (!parts.isEmpty()) p.sendSystemMessage(Component.literal(t.getDisplayName().getString() + " brought back: " + String.join(", ", parts) + " (in their bag)").withStyle(ChatFormatting.GREEN));
    }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    // ---------------------------------------------------------------- jobs you give

    /** Gives a job (bond {@value #ASSIGN_BOND}+), or "recall". Returns the message. */
    public static String assign(ServerPlayer p, CharacterType t, String job) {
        if (!enabled()) return "Off-duty activities are turned off in this world.";
        if (!Arrays.asList(CharacterType.PLAYABLE).contains(t)) return "No such character.";
        if (locked(p.server, t)) return t.getDisplayName().getString() + " is Season " + t.season() + " content.";
        if (onTeam(p, t)) return t.getDisplayName().getString() + " is with you right now.";
        CompoundTag c = rec(p, t);
        if (job.equals("recall")) {
            leaveGroup(p, t);
            c.remove("t"); c.putInt("a", 0); c.putLong("u", p.level().getGameTime() + 10 * MINUTE);
            return t.getDisplayName().getString() + " is back and free to join you.";
        }
        Act a = TASKS.get(job);
        if (a == null) return "Jobs: " + String.join(", ", TASKS.keySet()) + ", recall.";
        int bond = Bonds.level(p, t.getId());
        if (bond < ASSIGN_BOND) return "You need a bond of " + ASSIGN_BOND + "+ with " + t.getDisplayName().getString() + " to give them a job (yours is " + bond + ").";
        if (a == Act.DMZ && !dmz(p, t)) return t.getDisplayName().getString() + " has no Dragon Ball character of their own yet.";
        if (a.relaxing() && tireless(t)) { a = Act.TRAINING; job = "training"; }   // doesn't know how to rest
        leaveGroup(p, t);
        c.putString("t", job); c.putInt("a", a.ordinal()); c.remove("full");
        return t.getDisplayName().getString() + " goes " + switch (a) { case RELAX -> "off to relax"; case TRAINING -> "training"; case DMZ -> "on DMZ missions"; default -> verb(a); } + " until you call them back.";
    }

    // ---------------------------------------------------------------- on the team: downs, time, wandering off

    private static final Map<UUID, Long> COMBAT = new HashMap<>(), LAST_LEAVE = new HashMap<>();

    /** A teammate went down (counts towards resting afterwards). */
    static void downed(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        if (o == null || b.journey()) return;
        CompoundTag c = rec(o, b.character());
        long[] d = c.getLongArray("d");
        long now = o.level().getGameTime();
        List<Long> keep = new ArrayList<>();
        for (long x : d) if (now - x <= DOWN_WINDOW_MINUTES * MINUTE) keep.add(x);
        keep.add(now);
        c.putLongArray("d", keep.stream().mapToLong(Long::longValue).toArray());
    }

    public static void combat(UUID owner, long now) { if (owner != null) COMBAT.put(owner, now); }

    public static long lastCombat(UUID owner) { return COMBAT.getOrDefault(owner, Long.MIN_VALUE / 2); }

    /** (Tests) */
    public static void setLastCombat(UUID owner, long t) { COMBAT.put(owner, t); }
    public static void resetLeave(UUID owner) { LAST_LEAVE.remove(owner); }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity().level().isClientSide) return;
        long now = e.getEntity().level().getGameTime();
        stamp(e.getEntity(), now);
        stamp(e.getSource().getEntity(), now);
    }

    private static void stamp(Entity x, long now) {
        if (x instanceof ServerPlayer sp && !(x instanceof FakePlayer)) combat(sp.getUUID(), now);
        PartyBotEntity b = PartyBots.botOf(x);
        if (b != null) combat(b.ownerId(), now);
    }

    /** Where a teammate never wanders off: a Journey, the Tower, VR (raids and fights count as combat). */
    static boolean busyPlace(ServerPlayer p) {
        return com.xenowarriors.journey.Journey.inLobbyOrRun(p) || com.xenowarriors.tower.Tower.is(p.level()) || com.xenowarriors.vr.Vr.is(p.level());
    }

    /**
     * Once a simulated minute for a player's live bots: time on the team, and maybe one low-bond bot leaving to do its
     * own thing after {@value #QUIET_MINUTES}+ quiet minutes (one at a time, at most once per {@value #LEAVE_COOLDOWN_MINUTES}).
     * Returns the one that left, or null. {@code force}: (tests) no dice.
     */
    public static PartyBotEntity teamMinute(ServerPlayer p, long now, RandomSource r, boolean force) {
        if (!enabled()) return null;
        List<PartyBotEntity> bots = PartyBots.bots(p);
        for (PartyBotEntity b : bots) if (!b.journey() && !b.ownCharacter()) { CompoundTag c = rec(p, b.character()); c.putInt("tm", c.getInt("tm") + 1); }
        if (bots.isEmpty() || busyPlace(p) || now - lastCombat(p.getUUID()) < QUIET_MINUTES * MINUTE) return null;
        Long last = LAST_LEAVE.get(p.getUUID());
        if (last != null && now - last < LEAVE_COOLDOWN_MINUTES * MINUTE) return null;
        for (PartyBotEntity b : bots) {
            if (b.journey() || b.downed() || b.sparring() || b.isPassenger() || b.isVehicle() || b.ownCharacter()) continue;   // (your own characters don't wander off)
            if (Bonds.level(p, b.charKey()) >= LEAVE_BOND || rec(p, b.character()).getInt("tm") < QUIET_MINUTES) continue;
            if (!force && r.nextFloat() >= 0.1F) continue;
            CharacterType t = b.character();
            PartyBots.checkIn(b, p);
            b.discard();
            CompoundTag c = rec(p, t);
            c.putInt("a", Act.TRAINING.ordinal()); c.putLong("u", now + (15 + r.nextInt(16)) * MINUTE); c.remove("t");
            LAST_LEAVE.put(p.getUUID(), now);
            p.sendSystemMessage(Component.literal(t.getDisplayName().getString() + " got restless with nothing going on and went off to train. (Raise your bond to " + LEAVE_BOND + "+ and they'll stay.)").withStyle(ChatFormatting.GRAY));
            return b;
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        if (s.getTickCount() % MINUTE != 613) return;
        if (!enabled()) return;
        long now = s.overworld().getGameTime();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (p instanceof FakePlayer || !p.isAlive()) continue;
            try {
                COMBAT.putIfAbsent(p.getUUID(), now);
                teamMinute(p, now, p.getRandom(), false);
                minute(p, now, p.getRandom());
            } catch (Throwable t) { XenoWarriors.LOGGER.warn("Off-duty bots: a minute for {} failed", p.getGameProfile().getName(), t); }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { COMBAT.remove(e.getEntity().getUUID()); LAST_LEAVE.remove(e.getEntity().getUUID()); }

    // ---------------------------------------------------------------- what the pick screen gets

    /** Per playable character: activity, label, your bond with it. */
    public static List<com.xenowarriors.network.OpenBotPickPacket.Act> forPick(ServerPlayer p) {
        List<com.xenowarriors.network.OpenBotPickPacket.Act> out = new ArrayList<>();
        boolean on = enabled();
        for (CharacterType t : CharacterType.PLAYABLE) {
            boolean free = !on || onTeam(p, t) || locked(p.server, t);
            Act a = free ? Act.NONE : act(p, t);
            out.add(new com.xenowarriors.network.OpenBotPickPacket.Act(t.getId(), a.ordinal(), a == Act.NONE ? "" : status(p, t), Bonds.level(p, t.getId()), !task(p, t).isEmpty()));
        }
        return out;
    }

    // ---------------------------------------------------------------- (tests)

    public static void setForTest(ServerPlayer p, CharacterType t, Act a, long until, String task) {
        CompoundTag c = rec(p, t);
        leaveGroup(p, t);
        c.putInt("a", a.ordinal()); c.putLong("u", until);
        if (task == null) c.remove("t"); else c.putString("t", task);
        c.remove("full"); c.remove("haul"); c.remove("gain"); c.remove("d"); c.remove("tm");
    }

    public static boolean startPartyForTest(ServerPlayer p, CharacterType t, long now, int minutes) {
        if (!startParty(p, t, now, minutes, RandomSource.create(7))) return false;
        return true;
    }

    public static int giveForTest(ServerPlayer p, CharacterType t, ItemStack s) { return find(p, t, rec(p, t), s); }

    public static void setTeamMinutesForTest(ServerPlayer p, CharacterType t, int m) { rec(p, t).putInt("tm", m); }

    public static Act chooseForTest(ServerPlayer p, CharacterType t, long now) { return choose(p, t, rec(p, t), now, RandomSource.create(3)); }

    // ---------------------------------------------------------------- commands

    private static boolean creative(CommandSourceStack src) {
        if (src.getEntity() instanceof ServerPlayer sp && sp.isCreative()) return true;
        src.sendFailure(Component.literal("Creative mode only."));
        return false;
    }

    /** (Tests) the same checks the commands run. */
    public static String runToggle(ServerPlayer p, boolean on) {
        if (!p.isCreative()) return "Creative mode only.";
        setEnabled(p.server, on);
        return "Off-duty bot activities " + (on ? "on" : "off") + ".";
    }

    public static String runLimit(ServerPlayer p, int n) {
        if (!p.isCreative()) return "Creative mode only.";
        setPartyLimit(p.server, n);
        com.xenowarriors.party.Parties.sendLimit(p.server);
        return "Party limit is now " + partyLimit() + " (players and bots).";
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xeno")
                .then(Commands.literal("offduty")
                        .then(Commands.literal("on").executes(c -> { if (!creative(c.getSource())) return 0; c.getSource().sendSuccess(() -> Component.literal(runToggle((ServerPlayer) c.getSource().getEntity(), true)), true); return 1; }))
                        .then(Commands.literal("off").executes(c -> { if (!creative(c.getSource())) return 0; c.getSource().sendSuccess(() -> Component.literal(runToggle((ServerPlayer) c.getSource().getEntity(), false)), true); return 1; }))
                        .then(Commands.literal("task").then(Commands.argument("character", StringArgumentType.word()).then(Commands.argument("job", StringArgumentType.word())
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    String msg = assign(p, CharacterType.byId(StringArgumentType.getString(c, "character")), StringArgumentType.getString(c, "job"));
                                    p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.AQUA));
                                    return 1;
                                })))))
                .then(Commands.literal("radar").then(Commands.argument("bot", IntegerArgumentType.integer()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    com.xenowarriors.network.RequestPopupPacket.dismiss(p, "/xeno radar " + IntegerArgumentType.getInteger(c, "bot"));
                    if (!(p.level().getEntity(IntegerArgumentType.getInteger(c, "bot")) instanceof PartyBotEntity b) || !p.getUUID().equals(b.ownerId())) return 0;
                    p.sendSystemMessage(Component.literal(sendSearching(p, b)).withStyle(ChatFormatting.GOLD));
                    return 1;
                })))
                .then(Commands.literal("partylimit").then(Commands.argument("n", IntegerArgumentType.integer(1, 16))
                        .executes(c -> { if (!creative(c.getSource())) return 0; String m = runLimit((ServerPlayer) c.getSource().getEntity(), IntegerArgumentType.getInteger(c, "n")); c.getSource().sendSuccess(() -> Component.literal(m), true); return 1; }))));
    }
}
