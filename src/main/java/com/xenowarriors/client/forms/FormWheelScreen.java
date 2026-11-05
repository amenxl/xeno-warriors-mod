package com.xenowarriors.client.forms;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xenowarriors.client.ModKeyMappings;
import com.xenowarriors.forms.Form;
import com.xenowarriors.forms.FormItems;
import com.xenowarriors.forms.WeaponForms;
import com.xenowarriors.network.FormPickPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The summon key's hold wheel: every form of the weapon round a ring (locked ones greyed out with what they need).
 * Point at one and let go of the key (or click) to summon it; power gathers in the middle while the key is held, and
 * a full charge makes it the powered-up summon. Let go in the middle to call it off.
 */
public class FormWheelScreen extends Screen {
    private static final int INNER = 40, OUTER = 100;
    private final Form.Style style;
    private final List<Form> forms;
    private final boolean test;
    private final long opened;
    private Form hovered;
    private boolean done;
    /** UI test: point at this form. */
    public static Form testHover;

    public FormWheelScreen(Form.Style style, boolean test) {
        super(Component.literal("Weapon forms"));
        this.style = style;
        this.forms = Form.wheel(style);
        this.test = test;
        var mc = net.minecraft.client.Minecraft.getInstance();
        this.opened = mc.level == null ? 0 : mc.level.getGameTime();
    }

    @Override public boolean isPauseScreen() { return false; }

    private float charge(float pt) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        float t = mc.level == null ? 0 : mc.level.getGameTime() - opened + pt;
        return Mth.clamp(t / WeaponForms.CHARGE, 0F, 1F);
    }

    private boolean keyHeld() {
        var key = ModKeyMappings.SUMMON_BLADE.getKey();
        long win = minecraft.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(win, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getValue() >= 0 && InputConstants.isKeyDown(win, key.getValue());
    }

    @Override
    public void tick() {
        if (!test && !keyHeld()) release();
    }

    /** Lets go: summons the form pointed at (if it's unlocked), otherwise calls it off. */
    public void release() {
        if (done) return;
        done = true;
        Form f = hovered;
        if (f != null && FormsClient.unlocked(f) && !(f.gun() && FormsClient.gunCooldown() > 0)) ModNetwork.sendToServer(new FormPickPacket(FormPickPacket.PICK, f.ordinal()));
        else ModNetwork.sendToServer(new FormPickPacket(FormPickPacket.CANCEL, -1));
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        if (!done) { done = true; ModNetwork.sendToServer(new FormPickPacket(FormPickPacket.CANCEL, -1)); }
        super.onClose();
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (!test && ModKeyMappings.SUMMON_BLADE.matches(key, scan)) { release(); return true; }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (hovered != null) { release(); return true; }
        return super.mouseClicked(x, y, button);
    }

    private double angleOf(int i) { return -Math.PI / 2 + i * Math.PI * 2 / forms.size(); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        int cx = width / 2, cy = height / 2;
        float scale = Math.min(1F, (height - 20) / (OUTER * 2F + 40));
        // which slice the mouse is in
        double dx = mouseX - cx, dy = mouseY - cy;
        hovered = null;
        if (dx * dx + dy * dy > (INNER * scale * 0.6) * (INNER * scale * 0.6)) {
            double a = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / forms.size();
            int i = Math.floorMod((int) Math.floor(a / (Math.PI * 2 / forms.size())), forms.size());
            hovered = forms.get(i);
        }
        if (testHover != null && test) hovered = testHover;
        boolean heku = style == Form.Style.HEKU;
        int accent = heku ? 0xFF8A2A : 0xB05CFF;
        g.fill(0, 0, width, height, 0x50000000);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1F);
        float slice = (float) (Math.PI * 2 / forms.size());
        int gunCd = FormsClient.gunCooldown();
        for (int i = 0; i < forms.size(); i++) {
            Form f = forms.get(i);
            boolean open = FormsClient.unlocked(f) && !(f.gun() && gunCd > 0);
            float a0 = (float) angleOf(i) - slice / 2 + 0.02F, a1 = (float) angleOf(i) + slice / 2 - 0.02F;
            int col = f == hovered ? (open ? (0xD0000000 | accent) : 0xC0703030) : open ? 0xB0201820 : 0xB0101010;
            arc(g, 0, 0, INNER, OUTER, a0, a1, col);
            if (f == hovered) arc(g, 0, 0, OUTER, OUTER + 4, a0, a1, 0xFF000000 | accent);
        }
        // the charge ring in the middle
        float ch = charge(pt);
        arc(g, 0, 0, INNER - 10, INNER - 4, 0, (float) Math.PI * 2, 0x60000000);
        arc(g, 0, 0, INNER - 10, INNER - 4, (float) -Math.PI / 2, (float) (-Math.PI / 2 + Math.PI * 2 * ch), 0xFF000000 | (ch >= 1F ? 0xFFD040 : accent));
        for (int i = 0; i < forms.size(); i++) {
            Form f = forms.get(i);
            boolean open = FormsClient.unlocked(f) && !(f.gun() && gunCd > 0);
            double a = angleOf(i);
            float r = (INNER + OUTER) / 2F;
            int ix = Math.round((float) Math.cos(a) * r), iy = Math.round((float) Math.sin(a) * r);
            ItemStack st = FormItems.stack(style, f);
            g.pose().pushPose();
            g.pose().translate(ix, iy - 5, 0);
            g.pose().scale(1.7F, 1.7F, 1F);
            g.renderItem(st, -8, -8);
            g.pose().popPose();
            if (!open) g.fill(ix - 14, iy - 19, ix + 14, iy + 9, 0x90000000);
            String label = f == Form.BLADE ? "Hekublade" : f.label;
            g.drawCenteredString(font, label, ix, iy + 13, open ? 0xFFFFFF : 0x808080);
            if (f.gun() && gunCd > 0 && FormsClient.unlocked(f)) g.drawCenteredString(font, (gunCd + 19) / 20 + "s", ix, iy - 9, 0xFF6060);
            else if (!FormsClient.unlocked(f)) g.drawCenteredString(font, "locked", ix, iy - 9, 0xC08080);
        }
        String mid = ch >= 1F ? "POWERED" : "charging";
        g.drawCenteredString(font, mid, 0, -4, ch >= 1F ? 0xFFD040 : 0xC0C0C0);
        g.pose().popPose();
        // what's pointed at
        String name = heku ? "Hekublade" : "Wires";
        g.drawCenteredString(font, name + " Lv " + FormsClient.weaponLevel(), cx, Math.round(cy - (OUTER + 22) * scale), accent);
        if (hovered != null) {
            boolean un = FormsClient.unlocked(hovered);
            String line = un ? (hovered.gun() && gunCd > 0 ? "Your last gun broke - a new one in " + (gunCd + 19) / 20 + "s"
                    : "Let go to summon" + (ch >= 1F ? " (powered up)" : "") + (hovered.gun() ? " - infinite ammo" : ""))
                    : "Locked - needs " + hovered.requirement(style);
            g.drawCenteredString(font, line, cx, Math.round(cy + (OUTER + 12) * scale), un ? 0xFFFFFF : 0xFF8080);
        } else {
            g.drawCenteredString(font, "Point at a form and let go of the key - let go here to cancel", cx, Math.round(cy + (OUTER + 12) * scale), 0xA0A0A0);
        }
    }

    /** A ring segment from angle a0 to a1 between radii r0 and r1. */
    private static void arc(GuiGraphics g, float cx, float cy, float r0, float r1, float a0, float a1, int argb) {
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
            buf.vertex(m, cx + c * r1, cy + s * r1, 0).color(r, gr, b, a).endVertex();
            buf.vertex(m, cx + c * r0, cy + s * r0, 0).color(r, gr, b, a).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
    }
}
