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
    /** season = the story season it comes from (1 always on; 2/3 need /xeno season unlock). */
    public record Entry(Skill skill, int unlockLevel, int season) {
        Entry(Skill skill, int unlockLevel) { this(skill, unlockLevel, 1); }
    }

    private static final Map<CharacterType, List<Entry>> POOL = new EnumMap<>(CharacterType.class);
    private static final Map<CharacterType, Skill> ULTIMATE = new EnumMap<>(CharacterType.class);

    static {
        POOL.put(CharacterType.MIKIKO, List.of(
                new Entry(new HekubladeScatterSkill(), 0), new Entry(new KitsuneSkill(), 0),
                new Entry(new NetsuSkill(), 0), new Entry(new FireBlitzSkill(), 0),
                new Entry(new DanceKickSkill(), 15, 3), new Entry(new NetsuBurstSkill(), 30, 2),
                new Entry(new ManifestGreatnessSkill(), 50, 1), new Entry(new TrueEmpathySkill(), 75, 3),
                new Entry(new com.xenowarriors.soul.SoulSplitterSkill(), 10, 2)));
        ULTIMATE.put(CharacterType.MIKIKO, new BlazingWindSkill());
        POOL.put(CharacterType.RHALIX, List.of(
                new Entry(new KishiSkill(), 0), new Entry(new EarthWaveSkill(), 0),
                new Entry(new GravityCompressSkill(), 0), new Entry(new BangSkill(), 0),
                new Entry(new com.xenowarriors.skill.rhalix.MoonReflectSkill(), 25, 2), new Entry(new KaReleaseSkill(), 60, 3),
                new Entry(new com.xenowarriors.soul.SoulSplitterSkill(), 10, 2)));
        ULTIMATE.put(CharacterType.RHALIX, new GravityBarrageSkill());
        POOL.put(CharacterType.ULTIMA, List.of(
                new Entry(new com.xenowarriors.skill.ultima.AttackPredictionSkill(), 0, 2), new Entry(new com.xenowarriors.skill.ultima.EnergyBlastSkill(), 0, 2),
                new Entry(new com.xenowarriors.skill.ultima.LivingWeaponSkill(), 0, 2), new Entry(new com.xenowarriors.skill.ultima.OutputSurgeSkill(), 0, 2)));
        ULTIMATE.put(CharacterType.ULTIMA, new com.xenowarriors.skill.ultima.FullOutputBeamSkill());
        POOL.put(CharacterType.KAERU, List.of(
                new Entry(new HekubladeScatterSkill(), 0, 2), new Entry(new com.xenowarriors.skill.kaeru.SoundBurstSkill(), 0, 2),
                new Entry(new com.xenowarriors.skill.kaeru.HekubarrierSkill(), 0, 2), new Entry(new com.xenowarriors.skill.kaeru.FoxHealSkill(), 0, 2),
                new Entry(new com.xenowarriors.soul.SoulSplitterSkill(), 0, 2), new Entry(new com.xenowarriors.skill.kaeru.ScentTrackSkill(), 15, 2)));
        ULTIMATE.put(CharacterType.KAERU, new com.xenowarriors.skill.kaeru.HekubeatsFinaleSkill());
        // the Sky Patrol (season 1)
        POOL.put(CharacterType.KYRIE, List.of(
                new Entry(new com.xenowarriors.skill.sp.KyrieSkills.RapierThrust(), 0), new Entry(new KishiSkill(), 0),
                new Entry(new com.xenowarriors.skill.sp.KyrieSkills.GravityFetch(), 0), new Entry(new com.xenowarriors.skill.sp.KyrieSkills.BulletStop(), 0),
                new Entry(new com.xenowarriors.skill.sp.KyrieSkills.BoxingStance(), 15), new Entry(new com.xenowarriors.skill.sp.KyrieSkills.NoMilk(), 30)));
        ULTIMATE.put(CharacterType.KYRIE, new com.xenowarriors.skill.sp.KyrieSkills.RoyalGravity());
        POOL.put(CharacterType.LEXY, List.of(
                new Entry(new com.xenowarriors.skill.sp.LexySkills.FreakPunch(), 0), new Entry(new com.xenowarriors.skill.sp.LexySkills.TableBreaker(), 0),
                new Entry(new com.xenowarriors.skill.sp.LexySkills.KarmaSurge(), 0), new Entry(new com.xenowarriors.skill.sp.LexySkills.WingRush(), 0),
                new Entry(new com.xenowarriors.skill.sp.LexySkills.IronGrip(), 20)));
        ULTIMATE.put(CharacterType.LEXY, new com.xenowarriors.skill.sp.LexySkills.Persephone());
        POOL.put(CharacterType.MAX, List.of(
                new Entry(new com.xenowarriors.skill.sp.MaxSkills.DaggerFlurry(), 0), new Entry(new com.xenowarriors.skill.sp.MaxSkills.WindOfSorrow(), 0),
                new Entry(new com.xenowarriors.skill.sp.MaxSkills.WindWall(), 0), new Entry(new com.xenowarriors.skill.sp.MaxSkills.LunarMind(), 0),
                new Entry(new com.xenowarriors.skill.sp.MaxSkills.RapidVampireSlice(), 30)));
        ULTIMATE.put(CharacterType.MAX, new com.xenowarriors.skill.sp.MaxSkills.MoonlitGale());
        POOL.put(CharacterType.SAM, List.of(
                new Entry(new com.xenowarriors.skill.sp.SamSkills.BatterUp(), 0), new Entry(new com.xenowarriors.skill.sp.SamSkills.ClearSpace(), 0),
                new Entry(new com.xenowarriors.skill.sp.SamSkills.TickingTimeBomb(), 0), new Entry(new com.xenowarriors.skill.sp.SamSkills.KnockoutMode(), 0),
                new Entry(new com.xenowarriors.skill.sp.SamSkills.BatterShoot(), 20)));
        ULTIMATE.put(CharacterType.SAM, new com.xenowarriors.skill.sp.SamSkills.PsychoticTimeBomb());
        POOL.put(CharacterType.TECHNICA, List.of(
                new Entry(new com.xenowarriors.skill.sp.TechSkills.ExplosiveRounds(), 0), new Entry(new com.xenowarriors.skill.sp.TechSkills.GravityRounds(), 0),
                new Entry(new com.xenowarriors.skill.sp.TechSkills.GravityBoots(), 0), new Entry(new com.xenowarriors.skill.sp.TechSkills.VisorScan(), 0),
                new Entry(new com.xenowarriors.skill.sp.TechSkills.WingCloak(), 15), new Entry(new com.xenowarriors.skill.sp.TechSkills.RescuePlatform(), 30)));
        ULTIMATE.put(CharacterType.TECHNICA, new com.xenowarriors.skill.sp.TechSkills.TechBarrage());
        POOL.put(CharacterType.STEVIE, List.of(
                new Entry(new com.xenowarriors.skill.sp.DojoSkills.KarmaBlast(0x6A4AE0), 0), new Entry(new com.xenowarriors.skill.sp.DojoSkills.WarCry(), 0),
                new Entry(new com.xenowarriors.skill.sp.DojoSkills.GoodKarma(), 0), new Entry(new com.xenowarriors.skill.sp.DojoSkills.DiscoKick(), 0),
                new Entry(new com.xenowarriors.skill.sp.DojoSkills.BarThief(), 15)));
        ULTIMATE.put(CharacterType.STEVIE, new com.xenowarriors.skill.sp.DojoSkills.BrotherBlast(0x6A4AE0));
        POOL.put(CharacterType.GARRY, List.of(
                new Entry(new com.xenowarriors.skill.sp.DojoSkills.KarmaBlast(0x3AD83A), 0), new Entry(new com.xenowarriors.skill.sp.DojoSkills.WarCry(), 0),
                new Entry(new com.xenowarriors.skill.sp.DojoSkills.BodySlam(), 0), new Entry(new com.xenowarriors.skill.sp.DojoSkills.DiscoKick(), 0),
                new Entry(new com.xenowarriors.skill.sp.DojoSkills.FreestyleBars(), 15)));
        ULTIMATE.put(CharacterType.GARRY, new com.xenowarriors.skill.sp.DojoSkills.BrotherBlast(0x3AD83A));
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

    /** Unlocked by level (or creative) AND its story season is unlocked in this world. */
    public static boolean available(Entry e, int level, boolean creative, int season) {
        return season >= e.season() && unlocked(e, level, creative);
    }
}
