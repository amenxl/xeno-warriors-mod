package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Ultima's body language by output: at 25% his hands stay in his pockets and he fights with lazy kicks, like the
 * fight bores him; at 70% his blows are heavy two-handed slams and haymakers.
 */
public final class UltimaAnim {
    private UltimaAnim() {}

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (ClientCharacterData.get((net.minecraft.client.player.AbstractClientPlayer) p) != CharacterType.ULTIMA) return;
        if (UltimaClient.overload(p.getId())) return;
        int tier = UltimaClient.tier(p.getId());
        float atk = m.attackTime;
        if (tier == 0) {
            // hands in his pockets, shoulders a bit slack, head tilted - not even trying
            m.rightArm.xRot = 0.18F + (p.isCrouching() ? 0.3F : 0F);
            m.leftArm.xRot = 0.18F + (p.isCrouching() ? 0.3F : 0F);
            m.rightArm.yRot = 0.25F; m.leftArm.yRot = -0.25F;
            m.rightArm.zRot = 0.12F; m.leftArm.zRot = -0.12F;
            m.head.zRot = Mth.sin(age * 0.03F) * 0.06F + 0.08F;
            if (atk > 0F) {                                // a lazy kick instead of a punch
                float s = Mth.sin(atk * Mth.PI);
                m.rightLeg.xRot = -1.45F * s;
                m.rightLeg.zRot = 0.1F * s;
                m.leftLeg.xRot = 0.25F * s;
                m.body.yRot = -0.15F * s;
                m.rightArm.xRot += 0.1F * s; m.leftArm.xRot += 0.1F * s;
            }
        } else if (tier == 2 && atk > 0F) {
            // heavy blows: both fists come up and smash down, the body throwing its weight in
            boolean slam = (p.swingTime + p.getId()) % 2 == 0 || p.fallDistance > 0.5F;
            float up = atk < 0.45F ? atk / 0.45F : 1F - (atk - 0.45F) / 0.55F;
            float down = atk > 0.45F ? Mth.sin((atk - 0.45F) / 0.55F * Mth.PI) : 0F;
            if (slam) {
                float x = -2.9F * up + 0.8F * down;
                m.rightArm.xRot = x; m.leftArm.xRot = x;
                m.rightArm.zRot = 0.2F * up; m.leftArm.zRot = -0.2F * up;
                m.rightArm.yRot = -0.3F * up; m.leftArm.yRot = 0.3F * up;
                m.body.xRot = atk > 0.45F ? 0.5F * down : -0.2F * up;
                m.rightLeg.xRot = -0.4F * down; m.leftLeg.xRot = 0.35F * down;
            } else {                                       // haymaker: wound way back, then swung through
                float s = Mth.sin(atk * Mth.PI);
                m.rightArm.xRot = -1.4F - 0.3F * s;
                m.rightArm.yRot = atk < 0.4F ? 1.0F * (atk / 0.4F) : 1.0F - 2.2F * ((atk - 0.4F) / 0.6F);
                m.body.yRot = atk < 0.4F ? 0.5F * (atk / 0.4F) : 0.5F - 1.1F * ((atk - 0.4F) / 0.6F);
                m.leftArm.xRot = -0.6F; m.leftArm.zRot = -0.3F;
                m.rightLeg.xRot = -0.3F * s; m.leftLeg.xRot = 0.3F * s;
            }
        }
    }

    /** What his energy is called: Data (Gov data powers him), not Karma. */
    public static String resource() {
        return ClientCharacterData.getLocal() == CharacterType.ULTIMA ? "Data" : "Karma";
    }
}
