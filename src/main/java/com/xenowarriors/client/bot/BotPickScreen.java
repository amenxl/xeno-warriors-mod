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
    private static final int CW = 92, CH = 106, GAP = 8;
    /** Bond needed to call a busy one anyway / to give one a job (bot.OffDuty). */
    public static final int OVERRIDE_BOND = 60, ASSIGN_BOND = 50;
    private static final String[][] JOBS = {{"mining", "Go mining"}, {"wood", "Chop wood"}, {"training", "Train"}, {"dmz", "DMZ missions"}, {"building", "Build"}, {"relax", "Relax"}, {"recall", "Call back"}};
    private final Set<String> taken, cooling;
    private final int max, limit;
    private final Map<String, OpenBotPickPacket.Act> acts = new HashMap<>();
    /** The job menu (right-click on a card): whose, and where. */
    private CharacterType menuFor;
    private int menuX, menuY;
    public static String lastCommand;
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
        limit = m.limit();
        for (OpenBotPickPacket.Act a : m.acts()) acts.put(a.id(), a);
        for (CharacterType t : CharacterType.PLAYABLE) if (!taken.contains(t.getId())) choices.add(t);
        ModNetwork.sendToServer(new com.xenowarriors.network.CharLevelsPacket(new int[0]));      // each character's level with you
    }

    private boolean locked(CharacterType t) { return com.xenowarriors.client.ClientSkillState.season() < t.season(); }
    private boolean usable(CharacterType t) { return !locked(t) && !cooling.contains(t.getId()) && max > 0 && !busyBlocked(t); }
    private OpenBotPickPacket.Act act(CharacterType t) { return acts.get(t.getId()); }
    private int actCode(CharacterType t) { OpenBotPickPacket.Act a = act(t); return a == null ? 0 : a.act(); }
    private int bond(CharacterType t) { OpenBotPickPacket.Act a = act(t); return a == null ? 0 : a.bond(); }
    /** Busy off duty and your bond's too low to call them anyway (your own orders never block). */
    public boolean busyBlocked(CharacterType t) { OpenBotPickPacket.Act a = act(t); return a != null && a.act() > 0 && !a.ordered() && a.bond() < OVERRIDE_BOND; }
    private int level(CharacterType t) { return Math.max(1, com.xenowarriors.network.CharLevelsPacket.CLIENT[t.ordinal()]); }

    private int gridTop() { return 44; }
    private int gridBottom() { return height - 46; }       // room under the grid for the scroll hint
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
            if (picked.size() >= max) picked.remove(picked.iterator().next());      // swap out the oldest pick
            picked.add(t);
        }
        confirm.active = !picked.isEmpty();
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.2F));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (menuFor != null) {                                                   // (the job menu: a job, or anywhere else closes it)
            int row = jobAt(mx, my);
            if (row >= 0) assign(menuFor, JOBS[row][0]);
            menuFor = null;
            return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        if (my >= gridTop() && my < gridBottom()) for (int i = 0; i < choices.size(); i++) {
            int[] a = cardAt(i);
            if (mx >= a[0] && mx < a[0] + CW && my >= a[1] && my < a[1] + CH) {
                CharacterType t = choices.get(i);
                if (button == 1) { openJobs(t, (int) mx, (int) my); return true; }
                toggle(t);
                return true;
            }
        }
        return false;
    }

    /** Right-click: the job menu (bond 50+, not Season-locked). */
    public void openJobs(CharacterType t, int x, int y) {
        if (locked(t)) return;
        if (bond(t) < ASSIGN_BOND) {
            minecraft.player.displayClientMessage(Component.literal("You need a bond of " + ASSIGN_BOND + "+ with " + t.getDisplayName().getString() + " to give them a job (yours is " + bond(t) + ")").withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return;
        }
        menuFor = t;
        menuX = Math.min(x, width - 92);
        menuY = Math.min(y, height - JOBS.length * 13 - 18);
    }

    private int jobAt(double mx, double my) {
        if (mx < menuX || mx >= menuX + 90 || my < menuY + 14) return -1;
        int row = (int) ((my - menuY - 14) / 13);
        return row >= 0 && row < JOBS.length ? row : -1;
    }

    /** Gives the job (the server checks it) and opens the screen again with what everyone's doing now. */
    public void assign(CharacterType t, String job) {
        lastCommand = "xeno offduty task " + t.getId() + " " + job;
        minecraft.player.connection.sendCommand(lastCommand);
        minecraft.player.connection.sendCommand("xeno bot");
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
        int n = max;
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        String sub = max <= 0 ? "Your party is full (" + limit + " people and bots)" : "Pick up to " + n + " - they join at the level you have them at   (" + picked.size() + "/" + n + " picked)";
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
            JourneyScreen.face(g, t, x + CW / 2 - 20, y + 8, 40, OffDutyIcons.face(actCode(t), t == CharacterType.ULTIMA, net.minecraft.Util.getMillis() + t.ordinal() * 1300L));
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
            int ac = actCode(t);
            if (ac > 0) {                                                         // (off duty: what they're up to)
                OffDutyIcons.draw(g, ac, x + CW - OffDutyIcons.SIZE - 3, y + 6, net.minecraft.Util.getMillis());
                String lab = t == CharacterType.ULTIMA && ac == OffDutyIcons.TRAINING ? "Weight training" : act(t).label();
                g.pose().pushPose();
                g.pose().translate(x + CW / 2F, y + 87, 0);
                float k2 = Math.min(0.62F, (CW - 4) / (float) Math.max(1, font.width(lab)));
                g.pose().scale(k2, k2, 1F);
                g.drawCenteredString(font, lab, 0, 0, OffDutyIcons.color(ac));
                g.pose().popPose();
            }
            if (sel) g.drawCenteredString(font, "✔ PICKED", x + CW / 2, y + CH - 11, 0xFFE070);
            else if (ac > 0 && !busyBlocked(t) && !act(t).ordered()) g.drawCenteredString(font, "Bond " + bond(t) + ": comes", x + CW / 2, y + CH - 11, 0xFFC080FF);
            else if (busyBlocked(t)) g.drawCenteredString(font, "Busy", x + CW / 2, y + CH - 11, 0xFF9090A0);
            if (cooling.contains(t.getId())) {
                g.fill(x, y, x + CW, y + CH, 0x99300000);
                g.drawCenteredString(font, "Recovering...", x + CW / 2, y + CH - 11, 0xFF8080);
            } else if (!ok) g.fill(x, y, x + CW, y + 82, 0x80000000);
        }
        g.disableScissor();
        if (maxScroll() > 0) g.drawCenteredString(font, scroll < maxScroll() ? "▼ scroll for more" : "▲ scroll up", width / 2, gridBottom() + 2, 0x8899AA);
        else g.drawCenteredString(font, "Right-click a card to give an off-duty job (bond " + ASSIGN_BOND + "+)", width / 2, gridBottom() + 4, 0x667788);
        super.render(g, mx, my, pt);
        if (menuFor != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(menuX - 1, menuY - 1, menuX + 91, menuY + 15 + JOBS.length * 13, 0xFF000000 | (menuFor.getColor() & 0xFFFFFF));
            g.fill(menuX, menuY, menuX + 90, menuY + 14 + JOBS.length * 13, 0xF0101016);
            g.drawString(font, menuFor.getDisplayName().getString() + ":", menuX + 4, menuY + 3, 0xFFFFFF);
            int hr = jobAt(mx, my);
            for (int i = 0; i < JOBS.length; i++) {
                int yy = menuY + 14 + i * 13;
                if (i == hr) g.fill(menuX + 1, yy, menuX + 89, yy + 13, 0xFF303048);
                g.drawString(font, JOBS[i][1], menuX + 6, yy + 3, i == JOBS.length - 1 ? 0xFFAACCFF : 0xFFDDDDDD);
            }
            g.pose().popPose();
            return;
        }
        if (hover == null) for (int i = 0; i < choices.size(); i++) {           // (a busy card still tells what they're up to)
            int[] a = cardAt(i);
            if (actCode(choices.get(i)) > 0 && mx >= a[0] && mx < a[0] + CW && my >= a[1] && my < a[1] + CH && my >= gridTop() && my < gridBottom()) hover = choices.get(i);
        }
        if (hover != null) {
            String extra = actCode(hover) > 0 ? "\n" + act(hover).label() + (busyBlocked(hover) ? "\nBusy - a bond of " + OVERRIDE_BOND + "+ calls them anyway" : act(hover).ordered() ? "" : "\nYour bond (" + bond(hover) + ") calls them anyway") : "";
            List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>(font.split(Component.literal(hover.getFullName().getString() + "\n" + hover.getTitle().getString() + "\n" + hover.getElements().getString() + extra + "\nBond " + bond(hover)), 160));
            g.renderTooltip(font, lines, mx, my);
        }
    }

    private static int mix(int c, float f) {
        int r = (int) (((c >> 16) & 255) * f), gg = (int) (((c >> 8) & 255) * f), bb = (int) ((c & 255) * f);
        return (r << 16) | (gg << 8) | bb;
    }

    @Override public boolean isPauseScreen() { return false; }
}
