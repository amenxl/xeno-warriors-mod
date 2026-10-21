package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.UseSkillPacket;
import com.xenowarriors.client.screen.CharacterSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean pendingSelectScreen = false;

    private ClientEvents() {}

    public static void requestSelectScreen() {
        pendingSelectScreen = true;
    }

    /** Wait until the world has finished loading before showing the screen. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        WindEffects.tick(mc);

        // skill keys
        for (int slot = 0; slot < ModKeyMappings.SKILLS.length; slot++) {
            while (ModKeyMappings.SKILLS[slot].consumeClick()) {
                if (mc.player != null && ClientCharacterData.getLocal() != CharacterType.NONE) {
                    ModNetwork.sendToServer(new UseSkillPacket(slot));
                }
            }
        }

        if (!pendingSelectScreen) return;
        if (mc.player != null && mc.level != null && mc.screen == null) {
            pendingSelectScreen = false;
            mc.setScreen(new CharacterSelectScreen());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        pendingSelectScreen = false;
        ClientCharacterData.clear();
        ClientSkillState.clear();
        FlightTracker.clear();
    }
}
