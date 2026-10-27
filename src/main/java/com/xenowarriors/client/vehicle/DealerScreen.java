package com.xenowarriors.client.vehicle;

import com.xenowarriors.network.DealerBuyPacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.vehicle.VehicleKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The vehicle dealer: each vehicle with its price in emeralds; pick a colour, then buy. */
public class DealerScreen extends Screen {
    private final int emeralds;
    private final int[] colour = new int[VehicleKind.values().length];
    private static final int ROW = 46;

    public DealerScreen(int emeralds) { super(Component.literal("Vehicle Dealer")); this.emeralds = emeralds; }

    public static void open(int emeralds) { Minecraft.getInstance().setScreen(new DealerScreen(emeralds)); }

    private int top() { return Math.max(30, height / 2 - (VehicleKind.values().length * ROW) / 2); }

    @Override
    protected void init() {
        int x = width / 2 - 150, y = top();
        for (int i = 0; i < VehicleKind.values().length; i++) {
            final int idx = i;
            VehicleKind k = VehicleKind.values()[i];
            int ry = y + i * ROW;
            if (k.variants.length > 1)
                addRenderableWidget(Button.builder(Component.literal("Colour"), b -> colour[idx] = (colour[idx] + 1) % k.variants.length).bounds(x + 168, ry + 12, 56, 20).build());
            Button buy = addRenderableWidget(Button.builder(Component.literal("Buy"), b -> ModNetwork.sendToServer(new DealerBuyPacket(idx, colour[idx])))
                    .bounds(x + 232, ry + 12, 64, 20).build());
            boolean creative = minecraft != null && minecraft.player != null && minecraft.player.isCreative();
            buy.active = creative || emeralds >= k.price;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = width / 2 - 150, y = top();
        g.drawCenteredString(font, "Aurora Motors", width / 2, y - 22, 0xFFFFFF);
        g.drawCenteredString(font, "You have " + emeralds + " emeralds", width / 2, y - 11, 0x55FF55);
        for (int i = 0; i < VehicleKind.values().length; i++) {
            VehicleKind k = VehicleKind.values()[i];
            int ry = y + i * ROW;
            g.fill(x, ry, x + 300, ry + 42, 0xB0101418);
            g.fill(x, ry, x + 3, ry + 42, 0xFF33AACC);
            g.drawString(font, k.title, x + 10, ry + 8, 0xFFFFFF, false);
            String v = k.variant(colour[i]);
            g.drawString(font, k.seats + " seats" + (k.variants.length > 1 ? " - " + Character.toUpperCase(v.charAt(0)) + v.substring(1) : ""), x + 10, ry + 20, 0xAAAAAA, false);
            g.drawString(font, k.price + " emeralds", x + 10, ry + 31, emeralds >= k.price ? 0x55FF55 : 0xFF5555, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
