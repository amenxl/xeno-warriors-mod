package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Getting hurt, their own way (instead of Epic Fight's hit animations):
 * <ul>
 *   <li><b>Mikiko</b> - a wild jolt: head snapped back, thrown away from the hit with his arms flung out, a foot
 *       skidding back, then straight back into it.</li>
 *   <li><b>Rhalix</b> - a tight flinch: forearms snap up crossed in front of his face, he leans back and turns his
 *       shoulder into it, a short shake of the head as he comes out of it.</li>
 * </ul>
 * Blended over whatever he's doing for the hurt flash (10 ticks): in fast, out slower. The side it twists to follows
 * where the hit came from.
 */
public final class HurtFlinch {
    private HurtFlinch() {}

    /** 0..1 how far into the flinch: fast in (first 20%), slow out. */
    private static float amount(Player p, float age) {
        if (p.hurtTime <= 0 || p.hurtDuration <= 0 || p.isDeadOrDying() || p.isSleeping()) return 0F;
        float pt = Mth.clamp(age - p.tickCount, 0F, 1F);
        float t = 1F - Mth.clamp((p.hurtTime - pt) / p.hurtDuration, 0F, 1F);    // 0 at the hit -> 1 when it ends
        float e = t < 0.2F ? t / 0.2F : 1F - (t - 0.2F) / 0.8F;
        return e * e * (3F - 2F * e);
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (!(p instanceof AbstractClientPlayer cp)) return;
        CharacterType type = ClientCharacterData.get(cp);
        if (type != CharacterType.MIKIKO && type != CharacterType.RHALIX) return;
        if (com.xenowarriors.client.party.PartyClient.knockedOut(p)) return;
        float a = amount(p, age);
        if (a <= 0.001F) return;
        // which side the hit came from (vanilla's hurt direction: + from the right): twist away from it
        float side = Mth.sin(p.getHurtDir() * Mth.DEG_TO_RAD) >= 0F ? 1F : -1F;
        float t = 1F - Mth.clamp((p.hurtTime - Mth.clamp(age - p.tickCount, 0F, 1F)) / Math.max(1, p.hurtDuration), 0F, 1F);
        if (type == CharacterType.MIKIKO) {
            mix(m.head, a, -0.55F, 0.25F * side, 0.2F * side);
            mix(m.body, a, -0.18F, 0.35F * side, 0.06F * side);
            mix(m.rightArm, a, -0.6F, 0.2F, 0.95F);
            mix(m.leftArm, a, -0.45F, -0.2F, -0.85F);
            mix(m.rightLeg, a, side > 0 ? 0.45F : -0.15F, 0F, 0.12F);
            mix(m.leftLeg, a, side > 0 ? -0.15F : 0.45F, 0F, -0.12F);
        } else {
            float shake = t > 0.45F ? Mth.sin(age * 2.6F) * 0.18F * (1F - t) : 0F;   // shakes it off
            mix(m.head, a, 0.3F, shake + 0.15F * side, 0F);
            mix(m.body, a, -0.12F, 0.22F * side, 0F);
            mix(m.rightArm, a, -1.75F, -0.55F, 0.15F);                       // forearms crossed in front of his face
            mix(m.leftArm, a, -1.65F, 0.6F, -0.15F);
            mix(m.rightLeg, a, 0.2F, 0F, 0.06F);
            mix(m.leftLeg, a, -0.1F, 0F, -0.06F);
            LimbBend.extraElbows(p, -0.9F * a, -0.9F * a);                    // (bent: fists by his face)
        }
        // the shoulders go round with the twisted torso
        m.rightArm.z = Mth.sin(m.body.yRot) * 5F; m.rightArm.x = -Mth.cos(m.body.yRot) * 5F;
        m.leftArm.z = -Mth.sin(m.body.yRot) * 5F; m.leftArm.x = Mth.cos(m.body.yRot) * 5F;
        m.hat.copyFrom(m.head);
    }

    private static void mix(net.minecraft.client.model.geom.ModelPart part, float a, float x, float y, float z) {
        part.xRot = Mth.lerp(a, part.xRot, x);
        part.yRot = Mth.lerp(a, part.yRot, y);
        part.zRot = Mth.lerp(a, part.zRot, z);
    }
}
