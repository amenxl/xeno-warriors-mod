package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SetSkillSlotPacket;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.skill.SkillPool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Pick your 4 skills: click a slot on top, then a skill below to put it there (or click a skill first and it goes into
 * the selected slot). Locked skills show the Character Level they unlock at. Slot 5 is always the ultimate.
 */
public class SkillLoadoutScreen extends Screen {
    private final Screen parent;
    private int selected;
    private static final int BOX = 34, CARD_W = 150, CARD_H = 40;

    public SkillLoadoutScreen(Screen parent) {
        super(Component.literal("Skills"));
        this.parent = parent;
    }

    private CharacterType type() { return ClientCharacterData.getLocal(); }
    private boolean creative() { return minecraft != null && minecraft.player != null && minecraft.player.isCreative(); }

    private int slotsLeft() { return width / 2 - (5 * (BOX + 8) - 8) / 2; }

    private int cols() { return Math.max(1, Math.min(4, (width - 20) / (CARD_W + 6))); }

    private int cardX(int i) { int c = cols(); return width / 2 - (c * (CARD_W + 6) - 6) / 2 + (i % c) * (CARD_W + 6); }

    private int cardY(int i) { return 104 + (i / cols()) * (CARD_H + 6); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        CharacterType type = type();
        g.drawCenteredString(font, "Skills - pick 4 + your ultimate", width / 2, 10, 0xFFFFFF);
        g.drawCenteredString(font, "Character Level " + ClientSkillState.level() + (creative() ? "  (creative: everything unlocked)" : ""), width / 2, 22, 0xAAAAAA);
        int[] loadout = ClientSkillState.loadout();
        String hover = null;
        // equipped slots
        for (int s = 0; s < SkillManager.SLOTS; s++) {
            int x = slotsLeft() + s * (BOX + 8), y = 40;
            boolean ult = s == SkillManager.ULTIMATE_SLOT;
            Skill sk = SkillPool.inSlot(type, loadout, s);
            int border = ult ? 0xFFFFD24A : s == selected ? 0xFFFFFFFF : type.getColor();
            g.fill(x - 2, y - 2, x + BOX + 2, y + BOX + 2, border);
            g.fill(x, y, x + BOX, y + BOX, 0xEE101018);
            if (sk != null) g.drawCenteredString(font, shortName(sk), x + BOX / 2, y + 9, 0xFFFFFF);
            g.drawCenteredString(font, ult ? "ULT" : String.valueOf(s + 1), x + BOX / 2, y + BOX - 10, ult ? 0xFFD24A : 0xBBBBBB);
            if (sk != null && mx >= x && mx < x + BOX && my >= y && my < y + BOX) hover = sk.getName().getString() + (ult ? " (ultimate - always equipped)" : "") + "\n" + desc(sk);
        }
        {   // super ultimate: KAs and really big attacks
            int x = slotsLeft() + 5 * (BOX + 8) + 6, y = 40;
            g.fill(x - 2, y - 2, x + BOX + 2, y + BOX + 2, 0xFF8A3CC8);
            g.fill(x, y, x + BOX, y + BOX, 0xEE140A1E);
            g.drawCenteredString(font, "?", x + BOX / 2, y + 9, 0xB070FF);
            g.drawCenteredString(font, "KA", x + BOX / 2, y + BOX - 10, 0xB070FF);
            if (mx >= x && mx < x + BOX && my >= y && my < y + BOX) { var tk = ClientSkillState.ka(); hover = "Super Ultimate - KAs and really big attacks\n" + (tk == null ? type.getDisplayName().getString() + "'s KA hasn't been revealed yet." : "Test KA: " + tk.title() + " - " + KaGuideScreen.does(tk) + "\nPrice: " + tk.cost); }
        }
        g.drawCenteredString(font, "Click a slot, then a skill to put in it - hover anything for details", width / 2, 84, 0x888888);
        // every skill this character can learn
        List<SkillPool.Entry> pool = SkillPool.of(type);
        for (int i = 0; i < pool.size(); i++) {
            SkillPool.Entry e = pool.get(i);
            int x = cardX(i), y = cardY(i);
            boolean open = SkillPool.unlocked(e, ClientSkillState.level(), creative());
            int at = indexOf(loadout, i);
            boolean over = mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H;
            g.fill(x, y, x + CARD_W, y + CARD_H, at >= 0 ? 0xCC23402A : over && open ? 0xCC30304A : 0xCC1A1A24);
            g.fill(x, y, x + 3, y + CARD_H, open ? type.getColor() : 0xFF555555);
            g.drawString(font, e.skill().getName().getString(), x + 8, y + 6, open ? 0xFFFFFF : 0x777777);
            String line = !open ? "Unlocks at Lv " + e.unlockLevel() : at >= 0 ? "Equipped in slot " + (at + 1) : "Karma " + e.skill().getKarmaCost() + "  CD " + e.skill().getCooldownTicks() / 20 + "s";
            g.drawString(font, line, x + 8, y + 20, !open ? 0xAA5555 : at >= 0 ? 0x77DD88 : 0x99AABB);
            if (over) hover = e.skill().getName().getString() + "\n" + desc(e.skill()) + (open ? "" : "\nUnlocks at Character Level " + e.unlockLevel());
        }
        super.render(g, mx, my, pt);
        if (hover != null) {
            List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
            boolean first = true;
            for (String l : hover.split("\n")) {
                if (l.isEmpty()) { lines.add(net.minecraft.util.FormattedCharSequence.EMPTY); continue; }
                lines.addAll(font.split(Component.literal(l).withStyle(first ? net.minecraft.ChatFormatting.YELLOW : l.startsWith("Karma") ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.GRAY), 240));
                first = false;
            }
            g.renderTooltip(font, lines, mx, my);
        }
    }

    @Override
    protected void init() {
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("KA & Element Guide"),
                b -> minecraft.setScreen(new KaGuideScreen(this))).bounds(width - 124, height - 24, 116, 18).build());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int s = 0; s < SkillManager.ULTIMATE_SLOT; s++) {
            int x = slotsLeft() + s * (BOX + 8), y = 40;
            if (mx >= x && mx < x + BOX && my >= y && my < y + BOX) { selected = s; click(); return true; }
        }
        List<SkillPool.Entry> pool = SkillPool.of(type());
        for (int i = 0; i < pool.size(); i++) {
            int x = cardX(i), y = cardY(i);
            if (mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H) {
                if (!SkillPool.unlocked(pool.get(i), ClientSkillState.level(), creative())) return true;
                ModNetwork.CHANNEL.sendToServer(new SetSkillSlotPacket(selected, i));
                // show it right away; the server's answer confirms it
                int[] l = ClientSkillState.loadout().clone();
                for (int k = 0; k < l.length; k++) if (l[k] == i) l[k] = l[selected];
                l[selected] = i;
                ClientSkillState.setLoadout(l, ClientSkillState.level());
                selected = (selected + 1) % SkillManager.ULTIMATE_SLOT;
                click();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
    }

    private static int indexOf(int[] l, int v) { for (int i = 0; i < l.length; i++) if (l[i] == v) return i; return -1; }

    private static String shortName(Skill s) {
        String k = "skill.xenowarriors." + s.getId() + ".short";
        return I18n.exists(k) ? I18n.get(k) : s.getName().getString().substring(0, Math.min(2, s.getName().getString().length()));
    }

    private static String desc(Skill s) {
        String k = "skill.xenowarriors." + s.getId() + ".desc", h = "skill.xenowarriors." + s.getId() + ".how";
        String cd = s.getCooldownTicks() < 20 ? "none" : s.getCooldownTicks() / 20 + "s";
        String stats = "Karma " + s.getKarmaCost() + "   Cooldown " + cd + (s.clashColor() >= 0 ? "   Can clash" : "");
        return stats + "\n\n" + (I18n.exists(k) ? I18n.get(k) : "") + (I18n.exists(h) ? "\n\nHow: " + I18n.get(h) : "");
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
