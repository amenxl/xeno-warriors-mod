package com.xenowarriors.compat.epicfight;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * With Weapons of Miracles (WOM) installed: our characters' Epic Fight combos borrow its attack animations.
 * We only call its moves by name (like Epic Fight: Fighting Styles does) - none of its files are in this mod. Without
 * WOM nothing changes. Epic Fight's own dash and air attacks (the last two of a combo) are kept.
 * <ul>
 *   <li>Bare hands, per character (Mikiko and Rhalix keep their own fighting moves).</li>
 *   <li>Our weapons, for whoever holds them.</li>
 * </ul>
 */
public final class WomMoves {
    private WomMoves() {}

    private static final String C = "wom:biped/combat/", S = "wom:biped/skill/";

    /** Street fighting: quick jabs, a berserk hook, a claw strike (Kaeru, Lexy, Max, Sam, Stevie, Garry). */
    public static final String[] STREET = {C + "enderblaster_onehand_auto_1", S + "torment_berserk_auto_2", C + "enderblaster_onehand_auto_2", C + "agony_clawstrike", C + "enderblaster_onehand_auto_3"};
    /** Beast: wild claws, a jump kick and a heavy finisher (Ultima, Ai-oni, Kill). */
    public static final String[] BEAST = {C + "agony_auto_1", S + "torment_berserk_auto_2", C + "agony_auto_4", C + "enderblaster_onehand_jumpkick", C + "enderblaster_twohand_tishnaw"};
    /** Swift: clean, fast strikes (Kyrie, Technica, Yuri, Zane, Logan, Keita). */
    /**
     * Kaeru's bare hands: "The One chosen by the Sparks Of Black" (Epic Fight: Fighting Styles' Kokusen gauntlets -
     * the user's pick), its combo with its own dash and air attacks (see TAILS). Built from Weapons of Miracles' moves.
     */
    public static final String[] SPARKS = {C + "agony_auto_1", S + "torment_berserk_auto_2", C + "katana_auto_3", C + "agony_auto_4",
            "epicfight:biped/combat/dagger_auto2", C + "enderblaster_onehand_auto_3", C + "enderblaster_onehand_jumpkick"};
    /** A style's own dash and air attacks, in place of Epic Fight's. */
    public static final Map<String[], String[]> TAILS = new java.util.IdentityHashMap<>();
    static { TAILS.put(SPARKS, new String[]{S + "torment_berserk_dash", C + "enderblaster_twohand_tishnaw"}); }
    public static final String[] SWIFT = {C + "enderblaster_onehand_auto_1", C + "enderblaster_onehand_auto_2", C + "enderblaster_onehand_auto_3", C + "enderblaster_onehand_auto_4"};
    /** Mikiko's bare hands (the user's pick for now): Epic Fight's own glove punches. */
    public static final String[] GLOVES = {"epicfightx:biped/combat/fist_auto1", "epicfightx:biped/combat/fist_auto2", "epicfightx:biped/combat/fist_auto3",
            "epicfightx:biped/combat/fist_auto4", "epicfightx:biped/combat/fist_auto5"};   // (the glove's own five punches, at the glove's speed - the user: bare hands were slower)
    /** Rhalix's bare hands (the user's pick for now): the Ender Blaster's strikes - no shooting (right click guards). */
    public static final String[] ENDER = {C + "enderblaster_onehand_auto_1", C + "enderblaster_onehand_auto_2", C + "enderblaster_onehand_auto_3", C + "enderblaster_onehand_auto_4"};
    /** Kyrie's bare hands: Epic Fight: Dawn Day's Iron Fist, its first four hits, with its own dash and air attacks. */
    public static final String[] IRON_FIST = {"epicfight_dd:biped/combat/iron_fist_auto1", "epicfight_dd:biped/combat/iron_fist_auto2",
            "epicfight_dd:biped/combat/iron_fist_auto3", "epicfight_dd:biped/combat/iron_fist_auto4"};
    /** The Hekublade: EpicFight Nightfall's warrior broadblade (its idles stay Mikiko's own). */
    public static final String[] BROADBLADE = {"efn:biped/broadblade/broadblade_auto1", "efn:biped/broadblade/broadblade_auto2", "efn:biped/broadblade/broadblade_auto3",
            "efn:biped/broadblade/broadblade_auto4", "efn:biped/broadblade/broadblade_auto5"};
    /** Rhalix's Wire Sword: Weapons of Harmony's Arbiter's Blade. */
    public static final String[] ARBITER = {"woh:biped/combat/arbiters_blade/arbiters_blade_auto_1", "woh:biped/combat/arbiters_blade/arbiters_blade_auto_2",
            "woh:biped/combat/arbiters_blade/arbiters_blade_auto_3", "woh:biped/combat/arbiters_blade/arbiters_blade_auto_4"};
    static {
        TAILS.put(ENDER, new String[]{C + "enderblaster_onehand_dash", C + "enderblaster_onehand_jumpkick"});
        TAILS.put(IRON_FIST, new String[]{"epicfight_dd:biped/combat/iron_fist_dash", "epicfight_dd:biped/combat/iron_fist_airslash"});
        TAILS.put(GLOVES, new String[]{"epicfightx:biped/combat/fist_dash", "epicfightx:biped/combat/fist_airslash"});
        TAILS.put(BROADBLADE, new String[]{"efn:biped/broadblade/broadblade_dash_slash", "efn:biped/broadblade/broadblade_air_slash"});
        TAILS.put(ARBITER, new String[]{"woh:biped/combat/arbiters_blade/arbiters_blade_auto_5", "woh:biped/combat/arbiters_blade/arbiters_blade_airslash"});
    }

    /**
     * Mikiko's and Rhalix's own fighting styles (their own punches / Bajiquan and Rhalix's eight-hit string, baked from
     * their animations) - kept, but switched off for now: the user picked Epic Fight's glove punches for Mikiko and the
     * Ender Blaster for Rhalix. Their own stance and idles near enemies are still theirs. True brings the old ones back.
     */
    public static boolean CLASSIC_STYLES = false;

    public static final Map<CharacterType, String[]> FISTS = new java.util.EnumMap<>(CharacterType.class);
    /** Our weapons (item id path) -> their moves. */
    public static final Map<String, String[]> WEAPONS = new java.util.LinkedHashMap<>();
    static {
        for (CharacterType t : new CharacterType[]{CharacterType.LEXY, CharacterType.MAX, CharacterType.SAM, CharacterType.STEVIE, CharacterType.GARRY}) FISTS.put(t, STREET);
        for (CharacterType t : new CharacterType[]{CharacterType.ULTIMA, CharacterType.AIONI, CharacterType.KILL}) FISTS.put(t, BEAST);
        FISTS.put(CharacterType.KAERU, SPARKS);
        for (CharacterType t : new CharacterType[]{CharacterType.TECHNICA, CharacterType.YURI, CharacterType.ZANE, CharacterType.LOGAN, CharacterType.KEITA}) FISTS.put(t, SWIFT);
        FISTS.put(CharacterType.KYRIE, IRON_FIST);
        // the November 2026 seven: Ava the cage fighter brawls street style; Kaitlyn and Zack fight like the demons they
        // are; Kyle, Chase, Senju and Persephone with quick, clean strikes
        FISTS.put(CharacterType.AVA, STREET);
        FISTS.put(CharacterType.FOX_GIRL, SWIFT);
        FISTS.put(CharacterType.ATHREA, SWIFT); FISTS.put(CharacterType.RANREA, SWIFT);                      // (fox martial arts: quick, clean strikes)
        for (CharacterType t : new CharacterType[]{CharacterType.KAITLYN, CharacterType.ZACK}) FISTS.put(t, BEAST);
        for (CharacterType t : new CharacterType[]{CharacterType.KYLE, CharacterType.CHASE, CharacterType.SENJU, CharacterType.PERSEPHONE}) FISTS.put(t, SWIFT);
        FISTS.put(CharacterType.MIKIKO, GLOVES);
        FISTS.put(CharacterType.RHALIX, ENDER);
        FISTS.put(CharacterType.YOU, STREET);                                          // (your own warriors: by race - fistsFor)
        WEAPONS.put("hekublade", BROADBLADE);
        WEAPONS.put("wire_sword", ARBITER);
        WEAPONS.put("logan_blade", new String[]{C + "herrscher_auto_1", C + "herrscher_auto_2", C + "herrscher_auto_3"});             // the knight's sword
        WEAPONS.put("keita_blade", new String[]{C + "katana_sheathed_auto_1", C + "katana_sheathed_auto_2", C + "katana_sheathed_auto_3"});   // lazy quick-draw cuts
        WEAPONS.put("senju_blade", new String[]{C + "katana_auto_1", C + "katana_auto_2", C + "katana_auto_3"});
        WEAPONS.put("zane_broom", new String[]{C + "staff_auto_1", C + "staff_auto_2", C + "staff_auto_3"});
        WEAPONS.put("sam_bat", new String[]{C + "torment_auto_1", C + "torment_auto_2", C + "torment_auto_3", C + "torment_auto_4"});
        WEAPONS.put("max_knife", new String[]{C + "moonless_auto_1", C + "moonless_auto_2", C + "moonless_auto_3"});
        // Epic Fight: Dawn Day (when installed): Kyrie's rapier fights in the Florett style, Lexy's claws with the Hook Claws
        WEAPONS.put("kyrie_rapier", new String[]{"epicfight_dd:biped/combat/florett_auto1", "epicfight_dd:biped/combat/florett_auto2",
                "epicfight_dd:biped/combat/florett_auto3", "epicfight_dd:biped/combat/florett_auto4"});
        WEAPONS.put("lexy_claws", new String[]{"epicfight_dd:biped/combat/hookclaws_auto1", "epicfight_dd:biped/combat/hookclaws_auto2",
                "epicfight_dd:biped/combat/hookclaws_auto3", "epicfight_dd:biped/combat/hookclaws_auto4"});
        WEAPONS.put("demonite_sword", new String[]{C + "ruine_auto_1", C + "ruine_auto_2", C + "ruine_auto_3", C + "ruine_auto_4"});
    }

    private static Boolean loaded;
    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("wom");
        return loaded;
    }

    /** (Tests) combos swapped, moves that wouldn't resolve. */
    public static int swaps;
    public static final java.util.Set<String> MISSING = java.util.Collections.synchronizedSortedSet(new java.util.TreeSet<>());
    // (both the client and the integrated server's thread look moves up: a plain HashMap could break under that)
    private static final Map<String, AnimationManager.AnimationAccessor<? extends AttackAnimation>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    /** (Tests / logs) why a player's combo last fell back to Epic Fight's own. */
    public static volatile String lastFallback = "-";

    @SuppressWarnings("unchecked")
    private static AnimationManager.AnimationAccessor<? extends AttackAnimation> move(String key) {
        var cached = CACHE.get(key);
        if (cached != null) {
            try { if (cached.get() instanceof AttackAnimation) return cached; } catch (Throwable ignored) {}
            CACHE.remove(key);                                                     // (animations reloaded since: look it up again)
        }
        AnimationManager.AnimationAccessor<? extends AttackAnimation> acc = null;
        try {
            AnimationManager.AnimationAccessor<?> a = AnimationManager.byKey(key);
            if (a != null && a.get() instanceof AttackAnimation) acc = (AnimationManager.AnimationAccessor<? extends AttackAnimation>) a;
        } catch (Throwable ignored) {}
        // a move not found isn't remembered: asked before Weapons of Miracles' animations were loaded (or while another
        // mod - Extended Datapacks - reloads them), it used to stay missing all session and the character fought with
        // Epic Fight's plain fists (the user's Rhalix: no Ender Blaster strikes)
        if (acc == null) {
            if (MISSING.add(key)) com.xenowarriors.XenoWarriors.LOGGER.info("[Xeno Warriors] combo move not loaded (yet): {}", key);
            return null;
        }
        MISSING.remove(key);
        CACHE.put(key, acc);
        return acc;
    }

    /** Animations / datapacks reloaded: look the moves up again. */
    public static void clearCache() { CACHE.clear(); }

    /** (server side) /reload, a world starting: look the moves up again. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.xenowarriors.XenoWarriors.MOD_ID)
    public static final class Reloads {
        private Reloads() {}
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onReload(net.minecraftforge.event.AddReloadListenerEvent e) { clearCache(); }
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onStart(net.minecraftforge.event.server.ServerStartedEvent e) { clearCache(); }
    }

    /** The moves for this player's held weapon / bare hands, or null. */
    public static String[] movesFor(Player p) {
        ItemStack main = p.getMainHandItem();
        String[] keys;
        if (main.isEmpty()) {
            CharacterType t = character(p);
            keys = t == null ? null : fistsFor(p, t);
        } else {
            var id = ForgeRegistries.ITEMS.getKey(main.getItem());
            keys = id != null && id.getNamespace().equals(com.xenowarriors.XenoWarriors.MOD_ID) ? WEAPONS.get(id.getPath()) : null;
        }
        return keys != null && modOf(keys) ? keys : null;
    }

    /**
     * A character's bare-hand moves. Your own (You) warriors had none - Epic Fight's plain fist swings were hidden and
     * nothing played in their place (the user: no fighting animations with their hands). Now by race: demons and
     * devils brawl like beasts, Skylanders, angels and fox kin strike quick and clean, everyone else fights street style.
     * (Bots keep street style - their combo timing is worked out from the character alone.)
     */
    public static String[] fistsFor(Player p, CharacterType t) {
        if (t != CharacterType.YOU || p == null || p.getClass().getSimpleName().equals("BotAvatar")) return FISTS.get(t);
        try {
            com.xenowarriors.you.YouSlot s = com.xenowarriors.you.You.slot(p);
            if (s == null) return STREET;
            return switch (s.shown()) {
                case DEMON, DEVIL, ARTIFICIAL_DEMON, DEMON_SKYLANDER -> BEAST;
                case SKYLANDER, ARTIFICIAL_SKYLANDER, ANGEL, FOX_KIN -> SWIFT;
                default -> STREET;
            };
        } catch (Throwable e) {
            return STREET;
        }
    }

    /** The mod these moves come from is installed (Weapons of Miracles, or Epic Fight: Dawn Day). */
    private static boolean modOf(String[] keys) {
        String ns = keys[0].substring(0, keys[0].indexOf(':'));
        return ModList.get() != null && ModList.get().isLoaded(ns);
    }

    /** Moves from another Epic Fight mod (their animation events may expect that mod's own weapon - see guard). */
    static boolean foreign(String namespace) { return "epicfightx".equals(namespace) || "wom".equals(namespace) || "epicfight_dd".equals(namespace) || "woh".equals(namespace) || "efn".equals(namespace); }

    /** (from the mixin on Epic Fight's getAutoAttackMotion) our combo in place of the original, or the original. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List override(PlayerPatch<?> patch, List original) {
        if (patch == null) return original;
        try {
            Player p = patch.getOriginal();
            if (p == null) return original;
            List soul = soulrender(patch, p, original);
            if (soul != null) return soul;
            List own = ownMoves(p);
            if (own != null) {                                                        // our own characters' moves, rebuilt for Epic Fight
                List out = new ArrayList(own);
                out.addAll(tail(original, null));
                return out;
            }
            String[] keys = movesFor(p);
            if (keys == null) {
                if (p.getMainHandItem().isEmpty()) lastFallback = (p.level().isClientSide ? "client" : "server") + ": no moves for " + character(p);
                return original;
            }
            List out = new ArrayList();
            for (String k : keys) { var m = move(k); if (m != null) out.add(m); }
            if (out.isEmpty()) { lastFallback = (p.level().isClientSide ? "client" : "server") + ": none of " + keys[0] + "... loaded"; return original; }
            out.addAll(tail(original, keys));                                          // Epic Fight's dash and air attacks
            swaps++;
            SWAPPED.put(p, Boolean.TRUE);
            return out;
        } catch (Throwable t) {
            lastFallback = "error " + t;
            return original;
        }
    }

    /**
     * The dash and air attacks at the end of a combo: the original's, or - when the weapon had no combo for the way
     * it's held (Epic Fight hands back none, and other mods then crash on it: the user's crash with Kyrie's rapier and
     * Weapons of Harmony) - the style's own (Dawn Day's) or Epic Fight's fist ones.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List tail(List original, String[] keys) {
        List out = new ArrayList();
        String[] own = keys == null ? null : TAILS.get(keys);
        if (own != null) {
            for (String k : own) { var m = move(k); if (m != null) out.add(m); }
            if (out.size() == 2) return out;
            out.clear();
        }
        if (original != null && original.size() >= 3) return original.subList(original.size() - 2, original.size());
        String base = keys != null && keys[0].startsWith("epicfight_dd:biped/combat/hookclaws") ? "epicfight_dd:biped/combat/hookclaws_" : null;
        Object dash = base != null ? move(base + "dash") : null, air = base != null ? move(base + "airslash") : null;
        if (dash == null) dash = move("epicfight:biped/combat/fist_dash");
        if (air == null) air = move("epicfight:biped/combat/fist_airslash");
        if (dash != null) out.add(dash);
        if (air != null) out.add(air);
        return out;
    }

    /** (Tests) WOM animation events skipped because they threw, by animation. */
    private static final Map<Player, Boolean> SWAPPED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public static final Map<String, Integer> GUARDED = new java.util.TreeMap<>();

    /** A WOM animation's event threw while a player whose combo we swapped was playing it: skip it (true), or let it through. */
    public static boolean guard(Object patch, Object anim, RuntimeException e) {
        try {
            if (!(anim instanceof yesman.epicfight.api.asset.AssetAccessor<?> a) || a.registryName() == null || !foreign(a.registryName().getNamespace())) return false;
            if (!(patch instanceof yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch<?> lp) || !(lp.getOriginal() instanceof Player p) || (movesFor(p) == null && !SWAPPED.containsKey(p))) return false;
            GUARDED.merge(a.registryName().getPath(), 1, Integer::sum);
            if (GUARDED.size() < 64 && GUARDED.get(a.registryName().getPath()) == 1)
                com.xenowarriors.XenoWarriors.LOGGER.info("[Xeno Warriors] skipped a Weapons of Miracles animation event that needs its own weapon ({}): {}", a.registryName(), String.valueOf(e));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Mikiko's bare-hand combo: his own punches and Bajiquan, baked from his animations (null if not him / not bare hands / not baked). */
    public static final String[] MIKIKO_COMBO = {"jab_r", "jab_l", "ding_zhou_r", "tiao_zhang_l", "shuang_zhang", "tie_shan_kao"};

    @SuppressWarnings({"rawtypes", "unchecked"})
    static List ownMoves(Player p) {
        if (!CLASSIC_STYLES || !p.getMainHandItem().isEmpty()) return null;
        CharacterType t = character(p);
        String[] keys = t == CharacterType.MIKIKO ? MIKIKO_COMBO : t == CharacterType.RHALIX ? RHALIX_COMBO : null;
        var map = t == CharacterType.MIKIKO ? EfAnimations.MIKIKO : EfAnimations.RHALIX;
        if (keys == null) return null;
        List out = new ArrayList();
        for (String k : keys) {
            var acc = map.get(k);
            if (acc == null) return null;
            out.add(acc);
        }
        return out;
    }

    /**
     * Simply Swords' Soulrender (Epicfied): holding attack sends it into its ascended style - which fights with the first
     * four hits of Rhalix's own string (the user's pick), then the ascended style's own dash and air attacks.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static List soulrender(PlayerPatch<?> patch, Player p, List original) {
        var id = ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem());
        if (id == null || !"simplyswords".equals(id.getNamespace()) || !"soulrender".equals(id.getPath())) return null;
        try {
            var cap = patch.getHoldingItemCapability(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (cap == null || cap.getStyle(patch) != yesman.epicfight.world.capabilities.item.CapabilityItem.Styles.OCHS) return null;
        } catch (Throwable t) { return null; }
        List out = new ArrayList();
        for (int i = 0; i < 4; i++) {
            var acc = EfAnimations.RHALIX.get(RHALIX_COMBO[i]);
            if (acc == null) return null;
            out.add(acc);
        }
        out.addAll(tail(original, null));
        soulrenderSwaps++;
        return out;
    }
    public static int soulrenderSwaps;

    /** Rhalix's bare-hand ground combo: his eight hits, baked from his animations. */
    public static final String[] RHALIX_COMBO = {"hit0", "hit1", "hit2", "hit3", "hit4", "hit5", "hit6", "hit7"};

    /**
     * A bot's timing for one hit of its borrowed combo, in ticks: {when it lands, when the next can start} - from the
     * move's own attack phases (played at its own speed), so the bot's string keeps pace with the move its body plays
     * instead of cutting each one off a few ticks in. Null: no borrowed move (our own combo timing stays).
     */
    public static int[] botTiming(CharacterType t, ItemStack held, int hit) {
        try {
            if (t == null || t == CharacterType.MIKIKO && held.isEmpty()) return null;   // (Mikiko's glove punches: fast, our timing fits)
            String[] keys;
            if (held.isEmpty()) keys = FISTS.get(t);
            else {
                var id = ForgeRegistries.ITEMS.getKey(held.getItem());
                keys = id != null && id.getNamespace().equals(com.xenowarriors.XenoWarriors.MOD_ID) ? WEAPONS.get(id.getPath()) : null;
            }
            if (keys == null || !modOf(keys)) return null;
            var acc = move(keys[Math.floorMod(hit, keys.length)]);
            AttackAnimation a = acc == null ? null : acc.get();
            if (a == null || a.phases == null || a.phases.length == 0) return null;
            int strike = Math.max(2, Math.round(a.phases[0].contact * 20F));
            int next = Math.max(strike + 2, Math.round(a.phases[a.phases.length - 1].recovery * 20F));
            return new int[]{strike, Math.min(next, 40)};
        } catch (Throwable e) {
            return null;
        }
    }

    /** How many hits a bot's borrowed string has (-1: not borrowed). */
    public static int botComboLength(CharacterType t, ItemStack held) {
        if (t == null) return -1;
        String[] keys;
        if (held.isEmpty()) keys = FISTS.get(t);
        else {
            var id = ForgeRegistries.ITEMS.getKey(held.getItem());
            keys = id != null && id.getNamespace().equals(com.xenowarriors.XenoWarriors.MOD_ID) ? WEAPONS.get(id.getPath()) : null;
        }
        return keys == null || !modOf(keys) ? -1 : keys.length;
    }

    /** (Tests) attack moves played at their own speed. */
    public static int naturalSpeeds;

    /**
     * A borrowed bare-hand move plays at its own speed (as on the weapon it comes from). Epic Fight scales an attack by
     * the holder's attack speed against the move's: our bare hands' attack speed is 1.4 (character/FistTempo, the
     * pace of our own combos), so Weapons of Miracles' Ender Blaster strikes (made for 4.0) played at a third of their
     * speed - 3 to 5 seconds a hit (Rhalix and every Street / Swift / Beast style). Mikiko's glove punches keep the
     * glove's speed (the user's pick). The least speed it plays at (a move already faster stays faster - Max's knife,
     * the berserk hook); null: Epic Fight's own speed.
     */
    public static Float playSpeed(Object patch, Object anim) {
        try {
            if (!(patch instanceof PlayerPatch<?> pp) || !(anim instanceof yesman.epicfight.api.animation.types.StaticAnimation sa)) return null;
            Player p = pp.getOriginal();
            if (p == null) return null;
            String[] keys;
            ItemStack main = p.getMainHandItem();
            if (main.isEmpty()) {
                CharacterType t = character(p);
                if (t == null || t == CharacterType.MIKIKO) return null;
                keys = fistsFor(p, t);
            } else {                                                                   // our weapons' borrowed moves too (the Senju Blade's katana cuts were slow, Max's knife fast)
                var wid = ForgeRegistries.ITEMS.getKey(main.getItem());
                keys = wid != null && wid.getNamespace().equals(com.xenowarriors.XenoWarriors.MOD_ID) ? WEAPONS.get(wid.getPath()) : null;
            }
            if (keys == null || !modOf(keys)) return null;
            var acc = sa.getAccessor();
            String id = acc == null || acc.registryName() == null ? null : acc.registryName().toString();
            if (id == null) return null;
            boolean ours = java.util.Arrays.asList(keys).contains(id);
            String[] tail = TAILS.get(keys);
            if (!ours && tail != null) ours = java.util.Arrays.asList(tail).contains(id);
            if (!ours) return null;
            naturalSpeeds++;
            return 1F;
        } catch (Throwable e) {
            return null;
        }
    }

    private static CharacterType character(Player p) {
        CharacterType t;
        if (p.level().isClientSide) t = net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> EfClientCheck.type(p));
        else t = ModCapabilities.getType(p);
        return t;
    }
}
