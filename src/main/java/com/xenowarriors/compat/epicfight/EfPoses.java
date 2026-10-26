package com.xenowarriors.compat.epicfight;

import com.xenowarriors.anim.Poses;
import com.xenowarriors.npc.HumanReactions;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** For NPCs Epic Fight animates (our own poses don't reach its skeleton): the nearest Epic Fight animation. */
public final class EfPoses {
    private EfPoses() {}

    private static void run(LivingEntity e, AssetAccessor<? extends StaticAnimation> anim) {
        if (anim == null) return;
        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(e, LivingEntityPatch.class);
        if (patch != null) patch.playAnimationSynchronized(anim, 0F);
    }

    public static void play(LivingEntity e, int pose) {
        run(e, switch (pose) {
            case Poses.EL_THROW, Poses.EL_THRUST, Poses.EL_RAISE, Poses.KA_CAST -> Animations.BIPED_MOB_THROW;
            case Poses.EL_STOMP -> Animations.BIPED_LANDING;
            case Poses.KA_BURNOUT, Poses.PLANT -> Animations.BIPED_KNEEL;
            case Poses.CLASH_LOSE -> Animations.BIPED_KNOCKDOWN;
            case Poses.HOLD_JOLT -> Animations.BIPED_HIT_SHORT;
            case Poses.CHOP, Poses.MINE, Poses.SKY_MINE, Poses.PLACE, Poses.SHAPE -> Animations.BIPED_DIG_MAINHAND;
            case Poses.CLASH, Poses.BOUND -> Animations.BIPED_HIT_LONG;
            default -> null;
        });
    }

    /** Townsfolk reactions under Epic Fight. */
    public static void reaction(LivingEntity e, int type) {
        run(e, switch (type) {
            case HumanReactions.COWER -> Animations.BIPED_KNEEL;
            case HumanReactions.AWE, HumanReactions.NERVOUS -> Animations.BIPED_HIT_LONG;
            case HumanReactions.CHEER, HumanReactions.DANCE -> Animations.BIPED_JUMP;
            case HumanReactions.WAVE -> Animations.BIPED_MOB_THROW;
            default -> null;
        });
    }
}
