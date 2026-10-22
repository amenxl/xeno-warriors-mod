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
        boolean sword = p.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem;
        if (type == CharacterType.RHALIX && !sword) {
            // forearms crossed in an X in front of his face
            set(m.rightArm, -1.75F + shake, -0.62F, 0.05F, w);
            set(m.leftArm, -1.6F - shake, 0.62F, -0.05F, w);
        } else if (sword) {
            // sword held up across himself, the other hand braced against the flat
            set(m.rightArm, -1.35F + shake, -0.55F, 0F, w);
            set(m.leftArm, -1.45F - shake, 0.75F, 0F, w);
        } else {
            // Mikiko: both forearms up beside his head, elbows in
            set(m.rightArm, -2.05F + shake, -0.35F, 0.1F, w);
            set(m.leftArm, -2.05F - shake, 0.35F, -0.1F, w);
        }
        set(m.rightLeg, -0.25F, 0.08F, 0.06F, w);
        set(m.leftLeg, 0.2F, -0.08F, -0.06F, w);
        m.body.xRot = Mth.lerp(w, m.body.xRot, 0.18F - 0.12F * jolt);
        m.head.xRot = Mth.lerp(w, m.head.xRot, 0.25F);                  // chin tucked, eyes over the guard
        m.hat.copyFrom(m.head);
    }
}
