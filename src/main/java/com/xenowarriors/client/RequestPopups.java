package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.RequestPopupPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Request cards: party invites, battle challenges and team-ultimate prompts slide in at the top right. Press the
 * Accept key (Y) or Decline key (X) - it doesn't pause the game or take the mouse. Fades when the request runs out.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RequestPopups {
    private record Card(RequestPopupPacket req, long shownAt) {}
    private static final Deque<Card> CARDS = new ArrayDeque<>();

    private RequestPopups() {}

    public static void show(RequestPopupPacket req) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        CARDS.removeIf(c -> c.req.accept().equals(req.accept()));       // a newer one of the same kind replaces it
        CARDS.addFirst(new Card(req, mc.level.getGameTime()));
        if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8F, 1.6F);
    }

    private static Card top(Minecraft mc) {
        if (mc.level == null) return null;
        long now = mc.level.getGameTime();
        CARDS.removeIf(c -> now - c.shownAt > c.req.ticks());
        return CARDS.peekFirst();
    }

    private static void answer(Minecraft mc, boolean yes) {
        Card c = CARDS.pollFirst();
        if (c == null || mc.player == null) return;
        String cmd = yes ? c.req.accept() : c.req.decline();
        if (cmd != null && !cmd.isEmpty()) mc.player.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        mc.player.playSound(yes ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.NOTE_BLOCK_BASS.value(), 0.8F, yes ? 1.4F : 0.8F);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean any = top(mc) != null;
        while (ModKeyMappings.ACCEPT_REQUEST.consumeClick()) if (any && mc.screen == null) { answer(mc, true); any = top(mc) != null; }
        while (ModKeyMappings.DECLINE_REQUEST.consumeClick()) if (any && mc.screen == null) { answer(mc, false); any = top(mc) != null; }
    }

    public static class Overlay implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
            Minecraft mc = Minecraft.getInstance();
            Card c = top(mc);
            if (c == null || mc.options.hideGui) return;
            Font font = mc.font;
            float age = mc.level.getGameTime() - c.shownAt + partialTick;
            float left = c.req.ticks() - age;
            float slide = Mth.clamp(age / 6F, 0F, 1F);
            slide = slide * slide * (3 - 2 * slide);
            float fade = Mth.clamp(left / 20F, 0F, 1F);
            String yes = ModKeyMappings.ACCEPT_REQUEST.getTranslatedKeyMessage().getString();
            String no = ModKeyMappings.DECLINE_REQUEST.getTranslatedKeyMessage().getString();
            var lines = font.split(net.minecraft.network.chat.Component.literal(c.req.text()), 170);
            int w = 186, h = 30 + lines.size() * 10 + 14;
            int x = Math.round(width - 6 - w * slide), y = HudLayout.takeTopRight(h);
            int a = Math.round(fade * 230);
            g.fill(x, y, x + w, y + h, (a << 24) | 0x14121C);
            g.fill(x, y, x + 3, y + h, (Math.round(fade * 255) << 24) | (c.req.color() & 0xFFFFFF));
            int ta = Math.max(8, Math.round(fade * 255)) << 24;
            g.drawString(font, c.req.title(), x + 9, y + 6, (c.req.color() & 0xFFFFFF) | ta, false);
            int ly = y + 19;
            for (var l : lines) {
                g.drawString(font, l, x + 9, ly, 0xE8E8F0 | ta, false);
                ly += 10;
            }
            ly += 4;
            g.drawString(font, "[" + yes + "] Accept", x + 9, ly, 0x6CF07A | ta, false);
            g.drawString(font, "[" + no + "] Decline", x + 95, ly, 0xF06C6C | ta, false);
            // time left
            int bw = Math.round((w - 6) * Mth.clamp(left / c.req.ticks(), 0F, 1F));
            g.fill(x + 3, y + h - 2, x + 3 + bw, y + h, (a << 24) | 0x8080A0);
            if (CARDS.size() > 1) g.drawString(font, "+" + (CARDS.size() - 1), x + w - 16, y + 6, 0xA0A0B0 | ta, false);
        }
    }
}
