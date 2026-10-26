package com.xenowarriors.skill;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.skill.mikiko.*;
import com.xenowarriors.skill.rhalix.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every skill a character can learn. A player equips 4 of their unlocked skills (slots 1-4) plus the ultimate (slot 5).
 * Skills unlock at a Character Level; the first four are known from the start (and are the default loadout).
 */
public final class SkillPool {
    public record Entry(Skill skill, int unlockLevel) {}

    private static final Map<CharacterType, List<Entry>> POOL = new EnumMap<>(CharacterType.class);
    private static final Map<CharacterType, Skill> ULTIMATE = new EnumMap<>(CharacterType.class);

    static {
        POOL.put(CharacterType.MIKIKO, List.of(
                new Entry(new HekubladeScatterSkill(), 0), new Entry(new KitsuneSkill(), 0),
                new Entry(new NetsuSkill(), 0), new Entry(new FireBlitzSkill(), 0),
                new Entry(new DanceKickSkill(), 15), new Entry(new NetsuBurstSkill(), 30),
                new Entry(new ManifestGreatnessSkill(), 50), new Entry(new TrueEmpathySkill(), 75)));
        ULTIMATE.put(CharacterType.MIKIKO, new BlazingWindSkill());
        POOL.put(CharacterType.RHALIX, List.of(
                new Entry(new KishiSkill(), 0), new Entry(new EarthWaveSkill(), 0),
                new Entry(new GravityCompressSkill(), 0), new Entry(new BangSkill(), 0),
                new Entry(new KaReleaseSkill(), 60)));
        ULTIMATE.put(CharacterType.RHALIX, new GravityBarrageSkill());
    }

    private SkillPool() {}

    public static List<Entry> of(CharacterType type) { return POOL.getOrDefault(type, List.of()); }

    public static Skill ultimate(CharacterType type) { return ULTIMATE.get(type); }

    public static Entry entry(CharacterType type, int index) {
        List<Entry> l = of(type);
        return index >= 0 && index < l.size() ? l.get(index) : null;
    }

    public static int[] defaultLoadout() { return new int[]{0, 1, 2, 3}; }

    /** The skill in a slot for this loadout (slot 4 = ultimate). */
    public static Skill inSlot(CharacterType type, int[] loadout, int slot) {
        if (slot == SkillManager.ULTIMATE_SLOT) return ultimate(type);
        if (loadout == null || slot < 0 || slot >= loadout.length) return null;
        Entry e = entry(type, loadout[slot]);
        return e == null ? null : e.skill();
    }

    public static boolean unlocked(Entry e, int level, boolean creative) {
        return creative || level >= e.unlockLevel();
    }
}
