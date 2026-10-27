package com.xenowarriors.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Outfits in 3D: the outer layer of a skin-style outfit texture (the jacket, sleeves, pants and hat parts, where
 * DragonMineZ puts belts, collars, wristbands, armor plates and cuffs) built out of little raised blocks, one per pixel,
 * standing off the body instead of being painted flat. Built once per texture, bent at the knees and elbows like the
 * body under it.
 */
public final class GiVoxels {
    /** One body part's blocks: {x0, y0, z0, x1, y1, z1, u, v, faceMask} in that part's own space (pixels). */
    private record Part(List<float[]> upper, List<float[]> lower, float split) {}
    private record Layer(Map<String, Part> parts) {}
    private static final Map<String, Layer> CACHE = new HashMap<>();   // texture + "#" + inflate
    private static final float DEPTH = 0.9F;

    private GiVoxels() {}

    // part name, texture u, v, box x0, y0, z0, w, h, d
    private static final Object[][] OVERLAYS = {
            {"hat", 32, 0, -4F, -8F, -4F, 8, 8, 8},
            {"jacket", 16, 32, -4F, 0F, -2F, 8, 12, 4},
            {"right_sleeve", 40, 32, -3F, -2F, -2F, 4, 12, 4},
            {"left_sleeve", 48, 48, -1F, -2F, -2F, 4, 12, 4},
            {"right_pants", 0, 32, -2F, 0F, -2F, 4, 12, 4},
            {"left_pants", 0, 48, -2F, 0F, -2F, 4, 12, 4},
    };

    private static Layer load(ResourceLocation tex, float inflate) {
        NativeImage img = null;
        try (var in = Minecraft.getInstance().getResourceManager().open(tex)) {
            img = NativeImage.read(in);
        } catch (Exception e) {
            return new Layer(Map.of());
        }
        try {
            int s = Math.max(1, img.getWidth() / 64);
            Map<String, Part> parts = new HashMap<>();
            for (Object[] o : OVERLAYS) {
                String name = (String) o[0];
                int u = (int) o[1], v = (int) o[2], w = (int) o[6], h = (int) o[7], d = (int) o[8];
                float x0 = (float) o[3], y0 = (float) o[4], z0 = (float) o[5], x1 = x0 + w, y1 = y0 + h, z1 = z0 + d;
                float split = com.xenowarriors.client.LimbBend.splitY(name);
                List<float[]> up = new ArrayList<>(), low = new ArrayList<>();
                float in = inflate;
                // each face of the box: texture rectangle, and where pixel (i, j) sits on the body
                // north (front) -z, south (back) +z, east (his right, -x), west (his left, +x), up (top)
                int[][] faces = {{u + d, v + d, w, h, 0}, {u + 2 * d + w, v + d, w, h, 1}, {u, v + d, d, h, 2}, {u + d + w, v + d, d, h, 3}, {u + d, v, w, d, 4}};
                for (int[] f : faces) {
                    boolean[][] solid = new boolean[f[2]][f[3]];
                    for (int i = 0; i < f[2]; i++) for (int j = 0; j < f[3]; j++) {
                        int px = img.getPixelRGBA((f[0] + i) * s + s / 2, (f[1] + j) * s + s / 2);
                        solid[i][j] = ((px >>> 24) & 0xFF) > 24;
                    }
                    for (int i = 0; i < f[2]; i++) for (int j = 0; j < f[3]; j++) {
                        if (!solid[i][j]) continue;
                        float[] b;
                        switch (f[4]) {
                            case 0 -> b = new float[]{x0 + i, y0 + j, z0 - in - DEPTH, x0 + i + 1, y0 + j + 1, z0 - in};
                            case 1 -> b = new float[]{x1 - i - 1, y0 + j, z1 + in, x1 - i, y0 + j + 1, z1 + in + DEPTH};
                            case 2 -> b = new float[]{x0 - in - DEPTH, y0 + j, z1 - i - 1, x0 - in, y0 + j + 1, z1 - i};
                            case 3 -> b = new float[]{x1 + in, y0 + j, z0 + i, x1 + in + DEPTH, y0 + j + 1, z0 + i + 1};
                            default -> b = new float[]{x0 + i, y0 - in - DEPTH, z1 - j - 1, x0 + i + 1, y0 - in, z1 - j};
                        }
                        // sides shared with a neighbour on the same face are skipped (bit per side: -x +x -y +y -z +z)
                        int mask = 0x3F;
                        boolean l = i > 0 && solid[i - 1][j], r = i < f[2] - 1 && solid[i + 1][j];
                        boolean t = j > 0 && solid[i][j - 1], bo = j < f[3] - 1 && solid[i][j + 1];
                        switch (f[4]) {
                            case 0 -> mask &= ~((l ? 1 : 0) | (r ? 2 : 0) | (t ? 4 : 0) | (bo ? 8 : 0) | 32);
                            case 1 -> mask &= ~((l ? 2 : 0) | (r ? 1 : 0) | (t ? 4 : 0) | (bo ? 8 : 0) | 16);
                            case 2 -> mask &= ~((l ? 32 : 0) | (r ? 16 : 0) | (t ? 4 : 0) | (bo ? 8 : 0) | 2);
                            case 3 -> mask &= ~((l ? 16 : 0) | (r ? 32 : 0) | (t ? 4 : 0) | (bo ? 8 : 0) | 1);
                            default -> mask &= ~((l ? 1 : 0) | (r ? 2 : 0) | (t ? 32 : 0) | (bo ? 16 : 0) | 8);
                        }
                        float tu = ((f[0] + i) + 0.5F) / 64F, tv = ((f[1] + j) + 0.5F) / 64F;
                        float[] vox = {b[0], b[1], b[2], b[3], b[4], b[5], tu, tv, mask};
                        if (split > 0F && (b[1] + b[4]) / 2F > split + y0 + 2F) {           // below the knee / elbow
                            vox[1] -= split; vox[4] -= split;
                            low.add(vox);
                        } else up.add(vox);
                    }
                }
                parts.put(name, new Part(up, low, split));
            }
            return new Layer(parts);
        } finally {
            img.close();
        }
    }

    /** Draws the raised outer layer of {@code tex} on the visible parts of {@code model} (which is posed and bent). */
    public static void render(PoseStack pose, VertexConsumer vc, ResourceLocation tex, PlayerModel<?> model, float inflate,
                              int light, int overlay, float r, float g, float b) {
        Layer layer = CACHE.computeIfAbsent(tex + "#" + inflate, k -> load(tex, inflate));
        if (layer.parts.isEmpty()) return;
        draw(pose, vc, layer.parts.get("hat"), model.head, light, overlay, r, g, b);
        draw(pose, vc, layer.parts.get("jacket"), model.body, light, overlay, r, g, b);
        draw(pose, vc, layer.parts.get("right_sleeve"), model.rightArm, light, overlay, r, g, b);
        draw(pose, vc, layer.parts.get("left_sleeve"), model.leftArm, light, overlay, r, g, b);
        draw(pose, vc, layer.parts.get("right_pants"), model.rightLeg, light, overlay, r, g, b);
        draw(pose, vc, layer.parts.get("left_pants"), model.leftLeg, light, overlay, r, g, b);
    }

    private static void draw(PoseStack pose, VertexConsumer vc, Part part, ModelPart on, int light, int overlay, float r, float g, float b) {
        if (part == null || !on.visible) return;
        pose.pushPose();
        on.translateAndRotate(pose);
        boxes(pose, vc, part.upper, light, overlay, r, g, b);
        if (!part.lower.isEmpty() && on.hasChild(com.xenowarriors.client.LimbBend.LOWER)) {
            pose.pushPose();
            on.getChild(com.xenowarriors.client.LimbBend.LOWER).translateAndRotate(pose);
            boxes(pose, vc, part.lower, light, overlay, r, g, b);
            pose.popPose();
        } else if (!part.lower.isEmpty()) {
            pose.pushPose();
            pose.translate(0, part.split / 16F, 0);
            boxes(pose, vc, part.lower, light, overlay, r, g, b);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void boxes(PoseStack pose, VertexConsumer vc, List<float[]> list, int light, int overlay, float r, float g, float b) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (float[] v : list) {
            float x0 = v[0] / 16F, y0 = v[1] / 16F, z0 = v[2] / 16F, x1 = v[3] / 16F, y1 = v[4] / 16F, z1 = v[5] / 16F, u = v[6], t = v[7];
            int mask = (int) v[8];
            if ((mask & 1) != 0) quad(vc, m, n, -1, 0, 0, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, u, t, light, overlay, r, g, b);
            if ((mask & 2) != 0) quad(vc, m, n, 1, 0, 0, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, u, t, light, overlay, r, g, b);
            if ((mask & 4) != 0) quad(vc, m, n, 0, -1, 0, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, u, t, light, overlay, r, g, b);
            if ((mask & 8) != 0) quad(vc, m, n, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, u, t, light, overlay, r, g, b);
            if ((mask & 16) != 0) quad(vc, m, n, 0, 0, -1, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, u, t, light, overlay, r, g, b);
            if ((mask & 32) != 0) quad(vc, m, n, 0, 0, 1, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, u, t, light, overlay, r, g, b);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float u, float t, int light, int overlay, float r, float g, float b) {
        // (model space, like the game's own cubes: y points down)
        vc.vertex(m, ax, ay, az).color(r, g, b, 1F).uv(u, t).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, bx, by, bz).color(r, g, b, 1F).uv(u, t).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, cx, cy, cz).color(r, g, b, 1F).uv(u, t).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, dx, dy, dz).color(r, g, b, 1F).uv(u, t).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
