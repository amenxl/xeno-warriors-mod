package com.xenowarriors.you;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * A demon's shadow (YouDevil): a dark copy of them that steps out on the night their Devil's Hunger is full and fights
 * them alone - their strength, their weapon, a ki blast now and then and a lunging dash. Only its owner can hurt it
 * (no help: it's their trial). Beaten, they awaken as a Devil.
 */
public class DevilShadowEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(DevilShadowEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private int nextBlast = 80, nextDash = 120, ownerless;

    public DevilShadowEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        addTag(YouDevil.SHADOW_TAG);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 48).add(Attributes.ARMOR, 4).add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(OWNER, Optional.empty()); }

    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
    }

    /** Takes on its owner: their strength, name and weapon. */
    void copy(ServerPlayer p) {
        entityData.set(OWNER, Optional.of(p.getUUID()));
        float[] st = YouDevil.shadowStats(p);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(st[0]);
        setHealth(st[0]);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(st[1]);
        YouSlot s = You.slot(p);
        setCustomName(Component.literal((s != null ? s.name : p.getName().getString()) + "'s Shadow").withStyle(ChatFormatting.DARK_PURPLE));
        setItemSlot(EquipmentSlot.MAINHAND, p.getMainHandItem().copyWithCount(1));
        setDropChance(EquipmentSlot.MAINHAND, 0F);
        setTarget(p);
    }

    private ServerPlayer owner() {
        UUID id = ownerId();
        return id != null && level() instanceof ServerLevel l && l.getPlayerByUUID(id) instanceof ServerPlayer sp ? sp : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 2 == 0) level().addParticle(ParticleTypes.LARGE_SMOKE, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.02, 0);
            return;
        }
        ServerPlayer o = owner();
        if (o == null || !o.isAlive() || o.level() != level()) { if (++ownerless > 200) discard(); return; }
        ownerless = 0;
        if (getTarget() != o) setTarget(o);
        double d = distanceTo(o);
        if (--nextBlast <= 0 && d > 5 && hasLineOfSight(o)) {                      // a dark ki blast
            nextBlast = 90 + random.nextInt(40);
            Vec3 dir = o.getEyePosition().subtract(getEyePosition()).normalize();
            var b = new com.xenowarriors.npc.KiBlastEntity(level(), this, dir.x, dir.y, dir.z);
            b.setPos(getX() + dir.x, getEyeY() - 0.2, getZ() + dir.z);
            b.setDamage((float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8F);
            level().addFreshEntity(b);
            level().playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.7F, 0.6F);
        }
        if (--nextDash <= 0 && d > 3 && d < 14) {                                  // a lunging dash, like Devil Dash
            nextDash = 120 + random.nextInt(60);
            Vec3 v = o.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.4).add(0, 0.25, 0);
            setDeltaMovement(v); hurtMarked = true;
            ((ServerLevel) level()).sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 16, 0.3, 0.5, 0.3, 0.05);
        }
    }

    /** Only its owner can hurt it (and the void, /kill). */
    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(src, amount);
        Entity by = src.getEntity();
        if (by instanceof Projectile pr) by = pr.getOwner();
        UUID me = ownerId();
        if (by == null || me == null) return false;
        UUID who = by instanceof Player ? by.getUUID() : com.xenowarriors.combat.Allies.side(by);
        if (!(by instanceof Player) && !(by instanceof com.xenowarriors.soul.SoulEntity)) return false;   // (no bots or pets: it's their trial - their own souls may help)
        return me.equals(who) && super.hurt(src, amount);
    }

    @Override public boolean canBeLeashed(Player p) { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean canChangeDimensions() { return false; }

    @Override
    public void die(DamageSource src) {
        if (!level().isClientSide) {
            YouDevil.shadowDied(this);
            ((ServerLevel) level()).sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 40, 0.5, 1, 0.5, 0.05);
        }
        super.die(src);
    }

    @Override protected boolean shouldDropLoot() { return false; }
    @Override public int getExperienceReward() { return 0; }
}
