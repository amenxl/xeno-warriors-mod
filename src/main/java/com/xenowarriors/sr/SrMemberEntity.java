package com.xenowarriors.sr;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A Sky Rebellion member: a Skylander in the black SR hoodie (the white eye on the chest), sworn to "Father" - and a
 * demon vessel. Hostile to anyone not wearing the SR hoodie. At half health the demon inside wakes up: faster,
 * stronger, red-black aura.
 */
public class SrMemberEntity extends Monster {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(SrMemberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DEMON = SynchedEntityData.defineId(SrMemberEntity.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.55F, 0.05F, 0.1F), 1.2F);
    private static final String[] LINES = {"For Father.", "Nice hoodie. Welcome, brother.", "The Royal capital will be ours.", "Father sees everything.", "Stay out of the rebellion's way."};
    private BlockPos post;
    private int leapCd;

    public SrMemberEntity(EntityType<? extends Monster> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 50.0).add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ARMOR, 4.0).add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(VARIANT, 0); entityData.define(DEMON, false); }

    public String look() { return "sr_" + (entityData.get(VARIANT) % 2 + 1); }
    public boolean demon() { return entityData.get(DEMON); }

    public void setup(int variant, BlockPos at) {
        entityData.set(VARIANT, variant);
        post = at;
        restrictTo(at, 10);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(variant % 2 == 0 ? Items.IRON_SWORD : Items.STONE_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0.05F);
        setPersistenceRequired();
    }

    /** Wearing the SR hoodie: they take you for one of their own. */
    public static boolean disguised(Player p) {
        return p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof com.xenowarriors.item.SrHoodieItem
                || p.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof com.xenowarriors.item.SrHoodieItem;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.55));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(SrMemberEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> !disguised((Player) p)));
    }

    @Override
    protected InteractionResult mobInteract(Player p, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND && disguised(p) && getTarget() != p) {
            p.sendSystemMessage(Component.literal("SR Member: ").withStyle(ChatFormatting.DARK_RED)
                    .append(Component.literal(LINES[p.getRandom().nextInt(LINES.length)]).withStyle(ChatFormatting.GRAY)));
            getLookControl().setLookAt(p);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean r = super.hurt(src, amount);
        if (r && !level().isClientSide && !demon() && getHealth() < getMaxHealth() * 0.5F) {        // the demon wakes up
            entityData.set(DEMON, true);
            addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, -1, 1, false, false));
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 1, false, false));
            heal(8F);
            ServerLevel l = (ServerLevel) level();
            l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 30, .4, .8, .4, .08);
            l.playSound(null, blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.7F, 1.4F);
            setCustomName(Component.literal("SR Member (Demon Vessel)").withStyle(ChatFormatting.DARK_RED));
        }
        return r;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (leapCd > 0) leapCd--;
        var t = getTarget();
        if (t != null && onGround() && leapCd == 0 && distanceTo(t) > 4 && distanceTo(t) < 10) {          // wing-assisted leap
            Vec3 d = t.position().subtract(position());
            setDeltaMovement(d.x * 0.17, 0.55, d.z * 0.17); hurtMarked = true; leapCd = 70;
            ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 6, .3, .1, .3, .02);
        }
        if (demon() && tickCount % 4 == 0) ((ServerLevel) level()).sendParticles(DARK, getX(), getY() + 1, getZ(), 2, .3, .6, .3, 0);
    }

    @Override public boolean removeWhenFarAway(double d) { return post == null && super.removeWhenFarAway(d); }

    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t); t.putInt("Variant", entityData.get(VARIANT)); t.putBoolean("Demon", demon());
        if (post != null) t.putLong("Post", post.asLong());
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t); entityData.set(VARIANT, t.getInt("Variant")); entityData.set(DEMON, t.getBoolean("Demon"));
        if (t.contains("Post")) { post = BlockPos.of(t.getLong("Post")); restrictTo(post, 10); }
    }
}
