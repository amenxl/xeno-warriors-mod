package com.xenowarriors.npc;

import com.xenowarriors.corrupted.CorruptedFx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/** Someone a corrupted soul has taken for good: pitch black, red-eyed, and out for anyone living. */
public class DarkPossessedEntity extends Monster {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(DarkPossessedEntity.class, EntityDataSerializers.INT);

    public DarkPossessedEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 15;
        setCanPickUpLoot(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ARMOR, 4);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANT, 0);
    }

    public int variant() { return entityData.get(VARIANT); }
    public void setVariant(int v) { entityData.set(VARIANT, v); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, HumanNpcEntity.class, 10, true, false, e -> !((HumanNpcEntity) e).carriesSoul()));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data, CompoundTag tag) {
        setVariant(level.getRandom().nextInt(NpcLooks.DARK.length));
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && random.nextInt(2) == 0) {
            level().addParticle(CorruptedFx.BLACK, getRandomX(0.5), getRandomY(), getRandomZ(0.5), 0, 0.03, 0);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getInt("Variant"));
    }
}
