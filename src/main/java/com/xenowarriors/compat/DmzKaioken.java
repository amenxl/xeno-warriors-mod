package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.*;

/**
 * With DragonMineZ: Mikiko masters Kaioken 20 times as fast as anyone else. DragonMineZ raises a form's mastery while
 * it's used (Kaioken is one of its "stack" forms: x2, x3, x4, x10, x20, x100); once a second, whatever a Mikiko
 * player's Kaioken masteries went up by since the last look is added 19 more times (up to each form's max mastery).
 * The bookkeeping is per DragonMineZ character, so switching characters never pays out anything twice.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzKaioken {
    private DmzKaioken() {}

    public static final String GROUP = "kaioken";
    public static final double FACTOR = 20.0;
    private static final String[] FALLBACK = {"x2", "x3", "x4", "x10", "x20", "x100"};
    private static Boolean loaded;
    private static boolean broken;
    private static Method getCharacter, stackMasteries, getMastery, addMastery, stackGroup, stackForm, maxMastery;
    /** player -> character key -> form -> mastery last seen */
    private static final Map<UUID, Map<String, double[]>> SEEN = new HashMap<>();
    /** (For the tests) bonus mastery given so far, per player name. */
    public static final Map<String, Double> GIVEN = new HashMap<>();

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (getCharacter != null) return true;
        try {
            Class<?> data = Class.forName("com.dragonminez.common.stats.StatsData");
            Class<?> ch = Class.forName("com.dragonminez.common.stats.character.Character");
            Class<?> fm = Class.forName("com.dragonminez.common.stats.extras.FormMasteries");
            stackMasteries = ch.getMethod("getStackFormMasteries");
            getMastery = fm.getMethod("getMastery", String.class, String.class);
            addMastery = fm.getMethod("addMastery", String.class, String.class, double.class, double.class);
            Class<?> cm = Class.forName("com.dragonminez.common.config.ConfigManager");
            stackGroup = cm.getMethod("getStackFormGroup", String.class);
            stackForm = cm.getMethod("getStackForm", String.class, String.class);
            maxMastery = stackForm.getReturnType().getMethod("getMaxMastery");
            getCharacter = data.getMethod("getCharacter");
            return true;
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("Mikiko's fast Kaioken mastery off (DragonMineZ's API changed?)", t);
            return false;
        }
    }

    private static String[] forms() {
        try {
            Object g = stackGroup.invoke(null, GROUP);
            if (g != null) {
                for (Method m : g.getClass().getMethods())
                    if (m.getParameterCount() == 0 && Map.class.isAssignableFrom(m.getReturnType()) && m.getName().toLowerCase(Locale.ROOT).contains("form")) {
                        Map<?, ?> mp = (Map<?, ?>) m.invoke(g);
                        if (mp != null && !mp.isEmpty()) { List<String> out = new ArrayList<>(); for (Object k : mp.keySet()) out.add(String.valueOf(k)); return out.toArray(new String[0]); }
                    }
            }
        } catch (Throwable ignored) {}
        return FALLBACK;
    }

    private static String[] FORMS;

    /** Is this player Mikiko (the one who masters it fast)? */
    public static boolean fast(ServerPlayer p) { return ModCapabilities.getType(p) == CharacterType.MIKIKO; }

    /** The player's Kaioken mastery of one form (-1 = can't read). */
    public static double mastery(ServerPlayer p, String form) {
        if (!ready()) return -1;
        try { return (Double) getMastery.invoke(stackMasteries.invoke(getCharacter.invoke(DmzPerCharacter.live(p))), GROUP, form); }
        catch (Throwable t) { return -1; }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer || p.tickCount % 20 != 13) return;
        poll(p);
    }

    /** Catches up on the player's Kaioken mastery gains. Returns the bonus given now. */
    public static double poll(ServerPlayer p) {
        if (!ready()) return 0;
        Object data = DmzPerCharacter.live(p);
        if (data == null) return 0;
        if (FORMS == null) FORMS = forms();
        String key = DmzPerCharacter.bookKey(p);
        Map<String, double[]> per = SEEN.computeIfAbsent(p.getUUID(), u -> new HashMap<>());
        double given = 0;
        try {
            Object fm = stackMasteries.invoke(getCharacter.invoke(data));
            double[] last = per.get(key);
            double[] now = new double[FORMS.length];
            for (int i = 0; i < FORMS.length; i++) now[i] = (Double) getMastery.invoke(fm, GROUP, FORMS[i]);
            boolean mikiko = fast(p) && DmzPerCharacter.viewing(p) == null;
            if (last != null && last.length == now.length && mikiko) {
                for (int i = 0; i < FORMS.length; i++) {
                    double d = now[i] - last[i];
                    if (d <= 0 || d > 1000) continue;
                    Object fd = stackForm.invoke(null, GROUP, FORMS[i]);
                    double max = fd == null ? 100.0 : ((Number) maxMastery.invoke(fd)).doubleValue();
                    double add = Math.min(d * (FACTOR - 1.0), Math.max(0, max - now[i]));
                    if (add <= 0) continue;
                    addMastery.invoke(fm, GROUP, FORMS[i], add, max);
                    now[i] = (Double) getMastery.invoke(fm, GROUP, FORMS[i]);
                    given += add;
                }
                if (given > 0) {
                    GIVEN.merge(p.getGameProfile().getName(), given, Double::sum);
                    DmzPerCharacter.syncStats(p);
                }
            }
            per.put(key, now);
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("Mikiko's fast Kaioken mastery off (it didn't work)", t);
        }
        return given;
    }

    /**
     * What was seen is forgotten on logout: the next world (or a world restored from a backup) can have her at another
     * mastery, and the difference isn't a gain to multiply.
     */
    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        SEEN.remove(e.getEntity().getUUID());
        GIVEN.remove(e.getEntity().getGameProfile().getName());
    }

    /** (For the tests) forget what was seen, so the next poll starts counting from now. */
    public static void resetForTest(ServerPlayer p) { SEEN.remove(p.getUUID()); GIVEN.remove(p.getGameProfile().getName()); }
}
