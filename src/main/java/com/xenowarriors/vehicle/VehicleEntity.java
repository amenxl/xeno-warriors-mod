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
        setMaxUpStep(kind == VehicleKind.MOTORBIKE ? 1.0F : 1.1F);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes(VehicleKind k) {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, k.health).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.ARMOR, k == VehicleKind.GOV_TRUCK ? 14 : 6);
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
    @Override public boolean shouldRiderSit() { return true; }
    @Override protected boolean canAddPassenger(Entity e) { return getPassengers().size() < kind.seats; }
    @Override public boolean isPickable() { return !isRemoved(); }

    @Override
    public LivingEntity getControllingPassenger() {
        Entity first = getFirstPassenger();
        if (first instanceof Player p) return p;
        if (first instanceof com.xenowarriors.gov.GovAgentEntity g) return g;
        return null;
    }

    private boolean guardDriving() { return getFirstPassenger() instanceof com.xenowarriors.gov.GovAgentEntity; }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        int i = getPassengers().indexOf(passenger);
        double[][] seats = kind.seats();
        double[] s = seats[Math.max(0, Math.min(seats.length - 1, i))];
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);               // forward
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);               // right
        double x = getX() + fx * s[2] + rx * s[0], z = getZ() + fz * s[2] + rz * s[0];
        move.accept(passenger, x, getY() + s[1] + passenger.getMyRidingOffset(), z);
        if (passenger instanceof LivingEntity le && passenger != getControllingPassenger()) le.yBodyRot = getYRot();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return position().add(Mth.cos(yaw) * (kind.width / 2 + 0.6), 0.2, Mth.sin(yaw) * (kind.width / 2 + 0.6));
    }

    @Override
    protected InteractionResult mobInteract(Player p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (p.isSecondaryUseActive()) {                                   // pack it up
            if (owner != null && !owner.equals(p.getUUID()) && !p.isCreative()) {
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
            if (getFirstPassenger() == p) p.displayClientMessage(Component.literal(kind == VehicleKind.HELICOPTER
                    ? "W/S speed, look to turn, Jump to climb, Sprint key to descend, Sneak to get out"
                    : "W/S speed and brake, A/D steer, Sneak to get out").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ driving (runs on the driver's client)

    @Override
    protected void tickRidden(Player p, Vec3 input) {
        super.tickRidden(p, input);
        double fwd = p.zza, side = p.xxa;
        double target = fwd > 0 ? kind.maxSpeed : fwd < 0 ? -kind.maxSpeed * 0.4 : 0;
        double a = fwd == 0 ? kind.accel * 0.6 : (Math.signum(target - speed) != Math.signum(speed) && speed != 0 ? kind.accel * 2.2 : kind.accel);
        speed += Mth.clamp(target - speed, -a, a);
        if (kind == VehicleKind.HELICOPTER) {
            setYRot(Mth.rotLerp(0.12F, getYRot(), p.getYRot()));
            double want = level().isClientSide ? (VehicleControls.up ? 0.32 : VehicleControls.down ? -0.32 : 0) : climb;
            climb += Mth.clamp(want - climb, -0.04, 0.04);
        } else {
            float grip = (float) Math.min(1.0, Math.abs(speed) / 0.15);
            setYRot(getYRot() - (float) side * (kind == VehicleKind.MOTORBIKE ? 4.5F : kind == VehicleKind.GOV_TRUCK ? 2.4F : 3.4F) * grip * (speed < 0 ? -1 : 1));
        }
        yRotO = getYRot(); yBodyRot = getYRot(); yHeadRot = getYRot();
        setXRot(0);
    }

    @Override protected Vec3 getRiddenInput(Player p, Vec3 in) { return Vec3.ZERO; }
    @Override protected float getRiddenSpeed(Player p) { return 0F; }

    private boolean driven() { return getControllingPassenger() != null; }

    @Override
    public void travel(Vec3 in) {
        if (!driven() && kind != VehicleKind.HELICOPTER) { speed *= 0.9; super.travel(Vec3.ZERO); return; }
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        Vec3 v = getDeltaMovement();
        double y;
        if (kind == VehicleKind.HELICOPTER) {
            if (driven()) { setNoGravity(true); y = climb; }
            else { setNoGravity(false); speed *= 0.95; y = onGround() ? 0 : Math.max(v.y - 0.04, -0.18); }    // autorotates down gently
        } else {
            y = onGround() ? -0.08 : Math.max(v.y - 0.08, -1.5) * 0.98;
            if (isInWater()) { speed *= 0.9; y = Math.min(0.02, y + 0.1); }
        }
        Vec3 move = new Vec3(fx * speed, y, fz * speed);
        move(MoverType.SELF, move);
        if (horizontalCollision && Math.abs(speed) > 0.35) {                     // crash
            if (!level().isClientSide) level().playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.5F, 0.7F);
            speed *= 0.3;
        } else if (horizontalCollision) speed *= 0.6;
        setDeltaMovement(move.x, onGround() && kind != VehicleKind.HELICOPTER ? 0 : y, move.z);
        calculateEntityAnimation(false);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void aiStep() {
        if (!level().isClientSide && guardDriving()) patrol();
        super.aiStep();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            wheelRotO = wheelRot; rotorRotO = rotorRot;
            double hs = (getX() - xo) * -Mth.sin(getYRot() * Mth.DEG_TO_RAD) + (getZ() - zo) * Mth.cos(getYRot() * Mth.DEG_TO_RAD);
            wheelRot += (float) (hs / (kind == VehicleKind.GOV_TRUCK ? 0.44 : 0.33));
            float targetSpin = driven() || !onGround() ? 1.6F : 0F;
            rotorSpin += (targetSpin - rotorSpin) * 0.03F;
            rotorRot += rotorSpin;
            VehicleFx.tick(this);
        } else {
            if (driven() && tickCount % (kind == VehicleKind.HELICOPTER ? 5 : 9) == 0) {
                if (kind == VehicleKind.HELICOPTER)
                    level().playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 0.7F, 0.45F);
                else
                    level().playSound(null, blockPosition(), SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, 0.12F,
                            (kind == VehicleKind.GOV_TRUCK ? 0.4F : kind == VehicleKind.MOTORBIKE ? 0.9F : 0.6F) + (float) Math.abs(getDeltaMovement().horizontalDistance()) * 0.8F);
            }
        }
    }

    /** A Government guard driving the truck: slow loops around its post; gets out to fight anyone wanted nearby. */
    private void patrol() {
        if (home == null) home = position();
        var g = (com.xenowarriors.gov.GovAgentEntity) getFirstPassenger();
        Player threat = level().getNearestPlayer(this, 12);
        if (threat != null && com.xenowarriors.gov.GovFacilities.wanted(threat)) {
            g.stopRiding(); g.setTarget(threat); speed = 0; return;
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

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || isInvulnerableTo(src)) return false;
        if (src.getEntity() != null && hasPassenger(src.getEntity())) return false;          // (no shooting your own ride)
        float hp = getHealth() - amount * (src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) ? 1.5F : 0.6F);
        if (hp > 0) { setHealth(hp); level().playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_HURT, SoundSource.NEUTRAL, 0.5F, 1.4F); return true; }
        // wrecked: it bursts into smoke and drops back into its item (bought vehicles aren't lost for good)
        ejectPassengers();
        ((ServerLevel) level()).sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 0.8, 0.6, 0.8, 0.02);
        ((ServerLevel) level()).sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 3, 0.5, 0.3, 0.5, 0);
        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.8F, 1.2F);
        if (owner != null || kind != VehicleKind.GOV_TRUCK) spawnAtLocation(VehicleItem.make(kind, variant()));
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
