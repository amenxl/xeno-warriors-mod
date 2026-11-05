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
 *       what each started. Bots never start one at any other time.</li>
 *   <li>Location objectives count while you (or a party member) are there; "talk to" objectives when you talk to that
 *       person; missions that end at a person are handed in for the bot once everything else is done; story
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
    private static Class<?> pqdClass, registry, service, killObj, talkObj, locHelper, avail, statsData;
    private static Method unlocked, spawnKill, isLocation, locationMet, resolve, turnInReady, needsTurnIn;

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

    private static Object m(Object o, String name, Object... args) throws Exception {
        for (Method mm : o.getClass().getMethods())
            if (mm.getName().equals(name) && mm.getParameterCount() == args.length) {
                try { return mm.invoke(o, args); } catch (IllegalArgumentException wrongOverload) { /* try the next one */ }
            }
        throw new NoSuchMethodException(o.getClass().getSimpleName() + "." + name);
    }

    private static Object quest(String key) {
        try { return registry.getMethod("getQuest", String.class).invoke(null, key); } catch (Throwable t) { return null; }
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

    private static boolean typeEnabled(Object q) {
        try {
            Object cfg = Class.forName("com.dragonminez.common.config.ConfigManager").getMethod("getServerConfig").invoke(null);
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
            List<ServerPlayer> l = (List<ServerPlayer>) Class.forName("com.dragonminez.common.quest.PartyManager").getMethod("getAllPartyMembers", ServerPlayer.class).invoke(null, p);
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

    @SuppressWarnings("unchecked")
    private static List<String> accepted(Object pqd) throws Exception {
        return new ArrayList<>((Set<String>) m(pqd, "getAcceptedQuestIds"));
    }

    private static int progress(Object pqd, String key, int i) throws Exception { return (Integer) m(pqd, "getObjectiveProgress", key, i); }
    private static int required(Object q, Object pqd, String key, int i) throws Exception { return (Integer) m(q, "getObjectiveRequired", pqd, key, i); }

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
                if (b.journey()) continue;
                Data d = open(b, true);
                if (d == null || (d.live && dmzCountedLive)) continue;
                try { creditKill(d, dead, owners, src, b == killerBot); }
                catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot's DragonMineZ mission didn't count a kill", t); }
            }
        }
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
            if (counted) { changed = true; complete(d, key, q); }
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

    /** All done? Then the bot completes it (one that ends at a person is handed in for it) and gets the rewards. */
    private static boolean complete(Data d, String key, Object q) throws Exception {
        if ((Boolean) m(d.pqd, "isQuestCompleted", key)) return false;
        List<?> objs = (List<?>) m(q, "getObjectives");
        if ((Boolean) needsTurnIn.invoke(null, q)) {
            if (!(Boolean) turnInReady.invoke(null, d.pqd, key, q)) return false;
            for (int i = 0; i < objs.size(); i++) if (talkObj.isInstance(objs.get(i))) m(d.pqd, "setObjectiveProgress", key, i, required(q, d.pqd, key, i));
        } else {
            for (int i = 0; i < objs.size(); i++) if (progress(d.pqd, key, i) < required(q, d.pqd, key, i)) return false;
        }
        m(d.pqd, "completeQuest", key);
        if (key.equals(m(d.pqd, "getTrackedQuestId"))) m(d.pqd, "setTrackedQuestId", (Object) null);
        String got = claim(d, key, q);
        completions++;
        note("mission complete for " + d.key + ": " + key + " rewards " + got);
        d.owner.displayClientMessage(Component.empty().append(d.bot.character().getDisplayName()).append(" completed: ").append(title(key))
                .append(got.isEmpty() ? "" : " (" + got + ")").withStyle(ChatFormatting.GOLD), false);
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
        for (PartyBotEntity b : PartyBots.bots(p)) {
            if (b.journey()) continue;
            Data d = open(b, true);
            if (d == null || d.live) continue;                                       // (an open menu: DragonMineZ ticks its missions itself)
            try { tickMissions(d, p); } catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot's DragonMineZ missions failed to update", t); }
        }
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
                } else if (killObj.isInstance(obj)) summonStory(d, p, key, i, obj, prog, req);
                if (!parallel) break;
            }
            if (complete(d, key, q)) changed = true;
        }
        if (changed) d.commit();
    }

    /** A story fighter the bot must defeat never spawns by itself: summon it near you once (unless yours already was for the same objective). */
    private static void summonStory(Data d, ServerPlayer p, String key, int index, Object obj, int prog, int req) throws Exception {
        if ((Boolean) m(obj, "isTag") || !"NATURAL".equals(String.valueOf(m(obj, "getSpawnMode")))) return;
        EntityType<?> type = (EntityType<?>) m(obj, "resolveEntityType");
        if (type == null || !DmzQuestHelp.storyOnly(p.serverLevel(), type)) return;
        CompoundTag keep = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag done = keep.getCompound(SUMMONED);
        String id = d.key + "|" + key + "#" + index;
        if (done.getBoolean(id)) return;
        done.putBoolean(id, true);
        keep.put(SUMMONED, done);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, keep);
        for (DmzQuestHelp.Open mine : DmzQuestHelp.open(p)) if (mine.key().equals(key) && mine.index() == index) return;   // (yours come for both of you)
        int n = Math.max(1, Math.min(5, req - prog));
        double hp = ((Number) m(obj, "getHealth")).doubleValue();
        p.displayClientMessage(Component.empty().append(d.bot.character().getDisplayName()).append("'s mission target is coming: ").append(type.getDescription())
                .withStyle(ChatFormatting.GOLD), false);
        note("summoning " + n + " x " + ForgeRegistries.ENTITY_TYPES.getKey(type) + " for " + d.key + " " + key + "#" + index);
        for (int k = 0; k < n; k++) com.xenowarriors.skill.ServerScheduler.later(100 + k * 30, () -> DmzQuestHelp.summon(p, type, key, index, hp));
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || !ready()) return;
        String npc = null;
        try {
            String cls = e.getTarget().getClass().getName();
            if (cls.endsWith("QuestNPCEntity") || hasMethod(e.getTarget(), "getNpcId")) npc = (String) m(e.getTarget(), "getNpcId");
            else if (cls.endsWith("MastersEntity")) npc = (String) m(e.getTarget(), "getMasterName");
        } catch (Throwable ignored) {}
        if (npc == null) return;
        for (ServerPlayer r : party(p)) for (PartyBotEntity b : PartyBots.bots(r)) {
            if (b.journey()) continue;
            Data d = open(b, true);
            if (d == null || d.live) continue;
            try {
                boolean changed = false;
                for (String key : accepted(d.pqd)) {
                    Object q = quest(key);
                    if (q == null || (Boolean) m(d.pqd, "isQuestCompleted", key) || (Boolean) needsTurnIn.invoke(null, q)) continue;
                    List<?> objs = (List<?>) m(q, "getObjectives");
                    boolean parallel = (Boolean) m(q, "isParallelObjectives"), counted = false;
                    for (int i = 0; i < objs.size(); i++) {
                        Object obj = objs.get(i);
                        int prog = progress(d.pqd, key, i);
                        if (prog >= required(q, d.pqd, key, i) || (!parallel && !firstOpen(d.pqd, key, q, i))) continue;
                        if (talkObj.isInstance(obj) && npc.equals(m(obj, "getNpcId"))) { m(d.pqd, "setObjectiveProgress", key, i, prog + 1); counted = true; }
                    }
                    if (counted) { changed = true; note("talked to " + npc + " for " + d.key + " " + key); complete(d, key, q); }
                }
                if (changed) d.commit();
            } catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot's DragonMineZ mission didn't count a talk", t); }
        }
    }

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
                start(d, pick, partySize, !pick.equals(key));
                out.add(d.key + "=" + pick);
                if (!line.getSiblings().isEmpty()) line.append(Component.literal("  |  "));
                line.append(b.character().getDisplayName()).append(" started: ").append(title(pick));
            }
            if (!line.getSiblings().isEmpty()) p.displayClientMessage(line.withStyle(ChatFormatting.AQUA), false);
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("Bots couldn't start DragonMineZ missions", t); }
        return out;
    }

    /** The mission the bot starts: the same one if it can, else its next available one of the same kind; null = none (or it's already on yours). */
    @SuppressWarnings("unchecked")
    private static String pick(Data d, String key) throws Exception {
        if ((Boolean) m(d.pqd, "isQuestAccepted", key)) { note(d.key + " is already on " + key); return null; }
        if (eligible(d, key)) return key;
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
        for (String k : next) if (!k.equals(key) && eligible(d, k)) return k;
        note(d.key + " has no mission it can start with " + key);
        return null;
    }

    /** Could the bot start this one now (DragonMineZ's own checks on its data, except where it is - it's with you)? */
    private static boolean eligible(Data d, String key) {
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
            if (!ok) return false;
            Object blocker = avail.getMethod("describeNonPositionalStartBlocker", q.getClass(), String.class, Player.class, statsData).invoke(null, q, key, d.owner, d.sd);
            return blocker == null;
        } catch (Throwable t) { return false; }
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

    /** A bot's accepted missions (its saved data). */
    public static List<String> active(PartyBotEntity b) {
        Data d = open(b, false);
        try { return d == null ? List.of() : accepted(d.pqd); } catch (Throwable t) { return List.of(); }
    }

    /** Wipes a bot's mission data (test start). */
    public static void resetForTest(PartyBotEntity b) {
        Data d = open(b, false);
        if (d == null) return;
        try { m(d.pqd, "resetAll"); d.commit(); } catch (Throwable t) { XenoWarriors.LOGGER.warn("reset", t); }
    }

    public static boolean listeningForTest() { return listening; }
}
