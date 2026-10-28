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

    private static String key(YouSlot s) { return s.race + ":" + s.element + ":" + s.form; }

    public static List<SkillPool.Entry> pool(YouSlot s) {
        return POOLS.computeIfAbsent(key(s), k -> build(s));
    }

    public static Skill ultimate(YouSlot s) {
        return ULTS.computeIfAbsent(key(s), k -> switch (s.race) {
            case DEMON -> new com.xenowarriors.skill.ffr.SrSkills.DevilUnleashed();
            case ANGEL -> new YouSkillSet.Storm(s.element, 1.6F);
            default -> new YouSkillSet.Storm(s.element, 1F);
        });
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
            case YOU -> l.add(e(new YouSkillSet.Suppress(), 10));
            default -> l.add(e(new YouSkillSet.SecondWind(), 20));
        }
        return List.copyOf(l);
    }

    private static SkillPool.Entry e(Skill s, int lv) { return new SkillPool.Entry(s, lv, 1); }
}
