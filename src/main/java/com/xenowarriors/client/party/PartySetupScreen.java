package com.xenowarriors.client.party;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.BotActionPacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PartyCardsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Party Setup: the whole party standing side by side under a starry sky (like a gacha game's party screen). Each slot
 * shows the member in 3D with their name, level and pets above them; empty slots are for adding a bot or inviting
 * someone. Click a member for their options (a bot's mode / menu / dismiss, kick, leave).
 */
public class PartySetupScreen extends Screen {
    private static final int SLOTS = 4;
    private static PartyCardsPacket last;
    private static int version;

    private int seen = -1, selected = -1, ticks;
    private final List<Button> actions = new ArrayList<>();
    private final Map<Integer, PartyBotEntity> stand_ins = new HashMap<>();
    private final Screen parent;

    public PartySetupScreen(Screen parent) {
        super(Component.literal("Party Setup"));
        this.parent = parent;
    }

    public PartySetupScreen() { this(null); }

    public static void receive(PartyCardsPacket p) { last = p; version++; }

    private static void request() { ModNetwork.sendToServer(new PartyCardsPacket(List.of(), false, false)); }

    private List<PartyCardsPacket.Card> cards() { return last == null ? List.of() : last.cards(); }

    /** Party Swap (from the server): a lost bot teammate is replaced by another of your characters. */
    public static boolean swapOn;

    private static Component swapLabel() { return Component.literal("Party Swap: " + (swapOn ? "ON" : "OFF")); }

    /** The Team Synergy panel (synergies for this party, their bond levels, and the bond hearts) is open. */
    private boolean synergyOpen;
    private Button synergyButton;

    private Component synergyLabel() { return Component.literal(synergyOpen ? "Close Synergy" : "Synergy & Bond [S]"); }

    /** UI test: open / close the synergy panel. */
    public void testSynergy(boolean open) { synergyOpen = open; if (synergyButton != null) synergyButton.setMessage(synergyLabel()); }
    public static PartyCardsPacket lastCards() { return last; }

    @Override
    protected void init() {
        request();
        rebuild();
        addRenderableWidget(Button.builder(swapLabel(), b -> { swapOn = !swapOn; ModNetwork.sendToServer(new com.xenowarriors.network.PartySwapPacket(swapOn)); b.setMessage(swapLabel()); })
                .bounds(width - 122, 24, 110, 18)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("When one of your bot teammates is lost (not revived in time), another of your characters joins in their place to keep the party full.")))
                .build());
        synergyButton = addRenderableWidget(Button.builder(synergyLabel(), b -> testSynergy(!synergyOpen))
                .bounds(12, 24, 110, 18)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Which team synergies your party has (or could have), what they do, and your bond with each teammate.")))
                .build());
    }

    @Override
    public void tick() {
        if (++ticks % 40 == 0) request();
        if (seen != version) { seen = version; rebuild(); }
    }

    private int slotX(int i) { return (int) (width * (0.14 + 0.24 * i)); }
    private int feetY() { return (int) (height * 0.78); }

    // ------------------------------------------------------------------ the buttons for the selected slot

    private void rebuild() {
        for (Button b : actions) removeWidget(b);
        actions.clear();
        List<PartyCardsPacket.Card> cs = cards();
        if (selected >= SLOTS) selected = -1;
        int bw = 92, by = feetY() + 22;
        List<Object[]> list = new ArrayList<>();                                     // {label, runnable}
        if (selected >= 0 && selected < cs.size()) {
            PartyCardsPacket.Card c = cs.get(selected);
            if (c.bot() && c.mine()) {
                PartyBotEntity b = bot(c);
                String mode = b != null ? PartyBotEntity.MODES[b.mode()] : "Mode";
                list.add(new Object[]{"Mode: " + mode, (Runnable) () -> { if (b != null) ModNetwork.sendToServer(new BotActionPacket(c.entityId(), PartyBots.ACT_MODE, (b.mode() + 1) % 3)); }});
                list.add(new Object[]{"Bot menu", (Runnable) () -> ModNetwork.sendToServer(new BotActionPacket(c.entityId(), PartyBots.ACT_MENU, 0))});
                list.add(new Object[]{"Dismiss", (Runnable) () -> { ModNetwork.sendToServer(new BotActionPacket(c.entityId(), PartyBots.ACT_DISMISS, 0)); selected = -1; }});
            } else if (!c.bot() && c.mine()) {
                if (last.inParty()) list.add(new Object[]{"Leave party", (Runnable) () -> { minecraft.player.connection.sendCommand("xeno party leave"); selected = -1; }});
                list.add(new Object[]{"Change warrior", (Runnable) () -> minecraft.player.connection.sendCommand("xeno choose")});
            } else if (!c.bot() && last.leader()) {
                list.add(new Object[]{"Kick", (Runnable) () -> { minecraft.player.connection.sendCommand("xeno party kick " + c.name()); selected = -1; }});
            }
        } else if (selected >= 0) {                                                  // an empty slot
            list.add(new Object[]{"Add bot", (Runnable) this::addBot});
            list.add(new Object[]{"Invite player", (Runnable) this::invite});
        }
        if (selected >= 0) {
            int total = list.size() * (bw + 4) - 4;
            int x = Mth.clamp(slotX(selected) - total / 2, 6, width - total - 6);
            for (Object[] o : list) {
                Runnable r = (Runnable) o[1];
                Button b = Button.builder(Component.literal((String) o[0]), btn -> { r.run(); request(); }).bounds(x, by, bw, 18).build();
                actions.add(addRenderableWidget(b));
                x += bw + 4;
            }
        }
    }

    private void addBot() { minecraft.player.connection.sendCommand("xeno bot"); }

    private void invite() { minecraft.setScreen(new ChatScreen("/xeno party invite ")); }

    private PartyBotEntity bot(PartyCardsPacket.Card c) {
        return minecraft.level != null && minecraft.level.getEntity(c.entityId()) instanceof PartyBotEntity b ? b : null;
    }

    /** What to draw for a card: the real entity if it's loaded here, otherwise a stand-in of their character. */
    private LivingEntity body(int i, PartyCardsPacket.Card c) {
        if (minecraft.level == null) return null;
        CharacterType ct = CharacterType.values()[Mth.clamp(c.type(), 0, CharacterType.values().length - 1)];
        if (c.mine() && !c.bot() && ct == CharacterType.YOU) return minecraft.player;     // (a You character: only you have that skin)
        // otherwise always a stand-in, even for yourself: real players keep their world facing and can show their back
        PartyBotEntity s = stand_ins.computeIfAbsent(i, k -> com.xenowarriors.bot.BotEntities.BOT.get().create(minecraft.level));
        if (s != null) s.setCharacter(CharacterType.values()[Mth.clamp(c.type(), 0, CharacterType.values().length - 1)]);
        return s;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (synergyOpen) return true;                                  // (the panel covers the slots)
        if (my > height * 0.18 && my < feetY() + 14) {
            for (int i = 0; i < SLOTS; i++) if (Math.abs(mx - slotX(i)) < width * 0.1) { selected = selected == i ? -1 : i; rebuild(); return true; }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_S) { testSynergy(!synergyOpen); return true; }
        if (key == GLFW.GLFW_KEY_A) { addBot(); return true; }
        if (key == GLFW.GLFW_KEY_I) { invite(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g) {
        int h = height, w = width, horizon = (int) (h * 0.72);
        g.fillGradient(0, 0, w, horizon, 0xFF0B1240, 0xFF4A3C9C);                  // night sky down to a violet horizon
        g.fillGradient(0, horizon - 40, w, horizon, 0x00C8B4F0, 0x80E8C8F8);         // glow at the horizon
        g.fillGradient(0, horizon, w, h, 0xFF4A4AA8, 0xFF151A52);                   // the glassy floor
        g.fillGradient(0, horizon, w, horizon + 18, 0x70F0D8FF, 0x00F0D8FF);
        float t = (ticks + minecraft.getFrameTime()) / 20F;
        Random r = new Random(42);
        // a milky-way band sweeping up to the top right
        for (int k = 0; k < 90; k++) {
            float u = r.nextFloat();
            int cx = (int) (w * (0.35 + 0.6 * u)), cy = (int) (h * (0.62 - 0.6 * u) + (r.nextFloat() - 0.5) * h * 0.18);
            int s = 4 + r.nextInt(16);
            int col = r.nextBoolean() ? 0x0E7EE0FF : 0x0CF0A0E8;
            g.fill(cx - s, cy - s / 2, cx + s, cy + s / 2, col);
        }
        // stars, twinkling
        for (int k = 0; k < 170; k++) {
            int x = r.nextInt(w), y = r.nextInt(horizon);
            float tw = 0.5F + 0.5F * Mth.sin(t * (1 + r.nextFloat() * 2) + k);
            int a = (int) (90 + 165 * tw * r.nextFloat());
            int sz = r.nextInt(12) == 0 ? 2 : 1;
            g.fill(x, y, x + sz, y + sz, (a << 24) | (r.nextBoolean() ? 0xFFFFFF : 0xBFE4FF));
            if (sz == 2 && tw > 0.8F) { g.fill(x - 2, y, x + 4, y + 1, (a / 3 << 24) | 0xFFFFFF); g.fill(x, y - 2, x + 1, y + 4, (a / 3 << 24) | 0xFFFFFF); }
        }
        // sparkles drifting up off the floor
        for (int k = 0; k < 40; k++) {
            int x = r.nextInt(w);
            float life = (t * 0.15F + r.nextFloat()) % 1F;
            int y = (int) (h - life * (h - horizon + 30));
            g.fill(x, y, x + 1, y + 1, ((int) (200 * (1 - life)) << 24) | 0xE8F4FF);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        List<PartyCardsPacket.Card> cs = cards();
        int feet = feetY(), size = (int) Math.max(24, height * 0.2);
        for (int i = 0; i < SLOTS; i++) {
            int x = slotX(i);
            boolean sel = i == selected, hover = Math.abs(mx - x) < width * 0.1 && my > height * 0.18 && my < feet + 14;
            // a soft pool of light where they stand (and a beam over the selected one)
            for (int k = 0; k < 6; k++) {
                int rw = (int) (size * (0.55 - k * 0.07)), a = 0x18 + k * 6;
                g.fill(x - rw, feet - 2 + k / 2, x + rw, feet + 3 - k / 2, (a << 24) | 0xE8DAFF);
            }
            if (sel) g.fillGradient(x - 6, (int) (height * 0.12), x + 6, feet, 0x00FFFFFF, 0x40E8F0FF);
            if (i < cs.size()) {
                PartyCardsPacket.Card c = cs.get(i);
                LivingEntity body = body(i, c);
                if (body != null) InventoryScreen.renderEntityInInventoryFollowsMouse(g, x, feet, size, x - mx, (float) (feet - size * 1.6 - my), body);
                CharacterType t = CharacterType.values()[Mth.clamp(c.type(), 0, CharacterType.values().length - 1)];
                int top = feet - (int) (size * 2.25) - 30;
                // name (with a little diamond in their colour), level, pets
                String name = c.bot() ? t.getDisplayName().getString() : c.name();
                int nw = font.width(name) + 12;
                g.drawString(font, "◆", x - nw / 2, top, t.getColor() | 0xFF000000);
                g.drawString(font, name, x - nw / 2 + 12, top, 0xFFFFFFFF);
                g.drawCenteredString(font, "Lv. " + c.level(), x, top + 11, 0xFFD8E4FF);
                int py = top + 24;
                if (c.bond() >= 0) {                                          // your bond with them, and how close the next level is
                    String bl = "\u2665 Bond " + c.bond() + (c.bond() >= com.xenowarriors.party.Bonds.MAX ? " MAX" : "");
                    g.drawCenteredString(font, bl, x, py, 0xFFFF9AD8);
                    int bw2 = 40;
                    g.fill(x - bw2 / 2, py + 9, x + bw2 / 2, py + 11, 0xFF2A2050);
                    g.fill(x - bw2 / 2, py + 9, x - bw2 / 2 + Math.round(bw2 * Mth.clamp(c.bondXp(), 0F, 1F)), py + 11, 0xFFFF7AC8);
                    py += 14;
                }
                List<String> pets = c.pets();
                for (int k = 0; k < Math.min(3, pets.size()); k++) {
                    String p = pets.get(k);
                    boolean fox = p.startsWith("fox:");
                    String label = (fox ? "Fox: " : "Pet: ") + p.substring(p.indexOf(':') + 1);
                    g.drawCenteredString(font, label, x, py, fox ? 0xFFFFB060 : 0xFFA8E8FF);
                    py += 10;
                }
                if (pets.size() > 3) g.drawCenteredString(font, "+" + (pets.size() - 3) + " more pets", x, py, 0xFFAAAAAA);
                // under their feet: who they are in the party
                String tag = c.bot() ? "Bot of " + c.owner() : (c.leader() ? "★ Leader" : "") + (c.mine() ? (c.leader() ? " • You" : "You") : "");
                g.drawCenteredString(font, tag, x, feet + 6, c.bot() ? 0xFF9FB4E0 : 0xFFFFE070);
                if (hover && !sel) g.drawCenteredString(font, "click for options", x, feet + 16, 0x80FFFFFF);
            } else {
                // an empty slot: a glowing plus to add someone
                int cy = feet - size;
                int a = hover || sel ? 0xC0 : 0x70;
                g.fill(x - 14, cy - 2, x + 14, cy + 2, (a << 24) | 0xFFFFFF);
                g.fill(x - 2, cy - 14, x + 2, cy + 14, (a << 24) | 0xFFFFFF);
                g.drawCenteredString(font, "Empty", x, feet + 6, 0xFF8890C0);
                if (hover && !sel) g.drawCenteredString(font, "add a bot or invite", x, feet + 16, 0x80FFFFFF);
            }
        }
        // top bar
        g.drawString(font, "Party Setup", 12, 10, 0xFFFFFFFF);
        String count = "Team " + Math.min(cs.size(), SLOTS) + " / " + SLOTS;
        g.drawString(font, count, width - font.width(count) - 12, 10, 0xFFFFFFFF);
        for (int i = 0; i < SLOTS; i++) {                                            // the little row of diamonds
            int dx = width / 2 - 30 + i * 20;
            g.drawString(font, i < cs.size() ? "◆" : "◇", dx, 10, i == selected ? 0xFFFFFFFF : 0xFFB8C0F0);
        }
        // the active synergies, in a line under the top bar
        if (last != null && !synergyOpen) {
            StringBuilder on = new StringBuilder();
            for (PartyCardsPacket.Syn y : last.synergies()) if (y.state() == 2) on.append(on.length() == 0 ? "" : "   ").append("\u2726 ").append(y.name()).append(" (bond ").append(y.bond()).append(")");
            String line = on.length() == 0 ? "No synergy active - press S to see which teammates would make one" : "Synergy: " + on;
            g.drawCenteredString(font, line, width / 2, 46, on.length() == 0 ? 0xFF9AA0C8 : 0xFF8CFFB0);
            if (last.bondHearts() > 0) g.drawCenteredString(font, "Best bond " + last.bestBond() + ": +" + last.bondHearts() + " max hearts", width / 2, 57, 0xFFFF9AD8);
        }
        // bottom bar
        int by = height - 16;
        g.drawString(font, "[A] Add bot    [I] Invite    [S] Synergy    [Click] Options    [Esc] Leave", 12, by, 0xFFE0E4FF);
        super.render(g, mx, my, pt);
        if (synergyOpen) synergyPanel(g);
    }

    private void small(GuiGraphics g, String t, float x, float y, float k, int c) { small(g, t, x, y, k, c, 10000); }

    private void small(GuiGraphics g, String t, float x, float y, float k, int c, int maxW) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1F);
        g.drawString(font, font.plainSubstrByWidth(t, (int) (maxW / k)), 0, 0, c, false);
        g.pose().popPose();
    }

    /** The Team Synergy panel: every synergy, whether it's on, what it does at your bond, and its downside fading out. */
    private void synergyPanel(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        List<PartyCardsPacket.Syn> list = last == null ? List.of() : last.synergies();
        int pw = Math.min(600, width - 24), px = (width - pw) / 2, py = 46;
        String help = "Bond grows by fighting together, kills and revives. Higher bond = stronger synergy; downsides fade out by bond " + com.xenowarriors.party.Bonds.NO_DOWNSIDE + ".";
        var helpLines = font.split(Component.literal(help), pw - 16);
        int cols = pw >= 380 ? 2 : 1, rowH = 31, rows = (list.size() + cols - 1) / cols;
        int head = 20 + helpLines.size() * 9 + 4;
        int ph = Math.min(height - py - 20, head + Math.max(1, rows) * rowH + 4);
        g.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, 0xFF8C78E8);
        g.fill(px, py, px + pw, py + ph, 0xF0101030);
        g.drawString(font, "TEAM SYNERGY", px + 8, py + 6, 0xFFFFFFFF);
        String hb = last == null ? "" : "Best bond in party: " + last.bestBond() + "  \u2192  +" + last.bondHearts() + " max hearts (+" + com.xenowarriors.party.Bonds.heartsPer10() + " per 10 bond)";
        g.drawString(font, hb, px + pw - 8 - font.width(hb), py + 6, 0xFFFF9AD8);
        int hy = py + 18;
        for (var l : helpLines) { g.drawString(font, l, px + 8, hy, 0xFF9AA0C8, false); hy += 9; }
        if (list.isEmpty()) g.drawString(font, "Loading...", px + 8, py + head, 0xFF9AA0C8);
        int cw = (pw - 12) / cols;
        for (int i = 0; i < list.size(); i++) {
            PartyCardsPacket.Syn s = list.get(i);
            int x = px + 6 + (i % cols) * cw, y = py + head + (i / cols) * rowH, w = cw - 4;
            if (y + rowH - 3 > py + ph) break;
            int dot = s.state() == 2 ? 0xFF50F080 : s.state() == 1 ? 0xFFF0C040 : 0xFF505070;
            g.fill(x, y, x + w, y + rowH - 3, s.state() == 2 ? 0xC0183A28 : 0xC01A1A36);
            g.fill(x, y, x + 3, y + rowH - 3, dot);
            String state = s.state() == 2 ? "ACTIVE \u2665 " + s.bond() : s.state() == 1 ? "needs " + s.needs() + " (bond " + s.bond() + ")" : "not for you";
            g.drawString(font, s.name(), x + 7, y + 2, s.state() == 0 ? 0xFF8A8AA8 : 0xFFFFFFFF);
            small(g, state, x + w - 4 - font.width(state) * 0.75F, y + 3, 0.75F, dot);
            small(g, s.members() + ":  " + s.effect(), x + 7, y + 12, 0.7F, s.state() == 0 ? 0xFF707090 : 0xFFD8E0FF, w - 10);
            if (!s.downside().isEmpty()) small(g, "\u26a0 " + s.downside(), x + 7, y + 20, 0.7F, s.downside().startsWith("(") ? 0xFF8A8AA8 : 0xFFFFB0A0, w - 10);
        }
        g.pose().popPose();
    }
}
