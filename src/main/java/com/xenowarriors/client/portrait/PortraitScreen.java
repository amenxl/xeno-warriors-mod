package com.xenowarriors.client.portrait;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Random;

/**
 * A character portrait card (for promo pictures): the player drawn big in their hero pose (PortraitPoses), on a
 * background in their colour with a glow behind them, light rays, a floor shadow, a coloured key light and a
 * vignette, plus their name and outfit. Used by the UI test's "portraits" run; re-run it when a model changes.
 */
public class PortraitScreen extends Screen {
    private final CharacterType type;
    private final String outfit;
    private final float yaw;

    public PortraitScreen(CharacterType type, String outfit, float yaw) {
        super(Component.literal("Portrait"));
        this.type = type;
        this.outfit = outfit;
        this.yaw = yaw;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Minecraft mc = Minecraft.getInstance();
        int w = width, h = height, c = type.getColor() & 0xFFFFFF;
        float r = ((c >> 16) & 255) / 255F, gr = ((c >> 8) & 255) / 255F, b = (c & 255) / 255F;
        float cx = w * 0.62F, feet = h * 0.88F, scale = h * 0.36F;
        float lx = w * 0.18F, ly = -h * 0.15F;                     // where the key light comes from (top left)
        Random rnd = new Random(type.getId().hashCode() * 31L + outfit.hashCode());

        // background: the character's colour, deep at the bottom
        g.fillGradient(0, 0, w, h, argb(1F, r * 0.42F, gr * 0.42F, b * 0.42F), argb(1F, r * 0.08F, gr * 0.08F, b * 0.1F));

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = g.pose().last().pose();

        // light rays from the key light, and a soft glow behind the character (additive)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder bb = begin();
        float len = (float) Math.hypot(w, h) * 1.3F;
        float base = (float) Math.atan2(feet - ly, cx - lx);
        for (int i = 0; i < 11; i++) {
            float a = base + (i - 5) * 0.09F + (rnd.nextFloat() - 0.5F) * 0.05F, half = 0.012F + rnd.nextFloat() * 0.03F;
            float al = 0.10F + rnd.nextFloat() * 0.10F;
            tri(bb, m, lx, ly, 1F, 0.97F, 0.88F, al,
                    lx + Mth.cos(a - half) * len, ly + Mth.sin(a - half) * len, r, gr, b, 0F,
                    lx + Mth.cos(a + half) * len, ly + Mth.sin(a + half) * len, r, gr, b, 0F);
        }
        disc(bb, m, cx, feet - scale * 1.0F, scale * 1.45F, scale * 1.45F, r * 0.9F + 0.1F, gr * 0.9F + 0.1F, b * 0.9F + 0.1F, 0.55F, 0F);
        disc(bb, m, cx, feet - scale * 1.0F, scale * 0.75F, scale * 0.9F, 1F, 1F, 1F, 0.16F, 0F);
        // floor glow ring
        ring(bb, m, cx, feet, scale * 0.95F, scale * 0.22F, 0.06F, r, gr, b, 0.55F);
        // sparkles
        for (int i = 0; i < 60; i++) {
            float sx = rnd.nextFloat() * w, sy = rnd.nextFloat() * h * 0.9F, s = 0.6F + rnd.nextFloat() * (i < 8 ? 4.5F : 1.8F);
            float al = 0.25F + rnd.nextFloat() * 0.6F;
            tri(bb, m, sx - s, sy, 1F, 1F, 1F, al, sx + s, sy, 1F, 1F, 1F, al, sx, sy - s * 2.4F, r, gr, b, 0F);
            tri(bb, m, sx - s, sy, 1F, 1F, 1F, al, sx + s, sy, 1F, 1F, 1F, al, sx, sy + s * 2.4F, r, gr, b, 0F);
            tri(bb, m, sx, sy - s, 1F, 1F, 1F, al, sx, sy + s, 1F, 1F, 1F, al, sx - s * 2.4F, sy, r, gr, b, 0F);
            tri(bb, m, sx, sy - s, 1F, 1F, 1F, al, sx, sy + s, 1F, 1F, 1F, al, sx + s * 2.4F, sy, r, gr, b, 0F);
        }
        BufferUploader.drawWithShader(bb.end());

        // floor shadow (normal blend)
        RenderSystem.defaultBlendFunc();
        bb = begin();
        disc(bb, m, cx, feet, scale * 0.55F, scale * 0.11F, 0F, 0F, 0F, 0.75F, 0F);
        // name banner: a slanted stripe in the character's colour
        float by = h * 0.74F, bh = h * 0.15F;
        quad(bb, m, 0, by, w * 0.47F, by - bh * 0.08F, w * 0.43F, by + bh, 0, by + bh, r * 0.85F, gr * 0.85F, b * 0.85F, 0.9F, r * 0.5F, gr * 0.5F, b * 0.5F, 0.9F);
        quad(bb, m, 0, by + bh, w * 0.43F, by + bh, w * 0.428F, by + bh + 3, 0, by + bh + 3, 1F, 1F, 1F, 0.85F, 1F, 1F, 1F, 0.85F);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();

        // the character
        drawCharacter(g, mc.player, (int) cx, (int) feet, (int) scale);

        // on top: warm key light from the left, coloured rim from the right, then the vignette
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        bb = begin();
        quad(bb, m, 0, 0, w * 0.55F, 0, w * 0.55F, h, 0, h, 1F, 0.92F, 0.75F, 0.13F, 1F, 0.92F, 0.75F, 0F);
        quad(bb, m, w * 0.6F, 0, w, 0, w, h, w * 0.6F, h, r, gr, b, 0F, r, gr, b, 0.16F);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.defaultBlendFunc();
        bb = begin();
        float diag = (float) Math.hypot(w, h);
        ring(bb, m, w / 2F, h / 2F, diag * 0.36F, diag * 0.36F, diag * 0.22F, 0F, 0F, 0F, -0.8F);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();

        // name, outfit, show title
        g.pose().pushPose();
        g.pose().translate(w * 0.035F, by + bh * 0.16F, 0);
        g.pose().scale(3.2F, 3.2F, 1F);
        g.drawString(font, type.getDisplayName().copy().withStyle(s -> s.withBold(true)), 0, 0, 0xFFFFFF, true);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(w * 0.037F, by + bh * 0.68F, 0);
        g.pose().scale(1.5F, 1.5F, 1F);
        g.drawString(font, "◆ " + outfit, 0, 0, 0xFFF2D8, true);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(w * 0.035F, h * 0.05F, 0);
        g.pose().scale(1.3F, 1.3F, 1F);
        g.drawString(font, "XENO WARRIORS RECARNET", 0, 0, 0xE8E8F0, true);
        g.pose().popPose();
        g.pose().popPose();
    }

    private void drawCharacter(GuiGraphics g, LivingEntity e, int x, int y, int scale) { drawCharacter(g, e, type, x, y, scale, yaw); }

    /** Draws the entity in the character's hero pose, turned by {@code yaw} degrees (also used by the main menu). */
    public static void drawCharacter(GuiGraphics g, LivingEntity e, CharacterType type, int x, int y, int scale, float yaw) {
        if (e == null) return;
        float yb = e.yBodyRot, ybo = e.yBodyRotO, yr = e.getYRot(), xr = e.getXRot(), yh = e.yHeadRot, yho = e.yHeadRotO;
        e.yBodyRot = 180F; e.yBodyRotO = 180F; e.setYRot(180F); e.setXRot(0F); e.yHeadRot = 180F; e.yHeadRotO = 180F;
        PortraitPoses.active = type.getId();
        // hold their signature weapon for the picture (only on our copy here; put back right after)
        net.minecraft.world.item.Item sig = PortraitPoses.held(type.getId());
        net.minecraft.world.item.ItemStack hand = e.getMainHandItem();
        boolean swap = sig != null && !hand.is(sig);
        if (swap) e.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(sig));
        try {
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).mul(new Quaternionf().rotateX(-0.06F)).mul(new Quaternionf().rotateY(yaw * Mth.DEG_TO_RAD));
            InventoryScreen.renderEntityInInventory(g, x, y, scale, pose, null, e);
        } finally {
            if (swap) e.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, hand);
            PortraitPoses.active = null;
            e.yBodyRot = yb; e.yBodyRotO = ybo; e.setYRot(yr); e.setXRot(xr); e.yHeadRot = yh; e.yHeadRotO = yho;
        }
    }

    // ------------------------------------------------------------------ drawing helpers

    public static int argb(float a, float r, float g, float b) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | ((int) (Mth.clamp(r, 0, 1) * 255) << 16) | ((int) (Mth.clamp(g, 0, 1) * 255) << 8) | (int) (Mth.clamp(b, 0, 1) * 255);
    }

    public static BufferBuilder begin() {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        return bb;
    }

    public static void v(BufferBuilder bb, Matrix4f m, float x, float y, float r, float g, float b, float a) {
        bb.vertex(m, x, y, 0F).color(r, g, b, a).endVertex();
    }

    public static void tri(BufferBuilder bb, Matrix4f m, float x1, float y1, float r1, float g1, float b1, float a1,
                            float x2, float y2, float r2, float g2, float b2, float a2, float x3, float y3, float r3, float g3, float b3, float a3) {
        v(bb, m, x1, y1, r1, g1, b1, a1); v(bb, m, x2, y2, r2, g2, b2, a2); v(bb, m, x3, y3, r3, g3, b3, a3);
    }

    /** Quad corners clockwise from top-left; colour 1 on the left/top corners (1,4), colour 2 on (2,3). */
    public static void quad(BufferBuilder bb, Matrix4f m, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
                             float r1, float g1, float b1, float a1, float r2, float g2, float b2, float a2) {
        tri(bb, m, x1, y1, r1, g1, b1, a1, x2, y2, r2, g2, b2, a2, x3, y3, r2, g2, b2, a2);
        tri(bb, m, x1, y1, r1, g1, b1, a1, x3, y3, r2, g2, b2, a2, x4, y4, r1, g1, b1, a1);
    }

    /** A soft ellipse: alpha a0 in the middle fading to a1 at the edge. */
    public static void disc(BufferBuilder bb, Matrix4f m, float cx, float cy, float rx, float ry, float r, float g, float b, float a0, float a1) {
        int n = 64;
        for (int i = 0; i < n; i++) {
            float t0 = i * Mth.TWO_PI / n, t1 = (i + 1) * Mth.TWO_PI / n;
            tri(bb, m, cx, cy, r, g, b, a0, cx + Mth.cos(t0) * rx, cy + Mth.sin(t0) * ry, r, g, b, a1, cx + Mth.cos(t1) * rx, cy + Mth.sin(t1) * ry, r, g, b, a1);
        }
    }

    /**
     * An elliptical ring at radius (rx, ry) with soft width {@code thick} (as a fraction of rx when < 1). A negative
     * alpha means a vignette: transparent inside, |a| from the ring outward to far beyond the screen.
     */
    public static void ring(BufferBuilder bb, Matrix4f m, float cx, float cy, float rx, float ry, float thick, float r, float g, float b, float a) {
        int n = 72;
        boolean vig = a < 0;
        float tw = thick < 1F ? thick * rx : thick, ty = tw * ry / rx;
        for (int i = 0; i < n; i++) {
            float t0 = i * Mth.TWO_PI / n, t1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
            if (vig) {
                float al = -a, rx2 = rx + tw, ry2 = ry + ty, rx3 = rx * 4, ry3 = ry * 4;
                bb.vertex(m, cx + c0 * rx, cy + s0 * ry, 0).color(r, g, b, 0F).endVertex();
                bb.vertex(m, cx + c0 * rx2, cy + s0 * ry2, 0).color(r, g, b, al).endVertex();
                bb.vertex(m, cx + c1 * rx2, cy + s1 * ry2, 0).color(r, g, b, al).endVertex();
                bb.vertex(m, cx + c0 * rx, cy + s0 * ry, 0).color(r, g, b, 0F).endVertex();
                bb.vertex(m, cx + c1 * rx2, cy + s1 * ry2, 0).color(r, g, b, al).endVertex();
                bb.vertex(m, cx + c1 * rx, cy + s1 * ry, 0).color(r, g, b, 0F).endVertex();
                quad(bb, m, cx + c0 * rx2, cy + s0 * ry2, cx + c1 * rx2, cy + s1 * ry2, cx + c1 * rx3, cy + s1 * ry3, cx + c0 * rx3, cy + s0 * ry3, r, g, b, al, r, g, b, al);
            } else {
                float ri = rx - tw, rij = ry - ty, ro = rx + tw, roj = ry + ty;
                // inner soft edge -> ring -> outer soft edge
                bb.vertex(m, cx + c0 * ri, cy + s0 * rij, 0).color(r, g, b, 0F).endVertex();
                bb.vertex(m, cx + c0 * rx, cy + s0 * ry, 0).color(r, g, b, a).endVertex();
                bb.vertex(m, cx + c1 * rx, cy + s1 * ry, 0).color(r, g, b, a).endVertex();
                bb.vertex(m, cx + c0 * ri, cy + s0 * rij, 0).color(r, g, b, 0F).endVertex();
                bb.vertex(m, cx + c1 * rx, cy + s1 * ry, 0).color(r, g, b, a).endVertex();
                bb.vertex(m, cx + c1 * ri, cy + s1 * rij, 0).color(r, g, b, 0F).endVertex();
                bb.vertex(m, cx + c0 * rx, cy + s0 * ry, 0).color(r, g, b, a).endVertex();
                bb.vertex(m, cx + c0 * ro, cy + s0 * roj, 0).color(r, g, b, 0F).endVertex();
                bb.vertex(m, cx + c1 * ro, cy + s1 * roj, 0).color(r, g, b, 0F).endVertex();
                bb.vertex(m, cx + c0 * rx, cy + s0 * ry, 0).color(r, g, b, a).endVertex();
                bb.vertex(m, cx + c1 * ro, cy + s1 * roj, 0).color(r, g, b, 0F).endVertex();
                bb.vertex(m, cx + c1 * rx, cy + s1 * ry, 0).color(r, g, b, a).endVertex();
            }
        }
    }
}
