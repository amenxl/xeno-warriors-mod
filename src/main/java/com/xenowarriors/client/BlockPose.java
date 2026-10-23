package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Blocking with DragonMineZ's guard: Rhalix crosses his forearms in front of his face, Mikiko throws his forearms up
 * (or holds his sword up across himself), chin tucked, feet braced - jolting back when a hit lands on the guard.
 */
public final class BlockPose {
    private static final Map<Integer, float[]> W = new HashMap<>();   // {weight, age last updated}

    private BlockPose() {}

    private static float weight(Player p, float age) {
        boolean on = com.xenowarriors.client.compat.DmzCompat.blocking(p);
        float[] w = W.computeIfAbsent(p.getId(), k -> new float[]{0F, age});
        float dt = Mth.clamp(age - w[1], 0F, 3F);
        w[1] = age;
        w[0] = Mth.clamp(w[0] + (on ? 0.35F : -0.25F) * dt, 0F, 1F);
        return w[0];
    }

    private static void set(ModelPart part, float x, float y, float z, float w) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (!(p instanceof AbstractClientPlayer cp)) return;
        CharacterType type = ClientCharacterData.get(cp);
        if (type == CharacterType.NONE) return;
        float w = weight(p, age);
        if (w <= 0F) return;
        float jolt = p.hurtTime > 0 ? p.hurtTime / 10F : 0F;              // a hit landing on the guard
        float shake = Mth.sin(age * 2.5F) * 0.04F * jolt;
        float partial = Mth.clamp(age - p.tickCount, 0F, 1F);
        // walking with the guard up: short careful steps, the guard bobbing a little with each one
        float walk = Mth.clamp(p.walkAnimation.speed(partial) * 2.2F, 0F, 1F);
        float step = p.walkAnimation.position(partial) * 0.6662F;
        float bob = Mth.cos(step * 2F) * 0.05F * walk;
        boolean sword = p.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem;
        if (type == CharacterType.RHALIX && !sword) {
            // forearms crossed in an X in front of his face
            set(m.rightArm, -1.75F + shake + bob, -0.62F, 0.05F, w);
            set(m.leftArm, -1.6F - shake + bob, 0.62F, -0.05F, w);
        } else if (sword) {
            // sword held up across himself, the other hand braced against the flat
            set(m.rightArm, -1.35F + shake + bob, -0.55F, 0F, w);
            set(m.leftArm, -1.45F - shake + bob, 0.75F, 0F, w);
        } else {
            // Mikiko: both forearms up beside his head, elbows in
            set(m.rightArm, -2.05F + shake + bob, -0.35F, 0.1F, w);
            set(m.leftArm, -2.05F - shake + bob, 0.35F, -0.1F, w);
        }
        // legs: braced when standing; when walking, the normal stride shortened (feet staying under him)
        float stride = Mth.cos(step) * 0.7F * walk;
        set(m.rightLeg, Mth.lerp(walk, -0.25F, stride - 0.1F), 0.08F, 0.06F, w);
        set(m.leftLeg, Mth.lerp(walk, 0.2F, -stride - 0.1F), -0.08F, -0.06F, w);

        // striking from the guard (attacking while blocking): a quick snap out, then straight back behind the guard
        float swing = m.attackTime;
        if (swing > 0F) {
            float out = Mth.sin(swing * Mth.PI);                          // 0 -> 1 -> 0 over the swing
            if (type == CharacterType.RHALIX && !sword) {
                // a short front kick from behind the crossed arms
                set(m.rightLeg, -1.45F, 0F, 0.05F, out * w);
                m.body.xRot = Mth.lerp(out * w, m.body.xRot, -0.12F);
            } else if (sword) {
                // a tight thrust straight out of the guard
                set(m.rightArm, -1.55F, 0F, 0F, out * w);
            } else {
                // Mikiko: a jab out of the high guard, the other arm staying up
                set(m.rightArm, -1.6F, 0.05F, 0F, out * w);
                m.body.yRot = Mth.lerp(out * w, m.body.yRot, -0.25F);
            }
        }
        m.body.xRot = Mth.lerp(w, m.body.xRot, 0.18F - 0.12F * jolt - 0.08F * walk);
        m.head.xRot = Mth.lerp(w, m.head.xRot, 0.25F);                  // chin tucked, eyes over the guard
        m.hat.copyFrom(m.head);
    }
}
