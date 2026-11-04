package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * DragonMineZ's quests and the dimension you're in: the check itself is waived by mixin/compat/DragonMineZQuestDimensionMixin
 * (every dimension test of its quest system goes through QuestLocationHelper.isInDimension). This keeps a count for
 * the logs and has the UI test's helpers (reflection, so nothing here needs DragonMineZ).
 */
public final class DmzQuests {
    private DmzQuests() {}

    private static int waived;

    /** (from the mixin) a "you must be in dimension X" check was answered yes. */
    public static void noteDimensionWaived(Level level, String dimension) {
        if (waived++ < 3 || waived % 500 == 0)
            XenoWarriors.LOGGER.info("XW dmz quests: dimension {} counted as met in {} ({} so far)", dimension, level.dimension().location(), waived);
    }

    public static int waivedCount() { return waived; }

    private static Object data(ServerPlayer p) throws Exception {
        var cap = (net.minecraftforge.common.capabilities.Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
        return p.getCapability(cap).resolve().orElse(null);
    }

    /** UI test: the quest ids that have a "be in this dimension" objective, with that dimension. */
    public static java.util.List<String[]> dimensionQuests() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        try {
            Class<?> type = Class.forName("com.dragonminez.common.quest.QuestObjective$ObjectiveType");
            Object dim = null;
            for (Object c : type.getEnumConstants()) if (c.toString().equals("DIMENSION")) dim = c;
            Class<?> reg = Class.forName("com.dragonminez.common.quest.QuestRegistry");
            @SuppressWarnings("unchecked")
            var ids = (java.util.List<String>) reg.getMethod("getQuestIdsByObjectiveType", type).invoke(null, dim);
            for (String id : ids) {
                Object q = reg.getMethod("getQuest", String.class).invoke(null, id);
                if (q == null) continue;
                var objs = (java.util.List<?>) q.getClass().getMethod("getObjectives").invoke(q);
                for (int i = 0; i < objs.size(); i++) {
                    Object o = objs.get(i);
                    if (o.getClass().getSimpleName().equals("DimensionObjective"))
                        out.add(new String[]{id, String.valueOf(i), String.valueOf(o.getClass().getMethod("getDimensionId").invoke(o)), String.valueOf(objs.size())});
                }
            }
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("XW-TEST dmz quests lookup failed", t); }
        return out;
    }

    /** UI test: take a quest (as if accepted from its giver). */
    public static void accept(ServerPlayer p, String quest) {
        try {
            Object d = data(p);
            Object q = d.getClass().getMethod("getPlayerQuestData").invoke(d);
            q.getClass().getMethod("acceptQuest", String.class).invoke(q, quest);
            Object def = Class.forName("com.dragonminez.common.quest.QuestRegistry").getMethod("getQuest", String.class).invoke(null, quest);
            def.getClass().getMethod("initializeObjectiveRequirements", q.getClass(), String.class, int.class).invoke(def, q, quest, 1);
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("XW-TEST dmz quest accept failed", t); }
    }

    /** UI test: "accepted/completed progress-of-objective". */
    public static String progress(ServerPlayer p, String quest, int objective) {
        try {
            Object d = data(p);
            Object q = d.getClass().getMethod("getPlayerQuestData").invoke(d);
            return "accepted=" + q.getClass().getMethod("isQuestAccepted", String.class).invoke(q, quest)
                    + " completed=" + q.getClass().getMethod("isQuestCompleted", String.class).invoke(q, quest)
                    + " status=" + q.getClass().getMethod("getQuestStatus", String.class).invoke(q, quest)
                    + " objective" + objective + "=" + q.getClass().getMethod("getObjectiveProgress", String.class, int.class).invoke(q, quest, objective);
        } catch (Throwable t) { return "? " + t; }
    }

    /** UI test: would DragonMineZ let you start this quest here (its start requirements, dimension included)? */
    public static String startable(ServerPlayer p, String quest) {
        try {
            Object d = data(p);
            Object def = Class.forName("com.dragonminez.common.quest.QuestRegistry").getMethod("getQuest", String.class).invoke(null, quest);
            Class<?> chk = Class.forName("com.dragonminez.common.quest.QuestAvailabilityChecker");
            Object ok = chk.getMethod("areStartRequirementsMet", def.getClass(), String.class, net.minecraft.world.entity.player.Player.class, d.getClass())
                    .invoke(null, def, quest, p, d);
            return String.valueOf(ok);
        } catch (Throwable t) { return "? " + t; }
    }

    /** UI test: QuestLocationHelper.isInDimension straight (should be true anywhere now). */
    public static String inDimension(ServerPlayer p, String dim) {
        try {
            return String.valueOf(Class.forName("com.dragonminez.common.quest.QuestLocationHelper").getMethod("isInDimension", Level.class, String.class)
                    .invoke(null, p.level(), dim));
        } catch (Throwable t) { return "? " + t; }
    }
}
