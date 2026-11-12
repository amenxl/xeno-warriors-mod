package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * With DragonMineZ: bot teammates do Dragon Ball missions of their own, on their own DMZ character (saved per bot,
 * see {@link DmzPerCharacter}):
 * <ul>
 *   <li>A mission started from a bot's Dragon Ball menu is the bot's (its data is in the menu then). When the target
 *       is defeated - by the bot, by you, or by anyone in your DragonMineZ party - the bot's objective counts, and
 *       when it's done the bot completes the mission and gets its rewards (TP, alignment, skills, techniques, forms;
 *       item rewards go in its bag). Your own mission only counts when you're on that mission too (then both do).</li>
 *   <li>When you start a mission yourself, each of your bots with a DMZ character starts one at the same time: the
 *       same one if it can, else its own next available one (its enemies are summoned like yours). A chat line says
 *       what each started. When that mission's giver is close by, the bot walks over and takes it from them (started
 *       anyway after 30 s). On their own they only take missions with no enemy or boss to fight (autoAccept).</li>
 *   <li>Location objectives count while you (or a party member) are there; "talk to" objectives when you talk to that
 *       person; missions handed in to a person are handed in for the bot when you talk to them (DmzTalkNpcs brings them to you),
 *       or the bot walks up to them and talks to them itself (DmzBotTalk); story
 *       fighters that never spawn by themselves are summoned for its objectives like for yours (DmzQuestHelp).</li>
 * </ul>
 * The progress lives in the bot's saved DMZ character, so it stays over relogs and re-adding the bot. All by
 * reflection against DragonMineZ 2.1.x; nothing happens without it.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzBotQuests {
    private DmzBotQuests() {}

    private static final String K = "dmz_quest_key", I = "dmz_quest_objective_index", O = "dmz_quest_owner", SUMMONED = "xw_dmz_bot_qsum";
    private static Boolean loaded;
    private static boolean broken, listening;
    static Class<?> pqdClass, registry, service, killObj, talkObj, itemObj, locHelper, avail, statsData;
    static Method unlocked, spawnKill, isLocation, locationMet, resolve, turnInReady, needsTurnIn;

    /** (For the tests) what happened, newest last. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    /** (For the tests) how many missions bots started on their own / completed. */
    public static int autoStarts, completions;

    private static void note(String s) {
        if (LOG.size() > 80) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz bot missions: {}", s);
    }

    static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (spawnKill != null) return true;
        try {
            pqdClass = Class.forName("com.dragonminez.common.quest.PlayerQuestData");
            registry = Class.forName("com.dragonminez.common.quest.QuestRegistry");
            service = Class.forName("com.dragonminez.common.quest.QuestService");
            killObj = Class.forName("com.dragonminez.common.quest.objectives.KillObjective");
            talkObj = Class.forName("com.dragonminez.common.quest.objectives.TalkToObjective");
            try { itemObj = Class.forName("com.dragonminez.common.quest.objectives.ItemObjective"); } catch (Throwable t) { itemObj = null; }
            locHelper = Class.forName("com.dragonminez.common.quest.QuestLocationHelper");
            avail = Class.forName("com.dragonminez.common.quest.QuestAvailabilityChecker");
            statsData = Class.forName("com.dragonminez.common.stats.StatsData");
            Class<?> quest = Class.forName("com.dragonminez.common.quest.Quest");
            Class<?> objective = Class.forName("com.dragonminez.common.quest.QuestObjective");
            unlocked = Class.forName("com.dragonminez.server.events.QuestEvents").getDeclaredMethod("isKillObjectiveUnlocked", pqdClass, String.class, quest, int.class);
            unlocked.setAccessible(true);
            isLocation = locHelper.getMethod("isLocationObjective", objective);
            locationMet = locHelper.getMethod("isLocationConditionMet", ServerPlayer.class, objective);
            resolve = service.getMethod("resolveQuest", String.class);
            turnInReady = service.getMethod("isTurnInReady", pqdClass, String.class, quest);
            needsTurnIn = service.getMethod("requiresTurnInAction", quest);
            Method spawn = null;
            for (Method mm : service.getDeclaredMethods()) if (mm.getName().equals("spawnKillObjectives") && mm.getParameterCount() == 5) spawn = mm;
            if (spawn == null) throw new NoSuchMethodException("spawnKillObjectives");
            spawn.setAccessible(true);
            spawnKill = spawn;
            return true;
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("Bots' own DragonMineZ missions off (its API changed?)", t);
            return false;
        }
    }

    /** The methods m() may call, by class and "name/argument count" (getMethods() copies the whole list on every call). */
    private static final Map<Class<?>, Map<String, Method[]>> METHODS = new java.util.concurrent.ConcurrentHashMap<>();

    static Object m(Object o, String name, Object... args) throws Exception {
        Map<String, Method[]> byName = METHODS.computeIfAbsent(o.getClass(), c -> new java.util.concurrent.ConcurrentHashMap<>());
        String k = name + "/" + args.length;
        Method[] cands = byName.get(k);
        if (cands == null) {
            List<Method> l = new ArrayList<>();
            for (Method mm : o.getClass().getMethods())
                if (mm.getName().equals(name) && mm.getParameterCount() == args.length) {
                    if (!java.lang.reflect.Modifier.isPublic(mm.getDeclaringClass().getModifiers())) mm.trySetAccessible();   // (a private record's accessors)
                    l.add(mm);
                }
            cands = l.toArray(new Method[0]);
            byName.put(k, cands);
        }
        for (Method mm : cands) {
            try { return mm.invoke(o, args); } catch (IllegalArgumentException wrongOverload) { /* try the next one */ }
        }
        throw new NoSuchMethodException(o.getClass().getSimpleName() + "." + name);
    }

    private static Method getQuest, serverConfig, partyMembers;

    static Object quest(String key) {
        try {
            if (getQuest == null) getQuest = registry.getMethod("getQuest", String.class);
            return getQuest.invoke(null, key);
        } catch (Throwable t) { return null; }
    }

    private static Object saga(String key) {
        try { Object r = resolve.invoke(null, key); return r == null ? null : m(r, "saga"); } catch (Throwable t) { return null; }
    }

    /** The mission's name as DragonMineZ shows it (its title is usually a translation key). */
    public static MutableComponent title(String key) {
        Object q = quest(key);
        try {
            String t = q == null ? null : (String) m(q, "getTitle");
            if (t != null && !t.isBlank()) return Component.translatable(t);
        } catch (Throwable ignored) {}
        return Component.literal(key);
    }

    static boolean typeEnabled(Object q) {
        try {
            if (serverConfig == null) serverConfig = Class.forName("com.dragonminez.common.config.ConfigManager").getMethod("getServerConfig");
            Object cfg = serverConfig.invoke(null);
            Object g = m(cfg, "getGameplay");
            if ((Boolean) m(q, "isSagaQuest")) return Boolean.TRUE.equals(m(g, "getStoryModeEnabled"));
            if ((Boolean) m(q, "isSideQuest")) return Boolean.TRUE.equals(m(g, "getSideQuestsEnabled"));
            return true;
        } catch (Throwable t) { return true; }
    }

    /** Everyone in this player's DragonMineZ party (just them without one). */
    @SuppressWarnings("unchecked")
    static List<ServerPlayer> party(ServerPlayer p) {
        try {
            if (partyMembers == null) partyMembers = Class.forName("com.dragonminez.common.quest.PartyManager").getMethod("getAllPartyMembers", ServerPlayer.class);
            List<ServerPlayer> l = (List<ServerPlayer>) partyMembers.invoke(null, p);
            if (l != null && !l.isEmpty()) {
                List<ServerPlayer> out = new ArrayList<>();
                for (ServerPlayer s : l) if (s != null && !(s instanceof FakePlayer) && !out.contains(s)) out.add(s);
                if (!out.contains(p)) out.add(0, p);
                return out;
            }
        } catch (Throwable ignored) {}
        return new ArrayList<>(List.of(p));
    }

    // ------------------------------------------------------------------ a bot's DMZ data, to read and change

    /** A bot's DMZ data: the live one while its menu is open, else a copy of its saved one that {@link #commit} writes back. */
    static final class Data {
        final PartyBotEntity bot; final ServerPlayer owner; final String key; final boolean live; final Object sd, pqd; final CompoundTag tag;
        final Set<String> touched = new HashSet<>();
        Data(PartyBotEntity bot, ServerPlayer owner, String key, boolean live, Object sd, Object pqd, CompoundTag tag) {
            this.bot = bot; this.owner = owner; this.key = key; this.live = live; this.sd = sd; this.pqd = pqd; this.tag = tag;
        }

        /** Saves what changed: the mission data (and whatever rewards touched) go back into the bot's saved character. */
        void commit() {
            if (live) { DmzPerCharacter.syncAll(owner); return; }
            CompoundTag out = DmzPerCharacter.saveData(sd);
            if (out == null || !out.contains("PlayerQuestData")) return;
            tag.put("PlayerQuestData", out.getCompound("PlayerQuestData").copy());
            for (String s : touched) if (out.contains(s)) tag.put(s, out.get(s).copy());   // (only those: its stats stay as saved)
            DmzPerCharacter.store(owner, key, tag);
        }
    }

    /** Has the saved character any mission going? (Cheap: just its mission data.) */
    private static boolean anyActive(CompoundTag tag) {
        try {
            Object q = pqdClass.getConstructor().newInstance();
            m(q, "deserializeNBT", tag.getCompound("PlayerQuestData"));
            return !((Set<?>) m(q, "getAcceptedQuestIds")).isEmpty();
        } catch (Throwable t) { return false; }
    }

    /** The bot's DMZ data, or null (no DMZ character, or it's the one you're playing). activeOnly: null unless it has a mission going. */
    static Data open(PartyBotEntity b, boolean activeOnly) {
        if (!ready()) return null;
        ServerPlayer o = b.owner();
        if (o == null || o instanceof FakePlayer) return null;
        String key = DmzPerCharacter.botKey(b);
        if (DmzPerCharacter.isLive(o, key)) return null;
        try {
            if (key.equals(DmzPerCharacter.viewing(o))) {
                Object sd = DmzPerCharacter.live(o);
                return sd == null ? null : new Data(b, o, key, true, sd, m(sd, "getPlayerQuestData"), null);
            }
            CompoundTag tag = DmzPerCharacter.saved(o, key);
            if (tag == null || !tag.getCompound("Status").getBoolean("HasCreatedChar") || !tag.contains("PlayerQuestData")) return null;
            if (activeOnly && !anyActive(tag)) return null;
            Player stand = PartyBots.standIn(b);
            Object sd = stand == null ? null : DmzPerCharacter.blank(stand);
            if (sd == null || !DmzPerCharacter.loadData(sd, tag.copy())) return null;
            return new Data(b, o, key, false, sd, m(sd, "getPlayerQuestData"), tag);
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("Couldn't read a bot's DragonMineZ missions", t);
            return null;
        }
    }

    // ------------------------------------------------------------------ a cheap look: just the mission data, cached

    private record Peek(CompoundTag src, int hash, Object pqd) {}
    private static final Map<PartyBotEntity, Peek> PEEKS = new WeakHashMap<>();

    /** Is the bot's Dragon Ball menu open (its data live in its owner's capability)? */
    private static boolean viewed(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        return o != null && DmzPerCharacter.botKey(b).equals(DmzPerCharacter.viewing(o));
    }

    /**
     * The bot's saved mission data on its own - read only - or null when it has no mission going, no DMZ character,
     * or it's the one being played / its menu is open. Parsed again only when the saved missions changed, so the
     * once-a-second checks (here and in DmzTalkNpcs) and every kill don't load the bot's whole DMZ character.
     */
    static Object peek(PartyBotEntity b) {
        if (!ready()) return null;
        ServerPlayer o = b.owner();
        if (o == null || o instanceof FakePlayer) return null;
        String key = DmzPerCharacter.botKey(b);
        if (DmzPerCharacter.isLive(o, key) || key.equals(DmzPerCharacter.viewing(o))) return null;
        CompoundTag tag = DmzPerCharacter.saved(o, key);
        if (tag == null || !tag.getCompound("Status").getBoolean("HasCreatedChar") || !tag.contains("PlayerQuestData")) { PEEKS.remove(b); return null; }
        CompoundTag q = tag.getCompound("PlayerQuestData");
        int h = q.hashCode();
        Peek pk = PEEKS.get(b);
        if (pk == null || pk.src != q || pk.hash != h) {
            Object pqd = null;
            try {
                Object fresh = pqdClass.getConstructor().newInstance();
                m(fresh, "deserializeNBT", q.copy());
                if (!((Set<?>) m(fresh, "getAcceptedQuestIds")).isEmpty()) pqd = fresh;
            } catch (Throwable t) { pqd = null; }
            pk = new Peek(q, h, pqd);
            PEEKS.put(b, pk);
            peekParses++;
        }
        return pk.pqd;
    }

    /** (For the perf test) how often peek() had to parse the mission data again. */
    public static long peekParses;

    /** Might open(b, true) give anything? (Its menu is open, or it has a mission going.) */
    private static boolean mayBeActive(PartyBotEntity b) {
        return viewed(b) || peek(b) != null;
    }

    @SuppressWarnings("unchecked")
    static List<String> accepted(Object pqd) throws Exception {
        return new ArrayList<>((Set<String>) m(pqd, "getAcceptedQuestIds"));
    }

    static int progress(Object pqd, String key, int i) throws Exception { return (Integer) m(pqd, "getObjectiveProgress", key, i); }
    static int required(Object q, Object pqd, String key, int i) throws Exception { return (Integer) m(q, "getObjectiveRequired", pqd, key, i); }

    private static boolean firstOpen(Object pqd, String key, Object q, int index) throws Exception {
        for (int i = 0; i < index; i++) if (progress(pqd, key, i) < required(q, pqd, key, i)) return false;
        return true;
    }

    // ------------------------------------------------------------------ 1: defeating the target

    /** After DragonMineZ (and DmzBots) counted the kill for the players: count it for every bot of the killer's party. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (e.isCanceled() || dead.level().isClientSide || dead instanceof Player || dead instanceof PartyBotEntity || !ready()) return;
        Entity src = e.getSource().getEntity();
        PartyBotEntity killerBot = null;
        ServerPlayer main;
        if (src instanceof ServerPlayer sp && !(sp instanceof FakePlayer)) main = sp;
        else {
            killerBot = DmzBots.killerBot(src);
            main = killerBot == null ? null : killerBot.owner();
        }
        if (main == null || main instanceof FakePlayer) return;
        List<ServerPlayer> party = party(main);
        Set<String> owners = new HashSet<>();
        for (ServerPlayer s : party) owners.add(s.getStringUUID());
        // a bot's open menu puts its data in its owner's capability: DragonMineZ counted the kill there itself unless a
        // bot whose owner has a menu open landed it (DmzBots skips DragonMineZ's count then)
        boolean dmzCountedLive = killerBot == null || DmzPerCharacter.viewing(killerBot.owner()) == null;
        for (ServerPlayer r : party) {
            for (PartyBotEntity b : PartyBots.bots(r)) {
                if (b.journey() || !mayBeActive(b)) continue;
                Object pq = peek(b);                                                 // (null: its menu is open - the live data, as before)
                try { if (pq != null && !killCounts(pq, dead, owners)) continue; }  // (counts for none of its missions: nothing to load)
                catch (Throwable t) { /* look properly below */ }
                Data d = open(b, true);
                if (d == null || (d.live && dmzCountedLive)) continue;
                try { creditKill(d, dead, owners, src, b == killerBot); }
                catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot's DragonMineZ mission didn't count a kill", t); }
            }
        }
    }

    /** Would creditKill count this kill for any of these missions? (Read only; the same tests.) */
    private static boolean killCounts(Object pqd, LivingEntity dead, Set<String> owners) throws Exception {
        for (String key : accepted(pqd)) {
            Object q = quest(key);
            if (q == null || (Boolean) m(pqd, "isQuestCompleted", key) || !typeEnabled(q)) continue;
            List<?> objs = (List<?>) m(q, "getObjectives");
            for (int i = 0; i < objs.size(); i++) {
                Object obj = objs.get(i);
                if (!killObj.isInstance(obj)) continue;
                if (progress(pqd, key, i) >= required(q, pqd, key, i) || !(Boolean) unlocked.invoke(null, pqd, key, q, i) || !matches(dead, key, i, obj, owners)) continue;
                return true;
            }
        }
        return false;
    }

    private static void creditKill(Data d, LivingEntity dead, Set<String> owners, Entity src, boolean ownKill) throws Exception {
        boolean changed = false;
        for (String key : accepted(d.pqd)) {
            Object q = quest(key);
            if (q == null || (Boolean) m(d.pqd, "isQuestCompleted", key) || !typeEnabled(q)) continue;
            List<?> objs = (List<?>) m(q, "getObjectives");
            boolean counted = false;
            for (int i = 0; i < objs.size(); i++) {
                Object obj = objs.get(i);
                if (!killObj.isInstance(obj)) continue;
                int prog = progress(d.pqd, key, i), req = required(q, d.pqd, key, i);
                if (prog >= req || !(Boolean) unlocked.invoke(null, d.pqd, key, q, i) || !matches(dead, key, i, obj, owners)) continue;
                m(d.pqd, "setObjectiveProgress", key, i, prog + 1);
                counted = true;
                note("kill counted for " + d.key + " (" + d.owner.getGameProfile().getName() + "'s bot) on " + key + "#" + i + ": " + (prog + 1) + "/" + req
                        + " - " + ForgeRegistries.ENTITY_TYPES.getKey(dead.getType()) + " defeated by " + (ownKill ? "the bot" : src == null ? "?" : src.getName().getString()));
            }
            if (counted) { changed = true; complete(d, key, q, null); }
        }
        if (changed) d.commit();
    }

    /** DragonMineZ's own test: a target summoned for this objective (by you or your party), or - when it counts any - one of that kind not summoned for something else. */
    private static boolean matches(LivingEntity dead, String key, int index, Object obj, Set<String> owners) throws Exception {
        boolean typeMatches = (Boolean) m(obj, "matches", dead.getType());
        CompoundTag t = dead.getPersistentData();
        boolean hasTags = t.contains(K) || t.contains(I) || t.contains(O);
        boolean tagsMatch = t.contains(K) && t.contains(I) && t.contains(O) && key.equals(t.getString(K)) && index == t.getInt(I) && owners.contains(t.getString(O));
        return tagsMatch || (typeMatches && !hasTags && "ANY_MATCHING".equals(String.valueOf(m(obj, "getCountMode"))));
    }

    /**
     * All done? Then the bot completes it and gets the rewards. One that ends by handing it in to a person completes
     * when you talk to that person ({@code talkedTo}: their id) - DmzTalkNpcs brings them to you.
     */
    static boolean complete(Data d, String key, Object q, String talkedTo) throws Exception {
        if ((Boolean) m(d.pqd, "isQuestCompleted", key)) return false;
        List<?> objs = (List<?>) m(q, "getObjectives");
        if ((Boolean) needsTurnIn.invoke(null, q)) {
            if (talkedTo == null || !talkedTo.equals(m(q, "getTurnIn")) || !(Boolean) turnInReady.invoke(null, d.pqd, key, q)) return false;
            for (int i = 0; i < objs.size(); i++) if (talkObj.isInstance(objs.get(i))) m(d.pqd, "setObjectiveProgress", key, i, required(q, d.pqd, key, i));
        } else {
            for (int i = 0; i < objs.size(); i++) if (progress(d.pqd, key, i) < required(q, d.pqd, key, i)) return false;
        }
        m(d.pqd, "completeQuest", key);
        if (key.equals(m(d.pqd, "getTrackedQuestId"))) m(d.pqd, "setTrackedQuestId", (Object) null);
        String got = claim(d, key, q);
        completions++;
        note("mission complete for " + d.key + ": " + key + " rewards " + got);
        DmzNews.bot(d.owner, d.bot, "Mission complete", Component.empty().append(d.bot.character().getDisplayName()).append(" completed: ").append(title(key))
                .append(got.isEmpty() ? "" : " (" + got + ")").withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** Gives the bot every reward it hasn't had yet (as DragonMineZ would on claiming: scaled by its difficulty). Returns a short summary. */
    private static String claim(Data d, String key, Object q) throws Exception {
        List<?> rewards = (List<?>) m(q, "getRewards");
        Object diff = m(d.pqd, "getDifficulty");
        double mult = ((Number) m(diff, "questRewardMultiplier")).doubleValue();
        List<String> got = new ArrayList<>();
        for (int i = 0; i < rewards.size(); i++) {
            Object r = rewards.get(i);
            if ((Boolean) m(d.pqd, "isRewardClaimed", key, i) || !(Boolean) m(r, "isUnlockedFor", diff)) continue;
            try {
                String g = give(d, r, mult);
                if (g != null && !g.isEmpty()) got.add(g);
            } catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot's DragonMineZ mission reward failed: " + r.getClass().getSimpleName(), t); }
            m(d.pqd, "claimReward", key, i);
        }
        return String.join(", ", got);
    }

    private static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static String give(Data d, Object r, double mult) throws Exception {
        String kind = r.getClass().getSimpleName();
        switch (kind) {
            case "TPSReward" -> {
                int n = (Integer) m(r, "scaledAmount", mult);
                if (n <= 0) return null;
                m(m(d.sd, "getResources"), "addTrainingPoints", (float) n, false);
                d.touched.add("Resources");
                return "+" + n + " TP";
            }
            case "AlignmentReward" -> {
                int n = (Integer) m(r, "scaledAmount", mult);
                if (n == 0) return null;
                m(m(d.sd, "getResources"), "addAlignment", n);
                d.touched.add("Resources");
                return (n > 0 ? "+" : "") + n + " alignment";
            }
            case "SkillReward" -> {
                String skill = (String) m(r, "getSkill");
                int lv = (Integer) m(r, "getLevel");
                Object skills = m(d.sd, "getSkills");
                if ((Integer) m(skills, "getSkillLevel", skill) < lv) m(skills, "setSkillLevel", skill, lv);
                d.touched.add("Skills");
                return skill + " " + lv;
            }
            case "KiTechniqueReward" -> {
                Object tmpl = m(r, "getTemplate");
                Object copy = tmpl.getClass().getConstructor().newInstance();
                m(copy, "load", m(tmpl, "save"));
                m(m(d.sd, "getTechniques"), "unlockTechnique", copy);
                d.touched.add("Techniques");
                return String.valueOf(m(tmpl, "getName"));
            }
            case "TransformationReward" -> {
                Object ch = m(d.sd, "getCharacter");
                Method grant = r.getClass().getDeclaredMethod("grantFormSkill", statsData, String.class);
                grant.setAccessible(true);
                grant.invoke(r, d.sd, m(ch, "getRaceName"));
                boolean stack = (Boolean) field(r, "stack");
                String group = (String) field(r, "formGroup"), form = (String) field(r, "formName");
                double mastery = ((Number) field(r, "mastery")).doubleValue();
                Object masteries = m(ch, stack ? "getStackFormMasteries" : "getFormMasteries");
                if (mastery > ((Number) m(masteries, "getMastery", group, form)).doubleValue()) m(masteries, "setMastery", group, form, mastery, Double.MAX_VALUE);
                d.touched.add("Character"); d.touched.add("Skills");
                return "form " + form;
            }
            case "ItemReward", "GenericItemReward" -> {
                String id = kind.equals("ItemReward") ? (String) m(r, "getItemId") : (String) m(m(r, "getItemReward"), "getItemId");
                int n = (Integer) m(r, "scaledCount", mult);
                Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
                if (it == null || n <= 0) return null;
                ItemStack left = d.bot.inventory.addItem(new ItemStack(it, n));       // (in its bag; what doesn't fit goes to you)
                if (!left.isEmpty() && !d.owner.getInventory().add(left)) d.owner.drop(left, false);
                return n + "x " + new ItemStack(it).getHoverName().getString();
            }
            default -> {                                                              // (a command: it's written for a player, not run for bots)
                note("reward skipped for " + d.key + ": " + kind);
                return null;
            }
        }
    }

    // ------------------------------------------------------------------ 1b: where you are, who you talk to, story fighters

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer || p.tickCount % 20 != 7 || !ready()) return;
        expireTakes(p);
        if (p.tickCount % AUTO_EVERY == AUTO_EVERY / 2 + 7) autoAccept(p);
        for (PartyBotEntity b : PartyBots.bots(p)) {
            if (b.journey()) continue;
            Object pqd = peek(b);                                                    // (null: none going, or an open menu - DragonMineZ ticks its missions itself)
            if (pqd == null) continue;
            try {
                if (!changes(b, pqd, p)) continue;                                   // (nothing to write: no need to load its whole DMZ character)
                Data d = open(b, true);
                if (d == null || d.live) continue;
                tickMissions(d, p);
            } catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot's DragonMineZ missions failed to update", t); }
        }
    }

    /**
     * tickMissions without changing anything (on the cached mission data): would it change a location objective or
     * complete a mission? Story fighters are summoned here already (tickMissions does that the same way; it's once only).
     */
    private static boolean changes(PartyBotEntity b, Object pqd, ServerPlayer p) throws Exception {
        String bkey = DmzPerCharacter.botKey(b);
        List<ServerPlayer> party = null;
        for (String key : accepted(pqd)) {
            Object q = quest(key);
            if (q == null || (Boolean) m(pqd, "isQuestCompleted", key) || !typeEnabled(q)) continue;
            List<?> objs = (List<?>) m(q, "getObjectives");
            boolean parallel = (Boolean) m(q, "isParallelObjectives");
            for (int i = 0; i < objs.size(); i++) {
                Object obj = objs.get(i);
                int prog = progress(pqd, key, i), req = required(q, pqd, key, i);
                if (prog >= req) continue;
                if (!parallel && !firstOpen(pqd, key, q, i)) break;
                if ((Boolean) isLocation.invoke(null, obj)) {
                    if (party == null) party = party(p);
                    boolean there = false;
                    for (ServerPlayer s : party) if ((Boolean) locationMet.invoke(null, s, obj)) { there = true; break; }
                    if ((there ? 1 : 0) != prog) return true;
                } else if (itemObj != null && itemObj.isInstance(obj)) {              // (items: its things, yours and your other bots' - see DmzItemMissions)
                    if (Math.min(DmzItemMissions.forBot(b, (String) m(obj, "getItemId")), req) != prog) return true;
                } else if (killObj.isInstance(obj)) summonStory(b, bkey, p, key, i, obj, prog, req);
                if (!parallel) break;
            }
            if (!(Boolean) needsTurnIn.invoke(null, q)) {                            // (complete() would finish it)
                boolean all = true;
                for (int i = 0; i < objs.size() && all; i++) if (progress(pqd, key, i) < required(q, pqd, key, i)) all = false;
                if (all) return true;
            }
        }
        return false;
    }

    private static void tickMissions(Data d, ServerPlayer p) throws Exception {
        boolean changed = false;
        List<ServerPlayer> party = null;
        for (String key : accepted(d.pqd)) {
            Object q = quest(key);
            if (q == null || (Boolean) m(d.pqd, "isQuestCompleted", key) || !typeEnabled(q)) continue;
            List<?> objs = (List<?>) m(q, "getObjectives");
            boolean parallel = (Boolean) m(q, "isParallelObjectives");
            for (int i = 0; i < objs.size(); i++) {
                Object obj = objs.get(i);
                int prog = progress(d.pqd, key, i), req = required(q, d.pqd, key, i);
                if (prog >= req) continue;
                if (!parallel && !firstOpen(d.pqd, key, q, i)) break;
                if ((Boolean) isLocation.invoke(null, obj)) {                          // (like DragonMineZ: anyone in the party there counts)
                    if (party == null) party = party(p);
                    boolean there = false;
                    for (ServerPlayer s : party) if ((Boolean) locationMet.invoke(null, s, obj)) { there = true; break; }
                    int want = there ? 1 : 0;
                    if (want != prog) { m(d.pqd, "setObjectiveProgress", key, i, want); changed = true; note("location " + key + "#" + i + " -> " + want + " for " + d.key); }
                } else if (itemObj != null && itemObj.isInstance(obj)) {              // (like DragonMineZ: how many you have - counted, never taken)
                    int want = Math.min(DmzItemMissions.forBot(d.bot, (String) m(obj, "getItemId")), req);
                    if (want != prog) { m(d.pqd, "setObjectiveProgress", key, i, want); changed = true; note("items " + key + "#" + i + " -> " + want + "/" + req + " for " + d.key); }
                } else if (killObj.isInstance(obj)) summonStory(d.bot, d.key, p, key, i, obj, prog, req);
                if (!parallel) break;
            }
            if (complete(d, key, q, null)) changed = true;
        }
        if (changed) d.commit();
    }

    /** A story fighter the bot must defeat never spawns by itself: summon it near you once (unless yours already was for the same objective). */
    private static void summonStory(PartyBotEntity bot, String botKey, ServerPlayer p, String key, int index, Object obj, int prog, int req) throws Exception {
        DmzQuestHelp.Target t = DmzQuestHelp.target(p, obj);                    // (also: missionsIgnoreBiomes - ones that only live in other biomes)
        if (t == null) return;
        EntityType<?> type = t.type();
        CompoundTag keep = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag done = keep.getCompound(SUMMONED);
        String id = botKey + "|" + key + "#" + index;
        if (t.biome()) {                                                         // these again once the last group is gone (a story fighter: once)
            long now = p.server.overworld().getGameTime(), at = done.getLong(id);
            if (done.contains(id) && (now >= at && now - at < DmzQuestHelp.ARRIVAL_TICKS + DmzQuestHelp.BIOME_RESUMMON_TICKS || DmzQuestHelp.alive(p, key, index) > 0
                    || p.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL)) return;
            done.putLong(id, now);
        } else {
            if (done.getBoolean(id)) return;
            done.putBoolean(id, true);
        }
        keep.put(SUMMONED, done);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, keep);
        for (DmzQuestHelp.Open mine : DmzQuestHelp.open(p)) if (mine.key().equals(key) && mine.index() == index) return;   // (yours come for both of you)
        int n = Math.max(1, Math.min(5, req - prog));
        double hp = ((Number) m(obj, "getHealth")).doubleValue();
        DmzNews.bot(p, bot, "Mission target", Component.empty().append(bot.character().getDisplayName()).append(t.biome() ? "'s mission targets don't live around here, so they came: " : "'s mission target is coming: ")
                .append(type.getDescription()).withStyle(ChatFormatting.GOLD));
        note("summoning " + n + " x " + ForgeRegistries.ENTITY_TYPES.getKey(type) + " for " + botKey + " " + key + "#" + index + (t.biome() ? " (they only live in other biomes)" : ""));
        for (int k = 0; k < n; k++) com.xenowarriors.skill.ServerScheduler.later(100 + k * 30, () -> DmzQuestHelp.summon(p, type, key, index, hp));
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || !ready()) return;
        String npc = npcId(e.getTarget());
        if (npc == null) return;
        for (ServerPlayer r : party(p)) for (PartyBotEntity b : PartyBots.bots(r)) {
            if (b.journey() || !mayBeActive(b)) continue;
            Data d = open(b, true);
            if (d == null || d.live) continue;
            try { if (talkWith(d, npc)) d.commit(); }
            catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot's DragonMineZ mission didn't count a talk", t); }
        }
    }

    /** The DragonMineZ NPC / master id of this entity (what its "talk to" objectives name), or null. */
    static String npcId(Entity e) {
        try {
            String cls = e.getClass().getName();
            if (cls.endsWith("QuestNPCEntity") || hasMethod(e, "getNpcId")) return (String) m(e, "getNpcId");
            if (cls.endsWith("MastersEntity") || hasMethod(e, "getMasterName")) return (String) m(e, "getMasterName");
        } catch (Throwable ignored) {}
        return null;
    }

    /** The bot talked to this person: its "talk to" objectives for them count, and missions handed in to them complete. True = changed. */
    private static boolean talkWith(Data d, String npc) throws Exception {
        boolean changed = false;
        for (String key : accepted(d.pqd)) {
            Object q = quest(key);
            if (q == null || (Boolean) m(d.pqd, "isQuestCompleted", key)) continue;
            if ((Boolean) needsTurnIn.invoke(null, q)) {                             // (handed in to this person: done)
                if (complete(d, key, q, npc)) { changed = true; note("handed in " + key + " to " + npc + " for " + d.key); }
                continue;
            }
            List<?> objs = (List<?>) m(q, "getObjectives");
            boolean parallel = (Boolean) m(q, "isParallelObjectives"), counted = false;
            for (int i = 0; i < objs.size(); i++) {
                Object obj = objs.get(i);
                int prog = progress(d.pqd, key, i);
                if (prog >= required(q, d.pqd, key, i) || (!parallel && !firstOpen(d.pqd, key, q, i))) continue;
                if (talkObj.isInstance(obj) && npc.equals(m(obj, "getNpcId"))) { m(d.pqd, "setObjectiveProgress", key, i, prog + 1); counted = true; }
            }
            if (counted) { changed = true; note("talked to " + npc + " for " + d.key + " " + key); complete(d, key, q, npc); }
        }
        return changed;
    }

    /**
     * Who this mission waits on the bot (or you) to talk to right now, or null: the person it's handed in to once it's
     * ready, or the person of its current "talk to" objective. onlyLeft: only when that talk is all that's left.
     */
    static String waitingOn(Object pqd, String key, Object q, boolean onlyLeft) throws Exception {
        if ((Boolean) needsTurnIn.invoke(null, q))
            return (Boolean) turnInReady.invoke(null, pqd, key, q) ? (String) m(q, "getTurnIn") : null;
        List<?> objs = (List<?>) m(q, "getObjectives");
        boolean parallel = (Boolean) m(q, "isParallelObjectives");
        String npc = null;
        boolean otherOpen = false;
        for (int i = 0; i < objs.size(); i++) {
            if (progress(pqd, key, i) >= required(q, pqd, key, i)) continue;
            boolean current = parallel || firstOpen(pqd, key, q, i);
            if (talkObj.isInstance(objs.get(i)) && current) { if (npc == null) npc = (String) m(objs.get(i), "getNpcId"); }
            else otherOpen = true;
        }
        return onlyLeft && otherOpen ? null : npc;
    }

    // ------------------------------------------------------------------ 1c: the bot talks to them itself (DmzBotTalk walks it there)

    /** What the bot should go and talk to someone for: {mission key, person's id, "talk" / "turnin" / "take"}, the waiting take first. */
    public static List<String[]> talkJobs(PartyBotEntity b) {
        List<String[]> out = new ArrayList<>();
        if (!ready()) return out;
        Take t = TAKES.get(b.getUUID());
        if (t != null) out.add(new String[]{t.key, t.giver, "take"});
        Object pqd = peek(b);
        if (pqd == null) return out;
        try {
            for (String key : accepted(pqd)) {
                Object q = quest(key);
                if (q == null || (Boolean) m(pqd, "isQuestCompleted", key) || !typeEnabled(q)) continue;
                String npc = waitingOn(pqd, key, q, false);
                if (npc != null && !npc.isBlank()) out.add(new String[]{key, npc, (Boolean) needsTurnIn.invoke(null, q) ? "turnin" : "talk"});
            }
        } catch (Throwable t2) { XenoWarriors.LOGGER.warn("Couldn't read a bot's DragonMineZ missions for its talks", t2); }
        return out;
    }

    /** The bot reached this person and talks to them, exactly as your talking to them would count for it. True = something counted. */
    public static boolean botTalked(PartyBotEntity b, Entity person) {
        String npc = npcId(person);
        if (npc == null || !ready()) return false;
        Data d = open(b, true);
        if (d == null || d.live) return false;
        try {
            if (!talkWith(d, npc)) return false;
            d.commit();
            botTalks++;
            note(d.key + " talked to " + npc + " itself" + (person.getPersistentData().getBoolean(DmzTalkNpcs.TAG) ? " (the copy that came)" : " (the real one)"));
            DmzNews.bot(d.owner, b, "Bot talked", Component.empty().append(b.character().getDisplayName()).append(" talked to ").append(person.getDisplayName())
                    .withStyle(ChatFormatting.AQUA));
            return true;
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot's DragonMineZ talk failed", t); return false; }
    }

    /** (For the tests) talks / takes the bots did by walking up to someone themselves. */
    public static int botTalks, botTakes;

    /** A mission the bot goes to take from its giver (who's close by) when you start yours; started anyway after 30 s. */
    private record Take(UUID owner, String key, String giver, int partySize, boolean spawn, long until) {}
    private static final Map<UUID, Take> TAKES = new java.util.concurrent.ConcurrentHashMap<>();
    public static int TAKE_WAIT = 20 * 30;

    /** The bot reached the giver: it takes the mission now. */
    public static boolean botTook(PartyBotEntity b, Entity giver) {
        Take t = TAKES.remove(b.getUUID());
        return t != null && takeNow(b, t, giver);
    }

    private static boolean takeNow(PartyBotEntity b, Take t, Entity giver) {
        Data d = open(b, false);
        if (d == null || d.live) return false;
        try {
            if (!eligible(d, t.key)) { note(d.key + " can't take " + t.key + " any more (" + why + ")"); return false; }
            start(d, t.key, t.partySize, t.spawn);
            if (giver != null) botTakes++;
            DmzNews.bot(d.owner, b, "Bot mission", Component.empty().append(b.character().getDisplayName())
                    .append(Component.literal(giver != null ? " took " : " started: "))
                    .append(title(t.key)).append(giver != null ? Component.literal(" from ").append(giver.getDisplayName()) : Component.empty())
                    .withStyle(ChatFormatting.AQUA));
            note(d.key + (giver != null ? " took " + t.key + " from " + t.giver + " itself" : " started " + t.key + " (didn't get to " + t.giver + " in time)"));
            return true;
        } catch (Throwable e) { XenoWarriors.LOGGER.warn("A bot couldn't take its DragonMineZ mission", e); return false; }
    }

    /** Takes that waited too long (the bot fighting, or the giver out of reach) start anyway; the bot gone: dropped. */
    private static void expireTakes(ServerPlayer p) {
        if (TAKES.isEmpty()) return;
        long now = p.level().getGameTime();
        for (PartyBotEntity b : PartyBots.bots(p)) {
            Take t = TAKES.get(b.getUUID());
            if (t != null && (now > t.until || now < t.until - TAKE_WAIT - 20) && TAKES.remove(b.getUUID(), t)) takeNow(b, t, null);
        }
        TAKES.values().removeIf(t -> t.owner.equals(p.getUUID()) && now > t.until + 200);   // (its bot isn't here any more)
    }

    /** (For the tests) "mission<-giver" the bot is on its way to take, or null. */
    public static String takingForTest(PartyBotEntity b) { Take t = TAKES.get(b.getUUID()); return t == null ? null : t.key + "<-" + t.giver; }

    private static boolean hasMethod(Object o, String name) {
        for (Method mm : o.getClass().getMethods()) if (mm.getName().equals(name) && mm.getParameterCount() == 0) return true;
        return false;
    }

    // ------------------------------------------------------------------ 2: bots start theirs when you start yours

    @SubscribeEvent
    public static void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent e) { listen(); }

    /** Listens for DragonMineZ's "mission started" (its QuestStartEvent - fired only when a player starts one). */
    @SuppressWarnings("unchecked")
    static void listen() {
        if (listening || !ready()) return;
        try {
            Class<Event> c = (Class<Event>) Class.forName("com.dragonminez.common.events.DMZEvent$QuestStartEvent");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, c, DmzBotQuests::onQuestStart);
            listening = true;
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("Bots won't start DragonMineZ missions with you (its API changed?)", t); }
    }

    private static void onQuestStart(Event ev) {
        try {
            if (ev.isCanceled() || !(m(ev, "getPlayer") instanceof ServerPlayer p) || p instanceof FakePlayer) return;
            String key = (String) m(ev, "getQuestKey");
            String viewing = DmzPerCharacter.viewing(p);
            if (viewing != null) { note("mission " + key + " started from " + viewing + "'s Dragon Ball menu: it's the bot's"); return; }
            // DragonMineZ takes the mission right after this event: the bots follow a tick later, once it's really yours
            com.xenowarriors.skill.ServerScheduler.later(1, () -> followStart(p, key));
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("Bots couldn't follow a DragonMineZ mission start", t); }
    }

    /** How often (ticks) bots look for a mission to take on their own. */
    public static final int AUTO_EVERY = 20 * 60 * 3;
    /** (tests) missions bots took on their own. */
    public static int autoAccepted;

    /** No enemy or boss in it: no kill objectives (finding things, going places, talking to people, items, training...). */
    static boolean peaceful(Object q) throws Exception {
        for (Object obj : (List<?>) m(q, "getObjectives")) if (killObj.isInstance(obj)) return false;
        return true;
    }

    /**
     * Bots take Dragon Ball missions on their own - only ones with no enemy or boss to fight - when they have none going:
     * the first one it can start (its saga's next, then side missions). One at a time.
     */
    @SuppressWarnings("unchecked")
    public static List<String> autoAccept(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        if (!ready() || p.hasDisconnected() || DmzPerCharacter.viewing(p) != null) return out;
        try {
            List<String> keys = new ArrayList<>();
            for (Object s : ((Map<String, Object>) registry.getMethod("getAllSagas").invoke(null)).values())
                for (Object sq : (List<Object>) m(s, "getQuests"))
                    keys.add((String) pqdClass.getMethod("sagaQuestKey", String.class, int.class).invoke(null, m(s, "getId"), m(sq, "getId")));
            keys.addAll(((Map<String, Object>) registry.getMethod("getAllQuests").invoke(null)).keySet());
            int partySize = party(p).size();
            for (PartyBotEntity b : PartyBots.bots(p)) {
                if (b.journey() || TAKES.containsKey(b.getUUID())) continue;
                DmzBots.ensureRace(b);
                Data d = open(b, false);
                if (d == null || d.live) continue;
                boolean busy = false;
                for (String k : accepted(d.pqd)) if (!(Boolean) m(d.pqd, "isQuestCompleted", k)) { busy = true; break; }
                if (busy) continue;
                for (String k : keys) {
                    Object q = quest(k);
                    if (q == null || !peaceful(q) || !eligible(d, k)) continue;
                    start(d, k, partySize, false);
                    autoAccepted++;
                    out.add(d.key + "=" + k);
                    DmzNews.bot(p, b, "Bot missions", Component.empty().append(b.character().getDisplayName()).append(" took a mission on their own: ").append(title(k)).withStyle(ChatFormatting.AQUA));
                    break;
                }
            }
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("Bots couldn't take DragonMineZ missions on their own", t); }
        return out;
    }

    /** The player just started this mission: each of their bots with a DMZ character starts one too. Returns what was started. */
    public static List<String> followStart(ServerPlayer p, String key) {
        List<String> out = new ArrayList<>();
        if (!ready() || p.hasDisconnected() || DmzPerCharacter.viewing(p) != null) return out;
        try {
            Object own = DmzPerCharacter.live(p);
            if (own == null || !(Boolean) m(m(own, "getPlayerQuestData"), "isQuestAccepted", key)) return out;   // (it didn't start after all)
            int partySize = party(p).size();
            MutableComponent line = Component.empty();
            for (PartyBotEntity b : PartyBots.bots(p)) {
                if (b.journey()) continue;
                DmzBots.ensureRace(b);
                Data d = open(b, false);
                if (d == null || d.live) continue;
                String pick = pick(d, key);
                if (pick == null) continue;
                Entity giver = DmzBotTalk.giverNearby(b, quest(pick));
                if (!line.getSiblings().isEmpty()) line.append(Component.literal("  |  "));
                if (giver != null) {                                                  // (it walks over and takes it from them - DmzBotTalk)
                    String gid = npcId(giver);
                    TAKES.put(b.getUUID(), new Take(p.getUUID(), pick, gid, partySize, !pick.equals(key), p.level().getGameTime() + TAKE_WAIT));
                    out.add(d.key + "=" + pick + " (from " + gid + ")");
                    note(d.key + " goes to " + gid + " to take " + pick);
                    line.append(b.character().getDisplayName()).append(" goes to ").append(giver.getDisplayName()).append(" for: ").append(title(pick));
                    continue;
                }
                start(d, pick, partySize, !pick.equals(key));
                out.add(d.key + "=" + pick);
                line.append(b.character().getDisplayName()).append(" started: ").append(title(pick));
            }
            if (!line.getSiblings().isEmpty()) DmzNews.self(p, "Bot missions", line.withStyle(ChatFormatting.AQUA));
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("Bots couldn't start DragonMineZ missions", t); }
        return out;
    }

    /** The mission the bot starts: the same one if it can, else its next available one of the same kind; null = none (or it's already on yours). */
    @SuppressWarnings("unchecked")
    private static String pick(Data d, String key) throws Exception {
        if ((Boolean) m(d.pqd, "isQuestAccepted", key)) { note(d.key + " is already on " + key); return null; }
        if (TAKES.containsKey(d.bot.getUUID())) { note(d.key + " is still on its way to take " + TAKES.get(d.bot.getUUID()).key); return null; }
        if (eligible(d, key) || DmzBotTraining.trainFor(d, key)) return key;   // (only its level in the way: it trains up first)
        note(d.key + " can't take " + key + " (" + why + ")");
        Object q = quest(key);
        List<String> next = new ArrayList<>();
        if (q != null && (Boolean) m(q, "isSagaQuest")) {
            Object saga = saga(key);
            List<Object> sagas = new ArrayList<>();
            if (saga != null) sagas.add(saga);
            for (Object s : ((Map<String, Object>) registry.getMethod("getAllSagas").invoke(null)).values()) if (!sagas.contains(s)) sagas.add(s);
            for (Object s : sagas) for (Object sq : (List<Object>) m(s, "getQuests"))
                next.add((String) pqdClass.getMethod("sagaQuestKey", String.class, int.class).invoke(null, m(s, "getId"), m(sq, "getId")));
        } else if (q != null) {
            String giver = (String) m(q, "getQuestGiver");
            if (giver != null && !giver.isBlank()) next.addAll((List<String>) registry.getMethod("getQuestIdsByGiver", String.class).invoke(null, giver));
        }
        List<String> whyNot = new ArrayList<>();
        for (String k : next) {
            if (k.equals(key)) continue;
            if (eligible(d, k)) return k;
            if (whyNot.size() < 3 && !why.equals("done or on it")) whyNot.add(k + ": " + why);
        }
        String trained = DmzBotTraining.trainForNext(d, key, next);
        if (trained != null) return trained;
        note(d.key + " has no mission it can start with " + key + " " + whyNot);
        return null;
    }

    /** Could the bot start this one now (DragonMineZ's own checks on its data, except where it is - it's with you)? */
    static String why;

    static boolean eligible(Data d, String key) {
        why = "done or on it";
        try {
            Object q = quest(key);
            if (q == null || !typeEnabled(q)) return false;
            String status = String.valueOf(m(d.pqd, "getQuestStatus", key));
            if (status.equals("ACCEPTED") || (Boolean) m(d.pqd, "isQuestCompleted", key)) return false;
            boolean ok;
            if ((Boolean) m(q, "isSagaQuest")) {
                Object saga = saga(key);
                if (saga == null) return false;
                int idx = ((List<?>) m(saga, "getQuests")).indexOf(q);
                ok = idx >= 0 && (Boolean) avail.getMethod("isSagaQuestAvailable", q.getClass(), saga.getClass(), int.class, statsData).invoke(null, q, saga, idx, d.sd);
            } else ok = (Boolean) avail.getMethod("isAvailable", q.getClass(), statsData).invoke(null, q, d.sd);
            if (!ok) { why = "not available yet"; return false; }
            Object blocker = avail.getMethod("describeNonPositionalStartBlocker", q.getClass(), String.class, Player.class, statsData).invoke(null, q, key, d.owner, d.sd);
            if (blocker != null) why = blocker instanceof Component c ? c.getString() : String.valueOf(blocker);
            return blocker == null;
        } catch (Throwable t) { why = String.valueOf(t); return false; }
    }

    /** Starts it on the bot's data like DragonMineZ does (objectives sized for your party); its enemies are summoned unless it's yours too. */
    private static void start(Data d, String key, int partySize, boolean spawn) throws Exception {
        Object q = quest(key);
        m(d.pqd, "acceptQuest", key);
        m(q, "initializeObjectiveRequirements", d.pqd, key, partySize);
        Object diff = m(d.pqd, "getDifficulty");
        m(d.pqd, "setQuestDifficulty", key, diff);
        if (m(d.pqd, "getTrackedQuestId") == null) m(d.pqd, "setTrackedQuestId", key);
        if (spawn) {
            try { spawnKill.invoke(null, d.owner, resolve.invoke(null, key), d.pqd, partySize, diff); }
            catch (Throwable t) { XenoWarriors.LOGGER.warn("Couldn't summon the enemies of a bot's DragonMineZ mission", t); }
        }
        d.commit();
        autoStarts++;
        note(d.key + " started " + key + (spawn ? " (its own; enemies summoned)" : " (same as " + d.owner.getGameProfile().getName() + "'s)"));
    }

    // ------------------------------------------------------------------ for the tests

    /** "status objective=progress/required" of the bot's mission (from its saved or open data). */
    public static String status(PartyBotEntity b, String key, int objective) {
        Data d = open(b, false);
        if (d == null) return "no data";
        try {
            Object q = quest(key);
            return "status=" + m(d.pqd, "getQuestStatus", key) + " obj" + objective + "=" + progress(d.pqd, key, objective) + "/" + (q == null ? "?" : required(q, d.pqd, key, objective))
                    + " TP=" + m(m(d.sd, "getResources"), "getTrainingPoints") + (d.live ? " (menu open)" : "");
        } catch (Throwable t) { return "? " + t; }
    }

    /** The bot's first objective of this mission not done yet (-1 = none / no data). */
    public static int openObjective(PartyBotEntity b, String key) {
        Data d = open(b, false);
        Object q = quest(key);
        if (d == null || q == null) return -1;
        try {
            int n = ((List<?>) m(q, "getObjectives")).size();
            for (int i = 0; i < n; i++) if (progress(d.pqd, key, i) < required(q, d.pqd, key, i)) return i;
        } catch (Throwable ignored) {}
        return -1;
    }

    /**
     * Item ids your DragonMineZ missions still want - yours and your bot teammates' (an item objective not met yet).
     * For the party's mission bag (MissionBag): drops of these go there instead of into anyone's inventory.
     */
    public static Set<String> wantedItems(ServerPlayer p) {
        Set<String> out = new HashSet<>();
        if (!ready() || itemObj == null || p == null || p instanceof FakePlayer) return out;
        try { addWanted(out, DmzItemMissions.questData(p)); } catch (Throwable t) { /* no missions */ }
        for (PartyBotEntity b : PartyBots.bots(p)) {
            try { addWanted(out, peek(b)); } catch (Throwable t) { /* skip it */ }
        }
        return out;
    }

    private static void addWanted(Set<String> out, Object pqd) throws Exception {
        if (pqd == null) return;
        for (String key : accepted(pqd)) {
            Object q = quest(key);
            if (q == null || (Boolean) m(pqd, "isQuestCompleted", key)) continue;
            List<?> objs = (List<?>) m(q, "getObjectives");
            for (int i = 0; i < objs.size(); i++) {
                Object obj = objs.get(i);
                if (!itemObj.isInstance(obj) || progress(pqd, key, i) >= required(q, pqd, key, i)) continue;
                Object id = m(obj, "getItemId");
                if (id != null) out.add(id.toString());
            }
        }
    }

    /** A bot's accepted missions (its saved data). */
    public static List<String> active(PartyBotEntity b) {
        Data d = open(b, false);
        try { return d == null ? List.of() : accepted(d.pqd); } catch (Throwable t) { return List.of(); }
    }

    /** UI test: the bot takes this mission (as from its menu; nothing summoned). */
    public static boolean startForTest(PartyBotEntity b, String key) {
        Data d = open(b, false);
        if (d == null || quest(key) == null) return false;
        try { start(d, key, 1, false); return true; } catch (Throwable t) { XenoWarriors.LOGGER.warn("XW-TEST bot mission start", t); return false; }
    }

    /** Wipes a bot's mission data (test start). */
    public static void resetForTest(PartyBotEntity b) {
        Data d = open(b, false);
        if (d == null) return;
        try { m(d.pqd, "resetAll"); d.commit(); } catch (Throwable t) { XenoWarriors.LOGGER.warn("reset", t); }
    }

    public static boolean listeningForTest() { return listening; }

    /** UI test: could the bot start this mission now? "true" or why not. */
    public static String eligibleForTest(PartyBotEntity b, String key) {
        Data d = open(b, false);
        if (d == null) return "no data";
        return eligible(d, key) ? "true" : why;
    }
}
