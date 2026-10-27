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

    @Override
    protected void init() {
        request();
        rebuild();
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
        Entity e = minecraft.level.getEntity(c.entityId());
        if (c.mine() && !c.bot()) return minecraft.player;
        if (e instanceof LivingEntity l && e.getUUID().equals(c.id())) return l;
        PartyBotEntity s = stand_ins.computeIfAbsent(i, k -> com.xenowarriors.bot.BotEntities.BOT.get().create(minecraft.level));
        if (s != null) s.setCharacter(CharacterType.values()[Mth.clamp(c.type(), 0, CharacterType.values().length - 1)]);
        return s;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (my > height * 0.18 && my < feetY() + 14) {
            for (int i = 0; i < SLOTS; i++) if (Math.abs(mx - slotX(i)) < width * 0.1) { selected = selected == i ? -1 : i; rebuild(); return true; }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
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
        // bottom bar
        int by = height - 16;
        g.drawString(font, "[A] Add bot    [I] Invite    [Click] Options    [Esc] Leave", 12, by, 0xFFE0E4FF);
        super.render(g, mx, my, pt);
    }
}
