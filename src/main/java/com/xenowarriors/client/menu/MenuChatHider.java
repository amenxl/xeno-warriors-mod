package com.xenowarriors.client.menu;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * While one of our menus is open the chat isn't drawn behind it: its lines showed through the menus' see-through
 * panels, over their entries (the main menu, character select, party setup...). The chat screen itself is untouched.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MenuChatHider {
    private MenuChatHider() {}

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) return;
        var s = Minecraft.getInstance().screen;
        if (s != null && s.getClass().getName().startsWith("com.xenowarriors.")) event.setCanceled(true);
    }
}
