package com.xenowarriors.client.party;

import com.xenowarriors.network.PartySyncPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.joml.Vector3f;

/**
 * Where your party is: a marker (name and distance) over each member you're facing - through walls and beyond render
 * distance - and an arrow on the edge of the screen pointing to anyone off-screen. Downed members flash red.
 */
public class PartyPointers implements IGuiOverlay {
    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;
        if (com.xenowarriors.client.UltimateCutscene.active() || com.xenowarriors.client.TeamUltCutscene.active()) return;
        var members = PartyClient.members();
        if (members.size() < 2) return;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition();
        Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
        double fov = Math.toRadians(mc.options.fov().get());
        double tanY = Math.tan(fov / 2), aspect = width / (double) height;
        String dim = mc.level.dimension().location().toString();
        Font font = mc.font;
        long time = mc.level.getGameTime();
        for (PartySyncPacket.Member m : members) {
            if (m.id().equals(mc.player.getUUID()) || !dim.equals(m.dim())) continue;
            Player live = mc.level.getPlayerByUUID(m.id());
            Vec3 at = live != null ? live.getPosition(partial).add(0, live.getBbHeight() + 0.6, 0) : new Vec3(m.x(), m.y() + 2.4, m.z());
            Vec3 v = at.subtract(eye);
            double dist = v.length();
            if (live != null && dist < 4) continue;                       // right next to you
            double lx = -(v.x * left.x() + v.y * left.y() + v.z * left.z());
            double ly = v.x * up.x() + v.y * up.y() + v.z * up.z();
            double lz = v.x * look.x() + v.y * look.y() + v.z * look.z();
            boolean down = m.knockedOut();
            int color = down ? ((time / 6) % 2 == 0 ? 0xFF4040 : 0xFFB0B0) : 0x7CFFB0;
            String label = m.name() + "  " + Math.round(dist) + "m" + (down ? "  DOWN" : "");
            double nx = lz > 0.05 ? (lx / lz) / (tanY * aspect) : 0, ny = lz > 0.05 ? (ly / lz) / tanY : 0;
            if (lz > 0.05 && Math.abs(nx) < 0.95 && Math.abs(ny) < 0.92) {
                // on screen: a marker over them
                int sx = (int) Math.round(width / 2.0 * (1 + nx)), sy = (int) Math.round(height / 2.0 * (1 - ny));
                g.drawCenteredString(font, "▼", sx, sy - 4, 0xFF000000 | color);
                int w = font.width(label);
                g.fill(sx - w / 2 - 2, sy - 16, sx + w / 2 + 2, sy - 6, 0x80000000);
                g.drawCenteredString(font, label, sx, sy - 15, 0xFF000000 | color);
            } else {
                // off screen: an arrow on the edge pointing their way
                double dx = lx, dy = ly;
                if (lz <= 0.05 && Math.abs(dx) < 0.001 && Math.abs(dy) < 0.001) dx = 1;
                double ang = Math.atan2(-dy, dx);
                double hw = width / 2.0 - 22, hh = height / 2.0 - 18;
                double cx = Math.cos(ang), cy = Math.sin(ang);
                double s = Math.min(hw / Math.max(1e-4, Math.abs(cx)), hh / Math.max(1e-4, Math.abs(cy)));
                float ex = (float) (width / 2.0 + cx * s), ey = (float) (height / 2.0 + cy * s);
                g.pose().pushPose();
                g.pose().translate(ex, ey, 0);
                g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) ang + Mth.HALF_PI));
                g.pose().scale(1.6F, 1.6F, 1F);
                g.drawCenteredString(font, "▲", 0, -4, 0xFF000000 | color);
                g.pose().popPose();
                int w = font.width(label);
                int tx = (int) Mth.clamp(ex - cx * 14, w / 2F + 4, width - w / 2F - 4);
                int ty = (int) Mth.clamp(ey - cy * 14 - 4, 4, height - 12);
                g.fill(tx - w / 2 - 2, ty - 1, tx + w / 2 + 2, ty + 9, 0x80000000);
                g.drawCenteredString(font, label, tx, ty, 0xFF000000 | color);
            }
        }
    }
}
