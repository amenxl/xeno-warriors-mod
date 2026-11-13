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
        if (main.isEmpty()) {
            CharacterType t = character(p);
            return t == null ? null : FISTS.get(t);
        }
        var id = ForgeRegistries.ITEMS.getKey(main.getItem());
        return id != null && id.getNamespace().equals(com.xenowarriors.XenoWarriors.MOD_ID) ? WEAPONS.get(id.getPath()) : null;
    }

    /** (from the mixin on Epic Fight's getAutoAttackMotion) our combo in place of the original, or the original. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List override(PlayerPatch<?> patch, List original) {
        if (!loaded() || patch == null || original == null || original.size() < 3) return original;
        try {
            if (!(patch.getOriginal() instanceof Player p)) return original;
            String[] keys = movesFor(p);
            if (keys == null) return original;
            List out = new ArrayList();
            for (String k : keys) { var m = move(k); if (m != null) out.add(m); }
            if (out.isEmpty()) return original;
            out.addAll(original.subList(original.size() - 2, original.size()));      // Epic Fight's dash and air attacks
            swaps++;
            return out;
        } catch (Throwable t) {
            return original;
        }
    }

    private static CharacterType character(Player p) {
        CharacterType t;
        if (p.level().isClientSide) t = net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> EfClientCheck.type(p));
        else t = ModCapabilities.getType(p);
        return t;
    }
}
