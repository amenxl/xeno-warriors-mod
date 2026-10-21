package com.xenowarriors.corrupted;

import com.xenowarriors.party.Parties;
import com.xenowarriors.stats.Stats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A corrupted soul possessing a person. They move and fight like a player - walking upright, sprinting at you,
 * hopping in for critical hits - with three attacks (right hook, left hook, a two-handed overhead slam) and a
 * flinch when hit. Their strength scales with the player they hunt, and with that player's party.
 * If the body was a real villager, the soul is driven out at sunrise; a wandering body burns away.
 */
public class CorruptedVillagerEntity extends Zombie {
    public static final int RIGHT = 0, LEFT = 1, SLAM = 2;
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(CorruptedVillagerEntity.class, EntityDataSerializers.INT);
    private static final UUID HP_ID = UUID.fromString("2f6e8d14-1a3b-4c5d-8e9f-0a1b2c3d4e51"),
            DMG_ID = UUID.fromString("2f6e8d14-1a3b-4c5d-8e9f-0a1b2c3d4e52"),
            ARMOR_ID = UUID.fromString("2f6e8d14-1a3b-4c5d-8e9f-0a1b2c3d4e53");
    private boolean wasVillager;
    private int scaledFor = -1;

    public CorruptedVillagerEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.5)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ATTACK, RIGHT);
    }

    /** Which attack the last swing was (for the animation). */
    public int attackStyle() {
        return entityData.get(ATTACK);
    }

    public void markWasVillager() {
        wasVillager = true;
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    public void setBaby(boolean baby) {}

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    protected boolean isSunSensitive() {
        return !wasVillager;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        int style = random.nextInt(5) == 0 ? SLAM : random.nextBoolean() ? RIGHT : LEFT;
        entityData.set(ATTACK, style);
        swing(style == LEFT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        boolean hit = super.doHurtTarget(target);
        if (hit && style == SLAM && target instanceof LivingEntity living) {
            Vec3 push = living.position().subtract(position()).normalize();
            living.push(push.x * 0.6, 0.35, push.z * 0.6);
            living.hurtMarked = true;
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.WITCH, living.getX(), living.getY() + 0.2, living.getZ(), 12, 0.5, 0.1, 0.5, 0.05);
            }
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (random.nextInt(6) == 0) {
                level().addParticle(ParticleTypes.WITCH, getRandomX(0.5), getY() + 1.6 + random.nextDouble() * 0.5, getRandomZ(0.5), 0, 0.02, 0);
            }
            return;
        }
        LivingEntity target = getTarget();
        // move like a player: sprint when chasing, hop in for crits when close
        setSprinting(target != null && distanceToSqr(target) > 9);
        if (target != null && onGround() && distanceToSqr(target) < 12 && random.nextInt(30) == 0) jumpFromGround();
        if (tickCount % 100 == 0 || (target instanceof ServerPlayer && scaledFor == -1)) rescale();

        if (wasVillager && tickCount % 20 == 0 && level().isDay() && level().canSeeSky(blockPosition())) {
            Villager freed = convertTo(EntityType.VILLAGER, false);
            if (freed != null && level() instanceof ServerLevel server) {
                server.sendParticles(CorruptedFx.BLACK, freed.getX(), freed.getY() + 1.2, freed.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
                server.playSound(null, freed.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, getSoundSource(), 1F, 1F);
            }
        }
    }

    /**
     * Match the hunt: stronger against high-level warriors, and stronger again for each party member with them.
     * Character Level 150 = about 2.5x health and 1.9x damage; each extra party member nearby adds 35% health, 15% damage.
     */
    private void rescale() {
        ServerPlayer p = getTarget() instanceof ServerPlayer sp ? sp
                : level().getNearestPlayer(this, 48) instanceof ServerPlayer sp2 ? sp2 : null;
        if (p == null) return;
        int level = Stats.characterLevel(p);
        long party = Parties.members(p).stream().filter(m -> m.level() == p.level() && m.distanceToSqr(p) < 64 * 64).count();
        int key = level * 10 + (int) party;
        if (key == scaledFor) return;
        scaledFor = key;
        float ratio = getHealth() / getMaxHealth();
        // full scaling to Character Level 150, then gentler up to 999 (about 4x health, 2.8x damage)
        double lvA = Math.min(level, 150) / 150.0, lvB = Math.max(0, level - 150) / 849.0;
        double hp = lvA * 1.5 + lvB * 1.5 + (party - 1) * 0.35;
        double dmg = lvA * 0.9 + lvB * 0.9 + (party - 1) * 0.15;
        set(Attributes.MAX_HEALTH, HP_ID, hp, AttributeModifier.Operation.MULTIPLY_BASE);
        set(Attributes.ATTACK_DAMAGE, DMG_ID, dmg, AttributeModifier.Operation.MULTIPLY_BASE);
        set(Attributes.ARMOR, ARMOR_ID, Math.min(20.0, level / 15.0), AttributeModifier.Operation.ADDITION);
        setHealth(Math.max(1F, getMaxHealth() * ratio));
    }

    private void set(net.minecraft.world.entity.ai.attributes.Attribute attr, UUID id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount > 0) inst.addPermanentModifier(new AttributeModifier(id, "Possessed scaling", amount, op));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_VILLAGER_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.ARMOR_EQUIP_GENERIC;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(state.getSoundType().getStepSound(), 0.15F, 1F);   // footsteps like a person, not a shamble
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("WasVillager", wasVillager);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        wasVillager = tag.getBoolean("WasVillager");
    }
}
