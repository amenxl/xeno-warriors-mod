package com.xenowarriors.client.menu;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.math.Axis;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ClientMoney;
import com.xenowarriors.client.KaGuideScreen;
import com.xenowarriors.client.ModKeyMappings;
import com.xenowarriors.client.PacketScreens;
import com.xenowarriors.client.PlayerStatsScreen;
import com.xenowarriors.client.SkillLoadoutScreen;
import com.xenowarriors.client.cel.Cel;
import com.xenowarriors.client.portrait.PortraitScreen;
import com.xenowarriors.network.PlayerStatsPacket;
import com.xenowarriors.network.PlayerStatsRequestPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

import static com.xenowarriors.client.portrait.PortraitScreen.*;

/**
 * The M menu, laid out like an anime game's main menu: your character's art big on the left (their hero pose,
 * cel-shaded) on a panel in their colour, the menu in big letters beside it, Money and stats top right, and the
 * Collection Book bottom right. The whole UI takes the colour of the character you're playing.
 */
public class MainMenuScreen extends Screen {
    private record Entry(String name, String help, Runnable open) {}

    private final List<Entry> entries;
    private int selected;
    private final long opened = Util.getMillis();
    private PlayerStatsPacket.Entry self;
    private int refresh;

    public MainMenuScreen() {
        super(Component.literal("Main Menu"));
        entries = List.of(
                new Entry("Warriors", "See every warrior online: level, stats and Karma.", () -> PlayerStatsScreen.openTab(this, 0)),
                new Entry("My Warrior", "Your levels, training and progress.", () -> PlayerStatsScreen.openTab(this, 1)),
                new Entry("Outfits", "Change your outfit.", () -> PlayerStatsScreen.openTab(this, 2)),
                new Entry("Skills", "Set up your skill loadout.", () -> minecraft.setScreen(new SkillLoadoutScreen(this))),
                new Entry("KAs", "Your Karma Attacks and how to use them.", () -> minecraft.setScreen(new KaGuideScreen(this))),
                new Entry("Party", "Your party, bot teammates and pets.", () -> minecraft.setScreen(new com.xenowarriors.client.party.PartySetupScreen(this))),
                new Entry("Settings", "Cel-shading, HUD, Karma Sensing and combat options.", () -> PlayerStatsScreen.openTab(this, 3)));
    }

    /** (UI test) highlight this entry. */
    public void select(int i) { selected = Mth.clamp(i, 0, entries.size() - 1); }

    public void receive(List<PlayerStatsPacket.Entry> list) {
        for (PlayerStatsPacket.Entry e : list) if (e.self()) self = e;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        if (++refresh % 40 == 0) ModNetwork.sendToServer(new PlayerStatsRequestPacket());
    }

    // ------------------------------------------------------------------ layout

    private float step() { return height * 0.092F; }
    private float listX() { return width * 0.40F; }
    private float listY() { return height * 0.17F; }
    private float bigScale() { return step() * 0.92F / 9F; }

    private int entryAt(double mx, double my) {
        float s = step();
        for (int i = 0; i < entries.size(); i++) {
            float y = listY() + i * s;
            if (mx >= listX() - 6 && mx <= listX() + width * 0.3F && my >= y - 2 && my < y + s - 2) return i;
        }
        return -1;
    }

    private float[] bookBox() {             // x, y, w, h of the Collection card
        float bw = width * 0.12F, bh = height * 0.2F;
        return new float[]{width * 0.84F, height * 0.62F, bw, bh};
    }

    private boolean overBook(double mx, double my) {
        float[] b = bookBox();
        return mx >= b[0] - 4 && mx <= b[0] + b[2] + 4 && my >= b[1] - 14 && my <= b[1] + b[3] + 4;
    }

    private void openCollection() {
        if (!PacketScreens.collectionUnlocked) return;
        ModNetwork.sendToServer(new com.xenowarriors.network.CollectionActionPacket(0));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ModKeyMappings.STATS_MENU.matches(key, scan)) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) { selected = (selected + entries.size() - 1) % entries.size(); click(); return true; }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) { selected = (selected + 1) % entries.size(); click(); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER) { entries.get(selected).open.run(); return true; }
        if (key == GLFW.GLFW_KEY_C) { openCollection(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        int i = entryAt(mx, my);
        if (i >= 0 && i != selected) { selected = i; click(); }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = entryAt(mx, my);
        if (i >= 0) { selected = i; entries.get(i).open.run(); return true; }
        if (overBook(mx, my)) { openCollection(); return true; }
        return super.mouseClicked(mx, my, button);
    }

    private void click() {
        if (minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.6F, 0.25F));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Minecraft mc = Minecraft.getInstance();
        CharacterType type = ClientCharacterData.getLocal();
        int col = type == CharacterType.NONE ? 0x8090A0 : type.getColor() & 0xFFFFFF;
        float r = ((col >> 16) & 255) / 255F, gr = ((col >> 8) & 255) / 255F, b = (col & 255) / 255F;
        float lr = mix(r, 1F, 0.45F), lg = mix(gr, 1F, 0.45F), lb = mix(b, 1F, 0.45F);   // light tint
        float t = (Util.getMillis() - opened) / 1000F, in = ease(Mth.clamp(t / 0.35F, 0F, 1F));
        int w = width, h = height;
        float panelTop = w * 0.42F, panelBot = w * 0.33F, slide = (1F - in) * -w * 0.3F;

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = g.pose().last().pose();

        // darken the world on the right a little so the text reads
        BufferBuilder bb = begin();
        quad(bb, m, 0, 0, w, 0, w, h, 0, h, 0F, 0F, 0F, 0.7F, 0F, 0F, 0F, 0.25F);
        // the character panel (slanted right edge) in their colour, with a white edge
        float cx = w * 0.19F + slide, cy = h * 0.46F;
        quadP(bb, m, slide, 0, panelTop + slide, 0, panelBot + slide, h, slide, h, mix(r, 1F, 0.12F), mix(gr, 1F, 0.12F), mix(b, 1F, 0.12F), 1F, r * 0.55F, gr * 0.55F, b * 0.55F, 1F);
        quadP(bb, m, panelTop + slide, 0, panelTop + slide + 4, 0, panelBot + slide + 4, h, panelBot + slide, h, 1F, 1F, 1F, 0.95F, 1F, 1F, 1F, 0.95F);
        quadP(bb, m, panelTop + slide + 7, 0, panelTop + slide + 9, 0, panelBot + slide + 9, h, panelBot + slide + 7, h, lr, lg, lb, 0.9F, lr, lg, lb, 0.9F);
        BufferUploader.drawWithShader(bb.end());

        // starburst and stars inside the panel
        g.enableScissor(0, 0, (int) (panelBot + slide), h);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        bb = begin();
        float len = w;
        for (int i = 0; i < 18; i++) {
            float a0 = t * 0.05F + i * Mth.TWO_PI / 18F, a1 = a0 + Mth.TWO_PI / 36F;
            tri(bb, m, cx, cy, lr, lg, lb, 0.22F, cx + Mth.cos(a0) * len, cy + Mth.sin(a0) * len, lr, lg, lb, 0.05F, cx + Mth.cos(a1) * len, cy + Mth.sin(a1) * len, lr, lg, lb, 0.05F);
        }
        disc(bb, m, cx, cy, h * 0.5F, h * 0.5F, 1F, 1F, 1F, 0.35F, 0F);
        Random rnd = new Random(type.ordinal() * 7919L + 3);
        for (int i = 0; i < 14; i++) {
            float sx = slide + rnd.nextFloat() * panelBot, sy = rnd.nextFloat() * h, s = h * (0.015F + rnd.nextFloat() * 0.035F);
            star(bb, m, sx, sy, s, t * (0.2F + rnd.nextFloat() * 0.4F) * (i % 2 == 0 ? 1 : -1), 1F, 1F, 1F, 0.18F + rnd.nextFloat() * 0.2F);
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.defaultBlendFunc();
        g.disableScissor();
        RenderSystem.enableCull();

        // the character art: hero pose, cel-shaded, big and overflowing the panel
        if (mc.player != null) {
            Cel.force = true;
            try {
                PortraitScreen.drawCharacter(g, mc.player, type, (int) (w * 0.2F + slide), (int) (h * 1.2F), (int) (h * 0.56F), 24F);
            } finally { Cel.force = false; }
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        // "MAIN MENU" banner
        float bx = w * 0.015F, by = h * 0.025F, bs = Math.max(1.2F, h / 240F * 1.5F);
        String title = "MAIN MENU";
        int tw = (int) (font.width(title) * bs);
        g.fill((int) bx, (int) by, (int) (bx + tw + 34 * bs / 1.5F), (int) (by + 13 * bs), 0xE0101018);
        g.fill((int) bx, (int) (by + 13 * bs), (int) (bx + tw + 34 * bs / 1.5F), (int) (by + 13 * bs + 2), 0xFF000000 | col);
        star(g, bx + 9 * bs / 1.5F, by + 6.5F * bs, 5.5F * bs / 1.5F, 1F, 1F, 1F, 1F);
        outlined(g, title, bx + 18 * bs / 1.5F, by + 2.5F * bs, bs, light(col), 0x000000);

        // the menu
        float s = step(), sc = bigScale(), lx = listX();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            float d = ease(Mth.clamp((t - 0.05F * i) / 0.3F, 0F, 1F));
            float x = lx + (1F - d) * w * 0.25F, y = listY() + i * s;
            boolean sel = i == selected;
            String first = e.name.substring(0, 1), rest = e.name.substring(1);
            float restScale = sc * 0.74F;
            float textW = font.width(first) * sc + font.width(rest) * restScale;
            if (sel) {
                float pulse = 0.85F + 0.15F * Mth.sin(t * 6F);
                RenderSystem.enableBlend();
                RenderSystem.disableCull();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                Matrix4f mm = g.pose().last().pose();
                BufferBuilder sb = begin();
                float sy0 = y + s * 0.12F, sy1 = y + s * 0.9F;
                quadP(sb, mm, x - s * 0.35F, sy0, x + textW + s * 2.2F, sy0, x + textW + s * 1.9F, sy1, x - s * 0.65F, sy1, lr, lg, lb, 0.85F * pulse, r, gr, b, 0F);
                quadP(sb, mm, x - s * 0.65F, sy1, x + textW + s * 1.9F, sy1, x + textW + s * 1.88F, sy1 + 1.5F, x - s * 0.66F, sy1 + 1.5F, 1F, 1F, 1F, 0.9F, 1F, 1F, 1F, 0F);
                // arrow on the left
                tri(sb, mm, x - s * 0.75F, y + s * 0.3F, 1F, 1F, 1F, 1F, x - s * 0.75F, y + s * 0.74F, 1F, 1F, 1F, 1F, x - s * 0.42F, y + s * 0.52F, 1F, 1F, 1F, 1F);
                BufferUploader.drawWithShader(sb.end());
                RenderSystem.enableCull();
            }
            int main = sel ? 0xFFFFFF : 0xF2F2F2, firstCol = sel ? 0xFFFFFF : light(col);
            int ink = dark(col);
            outlined(g, first, x, y, sc, firstCol, ink);
            outlined(g, rest, x + font.width(first) * sc + sc, y + (9 * sc - 9 * restScale) * 0.85F, restScale, main, ink);
        }

        // top right: Money and stats
        float px = w * 0.77F, py = h * 0.03F, pw = w * 0.21F, rs = Math.max(1F, h / 240F);
        g.fill((int) px, (int) py, (int) (px + pw), (int) (py + 16 * rs), 0xD0101018);
        g.fill((int) px, (int) py, (int) (px + 2 * rs), (int) (py + 16 * rs), 0xFF000000 | col);
        g.pose().pushPose();
        g.pose().translate(px + 4 * rs, py + 0.5F * rs, 0);
        g.pose().scale(rs, rs, 1F);
        g.renderItem(new ItemStack(com.xenowarriors.item.ModItems.MONEY_NOTE.get()), 0, 0);
        g.pose().popPose();
        String money = com.xenowarriors.economy.Money.fmt(ClientMoney.get());
        outlined(g, money, px + pw - font.width(money) * rs * 1.2F - 5 * rs, py + 3.5F * rs, rs * 1.2F, 0xFFFFFF, 0x000000);
        float ry = py + 19 * rs;
        String[][] stats = self == null ? new String[][]{{"Level", "..."}} : new String[][]{
                {"Level", String.valueOf(self.level())}, {"Attack", String.valueOf(self.attack())}, {"Defense", String.valueOf(self.defense())},
                {"Speed", String.valueOf(self.speed())}, {"Karma", String.valueOf(Math.round(self.karma()))}};
        int[] dots = {col, 0xE05050, 0x50A0E0, 0x60D070, 0xC080F0};
        g.fill((int) (px + pw * 0.35F), (int) ry, (int) (px + pw), (int) (ry + stats.length * 11 * rs + 3 * rs), 0xB0101018);
        for (int i = 0; i < stats.length; i++) {
            float yy = ry + 2 * rs + i * 11 * rs;
            g.pose().pushPose();
            g.pose().translate(px + pw * 0.35F + 4 * rs, yy, 0);
            g.pose().scale(rs, rs, 1F);
            g.fill(0, 2, 5, 7, 0xFF000000 | dots[i]);
            g.drawString(font, stats[i][0], 8, 1, 0xC8C8D8, false);
            g.drawString(font, stats[i][1], (int) ((pw * 0.65F - 8 * rs) / rs) - font.width(stats[i][1]), 1, 0xFFFFFF, false);
            g.pose().popPose();
        }

        // bottom right: the Collection Book
        float[] bk = bookBox();
        boolean unlocked = PacketScreens.collectionUnlocked, hov = overBook(mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(bk[0] + bk[2] / 2, bk[1] + bk[3] / 2, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-7F + (hov ? 2F * Mth.sin(t * 8F) : 0F)));
        int cw = (int) (bk[2] / 2), ch = (int) (bk[3] / 2);
        g.fill(-cw - 2, -ch - 2, cw + 2, ch + 2, unlocked ? 0xFFFFFFFF : 0xFF808080);
        g.fillGradient(-cw, -ch, cw, ch, unlocked ? 0xFF000000 | light(col) : 0xFF505058, unlocked ? 0xFF000000 | col : 0xFF303038);
        float isc = Math.min(bk[2], bk[3]) / 16F * 0.7F;
        g.pose().pushPose();
        g.pose().scale(isc, isc, 1F);
        g.renderItem(new ItemStack(com.xenowarriors.item.ModItems.COLLECTION_BOOK.get()), -8, -8);
        g.pose().popPose();
        g.pose().popPose();
        String bl = unlocked ? "[C] Open Collection" : "Read a Collection Book";
        float bls = Math.max(1F, h / 300F);
        float blx = bk[0] + bk[2] / 2 - font.width(bl) * bls / 2, bly = bk[1] - 13 * bls;
        g.fill((int) (blx - 3), (int) (bly - 2), (int) (blx + font.width(bl) * bls + 3), (int) (bly + 9 * bls + 1), 0xD0101018);
        g.pose().pushPose();
        g.pose().translate(blx, bly, 0);
        g.pose().scale(bls, bls, 1F);
        g.drawString(font, bl, 0, 0, unlocked ? 0xFFFFFF : 0x9090A0, false);
        g.pose().popPose();

        // bottom: what the selected entry does, and the controls
        float hs = Math.max(1F, h / 300F);
        String help = entries.get(selected).help;
        float hx = w * 0.40F, hy = h - 26 * hs;
        g.fill((int) (hx - 4), (int) (hy - 3), (int) (w * 0.98F), (int) (hy + 9 * hs + 3), 0xD0101018);
        g.fill((int) (hx - 4), (int) (hy - 3), (int) (hx - 2), (int) (hy + 9 * hs + 3), 0xFF000000 | col);
        g.pose().pushPose();
        g.pose().translate(hx + 2, hy, 0);
        g.pose().scale(hs, hs, 1F);
        g.drawString(font, help, 0, 0, 0xF0F0F0, false);
        g.pose().popPose();
        String keys = "↑↓ Select    Esc Back    Enter Confirm";
        g.pose().pushPose();
        g.pose().translate(w * 0.98F - font.width(keys) * hs, h - 12 * hs, 0);
        g.pose().scale(hs, hs, 1F);
        g.drawString(font, keys, 0, 0, 0xB8B8C8, true);
        g.pose().popPose();

        g.pose().popPose();
    }

    // ------------------------------------------------------------------ helpers

    private static float mix(float a, float b, float f) { return a + (b - a) * f; }
    private static float ease(float x) { return 1F - (1F - x) * (1F - x) * (1F - x); }

    private static int light(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return ((r + (255 - r) * 45 / 100) << 16) | ((g + (255 - g) * 45 / 100) << 8) | (b + (255 - b) * 45 / 100);
    }

    private static int dark(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return ((r * 22 / 100) << 16) | ((g * 22 / 100) << 8) | (b * 22 / 100);
    }

    /** Big text with a thick ink outline (like the menu text in anime games). */
    private void outlined(GuiGraphics g, String s, float x, float y, float scale, int color, int ink) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1F);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (dx != 0 || dy != 0) g.drawString(font, s, dx, dy, ink, false);
        g.drawString(font, s, 0, 1, ink, false);
        g.drawString(font, s, 1, 2, ink, false);
        g.drawString(font, s, 0, 0, color, false);
        g.pose().popPose();
    }

    /** A quad with colour 1 on the left edge (corners 1 and 4) and colour 2 on the right (2 and 3). */
    private static void quadP(BufferBuilder bb, Matrix4f m, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
                              float r1, float g1, float b1, float a1, float r2, float g2, float b2, float a2) {
        quad(bb, m, x1, y1, x2, y2, x3, y3, x4, y4, r1, g1, b1, a1, r2, g2, b2, a2);
    }

    /** A five-pointed star. */
    private static void star(BufferBuilder bb, Matrix4f m, float cx, float cy, float s, float rot, float r, float g, float b, float a) {
        for (int i = 0; i < 5; i++) {
            float a0 = rot + i * Mth.TWO_PI / 5F - Mth.HALF_PI, a1 = a0 + Mth.TWO_PI / 10F, am = a0 - Mth.TWO_PI / 10F;
            tri(bb, m, cx, cy, r, g, b, a, cx + Mth.cos(a0) * s, cy + Mth.sin(a0) * s, r, g, b, a, cx + Mth.cos(a1) * s * 0.42F, cy + Mth.sin(a1) * s * 0.42F, r, g, b, a);
            tri(bb, m, cx, cy, r, g, b, a, cx + Mth.cos(am) * s * 0.42F, cy + Mth.sin(am) * s * 0.42F, r, g, b, a, cx + Mth.cos(a0) * s, cy + Mth.sin(a0) * s, r, g, b, a);
        }
    }

    private static void star(GuiGraphics g, float cx, float cy, float s, float r, float gr, float b, float a) {
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = begin();
        star(bb, g.pose().last().pose(), cx, cy, s, 0F, r, gr, b, a);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
    }
}
