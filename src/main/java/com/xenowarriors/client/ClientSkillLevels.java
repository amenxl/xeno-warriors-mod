package com.xenowarriors.client;

import com.xenowarriors.network.SkillLevelsPacket;
import com.xenowarriors.skill.SkillLevels;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The client's copy of the current character's ability levels (skill/SkillLevels). */
public final class ClientSkillLevels {
    private static final Map<String, int[]> LV = ClientReset.track(new HashMap<>());

    private ClientSkillLevels() {}

    public static void set(List<SkillLevelsPacket.Entry> l) {
        LV.clear();
        for (SkillLevelsPacket.Entry e : l) LV.put(e.id(), new int[]{e.tier(), e.level(), e.uses()});
    }

    /** {tier, level, uses} (tier 1 level 1 if never used). */
    public static int[] of(String id) { int[] v = LV.get(id); return v == null ? new int[]{1, 1, 0} : v.clone(); }
    public static int tier(String id) { return of(id)[0]; }
    public static int level(String id) { return of(id)[1]; }
    public static boolean canUpgrade(String id) { int[] v = of(id); return v[1] >= SkillLevels.MAX_LEVEL && v[0] < SkillLevels.MAX_TIER; }

    /** 0..1 toward the next level. */
    public static float progress(String id) {
        int[] v = of(id);
        return v[1] >= SkillLevels.MAX_LEVEL ? 1F : Math.min(1F, v[2] / (float) SkillLevels.needed(v[0], v[1]));
    }

    public static int cost(String id, int base) { int[] v = of(id); return SkillLevels.cost(base, v[0], v[1]); }
    public static int cooldown(String id, int base) { int[] v = of(id); return SkillLevels.cooldown(base, v[0], v[1]); }

    private static String f(float x) { return String.format(java.util.Locale.ROOT, "%.2f", x); }

    /** Tooltip lines: tier, level, progress, what it changes, and the upgrade. */
    public static String describe(String id) {
        int[] v = of(id);
        StringBuilder s = new StringBuilder("Tier " + v[0] + "/" + SkillLevels.MAX_TIER + "  •  Lv " + v[1] + "/" + SkillLevels.MAX_LEVEL);
        if (v[1] < SkillLevels.MAX_LEVEL) s.append("  (").append(v[2]).append("/").append(SkillLevels.needed(v[0], v[1])).append(" uses to next)");
        s.append("\nDamage x").append(f(SkillLevels.damageMult(v[0])))
                .append("   Karma x").append(f(SkillLevels.costMult(v[0], v[1])))
                .append("   Cooldown x").append(f(SkillLevels.cooldownMult(v[0], v[1])));
        if (canUpgrade(id)) s.append("\nUpgrade ready: Tier ").append(v[0] + 1).append(" = x").append(f(SkillLevels.damageMult(v[0] + 1)))
                .append(" damage, but x").append(f(SkillLevels.costMult(v[0] + 1, 1))).append(" Karma and x")
                .append(f(SkillLevels.cooldownMult(v[0] + 1, 1))).append(" cooldown until you level it again.");
        else if (v[0] >= SkillLevels.MAX_TIER && v[1] >= SkillLevels.MAX_LEVEL) s.append("\nMastered!");
        return s.toString();
    }
}
