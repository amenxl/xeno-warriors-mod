package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

import java.util.UUID;

/**
 * HUD portraits for characters without drawn portrait art: their actual head, rendered live in the portrait box (so it
 * always matches their outfit and any model update). Falls back to a badge with their initial when they're not loaded.
 */
public final class HudHead {
    private HudHead() {}

    public static LivingEntity find(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (mc.player != null && mc.player.getUUID().equals(id)) return mc.player;
        var pl = mc.level.getPlayerByUUID(id);
        if (pl != null) return pl;
        for (Entity e : mc.level.entitiesForRendering()) if (e instanceof LivingEntity le && e.getUUID().equals(id)) return le;
        return null;
    }

    public static void draw(GuiGraphics g, LivingEntity e, CharacterType type, int x, int y, int size, boolean redFlash) {
        int col = type.getColor() & 0xFFFFFF;
        g.fillGradient(x, y, x + size, y + size, 0xFF000000 | mix(col, 0.55F), 0xFF000000 | mix(col, 0.2F));
        if (e == null) {
            String init = type.getDisplayName().getString().substring(0, 1);
            var font = Minecraft.getInstance().font;
            g.pose().pushPose();
            g.pose().translate(x + size / 2F, y + size / 2F, 0);
            float s = size / 14F;
            g.pose().scale(s, s, 1F);
            g.drawString(font, init, -font.width(init) / 2, -4, 0xFFFFFF, true);
            g.pose().popPose();
            return;
        }
        int scale = Math.round(size * 1.55F);
        float yb = e.yBodyRot, ybo = e.yBodyRotO, yr = e.getYRot(), xr = e.getXRot(), yh = e.yHeadRot, yho = e.yHeadRotO;
        e.yBodyRot = 180F; e.yBodyRotO = 180F; e.setYRot(180F); e.setXRot(0F); e.yHeadRot = 180F; e.yHeadRotO = 180F;
        g.enableScissor(x, y, x + size, y + size);
        if (redFlash) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1F, 0.55F, 0.55F, 1F);
        try {
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).mul(new Quaternionf().rotateY(-0.35F));
            // head centre (about 1.55 blocks up) in the middle of the box
            InventoryScreen.renderEntityInInventory(g, x + size / 2, Math.round(y + size * 0.55F + 1.55F * scale), scale, pose, null, e);
        } finally {
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            g.disableScissor();
            e.yBodyRot = yb; e.yBodyRotO = ybo; e.setYRot(yr); e.setXRot(xr); e.yHeadRot = yh; e.yHeadRotO = yho;
        }
    }

    private static int mix(int c, float f) {
        int r = (int) (((c >> 16) & 255) * f), g = (int) (((c >> 8) & 255) * f), b = (int) ((c & 255) * f);
        return (r << 16) | (g << 8) | b;
    }
}
