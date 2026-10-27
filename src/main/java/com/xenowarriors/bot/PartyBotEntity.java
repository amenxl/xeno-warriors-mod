package com.xenowarriors.bot;

import com.xenowarriors.character.CharacterType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A bot teammate: another playable character (Mikiko, Rhalix, Ultima) that fights beside you at the level you've
 * raised that character to. Modes: NEUTRAL (only what you hit or what hits you/it), AGGRESSIVE (any hostile mob near
 * you), MINING (helps find and dig ore near you). Has its own inventory, can carry you if it flies, and goes down
 * instead of dying - revive it (sneak + right-click) or it leaves the party for a while.
 */
public class PartyBotEntity extends PathfinderMob {
    public static final int NEUTRAL = 0, AGGRESSIVE = 1, MINING = 2;
    public static final String[] MODES = {"Neutral", "Aggressive", "Mining"};
    private static final EntityDataAccessor<String> CHAR = SynchedEntityData.defineId(PartyBotEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(PartyBotEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(PartyBotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(PartyBotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DOWNED = SynchedEntityData.defineId(PartyBotEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SPAR = SynchedEntityData.defineId(PartyBotEntity.class, EntityDataSerializers.BOOLEAN);
    public final SimpleContainer inventory = new SimpleContainer(27);
    public float karma = 100F;
    int downedTicks;
    long[] skillReady = new long[5];
    LivingEntity relayTarget;
    long relayUntil;
    BlockPos mineTarget;
    int fullNotice;

    public PartyBotEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.FLYING_SPEED, 0.6);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(CHAR, "rhalix");
        entityData.define(OWNER, Optional.empty());
        entityData.define(MODE, NEUTRAL);
        entityData.define(LEVEL, 1);
        entityData.define(DOWNED, false);
        entityData.define(SPAR, false);
    }

    // ---------------------------------------------------------------- state

    @Override
    public void onAddedToWorld() { super.onAddedToWorld(); PartyBots.track(this, true); }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        PartyBots.track(this, false);
        if (!level().isClientSide && getRemovalReason() == RemovalReason.UNLOADED_TO_CHUNK && ownerId() != null) PartyBots.LEFT_BEHIND.add(Map.entry(ownerId(), record()));   // owner teleported far away
    }

    public CharacterType character() { return CharacterType.byId(entityData.get(CHAR)); }
    public void setCharacter(CharacterType t) { entityData.set(CHAR, t.getId()); }
    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public void setOwner(UUID id) { PartyBots.track(this, false); entityData.set(OWNER, Optional.ofNullable(id)); if (isAddedToWorld()) PartyBots.track(this, true); }
    public ServerPlayer owner() { return level() instanceof ServerLevel s && ownerId() != null ? (ServerPlayer) s.getPlayerByUUID(ownerId()) : null; }
    public int mode() { return entityData.get(MODE); }
    public void setMode(int m) { entityData.set(MODE, Math.floorMod(m, 3)); mineTarget = null; setTarget(null); }
    public int botLevel() { return entityData.get(LEVEL); }
    public boolean downed() { return entityData.get(DOWNED); }
    public boolean sparring() { return entityData.get(SPAR); }
    public void setSparring(boolean b) { entityData.set(SPAR, b); if (!b) setTarget(null); }
    public boolean canFly() { CharacterType t = character(); return t.skylander() || t == CharacterType.ULTIMA || t == CharacterType.MIKIKO; }

    /** Apply this character's level (from the owner's saved stats for it). */
    public void applyLevel(int level) {
        entityData.set(LEVEL, Math.max(1, level));
        double hp = 20 + 2 * Math.min(60, level / 10) + level * 0.15;
        var a = getAttribute(Attributes.MAX_HEALTH); if (a != null) a.setBaseValue(hp);
        var d = getAttribute(Attributes.ATTACK_DAMAGE); if (d != null) d.setBaseValue(4 + level * 0.12);
        var s = getAttribute(Attributes.MOVEMENT_SPEED); if (s != null) s.setBaseValue(0.32 + Math.min(0.15, level * 0.0015));
        if (getHealth() > getMaxHealth() || tickCount < 5) setHealth(getMaxHealth());
    }

    /** "Mikiko (bot)" */
    public net.minecraft.network.chat.Component label() {
        return character().getDisplayName().copy().append(" (bot)");
    }

    // ---------------------------------------------------------------- AI

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new DownedGoal());
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
        goalSelector.addGoal(3, new MineGoal());
        goalSelector.addGoal(4, new FollowOwner());
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new BotTargetGoal());
    }

    /** Lying down while downed: no moving, no fighting. */
    class DownedGoal extends Goal {
        DownedGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP, Flag.TARGET)); }
        @Override public boolean canUse() { return downed(); }
        @Override public void tick() { getNavigation().stop(); setTarget(null); }
    }

    class FollowOwner extends Goal {
        FollowOwner() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            ServerPlayer o = owner();
            return o != null && !downed() && getTarget() == null && mineTarget == null && distanceToSqr(o) > 6 * 6;
        }
        @Override public boolean canContinueToUse() { ServerPlayer o = owner(); return o != null && getTarget() == null && mineTarget == null && distanceToSqr(o) > 3 * 3; }
        @Override public void tick() {
            ServerPlayer o = owner();
            if (o == null) return;
            if (distanceToSqr(o) > 28 * 28 || o.level() != level()) PartyBots.teleportNear(PartyBotEntity.this, o);
            else getNavigation().moveTo(o, distanceToSqr(o) > 12 * 12 ? 1.35 : 1.1);
            getLookControl().setLookAt(o);
        }
    }

    class BotTargetGoal extends TargetGoal {
        LivingEntity pick;
        BotTargetGoal() { super(PartyBotEntity.this, false); }
        @Override public boolean canUse() {
            if (downed()) return false;
            ServerPlayer o = owner();
            if (o == null) return false;
            if (sparring()) { pick = o; return true; }
            if (relayTarget != null && relayTarget.isAlive() && level().getGameTime() < relayUntil && !PartyBots.friendly(PartyBotEntity.this, relayTarget)) { pick = relayTarget; return true; }
            if (mode() == AGGRESSIVE) {
                pick = level().getNearestEntity(level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, o.getBoundingBox().inflate(16),
                        m -> m instanceof Enemy && m.isAlive() && !PartyBots.friendly(PartyBotEntity.this, m)), net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat(),
                        PartyBotEntity.this, getX(), getY(), getZ());
                return pick != null;
            }
            return false;
        }
        @Override public void start() { setTarget(pick); super.start(); }
        @Override public boolean canContinueToUse() {
            LivingEntity t = getTarget();
            if (t == null || !t.isAlive() || downed()) return false;
            if (sparring()) return t == owner();
            ServerPlayer o = owner();
            return o != null && t.distanceToSqr(o) < 40 * 40 && !PartyBots.friendly(PartyBotEntity.this, t);
        }
    }

    /** Mining mode: senses ore near you (Karma Sense, or Data Sense for Ultima) and digs it out into its bag. */
    class MineGoal extends Goal {
        int dig;
        MineGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            if (mode() != MINING || downed() || getTarget() != null || tickCount % 20 != 0) return false;
            ServerPlayer o = owner();
            if (o == null) return false;
            mineTarget = PartyBots.findOre(PartyBotEntity.this, o);
            return mineTarget != null;
        }
        @Override public boolean canContinueToUse() { return mineTarget != null && mode() == MINING && !downed() && getTarget() == null && PartyBots.isOre(level().getBlockState(mineTarget)); }
        @Override public void start() { dig = 0; }
        @Override public void stop() { mineTarget = null; }
        @Override public void tick() {
            if (mineTarget == null) return;
            getLookControl().setLookAt(Vec3.atCenterOf(mineTarget));
            if (distanceToSqr(Vec3.atCenterOf(mineTarget)) > 3.2 * 3.2) { getNavigation().moveTo(mineTarget.getX() + .5, mineTarget.getY(), mineTarget.getZ() + .5, 1.1); return; }
            getNavigation().stop();
            if (++dig % 6 == 0) swing(InteractionHand.MAIN_HAND);
            level().destroyBlockProgress(getId(), mineTarget, Math.min(9, dig / 3));
            if (dig >= 30) { PartyBots.mine(PartyBotEntity.this, mineTarget); level().destroyBlockProgress(getId(), mineTarget, -1); mineTarget = null; }
        }
    }

    // ---------------------------------------------------------------- life

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        karma = Math.min(100F, karma + 0.08F);
        if (downed()) {
            if (++downedTicks > PartyBots.BLEED_OUT) PartyBots.bleedOut(this);
            if (tickCount % 10 == 0) ((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 0.5, getZ(), 1, .3, .1, .3, 0);
            return;
        }
        if (getTarget() != null && tickCount % 10 == 0) PartyBots.trySkill(this, getTarget());
        if (tickCount % 10 == 0) pickUpItems();
        ServerPlayer o = owner();
        if (o == null && tickCount > 100 && tickCount % 40 == 0) discard();          // owner gone
    }

    private void pickUpItems() {
        for (ItemEntity it : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(1.5), ItemEntity::isAlive)) {
            if (it.hasPickUpDelay()) continue;
            ItemStack left = inventory.addItem(it.getItem().copy());
            if (left.isEmpty()) it.discard();
            else { it.setItem(left); PartyBots.notifyFull(this); }
        }
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (downed() && !src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (src.getEntity() != null && PartyBots.friendly(this, src.getEntity()) && !(sparring() && src.getEntity() == owner())) return false;
        boolean r = super.hurt(src, amount);
        if (r && src.getEntity() instanceof LivingEntity att && !(src.getEntity() == owner() && sparring())) { relayTarget = att; relayUntil = level().getGameTime() + 200; }
        return r;
    }

    /** Instead of dying: goes down (sparring just ends the spar). */
    @Override
    public void die(DamageSource src) {
        if (!level().isClientSide && !src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && !isRemoved()) {
            setHealth(1F);
            if (sparring()) { PartyBots.sparOver(this, false); return; }
            entityData.set(DOWNED, true);
            downedTicks = 0;
            setTarget(null);
            PartyBots.downed(this);
            return;
        }
        super.die(src);
    }

    public void revive() { entityData.set(DOWNED, false); setHealth(getMaxHealth() * 0.4F); downedTicks = 0; }

    @Override public boolean isPushable() { return !downed() && super.isPushable(); }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    /** Saved with the owner (and respawned next to them), never into the world. */
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean canBeLeashed(Player p) { return false; }

    @Override
    protected InteractionResult mobInteract(Player p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && p instanceof ServerPlayer sp) PartyBots.interact(this, sp);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // ---------------------------------------------------------------- carrying the player (flying characters)

    @Override public LivingEntity getControllingPassenger() { return getFirstPassenger() instanceof Player p ? p : null; }

    @Override
    protected void tickRidden(Player rider, Vec3 input) {
        super.tickRidden(rider, input);
        setYRot(rider.getYRot()); yRotO = getYRot(); setXRot(rider.getXRot() * 0.5F); yBodyRot = getYRot(); yHeadRot = getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 input) {
        float fwd = rider.zza, side = rider.xxa * 0.5F;
        return new Vec3(side, 0, fwd);
    }

    @Override protected float getRiddenSpeed(Player rider) { return 0.35F; }

    @Override
    public void travel(Vec3 input) {
        if (isVehicle() && getControllingPassenger() instanceof Player rider) {        // flying with you aboard
            setNoGravity(true);
            Vec3 look = rider.getLookAngle();
            double sp = 0.6;
            Vec3 v = look.scale(rider.zza * sp).add(Vec3.directionFromRotation(0, rider.getYRot() - 90).scale(rider.xxa * sp * 0.5));
            setDeltaMovement(getDeltaMovement().scale(0.6).add(v.scale(0.4)));
            move(MoverType.SELF, getDeltaMovement());
            fallDistance = 0;
            return;
        }
        if (isNoGravity() && !isVehicle()) setNoGravity(false);
        super.travel(input);
    }

    @Override
    public double getPassengersRidingOffset() { return getBbHeight() * 0.55; }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putString("Char", entityData.get(CHAR));
        if (ownerId() != null) t.putUUID("Owner", ownerId());
        t.putInt("Mode", mode());
        t.putFloat("BotKarma", karma);
        t.put("Inv", inventory.createTag());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(CHAR, t.getString("Char"));
        if (t.hasUUID("Owner")) setOwner(t.getUUID("Owner"));
        entityData.set(MODE, t.getInt("Mode"));
        karma = t.getFloat("BotKarma");
        if (t.contains("Inv")) inventory.fromTag(t.getList("Inv", 10));
    }

    /** The bot as a record kept with the owner while they're offline. */
    public CompoundTag record() {
        CompoundTag t = new CompoundTag();
        t.putString("char", entityData.get(CHAR));
        t.putInt("mode", mode());
        t.putFloat("hp", getHealth());
        t.put("inv", inventory.createTag());
        return t;
    }

    public void loadRecord(CompoundTag t) {
        entityData.set(MODE, t.getInt("mode"));
        if (t.contains("inv")) inventory.fromTag(t.getList("inv", 10));
        if (t.contains("hp")) setHealth(Math.max(1F, t.getFloat("hp")));
    }
}
