package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.*;

/**
 * With DragonMineZ: bot teammates get new ki techniques of their own, with their own TP.
 * <ul>
 *   <li><b>Their own ki attacks.</b> About once a minute (out of a fight) each bot looks at its spare TP - what it has above what its next
 *       mission's level still costs ({@link DmzBotTraining#reserve}: training always comes first). With enough of it,
 *       and at most one every {@link #CREATE_GAP} ticks, it makes a new ki attack in DragonMineZ's own technique
 *       creator, the way its CreateTechniqueC2S does for a player: same parameter rules (normalizeStatsForType), same
 *       derived cast time / cooldown and TP price (calculateDerivedValues / getTpCost), same id (generateId). The kind
 *       follows its DragonMineZ class (a Spiritualist makes beams and giant balls, a Martial Artist discs and lasers...),
 *       the colour its character's, the name its own ("Kaeru's Ember Cannon"), and its power is the most that half its
 *       spare TP buys. At most {@link #MAX_CREATED} each.</li>
 *   <li><b>Techniques from masters.</b> A DragonMineZ master (Goku, Roshi, Piccolo, King Kai, Vegeta...) within
 *       {@link #MASTER_REACH} blocks, when things are quiet: the bot walks over ({@link DmzBotMasters}), talks to them
 *       and buys one of the ki or strike techniques they teach (DragonMineZ's skill offerings) exactly as the master's
 *       "Purchase" button does for a player (UpdateSkillC2S PURCHASE): same TP price (computeTpCost), same race rule,
 *       the master's alignment rule, not one it has already - again only with spare TP.</li>
 * </ul>
 * Only the bot's own saved DragonMineZ character changes (never yours, never while its Dragon Ball menu is open), and
 * its equipped slots are left alone - bots use every technique they know in a fight ({@link DmzBotKi}), so nothing has
 * to be bound for them. All by reflection; nothing happens without DragonMineZ.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzBotTechniques {
    private DmzBotTechniques() {}

    /** Ticks between two ki attacks a bot makes (8 minutes); techniques it makes, at most; share of its spare TP one may cost. */
    public static final int CREATE_GAP = 20 * 60 * 8, MAX_CREATED = 6;
    public static final float BUDGET_SHARE = 0.5F;
    public static final double MASTER_REACH = 40;
    private static final String LAST = "xw_dmz_ki_created_at", TRIED = "xw_dmz_ki_tried_at";
    private static final int RETRY = 20 * 60;

    private static Boolean loaded;
    private static boolean broken;
    private static Class<?> kiData, strikeData, kiType, utility, secondary;
    private static Method generateId, normalize, minDmg, maxDmg, defSize, defSpeed, defPen, maxSize, maxSpeed, maxPen, predefinedId, skillsConfig;
    private static Method computeTpCost, raceAllowed, refreshMax, unlockIfPresent, alignmentBlocker;
    private static java.lang.reflect.Constructor<?> purchase;
    private static Object purchaseAction;
    private static Map<?, ?> registry, strikeRegistry;

    /** (For the tests) what happened, newest last. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    /** (For the tests) ki attacks made and techniques learned so far. */
    public static int created, learned;

    private static void note(String s) {
        if (LOG.size() > 80) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz bot techniques: {}", s);
    }

    private static Object m(Object o, String name, Object... a) throws Exception { return DmzBotQuests.m(o, name, a); }

    static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (purchase != null) return true;
        try {
            Class<?> sd = Class.forName("com.dragonminez.common.stats.StatsData");
            kiData = Class.forName("com.dragonminez.common.stats.techniques.KiAttackData");
            strikeData = Class.forName("com.dragonminez.common.stats.techniques.StrikeAttackData");
            kiType = Class.forName("com.dragonminez.common.stats.techniques.KiAttackData$KiType");
            utility = Class.forName("com.dragonminez.common.stats.techniques.KiAttackData$Utility");
            secondary = Class.forName("com.dragonminez.common.stats.techniques.KiAttackData$SecondaryEffectType");
            generateId = Class.forName("com.dragonminez.common.stats.techniques.TechniqueData").getMethod("generateId", String.class, String.class);
            normalize = kiData.getMethod("normalizeStatsForType", kiType, float.class, float.class, float.class, int.class);
            minDmg = kiData.getMethod("getMinDamageForType", kiType);
            maxDmg = kiData.getMethod("getMaxDamageForType", kiType);
            defSize = kiData.getMethod("getDefaultSizeForType", kiType);
            defSpeed = kiData.getMethod("getDefaultSpeedForType", kiType);
            defPen = kiData.getMethod("getDefaultArmorPenForType", kiType);
            maxSize = kiData.getMethod("getMaxSizeForType", kiType);
            maxSpeed = kiData.getMethod("getMaxSpeedForType", kiType);
            maxPen = kiData.getMethod("getMaxArmorPenForType", kiType);
            Class<?> pre = Class.forName("com.dragonminez.common.stats.techniques.PredefinedTechniques");
            predefinedId = pre.getMethod("isPredefinedTechniqueId", String.class);
            registry = (Map<?, ?>) pre.getField("REGISTRY").get(null);
            strikeRegistry = (Map<?, ?>) pre.getField("STRIKE_REGISTRY").get(null);
            skillsConfig = Class.forName("com.dragonminez.common.config.ConfigManager").getMethod("getSkillsConfig");
            Class<?> up = Class.forName("com.dragonminez.common.network.C2S.UpdateSkillC2S");
            Class<?> act = Class.forName("com.dragonminez.common.network.C2S.UpdateSkillC2S$SkillAction");
            computeTpCost = up.getDeclaredMethod("computeTpCost", sd, String.class, int.class);
            raceAllowed = up.getDeclaredMethod("isSkillAllowedForPlayerRace", sd, String.class);
            refreshMax = up.getDeclaredMethod("refreshRuntimeMaxLevel", sd, String.class, Class.forName("com.dragonminez.common.stats.skills.Skill"));
            unlockIfPresent = up.getDeclaredMethod("unlockTechniqueIfPresent", sd, String.class);
            for (Method x : new Method[]{computeTpCost, raceAllowed, refreshMax, unlockIfPresent}) x.setAccessible(true);
            purchaseAction = act.getField("PURCHASE").get(null);
            try {
                alignmentBlocker = Class.forName("com.dragonminez.common.alignment.NpcDispositionService").getDeclaredMethod("masterAlignmentBlocker", sd, String.class);
                alignmentBlocker.setAccessible(true);
            } catch (Throwable t) { alignmentBlocker = null; XenoWarriors.LOGGER.info("XW dmz bot techniques: no master alignment rule in this DragonMineZ"); }
            purchase = up.getConstructor(act, String.class, int.class);
            return true;
        } catch (Throwable t) {
            broken = true; purchase = null;
            XenoWarriors.LOGGER.warn("Bots can't make or learn DragonMineZ techniques (its API changed?)", t);
            return false;
        }
    }

    // ------------------------------------------------------------------ spare TP

    private static float tp(Object sd) throws Exception { return ((Number) m(m(sd, "getResources"), "getTrainingPoints")).floatValue(); }

    /** {TP, reserve, spare}: its TP, what it keeps for its next mission's level, and what's above that. */
    static float[] spare(DmzBotQuests.Data d) throws Exception {
        float have = tp(d.sd), keep = DmzBotTraining.reserve(d);
        return new float[]{have, keep, keep >= Float.MAX_VALUE / 2 ? 0 : Math.max(0, have - keep)};
    }

    private static String botName(PartyBotEntity b) { return b.character().getDisplayName().getString(); }

    private static Map<?, ?> unlocked(Object sd) throws Exception { return (Map<?, ?>) m(m(sd, "getTechniques"), "getUnlockedTechniques"); }

    private static void save(DmzBotQuests.Data d, String... parts) {
        Collections.addAll(d.touched, parts);
        d.commit();
        if (d.tag != null) DmzPerCharacter.store(d.owner, d.key, d.tag.copy());    // (a new tag: its fighting copy re-reads it at once)
    }

    // ------------------------------------------------------------------ its own ki attacks

    /** The kinds each DragonMineZ class makes, in order (its first new attack is the first kind, and so on). */
    static final Map<String, String[]> KINDS = Map.of(
            "warrior",       new String[]{"MEDIUM_BALL", "BEAM", "EXPLOSION", "BARRAGE"},
            "berserker",     new String[]{"EXPLOSION", "GIANT_BALL", "BARRAGE", "MEDIUM_BALL"},
            "martialartist", new String[]{"DISK", "LASER", "BARRAGE", "SMALL_BALL"},
            "spiritualist",  new String[]{"BEAM", "GIANT_BALL", "WAVE", "BARRAGE"},
            "cleric",        new String[]{"LASER", "WAVE", "MEDIUM_BALL", "SMALL_BALL"},
            "tank",          new String[]{"WAVE", "EXPLOSION", "MEDIUM_BALL", "BARRAGE"},
            "paladin",       new String[]{"BEAM", "DISK", "MEDIUM_BALL", "EXPLOSION"});
    static final String[] DEFAULT_KINDS = {"BEAM", "MEDIUM_BALL", "DISK", "BARRAGE"};

    private static final Map<String, String[]> NOUNS = Map.of(
            "SMALL_BALL", new String[]{"Shot", "Bullet", "Spark"}, "MEDIUM_BALL", new String[]{"Sphere", "Bomb", "Orb"},
            "GIANT_BALL", new String[]{"Star", "Nova", "Sun"}, "WAVE", new String[]{"Wave", "Flare", "Surge"},
            "LASER", new String[]{"Ray", "Lance", "Needle"}, "BEAM", new String[]{"Cannon", "Beam", "Buster"},
            "DISK", new String[]{"Disc", "Cutter", "Saw"}, "EXPLOSION", new String[]{"Burst", "Eruption", "Blast"},
            "BARRAGE", new String[]{"Barrage", "Volley", "Storm"});
    private static final String[] WORDS = {"Ember", "Blazing", "Crimson", "Storm", "Thunder", "Spirit", "Phantom", "Solar", "Lunar", "Dragon",
            "Comet", "Astral", "Rising", "Fury", "Shining", "Fox", "Xeno", "Wild", "Nova", "Final"};

    private static String cls(Object sd) { return DmzBotTraining.cls(sd).toLowerCase(Locale.ROOT).replace("_", "").replace(" ", ""); }

    /** The ki attacks this bot made itself (its name as their author; DragonMineZ's own techniques don't count). */
    private static List<Object> made(Object sd, String author) throws Exception {
        List<Object> out = new ArrayList<>();
        for (Object t : unlocked(sd).values())
            if (kiData.isInstance(t) && author.equals(m(t, "getAuthor")) && !(Boolean) predefinedId.invoke(null, m(t, "getId"))) out.add(t);
        return out;
    }

    private static Object kind(String name) { return enumOf(kiType, name); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumOf(Class<?> c, String n) { return Enum.valueOf((Class) c, n); }

    /**
     * A new ki attack built like DragonMineZ's CreateTechniqueC2S builds a player's (everything but the id and the
     * owner's checks), with its derived cast time, cooldown and TP price worked out.
     */
    private static Object build(String name, String author, Object type, float damage, int color) throws Exception {
        return build(name, author, type, damage, -1, -1, -1, color);
    }

    /** The same, with its size, speed and armor penetration (negative = DragonMineZ's default for the kind). */
    private static Object build(String name, String author, Object type, float damage, float size, float speed, int pen, int color) throws Exception {
        Object t = kiData.getConstructor().newInstance();
        String n = name == null || name.trim().isEmpty() ? "New Skill" : name.trim();
        if (n.length() > 64) n = n.substring(0, 64);
        m(t, "setName", n);
        m(t, "setAuthor", author);
        m(t, "setId", generateId.invoke(null, author, n));
        m(t, "setKiType", type);
        m(t, "setUtility", enumOf(utility, "DAMAGE"));                              // (attacks only: no healing)
        float[] s = (float[]) normalize.invoke(null, type, damage, size >= 0 ? size : ((Number) defSize.invoke(null, type)).floatValue(),
                speed >= 0 ? speed : ((Number) defSpeed.invoke(null, type)).floatValue(), pen >= 0 ? pen : ((Number) defPen.invoke(null, type)).intValue());
        m(t, "setDamageMultiplier", s[0]);
        m(t, "setSize", s[1]);
        m(t, "setSpeed", s[2]);
        m(t, "setArmorPenetration", Math.round(s[3]));
        m(t, "setCastTime", 0);                                                     // (both derived below, as DragonMineZ does)
        m(t, "setCooldown", 0);
        int c = color & 0xFFFFFF;
        m(t, "setColorInterior", mix(c, 0xFFFFFF, 0.6F));
        m(t, "setColorExterior", c);
        m(t, "setColorOutline", mix(c, 0x000000, 0.45F));
        m(t, "setAnimation", "");
        m(t, "setSecondaryEffectType", enumOf(secondary, "NONE"));
        m(t, "setAffectedStat", (Object) null);
        m(t, "calculateDerivedValues");
        return t;
    }

    private static int mix(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f), g = Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f),
                bl = Math.round((a & 255) * (1 - f) + (b & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }

    private static int price(Object t) throws Exception { return Math.max(0, Math.round(((Number) m(t, "getTpCost")).floatValue())); }

    /**
     * Maybe makes a new ki attack for this bot (force: no waiting since the last one). Returns what happened, for the
     * logs and tests.
     */
    public static String create(PartyBotEntity b, boolean force) {
        if (!ready()) return "off";
        long now = b.level().getGameTime();
        long last = b.getPersistentData().getLong(LAST);
        if (!force && last > 0 && now >= last && now - last < CREATE_GAP) return "too soon";
        long tried = b.getPersistentData().getLong(TRIED);
        if (!force && tried > 0 && now >= tried && now - tried < RETRY) return "looked a moment ago";
        if (!force) b.getPersistentData().putLong(TRIED, now);                          // (its TP and reserve are looked at once a minute at most)
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null || d.live) return "no data";
        try {
            String author = botName(b);
            List<Object> mine = made(d.sd, author);
            if (mine.size() >= MAX_CREATED) return "has " + mine.size() + " already";
            float[] sp = spare(d);
            float budget = sp[2] * BUDGET_SHARE;
            String c = cls(d.sd);
            String[] kinds = KINDS.getOrDefault(c, DEFAULT_KINDS);
            Set<String> have = new HashSet<>();
            for (Object t : mine) have.add(String.valueOf(m(t, "getKiType")));
            String kindName = kinds[mine.size() % kinds.length];
            for (String k : kinds) if (!have.contains(k)) { kindName = k; break; }
            Object type = kind(kindName);
            int color = b.character().getColor();
            float lo = ((Number) minDmg.invoke(null, type)).floatValue(), hi = ((Number) maxDmg.invoke(null, type)).floatValue();
            int cheapest = price(build("x", author, type, lo, color));
            if (cheapest > budget) {
                String out = "can't afford a " + kindName + " yet: cheapest " + cheapest + " TP, budget " + Math.round(budget) + " (TP " + sp[0] + ", keeps " + (sp[1] >= Float.MAX_VALUE / 2 ? "all" : String.valueOf(sp[1])) + ")";
                if (force) note(d.key + ": " + out);
                return out;
            }
            float dmg = lo;                                                          // (the strongest one half its spare TP buys)
            if (price(build("x", author, type, hi, color)) <= budget) dmg = hi;
            else for (int i = 0; i < 24; i++) {
                float mid = (dmg + hi) / 2;
                if (price(build("x", author, type, mid, color)) <= budget) dmg = mid; else hi = mid;
            }
            // then, with what's left of the budget: armor penetration, speed, size - each to DragonMineZ's top for the kind if it can pay for it
            float size = -1, speed = -1;
            int pen = -1;
            String extras = "";
            if (dmg >= hi - 1.0E-4F) {
                int mp = ((Number) maxPen.invoke(null, type)).intValue();
                if (mp > ((Number) defPen.invoke(null, type)).intValue() && price(build("x", author, type, dmg, size, speed, mp, color)) <= budget) { pen = mp; extras += " +pen"; }
                float ms = ((Number) maxSpeed.invoke(null, type)).floatValue();
                if (ms > ((Number) defSpeed.invoke(null, type)).floatValue() && price(build("x", author, type, dmg, size, ms, pen, color)) <= budget) { speed = ms; extras += " +speed"; }
                float mz = ((Number) maxSize.invoke(null, type)).floatValue();
                if (mz > ((Number) defSize.invoke(null, type)).floatValue() && price(build("x", author, type, dmg, mz, speed, pen, color)) <= budget) { size = mz; extras += " +size"; }
            }
            Map<?, ?> all = unlocked(d.sd);
            Object tech = null;
            Random r = new Random(b.getUUID().getLeastSignificantBits() ^ mine.size() * 31L ^ now);
            String[] nouns = NOUNS.getOrDefault(kindName, new String[]{"Blast"});
            for (int tries = 0; tries < 40 && tech == null; tries++) {
                String name = author + "'s " + WORDS[r.nextInt(WORDS.length)] + " " + nouns[r.nextInt(nouns.length)];
                Object t = build(name, author, type, dmg, size, speed, pen, color);
                if (!all.containsKey(m(t, "getId"))) tech = t;
            }
            if (tech == null) return "no free name";
            // DragonMineZ's own checks and payment (CreateTechniqueC2S): enough TP for its price, then unlock
            int cost = price(tech);
            float before = tp(d.sd);
            if (before < cost) return "not enough TP";
            if (cost > 0) m(m(d.sd, "getResources"), "removeTrainingPoints", (float) cost);
            m(m(d.sd, "getTechniques"), "unlockTechnique", tech);
            String id = (String) m(tech, "getId");
            if (!unlocked(d.sd).containsKey(id)) { note(d.key + ": DragonMineZ wouldn't take " + id + " (technique list full?)"); return "not added"; }
            save(d, "Techniques", "Resources");
            b.getPersistentData().putLong(LAST, now);
            created++;
            String name = (String) m(tech, "getName");
            String what = d.key + " (" + c + ") made " + name + " [" + id + "] " + kindName + " dmg x" + String.format(Locale.ROOT, "%.2f", ((Number) m(tech, "getDamageMultiplier")).floatValue())
                    + " size " + m(tech, "getSize") + " speed " + m(tech, "getSpeed") + " pen " + m(tech, "getArmorPenetration") + extras
                    + " cast " + m(tech, "getCastTime") + " cd " + m(tech, "getCooldown") + " for " + cost + " TP (TP " + before + " -> " + tp(d.sd) + ", kept " + sp[1] + ", budget " + Math.round(budget) + ")";
            note(what);
            d.owner.displayClientMessage(Component.empty().append(b.character().getDisplayName()).append(" created a new ki attack: ")
                    .append(Component.literal(name).withStyle(ChatFormatting.GOLD)).append(" (" + pretty(kindName) + ", " + String.format(Locale.US, "%,d TP", cost) + ")")
                    .withStyle(ChatFormatting.AQUA), false);
            return "made " + id + " for " + cost;
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("A bot couldn't make a DragonMineZ ki attack", t);
            return "error " + t;
        }
    }

    static String pretty(String kind) {
        String s = kind.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer || p.tickCount % 200 != 141 || !ready()) return;
        if (DmzPerCharacter.viewing(p) != null) return;                                // (a bot's menu is open: its points are yours to click)
        for (PartyBotEntity b : PartyBots.bots(p)) {
            if (b.journey() || b.getTarget() != null) continue;                          // (not mid-fight)
            try { create(b, false); } catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot couldn't make a DragonMineZ ki attack", t); }
        }
    }

    // ------------------------------------------------------------------ masters

    /** The master's name ("goku", "roshi", "kingkai"...) if this is one of DragonMineZ's masters, else null. */
    static String masterName(Entity e) {
        try {
            var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
            if (id == null || !id.getNamespace().equals("dragonminez")) return null;
            for (Method mm : e.getClass().getMethods()) if (mm.getName().equals("getMasterName") && mm.getParameterCount() == 0) {
                Object n = mm.invoke(e);
                return n instanceof String s && !s.isBlank() ? s : null;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** What this master teaches that's a ki or strike attack (DragonMineZ's skill offerings, in its order). */
    @SuppressWarnings("unchecked")
    static List<String> teaches(String master) {
        List<String> out = new ArrayList<>();
        if (!ready() || master == null) return out;
        try {
            Object cfg = skillsConfig.invoke(null);
            Map<String, List<String>> offers = (Map<String, List<String>>) m(cfg, "getSkillOfferings");
            List<String> list = offers.get(master.toLowerCase(Locale.ROOT));
            if (list == null) return out;
            List<String> ki = (List<String>) m(cfg, "getKiSkills"), strike = (List<String>) m(cfg, "getStrikeSkills");
            for (String s : list) {
                if (s == null) continue;
                if (strike.contains(s) && strikeRegistry.containsKey(s)) out.add(s);
                else if (ki.contains(s) && registry.containsKey(s) && DmzBotKi.usable(registry.get(s))) out.add(s);   // (attacks only: no fake moon, no healing)
            }
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("Couldn't read a DragonMineZ master's techniques", t); }
        return out;
    }

    static boolean isStrike(String skill) { return strikeRegistry != null && strikeRegistry.containsKey(skill); }

    /** DragonMineZ's price for learning this skill (computeTpCost at level 0); -1 = can't be bought. */
    static int cost(Object sd, String skill) throws Exception { return ((Number) computeTpCost.invoke(null, sd, skill, 0)).intValue(); }

    /** Why the master won't teach this bot (DragonMineZ's alignment rule for masters), or null. */
    static String blocked(Object sd, String master) {
        if (alignmentBlocker == null) return null;
        try {
            Object c = alignmentBlocker.invoke(null, sd, master);
            return c == null ? null : ((Component) c).getString();
        } catch (Throwable t) { return null; }
    }

    /** What's stopping it learning this skill from the master (null = it can, for that many TP). */
    static String cantLearn(DmzBotQuests.Data d, String master, String skill, float spare) throws Exception {
        String why = blocked(d.sd, master);
        if (why != null) return "master won't teach it: " + why;
        if (!(Boolean) raceAllowed.invoke(null, d.sd, skill)) return "not for its race";
        if ((Boolean) m(m(d.sd, "getSkills"), "hasSkill", skill) || unlocked(d.sd).containsKey(skill)) return "knows it";
        int c = cost(d.sd, skill);
        if (c < 0) return "not for sale";
        if (c > spare) return "needs " + c + " TP (spare " + Math.round(spare) + ")";
        return null;
    }

    /**
     * The technique the bot would learn from this master now, or null: the first (in the master's order) it can afford
     * with spare TP, preferring the kind (ki or strike) it knows fewer of.
     */
    static String lesson(DmzBotQuests.Data d, String master, StringBuilder why) throws Exception {
        float spare = spare(d)[2];
        int ki = 0, strike = 0;
        for (Object t : unlocked(d.sd).values()) if (strikeData.isInstance(t)) strike++; else ki++;
        String first = null, other = null;
        for (String s : teaches(master)) {
            String no = cantLearn(d, master, s, spare);
            if (why != null) why.append(s).append(no == null ? " ok" : " (" + no + ")").append("; ");
            if (no != null) continue;
            boolean wanted = isStrike(s) ? strike < ki : ki <= strike;
            if (wanted && first == null) first = s;
            if (other == null) other = s;
        }
        return first != null ? first : other;
    }

    /**
     * The bot has talked to the master: it learns this technique exactly as DragonMineZ's "Purchase" button teaches a
     * player (UpdateSkillC2S PURCHASE: price, race, not already known; then the skill at level 1 and its technique).
     */
    static boolean learn(PartyBotEntity b, Entity masterEntity, String master, String skill) {
        if (!ready()) return false;
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null || d.live) return false;
        try {
            float[] sp = spare(d);
            String no = cantLearn(d, master, skill, sp[2]);
            if (no != null) { note(d.key + " can't learn " + skill + " from " + master + ": " + no); return false; }
            int c = cost(d.sd, skill);
            float before = tp(d.sd);
            if (before < c) return false;
            m(m(d.sd, "getResources"), "removeTrainingPoints", (float) c);
            Object skills = m(d.sd, "getSkills");
            m(skills, "setSkillLevel", skill, 1);
            Object sk = m(skills, "getSkill", skill);
            if (sk != null) refreshMax.invoke(null, d.sd, skill, sk);
            unlockIfPresent.invoke(purchase.newInstance(purchaseAction, skill, 0), d.sd, skill);
            boolean got = unlocked(d.sd).containsKey(skill);
            save(d, "Skills", "Techniques", "Resources");
            learned++;
            String tname = skill;
            try { Object t = unlocked(d.sd).get(skill); if (t != null) tname = (String) m(t, "getName"); } catch (Throwable ignored) {}
            Component shown = tname == null || tname.isBlank() || tname.equals(skill) ? Component.literal(DmzBotPower.pretty(skill))
                    : tname.startsWith("technique.") ? Component.translatableWithFallback(tname, DmzBotPower.pretty(skill)) : Component.literal(tname);   // (DragonMineZ's own: a lang key)
            note(d.key + " learned " + skill + " (" + (isStrike(skill) ? "STRIKE" : "KI") + ", technique " + (got ? "added" : "MISSING") + ") from " + master
                    + " for " + c + " TP (TP " + before + " -> " + tp(d.sd) + ", kept " + sp[1] + ")");
            Component who = masterEntity != null ? masterEntity.getDisplayName() : Component.literal(DmzBotPower.pretty(master));
            d.owner.displayClientMessage(Component.empty().append(b.character().getDisplayName()).append(" learned ")
                    .append(shown.copy().withStyle(ChatFormatting.GOLD)).append(" from ").append(who)
                    .append(" (" + String.format(Locale.US, "%,d TP", c) + ")").withStyle(ChatFormatting.AQUA), false);
            return true;
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("A bot couldn't learn a DragonMineZ technique", t);
            return false;
        }
    }

    // ------------------------------------------------------------------ for the tests

    /** {TP, reserve, spare} of the bot's saved character, or null. */
    public static float[] spareForTest(PartyBotEntity b) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null) return null;
        try { return spare(d); } catch (Throwable t) { return null; }
    }

    /** DragonMineZ's price of this skill for the bot (-1 = none). */
    public static int costForTest(PartyBotEntity b, String skill) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null || !ready()) return -1;
        try { return cost(d.sd, skill); } catch (Throwable t) { return -1; }
    }

    /** The ids of every technique the bot's saved character knows (and in brackets the ones equipped in its slots). */
    public static String knownForTest(PartyBotEntity b) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null) return "-";
        try {
            String[] slots = (String[]) m(m(d.sd, "getTechniques"), "getEquippedSlots");
            List<String> eq = new ArrayList<>();
            if (slots != null) for (String s : slots) if (s != null && !s.isEmpty()) eq.add(s);
            return unlocked(d.sd).keySet() + " equipped " + eq;
        } catch (Throwable t) { return "?" + t; }
    }

    /** Test setup: the bot's techniques and skills (null = keep) and its TP; its equipped slots emptied; its creation timer reset. */
    public static boolean setupForTest(PartyBotEntity b, float tp, boolean wipeTechniques) { return setupForTest(b, tp, wipeTechniques, -1); }

    /** The same, also setting its alignment (0-100; -1 = keep). Its own creation timer is set to now (only forced ones for 8 minutes). */
    public static boolean setupForTest(PartyBotEntity b, float tp, boolean wipeTechniques, int alignment) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null || d.live || !ready()) return false;
        try {
            Object tech = m(d.sd, "getTechniques");
            if (wipeTechniques) {
                Map<?, ?> all = unlocked(d.sd);
                for (Object id : new ArrayList<>(all.keySet())) m(tech, "removeTechnique", id);
                Object skills = m(d.sd, "getSkills");
                Object cfg = skillsConfig.invoke(null);
                for (String s : new String[]{"getKiSkills", "getStrikeSkills"})
                    for (Object k : (List<?>) m(cfg, s)) if ((Boolean) m(skills, "hasSkill", k)) m(skills, "removeSkill", k);
            }
            String[] slots = (String[]) m(tech, "getEquippedSlots");
            if (slots != null) Arrays.fill(slots, "");
            m(m(d.sd, "getResources"), "setTrainingPoints", tp);
            if (alignment >= 0) m(m(d.sd, "getResources"), "setAlignment", alignment);
            save(d, "Techniques", "Skills", "Resources");
            b.getPersistentData().putLong(LAST, b.level().getGameTime());
            return true;
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("setupForTest", t); return false; }
    }

    /** (Tests) it may make one by itself at its next look (its creation timer cleared). */
    public static void allowCreateForTest(PartyBotEntity b) { b.getPersistentData().remove(LAST); b.getPersistentData().remove(TRIED); }

    /** (Tests) a bot character's name, as its techniques' author. */
    public static String authorForTest(PartyBotEntity b) { return botName(b); }

    /** (Tests) is this one of DragonMineZ's masters, and its name. */
    public static String masterForTest(Entity e) { return masterName(e); }

    /** (Tests) what this master teaches that bots would learn. */
    public static List<String> teachesForTest(String master) { return teaches(master); }

    /** (Tests) what the bot would learn from this master now, and why not the others. */
    public static String lessonForTest(PartyBotEntity b, String master) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null) return "no data";
        try { StringBuilder sb = new StringBuilder(); String l = lesson(d, master, sb); return l + " | " + sb; } catch (Throwable t) { return "?" + t; }
    }

}
