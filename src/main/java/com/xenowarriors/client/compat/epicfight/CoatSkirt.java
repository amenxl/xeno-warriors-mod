package com.xenowarriors.client.compat.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Long coats (Technica's and Yuri's lab coats...) on Epic Fight's body: a coat that hangs below the hips used to be
 * one rigid box on the chest bone - when she bent or kicked, its bottom swung out like a board and her legs came
 * through it (the user's video). Here a long coat is cut at the hips: the top stays on the chest, and the skirt below
 * is cut down the middle into two flaps that hang from the hips and swing with each thigh (part of the way), the way
 * a coat's tails move.
 * <p>
 * The cut pieces are drawn as their own faces (not as cubes: Embeddium draws a cube from its own copy of it, so a cube
 * with its faces cut came out invisible - the first try).
 */
public final class CoatSkirt {
    private CoatSkirt() {}

    /** Model y (px below the neck) of the hips, where the skirt starts. */
    static final float HIPS = 12F;
    /** How far the flaps follow the thighs, and the most they swing (radians). */
    static final float FOLLOW = 0.8F, MAX = 1.2F;

    /** (Tests) what the cuts made. */
    public static final StringBuilder DEBUG = new StringBuilder();

    /** A long coat cube: from the chest down past the hips. */
    static boolean longCoat(ModelPart.Cube c) { return c.minY < 2F && c.maxY > 14.5F; }

    /** A face: its four corners (x, y, z in model px; u, v) and its normal. */
    record Quad(float[][] v, Vector3f n) {}

    /** Faces hanging from a pivot (model px), with its swing. */
    static final class Flap {
        final List<Quad> quads = new ArrayList<>();
        final float px, py, pz;
        float xRot, zRot;
        Flap(float px, float py, float pz) { this.px = px; this.py = py; this.pz = pz; }

        void render(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b) {
            pose.pushPose();
            pose.translate(px / 16F, py / 16F, pz / 16F);
            if (xRot != 0F || zRot != 0F) pose.mulPose(new Quaternionf().rotationZYX(zRot, 0F, xRot));
            Matrix4f m = pose.last().pose(); Matrix3f nm = pose.last().normal();
            for (Quad q : quads) {
                Vector3f n = nm.transform(new Vector3f(q.n()));
                for (float[] c : q.v()) {
                    Vector4f p = m.transform(new Vector4f(c[0] / 16F, c[1] / 16F, c[2] / 16F, 1F));
                    vc.vertex(p.x(), p.y(), p.z(), r, g, b, 1F, c[3], c[4], overlay, light, n.x(), n.y(), n.z());
                }
            }
            pose.popPose();
        }
    }

    /** The pieces of a body with a long coat: the cubes left as they are, the coat's top (on the chest) and the two flaps (null: no long coat here). */
    record Split(List<ModelPart.Cube> chest, Flap top, Flap right, Flap left) {}

    static Split split(List<ModelPart.Cube> body) {
        if (body.stream().noneMatch(CoatSkirt::longCoat)) return null;
        if (!reflect()) return null;                                                     // (can't cut: the coat stays as it was)
        List<ModelPart.Cube> chest = new ArrayList<>();
        Flap top = new Flap(0F, 0F, 0F), r = new Flap(-1.9F, HIPS, 0F), l = new Flap(1.9F, HIPS, 0F);
        int cut = 0;
        for (ModelPart.Cube c : body) {
            if (c.maxY <= HIPS + 1F) { chest.add(c); continue; }                        // (above the hips, or only just over)
            List<Quad> faces;
            try { faces = faces(c); } catch (Throwable t) { chest.add(c); continue; }   // (can't read it: as it was)
            cut++;
            for (Quad q : faces) {
                Quad t = clip(q, 1, -999F, HIPS);
                if (t != null) top.quads.add(t);
                Quad below = clip(q, 1, HIPS, 999F);
                if (below == null) continue;
                // below the hips: the right half (vanilla x < 0) hangs from the right hip, the left from the left
                Quad rq = clip(below, 0, -999F, 0F), lq = clip(below, 0, 0F, 999F);
                if (rq != null) r.quads.add(shift(rq, 1.9F, -HIPS, 0F));
                if (lq != null) l.quads.add(shift(lq, -1.9F, -HIPS, 0F));
            }
        }
        if (cut == 0) return null;
        if (DEBUG.length() < 600) DEBUG.append("cut ").append(cut).append(" coat cubes: top ").append(top.quads.size()).append(" faces, flaps ")
                .append(r.quads.size()).append('+').append(l.quads.size()).append("; ");
        return new Split(chest, top, r, l);
    }

    /** Swings the flaps with the thighs ({xRot, zRot} as vanilla leg angles, see legAngles). */
    static void swing(Split s, float[] right, float[] left) {
        s.right().xRot = clamp(right == null ? 0F : right[0] * FOLLOW); s.right().zRot = clamp(right == null ? 0F : right[1] * FOLLOW * 0.7F);
        s.left().xRot = clamp(left == null ? 0F : left[0] * FOLLOW); s.left().zRot = clamp(left == null ? 0F : left[1] * FOLLOW * 0.7F);
        // (a flap never swings into the body: a thigh kicked back lifts it less than one kicked forward)
        if (s.right().xRot > 0F) s.right().xRot *= 0.6F;
        if (s.left().xRot > 0F) s.left().xRot *= 0.6F;
    }

    private static float clamp(float a) { return Math.max(-MAX, Math.min(MAX, a)); }

    private static Quad shift(Quad q, float dx, float dy, float dz) {
        float[][] v = new float[4][];
        for (int i = 0; i < 4; i++) v[i] = new float[]{q.v()[i][0] + dx, q.v()[i][1] + dy, q.v()[i][2] + dz, q.v()[i][3], q.v()[i][4]};
        return new Quad(v, q.n());
    }

    /** A face's part between a and b on an axis (0 x, 1 y, 2 z): a rectangle stays a rectangle, its texture following (null if none of it is). */
    static Quad clip(Quad q, int axis, float a, float b) {
        float[][] vs = q.v();
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float[] v : vs) { lo = Math.min(lo, v[axis]); hi = Math.max(hi, v[axis]); }
        if (hi - lo < 0.001F) return lo >= a - 0.001F && lo <= b + 0.001F ? q : null;     // (a face across the axis: in or out)
        if (hi <= a + 0.001F || lo >= b - 0.001F) return null;
        float[][] out = new float[4][];
        for (int i = 0; i < 4; i++) {
            float[] v = vs[i];
            float c = v[axis], t = Math.max(a, Math.min(b, c));
            if (t == c) { out[i] = v; continue; }
            float[] o = null;                                                            // its partner at the other end of the axis
            for (float[] w : vs) if (w != v && same(w, v, axis) && Math.abs(w[axis] - c) > 0.001F) { o = w; break; }
            if (o == null) { out[i] = v; continue; }
            float k = (t - c) / (o[axis] - c);
            float[] p = new float[5];
            for (int j = 0; j < 5; j++) p[j] = v[j] + (o[j] - v[j]) * k;
            out[i] = p;
        }
        return new Quad(out, q.n());
    }

    private static boolean same(float[] p, float[] q, int axis) {
        for (int i = 0; i < 3; i++) if (i != axis && Math.abs(p[i] - q[i]) > 0.001F) return false;
        return true;
    }

    // ModelPart's Polygon and Vertex are private: their insides reached by what they hold (read only)
    private static java.lang.reflect.Field polygonsField, verticesField, normalField, posField, uField, vField;
    private static boolean reflected, reflectOk;

    /** A cube's faces as it was built (corners, texture spots, normals). */
    private static List<Quad> faces(ModelPart.Cube c) throws IllegalAccessException {
        List<Quad> out = new ArrayList<>();
        for (Object p : (Object[]) polygonsField.get(c)) {
            Object[] vs = (Object[]) verticesField.get(p);
            if (vs.length != 4) continue;
            float[][] v = new float[4][];
            for (int i = 0; i < 4; i++) {
                Vector3f pos = (Vector3f) posField.get(vs[i]);
                v[i] = new float[]{pos.x(), pos.y(), pos.z(), uField.getFloat(vs[i]), vField.getFloat(vs[i])};
            }
            out.add(new Quad(v, new Vector3f((Vector3f) normalField.get(p))));
        }
        return out;
    }

    private static boolean reflect() {
        if (reflected) return reflectOk;
        reflected = true;
        try {
            for (java.lang.reflect.Field f : ModelPart.Cube.class.getDeclaredFields())
                if (f.getType().isArray() && f.getType().getComponentType().getEnclosingClass() == ModelPart.class) { f.setAccessible(true); polygonsField = f; }
            Class<?> poly = polygonsField.getType().getComponentType();
            for (java.lang.reflect.Field f : poly.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                if (f.getType().isArray()) verticesField = f;
                else if (f.getType() == Vector3f.class) normalField = f;
            }
            Class<?> vert = verticesField.getType().getComponentType();
            List<java.lang.reflect.Field> floats = new ArrayList<>();
            for (java.lang.reflect.Field f : vert.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                if (f.getType() == Vector3f.class) posField = f;
                else if (f.getType() == float.class) floats.add(f);
            }
            uField = floats.get(0); vField = floats.get(1);
            reflectOk = posField != null && normalField != null;
        } catch (Throwable t) {
            com.xenowarriors.XenoWarriors.LOGGER.warn("XW: coats for Epic Fight - ModelPart's insides not as expected", t);
            reflectOk = false;
        }
        return reflectOk;
    }
}
