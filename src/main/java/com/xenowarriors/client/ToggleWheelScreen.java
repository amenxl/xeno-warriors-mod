package com.xenowarriors.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.ToggleActionPacket;
import com.xenowarriors.network.TogglesPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The transform key's hold wheel: your transformations, buffs and passive skills round a ring (Fox King, Netsu and
 * True Empathy for Mikiko; Demon's Remnants for Rhalix...). Point at one and let go of the key (or click): a
 * transformation goes off, a buff lights (or goes out), a passive switches on or off. Let go in the middle to do
 * nothing. What's on or running glows; what's switched off in Character Customization > Passives is greyed out.
 */
public class ToggleWheelScreen extends Screen {
    private static final int INNER = 38, OUTER = 96;
    private final boolean test;
    private TogglesPacket.Row hovered;
    private boolean done;
    /** UI test: point at this entry. */
    public static String testHover;

    public ToggleWheelScreen(boolean test) {
        super(Component.literal("Transformations & passives"));
        this.test = test;
        ModNetwork.sendToServer(new ToggleActionPacket(ToggleActionPacket.REQUEST, ""));
    }

    @Override public boolean isPauseScreen() { return false; }

    /** What goes on the wheel (the automatic passives are switched only in Character Customization). */
    private List<TogglesPacket.Row> rows() { return TogglesPacket.CLIENT.rows().stream().filter(r -> r.kind() != 3).toList(); }

    private boolean keyHeld() {
        var key = ModKeyMappings.FOX_KING.getKey();
        long win = minecraft.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(win, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getValue() >= 0 && InputConstants.isKeyDown(win, key.getValue());
    }

    @Override
    public void tick() {
        if (!test && !keyHeld()) release();
        if (minecraft.player != null && minecraft.player.tickCount % 10 == 0) ModNetwork.sendToServer(new ToggleActionPacket(ToggleActionPacket.REQUEST, ""));   // (running / cooldown state)
    }

    /** Lets go: the entry pointed at (if it can be had). */
    public void release() {
        if (done) return;
        done = true;
        TogglesPacket.Row r = hovered;
        if (r != null && r.locked().isEmpty()) ModNetwork.sendToServer(new ToggleActionPacket(ToggleActionPacket.USE, r.id()));
        minecraft.setScreen(null);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (!test && ModKeyMappings.FOX_KING.matches(key, scan)) { release(); return true; }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (hovered != null) { release(); return true; }
        return super.mouseClicked(x, y, button);
    }

    private double angleOf(int i, int n) { return -Math.PI / 2 + i * Math.PI * 2 / n; }

    private static String kind(int k) { return k == 0 ? "Transformation" : k == 1 ? "Buff" : k == 4 ? "Tracking" : "Passive"; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        List<TogglesPacket.Row> rows = rows();
        int n = rows.size(), cx = width / 2, cy = height / 2;
        float scale = Math.min(1F, (height - 30) / (OUTER * 2F + 50));
        g.fill(0, 0, width, height, 0x50000000);
        hovered = null;
        if (n > 0) {
            double dx = mouseX - cx, dy = mouseY - cy;
            if (dx * dx + dy * dy > (INNER * scale * 0.6) * (INNER * scale * 0.6)) {
                double a = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / n;
                hovered = rows.get(Math.floorMod((int) Math.floor(a / (Math.PI * 2 / n)), n));
            }
            if (test && testHover != null) for (var r : rows) if (r.id().equals(testHover)) hovered = r;
        }
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1F);
        if (n == 0) {
            arc(g, INNER, OUTER, 0, (float) Math.PI * 2, 0xB0101014);
            g.drawCenteredString(font, "No transformations", 0, -10, 0xC0C0C0);
            g.drawCenteredString(font, "or passives yet", 0, 2, 0xC0C0C0);
        }
        float slice = n == 0 ? 0 : (float) (Math.PI * 2 / n);
        long ms = net.minecraft.Util.getMillis();
        for (int i = 0; i < n; i++) {
            TogglesPacket.Row r = rows.get(i);
            boolean usable = r.locked().isEmpty() && (r.enabled() || r.kind() == 2);
            float a0 = (float) angleOf(i, n) - slice / 2 + 0.02F, a1 = (float) angleOf(i, n) + slice / 2 - 0.02F;
            int col = r == hovered ? (usable ? 0xD0000000 | r.color() : 0xC0703030) : usable ? 0xB0201820 : 0xB0101010;
            arc(g, INNER, OUTER, a0, a1, col);
            if (r.active()) {                                                   // on / running: a glowing rim that breathes
                int al = 0x80 + (int) (0x60 * (0.5F + 0.5F * Mth.sin(ms / 220F)));
                arc(g, OUTER - 4, OUTER, a0, a1, (al << 24) | r.color());
            }
            if (r == hovered) arc(g, OUTER, OUTER + 4, a0, a1, 0xFF000000 | r.color());
        }
        for (int i = 0; i < n; i++) {
            TogglesPacket.Row r = rows.get(i);
            boolean usable = r.locked().isEmpty() && (r.enabled() || r.kind() == 2);
            double a = angleOf(i, n);
            float rad = (INNER + OUTER) / 2F;
            int ix = Math.round((float) Math.cos(a) * rad), iy = Math.round((float) Math.sin(a) * rad);
            icon(g, r, ix, iy - 8, usable);
            fit(g, r.title(), ix, iy + 6, 70, usable ? 0xFFFFFF : 0x808080);
            String state = !r.locked().isEmpty() ? "locked" : r.kind() == 2 ? (r.enabled() ? "ON" : "OFF") : !r.enabled() ? "disabled" : r.active() ? (r.kind() == 4 ? "tracking" : "active") : "";
            if (!state.isEmpty()) fit(g, state, ix, iy + 16, 60, r.active() ? 0x7CFF7C : !r.locked().isEmpty() || !r.enabled() ? 0xC08080 : 0xC0C0C0);
        }
        g.drawCenteredString(font, n > 0 ? "let go" : "", 0, -10, 0x909090);
        g.drawCenteredString(font, n > 0 ? "here: cancel" : "", 0, 1, 0x909090);
        g.pose().popPose();
        g.drawCenteredString(font, "Transformations, buffs & passives", cx, Math.round(cy - (OUTER + 20) * scale), 0xFFE070);
        String line;
        int lc = 0xFFFFFF;
        if (hovered == null) { line = n > 0 ? "Point at one and let go of the key" : "Hold the transform key to open this wheel"; lc = 0xA0A0A0; }
        else if (!hovered.locked().isEmpty()) { line = hovered.title() + " - " + hovered.locked(); lc = 0xFF8080; }
        else if (hovered.kind() == 2) line = kind(hovered.kind()) + " - let go to switch it " + (hovered.enabled() ? "off" : "on");
        else if (!hovered.enabled()) { line = hovered.title() + " is disabled (Character Customization > Passives)"; lc = 0xFF8080; }
        else if (hovered.kind() == 1) line = "Buff - let go to " + (hovered.active() ? "put it out" : "light it");
        else if (hovered.kind() == 4) line = "Tracking - let go to pick a scent to follow";
        else line = "Transformation - let go to transform  (mastery " + Math.round(hovered.mastery()) + ")";
        g.drawCenteredString(font, line, cx, Math.round(cy + (OUTER + 10) * scale), lc);
    }

    /** A small emblem per entry, drawn from fills (no textures). */
    private void icon(GuiGraphics g, TogglesPacket.Row r, int x, int y, boolean on) {
        int c = on ? 0xFF000000 | r.color() : 0xFF555560;
        switch (r.kind()) {
            case 0 -> {                                                        // a transformation: a rising flare
                g.fill(x - 1, y - 7, x + 1, y + 3, c);
                g.fill(x - 4, y - 3, x - 2, y + 3, c); g.fill(x + 2, y - 3, x + 4, y + 3, c);
                g.fill(x - 6, y + 1, x - 5, y + 3, c); g.fill(x + 5, y + 1, x + 6, y + 3, c);
            }
            case 1 -> {                                                        // a buff: a flame
                g.fill(x - 3, y - 1, x + 3, y + 3, c); g.fill(x - 2, y - 4, x + 2, y - 1, c); g.fill(x - 1, y - 7, x + 1, y - 4, c);
                g.fill(x - 1, y, x + 1, y + 2, 0xFFFFF0A0);
            }
            case 4 -> {                                                        // tracking: a nose / paw print
                g.fill(x - 2, y - 1, x + 2, y + 3, c);
                g.fill(x - 5, y - 4, x - 3, y - 2, c); g.fill(x - 1, y - 6, x + 1, y - 4, c); g.fill(x + 3, y - 4, x + 5, y - 2, c);
            }
            default -> {                                                       // a passive: an eye
                g.fill(x - 6, y - 1, x + 6, y + 1, c); g.fill(x - 4, y - 3, x + 4, y - 1, c); g.fill(x - 4, y + 1, x + 4, y + 3, c);
                g.fill(x - 1, y - 2, x + 1, y + 2, 0xFF101014);
            }
        }
    }

    private void fit(GuiGraphics g, String s, int cx, int y, int room, int color) {
        float k = Math.min(1F, room / (float) Math.max(1, font.width(s)));
        g.pose().pushPose(); g.pose().translate(cx, y, 0); g.pose().scale(k, k, 1F);
        g.drawCenteredString(font, s, 0, 0, color); g.pose().popPose();
    }

    private static void arc(GuiGraphics g, float r0, float r1, float a0, float a1, int argb) {
        Matrix4f m = g.pose().last().pose();
        float a = (argb >>> 24) / 255F, r = (argb >> 16 & 255) / 255F, gr = (argb >> 8 & 255) / 255F, b = (argb & 255) / 255F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int steps = Math.max(2, (int) Math.ceil(Math.abs(a1 - a0) / 0.06F));
        for (int i = 0; i <= steps; i++) {
            float t = a0 + (a1 - a0) * i / steps, c = Mth.cos(t), s = Mth.sin(t);
            buf.vertex(m, c * r1, s * r1, 0).color(r, gr, b, a).endVertex();
            buf.vertex(m, c * r0, s * r0, 0).color(r, gr, b, a).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
    }
}
