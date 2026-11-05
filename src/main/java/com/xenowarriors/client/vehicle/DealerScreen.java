package com.xenowarriors.client.vehicle;

import com.xenowarriors.network.DealerBuyPacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.vehicle.VehicleKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The vehicle dealer: each vehicle with its price in emeralds; pick a colour, then buy. Standard and Mk II models on two pages. */
public class DealerScreen extends Screen {
    private final int emeralds;
    private final int[] colour = new int[VehicleKind.values().length];
    private static final int ROW = 46;
    /** Showing the Mk II models (remembered while the game's open). */
    private static boolean mk2;

    public DealerScreen(int emeralds) { super(Component.literal("Vehicle Dealer")); this.emeralds = emeralds; }

    public static void open(int emeralds) { Minecraft.getInstance().setScreen(new DealerScreen(emeralds)); }

    /** (UI test) */
    public static void page(boolean upgraded) { mk2 = upgraded; }

    private static List<VehicleKind> shown() {
        List<VehicleKind> l = new ArrayList<>();
        for (VehicleKind k : VehicleKind.values()) if (k.upgraded() == mk2) l.add(k);
        return l;
    }

    private int top() { return Math.max(42, height / 2 - (shown().size() * ROW) / 2 + 6); }

    @Override
    protected void init() {
        int x = width / 2 - 150, y = top();
        addRenderableWidget(Button.builder(Component.literal(mk2 ? "< Standard models" : "Mk II models >"), b -> { mk2 = !mk2; rebuildWidgets(); })
                .bounds(width / 2 + 40, y - 36, 110, 16).build());
        List<VehicleKind> list = shown();
        for (int i = 0; i < list.size(); i++) {
            VehicleKind k = list.get(i);
            final int idx = k.ordinal();
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
        g.drawString(font, "Aurora Motors", x, y - 34, 0xFFFFFF, false);
        g.drawString(font, "You have " + emeralds + " emeralds", x, y - 22, 0x55FF55, false);
        if (mk2) g.drawString(font, "Faster and tougher - the car, bike and truck fly too", x, y - 11, 0x7FE8FF, false);
        List<VehicleKind> list = shown();
        for (int i = 0; i < list.size(); i++) {
            VehicleKind k = list.get(i);
            int ry = y + i * ROW;
            g.fill(x, ry, x + 300, ry + 42, 0xB0101418);
            g.fill(x, ry, x + 3, ry + 42, k.upgraded() ? 0xFF3FE0FF : 0xFF33AACC);
            g.drawString(font, k.title, x + 10, ry + 8, k.upgraded() ? 0x9FF0FF : 0xFFFFFF, false);
            String v = k.variant(colour[k.ordinal()]);
            int in = k.insideSeats(), extra = k.seats - in;
            String seatText = in + " seats" + (extra > 0 ? " +" + extra + (k.heli() ? " hanging on" : " on top") : "");
            g.drawString(font, seatText + (k.variants.length > 1 ? " - " + Character.toUpperCase(v.charAt(0)) + v.substring(1) : ""), x + 10, ry + 20, 0xAAAAAA, false);
            g.drawString(font, k.price + " emeralds" + (k.upgraded() ? "   " + Math.round(k.maxSpeed * 72) + " km/h" + (k.hovers() ? ", flies" : "") : ""), x + 10, ry + 31,
                    emeralds >= k.price ? 0x55FF55 : 0xFF5555, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
