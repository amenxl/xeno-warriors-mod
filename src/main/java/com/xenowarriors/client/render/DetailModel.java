package com.xenowarriors.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * A character's 3D details (hair, clothes, gear, wings), loaded from
 * assets/xenowarriors/models/detail/<character>.json. That file is generated from the
 * Blockbench model, already in Minecraft model space, with the same six bones as the player
 * model so the details move with the head, body, arms and legs.
 */
public class DetailModel {
    public final ModelPart root;
    public final ModelPart head, body, rightArm, leftArm, rightLeg, leftLeg;
    /** Hair and goggles (hidden under the SR hood), and the front bangs that stay visible. May be null. */
    public final ModelPart hair, bangs;
    /** Rhalix's coat tails (front flaps, sides, back) - null for characters without them. */
    public final ModelPart coatBack, coatFrontR, coatFrontL, coatSideR, coatSideL;
    /** null for characters without wings */
    public final ModelPart rightWing, leftWing;
    /** High-res face (its own texture, 4x the pixels of a normal face); null if the character has none. */
    public ModelPart faceRoot, faceHead;
    private final float rightWingZ, leftWingZ, rightWingY, leftWingY;

    /** Outfit hood, pulled up over the head / bunched behind the neck (null if the outfit has none). */
    public ModelPart hoodUp, hoodDown;

    private DetailModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.hair = childOrNull(head, "hair");
        this.bangs = childOrNull(head, "bangs");
        this.hoodUp = childOrNull(head, "hood_up");
        this.hoodDown = childOrNull(body, "hood_down");
        this.coatBack = childOrNull(body, "coat_back");
        this.coatFrontR = childOrNull(body, "coat_front_r");
        this.coatFrontL = childOrNull(body, "coat_front_l");
        this.coatSideR = childOrNull(body, "coat_side_r");
        this.coatSideL = childOrNull(body, "coat_side_l");
        this.rightWing = childOrNull(body, "right_wing");
        this.leftWing = childOrNull(body, "left_wing");
        this.rightWingZ = rightWing != null ? rightWing.zRot : 0;
        this.leftWingZ = leftWing != null ? leftWing.zRot : 0;
        this.rightWingY = rightWing != null ? rightWing.yRot : 0;
        this.leftWingY = leftWing != null ? leftWing.yRot : 0;
        if (hair != null) collectStrands(hair);
        if (bangs != null) collectStrands(bangs);
        if (hair != null) findHoodPokers();
    }

    /** Hair strands that would stick out through the outfit's hood (hidden while it's up); the rest still show under it. */
    public final java.util.List<ModelPart> hoodPokers = new java.util.ArrayList<>();

    private void findHoodPokers() {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        // (no hood of its own: the standard hoodie hood's size - the Karma-Reducing Hoodie's)
        if (hoodUp == null) { b[0] = -5.45F; b[1] = -10.8F; b[2] = -5.1F; b[3] = 5.45F; b[4] = -0.6F; b[5] = 5.65F; }
        else hoodUp.visit(new com.mojang.blaze3d.vertex.PoseStack(), (pose, path, index, cube) -> {
            for (org.joml.Vector3f c : corners(pose, cube)) {
                b[0] = Math.min(b[0], c.x); b[1] = Math.min(b[1], c.y); b[2] = Math.min(b[2], c.z);
                b[3] = Math.max(b[3], c.x); b[4] = Math.max(b[4], c.y); b[5] = Math.max(b[5], c.z);
            }
        });
        float in = 0.15F;
        java.util.Set<String> poke = new java.util.HashSet<>();
        hair.visit(new com.mojang.blaze3d.vertex.PoseStack(), (pose, path, index, cube) -> {
            String rel = path.startsWith("/") ? path.substring(1) : path;      // paths look like "/strand/piece"
            int slash = rel.indexOf('/');
            String strand = slash < 0 ? rel : rel.substring(0, slash);
            if (strand.isEmpty()) return;     // the cap itself sits under the hood
            for (org.joml.Vector3f c : corners(pose, cube)) {
                // the open front of the hood lets the fringe through; anywhere else outside it would poke through
                if (c.x < b[0] + in || c.x > b[3] - in || c.y < b[1] + in || c.z > b[5] - in) { poke.add(strand); return; }
            }
        });
        for (String name : poke) {
            if (hair.hasChild(name)) hoodPokers.add(hair.getChild(name));
        }
    }

    private static org.joml.Vector3f[] corners(com.mojang.blaze3d.vertex.PoseStack.Pose pose, ModelPart.Cube cube) {
        org.joml.Vector3f[] out = new org.joml.Vector3f[8];
        int i = 0;
        for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 2; dy++) for (int dz = 0; dz < 2; dz++) {
            org.joml.Vector4f v = new org.joml.Vector4f((dx == 0 ? cube.minX : cube.maxX) / 16F, (dy == 0 ? cube.minY : cube.maxY) / 16F,
                    (dz == 0 ? cube.minZ : cube.maxZ) / 16F, 1F);
            pose.pose().transform(v);
            out[i++] = new org.joml.Vector3f(v.x * 16F, v.y * 16F, v.z * 16F);
        }
        return out;
    }

    // ---------------------------------------------------------------- hair blowing in the wind

    /**
     * One hair strand / dread / lock. All the stacked pieces of a strand share the same root and angle, so they're
     * grouped and the maths is done once per strand, not once per piece.
     */
    private static final class Strand {
        final java.util.List<ModelPart> parts = new java.util.ArrayList<>();
        final org.joml.Quaternionf base;
        final org.joml.Vector3f back, side;
        final float baseX, baseY, baseZ, phase;
        /** Super Saiyan styles (index 1, 2, 3): the strand's pose and how much longer it gets; null if it isn't hair. */
        org.joml.Quaternionf[] style;
        final float[] scale = {1F, 1F, 1F, 1F};
        /** SSJ3: how far the root moves back so the long hair falls from the back of the head */
        float back3;
        final float pivotZ;

        Strand(ModelPart first, org.joml.Quaternionf base, org.joml.Vector3f back, org.joml.Vector3f side, float phase) {
            parts.add(first);
            this.base = base;
            this.back = back;
            this.side = side;
            this.baseX = first.xRot;
            this.baseY = first.yRot;
            this.baseZ = first.zRot;
            this.phase = phase;
            this.pivotZ = first.z;
        }
    }

    private final java.util.List<Strand> strands = new java.util.ArrayList<>();
    private boolean hairMoved;
    private float lastStream = -1F, lastFlutter = -1F, lastAge = -1F;
    // scratch objects, reused every frame (no garbage)
    private static final org.joml.Quaternionf DELTA = new org.joml.Quaternionf(), TMP = new org.joml.Quaternionf(),
            OUT = new org.joml.Quaternionf();

    /** The stick in Rhalix's mouth (low pieces in the hair group, by his mouth); empty for others. */
    public final java.util.List<ModelPart> mouthStick = new java.util.ArrayList<>();

    private void collectStrands(ModelPart group) {
        java.util.Map<String, Strand> byRoot = new java.util.HashMap<>();
        group.getAllParts().forEach(p -> {
            if (p == group) return;
            if (p.y > -5F && p.z < -3.5F) {      // not hair: the stick in his mouth (doesn't blow in the wind)
                mouthStick.add(p);
                return;
            }
            String key = Math.round(p.x * 100) + "," + Math.round(p.y * 100) + "," + Math.round(p.z * 100) + ","
                    + Math.round(p.xRot * 1000) + "," + Math.round(p.yRot * 1000) + "," + Math.round(p.zRot * 1000);
            Strand existing = byRoot.get(key);
            if (existing != null) {
                existing.parts.add(p);
                return;
            }
            var base = new org.joml.Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot);
            var dir = base.transform(new org.joml.Vector3f(0F, -1F, 0F));        // the way the strand points
            var back = new org.joml.Vector3f(dir).cross(0F, 0F, 1F);           // swing it toward the back
            var side = new org.joml.Vector3f(dir).cross(1F, 0F, 0F);           // and side to side
            if (back.lengthSquared() < 1.0E-4F) back.set(1F, 0F, 0F);
            if (side.lengthSquared() < 1.0E-4F) side.set(0F, 0F, 1F);
            Strand st = new Strand(p, base, back.normalize(), side.normalize(), strands.size() * 0.7F);
            strands.add(st);
            byRoot.put(key, st);
            if (p.y < -5F) styleStrand(st, p.x, p.y, p.z, dir);    // hair on the head (not a cigarette or toothpick)
        });
        pickBang();
    }

    /** Works out where the strand points in each Super Saiyan style. */
    private static void styleStrand(Strand st, float x, float y, float z, org.joml.Vector3f dir) {
        // model space: -Y is up, -Z is the face; the head is centred on (0, -4, 0)
        var out = new org.joml.Vector3f(x, 0F, z);
        if (out.length() < 0.6F) out.set(0F, 0F, 1F);
        out.normalize();
        st.style = new org.joml.Quaternionf[4];
        // SSJ: big spikes standing up and fanning outward, swept a little back
        st.style[1] = towards(st.base, dir, new org.joml.Vector3f(out).mul(0.8F).add(0F, -1F, 0.35F));
        st.scale[1] = 1.12F;
        // SSJ2: stiffer, sharper and more upright
        st.style[2] = towards(st.base, dir, new org.joml.Vector3f(out).mul(0.5F).add(0F, -1F, 0.15F));
        st.scale[2] = 1.25F;
        // SSJ3: the front stays spiked up, everything else grows long and flows down the back
        if (z < -2.5F) {
            st.style[3] = towards(st.base, dir, new org.joml.Vector3f(out).mul(0.45F).add(0F, -1F, 0.1F));
            st.scale[3] = 1.3F;
        } else {
            st.style[3] = towards(st.base, dir, new org.joml.Vector3f(out.x * 0.22F, 1F, 0.1F + out.z * 0.08F));
            st.scale[3] = 3.2F;
            st.back3 = Math.max(0F, 4.3F - z);
        }
    }

    private static org.joml.Quaternionf towards(org.joml.Quaternionf base, org.joml.Vector3f from, org.joml.Vector3f to) {
        return new org.joml.Quaternionf().rotationTo(from, to.normalize()).mul(base);
    }

    /** SSJ2's single bang: the front-most lock hangs down over the forehead. */
    private void pickBang() {
        Strand best = null;
        float bestScore = Float.MAX_VALUE;
        for (Strand st : strands) {
            if (st.style == null) continue;
            ModelPart p = st.parts.get(0);
            if (p.z > -3.5F) continue;
            float score = p.z + Math.abs(p.x + 0.8F) * 0.4F;
            if (score < bestScore) { bestScore = score; best = st; }
        }
        if (best == null) return;
        var dir = best.base.transform(new org.joml.Vector3f(0F, -1F, 0F));
        best.style[2] = towards(best.base, dir, new org.joml.Vector3f(0.15F, 1F, -0.45F));
        best.scale[2] = 1.15F;
    }

    /**
     * Blows the hair back and makes it flutter.
     * @param stream  how far back it's pushed (radians) - more running, most flying
     * @param flutter how much it flaps around
     */
    public void animateHair(float stream, float flutter, float age) {
        animateHair(stream, flutter, age, 0, 0F);
    }

    private int lastStyle;
    private float lastWeight = -1F;
    private static final org.joml.Quaternionf STYLED = new org.joml.Quaternionf();

    /**
     * @param style  0 normal, 1 Super Saiyan, 2 Super Saiyan 2, 3 Super Saiyan 3
     * @param weight 0..1 how far into that style the hair is (it rises when transforming)
     */
    public void animateHair(float stream, float flutter, float age, int style, float weight) {
        if (strands.isEmpty()) return;
        boolean styling = style > 0 && style < 4 && weight > 0.004F;
        if (!styling && stream < 0.004F && flutter < 0.004F) {
            if (hairMoved) {
                for (Strand s : strands) for (ModelPart p : s.parts) {
                    p.xRot = s.baseX; p.yRot = s.baseY; p.zRot = s.baseZ; p.yScale = 1F; p.z = s.pivotZ;
                }
                hairMoved = false;
            }
            return;
        }
        // same frame as last time (e.g. the armor/shadow passes): nothing changed
        if (hairMoved && age == lastAge && stream == lastStream && flutter == lastFlutter
                && style == lastStyle && weight == lastWeight) return;
        lastAge = age;
        lastStream = stream;
        lastFlutter = flutter;
        lastStyle = style;
        lastWeight = weight;
        hairMoved = true;
        if (styling) {
            // spiked hair is stiff: the wind barely moves it (long SSJ3 hair still sways)
            float stiff = style == 3 ? 0.4F : 0.75F;
            stream *= 1F - stiff * weight;
            flutter *= 1F - stiff * 0.6F * weight;
        }
        for (Strand s : strands) {
            org.joml.Quaternionf base = s.base;
            float scale = 1F, pz = s.pivotZ;
            if (styling && s.style != null) {
                if (style == 3) pz += s.back3 * weight;
                base = STYLED.set(s.base).slerp(s.style[style], weight);
                scale = 1F + (s.scale[style] - 1F) * weight;
            }
            float a = stream + Mth.sin(age * 0.55F + s.phase) * flutter;
            float b = Mth.sin(age * 0.43F + s.phase * 1.7F) * flutter * 0.6F;
            DELTA.rotationAxis(a, s.back).mul(TMP.rotationAxis(b, s.side));
            DELTA.mul(base, OUT);
            // quaternion -> Z-Y-X angles (the order the game uses), straight from the components
            float x = OUT.x, y = OUT.y, z = OUT.z, w = OUT.w;
            float sy = Mth.clamp(2F * (w * y - x * z), -1F, 1F);
            float yRot = (float) Math.asin(sy);
            float xRot = (float) Mth.atan2(2F * (w * x + y * z), 1F - 2F * (x * x + y * y));
            float zRot = (float) Mth.atan2(2F * (w * z + x * y), 1F - 2F * (y * y + z * z));
            for (ModelPart p : s.parts) {
                p.xRot = xRot;
                p.yRot = yRot;
                p.zRot = zRot;
                p.yScale = scale;
                p.z = pz;
            }
        }
    }


    /**
     * The coat tails swing with the legs (pushed ahead of a leg stepping forward, out of the way of one kicking back,
     * so the legs never poke through) and stream back and flap when he runs or flies.
     * @param stream how far the wind pushes them back (radians), flutter how much they flap
     */
    public void animateCoat(float rightLeg, float leftLeg, float stream, float flutter, float age, boolean sitting) {
        if (coatBack == null) return;
        float fr = Math.min(0F, rightLeg), fl = Math.min(0F, leftLeg);           // legs forward (negative = forward)
        float br = Math.max(0F, rightLeg), bl = Math.max(0F, leftLeg);           // legs back
        float f1 = Mth.sin(age * 0.6F) * flutter, f2 = Mth.sin(age * 0.6F + 1.7F) * flutter, f3 = Mth.sin(age * 0.47F + 0.9F) * flutter;
        // front flaps ride on the thigh in front of them, a touch further so the knee stays behind the cloth
        if (coatFrontR != null) { coatFrontR.xRot = fr * 1.08F - 0.04F + stream * 0.35F + f1 * 0.6F; coatFrontR.zRot = 0.03F; }
        if (coatFrontL != null) { coatFrontL.xRot = fl * 1.08F - 0.04F + stream * 0.35F + f2 * 0.6F; coatFrontL.zRot = -0.03F; }
        // sides follow their leg either way, flared out a little to clear the hips
        if (coatSideR != null) {
            coatSideR.xRot = rightLeg * 0.6F + stream * 0.6F + f2 * 0.5F;
            coatSideR.zRot = 0.1F + Math.abs(rightLeg) * 0.12F + stream * 0.15F;
        }
        if (coatSideL != null) {
            coatSideL.xRot = leftLeg * 0.6F + stream * 0.6F + f1 * 0.5F;
            coatSideL.zRot = -0.1F - Math.abs(leftLeg) * 0.12F - stream * 0.15F;
        }
        // the back tail hangs from the waist: kicked out by whichever leg swings back, blown back by the wind
        float back = Math.max(br, bl) * 1.05F + 0.04F + stream * 1.3F + f3 * 1.1F;
        if (sitting) back = Math.max(back, 0.35F);
        coatBack.xRot = back;
    }

    /** Bends this model's elbows and knees like the player model's (see LimbBend). */
    public void copyBends(net.minecraft.client.model.HumanoidModel<?> parent) {
        com.xenowarriors.client.LimbBend.copy(parent.rightArm, rightArm);
        com.xenowarriors.client.LimbBend.copy(parent.leftArm, leftArm);
        com.xenowarriors.client.LimbBend.copy(parent.rightLeg, rightLeg);
        com.xenowarriors.client.LimbBend.copy(parent.leftLeg, leftLeg);
    }

    private static ModelPart childOrNull(ModelPart part, String name) {
        try {
            return part.getChild(name);
        } catch (java.util.NoSuchElementException e) {
            return null;
        }
    }

    public boolean hasWings() {
        return rightWing != null && leftWing != null;
    }

    /** Show/hide the wings and flap them (big flaps in the air, a slow sway otherwise). */
    public void setupWings(boolean visible, boolean flapping, float ageInTicks) {
        setupWings(visible, flapping, ageInTicks, 0F);
    }

    /**
     * @param spread 0..1 - opens the wings out flat and wide (Kishi cast in the air), replacing the
     *               flap with a slow, heavy beat.
     */
    public void setupWings(boolean visible, boolean flapping, float ageInTicks, float spread) {
        if (!hasWings()) return;
        rightWing.visible = visible;
        leftWing.visible = visible;
        float flap = flapping ? (float) Math.sin(ageInTicks * 0.35F) * 0.28F : (float) Math.sin(ageInTicks * 0.08F) * 0.05F;
        float sweep = flapping ? (float) Math.cos(ageInTicks * 0.35F) * 0.12F : 0F;
        float slow = (float) Math.sin(ageInTicks * 0.15F) * 0.06F;
        flap += (slow - flap) * spread;
        sweep *= 1F - spread;
        rightWing.zRot = rightWingZ + flap;
        leftWing.zRot = leftWingZ - flap;
        // rest pose sweeps the wings back; spreading swings them out to the sides
        rightWing.yRot = (rightWingY + sweep) * (1F - spread);
        leftWing.yRot = (leftWingY - sweep) * (1F - spread);
    }

    /**
     * Full wing pose.
     * @param grow   0..1 how far out the wings are (they grow from his back when taken out)
     * @param flap   up/down beat (radians, + = up)
     * @param tuck   0..1 extra fold back along his back (resting on the ground)
     * @param sweep  forward/back swing of the beat
     * @param spread 0..1 opened out flat and wide
     */
    public void poseWings(boolean visible, float grow, float flap, float tuck, float sweep, float spread) {
        if (!hasWings()) return;
        boolean show = visible && grow > 0.01F;
        rightWing.visible = show;
        leftWing.visible = show;
        float g = Math.max(0.01F, grow);
        for (ModelPart w : new ModelPart[]{rightWing, leftWing}) {
            w.xScale = g;
            w.yScale = g;
            w.zScale = g;
        }
        rightWing.zRot = rightWingZ + flap;
        leftWing.zRot = leftWingZ - flap;
        rightWing.yRot = (rightWingY * (1F + tuck) + sweep) * (1F - spread);
        leftWing.yRot = (leftWingY * (1F + tuck) - sweep) * (1F - spread);
    }

    /** Loads the detail model for a character, or returns null if it has none. */
    public static DetailModel load(CharacterType type) {
        return loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/detail/" + type.getId() + ".json");
    }

    /** Loads any model file in the same format (e.g. a character's armor pieces); null if missing. */
    /** Loading for Epic Fight's baker: limbs left whole (it bends knees and elbows itself). */
    private static boolean wholeLimbs;

    public static DetailModel loadWhole(String path) {
        wholeLimbs = true;
        try {
            return loadPath(path);
        } finally {
            wholeLimbs = false;
        }
    }

    public static DetailModel loadPath(String path) {
        try (InputStream in = DetailModel.class.getResourceAsStream(path)) {
            if (in == null) return null;
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            int size = json.get("texture_size").getAsInt();
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition rootDef = mesh.getRoot();
            for (Map.Entry<String, JsonElement> bone : json.getAsJsonObject("bones").entrySet()) {
                JsonObject b = bone.getValue().getAsJsonObject();
                float[] p = floats(b.getAsJsonArray("pivot"));
                float split = wholeLimbs ? 0F : com.xenowarriors.client.LimbBend.splitY(bone.getKey());
                if (split > 0F) {
                    limb(rootDef, bone.getKey(), b, p, split);
                    continue;
                }
                PartDefinition part = rootDef.addOrReplaceChild(bone.getKey(), cubes(b), PartPose.offset(p[0], p[1], p[2]));
                children(part, b);
            }
            DetailModel model = new DetailModel(LayerDefinition.create(mesh, size, size).bakeRoot());
            if (json.has("face")) {
                JsonObject face = json.getAsJsonObject("face");
                int faceSize = face.get("texture_size").getAsInt();
                float[] uv = floats(face.getAsJsonArray("uv"));
                float[] f = floats(face.getAsJsonArray("from"));
                float[] sz = floats(face.getAsJsonArray("size"));
                MeshDefinition faceMesh = new MeshDefinition();
                faceMesh.getRoot().addOrReplaceChild("head",
                        CubeListBuilder.create().texOffs((int) uv[0], (int) uv[1]).addBox(f[0], f[1], f[2], sz[0], sz[1], sz[2]),
                        PartPose.ZERO);
                model.faceRoot = LayerDefinition.create(faceMesh, faceSize, faceSize).bakeRoot();
                model.faceHead = model.faceRoot.getChild("head");
            }
            return model;
        } catch (Exception e) {
            XenoWarriors.LOGGER.error("Could not load model {}", path, e);
            return null;
        }
    }

    /**
     * An arm or leg split at the elbow / knee: everything below goes in a child part that bends (see LimbBend).
     * A cube crossing the joint is cut in two at a whole pixel, with the lower half's texture shifted to match.
     */
    private static void limb(PartDefinition root, String name, JsonObject b, float[] p, float split) {
        CubeListBuilder upper = CubeListBuilder.create(), lower = CubeListBuilder.create();
        for (JsonElement el : b.getAsJsonArray("cubes")) {
            JsonObject c = el.getAsJsonObject();
            float[] uv = floats(c.getAsJsonArray("uv"));
            float[] f = floats(c.getAsJsonArray("from"));
            float[] sz = floats(c.getAsJsonArray("size"));
            int u = (int) uv[0], v = (int) uv[1];
            float top = f[1], bottom = f[1] + sz[1];
            // (thin trim sitting just over the joint - a hem, a rim - stays with the upper half)
            if ((bottom <= split + 0.6F && (top + bottom) / 2F < split) || (top < split + 0.45F && bottom <= split + 0.75F)) {
                upper.texOffs(u, v).addBox(f[0], f[1], f[2], sz[0], sz[1], sz[2]);
            } else if (top >= split - 0.6F) {
                lower.texOffs(u, v).addBox(f[0], f[1] - split, f[2], sz[0], sz[1], sz[2]);
            } else {
                int h1 = Math.max(1, Math.round(split - top));            // whole pixels, so the texture lines up
                if (h1 >= sz[1] - 0.5F) {
                    upper.texOffs(u, v).addBox(f[0], f[1], f[2], sz[0], sz[1], sz[2]);
                    continue;
                }
                upper.texOffs(u, v).addBox(f[0], f[1], f[2], sz[0], h1, sz[2]);
                float y0 = top + h1 - split;
                lower.texOffs(u, v + h1).addBox(f[0], y0, f[2], sz[0], sz[1] - h1, sz[2]);
                lower.texOffs(u, v).addBox(f[0], y0 + sz[1] - h1, f[2], sz[0], 0.02F, sz[2]);   // its real underside
                if (h1 >= 2 && sz[1] - h1 >= 2F) {
                    // joint piece across the cut, a hair thinner: fills the gap when the elbow / knee bends
                    lower.texOffs(u, v + h1 - 2).addBox(f[0], y0 - 2F, f[2], sz[0], 4F, sz[2],
                            new net.minecraft.client.model.geom.builders.CubeDeformation(-0.05F));
                }
            }
        }
        PartDefinition up = root.addOrReplaceChild(name, upper, PartPose.offset(p[0], p[1], p[2]));
        PartDefinition low = up.addOrReplaceChild(com.xenowarriors.client.LimbBend.LOWER, lower, PartPose.offset(0F, split, 0F));
        for (JsonElement el : b.getAsJsonArray("children")) {
            JsonObject c = el.getAsJsonObject();
            float[] cp = floats(c.getAsJsonArray("pivot"));
            float[] r = floats(c.getAsJsonArray("rot"));
            boolean below = cp[1] >= split;
            PartDefinition part = (below ? low : up).addOrReplaceChild(c.get("name").getAsString(), cubes(c),
                    PartPose.offsetAndRotation(cp[0], below ? cp[1] - split : cp[1], cp[2], r[0], r[1], r[2]));
            children(part, c);
        }
    }

    private static void children(PartDefinition parent, JsonObject node) {
        for (JsonElement el : node.getAsJsonArray("children")) {
            JsonObject c = el.getAsJsonObject();
            float[] p = floats(c.getAsJsonArray("pivot"));
            float[] r = floats(c.getAsJsonArray("rot"));
            PartDefinition part = parent.addOrReplaceChild(c.get("name").getAsString(), cubes(c),
                    PartPose.offsetAndRotation(p[0], p[1], p[2], r[0], r[1], r[2]));
            children(part, c);
        }
    }

    private static CubeListBuilder cubes(JsonObject node) {
        CubeListBuilder builder = CubeListBuilder.create();
        for (JsonElement el : node.getAsJsonArray("cubes")) {
            JsonObject c = el.getAsJsonObject();
            float[] uv = floats(c.getAsJsonArray("uv"));
            float[] f = floats(c.getAsJsonArray("from"));
            float[] s = floats(c.getAsJsonArray("size"));
            builder.texOffs((int) uv[0], (int) uv[1]).addBox(f[0], f[1], f[2], s[0], s[1], s[2]);
        }
        return builder;
    }

    private static float[] floats(JsonArray array) {
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsFloat();
        return out;
    }
}
