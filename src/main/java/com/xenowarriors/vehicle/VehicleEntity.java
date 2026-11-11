package com.xenowarriors.vehicle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * A vehicle: car, motorbike, helicopter or armored truck. Right-click to get in (the first seat drives), W/S to
 * speed up and brake/reverse, A/D to steer; the helicopter climbs with jump and comes down with the sprint key, and
 * turns where you look. Sneak + right-click (the owner) puts it back in your inventory. Government trucks are driven
 * by guards on patrol - get in and the guard's thrown out (and you're wanted).
 */
public class VehicleEntity extends Mob implements GeoEntity {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public final VehicleKind kind;
    private UUID owner;
    /** Current speed along the facing (blocks/tick, negative = reversing), and the helicopter's climb rate. */
    private double speed, climb;
    // client: wheel and rotor angles for the renderer
    public float wheelRot, wheelRotO, rotorRot, rotorRotO, rotorSpin;
    // trucks: the guard's patrol
    private Vec3 home, patrolTarget;

    public VehicleEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.kind = Vehicles.kindOf(type);
        setMaxUpStep(kind.base() == VehicleKind.MOTORBIKE ? 1.0F : 1.1F);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes(VehicleKind k) {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, k.health).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.ARMOR, k.base() == VehicleKind.GOV_TRUCK ? 14 : 6);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(VARIANT, 0); }

    public int variant() { return entityData.get(VARIANT); }
    public void setVariant(int v) { entityData.set(VARIANT, Math.floorMod(v, kind.variants.length)); }
    public void setOwner(UUID u) { owner = u; }
    public double speed() { return speed; }

    @Override protected void registerGoals() {}
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity e) {}
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override protected boolean canRide(Entity e) { return false; }
    /** Client: the rider being drawn right now (so a rider standing on top or hanging on isn't drawn sitting). */
    public static Entity renderingRider;

    @Override public boolean shouldRiderSit() {
        Entity r = renderingRider;
        return r == null || r.getVehicle() != this || seatOf(r).type() == VehicleKind.SeatType.SIT;
    }

    /** The spot this passenger has (by boarding order). */
    public VehicleKind.Seat seatOf(Entity passenger) {
        return kind.seat(Math.max(0, getPassengers().indexOf(passenger)));
    }

    /** Who got off last and where they were (the dismount spot is asked for after they're off). */
    private Entity lastLeaver;
    private VehicleKind.SeatType lastLeaverSeat;

    @Override
    protected void removePassenger(Entity p) {
        lastLeaver = p;
        lastLeaverSeat = seatOf(p).type();
        super.removePassenger(p);
    }

    /** Hanging on the skids: dangling in the air, or (helicopter on the ground) standing beside it holding on. */
    public boolean dangling() { return groundBelow() < 0; }

    private int groundTick = -1;
    private double ground;

    /**
     * How far down the ground is (0 to 2.2 blocks), or -1 if it's further than that (hangers dangle). Not onGround: a
     * helicopter its pilot has set down can sit a hair above the ground (no gravity while driven), never "on" it.
     */
    public double groundBelow() {
        if (groundTick == tickCount) return ground;
        groundTick = tickCount;
        if (onGround() || isInWater()) return ground = 0;
        Vec3 a = position().add(0, 0.05, 0);
        var hit = level().clip(new net.minecraft.world.level.ClipContext(a, a.subtract(0, 2.25, 0), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.ANY, this));
        return ground = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? -1 : Math.max(0, a.y - 0.05 - hit.getLocation().y);
    }
    @Override protected boolean canAddPassenger(Entity e) { return getPassengers().size() < kind.seats; }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public double getPassengersRidingOffset() { return 0.0; }

    @Override
    public LivingEntity getControllingPassenger() {
        Entity first = getFirstPassenger();
        if (first instanceof Player p) return p;
        if (first instanceof com.xenowarriors.gov.GovAgentEntity g) return g;
        return null;
    }

    private boolean guardDriving() { return getFirstPassenger() instanceof com.xenowarriors.gov.GovAgentEntity; }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        VehicleKind.Seat s = seatOf(passenger);
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);               // forward
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);               // right
        double sx = s.x(), sy = s.y();
        switch (s.type()) {
            case SIT -> sy += passenger.getMyRidingOffset();
            case HANG -> { if (!dangling()) { sy = -groundBelow(); sx += Math.signum(sx) * 0.25; } }    // (landed: standing beside it on the ground, holding on)
            default -> {}                                                                  // standing on top: feet on the roof
        }
        double x = getX() + fx * s.z() + rx * sx, z = getZ() + fz * s.z() + rz * sx;
        move.accept(passenger, x, getY() + sy, z);
        if (passenger instanceof LivingEntity le && passenger != getControllingPassenger()) le.yBodyRot = getYRot();
        passenger.fallDistance = 0;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        // hanging off a helicopter in the air: just let go (drop from where you are, if there's room)
        if (e == lastLeaver && lastLeaverSeat == VehicleKind.SeatType.HANG && dangling()
                && level().noCollision(e, e.getBoundingBox())) return e.position();
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double side = kind.width / 2 + 0.6, back = kind.width / 2 + 1.0;
        Vec3[] tries = {position().add(Mth.cos(yaw) * side, 0.2, Mth.sin(yaw) * side), position().add(-Mth.cos(yaw) * side, 0.2, -Mth.sin(yaw) * side),
                position().add(Mth.sin(yaw) * back, 0.2, -Mth.cos(yaw) * back), position().add(0, getBbHeight() + 0.1, 0)};
        for (Vec3 c : tries) {                                              // the first spot with room to stand (never inside a wall)
            Vec3 ok = net.minecraft.world.entity.vehicle.DismountHelper.findSafeDismountLocation(e.getType(), level(), net.minecraft.core.BlockPos.containing(c), true);
            if (ok != null) return ok;
        }
        return super.getDismountLocationForPassenger(e);
    }

    @Override
    protected InteractionResult mobInteract(Player p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (p.isSecondaryUseActive()) {                                   // pack it up
            if (!p.isCreative() && ((owner != null && !owner.equals(p.getUUID())) || (owner == null && (kind.base() == VehicleKind.GOV_TRUCK || com.xenowarriors.raid.FactionRaids.isRaider(this))))) {   // (nor a raid's vehicles)   // (a seized Gov truck can be wrecked for iron, not packed up)
                p.displayClientMessage(Component.literal("That's not your " + kind.title.toLowerCase() + ".").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            if (!getPassengers().isEmpty()) return InteractionResult.CONSUME;
            ItemStack it = VehicleItem.make(kind, variant());
            if (!p.addItem(it)) spawnAtLocation(it);
            discard();
            return InteractionResult.CONSUME;
        }
        if (guardDriving() && getFirstPassenger() instanceof com.xenowarriors.gov.GovAgentEntity g) {      // stealing it
            g.stopRiding();
            g.setTarget(p);
            com.xenowarriors.gov.GovFacilities.makeWanted(p, "stealing a Government truck");
        }
        if (canAddPassenger(p)) {
            p.startRiding(this);
            if (getFirstPassenger() == p) p.displayClientMessage(Component.literal(kind.heli()
                    ? "W/S speed, look to turn, Jump to climb, Sprint key to descend, Sneak to get out"
                    : kind.hovers() ? "W/S speed, A/D steer, Jump to fly up, Sprint key to come down, Sneak to get out"
                    : "W/S speed and brake, A/D steer, Sneak to get out").withStyle(ChatFormatting.GRAY), true);
            else if (p.getVehicle() == this && seatOf(p).type() != VehicleKind.SeatType.SIT)
                p.displayClientMessage(Component.literal(seatOf(p).type() == VehicleKind.SeatType.HANG
                        ? "No seats left - you're hanging on to the rail (Sneak to let go)" : "No seats left - you're riding on top (Sneak to get off)")
                        .withStyle(ChatFormatting.GRAY), true);
        } else if (getPassengers().size() >= kind.seats)
            p.displayClientMessage(Component.literal("It's full.").withStyle(ChatFormatting.GRAY), true);
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ driving (runs on the driver's client)

    @Override
    protected void tickRidden(Player p, Vec3 input) {
        super.tickRidden(p, input);
        // The server never moves a player-driven vehicle (the driver's game does), so it never saw the no-gravity flag
        // the driver's side sets while flying - and vanilla kicks a driver "floating a vehicle too long" (4 s) on any
        // server without allow-flight (a dedicated server's default) unless the vehicle has no gravity there.
        if (!level().isClientSide && (kind.heli() || kind.hovers()) && !isNoGravity()) setNoGravity(true);
        double fwd = p.zza, side = p.xxa;
        double target = fwd > 0 ? kind.maxSpeed : fwd < 0 ? -kind.maxSpeed * 0.4 : 0;
        double a = fwd == 0 ? kind.accel * 0.6 : (Math.signum(target - speed) != Math.signum(speed) && speed != 0 ? kind.accel * 2.2 : kind.accel);
        speed += Mth.clamp(target - speed, -a, a);
        // Only the side that moves it (the driver's game) turns it: the server gets the driver's yaw with the position, and
        // turning it again here made it face further round than it was going (and jitter for everyone watching).
        boolean local = isControlledByLocalInstance();
        if (kind.heli()) {
            if (local) setYRot(Mth.rotLerp(0.12F, getYRot(), p.getYRot()));
            double want = level().isClientSide ? (VehicleControls.up ? 0.32 : VehicleControls.down ? -0.32 : 0) : climb;
            climb += Mth.clamp(want - climb, -0.04, 0.04);
        } else {
            if (kind.hovers() && level().isClientSide) {
                // Mk II: Jump lifts it off and climbs, the Sprint key brings it down; in the air it holds its height
                if (VehicleControls.up && !hovering) hovering = true;
                if (hovering && onGround() && !VehicleControls.up && climb <= 0) hovering = false;      // (set down: drives again)
                double want = !hovering ? 0 : VehicleControls.up ? 0.3 : VehicleControls.down ? -0.3 : 0;
                climb += Mth.clamp(want - climb, -0.04, 0.04);
            }
            float grip = hovering ? 1F : (float) Math.min(1.0, Math.abs(speed) / 0.15);
            if (local) setYRot(getYRot() - (float) side * (kind.base() == VehicleKind.MOTORBIKE ? 4.5F : kind.base() == VehicleKind.GOV_TRUCK ? 2.4F : 3.4F) * grip * (speed < 0 ? -1 : 1));
        }
        yBodyRot = getYRot(); yHeadRot = getYRot();
        setXRot(0);
    }

    /** Mk II land vehicles: flying right now (driver's side). */
    private boolean hovering;

    /** Always faces where it's going: no body turning on its own like a mob's (that's what drew it sideways). */
    @Override
    protected float tickHeadTurn(float yaw, float amount) {
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        return amount;
    }

    /** In the air in a Mk II car, bike or truck (or a helicopter): drawn tilting with its motion. */
    public boolean airborne() {
        return (kind.heli() || kind.hovers()) && !onGround() && groundBelow() != 0;
    }

    // client: how far it's tilted (degrees; pitch + is nose down, roll + is right side down) for the renderer
    public float pitchTilt, pitchTiltO, rollTilt, rollTiltO;

    private void tilt() {
        pitchTiltO = pitchTilt; rollTiltO = rollTilt;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double hs = (getX() - xo) * -Mth.sin(yaw) + (getZ() - zo) * Mth.cos(yaw), dy = getY() - yo;
        float turn = Mth.wrapDegrees(getYRot() - yRotO);
        float pitch, roll;
        if (airborne()) {
            pitch = (float) Mth.clamp(hs * 22, -10, 16) - (float) Mth.clamp(dy * 20, -6, 6);     // nose down flying forward, up when climbing
            roll = Mth.clamp(turn * 2.2F, -22F, 22F);                                             // banks into turns
        } else {
            // up a step or a slope: nose up (rear up when reversing up it)
            pitch = Math.abs(hs) > 0.04 && Math.abs(dy) < 1.2 ? Mth.clamp((float) -Math.toDegrees(Math.atan(dy / hs)), -20F, 20F) : 0;
            roll = kind.base() == VehicleKind.MOTORBIKE ? Mth.clamp(turn * 2.5F, -14F, 14F) : Mth.clamp(turn * 0.5F, -3F, 3F);   // the bike leans, cars sway a little
        }
        pitchTilt += (pitch - pitchTilt) * 0.2F;
        rollTilt += (roll - rollTilt) * 0.2F;
    }

    @Override protected Vec3 getRiddenInput(Player p, Vec3 in) { return Vec3.ZERO; }
    @Override protected float getRiddenSpeed(Player p) { return 0F; }

    private boolean driven() { return getControllingPassenger() != null; }

    @Override
    public void travel(Vec3 in) {
        if (!driven()) hovering = false;
        if (!driven() && !kind.heli()) { setNoGravity(false); speed *= 0.9; super.travel(Vec3.ZERO); return; }
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        Vec3 v = getDeltaMovement();
        double y;
        if (kind.heli()) {
            if (driven()) { setNoGravity(true); y = climb; }
            else { setNoGravity(false); speed *= 0.95; y = onGround() ? 0 : Math.max(v.y - 0.04, -0.18); }    // autorotates down gently
        } else if (hovering) {
            setNoGravity(true);                                          // Mk II flying: holds its height, Jump/Sprint key to climb/descend
            y = climb;
        } else {
            setNoGravity(false);
            y = onGround() ? -0.08 : Math.max(v.y - 0.08, -1.5) * 0.98;
            if (isInWater()) { speed *= 0.9; y = Math.min(0.02, y + 0.1); }
        }
        Vec3 move = new Vec3(fx * speed, y, fz * speed);
        move(MoverType.SELF, move);
        if (horizontalCollision && Math.abs(speed) > 0.35) {                     // crash
            if (!level().isClientSide) level().playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.5F, 0.7F);
            speed *= 0.3;
        } else if (horizontalCollision) speed *= 0.6;
        setDeltaMovement(move.x, onGround() && !kind.heli() && !hovering ? 0 : y, move.z);
        calculateEntityAnimation(false);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void aiStep() {
        if (!level().isClientSide && guardDriving() && !com.xenowarriors.raid.RaidPilot.drive(this)) patrol();      // (a faction raid's vehicles drive themselves at their target)
        super.aiStep();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            wheelRotO = wheelRot; rotorRotO = rotorRot;
            double hs = (getX() - xo) * -Mth.sin(getYRot() * Mth.DEG_TO_RAD) + (getZ() - zo) * Mth.cos(getYRot() * Mth.DEG_TO_RAD);
            wheelRot += (float) (hs / ((kind.base() == VehicleKind.GOV_TRUCK ? 0.44 : 0.33) * kind.scale));
            tilt();
            float targetSpin = driven() || !onGround() ? 1.6F : 0F;
            rotorSpin += (targetSpin - rotorSpin) * 0.03F;
            rotorRot += rotorSpin;
            VehicleFx.tick(this);
        } else {
            if (driven() && tickCount % (kind.heli() ? 5 : 9) == 0) {
                if (kind.heli())
                    level().playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 0.7F, 0.45F);
                else
                    level().playSound(null, blockPosition(), SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, 0.12F,
                            (kind.base() == VehicleKind.GOV_TRUCK ? 0.4F : kind.base() == VehicleKind.MOTORBIKE ? 0.9F : 0.6F) + (float) Math.abs(getDeltaMovement().horizontalDistance()) * 0.8F);
            }
        }
    }

    /**
     * A Government guard driving the truck: slow loops around its post. Someone wanted nearby: it stops and the guards
     * in it shoot from their seats; once they're right up close, the guards get out to fight.
     */
    private void patrol() {
        if (home == null) home = position();
        var g = (com.xenowarriors.gov.GovAgentEntity) getFirstPassenger();
        Player threat = level().getNearestPlayer(this, 20);
        if (threat != null && com.xenowarriors.gov.GovFacilities.wanted(threat) && !threat.isCreative() && !threat.isSpectator()) {
            speed += Mth.clamp(-speed, -0.04, 0.04);                       // brakes
            boolean close = distanceTo(threat) < kind.width / 2 + 3;
            for (Entity e : new java.util.ArrayList<>(getPassengers()))
                if (e instanceof com.xenowarriors.gov.GovAgentEntity a) {
                    if (close) a.stopRiding();
                    a.setTarget(threat);
                }
            return;
        }
        if (patrolTarget == null || patrolTarget.distanceToSqr(position()) < 9 || random.nextInt(600) == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = 8 + random.nextDouble() * 14;
            patrolTarget = home.add(Math.cos(a) * r, 0, Math.sin(a) * r);
        }
        Vec3 to = patrolTarget.subtract(position());
        float want = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F;
        setYRot(Mth.approachDegrees(getYRot(), want, 3F));
        yBodyRot = getYRot(); yHeadRot = getYRot();
        speed += Mth.clamp(0.12 - speed, -0.01, 0.01);
    }

    /**
     * An AI driver that isn't on patrol (a faction raid's convoys and helicopters, see raid.RaidPilot): turn toward a
     * heading (at most {@code turn} degrees a tick), ease toward a speed and (flying) a climb rate.
     */
    public void autopilot(float yaw, float turn, double wantSpeed, double wantClimb) {
        setYRot(Mth.approachDegrees(getYRot(), yaw, turn));
        yBodyRot = getYRot(); yHeadRot = getYRot();
        double a = kind.accel * 2;
        speed += Mth.clamp(wantSpeed - speed, -a, a);
        climb += Mth.clamp(wantClimb - climb, -0.04, 0.04);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || isRemoved() || isInvulnerableTo(src)) return false;   // (already wrecked this tick: a second hit drops no second item)
        if (src.getEntity() != null && hasPassenger(src.getEntity())) return false;          // (no shooting your own ride)
        if (com.xenowarriors.raid.FactionRaids.sameRaid(this, src.getEntity())) return false;   // (nor your own raid's)
        float hp = getHealth() - amount * (src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) ? 1.5F : 0.6F);
        if (hp > 0) { setHealth(hp); level().playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_HURT, SoundSource.NEUTRAL, 0.5F, 1.4F); return true; }
        // wrecked: it bursts into smoke and drops back into its item (bought vehicles aren't lost for good)
        ejectPassengers();
        ((ServerLevel) level()).sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 0.8, 0.6, 0.8, 0.02);
        ((ServerLevel) level()).sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 3, 0.5, 0.3, 0.5, 0);
        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.8F, 1.2F);
        if (owner != null || (kind.base() != VehicleKind.GOV_TRUCK && !com.xenowarriors.raid.FactionRaids.isRaider(this))) spawnAtLocation(VehicleItem.make(kind, variant()));
        else spawnAtLocation(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 6));
        discard();
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Variant", variant());
        if (owner != null) t.putUUID("Owner", owner);
        if (home != null) { t.putDouble("HomeX", home.x); t.putDouble("HomeY", home.y); t.putDouble("HomeZ", home.z); }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        setVariant(t.getInt("Variant"));
        if (t.hasUUID("Owner")) owner = t.getUUID("Owner");
        if (t.contains("HomeX")) home = new Vec3(t.getDouble("HomeX"), t.getDouble("HomeY"), t.getDouble("HomeZ"));
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
