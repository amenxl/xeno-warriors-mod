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
import net.minecraft.world.level.block.state.BlockState;
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
    /** Its bag: the character's own inventory (36 slots, like a player's main inventory). */
    public final SimpleContainer inventory = new SimpleContainer(36);
    /** Eating: ticks left on the current bite, and the slot being eaten from. */
    int eatTicks = -1, eatSlot = -1;
    /** Reviving: who it's standing by. */
    LivingEntity reviving;
    /** Wandering about near its owner. */
    Vec3 wanderTo; int wanderWait;
    public float karma = 100F;
    int downedTicks;
    long[] skillReady = new long[5];
    LivingEntity relayTarget;

    /** (Journey mode) go for this enemy until the given game time. */
    public void journeyTarget(LivingEntity t, long until) { relayTarget = t; relayUntil = until; setTarget(t); }

    /** A Journey AI teammate (its own rules: no menu, no sparring, no reviving). */
    public boolean journey() { return getPersistentData().getBoolean("xw_journey_bot"); }
    long relayUntil;
    BlockPos mineTarget;
    int fullNotice;
    /** Blocks it gave up on reaching (pos -> until tickCount), so it doesn't walk at a buried ore forever. */
    final java.util.Map<Long, Integer> giveUp = new java.util.HashMap<>();
    boolean unreachable(BlockPos p) { Integer t = giveUp.get(p.asLong()); return t != null && t > tickCount; }

    /** (For tests) what it's mining and which goals are running. */
    public BlockPos mineTargetForTest() { return mineTarget; }
    public String goalsForTest() {
        StringBuilder sb = new StringBuilder();
        goalSelector.getRunningGoals().forEach(g -> sb.append(g.getGoal().getClass().getSimpleName()).append(' '));
        targetSelector.getRunningGoals().forEach(g -> sb.append("T:").append(g.getGoal().getClass().getSimpleName()).append(' '));
        return sb.toString().trim();
    }

    /** When you last put something in its hand from its menu (it keeps that a while; see BotGear). */
    public int handPicked = -100000;

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
        if (!level().isClientSide && getRemovalReason() == RemovalReason.UNLOADED_TO_CHUNK && ownerId() != null && !journey()) PartyBots.LEFT_BEHIND.add(Map.entry(ownerId(), record()));   // owner teleported far away
        if (!level().isClientSide && getRemovalReason() != null && getRemovalReason().shouldDestroy()) PartyBots.forgetFake(getUUID());
    }

    public CharacterType character() { return CharacterType.byId(entityData.get(CHAR)); }
    public void setCharacter(CharacterType t) { entityData.set(CHAR, t.getId()); }
    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public void setOwner(UUID id) { PartyBots.track(this, false); entityData.set(OWNER, Optional.ofNullable(id)); if (isAddedToWorld()) PartyBots.track(this, true); }
    /** The owner, wherever they are on the server (null when offline). */
    public ServerPlayer owner() { return level() instanceof ServerLevel s && ownerId() != null ? s.getServer().getPlayerList().getPlayer(ownerId()) : null; }
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
        goalSelector.addGoal(2, new ReviveGoal());
        goalSelector.addGoal(3, new com.xenowarriors.combat.BotCombo(this, 1.3, m -> ((PartyBotEntity) m).character()));   // the same combos as players
        goalSelector.addGoal(4, new MineGoal());
        goalSelector.addGoal(5, new FollowOwner());
        goalSelector.addGoal(6, new WanderNearOwner());
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new BotTargetGoal());
    }

    /** Lying down while downed: no moving, no fighting. */
    class DownedGoal extends Goal {
        DownedGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP, Flag.TARGET)); }
        @Override public boolean canUse() { return downed(); }
        @Override public void tick() { getNavigation().stop(); setTarget(null); }
    }

    /** Keeps 4-8 blocks from the owner (not on top of them); flies along if it can, otherwise waits and teleports once they land. */
    /** Flying alongside the owner right now (gravity stays off). */
    boolean flyingWithOwner;

    class FollowOwner extends Goal {
        FollowOwner() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            ServerPlayer o = owner();
            return o != null && !downed() && getTarget() == null && mineTarget == null && reviving == null && !isPassenger() && distanceToSqr(o) > 8 * 8;
        }
        @Override public boolean canContinueToUse() { ServerPlayer o = owner(); return o != null && getTarget() == null && mineTarget == null && reviving == null && !isPassenger() && distanceToSqr(o) > 4 * 4; }
        @Override public void stop() { getNavigation().stop(); flyingWithOwner = false; if (isNoGravity() && !isVehicle()) { setNoGravity(false); } }
        @Override public void tick() {
            ServerPlayer o = owner();
            if (o == null) return;
            boolean ownerFlying = o.getAbilities().flying || o.isFallFlying() || PartyBots.airborne(o);
            if (o.level() != level()) { PartyBots.teleportNear(PartyBotEntity.this, o); return; }
            if (ownerFlying) {
                if (canFly()) { flyingWithOwner = true; PartyBots.flyToward(PartyBotEntity.this, o); return; }
                flyingWithOwner = false;
                // can't fly: wait on the ground for them to land
                getNavigation().stop();
                getLookControl().setLookAt(o);
                return;
            }
            if (isNoGravity() && !isVehicle()) setNoGravity(false);
            double d2 = distanceToSqr(o);
            if (d2 > 28 * 28 || (d2 > 12 * 12 && !canFly() && o.onGround() && getNavigation().isDone() && tickCount % 20 == 0)) { PartyBots.teleportNear(PartyBotEntity.this, o); return; }
            if (o.getVehicle() != null && tickCount % 10 == 0 && PartyBots.boardWith(PartyBotEntity.this, o)) return;
            getNavigation().moveTo(o, d2 > 14 * 14 ? 1.4 : d2 > 10 * 10 ? 1.2 : 1.0);
            getLookControl().setLookAt(o);
        }
    }

    /** Idle near the owner: every so often strolls a few blocks and looks about, instead of standing on the spot. */
    class WanderNearOwner extends Goal {
        WanderNearOwner() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            ServerPlayer o = owner();
            if (o == null || downed() || getTarget() != null || mineTarget != null || reviving != null || isPassenger() || o.getVehicle() != null) return false;
            if (PartyBots.airborne(o) || o.getAbilities().flying) return false;
            if (--wanderWait > 0) return false;
            wanderWait = 60 + getRandom().nextInt(140);
            if (distanceToSqr(o) > 8 * 8) return false;
            double a = getRandom().nextDouble() * Math.PI * 2, r = 2.5 + getRandom().nextDouble() * 4;
            Vec3 to = o.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            BlockPos bp = BlockPos.containing(to);
            if (!level().getBlockState(bp).isAir() || !level().getBlockState(bp.below()).isSolid()) return false;
            wanderTo = to;
            return true;
        }
        @Override public boolean canContinueToUse() { return wanderTo != null && !getNavigation().isDone() && getTarget() == null && !downed() && reviving == null; }
        @Override public void start() { getNavigation().moveTo(wanderTo.x, wanderTo.y, wanderTo.z, 0.8); }
        @Override public void stop() { wanderTo = null; }
    }

    /** Someone's down (the owner, a party member or another bot): go and stand in their revive circle. */
    class ReviveGoal extends Goal {
        ReviveGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        private int lastLook;
        @Override public boolean canUse() {
            // (the goal selector only asks every other tick, on its own clock: a tickCount % 10 test could never line up)
            if (downed() || journey() || tickCount - lastLook < 5) return false;
            lastLook = tickCount;
            reviving = PartyBots.someoneDown(PartyBotEntity.this);
            return reviving != null;
        }
        @Override public boolean canContinueToUse() { return reviving != null && reviving.isAlive() && !downed() && PartyBots.isDown(reviving) && reviving.distanceToSqr(PartyBotEntity.this) < 40 * 40; }
        private int stalled, going; private double closest;
        @Override public boolean requiresUpdateEveryTick() { return true; }     // (its timers count real ticks)
        @Override public void start() { stalled = 0; going = 0; closest = 1e9; }
        @Override public void stop() { reviving = null; }
        @Override public void tick() {
            if (reviving == null) return;
            setTarget(null);
            getLookControl().setLookAt(reviving);
            double d2 = distanceToSqr(reviving);
            if (d2 > 1.4 * 1.4) {
                if (getNavigation().isDone() || tickCount % 10 == 0) getNavigation().moveTo(reviving.getX(), reviving.getY(), reviving.getZ(), 1.35);
                if (d2 < closest - 0.25) { closest = d2; stalled = 0; } else stalled++;
                if (d2 > 24 * 24 || stalled > 40 || ++going > 60) {               // too far, two seconds without getting closer, or three seconds on the way: hop over to them
                    stalled = 0; going = 0;
                    for (net.minecraft.world.phys.Vec3 off : new net.minecraft.world.phys.Vec3[]{new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1), new Vec3(1, 1, 0), new Vec3(0, 1, 1)}) {
                        Vec3 at = reviving.position().add(off);
                        if (level().noCollision(PartyBotEntity.this, getBoundingBox().move(at.subtract(position())))) { teleportTo(at.x, at.y, at.z); getNavigation().stop(); break; }
                    }
                }
            } else getNavigation().stop();
        }
    }

    /** Never a target: souls sealed in a Hekublade (on display in the realm), anything that can't be hurt, the blade's anchor. */
    public static boolean untouchable(LivingEntity t) {
        return t != null && (t.getPersistentData().hasUUID("xw_sealed") || t.isInvulnerable() || t.getTags().contains("xw_blade_anchor")
                || (t.level().dimension() == com.xenowarriors.mikiko.HekubladeRealm.KEY && t instanceof net.minecraft.world.entity.Mob m && m.isNoAi()));
    }

    @Override
    public void setTarget(LivingEntity t) { super.setTarget(untouchable(t) ? null : t); }

    class BotTargetGoal extends TargetGoal {
        LivingEntity pick;
        BotTargetGoal() { super(PartyBotEntity.this, false); }
        @Override public boolean canUse() {
            if (downed()) return false;
            ServerPlayer o = owner();
            if (o == null) return false;
            if (sparring()) { pick = o; return true; }
            if (relayTarget != null && relayTarget.isAlive() && level().getGameTime() < relayUntil && !PartyBots.friendly(PartyBotEntity.this, relayTarget) && !untouchable(relayTarget)) { pick = relayTarget; return true; }
            if (mode() == AGGRESSIVE) {
                pick = level().getNearestEntity(level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, o.getBoundingBox().inflate(16),
                        m -> m instanceof Enemy && m.isAlive() && !PartyBots.friendly(PartyBotEntity.this, m) && !untouchable(m)), net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat(),
                        PartyBotEntity.this, getX(), getY(), getZ());
                return pick != null;
            }
            return false;
        }
        @Override public void start() { setTarget(pick); super.start(); }
        @Override public boolean canContinueToUse() {
            LivingEntity t = getTarget();
            if (t == null || !t.isAlive() || downed() || untouchable(t)) return false;
            if (sparring()) return t == owner();
            ServerPlayer o = owner();
            return o != null && t.distanceToSqr(o) < 40 * 40 && !PartyBots.friendly(PartyBotEntity.this, t);
        }
    }

    /** Mining mode: senses ore near you (Karma Sense, or Data Sense for Ultima) and digs it out into its bag. */
    /** Mine mode: ore it senses near you, and trees (the whole trunk comes down), with the best tool it has. */
    class MineGoal extends Goal {
        int dig, need, lastLook, lastTreeLook = -1000, walking;
        double closest;
        @Override public boolean requiresUpdateEveryTick() { return true; }     // (digging and its timers count real ticks)
        MineGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            // (the goal selector asks every other tick on its own clock: a tickCount % 20 test could never line up)
            if (mode() != MINING || downed() || getTarget() != null || tickCount - lastLook < 10) return false;
            lastLook = tickCount;
            ServerPlayer o = owner();
            if (o == null || o.level() != level() || !com.xenowarriors.modes.ModeRules.canGrief(level())) return false;
            mineTarget = PartyBots.findOre(PartyBotEntity.this, o);
            if (mineTarget == null && tickCount - lastTreeLook >= 40) {         // no ore: a tree (looked for less often - it's a bigger search)
                lastTreeLook = tickCount;
                mineTarget = BotGear.findTree(PartyBotEntity.this, o);
            }
            return mineTarget != null;
        }
        private boolean breakable(BlockState s) { return PartyBots.isOre(s) || BotGear.isLog(s); }
        @Override public boolean canContinueToUse() { return mineTarget != null && mode() == MINING && !downed() && getTarget() == null && breakable(level().getBlockState(mineTarget)); }
        @Override public void start() { dig = 0; need = 0; walking = 0; closest = 1e9; }
        @Override public void stop() { if (mineTarget != null) level().destroyBlockProgress(getId(), mineTarget, -1); mineTarget = null; }
        @Override public void tick() {
            if (mineTarget == null) return;
            getLookControl().setLookAt(Vec3.atCenterOf(mineTarget));
            double d2 = distanceToSqr(Vec3.atCenterOf(mineTarget));
            if (d2 > 3.2 * 3.2) {
                if (d2 < closest - 0.5) { closest = d2; walking = 0; }              // getting closer
                if (++walking > 80) {                                              // 4 seconds without getting any closer: it can't get there
                    giveUp.put(mineTarget.asLong(), tickCount + 20 * 60);
                    if (giveUp.size() > 64) giveUp.values().removeIf(t -> t <= tickCount);
                    mineTarget = null;
                    return;
                }
                if (getNavigation().isDone() || tickCount % 10 == 0) getNavigation().moveTo(mineTarget.getX() + .5, mineTarget.getY(), mineTarget.getZ() + .5, 1.1);
                return;
            }
            getNavigation().stop();
            BlockState st = level().getBlockState(mineTarget);
            if (need == 0) { BotGear.holdBestTool(PartyBotEntity.this, st); need = BotGear.digTicks(PartyBotEntity.this, mineTarget, st); }
            if (++dig % 6 == 0) swing(InteractionHand.MAIN_HAND);
            level().destroyBlockProgress(getId(), mineTarget, Math.min(9, dig * 10 / Math.max(1, need)));
            if (dig >= need) {
                level().destroyBlockProgress(getId(), mineTarget, -1);
                if (BotGear.isLog(st)) BotGear.fellTree(PartyBotEntity.this, mineTarget);
                else { PartyBots.mine(PartyBotEntity.this, mineTarget); BotGear.wear(PartyBotEntity.this); }
                mineTarget = null;
            }
        }
    }

    // ---------------------------------------------------------------- life

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        karma = Math.min(100F, karma + 0.08F);
        if (downed()) {
            if (++downedTicks > PartyBots.BLEED_OUT) { PartyBots.bleedOut(this); return; }
            if (tickCount % 10 == 0) ((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 0.5, getZ(), 1, .3, .1, .3, 0);
            if (!journey()) com.xenowarriors.party.ReviveCircle.tickBot(this);
            return;
        }
        if (getTarget() != null && tickCount % 10 == 0) { BotGear.holdBestWeapon(this); PartyBots.trySkill(this, getTarget()); }
        if (tickCount % 10 == 0) pickUpItems();
        tickEating();
        if (tickCount % 40 == 0) PartyBots.wearBestArmour(this);
        ServerPlayer o = owner();
        if (o == null && tickCount > 100 && tickCount % 40 == 0) { if (!journey()) PartyBots.LEFT_BEHIND.add(java.util.Map.entry(ownerId(), record())); PartyBots.forgetFake(getUUID()); discard(); }   // owner gone: kept until they're back
        else if (o != null && o.level() != level() && tickCount % 20 == 0 && !journey()) {   // owner in another world (respawned, portal): follow, or wait
            if (PartyBots.canFollowTo(character(), o.level())) PartyBots.teleportNear(this, o);
            else { PartyBots.LEFT_BEHIND.add(java.util.Map.entry(ownerId(), record())); PartyBots.forgetFake(getUUID()); discard(); }
        }
    }

    /** Hungry (under 60% health, not fighting): eats food from its bag - a few seconds of chewing, then the food's worth in health. */
    private void tickEating() {
        if (eatTicks >= 0) {
            if (eatSlot < 0 || eatSlot >= inventory.getContainerSize() || !inventory.getItem(eatSlot).isEdible()) { eatTicks = -1; eatSlot = -1; return; }
            if (eatTicks % 4 == 0) {
                ItemStack food = inventory.getItem(eatSlot);
                ((ServerLevel) level()).sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, food), getX(), getY() + 1.2, getZ(), 4, 0.15, 0.1, 0.15, 0.05);
                level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EAT, net.minecraft.sounds.SoundSource.NEUTRAL, 0.6F, 0.9F + getRandom().nextFloat() * 0.3F);
            }
            if (--eatTicks < 0) {
                ItemStack food = inventory.getItem(eatSlot);
                var props = food.getFoodProperties(this);
                float heal = props == null ? 2F : props.getNutrition() * 1.2F + props.getSaturationModifier() * 4F;
                heal(Math.max(2F, heal));
                if (props != null) for (var eff : props.getEffects()) if (getRandom().nextFloat() < eff.getSecond()) addEffect(new net.minecraft.world.effect.MobEffectInstance(eff.getFirst()));
                ItemStack rest = food.getCraftingRemainingItem();
                food.shrink(1);
                if (food.isEmpty()) inventory.setItem(eatSlot, rest.isEmpty() ? ItemStack.EMPTY : rest);
                else if (!rest.isEmpty()) inventory.addItem(rest);
                level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_BURP, net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 1F);
                eatSlot = -1;
            }
            return;
        }
        if (tickCount % 30 != 0 || getHealth() > getMaxHealth() * 0.6F || getTarget() != null || sparring()) return;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEdible() && s.getFoodProperties(this) != null && !s.is(net.minecraft.world.item.Items.ROTTEN_FLESH) && !s.is(net.minecraft.world.item.Items.SPIDER_EYE) && !s.is(net.minecraft.world.item.Items.PUFFERFISH)) {
                eatSlot = i; eatTicks = 32;
                com.xenowarriors.anim.Poses.play(this, com.xenowarriors.anim.Poses.EL_RAISE, 32);
                return;
            }
        }
    }

    public boolean eating() { return eatTicks >= 0; }

    private void pickUpItems() {
        ServerPlayer own = owner();
        if (own != null && own.distanceToSqr(this) < 9) return;          // (the owner's own drops are theirs)
        for (ItemEntity it : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(1.5), ItemEntity::isAlive)) {
            if (it.hasPickUpDelay() || it.getOwner() instanceof net.minecraft.world.entity.player.Player || it.getAge() < 60) continue;
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
        if (!level().isClientSide && !journey()) PartyBots.checkIn(this, owner());   // (the void, /kill: their things go home first)
        super.die(src);
    }

    public void revive() { entityData.set(DOWNED, false); setHealth(getMaxHealth() * 0.4F); downedTicks = 0; com.xenowarriors.party.ReviveCircle.forget(getUUID()); }

    @Override public boolean isPushable() { return !downed() && super.isPushable(); }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    /** Saved with the owner (and respawned next to them), never into the world. */
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean canBeLeashed(Player p) { return false; }

    /** Sneak + right-click to talk to a bot (a plain right-click just tells you so). Sneaking with armour in hand puts it on them. */
    @Override
    protected InteractionResult mobInteract(Player p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && p instanceof ServerPlayer sp) {
            if (!p.isShiftKeyDown()) {
                if (!journey() && p.getUUID().equals(ownerId())) sp.displayClientMessage(net.minecraft.network.chat.Component.literal("Sneak + right-click to talk to ").append(label()).withStyle(net.minecraft.ChatFormatting.GRAY), true);
                return InteractionResult.PASS;
            }
            ItemStack held = p.getItemInHand(hand);
            if (!held.isEmpty() && held.getItem() instanceof net.minecraft.world.item.ArmorItem ai && p.getUUID().equals(ownerId()) && !downed() && !journey()) {
                ItemStack old = getItemBySlot(ai.getEquipmentSlot());
                setItemSlot(ai.getEquipmentSlot(), held.copyWithCount(1));
                held.shrink(1);
                if (!old.isEmpty() && !p.getInventory().add(old)) p.drop(old, false);
                level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC, net.minecraft.sounds.SoundSource.NEUTRAL, 1F, 1F);
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal("").append(label()).append(" put on ").append(getItemBySlot(ai.getEquipmentSlot()).getHoverName()).withStyle(net.minecraft.ChatFormatting.AQUA), true);
                return InteractionResult.CONSUME;
            }
            PartyBots.interact(this, sp);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Bots keep their armour: it's never dropped as loot (it goes back to the character's save). */
    /** A landed hit wears its weapon down (the weapon's damage is already in its attack damage, as a held item). */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity t) {
        boolean hit = super.doHurtTarget(t);
        if (hit && !level().isClientSide) BotGear.wear(this);
        return hit;
    }

    @Override protected void dropEquipment() {}
    @Override protected void dropCustomDeathLoot(DamageSource s, int looting, boolean recentlyHit) {}

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
        if (isNoGravity() && !isVehicle() && !flyingWithOwner) setNoGravity(false);
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
        t.put("Armour", armourTag());
    }

    /** Worn armour, offhand and what's in its hand, as a list (feet, legs, chest, head, offhand, hand). */
    public ListTag armourTag() {
        ListTag l = new ListTag();
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND}) l.add(getItemBySlot(s).save(new CompoundTag()));
        return l;
    }

    public void loadArmour(ListTag l) {
        EquipmentSlot[] slots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND};
        for (int i = 0; i < slots.length && i < l.size(); i++) setItemSlot(slots[i], ItemStack.of(l.getCompound(i)));
    }

    public ItemStack[] armour() {
        return new ItemStack[]{getItemBySlot(EquipmentSlot.FEET), getItemBySlot(EquipmentSlot.LEGS), getItemBySlot(EquipmentSlot.CHEST), getItemBySlot(EquipmentSlot.HEAD)};
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(CHAR, t.getString("Char"));
        if (t.hasUUID("Owner")) setOwner(t.getUUID("Owner"));
        entityData.set(MODE, t.getInt("Mode"));
        karma = t.getFloat("BotKarma");
        if (t.contains("Inv")) inventory.fromTag(t.getList("Inv", 10));
        if (t.contains("Armour")) loadArmour(t.getList("Armour", 10));
    }

    /** The bot as a record kept with the owner while they're offline. */
    public CompoundTag record() {
        CompoundTag t = new CompoundTag();
        t.putString("char", entityData.get(CHAR));
        t.putInt("mode", mode());
        t.putFloat("hp", getHealth());
        t.put("inv", inventory.createTag());
        t.put("armour", armourTag());
        if (getPersistentData().contains("xw_inv_kept")) t.put("kept", getPersistentData().getCompound("xw_inv_kept"));   // (that character's ender chest and XP)
        return t;
    }

    public void loadRecord(CompoundTag t) {
        entityData.set(MODE, t.getInt("mode"));
        if (t.contains("inv")) inventory.fromTag(t.getList("inv", 10));
        if (t.contains("armour")) loadArmour(t.getList("armour", 10));
        if (t.contains("hp")) setHealth(Math.max(1F, t.getFloat("hp")));
        if (t.contains("kept")) getPersistentData().put("xw_inv_kept", t.getCompound("kept"));
    }
}
