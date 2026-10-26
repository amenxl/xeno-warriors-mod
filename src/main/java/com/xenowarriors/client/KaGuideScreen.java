package com.xenowarriors.client;

import com.xenowarriors.ka.Element;
import com.xenowarriors.ka.Ka;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** A guide to every KA and element: what it manipulates or does, its price, its Karma cost, who has it. */
public class KaGuideScreen extends Screen {
    private final Screen parent;
    private boolean elements;
    private static final int CW = 120, CH = 22;

    public KaGuideScreen(Screen parent) {
        super(Component.literal("KA & Element Guide"));
        this.parent = parent;
    }

    public static String does(Ka k) {
        String key = "ka.xenowarriors." + k.id() + ".does";
        return I18n.exists(key) ? I18n.get(key) : "";
    }

    private static String does(Element e) {
        String key = "element.xenowarriors." + e.id() + ".does";
        return I18n.exists(key) ? I18n.get(key) : "";
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("KAs"), b -> elements = false).bounds(width / 2 - 82, 22, 80, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Elements"), b -> elements = true).bounds(width / 2 + 2, 22, 80, 16).build());
    }

    private int cols() { return Math.max(1, Math.min(4, (width - 20) / (CW + 6))); }

    private int cx(int i) { int c = cols(); return width / 2 - (c * (CW + 6) - 6) / 2 + (i % c) * (CW + 6); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "KA & Element Guide", width / 2, 8, 0xFFFFFF);
        List<String> hover = null;
        int y0 = 46;
        if (!elements) {
            g.drawCenteredString(font, "KAs manipulate one thing. Only certain people have one - and every KA costs its user.", width / 2, y0, 0xAAAAAA);
            int y = y0 + 14, i = 0;
            for (int tier = 0; tier < 2; tier++) {
                Ka[] list = tier == 0 ? Ka.SIMPLE : Ka.RARE;
                g.drawString(font, tier == 0 ? "Simple (rare in townsfolk and Skylanders)" : "Rare (guardians, raid captains, bosses)", cx(0), y, tier == 0 ? 0x55FFFF : 0xFF77FF);
                y += 12;
                for (int k = 0; k < list.length; k++) {
                    Ka ka = list[k];
                    int x = cx(k), yy = y + (k / cols()) * (CH + 4);
                    boolean over = mx >= x && mx < x + CW && my >= yy && my < yy + CH;
                    g.fill(x, yy, x + CW, yy + CH, over ? 0xCC30304A : 0xCC1A1A24);
                    g.fill(x, yy, x + 3, yy + CH, 0xFF000000 | ka.color);
                    g.drawString(font, ka.title(), x + 7, yy + 3, 0xFFFFFF);
                    g.drawString(font, ka.manipulates, x + 7, yy + 12, 0x8899AA);
                    if (over) hover = List.of("KA: " + ka.title() + (ka.rare ? "  (rare)" : "  (simple)"),
                            "Manipulates " + ka.manipulates + "   Karma " + ka.karma, "",
                            "What it does: " + does(ka), "",
                            "The price: " + ka.cost + ". It gets worse every use, and too many uses close together burn the KA out for 5 minutes.", "",
                            "Counter: Rhalix's KA Release breaks it (costing what the KA cost them). Hit a Bind user to free whoever they hold.");
                }
                y += ((list.length + cols() - 1) / cols()) * (CH + 4) + 6;
            }
        } else {
            g.drawCenteredString(font, "Elements: everyday powers. Each attack costs the user Karma. Blasts can be clashed.", width / 2, y0, 0xAAAAAA);
            int y = y0 + 14;
            Element[] all = Element.values();
            for (int k = 0; k < all.length; k++) {
                Element el = all[k];
                int x = cx(k), yy = y + (k / cols()) * (CH + 4);
                boolean over = mx >= x && mx < x + CW && my >= yy && my < yy + CH;
                g.fill(x, yy, x + CW, yy + CH, over ? 0xCC30304A : 0xCC1A1A24);
                g.fill(x, yy, x + 3, yy + CH, 0xFF000000 | el.color);
                g.drawString(font, el.title(), x + 7, yy + 3, 0xFFFFFF);
                g.drawString(font, com.xenowarriors.ka.Elements.blast(el) ? "blast - clashable" : "Karma " + el.karma, x + 7, yy + 12, 0x8899AA);
                if (over) hover = List.of("Element: " + el.title(), "Karma " + el.karma + (com.xenowarriors.ka.Elements.blast(el) ? "   Charges up first - can be clashed" : ""), "",
                        does(el), "", "Who has it: " + who(el));
            }
        }
        super.render(g, mx, my, pt);
        if (hover != null) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (int i = 0; i < hover.size(); i++) {
                String l = hover.get(i);
                if (l.isEmpty()) { lines.add(FormattedCharSequence.EMPTY); continue; }
                lines.addAll(font.split(Component.literal(l).withStyle(i == 0 ? ChatFormatting.YELLOW : i == 1 ? ChatFormatting.AQUA : ChatFormatting.GRAY), 240));
            }
            g.renderTooltip(font, lines, mx, my);
        }
    }

    private static String who(Element el) {
        List<String> w = new ArrayList<>();
        for (Element e : Element.HUMAN) if (e == el) w.add("townsfolk");
        for (Element e : Element.SKYLANDER) if (e == el) w.add("Skylanders");
        for (Element e : Element.CSOUL) if (e == el) w.add("corrupted souls");
        if (el == Element.VOID) w.add("corrupted souls (rare)");
        return String.join(", ", w);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
