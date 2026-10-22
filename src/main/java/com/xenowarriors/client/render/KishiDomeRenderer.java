package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.KishiDomeEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws Kishi like the reference art: a glowing see-through column of gravity
 * pressing straight down, with bright rings that keep sliding downward.
 * Heavier gravity = brighter, more purple, and faster rings.
 */
public class KishiDomeRenderer extends EntityRenderer<KishiDomeEntity> {
    private static final ResourceLocation WHITE = XenoWarriors.id("textures/entity/kishi_dome.png");
    private static final int SEGMENTS = 40;

    public KishiDomeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(KishiDomeEntity dome, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        float age = dome.tickCount + partialTick;
        int level = dome.getGravityLevel();
        float fadeIn = Mth.clamp(age / 5F, 0F, 1F);
        float fadeOut = Mth.clamp((KishiDomeEntity.DURATION - age) / 10F, 0F, 1F);
        float alpha = fadeIn * fadeOut;
        if (alpha <= 0) return;

        float r = dome.shownRadius(partialTick);
        float h = dome.shownHeight(partialTick);
        // the dome slams down from above when cast
        float bottom = (1F - fadeIn) * h;
        // color goes from white-lavender towards purple as gravity gets heavier
        float red = 0.92F - 0.08F * level, green = 0.88F - 0.14F * level, blue = 1.0F;
        float power = 0.7F + 0.15F * level;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();

        // walls: stronger at the bottom, fading towards the top
        wall(vc, m, n, r, bottom, h, red, green, blue, 0.30F * power * alpha, 0.06F * alpha);

        // bright rings sliding down the column
        int rings = 2 + level;
        float speed = 0.05F + 0.03F * level;
        for (int i = 0; i < rings; i++) {
            float t = ((age * speed) + i / (float) rings) % 1F;
            float y = h - t * (h - bottom);
            float ringAlpha = 0.55F * power * alpha * (0.4F + 0.6F * t);
            wall(vc, m, n, r * 1.01F, y, Math.min(h, y + 0.12F), red, green, blue, ringAlpha, ringAlpha);
        }

        // glowing ring on the ground and a faint lid on top
        flatRing(vc, m, n, r - 0.3F, r + 0.15F, bottom + 0.03F, red, green, blue, 0.65F * power * alpha);
        flatRing(vc, m, n, 0F, r, h, red, green, blue, 0.12F * alpha);

        super.render(dome, yaw, partialTick, poseStack, buffers, light);
    }

    private static void wall(VertexConsumer vc, Matrix4f m, Matrix3f n, float r, float y0, float y1,
                             float red, float green, float blue, float a0, float a1) {
        for (int i = 0; i < SEGMENTS; i++) {
            float ang0 = (float) (i * Math.PI * 2 / SEGMENTS);
            float ang1 = (float) ((i + 1) * Math.PI * 2 / SEGMENTS);
            float x0 = Mth.cos(ang0) * r, z0 = Mth.sin(ang0) * r;
            float x1 = Mth.cos(ang1) * r, z1 = Mth.sin(ang1) * r;
            float nx = Mth.cos((ang0 + ang1) / 2), nz = Mth.sin((ang0 + ang1) / 2);
            vertex(vc, m, n, x0, y0, z0, red, green, blue, a0, nx, 0, nz);
            vertex(vc, m, n, x1, y0, z1, red, green, blue, a0, nx, 0, nz);
            vertex(vc, m, n, x1, y1, z1, red, green, blue, a1, nx, 0, nz);
            vertex(vc, m, n, x0, y1, z0, red, green, blue, a1, nx, 0, nz);
        }
    }

    private static void flatRing(VertexConsumer vc, Matrix4f m, Matrix3f n, float inner, float outer, float y,
                                 float red, float green, float blue, float a) {
        for (int i = 0; i < SEGMENTS; i++) {
            float ang0 = (float) (i * Math.PI * 2 / SEGMENTS);
            float ang1 = (float) ((i + 1) * Math.PI * 2 / SEGMENTS);
            float c0 = Mth.cos(ang0), s0 = Mth.sin(ang0), c1 = Mth.cos(ang1), s1 = Mth.sin(ang1);
            vertex(vc, m, n, c0 * inner, y, s0 * inner, red, green, blue, a, 0, 1, 0);
            vertex(vc, m, n, c1 * inner, y, s1 * inner, red, green, blue, a, 0, 1, 0);
            vertex(vc, m, n, c1 * outer, y, s1 * outer, red, green, blue, a, 0, 1, 0);
            vertex(vc, m, n, c0 * outer, y, s0 * outer, red, green, blue, a, 0, 1, 0);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z,
                               float r, float g, float b, float a, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, nx, ny, nz).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(KishiDomeEntity dome) {
        return WHITE;
    }
}
