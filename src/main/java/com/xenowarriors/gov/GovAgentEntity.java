package com.xenowarriors.gov;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Government people. Workers (lab coats) and C2 guards (helmets, armor). No elements, no KAs - human modifications
 * instead, like Kyle's: SPEED (dashes), PHASE (invisible, hits pass through until he's been hit a few times),
 * ATTACK MODE (powers up when hurt), ARMOR plating (tanky, slow), SCANNER eye (sees through invisibility, calls guards),
 * JUMP boosters (big leaps). Guards leave you alone until you're caught: breaking things or opening containers in
 * a facility (or hitting anyone there) makes you wanted, and every guard around comes for you.
 */
public class GovAgentEntity extends PathfinderMob {
    public static final int SPEED = 1, PHASE = 2, ATTACK_MODE = 4, ARMOR = 8, SCANNER = 16, JUMP = 32;
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(GovAgentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODS = SynchedEntityData.defineId(GovAgentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> GUARD = SynchedEntityData.defineId(GovAgentEntity.class, EntityDataSerializers.BOOLEAN);
    private BlockPos post;
    private int phaseHits, dashCd, attackModeAt = -1;

    public GovAgentEntity(EntityType<? extends GovAgentEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 26).add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ARMOR, 4);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANT, 0);
        entityData.define(MODS, 0);
        entityData.define(GUARD, true);
    }

    public boolean guard() { return entityData.get(GUARD); }
    public int mods() { return entityData.get(MODS); }
    public boolean has(int mod) { return (mods() & mod) != 0; }
    public String look() { return (guard() ? "gov_guard_" : "gov_worker_") + (entityData.get(VARIANT) % 2 + 1); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new GunGoal());
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15, true) { @Override public boolean canUse() { return guard() && !hasGun() && super.canUse(); } });
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 10F, 1.0, 1.35, p -> !guard() && GovFacilities.wanted((Player) p)));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) { @Override public boolean canUse() { return guard() && super.canUse(); } }.setAlertOthers(GovAgentEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> guard() && GovFacilities.wanted((Player) p)));
    }

    /** A worker or guard with its modifications. */
    public void setup(boolean guard, int variant, BlockPos post) {
        entityData.set(GUARD, guard);
        entityData.set(VARIANT, variant);
        this.post = post.immutable();
        restrictTo(post, 20);
        int[] all = {SPEED, PHASE, ATTACK_MODE, ARMOR, SCANNER, JUMP};
        int m;
        if (guard) {
            m = all[random.nextInt(all.length)];
            if (random.nextInt(3) == 0) m |= all[random.nextInt(all.length)];
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextBoolean() ? Items.IRON_SWORD : Items.IRON_AXE));
            setDropChance(EquipmentSlot.MAINHAND, 0.05F);
        } else m = random.nextBoolean() ? SCANNER : SPEED;
        entityData.set(MODS, m);
        if ((m & ARMOR) != 0) {
            getAttribute(Attributes.ARMOR).setBaseValue(14);
            getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.7);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.22);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
            setHealth(40);
        }
        if ((m & SPEED) != 0) getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.34);
        if ((m & PHASE) != 0) addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false));
        if (!guard) getAttribute(Attributes.MAX_HEALTH).setBaseValue(18);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance d, MobSpawnType reason, SpawnGroupData data, CompoundTag tag) {
        if (mods() == 0) setup(random.nextInt(3) != 0, random.nextInt(2), blockPosition());
        return super.finalizeSpawn(level, d, reason, data, tag);
    }

    // ---------------------------------------------------------------- guns (with TACZ installed)

    private boolean gunDrawn;
    private int burst;

    public boolean hasGun() { return com.xenowarriors.compat.TaczGuns.isGun(getMainHandItem()); }

    public boolean skyAmmo() { return getPersistentData().getBoolean("xw_sky_ammo"); }

    /** Some guards carry a gun (and some of those load Sky-tech rounds). Rolled once per guard. */
    private void armWithGun() {
        var t = getPersistentData();
        if (t.getBoolean("xw_gun_rolled") || !guard() || !com.xenowarriors.compat.TaczGuns.available()) return;
        t.putBoolean("xw_gun_rolled", true);
        if (random.nextFloat() >= 0.6F) return;
        String id = com.xenowarriors.compat.TaczGuns.GOV_GUNS[random.nextInt(com.xenowarriors.compat.TaczGuns.GOV_GUNS.length)];
        net.minecraft.world.item.ItemStack g = com.xenowarriors.compat.TaczGuns.gun(id, 30);
        if (g.isEmpty()) return;
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, g);
        setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.04F);
        if (random.nextFloat() < 0.35F) t.putBoolean("xw_sky_ammo", true);
    }

    /** Keeps 6-16 blocks away and fires in bursts while it can see its target. */
    private class GunGoal extends net.minecraft.world.entity.ai.goal.Goal {
        GunGoal() { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return guard() && hasGun() && getTarget() != null && getTarget().isAlive(); }
        private int aim;
        @Override public void start() { aim = 0; }
        @Override public void stop() { getNavigation().stop(); }
        @Override public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            if (aim++ < 30) { getLookControl().setLookAt(t, 30F, 30F); return; }     // raise the gun and aim first (a warning)
            getLookControl().setLookAt(t, 30F, 30F);
            double dist = distanceTo(t);
            boolean see = getSensing().hasLineOfSight(t);
            if (!see || dist > 16) getNavigation().moveTo(t, 1.0);
            else if (dist < 6) {
                Vec3 away = position().add(position().subtract(t.position()).normalize().scale(5));
                getNavigation().moveTo(away.x, away.y, away.z, 1.1);
            } else getNavigation().stop();
            burst = (burst + 1) % 60;
            if (!see || burst >= 16 || tickCount % 4 != 0) return;           // short, slow bursts (4 shots), then a 2 s pause
            if (!gunDrawn) { com.xenowarriors.compat.TaczGuns.draw(GovAgentEntity.this); gunDrawn = true; return; }
            Vec3 d = t.getBoundingBox().getCenter().subtract(getEyePosition());
            float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F) + (random.nextFloat() - .5F) * 12F;
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))) + (random.nextFloat() - .5F) * 8F;
            setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw; setXRot(pitch);
            com.xenowarriors.compat.TaczGuns.shoot(GovAgentEntity.this, pitch, yaw);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        ServerLevel l = (ServerLevel) level();
        if (tickCount == 10) armWithGun();
        if (tickCount == 20 && hasGun()) { com.xenowarriors.compat.TaczGuns.draw(this); gunDrawn = true; }
        LivingEntity t = getTarget();
        if (dashCd > 0) dashCd--;
        if (t != null && t.isAlive()) {
            double dist = distanceTo(t);
            // SPEED: a burst across the room
            if (has(SPEED) && dashCd == 0 && dist > 4 && dist < 14) {
                Vec3 d = t.position().subtract(position()).normalize();
                setDeltaMovement(d.x * 1.3, 0.15, d.z * 1.3); hurtMarked = true; dashCd = 60;
                l.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 8, 0.3, 0.2, 0.3, 0.02);
                l.playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 0.7F, 1.6F);
            }
            // JUMP boosters: a big leap onto them
            if (has(JUMP) && dashCd == 0 && onGround() && dist > 3 && dist < 12) {
                Vec3 d = t.position().subtract(position());
                setDeltaMovement(d.x * 0.16, 0.85, d.z * 0.16); hurtMarked = true; dashCd = 80;
                l.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 10, 0.2, 0.05, 0.2, 0.02);
                l.playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 0.7F, 1.2F);
            }
        }
        // SCANNER: shows hidden intruders and calls the guards
        if (has(SCANNER) && tickCount % 20 == 0) {
            for (Player p : l.getEntitiesOfClass(Player.class, getBoundingBox().inflate(16), p -> GovFacilities.wanted(p))) {
                p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
                if (!guard() && tickCount % 100 == 0) GovFacilities.alarm(l, blockPosition(), p, "Intruder spotted!");
            }
            l.sendParticles(new DustParticleOptions(new Vector3f(1F, .2F, .2F), 0.6F), getX(), getEyeY(), getZ(), 1, 0.05, 0.05, 0.05, 0);
        }
        // PHASE: comes back after a while unhit
        if (has(PHASE) && phaseHits >= 3 && tickCount % 300 == 0) { phaseHits = 0; addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false)); }
        if (attackModeAt >= 0 && tickCount % 4 == 0) l.sendParticles(new DustParticleOptions(new Vector3f(1F, .15F, .1F), 1F), getX(), getY() + 1, getZ(), 2, 0.3, 0.6, 0.3, 0);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (!level().isClientSide) {
            // PHASE: the first few hits pass right through him (he flickers visible a bit more each time)
            if (has(PHASE) && phaseHits < 3 && src.getEntity() != null) {
                phaseHits++;
                ((ServerLevel) level()).sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 12, 0.3, 0.6, 0.3, 0.05);
                playSound(SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.8F);
                if (phaseHits >= 3) removeEffect(MobEffects.INVISIBILITY);
                if (src.getEntity() instanceof Player p) GovFacilities.makeWanted(p, "attacked Government staff");
                return false;
            }
            if (src.getEntity() instanceof Player p) GovFacilities.makeWanted(p, "attacked Government staff");
        }
        boolean r = super.hurt(src, amount);
        // ATTACK MODE: at half health he switches it on
        if (r && has(ATTACK_MODE) && attackModeAt < 0 && getHealth() < getMaxHealth() / 2) {
            attackModeAt = tickCount;
            addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, -1, 1, false, true));
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 1, false, true));
            if (level() instanceof ServerLevel l) for (ServerPlayer p : l.players()) if (p.distanceToSqr(this) < 30 * 30)
                p.displayClientMessage(Component.literal(getName().getString() + ": ATTACK MODE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            playSound(SoundEvents.BEACON_POWER_SELECT, 1F, 0.6F);
        }
        return r;
    }

    @Override
    protected Component getTypeName() {
        return Component.literal(guard() ? "Government Guard" : "Government Worker");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", entityData.get(VARIANT));
        tag.putInt("Mods", mods());
        tag.putBoolean("Guard", guard());
        if (post != null) tag.putLong("Post", post.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(VARIANT, tag.getInt("Variant"));
        entityData.set(MODS, tag.getInt("Mods"));
        entityData.set(GUARD, tag.getBoolean("Guard"));
        if (tag.contains("Post")) { post = BlockPos.of(tag.getLong("Post")); restrictTo(post, 20); }
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    /** Guards sometimes carry a Gov Keycard (opens a facility vault). */
    @Override
    protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource src, int looting, boolean hit) {
        super.dropCustomDeathLoot(src, looting, hit);
        if (hit && guard() && random.nextFloat() < 0.08F + looting * 0.03F) spawnAtLocation(GovItems.KEYCARD.get());
    }
}
