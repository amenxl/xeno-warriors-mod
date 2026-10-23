package com.xenowarriors.client.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.KarmaSense;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * With DragonMineZ: Karma Sensing works like its Ki Sense - while Mikiko or Rhalix are sensing, DragonMineZ's Ki Sense
 * search runs for them (its auras through walls, power readings), only the auras glow dark purple: Karma, not Ki.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DmzKiSenseLink {
    private static Boolean loaded;
    private static boolean broken, linked;
    private static Field mode;
    private static Object SEARCH, NONE;
    private static Method scanTick;
    private static Capability<?> cap;

    private DmzKiSenseLink() {}

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (mode == null) {
            try {
                Class<?> state = Class.forName("com.dragonminez.client.systems.kisense.KiSenseState");
                Class<?> modeEnum = Class.forName("com.dragonminez.client.systems.kisense.KiSenseState$Mode");
                mode = state.getDeclaredField("mode");
                mode.setAccessible(true);
                SEARCH = modeEnum.getField("SEARCH").get(null);
                NONE = modeEnum.getField("NONE").get(null);
                Class<?> data = Class.forName("com.dragonminez.common.stats.StatsData");
                scanTick = Class.forName("com.dragonminez.client.systems.kisense.KiSenseScan")
                        .getMethod("tick", net.minecraft.world.entity.player.Player.class, data, modeEnum);
                cap = (Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
            } catch (Throwable t) {
                broken = true;
                return false;
            }
        }
        return true;
    }

    /** Karma Sensing is driving DragonMineZ's Ki Sense right now (its auras are Karma's). */
    public static boolean karmaDriven() {
        return linked;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ready()) return;
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        boolean want = p != null && mc.level != null && ClientCharacterData.getLocal() != CharacterType.NONE && KarmaSense.active();
        try {
            if (want) {
                Object data = p.getCapability(cap).resolve().orElse(null);
                if (data == null) return;
                mode.set(null, SEARCH);                         // (after DragonMineZ's own tick, which would switch it off)
                scanTick.invoke(null, p, data, SEARCH);
                linked = true;
            } else if (linked) {
                mode.set(null, NONE);
                linked = false;
            }
        } catch (Throwable t) {
            broken = true;
            linked = false;
        }
    }

    /** DragonMineZ's auras in Xeno Warriors' Karma colors: each sensed thing glows the color Karma Sensing gives it. */
    public static void recolor(LivingEntity e, float[][] colors) {
        if (!linked || colors == null) return;
        int rgb = KarmaSense.color(e);
        float[] c = {((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F};
        for (int i = 0; i < colors.length; i++) {
            float[] row = colors[i];
            if (row == null) continue;
            float f = 1F - i * 0.18F;                          // outer layers a touch darker
            for (int k = 0; k < Math.min(3, row.length); k++) row[k] = c[k] * f;
        }
    }
}
