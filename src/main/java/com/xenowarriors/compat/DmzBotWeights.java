package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.*;

/**
 * With DragonMineZ: bots train with DragonMineZ's weights (Turtle Shell, Workout Weights, Weighted Cape).
 * <ul>
 *   <li><b>Wearing them.</b> A bot wears the weight in its bag that's closest to its ideal training weight (bots have no
 *       Curios slot: one in its bag counts as worn). Its training TP - the TP it earns over time, from kills and from
 *       minigames - gets DragonMineZ's own weight bonus for that weight against its ideal weight (GravityLogic's
 *       {@code weightTpMultiplierForRatio} and ideal weight, from its own Dragon Ball character), so the right weight
 *       trains it faster, like it does you.</li>
 *   <li><b>Asking for them.</b> With no weights, when it passes a master who hands them out (Roshi: Turtle Shell, King Kai:
 *       Workout Weights, Piccolo: Weighted Cape), it walks over and asks ({@link DmzBotMasters}) - and gets one set to
 *       its ideal weight, as the master's menu gives you one.</li>
 * </ul>
 */
public final class DmzBotWeights {
    private DmzBotWeights() {}

    /** master -> weight item they hand out */
    static final Map<String, String> GIVES = Map.of("roshi", "dragonminez:weight_turtle_shell", "kingkai", "dragonminez:workout_weights",
            "piccolo", "dragonminez:weight_piccolo_cape");

    private static Boolean loaded;
    private static boolean broken;
    private static Class<?> weightItem;
    private static Method getWeight, setWeight, gravityCfg, ratioMult, idealOf;

    /** (Tests) */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    public static int given;

    private static void note(String s) {
        if (LOG.size() > 40) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz bot weights: {}", s);
    }

    static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (getWeight != null) return true;
        try {
            weightItem = Class.forName("com.dragonminez.common.init.item.WeightItem");
            getWeight = weightItem.getMethod("getWeight", ItemStack.class);
            setWeight = weightItem.getMethod("setWeight", ItemStack.class, int.class);
            Class<?> gl = Class.forName("com.dragonminez.server.util.GravityLogic");
            gravityCfg = gl.getDeclaredMethod("cfg");
            gravityCfg.setAccessible(true);
            Class<?> cfgClass = gravityCfg.getReturnType();
            ratioMult = gl.getDeclaredMethod("weightTpMultiplierForRatio", double.class, cfgClass);
            ratioMult.setAccessible(true);
            for (Method mm : gl.getDeclaredMethods())                                // (the ideal weight for a character: a lambda in getIdealWeight)
                if (mm.getName().startsWith("lambda$getIdealWeight$") && mm.getParameterCount() == 2) { idealOf = mm; mm.setAccessible(true); }
            return true;
        } catch (Throwable t) {
            broken = true; getWeight = null;
            XenoWarriors.LOGGER.warn("Bots won't train with DragonMineZ weights (its API changed?)", t);
            return false;
        }
    }

    static boolean isWeight(ItemStack s) { return !s.isEmpty() && weightItem != null && weightItem.isInstance(s.getItem()); }

    private static int weight(ItemStack s) { try { return ((Number) getWeight.invoke(null, s)).intValue(); } catch (Throwable t) { return 0; } }

    /** Its ideal training weight (DragonMineZ's, for its own character), or -1. */
    static int ideal(Object sd) {
        if (idealOf == null || sd == null) return -1;
        try { Object v = idealOf.invoke(null, gravityCfg.invoke(null), sd); return v instanceof Number n ? n.intValue() : -1; } catch (Throwable t) { return -1; }
    }

    /** The weight it wears: the one in its bag closest to its ideal weight (empty: none). */
    public static ItemStack worn(PartyBotEntity b, int ideal) {
        ItemStack best = ItemStack.EMPTY;
        int bestD = Integer.MAX_VALUE;
        var inv = b.inventory;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!isWeight(s)) continue;
            int d = ideal > 0 ? Math.abs(weight(s) - ideal) : -weight(s);
            if (d < bestD) { bestD = d; best = s; }
        }
        return best;
    }

    private static final Map<UUID, double[]> CACHE = new HashMap<>();   // bot -> {multiplier, game time}

    /** DragonMineZ's weight TP bonus for the weight it wears (1 = none). Worked out at most every 30 s. */
    public static double multiplier(PartyBotEntity b) {
        if (!ready()) return 1;
        long now = b.level().getGameTime();
        double[] c = CACHE.get(b.getUUID());
        if (c != null && now - (long) c[1] < 600 && now >= (long) c[1]) return c[0];
        double m = 1;
        try {
            Object sd = DmzBotKi.statsFor(b);
            int ideal = ideal(sd);
            ItemStack w = worn(b, ideal);
            if (!w.isEmpty() && ideal > 0) {
                Object v = ratioMult.invoke(null, weight(w) / (double) ideal, gravityCfg.invoke(null));
                if (v instanceof Number n && n.doubleValue() > 0) m = n.doubleValue();
            }
        } catch (Throwable t) { m = 1; }
        CACHE.put(b.getUUID(), new double[]{m, now});
        return m;
    }

    /** Has it any weight in its bag? */
    static boolean has(PartyBotEntity b) {
        var inv = b.inventory;
        for (int i = 0; i < inv.getContainerSize(); i++) if (isWeight(inv.getItem(i))) return true;
        return false;
    }

    /** Would this master hand it a weight (it has none, they give them out)? */
    static boolean wantsFrom(PartyBotEntity b, String master) {
        return ready() && master != null && GIVES.containsKey(master.toLowerCase(Locale.ROOT)) && !has(b);
    }

    /** The master hands it a weight set to its ideal weight. */
    static boolean receive(PartyBotEntity b, DmzBotQuests.Data d, String master) {
        if (!wantsFrom(b, master)) return false;
        try {
            Item it = ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(GIVES.get(master.toLowerCase(Locale.ROOT))));
            if (it == null || !weightItem.isInstance(it)) return false;
            ItemStack s = new ItemStack(it);
            int ideal = ideal(d.sd);
            setWeight.invoke(null, s, Math.max(1, ideal));
            if (!b.inventory.canAddItem(s)) return false;
            b.inventory.addItem(s);
            CACHE.remove(b.getUUID());
            given++;
            note(d.key + " got " + it + " (" + Math.max(1, ideal) + ") from " + master);
            DmzNews.bot(d.owner, b, "Training weights", Component.empty().append(b.character().getDisplayName()).append(" got ")
                    .append(s.getHoverName().copy().withStyle(ChatFormatting.GOLD)).append(" from " + DmzBotPower.pretty(master) + " to train with").withStyle(ChatFormatting.AQUA));
            return true;
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("A bot couldn't get weights from a master", t);
            return false;
        }
    }

    /** (Tests) */
    public static String stateForTest(PartyBotEntity b) {
        Object sd = DmzBotKi.statsFor(b);
        int ideal = ideal(sd);
        ItemStack w = worn(b, ideal);
        CACHE.remove(b.getUUID());
        return "ideal " + ideal + " worn " + (w.isEmpty() ? "none" : w.getItem() + " " + weight(w)) + " x" + String.format(Locale.ROOT, "%.2f", multiplier(b));
    }

    public static boolean receiveForTest(PartyBotEntity b, String master) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        return d != null && receive(b, d, master);
    }
}
