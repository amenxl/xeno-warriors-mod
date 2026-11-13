package com.xenowarriors.toggle;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.TogglesPacket;
import com.xenowarriors.skill.FormMastery;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.skill.SkillPool;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * A character's transformations, buffs and passive skills in one place:
 * <ul>
 *   <li>the toggle wheel (hold the transform key) - transform, light / put out a buff, switch a passive on or off;</li>
 *   <li>Character Customization > Passives - turn each one on or off for good, and see its level and mastery;</li>
 *   <li>the right+left click skill (hold right click, tap left click): one move per character, picked in
 *       Character Customization > R+L Click - Mikiko's Fire Blitz or Dance Kick, Rhalix's Bang. It stops a block,
 *       and its cooldown is a fifth shorter than from a slot.</li>
 * </ul>
 * True Empathy is a passive now: switched on, Mikiko copies the moves he sees and reads attacks coming at him by
 * himself (Karma each time); overusing it (three times inside 90 seconds) strains him - and switches it off.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Toggles {
    private Toggles() {}

    /**
     * TRANSFORM: the transform key / wheel. BUFF: lit and put out from the wheel. PASSIVE: switched on / off from the wheel,
     * starts off. AUTO: works by itself, switched on / off only in Character Customization (starts on). TRACK: the wheel
     * opens Kaeru's scent picker.
     */
    public enum Kind { TRANSFORM("Transformation"), BUFF("Buff"), PASSIVE("Passive"), AUTO("Passive"), TRACK("Tracking");
        public final String title; Kind(String t) { title = t; } }

    public enum Entry {
        FOX_KING("fox_king", "Fox King", Kind.TRANSFORM, 0xFFB347),
        DEMONS_REMNANTS("demons_remnants", "Demon's Remnants", Kind.TRANSFORM, 0xC0303A),
        DEVIL_UNLEASHED("devil_unleashed", "Devil Unleashed", Kind.TRANSFORM, 0x8A1A2A),
        PERSEPHONE("persephone", "Persephone", Kind.TRANSFORM, 0xE04A8A),
        NETSU("netsu", "Netsu", Kind.BUFF, 0xFF6A1A),
        TRUE_EMPATHY("true_empathy", "True Empathy", Kind.PASSIVE, 0xC890FF),
        LIVING_WEAPON("living_weapon", "Living Weapon", Kind.TRANSFORM, 0x1FE07A),
        ATTACK_PREDICTION("attack_prediction", "Attack Prediction", Kind.PASSIVE, 0x7AF0D0),
        LUNAR_MIND("lunar_mind", "Lunar Mind", Kind.AUTO, 0x9AD2F2),
        GRAVITY_BOOTS("gravity_boots", "Gravity Boots", Kind.AUTO, 0x9A4AE0),
        VISOR_SCAN("visor_scan", "Visor Scan", Kind.AUTO, 0xEC5AA0),
        RESCUE_PLATFORM("rescue_platform", "Rescue Platform", Kind.AUTO, 0xFFD24A),
        SCENT_TRACK("scent_track", "Scent Tracking", Kind.TRACK, 0xE8701C),
        // a Modified Human's Government mods, for good (switched on / off on the wheel; they start on)
        MOD_SPEED("mod_speed", "Speed Mod", Kind.PASSIVE, 0x4FB3E8, true),
        MOD_JUMP("mod_jump", "Jump Boosters", Kind.PASSIVE, 0x4FB3E8, true),
        MOD_SCANNER("mod_scanner", "Scanner Eye", Kind.PASSIVE, 0x4FB3E8, true),
        MOD_PLATING("mod_plating", "Armor Plating", Kind.PASSIVE, 0x4FB3E8, true),
        // a Devil's: your Karma can't be read or sensed (starts on)
        KARMA_MASK("karma_mask", "Karma Mask", Kind.PASSIVE, 0x6A1020, true),
        // a second element's: the first Bolt casts the second element (starts off)
        SWAP_ELEMENT("swap_element", "Bolt: second element", Kind.PASSIVE, 0xC8A0FF),
        // abilities bought with APL (apl.Apl): there once bought, on until switched off
        REVIVE_BUBBLE("revive_bubble", "Revive Bubble", Kind.PASSIVE, 0x7AD8FF, true),
        SECOND_WIND("second_wind", "Second Wind", Kind.PASSIVE, 0x8AF0A0, true),
        STEADY_FEET("steady_feet", "Steady Feet", Kind.PASSIVE, 0xC8A070, true),
        AUTO_EAT("auto_eat", "Auto Eat", Kind.PASSIVE, 0xF0B060, true),
        AUTO_POTION("auto_potion", "Auto Potion", Kind.PASSIVE, 0xE060C0, true);

        /** Shown on the toggle wheel (the automatic ones only in Character Customization). */
        public boolean wheel() { return kind != Kind.AUTO; }

        public final String id, title;
        public final Kind kind;
        public final int color;
        /** A passive that starts switched on. */
        public final boolean startsOn;
        Entry(String id, String title, Kind kind, int color) { this(id, title, kind, color, false); }
        Entry(String id, String title, Kind kind, int color, boolean startsOn) { this.id = id; this.title = title; this.kind = kind; this.color = color; this.startsOn = startsOn; }

        /** On until switched off. */
        public boolean defaultOn() { return kind != Kind.PASSIVE || startsOn; }

        public static Entry byId(String id) { for (Entry e : values()) if (e.id.equals(id)) return e; return null; }
    }

    /** The right+left click skills each character can pick from (skill ids). */
    public static List<String> rlChoices(CharacterType t) {
        return switch (t) {
            case MIKIKO -> List.of("fire_blitz", "dance_kick");
            case RHALIX -> List.of("bang");
            case KYRIE -> List.of("gravity_fetch");
            case MAX -> List.of("wind_of_sorrow");
            case SAM -> List.of("batter_up", "clear_space");
            case TECHNICA -> List.of("explosive_rounds", "gravity_rounds");
            case KAERU -> List.of("sound_burst");
            case ULTIMA -> List.of("energy_blast");
            case ZANE -> List.of("fire_spell", "thunder_spell", "broom_smack");
            case YURI -> List.of("elevator_hack", "virus_upload");
            case STEVIE, GARRY -> List.of("karma_blast", "disco_kick");
            case YOU -> List.of("you_strike", "karma_blast", "you_bolt");      // (custom characters: Karma Strike, Karma Blast, their element's Bolt - Firebolt for fire)
            default -> List.of();
        };
    }

    public static final float RL_COOLDOWN = 0.8F;
    private static final String ROOT = "xw_toggles", RL = "xw_rl_skill";

    /** This player's entries (by who they're playing), and the APL abilities they've bought for that character. */
    public static List<Entry> of(Player p) {
        List<Entry> l = new ArrayList<>(base(p));
        for (com.xenowarriors.apl.Apl.Ability a : com.xenowarriors.apl.Apl.Ability.values())
            if (a.toggle != null && com.xenowarriors.apl.Apl.rank(p, a) > 0) { Entry e = Entry.byId(a.toggle); if (e != null) l.add(e); }
        return l;
    }

    private static List<Entry> base(Player p) {
        CharacterType t = ModCapabilities.getType(p);
        if (t == CharacterType.YOU) {
            var s = com.xenowarriors.you.You.slot(p);
            if (s == null) return List.of();
            List<Entry> l = new ArrayList<>();
            if (s.demon()) l.add(Entry.DEVIL_UNLEASHED);
            if (s.devil) l.add(Entry.KARMA_MASK);
            if (s.element2 != null) l.add(Entry.SWAP_ELEMENT);
            if (s.path == com.xenowarriors.you.YouSlot.PATH_MODIFIED) { l.add(Entry.MOD_SPEED); l.add(Entry.MOD_JUMP); l.add(Entry.MOD_SCANNER); l.add(Entry.MOD_PLATING); }
            return l;
        }
        return of(t);
    }

    /** A character's entries (its bot gets the same ones). */
    public static List<Entry> of(CharacterType t) {
        return switch (t) {
            case MIKIKO -> List.of(Entry.FOX_KING, Entry.NETSU, Entry.TRUE_EMPATHY);
            case RHALIX -> List.of(Entry.DEMONS_REMNANTS);
            case ULTIMA -> List.of(Entry.LIVING_WEAPON, Entry.ATTACK_PREDICTION);
            case MAX -> List.of(Entry.LUNAR_MIND);
            case TECHNICA -> List.of(Entry.GRAVITY_BOOTS, Entry.VISOR_SCAN, Entry.RESCUE_PLATFORM);
            case KAERU -> List.of(Entry.SCENT_TRACK);
            case KILL -> List.of(Entry.DEVIL_UNLEASHED);
            case LEXY -> List.of(Entry.PERSEPHONE);
            default -> List.of();
        };
    }

    /** The owner's settings for one character (read only: nothing is written for a character they never touched). */
    private static CompoundTag settingsFor(Player owner, CharacterType t) {
        return owner.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ROOT).getCompound(com.xenowarriors.character.PlayerCharacter.key(t, 0));
    }

    /** That character's bot: the owner's on / off for it (what they set in Character Customization or on the wheel). */
    public static boolean enabledFor(Player owner, CharacterType t, Entry e) {
        if (owner == null) return e.defaultOn();
        CompoundTag me = settingsFor(owner, t);
        return me.contains(e.id) ? me.getBoolean(e.id) : e.defaultOn();
    }

    /** (Tests) sets the owner's on / off for a character they're not playing. */
    public static void setForTest(Player owner, CharacterType t, Entry e, boolean on) {
        CompoundTag pers = owner.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = pers.getCompound(ROOT);
        String k = com.xenowarriors.character.PlayerCharacter.key(t, 0);
        CompoundTag me = root.getCompound(k);
        me.putBoolean(e.id, on);
        root.put(k, me); pers.put(ROOT, root); owner.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
    }

    /** That character's bot: the right+left click skill the owner picked for it ("" = none). */
    public static String rlSkillFor(Player owner, CharacterType t) {
        List<String> ok = rlChoices(t);
        if (ok.isEmpty()) return "";
        String id = owner == null ? "" : settingsFor(owner, t).getString(RL);
        return ok.contains(id) ? id : id.equals("-") ? "" : ok.get(0);
    }

    private static String charKey(Player p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.key()).orElse(ModCapabilities.getType(p).getId());
    }

    private static CompoundTag mine(Player p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = pers.getCompound(ROOT);
        CompoundTag me = root.getCompound(charKey(p));
        root.put(charKey(p), me); pers.put(ROOT, root); p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        return me;
    }

    /** On: a transformation / buff may be used; a passive is running. Passives start off, the rest on. */
    public static boolean enabled(Player p, Entry e) {
        CompoundTag me = mine(p);
        return me.contains(e.id) ? me.getBoolean(e.id) : e.defaultOn();   // (passives start off - but a Modified Human's mods and a Devil's mask; the automatic ones, transformations and buffs on)
    }

    public static void setEnabled(ServerPlayer p, Entry e, boolean on) {
        if (on && unavailable(p, e) != null) { p.displayClientMessage(Component.literal(unavailable(p, e)).withStyle(ChatFormatting.RED), true); return; }
        mine(p).putBoolean(e.id, on);
        p.displayClientMessage(Component.literal(e.title + (on ? (e.kind == Kind.PASSIVE ? " - on" : " - enabled") : (e.kind == Kind.PASSIVE ? " - off" : " - disabled")))
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        if (!on && e == Entry.NETSU && com.xenowarriors.mikiko.Netsu.active(p)) com.xenowarriors.mikiko.Netsu.stop(p);
    }

    /** Why it can't be had yet (a skill not unlocked / a locked season), or null. */
    public static String unavailable(ServerPlayer p, Entry e) {
        int modAt = com.xenowarriors.you.YouPaths.modLevel(e);                  // (a Modified Human gets one mod every 10 levels from 20)
        if (modAt > 0 && !p.isCreative() && com.xenowarriors.stats.Stats.characterLevel(p) < modAt) return "Unlocks at Character Level " + modAt;
        Skill s = skill(p, e);
        if (s == null) return null;
        for (SkillPool.Entry en : SkillPool.of(p)) if (en.skill().getId().equals(s.getId())) {
            if (com.xenowarriors.character.Seasons.highest(p.server) < en.season()) return "Season " + en.season() + " content";
            if (!SkillPool.unlocked(en, com.xenowarriors.stats.Stats.characterLevel(p), p.isCreative())) return "Unlocks at Character Level " + en.unlockLevel();
        }
        return null;
    }

    /** The skill behind a buff / passive (null for a transformation). */
    static Skill skill(Player p, Entry e) {
        String id = e.kind == Kind.TRANSFORM && e != Entry.LIVING_WEAPON ? null : e.id;
        if (id == null) return null;
        for (SkillPool.Entry en : SkillPool.of(p)) if (en.skill().getId().equals(id)) return en.skill();
        return null;
    }

    public static boolean active(ServerPlayer p, Entry e) {
        return switch (e) {
            case FOX_KING -> com.xenowarriors.mikiko.FoxKing.active(p);
            case DEMONS_REMNANTS -> com.xenowarriors.rhalix.DemonsRemnants.active(p);
            case DEVIL_UNLEASHED, PERSEPHONE -> com.xenowarriors.skill.UltForms.activeNow(p);
            case NETSU -> com.xenowarriors.mikiko.Netsu.active(p);
            case LIVING_WEAPON -> p.getPersistentData().getLong(com.xenowarriors.skill.ultima.LivingWeaponSkill.UNTIL) > p.level().getGameTime();
            case LUNAR_MIND -> enabled(p, e) && com.xenowarriors.skill.sp.MaxSkills.night(p);
            case SCENT_TRACK -> Passives.tracking(p);
            default -> enabled(p, e);
        };
    }

    /** Mastery 0-100 (transformations), or the skill's progress to its next level (buffs, passives). */
    public static float mastery(ServerPlayer p, Entry e) {
        return switch (e) {
            case FOX_KING -> com.xenowarriors.mikiko.FoxKing.mastery(p);
            case DEMONS_REMNANTS -> FormMastery.get(p, FormMastery.DEMON);
            case DEVIL_UNLEASHED -> FormMastery.get(p, FormMastery.DEVIL);
            case PERSEPHONE -> FormMastery.get(p, FormMastery.PERSEPHONE);
            case LIVING_WEAPON -> FormMastery.get(p, FormMastery.LIVING);
            default -> { Skill s = skill(p, e); yield s == null ? 0F : com.xenowarriors.skill.SkillLevels.progress(p, s.getId()) * 100F; }
        };
    }

    /** Level: a buff's / passive's ability level; a transformation's mastery tier (1, 2 at 25, 3 at 50, 4 at 75, 5 mastered). */
    public static int level(ServerPlayer p, Entry e) {
        var apl = com.xenowarriors.apl.Apl.Ability.byId(e.id);
        if (apl != null) return com.xenowarriors.apl.Apl.rank(p, apl);
        if (e.kind == Kind.TRANSFORM) { float m = mastery(p, e); return m >= 100 ? 5 : m >= 75 ? 4 : m >= 50 ? 3 : m >= 25 ? 2 : 1; }
        Skill s = skill(p, e);
        return s == null ? 1 : com.xenowarriors.skill.SkillLevels.abilityLevel(p, s.getId());   // (tier and level together: Tier 2 Lv 3 = 13)
    }

    /** The transformation this character's transform key uses (null: none of these). */
    public static Entry transformOf(Player p) {
        for (Entry e : of(p)) if (e.kind == Kind.TRANSFORM) return e;
        return null;
    }

    /** The transform key: refused (with a message) when that transformation is switched off. */
    public static boolean transformAllowed(ServerPlayer p) {
        Entry e = transformOf(p);
        if (e == null || enabled(p, e)) return true;
        p.displayClientMessage(Component.literal(e.title + " is turned off (Character Customization > Passives)").withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    /** From the toggle wheel: transform, light / put out a buff, or switch a passive on / off. */
    public static void use(ServerPlayer p, Entry e) {
        if (!of(p).contains(e)) return;
        switch (e.kind) {
            case TRANSFORM -> {
                if (!enabled(p, e)) { transformAllowed(p); return; }
                com.xenowarriors.network.FoxKingTogglePacket.press(p);              // (same as tapping the transform key)
            }
            case BUFF -> {
                if (!enabled(p, e)) { p.displayClientMessage(Component.literal(e.title + " is turned off (Character Customization > Passives)").withStyle(ChatFormatting.GRAY), true); return; }
                String why = unavailable(p, e);
                if (why != null) { p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), true); return; }
                Skill s = skill(p, e);
                if (s != null) SkillManager.useFree(p, s, 1F);                       // (lit again: puts it out - its recast)
            }
            case PASSIVE, AUTO -> setEnabled(p, e, !enabled(p, e));
            case TRACK -> {
                if (!enabled(p, e)) { p.displayClientMessage(Component.literal(e.title + " is turned off (Character Customization > Passives)").withStyle(ChatFormatting.GRAY), true); return; }
                String why = unavailable(p, e);
                if (why != null) { p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), true); return; }
                Passives.openScent(p);
            }
        }
        send(p);
    }

    // ------------------------------------------------------------------ the right+left click skill

    public static String rlSkill(Player p) {
        String id = mine(p).getString(RL);
        List<String> ok = rlChoices(ModCapabilities.getType(p));
        if (ok.isEmpty()) return "";
        return ok.contains(id) ? id : id.equals("-") ? "" : ok.get(0);           // (the first one by default; "-" = none)
    }

    public static void setRl(ServerPlayer p, String id) {
        if (!id.isEmpty() && !rlChoices(ModCapabilities.getType(p)).contains(id)) return;
        mine(p).putString(RL, id.isEmpty() ? "-" : id);
        send(p);
    }

    /** Hold right click, tap left click: the right+left click skill (stops a block). */
    public static void useRl(ServerPlayer p) {
        String id = rlSkill(p);
        if (id.isEmpty()) return;
        if (com.xenowarriors.item.Guns.isGun(p.getMainHandItem()) || com.xenowarriors.item.Guns.isGun(p.getOffhandItem())) return;   // (a gun: right click aims, left click shoots)
        Skill s = null;
        for (SkillPool.Entry en : SkillPool.of(p)) if (en.skill().getId().equals(id)) {
            if (com.xenowarriors.character.Seasons.highest(p.server) < en.season()) { p.displayClientMessage(Component.literal("Season " + en.season() + " content is locked").withStyle(ChatFormatting.RED), true); return; }
            if (!SkillPool.unlocked(en, com.xenowarriors.stats.Stats.characterLevel(p), p.isCreative())) { p.displayClientMessage(Component.literal("Unlocks at Character Level " + en.unlockLevel()).withStyle(ChatFormatting.RED), true); return; }
            s = en.skill();
        }
        if (s == null) return;
        if (p.isUsingItem()) p.stopUsingItem();                                  // (no more blocking)
        SkillManager.useFree(p, s, RL_COOLDOWN);
    }

    // ------------------------------------------------------------------ what the screens get

    public static void send(ServerPlayer p) {
        if (p instanceof FakePlayer) return;
        List<TogglesPacket.Row> rows = new ArrayList<>();
        for (Entry e : of(p)) {
            String why = unavailable(p, e);
            rows.add(new TogglesPacket.Row(e.id, e.title, e.kind.ordinal(), e.color, why == null ? "" : why, enabled(p, e), active(p, e), level(p, e), mastery(p, e)));
        }
        List<TogglesPacket.Rl> rl = new ArrayList<>();
        for (String id : rlChoices(ModCapabilities.getType(p))) {
            String why = "";
            int cd = 0;
            for (SkillPool.Entry en : SkillPool.of(p)) if (en.skill().getId().equals(id)) {
                if (com.xenowarriors.character.Seasons.highest(p.server) < en.season()) why = "Season " + en.season() + " content";
                else if (!SkillPool.unlocked(en, com.xenowarriors.stats.Stats.characterLevel(p), p.isCreative())) why = "Unlocks at Lv " + en.unlockLevel();
                cd = Math.round(com.xenowarriors.skill.SkillLevels.cooldownFor(p, id, en.skill().getCooldownTicks()) * RL_COOLDOWN);
                rl.add(new TogglesPacket.Rl(id, en.skill().getName().getString(), why, cd, com.xenowarriors.skill.SkillLevels.costFor(p, id, en.skill().getKarmaCost(p))));
            }
        }
        ModNetwork.sendToPlayer(new TogglesPacket(rows, rl, rlSkill(p)), p);
    }

    // ------------------------------------------------------------------ True Empathy, the passive

    public static final int EMPATHY_GAP = 25 * 20;
    private static final String EMPATHY_NEXT = "xw_empathy_passive_next";

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p instanceof FakePlayer || p.tickCount % 10 != 5) return;
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO || !enabled(p, Entry.TRUE_EMPATHY)) return;
        if (unavailable(p, Entry.TRUE_EMPATHY) != null) { mine(p).putBoolean(Entry.TRUE_EMPATHY.id, false); return; }
        if (!p.isAlive() || com.xenowarriors.party.Duels.cantAct(p)) return;
        long now = p.level().getGameTime();
        if (now < p.getPersistentData().getLong(EMPATHY_NEXT)) return;
        Object[] seen = SkillManager.WITNESSED.get(p.getUUID());
        boolean copy = seen != null && now - (long) seen[1] < 20 * 20;
        boolean threat = !copy && !p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(6),
                m -> m.isAlive() && m.getTarget() == p).isEmpty();
        if (!copy && !threat) return;
        Skill s = skill(p, Entry.TRUE_EMPATHY);
        if (s == null) return;
        int cost = com.xenowarriors.skill.SkillLevels.costFor(p, s.getId(), s.getKarmaCost(p));
        if (!com.xenowarriors.karma.Karma.has(p, cost)) return;                  // (quietly waits for Karma)
        if (!s.use(p)) return;
        if (!p.isCreative()) com.xenowarriors.karma.Karma.add(p, -cost);
        com.xenowarriors.skill.SkillLevels.used(p, s);
        p.getPersistentData().putLong(EMPATHY_NEXT, now + EMPATHY_GAP);
        if (com.xenowarriors.skill.mikiko.TrueEmpathySkill.overused(p)) {        // pushed too far: it switches itself off
            mine(p).putBoolean(Entry.TRUE_EMPATHY.id, false);
            p.displayClientMessage(Component.literal("True Empathy overused - it switched itself off. Let him rest a while.").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            send(p);
        }
    }
}
