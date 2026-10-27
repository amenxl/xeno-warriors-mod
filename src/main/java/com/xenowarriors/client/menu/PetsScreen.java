package com.xenowarriors.client.menu;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ModKeyMappings;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PetActionPacket;
import com.xenowarriors.network.PetListPacket;
import com.xenowarriors.pet.PetCare;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Pets (from the main menu): every fox and winged companion you have - level, health, where they are and, if they ran
 * away from a fight, how long until the Fox Whistle can call them back. Pick one to rename it, change its skin, open
 * its bag, or whistle everyone back. Laid out on a 400x225 grid scaled to the window, in your character's colour.
 */
public class PetsScreen extends Screen {
    private static final int VW = 400, VH = 225, LIST_X = 8, LIST_W = 140, CARD_H = 34, PANEL_X = 156, PANEL_W = 236;
    private final Screen parent;
    private List<PetListPacket.Pet> pets = new ArrayList<>();
    private int selected, listScroll, refresh;
    private long receivedAt;
    private EditBox name;
    private boolean renaming;

    public PetsScreen(Screen parent) {
        super(Component.literal("Pets"));
        this.parent = parent;
    }

    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new PetsScreen(parent));
        ModNetwork.sendToServer(new PetListPacket(List.of()));
    }

    public static void receive(PetListPacket m) {
        if (Minecraft.getInstance().screen instanceof PetsScreen s) {
            s.pets = m.pets();
            s.receivedAt = s.ticks;
            s.selected = Mth.clamp(s.selected, 0, Math.max(0, s.pets.size() - 1));
        }
    }

    private long ticks;
    private Boolean hudWas;

    @Override
    protected void init() {
        if (hudWas == null) { hudWas = minecraft.options.hideGui; minecraft.options.hideGui = true; }
        float s = scale();
        float[] o = origin();
        name = new EditBox(font, (int) (o[0] + (PANEL_X + 8) * s), (int) (o[1] + 46 * s), (int) (140 * s), (int) (14 * s), Component.literal("Name"));
        name.setMaxLength(24);
        name.visible = false;
        addRenderableWidget(name);
    }

    @Override
    public void removed() {
        if (hudWas != null) { minecraft.options.hideGui = hudWas; hudWas = null; }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void tick() {
        ticks++;
        if (++refresh % 40 == 0) ModNetwork.sendToServer(new PetListPacket(List.of()));
    }

    private float scale() { return Math.min(width / (float) VW, height / (float) VH); }
    private float[] origin() { float s = scale(); return new float[]{(width - VW * s) / 2F, (height - VH * s) / 2F}; }

    private PetListPacket.Pet sel() { return pets.isEmpty() ? null : pets.get(Mth.clamp(selected, 0, pets.size() - 1)); }

    private int awayLeft(PetListPacket.Pet p) { return (int) Math.max(0, p.awayLeft() - (ticks - receivedAt)); }

    private void send(int action, int arg, String text) {
        PetListPacket.Pet p = sel();
        ModNetwork.sendToServer(new PetActionPacket(p == null ? 0 : p.kind(), p == null ? 0 : p.index(), action, arg, text == null ? "" : text));
    }

    // ------------------------------------------------------------------ buttons (virtual coords)

    private record Btn(String label, float x, float y, float w, float h, boolean enabled, Runnable run) {}

    private List<Btn> buttons() {
        List<Btn> l = new ArrayList<>();
        PetListPacket.Pet p = sel();
        l.add(new Btn("Whistle everyone back", PANEL_X + PANEL_W - 118, VH - 22, 112, 15, !pets.isEmpty(), () -> send(PetCare.WHISTLE, 0, null)));
        if (p == null) return l;
        boolean here = p.status() == 0 && p.entityId() >= 0;
        l.add(new Btn(renaming ? "Save name" : "Rename", PANEL_X + 152, 46, 50, 14, true, () -> {
            if (renaming) { send(PetCare.RENAME, 0, name.getValue()); renaming = false; name.visible = false; }
            else { renaming = true; name.visible = true; name.setValue(p.name()); setFocused(name); name.setFocused(true); }
        }));
        l.add(new Btn("◀", PANEL_X + 96, 128, 14, 14, true, () -> cycleSkin(p, -1)));
        l.add(new Btn("▶", PANEL_X + PANEL_W - 22, 128, 14, 14, true, () -> cycleSkin(p, 1)));
        l.add(new Btn("Open bag", PANEL_X + 8, VH - 22, 60, 15, here, () -> send(PetCare.BAG, 0, null)));
        return l;
    }

    private void cycleSkin(PetListPacket.Pet p, int dir) {
        int n = p.skins().size();
        if (n == 0) return;
        int s = p.skin();
        for (int k = 0; k < n; k++) {
            s = Math.floorMod(s + dir, n);
            if (p.kind() != 0 || p.level() >= p.skinLevels().get(s)) break;
        }
        send(PetCare.SKIN, s, null);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (renaming && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) { send(PetCare.RENAME, 0, name.getValue()); renaming = false; name.visible = false; return true; }
        if (renaming && key == GLFW.GLFW_KEY_ESCAPE) { renaming = false; name.visible = false; return true; }
        if (renaming) return super.keyPressed(key, scan, mods);
        if (ModKeyMappings.STATS_MENU.matches(key, scan)) { minecraft.setScreen(null); return true; }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) { selected = Math.max(0, selected - 1); return true; }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) { selected = Math.min(pets.size() - 1, selected + 1); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        int max = Math.max(0, pets.size() * (CARD_H + 4) - (VH - 40));
        listScroll = Mth.clamp(listScroll - (int) Math.signum(d) * (CARD_H + 4), 0, max);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (name.visible && name.mouseClicked(mx, my, button)) return true;
        float s = scale();
        float[] o = origin();
        double vx = (mx - o[0]) / s, vy = (my - o[1]) / s;
        if (vx < 30 && vy < 22) { onClose(); return true; }
        for (Btn b : buttons()) if (b.enabled && vx >= b.x && vx < b.x + b.w && vy >= b.y && vy < b.y + b.h) { b.run.run(); click(); return true; }
        for (int i = 0; i < pets.size(); i++) {
            float y = 30 + i * (CARD_H + 4) - listScroll;
            if (vx >= LIST_X && vx < LIST_X + LIST_W && vy >= y && vy < y + CARD_H && vy >= 28) {
                if (selected != i) { renaming = false; name.visible = false; }
                selected = i; click(); return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.3F));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        CharacterType type = ClientCharacterData.getLocal();
        int col = type == CharacterType.NONE ? 0x8090A0 : type.getColor() & 0xFFFFFF;
        g.fillGradient(0, 0, width, height, 0xF0101016, 0xF0060608);
        float s = scale();
        float[] o = origin();
        double vx = (mx - o[0]) / s, vy = (my - o[1]) / s;
        g.pose().pushPose();
        g.pose().translate(o[0], o[1], 0);
        g.pose().scale(s, s, 1F);

        // header
        g.fill(4, 4, 26, 20, 0xFF16161C);
        box(g, 4, 4, 26, 20, 0xFF000000 | col);
        g.drawString(font, "↶", 11, 8, vx < 30 && vy < 22 ? 0xFFFFFF : light(col), false);
        g.fill(30, 4, 30 + font.width("PETS") * 2 + 12, 20, 0xFF16161C);
        sc(g, "PETS", 36, 5.5F, 2F, 0xFFFFFF);
        String note = "Enemies mostly leave pets alone. A pet that runs from a fight needs " + PetCare.fmt(PetCare.COOLDOWN) + " before the whistle can call it back.";
        sc(g, note, VW - 6 - font.width(note) * 0.55F, 22, 0.55F, 0x8A8A9A);

        // the list
        g.enableScissor((int) (o[0] + LIST_X * s), (int) (o[1] + 28 * s), (int) (o[0] + (LIST_X + LIST_W) * s), (int) (o[1] + (VH - 4) * s));
        for (int i = 0; i < pets.size(); i++) {
            PetListPacket.Pet p = pets.get(i);
            int y = 30 + i * (CARD_H + 4) - listScroll;
            boolean on = i == selected, over = vx >= LIST_X && vx < LIST_X + LIST_W && vy >= y && vy < y + CARD_H;
            g.fill(LIST_X, y, LIST_X + LIST_W, y + CARD_H, on ? 0xFF000000 | dark(col) : over ? 0xFF24242E : 0xFF18181F);
            g.fill(LIST_X, y, LIST_X + 3, y + CARD_H, 0xFF000000 | (p.kind() == 0 ? 0xFF9A40 : 0xC77DFF));
            if (on) box(g, LIST_X, y, LIST_X + LIST_W, y + CARD_H, 0xFF000000 | light(col));
            g.drawString(font, font.plainSubstrByWidth(p.name(), LIST_W - 12), LIST_X + 7, y + 4, 0xFFFFFF, false);
            sc(g, p.species() + (p.kind() == 0 ? "  •  Lv " + p.level() : ""), LIST_X + 7, y + 14, 0.75F, 0xA0A0B0);
            String[] st = statusText(p);
            sc(g, st[0], LIST_X + 7, y + 23, 0.75F, Integer.parseInt(st[1], 16));
        }
        g.disableScissor();
        if (pets.isEmpty()) {
            int y = 34;
            for (FormattedCharSequence l : font.split(Component.literal("You don't have any pets yet. Mikiko tames foxes with rabbit meat; Rhalix befriends bats, parrots, chickens and bees."), LIST_W))
            { g.drawString(font, l, LIST_X, y, 0xA0A0B0, false); y += 10; }
        }

        // the selected pet
        g.fill(PANEL_X, 28, PANEL_X + PANEL_W, VH - 4, 0xEE141418);
        box(g, PANEL_X, 28, PANEL_X + PANEL_W, VH - 4, 0xFF34343E);
        PetListPacket.Pet p = sel();
        if (p != null) {
            if (!renaming) sc(g, font.plainSubstrByWidth(p.name(), 120), PANEL_X + 8, 34, 1.6F, 0xFFFFFF);
            sc(g, p.species().toUpperCase(java.util.Locale.ROOT) + (p.kind() == 0 ? "  •  LV " + p.level() : ""), PANEL_X + 8, 64, 0.75F, light(col));
            // model
            LivingEntity e = p.entityId() >= 0 && minecraft.level != null && minecraft.level.getEntity(p.entityId()) instanceof LivingEntity le ? le : null;
            g.fill(PANEL_X + 8, 74, PANEL_X + 90, 160, 0xFF0E0E12);
            box(g, PANEL_X + 8, 74, PANEL_X + 90, 160, 0xFF000000 | dark(col));
            if (e != null) InventoryScreen.renderEntityInInventoryFollowsMouse(g, PANEL_X + 49, 150, p.kind() == 0 ? 52 : 40, (float) (PANEL_X + 49 - vx), (float) (110 - vy), e);
            else { String t = p.status() >= 2 && p.status() <= 3 ? "(away)" : "(not nearby)"; sc(g, t, PANEL_X + 49 - font.width(t) * 0.4F, 114, 0.8F, 0x707080); }

            int rx = PANEL_X + 96, rw = PANEL_W - 104, ry = 74;
            if (p.kind() == 0) {
                row(g, rx, ry, rw, "Level", p.level() + (p.xpNeed() > 0 ? "" : " (MAX)"), 0xFFFFFF);
                if (p.xpNeed() > 0) bar(g, rx, ry + 11, rw, p.xp() / p.xpNeed(), 0xFF000000 | col);
                ry += 16;
            }
            row(g, rx, ry, rw, "Health", p.maxHealth() > 0 && p.entityId() >= 0 ? Math.round(p.health()) + " / " + Math.round(p.maxHealth()) : "-", 0xFF8080);
            if (p.maxHealth() > 0 && p.entityId() >= 0) bar(g, rx, ry + 11, rw, p.health() / p.maxHealth(), 0xFFE05050);
            ry += 16;
            if (p.kind() == 0) { row(g, rx, ry, rw, "Defeated", String.valueOf(p.kills()), 0xFFFFFF); ry += 14; }
            String[] st = statusText(p);
            row(g, rx, ry, rw, "Status", st[0], Integer.parseInt(st[1], 16));
            // skin
            String skin = p.skins().isEmpty() ? "-" : p.skins().get(Mth.clamp(p.skin(), 0, p.skins().size() - 1));
            g.fill(rx, 128, rx + rw, 142, 0xFF101016);
            box(g, rx, 128, rx + rw, 142, 0xFF34343E);
            g.drawCenteredString(font, "Skin: " + skin, rx + rw / 2, 131, 0xFFFFFF);
            String nextLock = nextLockedSkin(p);
            if (nextLock != null) sc(g, nextLock, rx, 145, 0.7F, 0x8A8A9A);
            if (p.status() == 2) {
                int left = awayLeft(p);
                float f = 1F - left / (float) PetCare.COOLDOWN;
                sc(g, "RAN AWAY - RECOVERING", PANEL_X + 8, 166, 0.75F, 0xFF9A7A);
                bar(g, PANEL_X + 8, 175, PANEL_W - 16, f, 0xFFFF9A40);
                sc(g, PetCare.fmt(left) + " until the whistle can call " + p.name() + " back", PANEL_X + 8, 180, 0.75F, 0xD0D0DC);
            }
        }
        // buttons
        for (Btn b : buttons()) {
            boolean over = b.enabled && vx >= b.x && vx < b.x + b.w && vy >= b.y && vy < b.y + b.h;
            g.fill((int) b.x, (int) b.y, (int) (b.x + b.w), (int) (b.y + b.h), !b.enabled ? 0xFF2A2A30 : over ? 0xFF000000 | light(col) : 0xFF000000 | col);
            g.drawCenteredString(font, b.label, (int) (b.x + b.w / 2), (int) (b.y + (b.h - 8) / 2), !b.enabled ? 0x707078 : over ? 0x101010 : 0xFFFFFF);
        }
        g.pose().popPose();
        if (name.visible) name.render(g, mx, my, pt);
    }

    private String nextLockedSkin(PetListPacket.Pet p) {
        if (p.kind() != 0) return null;
        int best = Integer.MAX_VALUE; String nm = null;
        for (int i = 0; i < p.skins().size(); i++) {
            int lv = p.skinLevels().get(i);
            if (lv > p.level() && lv < best) { best = lv; nm = p.skins().get(i); }
        }
        return nm == null ? null : "Next skin: " + nm + " at Lv " + best;
    }

    private String[] statusText(PetListPacket.Pet p) {
        return switch (p.status()) {
            case 0 -> new String[]{"With you", "77DD88"};
            case 1 -> new String[]{"On your back", "77DD88"};
            case 2 -> new String[]{"Ran away - back in " + PetCare.fmt(awayLeft(p)), "FF9A7A"};
            case 3 -> new String[]{"Ran away - ready, use the whistle", "FFD24A"};
            default -> new String[]{"Somewhere else", "A0A0B0"};
        };
    }

    private void row(GuiGraphics g, int x, int y, int w, String label, String value, int vc) {
        sc(g, label, x, y + 1, 0.8F, 0xA0A0B0);
        String v = font.plainSubstrByWidth(value, (int) ((w - 40) / 0.8F));
        sc(g, v, x + w - font.width(v) * 0.8F, y + 1, 0.8F, vc);
    }

    private static void bar(GuiGraphics g, int x, int y, int w, float f, int c) {
        g.fill(x, y, x + w, y + 2, 0xFF2A2A34);
        g.fill(x, y, x + Math.round(w * Mth.clamp(f, 0, 1)), y + 2, c);
    }

    private static void box(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c); g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c); g.fill(x1 - 1, y0, x1, y1, c);
    }

    private void sc(GuiGraphics g, String s, float x, float y, float k, int c) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1F);
        g.drawString(font, s, 0, 0, c, false);
        g.pose().popPose();
    }

    private static int light(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return ((r + (255 - r) * 45 / 100) << 16) | ((g + (255 - g) * 45 / 100) << 8) | (b + (255 - b) * 45 / 100);
    }

    private static int dark(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return ((r * 30 / 100) << 16) | ((g * 30 / 100) << 8) | (b * 30 / 100);
    }
}
