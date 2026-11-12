package com.xenowarriors.you;

import com.xenowarriors.character.Race;
import com.xenowarriors.ka.Element;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillPool;
import com.xenowarriors.skill.sp.DojoSkills;
import com.xenowarriors.skill.sp.LexySkills;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A You character's skill pool: the starter kit, their element's Bolt and Wave, and their race's skills, with the
 * ultimate from their race. Built once per race + element and cached.
 */
public final class YouSkills {
    private YouSkills() {}

    private static final Map<String, List<SkillPool.Entry>> POOLS = new ConcurrentHashMap<>();
    private static final Map<String, Skill> ULTS = new ConcurrentHashMap<>();

    private static String key(YouSlot s) { return s.race + ":" + s.element + ":" + s.form + ":" + s.path + ":" + s.devil + ":" + s.element2 + ":" + s.ascended; }

    public static List<SkillPool.Entry> pool(YouSlot s) {
        return POOLS.computeIfAbsent(key(s), k -> build(s));
    }

    public static Skill ultimate(YouSlot s) {
        return ULTS.computeIfAbsent(key(s), k -> s.demon() ? new com.xenowarriors.skill.ffr.SrSkills.HellclawRend()      // (Devil Unleashed: sneak + the transform key)
                : s.angel() ? new YouSkillSet.Storm(s.element, 1.6F) : new YouSkillSet.Storm(s.element, 1F));
    }

    private static List<SkillPool.Entry> build(YouSlot s) {
        Race r = s.race; Element el = s.element;
        List<SkillPool.Entry> l = new ArrayList<>();
        // the starter kit (the default loadout is the first four)
        l.add(e(new YouSkillSet.KarmaStrike(), 0));
        l.add(e(new DojoSkills.KarmaBlast(el.color), 0));
        l.add(e(new YouSkillSet.Bolt(el), 0));
        l.add(e(switch (r) {                                   // the fourth starter: your race's signature
            case FOX_KIN -> new com.xenowarriors.skill.kaeru.FoxHealSkill();
            case SKYLANDER, ANGEL -> new LexySkills.WingRush();
            case DEMON -> new com.xenowarriors.skill.ffr.SrSkills.DevilDash();
            case SEEDLING -> new YouSkillSet.RootSnare();
            case YOU -> new YouSkillSet.Scan();
            default -> new YouSkillSet.Focus();
        }, 0));
        if (r != Race.HUMAN) l.add(e(new YouSkillSet.Focus(), 5));
        l.add(e(new YouSkillSet.BurstStep(), r == Race.HUMAN ? 3 : 8));
        l.add(e(new YouSkillSet.Wave(el), r == Race.HUMAN ? 10 : 15));
        switch (r) {
            case FOX_KIN -> { l.add(e(new YouSkillSet.KitsuneTier(1), 5)); l.add(e(new YouSkillSet.KitsuneTier(2), 20)); l.add(e(new YouSkillSet.KitsuneTier(3), 45)); }
            case SKYLANDER -> l.add(e(new YouSkillSet.SkyForm(s.form != YouSlot.FORM_SOLAR), 10));
            case ANGEL -> { l.add(e(new YouSkillSet.DivineLight(), 0)); l.add(e(new YouSkillSet.SkyForm(true), 0)); l.add(e(new YouSkillSet.SkyForm(false), 0)); }
            case DEMON -> l.add(e(new com.xenowarriors.skill.ffr.SrSkills.DevilFlame(), 15));
            case SEEDLING -> l.add(e(new YouSkillSet.Bloom(), 10));
            case YOU -> { l.add(e(new YouSkillSet.Suppress(), 10)); l.add(e(new YouCapture.CaptureSkill(), 12)); }
            default -> l.add(e(new YouSkillSet.SecondWind(), 20));
        }
        // what a Government path or a Devil's awakening adds - after the rest, so saved loadouts keep their skills
        switch (s.path) {
            case YouSlot.PATH_MODIFIED -> l.add(e(new YouPaths.Overclock(), 0));
            case YouSlot.PATH_DEMON -> { l.add(e(new com.xenowarriors.skill.ffr.SrSkills.DevilDash(), 0)); l.add(e(new com.xenowarriors.skill.ffr.SrSkills.DevilFlame(), 0)); }
            case YouSlot.PATH_SKYLANDER -> { l.add(e(new LexySkills.WingRush(), 0)); l.add(e(new YouSkillSet.SkyForm(s.form != YouSlot.FORM_SOLAR), 0)); }
            default -> {}
        }
        if (s.devil) l.add(e(new YouDevil.SelfSplit(), 0));
        if (r == Race.SKYLANDER) l.add(e(new YouSkillSet.SkyForm(s.form == YouSlot.FORM_SOLAR), YouAngel.SECOND_FORM_LEVEL));   // (a born Skylander's other path, from Lv 50)
        if (s.element2 != null) { l.add(e(new YouSkillSet.Bolt(s.element2, true), 0)); l.add(e(new YouSkillSet.Wave(s.element2, true), 0)); }   // (an Element Crystal's)
        if (s.ascended) { l.add(e(new YouSkillSet.DivineLight(), 0)); l.add(e(new YouAngel.AngelEye(), 0)); }
        return List.copyOf(l);
    }

    private static SkillPool.Entry e(Skill s, int lv) { return new SkillPool.Entry(s, lv, 1); }
}
