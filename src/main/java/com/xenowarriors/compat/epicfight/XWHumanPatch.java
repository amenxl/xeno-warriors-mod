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

    private static yesman.epicfight.world.entity.ai.goal.CombatBehaviors.Builder<HumanoidMobPatch<?>> fists;

    /**
     * Bare hands: Epic Fight's player fist combo (jab, jab, hook - and the dash punch from a step away). Epic Fight's own
     * "humanoid fist" moveset for mobs is the zombie's arm swipes, which looked wrong on people.
     */
    static yesman.epicfight.world.entity.ai.goal.CombatBehaviors.Builder<HumanoidMobPatch<?>> fists() {
        if (fists == null) {
            fists = yesman.epicfight.world.entity.ai.goal.CombatBehaviors.<HumanoidMobPatch<?>>builder()
                    .newBehaviorSeries(yesman.epicfight.world.entity.ai.goal.CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                            .weight(100F).canBeInterrupted(true).looping(true)
                            .nextBehavior(yesman.epicfight.world.entity.ai.goal.CombatBehaviors.Behavior.<HumanoidMobPatch<?>>builder()
                                    .animationBehavior(Animations.FIST_AUTO1).withinEyeHeight().withinDistance(0D, 2.0D))
                            .nextBehavior(yesman.epicfight.world.entity.ai.goal.CombatBehaviors.Behavior.<HumanoidMobPatch<?>>builder()
                                    .animationBehavior(Animations.FIST_AUTO2).withinEyeHeight().withinDistance(0D, 2.0D))
                            .nextBehavior(yesman.epicfight.world.entity.ai.goal.CombatBehaviors.Behavior.<HumanoidMobPatch<?>>builder()
                                    .animationBehavior(Animations.FIST_AUTO3).withinEyeHeight().withinDistance(0D, 2.2D)))
                    .newBehaviorSeries(yesman.epicfight.world.entity.ai.goal.CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                            .weight(25F).canBeInterrupted(false).looping(false).cooldown(80)
                            .nextBehavior(yesman.epicfight.world.entity.ai.goal.CombatBehaviors.Behavior.<HumanoidMobPatch<?>>builder()
                                    .animationBehavior(Animations.FIST_DASH).withinEyeHeight().withinDistance(2.2D, 4.5D)));
        }
        return fists;
    }

    @Override
    protected yesman.epicfight.world.entity.ai.goal.CombatBehaviors.Builder<HumanoidMobPatch<?>> getHoldingItemWeaponMotionBuilder() {
        if (getOriginal().getMainHandItem().isEmpty()) return fists();
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
