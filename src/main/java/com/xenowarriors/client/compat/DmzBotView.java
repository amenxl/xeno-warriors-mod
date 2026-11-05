package com.xenowarriors.client.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.BotDmzPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A bot's DragonMineZ menu on the client: DragonMineZ's own menu opens on the bot's DMZ character (swapped in by the
 * server), with a banner saying whose it is; when no DragonMineZ screen has been open for a few ticks the server is
 * told to put yours back.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DmzBotView {
    private DmzBotView() {}

    private static String name;
    private static int away, opened;

    /** Whose DMZ character the menu shows (null = your own). */
    public static String showing() { return name; }

    public static void handle(int action, String who) {
        Minecraft mc = Minecraft.getInstance();
        if (action == BotDmzPacket.OPEN) {
            name = who; away = 0; opened = 0;
            if (!DmzMenu.open()) { ModNetwork.sendToServer(new BotDmzPacket(BotDmzPacket.CLOSE, "")); name = null; }
        } else if (action == BotDmzPacket.CLOSED) {
            boolean was = name != null;
            name = null;
            if (was && dmzScreen(mc)) mc.setScreen(null);
            if (was && mc.player != null) mc.player.displayClientMessage(Component.literal("Back to your own Dragon Ball character"), true);
        }
    }

    private static boolean dmzScreen(Minecraft mc) {
        return mc.screen != null && mc.screen.getClass().getName().startsWith("com.dragonminez");
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || name == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { name = null; return; }
        opened++;
        away = dmzScreen(mc) ? 0 : away + 1;
        if (away > 4 && opened > 10) {                                          // (DragonMineZ's menus hand over to each other without a gap)
            name = null;
            ModNetwork.sendToServer(new BotDmzPacket(BotDmzPacket.CLOSE, ""));
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post e) {
        if (name == null || !e.getScreen().getClass().getName().startsWith("com.dragonminez")) return;
        var g = e.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        String s = "Bot: " + name + "'s Dragon Ball character";
        int w = font.width(s) + 12, x = (e.getScreen().width - w) / 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x, 2, x + w, 15, 0xC0201008);
        g.fill(x, 15, x + w, 16, 0xFFFFA020);
        g.drawString(font, s, x + 6, 5, 0xFFD060);
        g.pose().popPose();
    }
}
