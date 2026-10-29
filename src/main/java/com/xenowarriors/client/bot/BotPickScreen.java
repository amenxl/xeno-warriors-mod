package com.xenowarriors.client.bot;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.journey.JourneyScreen;
import com.xenowarriors.network.BotPickPacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.OpenBotPickPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.*;

/**
 * "Pick people to join your party": a grid of cards (face, name, level, title in the character's colour) that scrolls
 * when there are more than fit, minus you and anyone already with you. Click a card to pick it; picks are highlighted.
 */
public class BotPickScreen extends Screen {
    private static final int CW = 92, CH = 96, GAP = 8;
    private final Set<String> taken, cooling;
    private final int max;
    private final List<CharacterType> choices = new ArrayList<>();
    private final LinkedHashSet<CharacterType> picked = new LinkedHashSet<>();
    private Button confirm;
    private int cols, scroll;
    private CharacterType hover;

    public BotPickScreen(OpenBotPickPacket m) {
        super(Component.literal("Pick people to join your party"));
        taken = new HashSet<>(Arrays.asList(m.taken()));
        cooling = new HashSet<>(Arrays.asList(m.cooling()));
        max = m.free();
        for (CharacterType t : CharacterType.PLAYABLE) if (!taken.contains(t.getId())) choices.add(t);
        ModNetwork.sendToServer(new com.xenowarriors.network.CharLevelsPacket(new int[0]));      // each character's level with you
    }

    private boolean locked(CharacterType t) { return com.xenowarriors.client.ClientSkillState.season() < t.season(); }
    private boolean usable(CharacterType t) { return !locked(t) && !cooling.contains(t.getId()) && max > 0; }
    private int level(CharacterType t) { return Math.max(1, com.xenowarriors.network.CharLevelsPacket.CLIENT[t.ordinal()]); }

    private int gridTop() { return 44; }
    private int gridBottom() { return height - 36; }
    private int gridLeft() { return (width - (cols * CW + (cols - 1) * GAP)) / 2; }
    private int rows() { return (choices.size() + cols - 1) / cols; }
    private int maxScroll() { return Math.max(0, rows() * (CH + GAP) - GAP - (gridBottom() - gridTop())); }

    @Override
    protected void init() {
        cols = Mth.clamp((width - 24 + GAP) / (CW + GAP), 1, Math.max(1, choices.size()));
        scroll = Math.min(scroll, maxScroll());
        confirm = addRenderableWidget(Button.builder(Component.literal("Add to party"), b -> {
            ModNetwork.sendToServer(new BotPickPacket(picked.stream().map(CharacterType::getId).toArray(String[]::new)));
            onClose();
        }).bounds(width / 2 - 82, height - 28, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width / 2 + 2, height - 28, 80, 20).build());
        confirm.active = !picked.isEmpty();
    }

    private int[] cardAt(int i) {
        int r = i / cols, c = i % cols;
        return new int[]{gridLeft() + c * (CW + GAP), gridTop() + r * (CH + GAP) - scroll};
    }

    private void toggle(CharacterType t) {
        if (!usable(t)) return;
        if (!picked.remove(t)) {
            if (picked.size() >= Math.min(2, max)) picked.remove(picked.iterator().next());      // swap out the oldest pick
            picked.add(t);
        }
        confirm.active = !picked.isEmpty();
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.2F));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (my >= gridTop() && my < gridBottom()) for (int i = 0; i < choices.size(); i++) {
            int[] a = cardAt(i);
            if (mx >= a[0] && mx < a[0] + CW && my >= a[1] && my < a[1] + CH) { toggle(choices.get(i)); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (int) (delta * 24), 0, maxScroll());
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hover = null;
        int n = Math.min(2, max);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        String sub = max <= 0 ? "Your party is full (4 people and bots)" : "Pick up to " + n + " - they join at the level you have them at   (" + picked.size() + "/" + n + " picked)";
        g.drawCenteredString(font, sub, width / 2, 24, max <= 0 ? 0xFF6060 : 0xAAAAAA);
        if (choices.isEmpty()) g.drawCenteredString(font, "Everyone's already in your party!", width / 2, height / 2, 0xCCCCCC);
        g.enableScissor(0, gridTop(), width, gridBottom());
        for (int i = 0; i < choices.size(); i++) {
            CharacterType t = choices.get(i);
            int[] a = cardAt(i);
            int x = a[0], y = a[1];
            if (y + CH < gridTop() || y > gridBottom()) continue;
            boolean sel = picked.contains(t), ok = usable(t);
            boolean hov = ok && mx >= x && mx < x + CW && my >= y && my < y + CH && my >= gridTop() && my < gridBottom();
            if (hov) hover = t;
            int col = t.getColor() & 0xFFFFFF;
            g.fill(x - 1, y - 1, x + CW + 1, y + CH + 1, sel ? 0xFFFFE070 : hov ? 0xFF000000 | col : 0xFF000000 | mix(col, 0.45F));
            g.fill(x, y, x + CW, y + CH, sel ? 0xF0262010 : 0xF0101016);
            g.fill(x, y, x + CW, y + 3, 0xFF000000 | col);                      // the faction colour stripe
            if (locked(t)) {
                g.fill(x + CW / 2 - 20, y + 14, x + CW / 2 + 20, y + 54, 0xFF202026);
                g.drawCenteredString(font, "???", x + CW / 2, y + 30, 0x888888);
                g.drawCenteredString(font, "Season " + t.season(), x + CW / 2, y + 60, 0xAA5555);
                g.drawCenteredString(font, "content", x + CW / 2, y + 70, 0xAA5555);
                continue;
            }
            JourneyScreen.face(g, t, x + CW / 2 - 20, y + 8, 40);
            String name = t.getDisplayName().getString();
            g.drawCenteredString(font, name, x + CW / 2, y + 52, 0xFF000000 | col);
            g.drawCenteredString(font, "Lv " + level(t), x + CW / 2, y + 64, 0xFFD966);
            String title = t.getTitle().getString();
            g.pose().pushPose();
            g.pose().translate(x + CW / 2F, y + 78, 0);
            float k = Math.min(0.75F, (CW - 6) / (float) Math.max(1, font.width(title)) * 0.75F);
            g.pose().scale(k, k, 1F);
            g.drawCenteredString(font, title, 0, 0, 0xBBBBBB);
            g.pose().popPose();
            if (sel) g.drawCenteredString(font, "✔ PICKED", x + CW / 2, y + CH - 11, 0xFFE070);
            if (cooling.contains(t.getId())) {
                g.fill(x, y, x + CW, y + CH, 0x99300000);
                g.drawCenteredString(font, "Recovering...", x + CW / 2, y + CH - 11, 0xFF8080);
            } else if (!ok) g.fill(x, y, x + CW, y + CH, 0x80000000);
        }
        g.disableScissor();
        if (maxScroll() > 0) g.drawCenteredString(font, scroll < maxScroll() ? "▼ scroll for more" : "▲ scroll up", width / 2, gridBottom() - 10, 0x8899AA);
        super.render(g, mx, my, pt);
        if (hover != null) {
            List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>(font.split(Component.literal(hover.getFullName().getString() + "\n" + hover.getTitle().getString() + "\n" + hover.getElements().getString()), 160));
            g.renderTooltip(font, lines, mx, my);
        }
    }

    private static int mix(int c, float f) {
        int r = (int) (((c >> 16) & 255) * f), gg = (int) (((c >> 8) & 255) * f), bb = (int) ((c & 255) * f);
        return (r << 16) | (gg << 8) | bb;
    }

    @Override public boolean isPauseScreen() { return false; }
}
