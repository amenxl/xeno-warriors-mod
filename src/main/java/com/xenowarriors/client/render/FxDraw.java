package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Glowing see-through shapes for gravity effects (spheres, rings, beams). */
public final class FxDraw {
    public static final ResourceLocation WHITE = XenoWarriors.id("textures/entity/kishi_dome.png");

    private static final float PI = (float) Math.PI, TWO_PI = PI * 2F;

    private FxDraw() {}

    public static VertexConsumer glow(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
    }

    /**
     * A gravity sphere: a faint shell with bright bands that ripple toward the poles (or inward with
     * a negative speed), so it reads as pressure pushing in. Color is 0..1 RGB.
     */
    public static void sphere(PoseStack pose, VertexConsumer vc, float radius, float r, float g, float b,
                              float alpha, float age, float bandSpeed) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int stacks = 18, slices = 36;
        for (int i = 0; i < stacks; i++) {
            float v0 = (float) i / stacks, v1 = (float) (i + 1) / stacks;
            float lat0 = (v0 - 0.5F) * PI, lat1 = (v1 - 0.5F) * PI;
            float a0 = alpha * band(v0, age, bandSpeed), a1 = alpha * band(v1, age, bandSpeed);
            for (int j = 0; j < slices; j++) {
                float lon0 = j * TWO_PI / slices, lon1 = (j + 1) * TWO_PI / slices;
                quadSphere(vc, m, n, radius, lat0, lat1, lon0, lon1, r, g, b, a0, a1);
            }
        }
    }

    private static float band(float v, float age, float speed) {
        // brighter near the equator rim, plus moving bands
        float wave = Mth.sin(v * PI * 7F - age * speed);
        float bandGlow = wave > 0.75F ? (wave - 0.75F) * 4F : 0F;
        return 0.35F + 0.65F * bandGlow;
    }

    private static void quadSphere(VertexConsumer vc, Matrix4f m, Matrix3f n, float rad,
                                   float lat0, float lat1, float lon0, float lon1,
                                   float r, float g, float b, float a0, float a1) {
        sv(vc, m, n, rad, lat0, lon0, r, g, b, a0);
        sv(vc, m, n, rad, lat0, lon1, r, g, b, a0);
        sv(vc, m, n, rad, lat1, lon1, r, g, b, a1);
        sv(vc, m, n, rad, lat1, lon0, r, g, b, a1);
    }

    private static void sv(VertexConsumer vc, Matrix4f m, Matrix3f n, float rad, float lat, float lon,
                           float r, float g, float b, float a) {
        float cl = Mth.cos(lat);
        float x = cl * Mth.cos(lon), y = Mth.sin(lat), z = cl * Mth.sin(lon);
        vertex(vc, m, n, x * rad, y * rad, z * rad, r, g, b, a, x, y, z);
    }

    /** A thin glowing band around the Y axis (rotate the pose first to tilt it). */
    public static void ring(PoseStack pose, VertexConsumer vc, float radius, float width, float r, float g, float b, float alpha) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int seg = 40;
        for (int i = 0; i < seg; i++) {
            float t0 = i * TWO_PI / seg, t1 = (i + 1) * TWO_PI / seg;
            float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
            vertex(vc, m, n, c0 * radius, -width / 2, s0 * radius, r, g, b, alpha, c0, 0, s0);
            vertex(vc, m, n, c1 * radius, -width / 2, s1 * radius, r, g, b, alpha, c1, 0, s1);
            vertex(vc, m, n, c1 * radius, width / 2, s1 * radius, r, g, b, alpha, c1, 0, s1);
            vertex(vc, m, n, c0 * radius, width / 2, s0 * radius, r, g, b, alpha, c0, 0, s0);
        }
    }

    /** A flat ring lying on the ground (inner radius 0 = filled disc). */
    public static void disc(PoseStack pose, VertexConsumer vc, float inner, float outer, float r, float g, float b, float aInner, float aOuter) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int seg = 40;
        for (int i = 0; i < seg; i++) {
            float t0 = i * TWO_PI / seg, t1 = (i + 1) * TWO_PI / seg;
            float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
            vertex(vc, m, n, c0 * inner, 0, s0 * inner, r, g, b, aInner, 0, 1, 0);
            vertex(vc, m, n, c1 * inner, 0, s1 * inner, r, g, b, aInner, 0, 1, 0);
            vertex(vc, m, n, c1 * outer, 0, s1 * outer, r, g, b, aOuter, 0, 1, 0);
            vertex(vc, m, n, c0 * outer, 0, s0 * outer, r, g, b, aOuter, 0, 1, 0);
        }
    }

    /** A glowing beam from the pose origin to (dx, dy, dz), drawn as two crossed strips. */
    public static void beam(PoseStack pose, VertexConsumer vc, float dx, float dy, float dz, float width,
                            float r, float g, float b, float alpha) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01F) return;
        // two perpendicular offsets
        float ux, uy, uz;
        if (Math.abs(dy) < 0.9F * len) { ux = -dz; uy = 0; uz = dx; } else { ux = 1; uy = 0; uz = 0; }
        float ul = Mth.sqrt(ux * ux + uy * uy + uz * uz);
        ux = ux / ul * width; uy = uy / ul * width; uz = uz / ul * width;
        float wx = (dy * uz - dz * uy) / len, wy = (dz * ux - dx * uz) / len, wz = (dx * uy - dy * ux) / len;
        for (int k = 0; k < 2; k++) {
            float ox = k == 0 ? ux : wx, oy = k == 0 ? uy : wy, oz = k == 0 ? uz : wz;
            vertex(vc, m, n, -ox, -oy, -oz, r, g, b, alpha, 0, 1, 0);
            vertex(vc, m, n, ox, oy, oz, r, g, b, alpha, 0, 1, 0);
            vertex(vc, m, n, dx + ox, dy + oy, dz + oz, r, g, b, alpha * 0.4F, 0, 1, 0);
            vertex(vc, m, n, dx - ox, dy - oy, dz - oz, r, g, b, alpha * 0.4F, 0, 1, 0);
        }
    }

    public static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z,
                              float r, float g, float b, float a, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, Math.max(0F, Math.min(1F, a))).uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, nx, ny, nz).endVertex();
    }
}
