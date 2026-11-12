package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.*;

/**
 * With DragonMineZ: bot teammates learn and level up their own transformations.
 * <ul>
 *   <li><b>Learning forms.</b> About every {@link #GAP} ticks (out of a fight) a bot looks at its race's form skills
 *       (Super Saiyan, the Human / Namekian / Frost Demon... forms - DragonMineZ's race config) and, with enough spare TP
 *       (what's above its next mission's level, see {@link DmzBotTraining#reserve}), buys the next level of the
 *       cheapest one exactly as DragonMineZ's skill menu does for a player (UpdateSkillC2S: same TP price
 *       {@code computeTpCost}, same max level {@code refreshRuntimeMaxLevel}). A higher form skill level unlocks the
 *       next form (SSJ -> SSJ2 -> SSJ3...), which {@link DmzBotPower} then uses in fights. Forms DragonMineZ sells only
 *       through a master stay master-only, as for players.</li>
 *   <li><b>Mastering forms.</b> While a bot is transformed it gains mastery like a player: DragonMineZ's passive mastery
 *       every 5 s and its mastery per hit dealt / taken for that form; saved to the bot's character about once a minute.</li>
 * </ul>
 * Only the bot's own saved DragonMineZ character changes (never yours, never while its Dragon Ball menu is open).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzBotForms {
    private DmzBotForms() {}

    /** Ticks between two form levels a bot buys by itself (90 s). */
    public static final int GAP = 20 * 90;
    /** Share of its spare TP a form level may cost (forms come first: most of it). */
    public static final float BUDGET_SHARE = 0.8F;
    private static final String LAST = "xw_dmz_form_up_at";

    private static Boolean loaded;
    private static boolean broken;
    private static Method computeTpCost, refreshMax, raceCharacter, hasFormSkill, buyFromMaster, raceFormSkills;
    private static Method passive, perDealt, perTaken;

    /** (Tests) what happened, newest last; form levels bought and mastery saved so far. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    public static int learned;
    public static double masterySaved;

    private static void note(String s) {
        if (LOG.size() > 60) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz bot forms: {}", s);
    }

    private static Object m(Object o, String name, Object... a) throws Exception { return DmzBotQuests.m(o, name, a); }

    static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (computeTpCost != null) return true;
        try {
            Class<?> sd = Class.forName("com.dragonminez.common.stats.StatsData");
            Class<?> up = Class.forName("com.dragonminez.common.network.C2S.UpdateSkillC2S");
            computeTpCost = up.getDeclaredMethod("computeTpCost", sd, String.class, int.class);
            refreshMax = up.getDeclaredMethod("refreshRuntimeMaxLevel", sd, String.class, Class.forName("com.dragonminez.common.stats.skills.Skill"));
            computeTpCost.setAccessible(true);
            refreshMax.setAccessible(true);
            raceCharacter = Class.forName("com.dragonminez.common.config.ConfigManager").getMethod("getRaceCharacter", String.class);
            Class<?> rc = raceCharacter.getReturnType();
            hasFormSkill = rc.getMethod("hasFormSkill", String.class);
            buyFromMaster = rc.getMethod("isFormSkillBuyFromMaster", String.class);
            raceFormSkills = rc.getMethod("getFormSkills");
            Class<?> fd = Class.forName("com.dragonminez.common.config.FormConfig$FormData");
            passive = fd.getMethod("getPassiveMasteryEveryFiveSeconds");
            perDealt = fd.getMethod("getMasteryPerHitDealt");
            perTaken = fd.getMethod("getMasteryPerHitReceived");
            return true;
        } catch (Throwable t) {
            broken = true; computeTpCost = null;
            XenoWarriors.LOGGER.warn("Bots won't learn DragonMineZ forms by themselves (its API changed?)", t);
            return false;
        }
    }

    private static float tp(Object sd) throws Exception { return ((Number) m(m(sd, "getResources"), "getTrainingPoints")).floatValue(); }

    private static void save(DmzBotQuests.Data d, String... parts) {
        Collections.addAll(d.touched, parts);
        d.commit();
        if (d.tag != null) DmzPerCharacter.store(d.owner, d.key, d.tag.copy());
    }

    static String pretty(String skill) {
        String s = skill.replace('_', ' ');
        if (s.toLowerCase(Locale.ROOT).startsWith("super")) s = "Super " + s.substring(5);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ learning / levelling form skills

    /** Buys the next level of the cheapest form skill it can afford. Returns what it did (for the tests). */
    public static String learn(PartyBotEntity b, boolean force) {
        if (!ready()) return "off";
        long now = b.level().getGameTime(), last = b.getPersistentData().getLong(LAST);
        if (!force && last > 0 && now >= last && now - last < GAP) return "too soon";
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null || d.live) return "no data";
        try {
            String race = String.valueOf(m(m(d.sd, "getCharacter"), "getRaceName"));
            Object rc = raceCharacter.invoke(null, race);
            if (rc == null) return "no race config (" + race + ")";
            float[] sp = DmzBotTechniques.spare(d);
            float budget = sp[2] * BUDGET_SHARE;
            Object skills = m(d.sd, "getSkills");
            String best = null; int bestCost = Integer.MAX_VALUE, bestLv = 0;
            StringBuilder why = new StringBuilder();
            for (Object o : (Collection<?>) raceFormSkills.invoke(rc)) {
                String s = String.valueOf(o);
                if (!(Boolean) hasFormSkill.invoke(rc, s)) continue;
                int lv = ((Number) m(skills, "getSkillLevel", s)).intValue();
                if (lv <= 0 && (Boolean) buyFromMaster.invoke(rc, s)) { why.append(s).append(" from a master; "); continue; }
                Object sk = m(skills, "getSkill", s);
                if (sk != null) {
                    refreshMax.invoke(null, d.sd, s, sk);
                    if ((Boolean) m(sk, "isMaxLevel") && lv > 0) { why.append(s).append(" max; "); continue; }
                }
                int c = ((Number) computeTpCost.invoke(null, d.sd, s, lv)).intValue();
                if (c < 0 || c > budget) { why.append(s).append(' ').append(c).append(" TP; "); continue; }
                if (c < bestCost) { best = s; bestCost = c; bestLv = lv; }
            }
            if (best == null) return "nothing to learn (" + why + "budget " + Math.round(budget) + ")";
            float before = tp(d.sd);
            if (before < bestCost) return "not enough TP";
            m(m(d.sd, "getResources"), "removeTrainingPoints", (float) bestCost);
            Object sk = m(skills, "getSkill", best);
            if (sk == null || bestLv <= 0) m(skills, "setSkillLevel", best, bestLv + 1);
            else m(sk, "addLevel", 1);
            try { m(d.sd, "updateTransformationSkillLimits", race); } catch (Throwable ignored) {}
            save(d, "Skills", "Resources");
            b.getPersistentData().putLong(LAST, now);
            learned++;
            int nl = ((Number) m(skills, "getSkillLevel", best)).intValue();
            note(d.key + " " + (bestLv <= 0 ? "learned " : "raised ") + best + " to level " + nl + " for " + bestCost + " TP (TP " + before + " -> " + tp(d.sd) + ", kept " + sp[1] + ")");
            Component name = Component.translatableWithFallback("skill.dragonminez." + best, pretty(best)).withStyle(ChatFormatting.GOLD);
            DmzNews.bot(d.owner, b, bestLv <= 0 ? "New transformation" : "Transformation trained", Component.empty().append(b.character().getDisplayName())
                    .append(bestLv <= 0 ? " learned the " : " trained ").append(name).append(bestLv <= 0 ? " transformation" : " to level " + nl)
                    .append(" (" + String.format(Locale.US, "%,d TP", bestCost) + ")").withStyle(ChatFormatting.AQUA));
            return (bestLv <= 0 ? "learned " : "raised ") + best + " to " + nl + " for " + bestCost;
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("A bot couldn't learn a DragonMineZ form", t);
            return "error " + t;
        }
    }

    // ------------------------------------------------------------------ mastery

    /** bot -> "group|form" -> mastery earned and not saved yet */
    private static final Map<UUID, Map<String, Double>> PENDING = new HashMap<>();

    private static double num(Method m, Object o) {
        try { Object v = m.invoke(o); return v instanceof Number n ? n.doubleValue() : 0; } catch (Throwable t) { return 0; }
    }

    private static void gain(PartyBotEntity b, Method rate, double times) {
        String[] f = DmzBotPower.formOf(b);
        Object fd = f == null ? null : DmzBotPower.activeFormData(b);
        if (fd == null) return;
        double amt = num(rate, fd) * times;
        if (amt > 0) PENDING.computeIfAbsent(b.getUUID(), u -> new HashMap<>()).merge(f[0] + "|" + f[1], amt, Double::sum);
    }

    /** Writes the mastery it earned into its saved character. */
    public static String flush(PartyBotEntity b) {
        Map<String, Double> pend = PENDING.get(b.getUUID());
        if (pend == null || pend.isEmpty() || !ready()) return "nothing";
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null || d.live) return "no data";
        try {
            Object ch = m(d.sd, "getCharacter");
            StringBuilder sb = new StringBuilder();
            double total = 0;
            for (var e : pend.entrySet()) {
                String[] gf = e.getKey().split("\\|", 2);
                m(ch, "gainMastery", gf[0], gf[1], (double) e.getValue());
                total += e.getValue();
                sb.append(gf[1]).append(" +").append(String.format(Locale.ROOT, "%.2f", e.getValue())).append(" (now ")
                        .append(String.format(Locale.ROOT, "%.2f", ((Number) m(m(ch, "getFormMasteries"), "getMastery", gf[0], gf[1])).doubleValue())).append(") ");
            }
            pend.clear();
            save(d, "Character");
            masterySaved += total;
            note(d.key + " mastery " + sb.toString().trim());
            return sb.toString().trim();
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("A bot couldn't save its DragonMineZ form mastery", t);
            pend.clear();
            return "error " + t;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent e) {
        if (e.isCanceled() || !ready()) return;
        if (e.getSource().getEntity() instanceof PartyBotEntity b) gain(b, perDealt, 1);
        if (e.getEntity() instanceof PartyBotEntity b) gain(b, perTaken, 1);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer || !ready()) return;
        if (p.tickCount % 100 == 37) for (PartyBotEntity b : PartyBots.bots(p)) gain(b, passive, 1);     // passive mastery every 5 s
        if (p.tickCount % 200 != 77 || DmzPerCharacter.viewing(p) != null) return;     // (a bot's menu open: its data is yours to click)
        for (PartyBotEntity b : PartyBots.bots(p)) {
            if (b.journey()) continue;
            if (b.getTarget() == null || p.tickCount % 1200 == 77) flush(b);               // (mid-fight: once a minute)
            if (b.getTarget() != null) continue;                                           // (not mid-fight)
            try { learn(b, false); } catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot couldn't learn a DragonMineZ form", t); }
        }
    }

    /** (Tests) mastery waiting to be saved. */
    public static String pendingForTest(PartyBotEntity b) { return String.valueOf(PENDING.get(b.getUUID())); }

    /** (Tests) gives the bot's saved character this race, these TP, and none of the race's form skills. */
    public static String setupForTest(PartyBotEntity b, String race, float tp) {
        if (!ready()) return "off";
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null || d.live) return "no data";
        try {
            m(m(d.sd, "getCharacter"), "setRace", race);
            Object rc = raceCharacter.invoke(null, race);
            Object skills = m(d.sd, "getSkills");
            StringBuilder sb = new StringBuilder();
            for (Object o : (Collection<?>) raceFormSkills.invoke(rc)) {
                sb.append(o).append(' ');
                if ((Boolean) m(skills, "hasSkill", String.valueOf(o))) m(skills, "setSkillLevel", String.valueOf(o), 0);
            }
            m(m(d.sd, "getResources"), "setTrainingPoints", tp);
            b.getPersistentData().remove(LAST);
            save(d, "Character", "Skills", "Resources");
            return "form skills: " + sb.toString().trim();
        } catch (Throwable t) { return "error " + t; }
    }

    /** (Tests) a form skill's level on the bot's saved character. */
    public static int levelForTest(PartyBotEntity b, String skill) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null) return -1;
        try { return ((Number) m(m(d.sd, "getSkills"), "getSkillLevel", skill)).intValue(); } catch (Throwable t) { return -1; }
    }

    /** (Tests) its saved mastery of a form. */
    public static double masteryForTest(PartyBotEntity b, String group, String form) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null) return -1;
        try { return ((Number) m(m(m(d.sd, "getCharacter"), "getFormMasteries"), "getMastery", group, form)).doubleValue(); } catch (Throwable t) { return -1; }
    }
}
