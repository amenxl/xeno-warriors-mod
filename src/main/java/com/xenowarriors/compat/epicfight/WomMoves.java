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
    public static final String[] SWIFT = {C + "enderblaster_onehand_auto_1", C + "enderblaster_onehand_auto_2", C + "enderblaster_onehand_auto_3", C + "enderblaster_onehand_auto_4"};

    public static final Map<CharacterType, String[]> FISTS = new java.util.EnumMap<>(CharacterType.class);
    /** Our weapons (item id path) -> their moves. */
    public static final Map<String, String[]> WEAPONS = new java.util.LinkedHashMap<>();
    static {
        for (CharacterType t : new CharacterType[]{CharacterType.KAERU, CharacterType.LEXY, CharacterType.MAX, CharacterType.SAM, CharacterType.STEVIE, CharacterType.GARRY}) FISTS.put(t, STREET);
        for (CharacterType t : new CharacterType[]{CharacterType.ULTIMA, CharacterType.AIONI, CharacterType.KILL}) FISTS.put(t, BEAST);
        for (CharacterType t : new CharacterType[]{CharacterType.KYRIE, CharacterType.TECHNICA, CharacterType.YURI, CharacterType.ZANE, CharacterType.LOGAN, CharacterType.KEITA}) FISTS.put(t, SWIFT);
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
    public static final java.util.Set<String> MISSING = new java.util.TreeSet<>();
    private static final Map<String, AnimationManager.AnimationAccessor<? extends AttackAnimation>> CACHE = new HashMap<>();

    @SuppressWarnings("unchecked")
    private static AnimationManager.AnimationAccessor<? extends AttackAnimation> move(String key) {
        if (CACHE.containsKey(key)) return CACHE.get(key);
        AnimationManager.AnimationAccessor<? extends AttackAnimation> acc = null;
        try {
            AnimationManager.AnimationAccessor<?> a = AnimationManager.byKey(key);
            if (a != null && a.get() instanceof AttackAnimation) acc = (AnimationManager.AnimationAccessor<? extends AttackAnimation>) a;
        } catch (Throwable ignored) {}
        if (acc == null) MISSING.add(key);
        CACHE.put(key, acc);
        return acc;
    }

    /** The moves for this player's held weapon / bare hands, or null. */
    public static String[] movesFor(Player p) {
        ItemStack main = p.getMainHandItem();
        String[] keys;
        if (main.isEmpty()) {
            CharacterType t = character(p);
            keys = t == null ? null : FISTS.get(t);
        } else {
            var id = ForgeRegistries.ITEMS.getKey(main.getItem());
            keys = id != null && id.getNamespace().equals(com.xenowarriors.XenoWarriors.MOD_ID) ? WEAPONS.get(id.getPath()) : null;
        }
        return keys != null && modOf(keys) ? keys : null;
    }

    /** The mod these moves come from is installed (Weapons of Miracles, or Epic Fight: Dawn Day). */
    private static boolean modOf(String[] keys) {
        String ns = keys[0].substring(0, keys[0].indexOf(':'));
        return ModList.get() != null && ModList.get().isLoaded(ns);
    }

    /** Moves from another Epic Fight mod (their animation events may expect that mod's own weapon - see guard). */
    static boolean foreign(String namespace) { return "wom".equals(namespace) || "epicfight_dd".equals(namespace); }

    /** (from the mixin on Epic Fight's getAutoAttackMotion) our combo in place of the original, or the original. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List override(PlayerPatch<?> patch, List original) {
        if (patch == null || original == null || original.size() < 3) return original;
        try {
            Player p = patch.getOriginal();
            if (p == null) return original;
            List own = ownMoves(p);
            if (own != null) {                                                        // our own characters' moves, rebuilt for Epic Fight
                List out = new ArrayList(own);
                out.addAll(original.subList(original.size() - 2, original.size()));
                return out;
            }
            String[] keys = movesFor(p);
            if (keys == null) return original;
            List out = new ArrayList();
            for (String k : keys) { var m = move(k); if (m != null) out.add(m); }
            if (out.isEmpty()) return original;
            out.addAll(original.subList(original.size() - 2, original.size()));      // Epic Fight's dash and air attacks
            swaps++;
            SWAPPED.put(p, Boolean.TRUE);
            return out;
        } catch (Throwable t) {
            return original;
        }
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
        if (!p.getMainHandItem().isEmpty()) return null;
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

    /** Rhalix's bare-hand ground combo: his eight hits, baked from his animations. */
    public static final String[] RHALIX_COMBO = {"hit0", "hit1", "hit2", "hit3", "hit4", "hit5", "hit6", "hit7"};

    private static CharacterType character(Player p) {
        CharacterType t;
        if (p.level().isClientSide) t = net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> EfClientCheck.type(p));
        else t = ModCapabilities.getType(p);
        return t;
    }
}
