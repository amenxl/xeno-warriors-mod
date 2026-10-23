package com.xenowarriors.compat.epicfight;

import net.minecraft.world.entity.PathfinderMob;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.entitypatch.Faction;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;

/** A person, Epic Fight style: biped idle/walk/run, Epic Fight combat for what's in their hand, and flying (DMZ fighters). */
public class XWHumanPatch<T extends PathfinderMob> extends HumanoidMobPatch<T> {
    public XWHumanPatch(Faction faction) {
        super(faction);
    }

    @Override
    public void initAnimator(Animator animator) {
        super.initAnimator(animator);
        commonAggresiveMobAnimatorInit(animator);
        animator.addLivingAnimation(LivingMotions.FLY, Animations.BIPED_FLYING);
    }

    @Override
    public void updateMotion(boolean considerInaction) {
        commonAggressiveMobUpdateMotion(considerInaction);
        if (getOriginal().isNoGravity() && !getOriginal().onGround()) currentLivingMotion = LivingMotions.FLY;
    }
}
