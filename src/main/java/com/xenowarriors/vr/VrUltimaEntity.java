package com.xenowarriors.vr;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * VR Ultima: a training copy made by Yuri's computer - it can only run him at 20% output. Fast dashes, heavy hands, and
 * now and then "Stop right there!" (freezes you for a moment). "VR Ultima is down. You win!"
 */
public class VrUltimaEntity extends Monster {
    private static final DustParticleOptions DATA = new DustParticleOptions(new Vector3f(0.25F, 1F, 0.6F), 0.9F);
    private int dashCd = 40, stopCd = 160;

    public VrUltimaEntity(EntityType<? extends Monster> type, Level level) { super(type, level); setPersistenceRequired(); }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 60.0).add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33).add(Attributes.ARMOR, 6.0).add(Attributes.FOLLOW_RANGE, 40.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16F));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        ServerLevel l = (ServerLevel) level();
        if (tickCount % 3 == 0) l.sendParticles(DATA, getX(), getY() + 1, getZ(), 1, .3, .6, .3, 0);
        LivingEntity t = getTarget();
        if (t == null) return;
        if (dashCd > 0) dashCd--;
        if (stopCd > 0) stopCd--;
        double d = distanceTo(t);
        if (dashCd == 0 && d > 3.5 && d < 16) {                                     // dash in
            Vec3 v = t.position().subtract(position()).normalize();
            setDeltaMovement(v.x * 1.4, 0.2, v.z * 1.4); hurtMarked = true; dashCd = 60 + random.nextInt(40);
            l.playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 0.6F, 1.6F);
        }
        if (stopCd == 0 && d < 10 && t instanceof ServerPlayer sp) {                // "Stop right there!"
            stopCd = 220 + random.nextInt(120);
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 6, false, true));
            sp.addEffect(new MobEffectInstance(MobEffects.JUMP, 30, 128, false, false));
            sp.displayClientMessage(Component.literal("VR Ultima: Stop right there!").withStyle(ChatFormatting.GREEN), true);
        }
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
}
