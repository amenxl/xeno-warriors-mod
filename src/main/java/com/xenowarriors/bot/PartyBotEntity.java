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
    private static final EntityDataAccessor<Boolean> DMZ_FLY = SynchedEntityData.defineId(PartyBotEntity.class, EntityDataSerializers.BOOLEAN);
    /** Its bag (slots 0-35: the character's own inventory, like a player's main inventory) and its three extra tabs (36-116, only while it's a bot - see BotInventory). */
    public final BotInventory inventory = new BotInventory();
    /** When to tidy its things next (a few seconds after they changed; 0 = nothing pending - see BotBags). */
    int tidyAt;
    /** Tidying right now (its own moves don't schedule another). */
    boolean sorting;
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

    /** Flying under its own power right now (to reach something, or a turn about the sky); when it last flew. */
    boolean selfFlying; int flewAt;
    /** Ticks left of a running jump across a gap (no cliff check meanwhile), and what the last one was (tests). */
    int leapTicks; public String lastLeap = "";
    /** Drinking a potion: ticks left, from which bag slot; when it may drink again; what it last drank; how many. */
    int drinkTicks = -1, drinkSlot = -1, drinkReady; String lastDrink = ""; public int drinks;
    /** Using a bow or gun right now (so it doesn't swap to its sword mid-shot), and shots fired (tests). */
    boolean rangedActive; public int shots;
    /** Ticks spent in water / lava in a row, and how it got out (tests). */
    int wetTicks, lavaTicks; public String lastEscape = "";
    /** When it next takes a turn about the sky (flying characters, idle by their owner). */
    int nextFlourish = 600 + (int) (Math.random() * 600);
    /** Its target can't be reached on foot (flying, up a pillar, across a drop): worked out every half second. */
    private boolean unreachableCache; private int unreachableAt = -100; private LivingEntity unreachableFor;
    /** Cliff edges it stopped at (tests). */
    public int edgeStops;
    /** Times its owner's tick brought it over from far behind (tests). */
    public int catchUps;
    /** Something it's off doing for a minute (a furnace, handing something over, fetching): see BotErrands. */
    public BotErrands.Errand errand;
    boolean busyElsewhere() { return errand != null; }
    /** When it was last hurt (its own clock); shield guards (ticks left of this one, how many raised, blows stopped, swaps); Data Shards it absorbed. */
    public int lastHurtAt = -100000, guardTicks, guards, shieldBlocks, shieldSwaps, shardAt = -100000, shardsUsed;
    /** Ores/logs it's off getting for a craft it has in mind (see BotCrafting.gatherTarget), and how many more. */
    boolean gathering; public int gathered;
    /** (Tests) crafting looks held back because it wasn't safe; crafting tables it made itself. */
    public int offersHeld, tablesMade, seatShots, autoCrafted;
    /** Yuri: when she next looks about for hamburger makings, and how often she has. */
    public int yuriNext = 300 + (int) (Math.random() * 300), yuriLooks;

    public boolean drinking() { return drinkTicks >= 0; }
    public boolean selfFlyingForTest() { return selfFlying; }
    /** Flying right now (after its owner, on a lap of the sky, or with no ground under it while it can fly). */
    public boolean airborneFlying() {
        if (onGround() || isPassenger() || isInWater()) return false;
        if (isNoGravity() || flyingWithOwner || selfFlying) return true;
        return canFly() && level().getBlockState(blockPosition().below(2)).isAir() && level().getBlockState(blockPosition().below()).isAir();
    }
    /** (Tests) take a turn about the sky now. */
    public void flourishNow() { nextFlourish = tickCount; }

    /** Its target can't be reached by walking there (it flies, or there's no path to it). */
    public boolean targetUnreachable() {
        LivingEntity t = getTarget();
        if (t == null) return false;
        if (t != unreachableFor || tickCount - unreachableAt >= 10) {
            unreachableFor = t; unreachableAt = tickCount;
            if (BotMoves.airborne(t)) unreachableCache = true;
            else if (distanceToSqr(t) < 2.5 * 2.5) unreachableCache = false;
            else {
                var path = getNavigation().createPath(t, 0);
                unreachableCache = path == null || !path.canReach() && (path.getEndNode() == null || path.getEndNode().distanceTo(t.blockPosition()) > 2.5F);
            }
        }
        return unreachableCache;
    }

    public PartyBotEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        inventory.addListener(c -> { if (!sorting && !level().isClientSide) tidyAt = tickCount + 100; });   // (its things changed: tidy up in a few seconds)
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
        entityData.define(DMZ_FLY, false);
    }

    // ---------------------------------------------------------------- state

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (stranded) { if (!level().isClientSide) PartyBots.STRANDED.add(this); return; }   // (its record already went to the waiting list: this copy goes)
        PartyBots.track(this, true);
    }

    /** Its chunk dropped out of the world under it (owner teleported, respawned or flew off far): it's on the waiting list instead. */
    boolean stranded;

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        PartyBots.track(this, false);
        // Owner went far away: the chunk stops being tracked first (no removal reason yet), and is only unloaded - without
        // saving a bot, see shouldBeSaved - later. Either way it waits for its owner, things and all (it used to be lost
        // here with everything it carried, and re-adding it then found that character's save empty).
        RemovalReason why = getRemovalReason();
        if (!level().isClientSide && !stranded && ownerId() != null && !journey() && (why == null || why == RemovalReason.UNLOADED_TO_CHUNK)) {
            stranded = true;
            if (downed()) PartyBots.strandedDown(this);                          // (left lying there: it goes home, as if it bled out)
            else PartyBots.LEFT_BEHIND.add(Map.entry(ownerId(), record()));
            if (why == null) PartyBots.STRANDED.add(this);                       // (the entity itself goes next tick: never two of it)
            PartyBots.testLog(this, "stranded (" + (why == null ? "chunk left the world" : "chunk unloaded") + ") - on the waiting list");
        }
        if (!level().isClientSide && why != null && why.shouldDestroy()) PartyBots.forgetFake(getUUID());
    }

    /** Never through a portal on its own (it follows its owner between worlds, see PartyBots.teleportNear). */
    @Override public boolean canChangeDimensions() { return false; }

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
    /** Its side: owner, party, their pets and bots (so DragonMineZ ki attacks it throws pass them by) - not the owner while sparring. */
    @Override
    public boolean isAlliedTo(Entity e) {
        if (super.isAlliedTo(e)) return true;
        if (level().isClientSide || e == null || e == this) return false;
        return PartyBots.friendly(this, e) && !(sparring() && e == owner());
    }

    public boolean canFly() { return naturalFlier() || entityData.get(DMZ_FLY); }

    /** Its character flies by itself (Skylanders, Ultima, Mikiko). */
    public boolean naturalFlier() { CharacterType t = character(); return t.skylander() || t == CharacterType.ULTIMA || t == CharacterType.MIKIKO; }

    /** With DragonMineZ: it has learned Fly (and has the ki for it) - it flies like the fliers do (DmzBotSkills keeps this up to date). */
    public boolean dmzFly() { return entityData.get(DMZ_FLY); }
    public void setDmzFly(boolean on) { if (entityData.get(DMZ_FLY) != on) entityData.set(DMZ_FLY, on); }

    /** Apply this character's level (from the owner's saved stats for it). */
    public void applyLevel(int level) {
        entityData.set(LEVEL, Math.max(1, level));
        double hp = 20 + 2 * com.xenowarriors.stats.Stats.bonusHearts(level);   // (the same hearts a player of this Character Level gets)
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
        goalSelector.addGoal(2, new EscapeLiquid());
        goalSelector.addGoal(3, new ReviveGoal());
        goalSelector.addGoal(4, new BotRanged(this));                                                   // bows, crossbows, guns: far or out-of-reach enemies
        goalSelector.addGoal(5, new FlyChase());                                                        // fliers: up to what they can't walk to
        goalSelector.addGoal(6, new com.xenowarriors.combat.BotCombo(this, 1.3, m -> ((PartyBotEntity) m).character()));   // the same combos as players
        goalSelector.addGoal(7, new MineGoal());
        goalSelector.addGoal(7, new BotErrands(this));                                                  // (quiet times: furnaces, handing things over, Ultima's shards, Yuri's look about)
        goalSelector.addGoal(7, new com.xenowarriors.compat.DmzBotTalk(this));                          // (DragonMineZ: walks up to the people its own missions wait on)
        goalSelector.addGoal(7, new com.xenowarriors.compat.DmzBotMasters(this));                       // (DragonMineZ: learns techniques from masters close by)
        goalSelector.addGoal(8, new FollowOwner());
        goalSelector.addGoal(9, new FlyAbout());
        goalSelector.addGoal(10, new WanderNearOwner());
        goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(12, new RandomLookAroundGoal(this));
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

    /** The owner is in trouble right now: just hurt, being attacked, or low. */
    public static boolean ownerInDanger(ServerPlayer o) {
        return o.hurtTime > 0 || (o.getLastHurtByMob() != null && o.tickCount - o.getLastHurtByMobTimestamp() < 100) || o.getHealth() < o.getMaxHealth() * 0.4F;
    }

    /** How far it roams before coming back: fliers range out to 32 blocks (back fast when the owner is attacked), walkers 8. */
    public double leash() {
        ServerPlayer o = owner();
        return canFly() && !journey() && o != null && !ownerInDanger(o) && !(o.getAbilities().flying || o.isFallFlying()) ? FLIER_LEASH : 8;
    }
    public static final double FLIER_LEASH = 32;

    class FollowOwner extends Goal {
        FollowOwner() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            ServerPlayer o = owner();
            if (o == null || downed() || getTarget() != null || mineTarget != null || reviving != null || isPassenger() || busyElsewhere()) return false;
            double l = leash();
            return distanceToSqr(o) > l * l;
        }
        @Override public boolean canContinueToUse() {
            ServerPlayer o = owner();
            if (o == null || getTarget() != null || mineTarget != null || reviving != null || isPassenger() || busyElsewhere()) return false;
            double l = leash() > 8 ? 12 : 4;
            return distanceToSqr(o) > l * l || o.level() != level();
        }
        @Override public void stop() { getNavigation().stop(); flyingWithOwner = false; BotMoves.land(PartyBotEntity.this); stuck = 0; if (isNoGravity() && !isVehicle()) { setNoGravity(false); } }
        private int stuck; private Vec3 lastPos = Vec3.ZERO;
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
            double d2 = distanceToSqr(o);
            // a flier that can't walk to them (up on a ledge, across a ravine, stuck behind something): flies over
            if (canFly()) {
                if (!selfFlying && position().distanceToSqr(lastPos) < 0.0025 && d2 > 6 * 6) stuck++; else if (!selfFlying) stuck = 0;   // (not getting anywhere on foot)
                lastPos = position();
                boolean above = o.getY() - getY() > 2.5 && o.onGround();
                if (selfFlying || stuck > 30 || (above && getNavigation().isDone()) || (tickCount % 10 == 0 && above && !getNavigation().moveTo(o, 1.0))) {
                    if (d2 < 3.5 * 3.5 && Math.abs(o.getY() - getY()) < 1.5) { BotMoves.land(PartyBotEntity.this); stuck = 0; }
                    else { BotMoves.flyTo(PartyBotEntity.this, o.position().add(0, 1.2, 0).add(position().subtract(o.position()).multiply(1, 0, 1).normalize().scale(1.2)), d2 > 12 * 12 ? 0.8 : 0.5); getLookControl().setLookAt(o); return; }
                }
            }
            // a flier a long way off (it was ranging about, or the owner's in trouble): back through the air, fast
            if (canFly() && d2 > 16 * 16 && d2 < 56 * 56) {
                BotMoves.flyTo(PartyBotEntity.this, o.position().add(0, 1.5, 0), ownerInDanger(o) ? 1.0 : 0.8);
                getLookControl().setLookAt(o);
                return;
            }
            if (isNoGravity() && !isVehicle() && !selfFlying) setNoGravity(false);
            double tp = canFly() ? 56 : 28;
            if (d2 > tp * tp || (d2 > 12 * 12 && !canFly() && o.onGround() && getNavigation().isDone() && tickCount % 20 == 0)) { PartyBots.teleportNear(PartyBotEntity.this, o); return; }
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
            double l = leash();
            if (distanceToSqr(o) > l * l || busyElsewhere()) return false;
            double a = getRandom().nextDouble() * Math.PI * 2, r = l > 8 ? 4 + getRandom().nextDouble() * 18 : 2.5 + getRandom().nextDouble() * 4;   // (fliers range further)
            Vec3 to = o.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            BlockPos bp = BlockPos.containing(to);
            if (!level().getBlockState(bp).isAir() || !level().getBlockState(bp.below()).isSolid()) return false;
            var path = getNavigation().createPath(bp, 0);                      // (a stroll, not a swim: no wandering through water)
            if (path == null) return false;
            for (int i = 0; i < path.getNodeCount(); i++) { var t = path.getNode(i).type; if (t == net.minecraft.world.level.pathfinder.BlockPathTypes.WATER || t == net.minecraft.world.level.pathfinder.BlockPathTypes.LAVA) return false; }
            wanderTo = to;
            return true;
        }
        @Override public boolean canContinueToUse() { return wanderTo != null && !getNavigation().isDone() && getTarget() == null && !downed() && reviving == null; }
        @Override public void start() { getNavigation().moveTo(wanderTo.x, wanderTo.y, wanderTo.z, 0.8); }
        @Override public void stop() { wanderTo = null; }
    }

    /** Fliers: something they can't walk to (a flying enemy, one up a pillar or across a ravine) - up into the air and at it. */
    class FlyChase extends Goal {
        private int swingAt;
        FlyChase() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        private boolean able() { LivingEntity t = getTarget(); return canFly() && t != null && t.isAlive() && !downed() && !isPassenger() && reviving == null && distanceToSqr(t) < 48 * 48; }
        @Override public boolean canUse() { return able() && targetUnreachable(); }
        @Override public boolean canContinueToUse() { return able() && (targetUnreachable() || !onGround()); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void stop() { BotMoves.land(PartyBotEntity.this); }
        @Override public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30F, 30F);
            if (!targetUnreachable()) { BotMoves.land(PartyBotEntity.this); if (tickCount % 10 == 0) getNavigation().moveTo(t, 1.3); return; }   // (they came down: back to the ground)
            Vec3 side = position().subtract(t.position()).multiply(1, 0, 1);
            side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 goal = t.position().add(side.scale(1.3)).add(0, Math.max(0, t.getBbHeight() * 0.5 - getBbHeight() * 0.4), 0);
            BotMoves.flyTo(PartyBotEntity.this, goal, distanceToSqr(t) > 10 * 10 ? 0.8 : 0.55);
            getLookControl().setLookAt(t, 30F, 30F);
            if (distanceToSqr(t) < 3.2 * 3.2 && tickCount - swingAt >= 12 && hasLineOfSight(t)) {      // close enough: hit them (the same combo hits as on the ground)
                swingAt = tickCount;
                com.xenowarriors.combat.ComboStyle st = com.xenowarriors.combat.ComboStyle.of(PartyBotEntity.this, getMainHandItem(), character());
                if (st == null || st == com.xenowarriors.combat.ComboStyle.MIKIKO) st = com.xenowarriors.combat.ComboStyle.FIST;
                swing(InteractionHand.MAIN_HAND);
                com.xenowarriors.combat.ComboServer.botStrike(PartyBotEntity.this, st, 0, false, t);
                airHits++;
            }
        }
    }
    public int airHits;

    /** In water it doesn't need to be in (or any lava): out onto the nearest dry land, fast. */
    class EscapeLiquid extends Goal {
        private BlockPos spot; private int ticks, look; private String from = "";
        EscapeLiquid() { setFlags(EnumSet.of(Flag.MOVE)); }
        private boolean needed() {
            if (downed() || isPassenger()) return false;
            if (isInLava()) return true;
            if (!isInWater()) return false;
            if (getAirSupply() < getMaxAirSupply() / 3) return true;                // running out of air
            ServerPlayer o = owner();
            if (o != null && o.level() == level() && o.isInWater() && o.distanceToSqr(PartyBotEntity.this) < 16 * 16) return false;   // swimming with the owner
            LivingEntity t = getTarget();
            if (t != null && t.isInWater()) return false;                           // fighting something in the water
            return wetTicks > 40;                                                   // otherwise no standing about in it
        }
        @Override public boolean canUse() {
            if (!needed() || tickCount - look < 8) return false;
            look = tickCount;
            spot = BotMoves.dryLand(PartyBotEntity.this, isInLava() ? 8 : 12);
            return spot != null || isInLava();
        }
        @Override public boolean canContinueToUse() { return (isInWater() || isInLava() || !onGround()) && ticks < 400 && !downed() && !isPassenger(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { ticks = 0; from = isInLava() ? "lava" : "water"; getNavigation().stop(); }
        @Override public void stop() {
            BotMoves.land(PartyBotEntity.this);
            lastEscape = from + (isInWater() || isInLava() ? " still in after " : " out after ") + ticks + " ticks";
        }
        @Override public void tick() {
            ticks++;
            boolean lava = isInLava();
            if (spot == null || ticks % 40 == 0) { BlockPos s2 = BotMoves.dryLand(PartyBotEntity.this, lava ? 8 : 12); if (s2 != null) spot = s2; }
            if (spot != null) {
                Vec3 at = Vec3.atBottomCenterOf(spot);
                if (canFly()) {
                    if (!lava && !isInWater() && position().distanceToSqr(at) < 1.5 * 1.5) BotMoves.land(PartyBotEntity.this);
                    else BotMoves.flyTo(PartyBotEntity.this, at.add(0, 0.6, 0), lava ? 0.7 : 0.5);
                } else {
                    if (ticks % 10 == 1) getNavigation().moveTo(at.x, at.y, at.z, 1.4);
                    if (isInWater() || isInLava()) { BotMoves.swimToward(PartyBotEntity.this, spot, lava); getJumpControl().jump(); }
                }
            } else getJumpControl().jump();
            // the safety net: still in lava after 3 seconds (or water after 15): out onto the land, or over to the owner
            if ((lava && lavaTicks > 60) || (!lava && isInWater() && ticks > 300)) {
                if (spot != null) teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                else if (owner() != null && owner().level() == level()) PartyBots.teleportNear(PartyBotEntity.this, owner());
                from = from + " (lifted out)";
                lavaTicks = 0;
            }
        }
    }

    /** Now and then a flier takes a turn about the sky: up off the ground, a lap or two around its owner, and back down. */
    class FlyAbout extends Goal {
        private int t, dur; private double ang, turn, radius = 4.5; private boolean landing;
        public FlyAbout() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        private boolean calm() {
            ServerPlayer o = owner();
            return o != null && o.level() == level() && canFly() && !downed() && getTarget() == null && mineTarget == null && reviving == null && !isPassenger() && !isVehicle()
                    && o.getVehicle() == null && !sparring() && !journey() && mode() != MINING && !isInLava() && !PartyBots.airborne(o) && !o.getAbilities().flying && eatTicks < 0 && drinkTicks < 0;
        }
        private boolean openSky() {
            for (int i = 1; i <= 7; i++) if (!level().getBlockState(blockPosition().above(i)).getCollisionShape(level(), blockPosition().above(i)).isEmpty()) return false;
            return true;
        }
        @Override public boolean canUse() {
            if (tickCount < nextFlourish || !calm()) return false;
            if (distanceToSqr(owner()) > 10 * 10 || !openSky()) { nextFlourish = tickCount + 200; return false; }   // (indoors, underground: not now)
            nextFlourish = tickCount + 900 + getRandom().nextInt(1500);           // (every 45 s - 2 min or so)
            return true;
        }
        @Override public boolean canContinueToUse() {
            ServerPlayer o = owner();
            double far = leash() + 6;
            return calm() && distanceToSqr(o) < far * far && t < dur + 120 && !(landing && onGround() && t > 5);
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() {
            ServerPlayer o = owner();
            t = 0; dur = 120 + getRandom().nextInt(80); landing = false;
            ang = Math.atan2(getZ() - o.getZ(), getX() - o.getX());
            radius = leash() > 8 ? 7 + getRandom().nextDouble() * 12 : 4.5;    // (a wide loop out over the area, when nothing's up)
            turn = (getRandom().nextBoolean() ? 0.045 : -0.045) * 4.5 / radius * 1.6;
            flourishes++; flourishPeak = 0; flourishFar = 0;
            ((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 8, 0.3, 0.05, 0.3, 0.05);
        }
        @Override public void stop() { BotMoves.land(PartyBotEntity.this); }
        @Override public void tick() {
            ServerPlayer o = owner();
            if (o == null) return;
            t++;
            flourishPeak = Math.max(flourishPeak, getY() - o.getY());
            flourishFar = Math.max(flourishFar, Math.sqrt(distanceToSqr(o)));
            if (!landing && t >= dur) landing = true;
            if (!landing) {
                ang += turn;
                double h = Math.min(5.0, t * 0.1) + Math.sin(t * 0.08) * 0.6;
                Vec3 goal = o.position().add(Math.cos(ang) * radius, h, Math.sin(ang) * radius);
                if (!level().noCollision(PartyBotEntity.this, getBoundingBox().move(goal.subtract(position())))) goal = goal.add(0, 1.5, 0);
                BotMoves.flyTo(PartyBotEntity.this, goal, 0.42);
            } else {                                                            // back down beside them
                Vec3 spot = o.position().add(Math.cos(ang) * 2.5, 0.2, Math.sin(ang) * 2.5);
                if (position().subtract(spot).horizontalDistanceSqr() < 1.0 || BotMoves.heightAboveGround(PartyBotEntity.this) < 0.8 || t > dur + 100) BotMoves.land(PartyBotEntity.this);
                else BotMoves.flyTo(PartyBotEntity.this, spot, 0.35);
            }
        }
    }
    public int flourishes; public double flourishPeak, flourishFar;

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
        private int stalled, going, warpReady; private double closest;
        @Override public boolean requiresUpdateEveryTick() { return true; }     // (its timers count real ticks)
        @Override public void start() { stalled = 0; going = 0; closest = 1e9; }
        @Override public void stop() { reviving = null; }
        @Override public void tick() {
            if (reviving == null) return;
            setTarget(null);
            getLookControl().setLookAt(reviving);
            double d2 = distanceToSqr(reviving);
            // Rhalix warps straight to them (30 Karma) and shields them both while he revives
            if (character() == CharacterType.RHALIX && karma >= com.xenowarriors.party.ReviveWarp.COST && tickCount >= warpReady
                    && d2 > com.xenowarriors.party.ReviveWarp.BOT_MIN_DIST * com.xenowarriors.party.ReviveWarp.BOT_MIN_DIST
                    && d2 <= com.xenowarriors.party.ReviveWarp.RANGE * com.xenowarriors.party.ReviveWarp.RANGE) {
                warpReady = tickCount + 100;
                if (com.xenowarriors.party.ReviveWarp.warp(PartyBotEntity.this, reviving)) { getNavigation().stop(); return; }
            }
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
    public void setTarget(LivingEntity t) {
        if (t != null && !level().isClientSide && PartyBots.friendly(this, t) && !(sparring() && t == owner())) t = null;   // (never its own side: the owner, the party, their pets, bots and souls)
        super.setTarget(untouchable(t) ? null : t);
    }

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
        int dig, need, lastLook, lastTreeLook = -1000, walking, lastGatherLook = -1000;
        double closest;
        @Override public boolean requiresUpdateEveryTick() { return true; }     // (digging and its timers count real ticks)
        MineGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            // (the goal selector asks every other tick on its own clock: a tickCount % 20 test could never line up)
            if (downed() || getTarget() != null || tickCount - lastLook < 10) return false;
            lastLook = tickCount;
            ServerPlayer o = owner();
            if (o == null || o.level() != level() || !com.xenowarriors.modes.ModeRules.canGrief(level())) return false;
            if (mode() != MINING) {                                             // not mining: only ore it's short of for an upgrade it has in mind, close by
                if (tickCount - lastGatherLook < 100 || errand != null || journey()) return false;
                lastGatherLook = tickCount;
                mineTarget = BotCrafting.gatherTarget(PartyBotEntity.this, o);
                gathering = mineTarget != null;
                return gathering;
            }
            gathering = false;
            mineTarget = PartyBots.findOre(PartyBotEntity.this, o);
            if (mineTarget == null && tickCount - lastTreeLook >= 40) {         // no ore: a tree (looked for less often - it's a bigger search)
                lastTreeLook = tickCount;
                mineTarget = BotGear.findTree(PartyBotEntity.this, o);
            }
            return mineTarget != null;
        }
        private boolean breakable(BlockState s) { return PartyBots.isOre(s) || BotGear.isLog(s); }
        @Override public boolean canContinueToUse() {
            if (mineTarget == null || downed() || getTarget() != null || !breakable(level().getBlockState(mineTarget))) return false;
            if (mode() == MINING) return true;
            ServerPlayer o = owner();                                           // (getting ore for a craft: never in a fight or far from its owner)
            return gathering && o != null && o.level() == level() && o.distanceToSqr(PartyBotEntity.this) < 20 * 20 && (tickCount % 10 != 0 || BotErrands.danger(PartyBotEntity.this) == null);
        }
        @Override public void start() { dig = 0; need = 0; walking = 0; closest = 1e9; }
        @Override public void stop() { if (mineTarget != null) level().destroyBlockProgress(getId(), mineTarget, -1); mineTarget = null; gathering = false; }
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
                if (gathering) { gathered++; PartyBots.testLog(PartyBotEntity.this, "gather: mined " + net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(st.getBlock()).getPath() + " (" + gathered + " so far)"); }
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
        if (pendingHp >= 0 && tickCount > 200) pendingHp = -1;                  // (what didn't come back by now stays as it is)
        if (downed()) {
            if (++downedTicks > PartyBots.BLEED_OUT) { PartyBots.bleedOut(this); return; }
            if (tickCount % 10 == 0) ((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 0.5, getZ(), 1, .3, .1, .3, 0);
            if (!journey()) com.xenowarriors.party.ReviveCircle.tickBot(this);
            return;
        }
        if (getTarget() != null && tickCount % 10 == 0) { if (!rangedActive) BotGear.holdBestWeapon(this); PartyBots.trySkill(this, getTarget()); }
        if (tickCount % 10 == 0) pickUpItems();
        tickEating();
        BotPotions.tick(this);
        BotShield.tick(this);
        if (tickCount % 20 == 3) BotErrands.absorbTick(this);
        if (tickCount % 20 == 11) BotSupport.tick(this);                         // (torches in the dark; Kaeru's Fox Heal)
        tickMoves();
        if (tickCount % 40 == 0) PartyBots.wearBestArmour(this);
        if (tickCount % 20 == 0) BotGear.tickIdle(this);                         // (puts its weapon away after a fight; crafting ideas)
        if (tickCount % 20 == 15) BotBags.tick(this);                            // (keeps its things tidy when it's idle)
        ServerPlayer o = owner();
        if (o == null && ownerId() == null) { if (tickCount > 100 && !isNoAi()) { PartyBots.forgetFake(getUUID()); discard(); } return; }   // (nobody's bot, e.g. summoned by command: it goes; a no-AI display one stays)
        if (o == null && tickCount > 100 && tickCount % 40 == 0) { if (!journey()) PartyBots.LEFT_BEHIND.add(java.util.Map.entry(ownerId(), record())); PartyBots.forgetFake(getUUID()); discard(); }   // owner gone: kept until they're back
        else if (o != null && o.level() != level() && tickCount % 20 == 0 && !journey()) {   // owner in another world (respawned, portal): follow, or wait
            if (PartyBots.canFollowTo(character(), o.level())) PartyBots.teleportNear(this, o);
            else { PartyBots.LEFT_BEHIND.add(java.util.Map.entry(ownerId(), record())); PartyBots.forgetFake(getUUID()); discard(); }
        }
    }

    /** Getting about: gap jumps, hopping up steps, time in water/lava, no hovering left over from a flight. */
    private void tickMoves() {
        wetTicks = isInWater() ? wetTicks + 1 : 0;
        lavaTicks = isInLava() ? lavaTicks + 1 : 0;
        if (leapTicks > 0) leapTicks--;
        if (selfFlying && tickCount - flewAt > 5) BotMoves.land(this);           // (whatever was flying it has stopped)
        if (selfFlying || isPassenger() || downed() || isInWater() || isInLava()) return;
        BotMoves.stepUp(this);
        // a gap between it and where it's going: a running jump across (if there's somewhere to land)
        if (tickCount % 4 == 0 && onGround() && leapTicks == 0) {
            Vec3 goal = null;
            LivingEntity t = getTarget();
            ServerPlayer o = owner();
            if (t != null && reviving == null) goal = t.position();
            else if (reviving != null) goal = reviving.position();
            else if (mineTarget != null) goal = Vec3.atBottomCenterOf(mineTarget);
            else if (o != null && o.level() == level() && distanceToSqr(o) > 6 * 6 && o.onGround()) goal = o.position();
            if (goal != null && Math.abs(goal.y - getY()) < 2.5 && (getNavigation().isDone() || getDeltaMovement().horizontalDistanceSqr() < 0.0004)) BotMoves.tryLeap(this, goal);
        }
    }

    /** Never walks off a cliff (4+ blocks down, no water at the bottom) unless it can fly - knocked back or not. */
    @Override
    public void move(MoverType type, Vec3 v) {
        if (!level().isClientSide && (type == MoverType.SELF || type == MoverType.PLAYER) && onGround() && leapTicks == 0 && !isNoGravity() && !canFly()
                && !isInWater() && !isInLava() && !isPassenger() && BotMoves.cliffAhead(this, v)) {
            v = new Vec3(0, v.y, 0);
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (++edgeStops % 20 == 1) getNavigation().stop();
        }
        super.move(type, v);
    }

    /** How far a path may drop it: never a cliff on foot (whatever it's chasing); fliers just fly down. */
    @Override public int getMaxFallDistance() { return canFly() ? 16 : 3; }

    /** Fliers catch themselves before they land. */
    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource src) {
        if (canFly() && !downed()) { fallDistance = 0; return false; }
        return super.causeFallDamage(dist, mult, src);
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
                heal(Math.max(2F, heal) * (1F + com.xenowarriors.stats.Appetite.bonus(this)));   // (its Appetite level: food does more, like for a player)
                if (props != null) com.xenowarriors.stats.Appetite.addXp(this, props.getNutrition());
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
        if (tickCount % 30 != 0 || getHealth() > getMaxHealth() * 0.6F || getTarget() != null || sparring() || drinking()) return;
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
            if (it.getItem().getItem() instanceof com.xenowarriors.statboost.StatBoostItem) continue;   // (Stat Boosts are yours: it only uses the ones you give it)
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
        if (r) { lastHurtAt = tickCount; pendingHp = -1; }
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
        if (!level().isClientSide && !journey()) { PartyBots.checkIn(this, owner()); com.xenowarriors.party.PartySwap.botLost(owner(), character()); }   // (the void, /kill: their things go home first)
        super.die(src);
    }

    /**
     * Health from a saved record (relog, re-add, back from a Journey) that its max health couldn't hold yet - its bond
     * hearts come from teammates (the owner's other bots) who spawn a moment after it. It gets that health back as its
     * max health rises, in its first 10 seconds and until it's hurt. -1: none.
     */
    float pendingHp = -1;
    /** It came back from a saved record (its health is the saved one, not a fresh full bar). */
    boolean loadedRecord;

    /** Its max health just changed (bond hearts, DragonMineZ health): keep its health in range, give back saved health, top up a brand-new bot. */
    public void maxHealthChanged(float before) {
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        if (pendingHp >= 0) {
            if (getHealth() < pendingHp) setHealth(Math.min(pendingHp, getMaxHealth()));
            if (getHealth() >= pendingHp - 0.01F) pendingHp = -1;
        } else if (tickCount < 5 && !loadedRecord && before < getMaxHealth()) setHealth(getMaxHealth());   // (just out, fresh: at full health)
    }

    /** (Tests) saved health still waiting to be given back. */
    public float pendingHpForTest() { return pendingHp; }

    public void revive() {
        entityData.set(DOWNED, false); setHealth(getMaxHealth() * 0.4F); downedTicks = 0; com.xenowarriors.party.ReviveCircle.forget(getUUID());
        ReviveAftermath.revived(this);                                          // (10 s invincible, and a shockwave that stuns the enemies about)
    }

    @Override public boolean isPushable() { return !downed() && super.isPushable(); }

    /** Standing in someone's revive circle: it holds its ground (hits don't knock it out of the circle). */
    @Override
    public void knockback(double strength, double x, double z) {
        if (reviving != null && !downed() && distanceToSqr(reviving) < com.xenowarriors.party.ReviveCircle.RADIUS * com.xenowarriors.party.ReviveCircle.RADIUS) return;
        super.knockback(strength, x, z);
    }
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

    /** A blow stopped by its shield: the shield wears, as a player's does (and breaks when it's done). */
    @Override
    protected void hurtCurrentlyUsedShield(float amount) {
        shieldBlocks++;
        PartyBots.testLog(this, "shield blocked " + String.format(java.util.Locale.ROOT, "%.1f", amount) + " damage (blocks " + shieldBlocks + ")");
        ItemStack s = getUseItem();
        if (amount < 3F || !BotErrands.isShield(s) || !s.isDamageableItem()) return;
        InteractionHand hand = getUsedItemHand();
        s.hurtAndBreak(1 + net.minecraft.util.Mth.floor(amount), this, e -> e.broadcastBreakEvent(hand));
        if (s.isEmpty()) { setItemSlot(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, ItemStack.EMPTY); stopUsingItem(); }
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
        if (isNoGravity() && !isVehicle() && !flyingWithOwner && !selfFlying) setNoGravity(false);
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
        t.put("Inv", inventory.bagTag());
        t.put("XInv", inventory.extraTag());
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
        if (t.contains("Inv")) inventory.loadBag(t.getList("Inv", 10));
        if (t.contains("XInv")) { inventory.clearExtra(); inventory.loadExtra(t.getList("XInv", 10)); }
        if (t.contains("Armour")) loadArmour(t.getList("Armour", 10));
    }

    /** The bot as a record kept with the owner while they're offline. */
    public CompoundTag record() {
        CompoundTag t = new CompoundTag();
        t.putString("char", entityData.get(CHAR));
        t.putInt("mode", mode());
        t.putFloat("hp", getHealth());
        t.putFloat("karma", karma);
        t.put("inv", inventory.bagTag());
        ListTag x = inventory.extraTag();
        if (!x.isEmpty()) t.put("xbag", x);                                      // (its extra tabs: only while it's a bot - see BotBags)
        t.put("armour", armourTag());
        if (getPersistentData().contains("xw_inv_kept")) t.put("kept", getPersistentData().getCompound("xw_inv_kept"));   // (that character's ender chest and XP)
        return t;
    }

    public void loadRecord(CompoundTag t) {
        entityData.set(MODE, t.getInt("mode"));
        if (t.contains("inv")) inventory.loadBag(t.getList("inv", 10));
        if (t.contains("xbag")) { inventory.clearExtra(); inventory.loadExtra(t.getList("xbag", 10)); }
        if (t.contains("armour")) loadArmour(t.getList("armour", 10));
        if (t.contains("hp")) {
            float raw = t.getFloat("hp"), hp = Float.isFinite(raw) ? Math.max(1F, raw) : getMaxHealth();   // (a broken number never becomes its health)
            setHealth(hp);
            if (hp > getHealth() + 0.01F) pendingHp = hp;  // (more than its max holds yet: a bond partner isn't back yet - see maxHealthChanged)
            loadedRecord = true;
        }
        if (t.contains("kept")) getPersistentData().put("xw_inv_kept", t.getCompound("kept"));
        if (t.contains("karma")) { float k = t.getFloat("karma"); if (Float.isFinite(k)) karma = Math.max(0F, Math.min(100F, k)); }
    }
}
