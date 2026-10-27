package com.xenowarriors.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

/**
 * Opens DragonMineZ's own character menu (its stats screen, or race selection if the player hasn't made their DMZ
 * character yet) - the same thing DMZ's menu key does. Done by reflection, so nothing breaks without DragonMineZ.
 */
public final class DmzMenu {
    private DmzMenu() {}

    public static boolean available() { return ModList.get() != null && ModList.get().isLoaded("dragonminez"); }

    public static boolean open() {
        Minecraft mc = Minecraft.getInstance();
        if (!available() || mc.player == null) return false;
        try {
            Capability<?> cap = (Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
            Object data = mc.player.getCapability(cap).resolve().orElse(null);
            if (data == null) return false;
            Object status = data.getClass().getMethod("getStatus").invoke(data);
            boolean made = (Boolean) status.getClass().getMethod("isHasCreatedCharacter").invoke(status);
            Screen s;
            if (made) s = (Screen) Class.forName("com.dragonminez.client.gui.character.CharacterStatsScreen").getConstructor().newInstance();
            else {
                Object character = data.getClass().getMethod("getCharacter").invoke(data);
                Class<?> rs = Class.forName("com.dragonminez.client.gui.character.RaceSelectionScreen");
                s = (Screen) rs.getConstructor(Class.forName("com.dragonminez.common.stats.character.Character")).newInstance(character);
            }
            mc.setScreen(s);
            return true;
        } catch (Throwable t) {
            com.xenowarriors.XenoWarriors.LOGGER.warn("Couldn't open the DragonMineZ menu", t);
            return false;
        }
    }
}
