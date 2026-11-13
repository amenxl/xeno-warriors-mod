package com.xenowarriors.client.efbake;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Records our own (vanilla-model) animations frame by frame, so they can be rebuilt as Epic Fight animations
 * (tools/xw/ef/bake.py turns a recording into Epic Fight's joint json). Only while a recording is running (the
 * "efbake" test): each drawn frame of the recorded entity stores its model's part rotations and offsets and the
 * elbow / knee bends, with the time in ticks.
 */
public final class PoseRecorder {
    private PoseRecorder() {}

    private static int entityId = -1;
    private static float start;
    private static float lastT = -1F;
    private static final List<float[]> FRAMES = new ArrayList<>();

    public static void start(LivingEntity e) {
        entityId = e.getId();
        FRAMES.clear();
        lastT = -1F;
        start = e.tickCount + Minecraft.getInstance().getFrameTime();
    }

    public static boolean recording() { return entityId >= 0; }

    /** Stops and returns the frames: {t, head xyz, body xyz, rArm xyz, lArm xyz, rLeg xyz, lLeg xyz, bends r/l elbow r/l knee, body y offset, body motion lift/forward/spin/lean/roll/barrel}. */
    public static List<float[]> stop() {
        entityId = -1;
        return new ArrayList<>(FRAMES);
    }

    /** From LimbBend.apply, once the pose is final. */
    public static void frame(PlayerModel<?> m, LivingEntity e, float er, float el, float kr, float kl) {
        if (e.getId() != entityId) return;
        float t = e.tickCount + Minecraft.getInstance().getFrameTime() - start;
        if (t <= lastT + 0.2F) return;                      // (one sample per ~0.2 tick; the model is set up more than once a frame)
        lastT = t;
        float[] f = new float[1 + 18 + 4 + 1 + 6];
        f[0] = t;
        int i = 1;
        for (ModelPart p : new ModelPart[]{m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) {
            f[i++] = p.xRot; f[i++] = p.yRot; f[i++] = p.zRot;
        }
        f[i++] = er; f[i++] = el; f[i++] = kr; f[i++] = kl;
        f[i++] = m.body.y;
        float[] b = e instanceof net.minecraft.client.player.AbstractClientPlayer cp
                ? com.xenowarriors.client.mikiko.MikikoAnimations.bodyMotionForBake(cp, Minecraft.getInstance().getFrameTime()) : null;
        if (b != null) for (int k = 0; k < 6 && k < b.length; k++) f[i + k] = b[k];
        FRAMES.add(f);
    }
}
