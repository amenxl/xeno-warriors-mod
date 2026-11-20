package com.xenowarriors.client.compat.epicfight;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

/**
 * Long coats (Technica's and Yuri's lab coats...) on Epic Fight's body: a coat that hangs below the hips used to be
 * one rigid box on the chest bone - when she bent or kicked, its bottom swung out like a board and her legs came
 * through it (the user's video). Here a long coat is cut at the hips: the top stays on the chest, and the skirt below
 * is cut down the middle into two flaps that hang from the hips and swing with each thigh (part of the way), the way
 * a coat's tails move.
 */
final class CoatSkirt {
    private CoatSkirt() {}

    /** Model y (px below the neck) of the hips, where the skirt starts. */
    static final float HIPS = 12F;
    /** How far the flaps follow the thighs, and the most they swing (radians). */
    static final float FOLLOW = 0.8F, MAX = 1.2F;

    private static java.lang.reflect.Field polygonsField;

    /** A long coat cube: from the chest down past the hips. */
    static boolean longCoat(ModelPart.Cube c) { return c.minY < 2F && c.maxY > 14.5F; }

    /** The pieces of a body with a long coat: the cubes for the chest, and the two flaps (null: no long coat here). */
    record Split(List<ModelPart.Cube> chest, ModelPart right, ModelPart left) {}

    static Split split(List<ModelPart.Cube> body) {
        if (body.stream().noneMatch(CoatSkirt::longCoat)) return null;
        List<ModelPart.Cube> chest = new ArrayList<>(), r = new ArrayList<>(), l = new ArrayList<>();
        for (ModelPart.Cube c : body) {
            if (c.maxY <= HIPS + 1F) { chest.add(c); continue; }                        // (above the hips, or only just over)
            ModelPart.Cube top = clip(c, 1, -999F, HIPS, 0F, 0F, 0F);
            if (top != null) chest.add(top);
            // below the hips: the right half (vanilla x < 0) hangs from the right hip, the left from the left
            ModelPart.Cube rr = clip(c, 1, HIPS, 999F, 0F, 0F, 0F);
            if (rr == null) continue;
            ModelPart.Cube right = clip(rr, 0, -999F, 0F, 1.9F, -HIPS, 0F), left = clip(rr, 0, 0F, 999F, -1.9F, -HIPS, 0F);
            if (right != null) r.add(right);
            if (left != null) l.add(left);
        }
        ModelPart pr = new ModelPart(r, new HashMap<>()), pl = new ModelPart(l, new HashMap<>());
        pr.setInitialPose(PartPose.offset(-1.9F, HIPS, 0F)); pr.resetPose();
        pl.setInitialPose(PartPose.offset(1.9F, HIPS, 0F)); pl.resetPose();
        return new Split(chest, pr, pl);
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

    /**
     * The part of a cube between lo and hi on an axis (0 x, 1 y, 2 z), its corners moved by (dx, dy, dz) - the faces cut
     * the way a box is cut, their texture following (null: nothing left).
     */
    static ModelPart.Cube clip(ModelPart.Cube c, int axis, float lo, float hi, float dx, float dy, float dz) {
        float[] min = {c.minX, c.minY, c.minZ}, max = {c.maxX, c.maxY, c.maxZ};
        float a = Math.max(min[axis], lo), b = Math.min(max[axis], hi);
        if (b - a < 0.01F) return null;
        min[axis] = a; max[axis] = b;
        try {
            ModelPart.Polygon[] src = polygons(c);
            ModelPart.Cube out = new ModelPart.Cube(0, 0, min[0] + dx, min[1] + dy, min[2] + dz, max[0] - min[0], max[1] - min[1], max[2] - min[2],
                    0F, 0F, 0F, false, 64F, 64F, EnumSet.allOf(Direction.class));
            ModelPart.Polygon[] dst = polygons(out);
            int n = 0;
            for (ModelPart.Polygon p : src) {
                ModelPart.Vertex[] v = clipPolygon(p.vertices, axis, a, b);
                if (v == null || n >= dst.length) continue;
                for (int i = 0; i < 4; i++) {
                    Vector3f q = v[i].pos;
                    dst[n].vertices[i] = new ModelPart.Vertex(q.x() + dx, q.y() + dy, q.z() + dz, v[i].u, v[i].v);
                }
                dst[n].normal.set(p.normal);
                n++;
            }
            for (; n < dst.length; n++) {                                                  // (faces cut away: nothing to draw)
                ModelPart.Vertex z = dst[n].vertices[0];
                for (int i = 0; i < 4; i++) dst[n].vertices[i] = z;
            }
            return out;
        } catch (Throwable t) {
            com.xenowarriors.XenoWarriors.LOGGER.warn("XW: couldn't cut a coat for Epic Fight", t);
            return null;
        }
    }

    /** A face's corners inside [a, b] on the axis (a rectangle stays a rectangle; null if none of it is). */
    private static ModelPart.Vertex[] clipPolygon(ModelPart.Vertex[] vs, int axis, float a, float b) {
        if (vs.length != 4) return null;
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (ModelPart.Vertex v : vs) { float c = coord(v.pos, axis); lo = Math.min(lo, c); hi = Math.max(hi, c); }
        if (hi < a + 0.001F || lo > b - 0.001F) {
            if (hi - lo < 0.001F) return null;                                             // (a face across the axis, outside)
            return null;
        }
        if (hi - lo < 0.001F) return vs;                                                   // (a face across the axis, inside)
        ModelPart.Vertex[] out = new ModelPart.Vertex[4];
        for (int i = 0; i < 4; i++) {
            ModelPart.Vertex v = vs[i];
            float c = coord(v.pos, axis);
            float t = Math.max(a, Math.min(b, c));
            if (t == c) { out[i] = v; continue; }
            // slide it along its edge to the partner corner at the other end of the axis
            ModelPart.Vertex o = null;
            for (ModelPart.Vertex w : vs) if (w != v && same(w.pos, v.pos, axis) && Math.abs(coord(w.pos, axis) - c) > 0.001F) { o = w; break; }
            if (o == null) { out[i] = v; continue; }
            float k = (t - c) / (coord(o.pos, axis) - c);
            Vector3f p = new Vector3f(v.pos).lerp(o.pos, k);
            out[i] = new ModelPart.Vertex(p.x(), p.y(), p.z(), v.u + (o.u - v.u) * k, v.v + (o.v - v.v) * k);
        }
        return out;
    }

    /** Same corner apart from the axis? */
    private static boolean same(Vector3f p, Vector3f q, int axis) {
        for (int i = 0; i < 3; i++) if (i != axis && Math.abs(coord(p, i) - coord(q, i)) > 0.001F) return false;
        return true;
    }

    private static float coord(Vector3f p, int axis) { return axis == 0 ? p.x() : axis == 1 ? p.y() : p.z(); }

    private static ModelPart.Polygon[] polygons(ModelPart.Cube c) throws IllegalAccessException {
        if (polygonsField == null) {
            for (java.lang.reflect.Field f : ModelPart.Cube.class.getDeclaredFields())
                if (f.getType() == ModelPart.Polygon[].class) { f.setAccessible(true); polygonsField = f; break; }
            if (polygonsField == null) throw new IllegalStateException("no polygons");
        }
        return (ModelPart.Polygon[]) polygonsField.get(c);
    }
}
