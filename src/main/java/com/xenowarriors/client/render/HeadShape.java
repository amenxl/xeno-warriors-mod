package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ClientSettings;
import com.xenowarriors.client.FirstPersonHead;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The "anime jaw" head (a trial, switchable in Settings): Mikiko's and Rhalix's heads taper to a chin - the
 * bottom of the head narrows in two steps. The plain head is hidden and this one is drawn in its place, with the same
 * skin, and the face drawn over the matching shape.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HeadShape {
    /** {x0, y0, z0, x1, y1, z1} in head space (pixels, y down, face toward -z). */
    private static final float[][] JAW = {{-4, -8, -4, 4, -2.2F, 4}, {-3.6F, -2.2F, -4, 3.6F, -1.1F, 3.6F}, {-2.6F, -1.1F, -3.7F, 2.6F, 0, 3.0F}};
    private static boolean hid;

    private HeadShape() {}

    public static boolean active(AbstractClientPlayer p) {
        return ClientSettings.animeJaw() && ClientCharacterData.get(p) != CharacterType.NONE && !FirstPersonHead.ownFirstPerson(p);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPre(RenderPlayerEvent.Pre event) {
        if (event.isCanceled() || !(event.getEntity() instanceof AbstractClientPlayer p) || !active(p)) return;
        var m = event.getRenderer().getModel();
        if (!m.head.visible) return;
        m.head.visible = false;
        hid = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onPost(RenderPlayerEvent.Post event) {
        if (!hid) return;
        event.getRenderer().getModel().head.visible = true;
        hid = false;
    }

    /** The shaped head, in the player's skin (called from the detail layer, which draws after the model). */
    public static void drawHead(ModelPart head, PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, int light, int overlay) {
        pose.pushPose();
        head.translateAndRotate(pose);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(p.getSkinTextureLocation()));
        for (float[] b : JAW) box(pose, vc, b, light, overlay);
        pose.popPose();
    }

    /** The face texture over the front of the shaped head (each step of the jaw gets its own strip). */
    public static void drawFace(ModelPart head, PoseStack pose, VertexConsumer vc, int light, int overlay) {
        pose.pushPose();
        head.translateAndRotate(pose);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (float[] b : JAW) {
            float z = (b[2] - 0.08F) / 16F;
            float u0 = (b[0] + 4) / 16F, u1 = (b[3] + 4) / 16F, v0 = (b[1] + 8) / 16F, v1 = (b[4] + 8) / 16F;   // face art: top-left 32x32 of 64
            quad(vc, m, n, b[0], b[1], z * 16, b[3], b[1], z * 16, b[3], b[4], z * 16, b[0], b[4], z * 16, u0, v0, u1, v0, u1, v1, u0, v1, 0, 0, -1, light, overlay);
        }
        pose.popPose();
    }

    private static void box(PoseStack pose, VertexConsumer vc, float[] b, int light, int overlay) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        // skin UVs of the 8x8x8 head, projected onto this piece of it (64x64 skin)
        float s = 64F;
        // front (-z): u = 8 + (x+4), v = 8 + (y+8)
        quad(vc, m, n, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0,
                (12 + x0) / s, (16 + y0) / s, (12 + x1) / s, (16 + y0) / s, (12 + x1) / s, (16 + y1) / s, (12 + x0) / s, (16 + y1) / s, 0, 0, -1, light, overlay);
        // back (+z): u = 24 + (4 - x)
        quad(vc, m, n, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1,
                (28 - x1) / s, (16 + y0) / s, (28 - x0) / s, (16 + y0) / s, (28 - x0) / s, (16 + y1) / s, (28 - x1) / s, (16 + y1) / s, 0, 0, 1, light, overlay);
        // his right (-x): u = 4 - z (front edge at u 8)
        quad(vc, m, n, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1,
                (4 - z1) / s, (16 + y0) / s, (4 - z0) / s, (16 + y0) / s, (4 - z0) / s, (16 + y1) / s, (4 - z1) / s, (16 + y1) / s, -1, 0, 0, light, overlay);
        // his left (+x): u = 16 + (z + 4)
        quad(vc, m, n, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0,
                (20 + z0) / s, (16 + y0) / s, (20 + z1) / s, (16 + y0) / s, (20 + z1) / s, (16 + y1) / s, (20 + z0) / s, (16 + y1) / s, 1, 0, 0, light, overlay);
        // top (-y): u = 8 + (x+4), v = 4 - z (front edge at v 8)
        quad(vc, m, n, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0,
                (12 + x0) / s, (4 - z1) / s, (12 + x1) / s, (4 - z1) / s, (12 + x1) / s, (4 - z0) / s, (12 + x0) / s, (4 - z0) / s, 0, -1, 0, light, overlay);
        // bottom (+y): u = 16 + (x+4), v = z + 4
        quad(vc, m, n, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1,
                (20 + x0) / s, (4 + z0) / s, (20 + x1) / s, (4 + z0) / s, (20 + x1) / s, (4 + z1) / s, (20 + x0) / s, (4 + z1) / s, 0, 1, 0, light, overlay);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float au, float av, float bu, float bv, float cu, float cv, float du, float dv,
                             float nx, float ny, float nz, int light, int overlay) {
        float k = 1F / 16F;
        vc.vertex(m, ax * k, ay * k, az * k).color(255, 255, 255, 255).uv(au, av).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, bx * k, by * k, bz * k).color(255, 255, 255, 255).uv(bu, bv).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, cx * k, cy * k, cz * k).color(255, 255, 255, 255).uv(cu, cv).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, dx * k, dy * k, dz * k).color(255, 255, 255, 255).uv(du, dv).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
