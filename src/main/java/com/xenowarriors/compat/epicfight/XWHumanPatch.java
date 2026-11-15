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

    /** Bare hands: Epic Fight's own fist combos (it fell back to the zombie's arm swipes for an empty hand). */
    @Override
    protected yesman.epicfight.world.entity.ai.goal.CombatBehaviors.Builder<HumanoidMobPatch<?>> getHoldingItemWeaponMotionBuilder() {
        if (getOriginal().getMainHandItem().isEmpty()) return yesman.epicfight.gameasset.MobCombatBehaviors.HUMANOID_FIST;
        return super.getHoldingItemWeaponMotionBuilder();
    }

    /** Bare hands count as armed (fists): Epic Fight's fighting AI and fist combos, not the plain mob swipe. */
    @Override
    public boolean isArmed() {
        return getOriginal().getMainHandItem().isEmpty() || super.isArmed();
    }

    @Override
    public void updateMotion(boolean considerInaction) {
        commonAggressiveMobUpdateMotion(considerInaction);
        if (getOriginal().isNoGravity() && !getOriginal().onGround()) currentLivingMotion = LivingMotions.FLY;
    }
}
