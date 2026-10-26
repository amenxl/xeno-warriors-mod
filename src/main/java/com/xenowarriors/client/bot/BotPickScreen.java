package com.xenowarriors.client.bot;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.network.BotPickPacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.OpenBotPickPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.*;

/** "Pick 2 people to join your party": character-select style cards, minus you and anyone already with you. */
public class BotPickScreen extends Screen {
    private static final int CARD_H = 168, GAP = 12;
    private final Set<String> taken, cooling;
    private final int max;
    private final List<CharacterType> choices = new ArrayList<>();
    private final LinkedHashSet<CharacterType> picked = new LinkedHashSet<>();
    private int cardW = 140;
    private Button confirm;

    public BotPickScreen(OpenBotPickPacket m) {
        super(Component.literal("Pick 2 people to join your party"));
        taken = new HashSet<>(Arrays.asList(m.taken()));
        cooling = new HashSet<>(Arrays.asList(m.cooling()));
        max = m.free();
        for (CharacterType t : CharacterType.PLAYABLE) if (!taken.contains(t.getId())) choices.add(t);
    }

    private boolean locked(CharacterType t) { return com.xenowarriors.client.ClientSkillState.season() < t.season(); }
    private boolean usable(CharacterType t) { return !locked(t) && !cooling.contains(t.getId()); }
    private int left(int i) { int total = choices.size() * cardW + (choices.size() - 1) * GAP; return (width - total) / 2 + i * (cardW + GAP); }
    private int top() { return Math.max(40, (height - CARD_H - 40) / 2); }

    @Override
    protected void init() {
        cardW = Math.min(140, choices.isEmpty() ? 140 : (width - 20 - (choices.size() - 1) * GAP) / choices.size());
        for (int i = 0; i < choices.size(); i++) {
            CharacterType t = choices.get(i);
            Button b = Button.builder(label(t), btn -> toggle(t)).bounds(left(i) + 8, top() + CARD_H - 26, cardW - 16, 20).build();
            b.active = usable(t) && max > 0;
            addRenderableWidget(b);
        }
        confirm = addRenderableWidget(Button.builder(Component.literal("Add to party"), b -> {
            ModNetwork.sendToServer(new BotPickPacket(picked.stream().map(CharacterType::getId).toArray(String[]::new)));
            onClose();
        }).bounds(width / 2 - 80, top() + CARD_H + 12, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width / 2 + 4, top() + CARD_H + 12, 76, 20).build());
        confirm.active = false;
    }

    private Component label(CharacterType t) {
        if (locked(t)) return Component.literal("Locked");
        if (cooling.contains(t.getId())) return Component.literal("Recovering...");
        return Component.literal(picked.contains(t) ? "✔ Picked" : "Pick");
    }

    private void toggle(CharacterType t) {
        if (!picked.remove(t)) {
            if (picked.size() >= Math.min(2, max)) picked.remove(picked.iterator().next());      // swap out the oldest pick
            picked.add(t);
        }
        rebuildLabels();
    }

    private void rebuildLabels() {
        int i = 0;
        for (var w : children()) if (w instanceof Button b && i < choices.size()) { b.setMessage(label(choices.get(i))); i++; }
        confirm.active = !picked.isEmpty();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int top = top();
        g.pose().pushPose();
        g.pose().scale(1.6F, 1.6F, 1F);
        g.drawCenteredString(font, title, (int) (width / 3.2F), (int) ((top - 30) / 1.6F), 0xFFFFFF);
        g.pose().popPose();
        String sub = max <= 0 ? "Your party is full (4 people and bots)" : "Up to " + Math.min(2, max) + " - they join at the level you have them at";
        g.drawCenteredString(font, sub, width / 2, top - 12, max <= 0 ? 0xFF6060 : 0xAAAAAA);
        if (choices.isEmpty()) g.drawCenteredString(font, "Everyone's already in your party!", width / 2, top + CARD_H / 2, 0xCCCCCC);
        for (int i = 0; i < choices.size(); i++) {
            CharacterType t = choices.get(i);
            int x = left(i), cx = x + cardW / 2;
            boolean sel = picked.contains(t);
            boolean hov = mx >= x && mx < x + cardW && my >= top && my < top + CARD_H;
            int border = sel ? 0xFFFFE070 : hov ? t.getColor() : (t.getColor() & 0xFFFFFF) | 0x88000000;
            g.fill(x - 2, top - 2, x + cardW + 2, top + CARD_H + 2, border);
            g.fill(x, top, x + cardW, top + CARD_H, sel ? 0xE0201C10 : 0xE0101016);
            if (locked(t)) {
                g.drawCenteredString(font, "???", cx, top + 8, 0x888888);
                g.drawCenteredString(font, "Season " + t.season() + " content", cx, top + 20, 0xAA5555);
                continue;
            }
            g.drawCenteredString(font, t.getFullName(), cx, top + 8, t.getColor());
            g.drawCenteredString(font, t.getTitle(), cx, top + 20, 0xDDDDDD);
            int modelY = top + CARD_H - 40;
            ClientCharacterData.beginPreview(t);
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, modelY, 46, (float) (cx - mx), (float) (modelY - 76 - my), minecraft.player);
            } finally {
                ClientCharacterData.endPreview();
            }
            if (cooling.contains(t.getId())) g.fill(x, top, x + cardW, top + CARD_H, 0x88300000);
        }
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
