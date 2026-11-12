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
    /** Characters with more than one ultimate to choose from (index 0 = the default one, the same as ULTIMATE). */
    private static final Map<CharacterType, List<Entry>> ULTIMATES = new EnumMap<>(CharacterType.class);

    static {
        POOL.put(CharacterType.MIKIKO, List.of(
                new Entry(new HekubladeScatterSkill(), 0), new Entry(new KitsuneSkill(), 0),
                new Entry(new NetsuSkill(), 0), new Entry(new FireBlitzSkill(), 0),
                new Entry(new DanceKickSkill(), 15, 3), new Entry(new NetsuBurstSkill(), 30, 2),
                new Entry(new ManifestGreatnessSkill(), 50, 1), new Entry(new TrueEmpathySkill(), 75, 3),
                new Entry(new com.xenowarriors.soul.SoulSplitterSkill(), 10, 2)));
        Skill blazing = new BlazingWindSkill();
        ULTIMATE.put(CharacterType.MIKIKO, blazing);
        // a second ultimate to pick for the ultimate slot, once he's strong enough
        ULTIMATES.put(CharacterType.MIKIKO, List.of(new Entry(blazing, 0), new Entry(new HekubladeRainSkill(), HekubladeRainSkill.UNLOCK_LEVEL)));
        POOL.put(CharacterType.RHALIX, List.of(
                new Entry(new KishiSkill(), 0), new Entry(new EarthWaveSkill(), 0),
                new Entry(new GravityCompressSkill(), 0), new Entry(new BangSkill(), 0),
                new Entry(new com.xenowarriors.skill.rhalix.MoonReflectSkill(), 25, 2), new Entry(new KaReleaseSkill(), 60, 3),
                new Entry(new com.xenowarriors.soul.SoulSplitterSkill(), 10, 2),
                new Entry(new JudgmentAbsorptionSkill(), JudgmentAbsorptionSkill.UNLOCK_LEVEL)));   // (Season 1; added last so saved loadout numbers stay put)
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
        ULTIMATE.put(CharacterType.LEXY, new com.xenowarriors.skill.sp.LexySkills.MaynayaCrash());   // (Persephone is her transformation now: UltForms)
        POOL.put(CharacterType.MAX, List.of(
                new Entry(new com.xenowarriors.skill.sp.MaxSkills.DaggerFlurry(), 0), new Entry(new com.xenowarriors.skill.sp.MaxSkills.WindOfSorrow(), 0),
                new Entry(new com.xenowarriors.skill.sp.MaxSkills.WindWall(), 0), new Entry(new com.xenowarriors.skill.sp.MaxSkills.LunarMind(), 0),
                new Entry(new com.xenowarriors.skill.sp.MaxSkills.RapidVampireSlice(), 30)));
        ULTIMATE.put(CharacterType.MAX, new com.xenowarriors.skill.sp.MaxSkills.MoonlitGale());
        POOL.put(CharacterType.SAM, List.of(
                new Entry(new com.xenowarriors.skill.sp.SamSkills.BatterUp(), 0), new Entry(new com.xenowarriors.skill.sp.SamSkills.ClearSpace(), 0, 3),
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
        POOL.put(CharacterType.YURI, List.of(
                new Entry(new com.xenowarriors.skill.ffr.YuriSkills.CameraHack(), 0, 2), new Entry(new com.xenowarriors.skill.ffr.YuriSkills.Firewall(), 0, 2),
                new Entry(new com.xenowarriors.skill.ffr.YuriSkills.ElevatorHack(), 0, 2), new Entry(new com.xenowarriors.skill.ffr.YuriSkills.TenjoMedical(), 0, 2),
                new Entry(new com.xenowarriors.skill.ffr.YuriSkills.VirusUpload(), 20, 2)));
        ULTIMATE.put(CharacterType.YURI, new com.xenowarriors.skill.ffr.YuriSkills.OpenSkyGround());
        POOL.put(CharacterType.ZANE, List.of(
                new Entry(new com.xenowarriors.skill.ffr.ZaneSkills.FireSpell(), 0, 3), new Entry(new com.xenowarriors.skill.ffr.ZaneSkills.IceSpell(), 0, 3),
                new Entry(new com.xenowarriors.skill.ffr.ZaneSkills.ThunderSpell(), 0, 3), new Entry(new com.xenowarriors.skill.ffr.ZaneSkills.BroomSmack(), 0, 3),
                new Entry(new com.xenowarriors.skill.ffr.ZaneSkills.ManaBarrier(), 20, 3)));
        ULTIMATE.put(CharacterType.ZANE, new com.xenowarriors.skill.ffr.ZaneSkills.PastMages());
        POOL.put(CharacterType.LOGAN, List.of(
                new Entry(new com.xenowarriors.skill.ffr.SrSkills.ThunderFlash(), 0), new Entry(new com.xenowarriors.skill.ffr.SrSkills.KnightCharge(), 0),
                new Entry(new com.xenowarriors.skill.ffr.SrSkills.RoyalGuard(), 0), new Entry(new com.xenowarriors.skill.ffr.SrSkills.PortalEscape(), 0)));
        ULTIMATE.put(CharacterType.LOGAN, new com.xenowarriors.skill.ffr.SrSkills.ThunderJudgement());
        POOL.put(CharacterType.KILL, List.of(
                new Entry(new com.xenowarriors.skill.ffr.SrSkills.Impale(), 0), new Entry(new com.xenowarriors.skill.ffr.SrSkills.DevilDash(), 0),
                new Entry(new com.xenowarriors.skill.ffr.SrSkills.PlayWeak(), 0), new Entry(new com.xenowarriors.skill.ffr.SrSkills.DevilFlame(), 0)));
        ULTIMATE.put(CharacterType.KILL, new com.xenowarriors.skill.ffr.SrSkills.HellclawRend());   // (Devil Unleashed is his transformation now: UltForms)
        POOL.put(CharacterType.KEITA, List.of(
                new Entry(new com.xenowarriors.skill.ffr.SrSkills.LightsOut(), 0), new Entry(new com.xenowarriors.skill.ffr.SrSkills.Kneel(), 0),
                new Entry(new com.xenowarriors.skill.ffr.SrSkills.LazySlash(), 0), new Entry(new com.xenowarriors.skill.ffr.SrSkills.PowerNap(), 0)));
        ULTIMATE.put(CharacterType.KEITA, new com.xenowarriors.skill.ffr.SrSkills.MassCut());
    }

    private SkillPool() {}

    public static List<Entry> of(CharacterType type) { return POOL.getOrDefault(type, List.of()); }

    public static Skill ultimate(CharacterType type) { return ULTIMATE.get(type); }

    public static Entry entry(CharacterType type, int index) {
        List<Entry> l = of(type);
        return index >= 0 && index < l.size() ? l.get(index) : null;
    }

    // ---- by player: a You character's pool comes from their race and element (works on both sides)

    public static List<Entry> of(net.minecraft.world.entity.player.Player p) {
        var s = com.xenowarriors.you.You.slot(p);
        return s != null ? com.xenowarriors.you.YouSkills.pool(s) : of(typeOf(p));
    }

    private static CharacterType typeOf(net.minecraft.world.entity.player.Player p) {
        return p.level().isClientSide ? com.xenowarriors.you.You.clientType.apply(p) : com.xenowarriors.character.ModCapabilities.getType(p);
    }

    public static Skill ultimate(net.minecraft.world.entity.player.Player p) {
        var s = com.xenowarriors.you.You.slot(p);
        return s != null ? com.xenowarriors.you.YouSkills.ultimate(s) : ultimate(typeOf(p));
    }

    public static Entry entry(net.minecraft.world.entity.player.Player p, int index) {
        List<Entry> l = of(p);
        return index >= 0 && index < l.size() ? l.get(index) : null;
    }

    // ---- ultimates: some characters can pick another one for the ultimate slot (it unlocks at a Character Level).
    //      A loadout array may carry the chosen one as a 5th number (0 or missing = the default ultimate).

    /** The ultimates this character can choose between (just the one for most). */
    public static List<Entry> ultimates(CharacterType type) {
        List<Entry> l = ULTIMATES.get(type);
        if (l != null) return l;
        Skill u = ultimate(type);
        return u == null ? List.of() : List.of(new Entry(u, 0));
    }

    public static List<Entry> ultimates(net.minecraft.world.entity.player.Player p) {
        var s = com.xenowarriors.you.You.slot(p);
        if (s != null) { Skill u = com.xenowarriors.you.YouSkills.ultimate(s); return u == null ? List.of() : List.of(new Entry(u, 0)); }
        return ultimates(typeOf(p));
    }

    /** The ultimate picked in this loadout (its 5th number), else the default one. */
    public static Skill ultimate(CharacterType type, int[] loadout) {
        List<Entry> l = ultimates(type);
        int c = loadout != null && loadout.length > SkillManager.ULTIMATE_SLOT ? loadout[SkillManager.ULTIMATE_SLOT] : 0;
        return c > 0 && c < l.size() ? l.get(c).skill() : ultimate(type);
    }

    public static Skill ultimate(net.minecraft.world.entity.player.Player p, int[] loadout) {
        List<Entry> l = ultimates(p);
        int c = loadout != null && loadout.length > SkillManager.ULTIMATE_SLOT ? loadout[SkillManager.ULTIMATE_SLOT] : 0;
        return c > 0 && c < l.size() ? l.get(c).skill() : ultimate(p);
    }

    /** A 4-skill loadout with the chosen ultimate added as its 5th number. */
    public static int[] withUltimate(int[] loadout, int ultimate) {
        int[] out = java.util.Arrays.copyOf(loadout, SkillManager.ULTIMATE_SLOT + 1);
        out[SkillManager.ULTIMATE_SLOT] = Math.max(0, ultimate);
        return out;
    }

    public static Skill inSlot(net.minecraft.world.entity.player.Player p, int[] loadout, int slot) {
        if (slot == SkillManager.ULTIMATE_SLOT) return ultimate(p, loadout);
        if (loadout == null || slot < 0 || slot >= loadout.length) return null;
        Entry e = entry(p, loadout[slot]);
        return e == null || rlOnly(typeOf(p), e) ? null : e.skill();
    }

    public static int[] defaultLoadout() { return new int[]{0, 1, 2, 3}; }

    /** A right+left click skill: not in the skill menu or the slots any more (Character Customization > R+L Click). */
    public static boolean rlOnly(CharacterType t, Entry e) {
        return e != null && t != CharacterType.YOU && com.xenowarriors.toggle.Toggles.rlChoices(t).contains(e.skill().getId());   // (a custom character's are its starter kit too: they stay in its skills as well)
    }

    /** Pool indices the skill menu shows for this character (everything but the right+left click skills). */
    public static List<Integer> menuIndices(CharacterType t, List<Entry> pool) {
        List<Integer> l = new java.util.ArrayList<>();
        for (int i = 0; i < pool.size(); i++) if (!rlOnly(t, pool.get(i))) l.add(i);
        return l;
    }

    /** The first four skills that go in slots (right+left click ones skipped); -1 = an empty slot. */
    public static int[] defaultLoadout(CharacterType t) { return clean(t, new int[]{-1, -1, -1, -1}); }

    /**
     * A loadout without the right+left click skills: each one's slot gets the next skill that isn't equipped yet (one
     * known from the start first), or stays empty (-1).
     */
    public static int[] clean(CharacterType t, int[] l) {
        int[] out = l.clone();
        List<Entry> pool = of(t);
        for (int s = 0; s < out.length; s++) {
            Entry e = out[s] >= 0 && out[s] < pool.size() ? pool.get(out[s]) : null;
            if (e != null && !rlOnly(t, e)) continue;
            out[s] = -1;
            int best = -1;                                                       // (the next free skill: Season 1 first, then the earliest unlock)
            for (int i = 0; i < pool.size(); i++) {
                if (rlOnly(t, pool.get(i))) continue;
                boolean used = false;
                for (int k = 0; k < out.length; k++) if (out[k] == i) used = true;
                if (used) continue;
                if (best < 0 || pool.get(i).season() < pool.get(best).season()
                        || pool.get(i).season() == pool.get(best).season() && pool.get(i).unlockLevel() < pool.get(best).unlockLevel()) best = i;
            }
            out[s] = best;
        }
        return out;
    }

    /** The skill in a slot for this loadout (slot 4 = ultimate). */
    public static Skill inSlot(CharacterType type, int[] loadout, int slot) {
        if (slot == SkillManager.ULTIMATE_SLOT) return ultimate(type, loadout);
        if (loadout == null || slot < 0 || slot >= loadout.length) return null;
        Entry e = entry(type, loadout[slot]);
        return e == null || rlOnly(type, e) ? null : e.skill();
    }

    public static boolean unlocked(Entry e, int level, boolean creative) {
        return creative || level >= e.unlockLevel();
    }

    /** Unlocked by level (or creative) AND its story season is unlocked in this world. */
    public static boolean available(Entry e, int level, boolean creative, int season) {
        return season >= e.season() && unlocked(e, level, creative);
    }
}
