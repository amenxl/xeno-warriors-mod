package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/** With DragonMineZ: Mikiko and Rhalix can't learn Ki Sense - they sense with Karma instead (which works the same). */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzNoKiSense {
    private static Boolean loaded;
    private static boolean broken;
    private static Capability<?> cap;
    private static Method getSkills, hasSkill, removeSkill;

    private DmzNoKiSense() {}

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (cap == null) {
            try {
                cap = (Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
                getSkills = Class.forName("com.dragonminez.common.stats.StatsData").getMethod("getSkills");
                Class<?> skills = getSkills.getReturnType();
                hasSkill = skills.getMethod("hasSkill", String.class);
                removeSkill = skills.getMethod("removeSkill", String.class);
            } catch (Throwable t) {
                broken = true;
                return false;
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.tickCount % 20 != 0 || !ready()) return;
        if (ModCapabilities.getType(event.player) == CharacterType.NONE) return;
        try {
            Object data = event.player.getCapability(cap).resolve().orElse(null);
            if (data == null) return;
            Object skills = getSkills.invoke(data);
            if ((Boolean) hasSkill.invoke(skills, "kisense")) {
                removeSkill.invoke(skills, "kisense");
                if (event.player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal("You already sense with Karma - Ki Sense isn't for you.")
                            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), false);
                }
            }
        } catch (Throwable t) {
            broken = true;
        }
    }
}
