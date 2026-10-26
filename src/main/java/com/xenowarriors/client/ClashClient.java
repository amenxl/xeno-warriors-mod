package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ClashPushPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Clash meter at the top of the screen; attack clicks become pushes while you're in one. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClashClient implements IGuiOverlay {
    private static boolean active;
    private static float pos = 0.5F, shown = 0.5F;
    private static int ticks;
    private static String opponent = "";

    public static void set(boolean a, float p, int t, String o) { active = a; pos = p; ticks = t; opponent = o; if (!a) shown = 0.5F; }

    public static boolean active() { return active; }

    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active) return;
        if (event.isAttack()) ModNetwork.sendToServer(new ClashPushPacket());
        event.setCanceled(true);
        event.setSwingHand(event.isAttack());
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        if (!active || Minecraft.getInstance().options.hideGui) return;
        shown += (pos - shown) * 0.3F;
        int w = Math.min(220, width - 40), x = width / 2 - w / 2, y = height / 2 - 52;
        var font = Minecraft.getInstance().font;
        boolean flash = (ticks / 4) % 2 == 0;
        g.drawCenteredString(font, "CLASH vs " + opponent + " - MASH LEFT CLICK!", width / 2, y - 12, flash ? 0xFFD24A : 0xFFFFFF);
        g.fill(x - 2, y - 2, x + w + 2, y + 12, 0xFF000000);
        int split = x + Math.round(w * shown);
        g.fill(x, y, split, y + 10, 0xFF4FA8FF);
        g.fill(split, y, x + w, y + 10, 0xFFFF4A4A);
        g.fill(split - 2, y - 3, split + 2, y + 13, 0xFFFFFFFF);
        int left = Math.max(0, (com.xenowarriors.ka.Clashes.MAX_TICKS - ticks + 19) / 20);
        g.drawCenteredString(font, left + "s", width / 2, y + 15, 0xAAAAAA);
    }
}
