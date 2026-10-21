package com.xenowarriors.client.mikiko;

import com.eliotlash.mclib.math.IValue;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.keyframe.AnimationPoint;
import software.bernie.geckolib.core.keyframe.BoneAnimation;
import software.bernie.geckolib.core.keyframe.Keyframe;
import software.bernie.geckolib.loading.object.BakedAnimations;

import java.util.List;

/**
 * Plays GeckoLib keyframe animations (assets/xenowarriors/animations/mikiko.animation.json, made in the
 * GeckoLib/Blockbench format) on the player model. GeckoLib loads and bakes the file with its easings;
 * this samples the bones (head, body, right_arm, left_arm, right_leg, left_leg) and poses the
 * vanilla model parts with them, blended in by a weight.
 */
public final class GeoAnims {
    public static final ResourceLocation FILE = XenoWarriors.id("animations/mikiko.animation.json");

    private GeoAnims() {}

    public static final ResourceLocation RHALIX = XenoWarriors.id("animations/rhalix.animation.json");

    public static Animation get(String name) {
        return get(FILE, name);
    }

    public static Animation get(ResourceLocation file, String name) {
        BakedAnimations baked = GeckoLibCache.getBakedAnimations().get(file);
        return baked == null ? null : baked.getAnimation(name);
    }

    /** Length in ticks (0 if the animation is missing). */
    public static float length(String name) {
        return length(FILE, name);
    }

    public static float length(ResourceLocation file, String name) {
        Animation a = get(file, name);
        return a == null ? 0F : (float) a.length();
    }

    /** A weight that fades in over the first and out over the last few ticks of a one-shot animation. */
    public static float envelope(float t, float length, float fade) {
        if (t < 0F || t > length) return 0F;
        return MikikoAnimations.smooth(t / fade) * MikikoAnimations.smooth((length - t) / fade);
    }

    /** Poses the model at time t (ticks). Returns false if the animation isn't loaded. */
    public static boolean apply(HumanoidModel<?> model, String name, float t, boolean loop, float weight) {
        return apply(model, FILE, name, t, loop, weight);
    }

    public static boolean apply(HumanoidModel<?> model, ResourceLocation file, String name, float t, boolean loop, float weight) {
        Animation anim = get(file, name);
        if (anim == null || weight <= 0F) return false;
        double len = anim.length();
        double time = loop && len > 0 ? t % len : Math.min(t, len);
        for (BoneAnimation bone : anim.boneAnimations()) {
            ModelPart part = part(model, bone.boneName());
            if (part == null) continue;
            var rot = bone.rotationKeyFrames();
            if (!rot.xKeyframes().isEmpty()) part.xRot = Mth.lerp(weight, part.xRot, (float) sample(rot.xKeyframes(), time));
            if (!rot.yKeyframes().isEmpty()) part.yRot = Mth.lerp(weight, part.yRot, (float) sample(rot.yKeyframes(), time));
            if (!rot.zKeyframes().isEmpty()) part.zRot = Mth.lerp(weight, part.zRot, (float) sample(rot.zKeyframes(), time));
            var pos = bone.positionKeyFrames();
            // Blockbench is y-up and mirrored on x; the model is y-down
            if (!pos.xKeyframes().isEmpty()) part.x -= (float) sample(pos.xKeyframes(), time) * weight;
            if (!pos.yKeyframes().isEmpty()) part.y -= (float) sample(pos.yKeyframes(), time) * weight;
            if (!pos.zKeyframes().isEmpty()) part.z += (float) sample(pos.zKeyframes(), time) * weight;
        }
        return true;
    }

    private static ModelPart part(HumanoidModel<?> m, String bone) {
        return switch (bone) {
            case "head" -> m.head;
            case "body" -> m.body;
            case "right_arm" -> m.rightArm;
            case "left_arm" -> m.leftArm;
            case "right_leg" -> m.rightLeg;
            case "left_leg" -> m.leftLeg;
            default -> null;
        };
    }

    private static double sample(List<Keyframe<IValue>> frames, double t) {
        double start = 0;
        Keyframe<IValue> last = null;
        for (Keyframe<IValue> k : frames) {
            last = k;
            double len = k.length();
            if (t <= start + len && len > 0) {
                AnimationPoint point = new AnimationPoint(k, t - start, len, k.startValue().get(), k.endValue().get());
                return k.easingType().apply(point);
            }
            start += len;
        }
        return last == null ? 0 : last.endValue().get();
    }
}
