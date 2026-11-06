package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.XwConfig;

import java.lang.reflect.Method;
import java.util.*;

/**
 * DragonMineZ's movie missions without a trip to Kami's Lookout. The Movies saga opens with "Kami's Lookout Warning"
 * (objectives: be at the structure dragonminez:kamilookout, then talk to Dende), and the movie side mission "Dende's
 * Lookout Vigil" needs you standing at the Lookout to even start it (a STRUCTURE start requirement) and again as its
 * first objective. Those Lookout checks are answered "yes" for movie missions only (mixins DragonMineZMovieLookoutMixin
 * and DragonMineZMovieConditionMixin); level, saga order and every other requirement stay. Dende himself comes to you
 * for the talk (DmzTalkNpcs), and your bots talk to him themselves (DmzBotTalk). Off with moviesWithoutLookout=false.
 * <p>
 * A movie mission: one of the movies_saga, one whose category says "movie", or a side mission that needs a Movies saga
 * step first. All by reflection; nothing happens without DragonMineZ.
 */
public final class DmzMovieMissions {
    private DmzMovieMissions() {}

    public static final String LOOKOUT = "dragonminez:kamilookout";
    public static final String SAGA = "movies_saga";

    /** (For the tests) how many Lookout checks were waived: start requirements, objectives. */
    public static int waivedConditions, waivedObjectives;
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    private static void note(String s) {
        if (LOG.size() > 40) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz movies: {}", s);
    }

    private static boolean on() { return XwConfig.get(XwConfig.DMZ_MOVIES_ANYWHERE); }

    private static Class<?> registry;
    private static Method getQuest, getClientQuest, allQuests, allSagas, clientQuests, clientSagas;
    private static boolean broken;

    private static boolean init() {
        if (registry != null) return true;
        if (broken) return false;
        try {
            Class<?> r = Class.forName("com.dragonminez.common.quest.QuestRegistry");
            getQuest = r.getMethod("getQuest", String.class);
            getClientQuest = r.getMethod("getClientQuest", String.class);
            allQuests = r.getMethod("getAllQuests");
            allSagas = r.getMethod("getAllSagas");
            clientQuests = r.getMethod("getClientQuests");
            clientSagas = r.getMethod("getClientSagas");
            registry = r;
            return true;
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("DragonMineZ movie missions: its quest registry changed, the Lookout stays required", t);
            return false;
        }
    }

    private static Object call(Object o, String name) throws Exception { return DmzBotQuests.m(o, name); }

    private static Object questOf(String key) {
        if (key == null || !init()) return null;
        try {
            Object q = getQuest.invoke(null, key);
            return q != null ? q : getClientQuest.invoke(null, key);
        } catch (Throwable t) { return null; }
    }

    // ------------------------------------------------------------------ what's a movie mission

    private static final Map<Object, Boolean> MOVIE = Collections.synchronizedMap(new WeakHashMap<>());

    /** Is this (quest, its key) one of the movie missions? */
    public static boolean isMovie(Object q, String key) {
        if (q == null) return key != null && key.startsWith(SAGA);
        Boolean b = MOVIE.get(q);
        if (b == null) {
            b = movie(q, key);
            MOVIE.put(q, b);
        }
        return b;
    }

    public static boolean isMovie(String key) { return isMovie(questOf(key), key); }

    private static boolean movie(Object q, String key) {
        try {
            if (key != null && key.startsWith(SAGA)) return true;
            String cat = (String) call(q, "getCategory");
            if (cat != null && cat.toLowerCase(Locale.ROOT).contains("movie")) return true;
            if (init()) for (Method m : new Method[]{allSagas, clientSagas}) {
                Map<?, ?> sagas = (Map<?, ?>) m.invoke(null);
                if (sagas == null) continue;
                Object movies = sagas.get(SAGA);
                if (movies != null && ((List<?>) call(movies, "getQuests")).contains(q)) return true;
            }
            return mentionsMovies(call(q, "getPrerequisites"), 0) || mentionsMovies(call(q, "getStartRequirements"), 0);
        } catch (Throwable t) { return false; }
    }

    /** Does this condition group need a Movies saga step? */
    private static boolean mentionsMovies(Object prereqs, int depth) throws Exception {
        if (prereqs == null || depth > 6) return false;
        for (Object c : (List<?>) call(prereqs, "conditions")) {
            if ((Boolean) call(c, "isNestedGroup")) { if (mentionsMovies(call(c, "getNested"), depth + 1)) return true; continue; }
            if ("SAGA_QUEST".equals(String.valueOf(call(c, "getType"))) && SAGA.equals(call(c, "getSagaId"))) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ the Lookout objectives of movie missions

    private static Set<Object> lookoutObjectives = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int builtFrom;

    /** The "be at Kami's Lookout" objectives of the movie missions (the very objects DragonMineZ ticks). Rebuilt when its missions reload. */
    private static synchronized Set<Object> lookoutObjectives() {
        if (!init()) return Set.of();
        try {
            List<Object[]> all = new ArrayList<>();
            int h = 17;
            for (Method qm : new Method[]{allQuests, clientQuests}) {
                Map<?, ?> m = (Map<?, ?>) qm.invoke(null);
                if (m == null) continue;
                h = h * 31 + System.identityHashCode(m) + m.size();
                for (Map.Entry<?, ?> e : m.entrySet()) all.add(new Object[]{e.getKey(), e.getValue()});
            }
            for (Method sm : new Method[]{allSagas, clientSagas}) {
                Map<?, ?> m = (Map<?, ?>) sm.invoke(null);
                if (m == null) continue;
                h = h * 31 + System.identityHashCode(m) + m.size();
                Object movies = m.get(SAGA);
                if (movies != null) for (Object q : (List<?>) call(movies, "getQuests")) {
                    h = h * 31 + System.identityHashCode(q);
                    all.add(new Object[]{SAGA + ":" + call(q, "getId"), q});
                }
            }
            if (h == builtFrom && !lookoutObjectives.isEmpty()) return lookoutObjectives;
            Set<Object> out = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Object[] e : all) {
                Object q = e[1];
                if (q == null || !isMovie(q, String.valueOf(e[0]))) continue;
                for (Object o : (List<?>) call(q, "getObjectives")) if (isLookout(o)) out.add(o);
            }
            lookoutObjectives = out;
            builtFrom = h;
            note("movie missions' Lookout objectives: " + out.size());
            return out;
        } catch (Throwable t) { return lookoutObjectives; }
    }

    private static boolean isLookout(Object objective) {
        try {
            return objective != null && objective.getClass().getSimpleName().equals("StructureObjective") && LOOKOUT.equals(call(objective, "getStructureId"));
        } catch (Throwable t) { return false; }
    }

    // ------------------------------------------------------------------ from the mixins

    /** QuestLocationHelper.isLocationConditionMet: a movie mission's "be at Kami's Lookout" objective is met anywhere. */
    public static boolean waiveObjective(Object objective) {
        if (!on() || !isLookout(objective) || !lookoutObjectives().contains(objective)) return false;
        if (waivedObjectives++ < 3 || waivedObjectives % 500 == 0) note("Lookout objective counted as reached (" + waivedObjectives + " so far)");
        return true;
    }

    /** QuestAvailabilityChecker.evaluateCondition: a movie mission's "be at Kami's Lookout" start requirement is met anywhere. */
    public static boolean waiveCondition(Object condition, Object context) {
        if (!on() || condition == null || context == null) return false;
        try {
            if (!"STRUCTURE".equals(String.valueOf(call(condition, "getType"))) || !LOOKOUT.equals(call(condition, "getStructureId"))) return false;
            String key = (String) call(context, "questKey");
            if (key == null || !isMovie(key)) return false;
            if (waivedConditions++ < 3 || waivedConditions % 500 == 0) note("Lookout requirement of " + key + " counted as met (" + waivedConditions + " so far)");
            return true;
        } catch (Throwable t) { return false; }
    }

    // ------------------------------------------------------------------ for the tests

    /** {key, objective index} of every movie mission's Lookout objective, and of every movie mission with a Lookout start requirement (index -1). */
    public static List<String[]> lookoutMissions() {
        List<String[]> out = new ArrayList<>();
        if (!init()) return out;
        try {
            Map<String, Object> all = new TreeMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) allQuests.invoke(null)).entrySet()) all.put(String.valueOf(e.getKey()), e.getValue());
            Object movies = ((Map<?, ?>) allSagas.invoke(null)).get(SAGA);
            if (movies != null) for (Object q : (List<?>) call(movies, "getQuests"))
                all.putIfAbsent((String) Class.forName("com.dragonminez.common.quest.PlayerQuestData").getMethod("sagaQuestKey", String.class, int.class)
                        .invoke(null, SAGA, call(q, "getId")), q);
            for (Map.Entry<String, Object> e : all.entrySet()) {
                if (!isMovie(e.getValue(), e.getKey())) continue;
                List<?> objs = (List<?>) call(e.getValue(), "getObjectives");
                for (int i = 0; i < objs.size(); i++) if (isLookout(objs.get(i))) out.add(new String[]{e.getKey(), String.valueOf(i), String.valueOf(objs.size())});
                if (needsLookout(call(e.getValue(), "getStartRequirements"))) out.add(new String[]{e.getKey(), "-1", String.valueOf(objs.size())});
            }
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("XW-TEST dmz movies lookup failed", t); }
        return out;
    }

    private static boolean needsLookout(Object prereqs) throws Exception {
        if (prereqs == null) return false;
        for (Object c : (List<?>) call(prereqs, "conditions")) {
            if ((Boolean) call(c, "isNestedGroup")) { if (needsLookout(call(c, "getNested"))) return true; continue; }
            if ("STRUCTURE".equals(String.valueOf(call(c, "getType"))) && LOOKOUT.equals(call(c, "getStructureId"))) return true;
        }
        return false;
    }
}
