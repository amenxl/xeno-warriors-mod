package com.xenowarriors.pickup;

import com.xenowarriors.bot.PartyBotEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * A health orb (HealthOrbs): a small glowing red orb that bobs where an enemy fell. Get close and it drifts to you like
 * experience; touching it heals {@link #HEAL_FRACTION} of your max health - always the same share, whatever your level.
 * Players and their bot teammates can take it (only someone who's hurt pulls it in). Gone after a minute.
 */
public class HealthOrbEntity extends Entity {
    public static final float HEAL_FRACTION = 0.025F;
    public static final int LIFE = 60 * 20;
    public static final double PULL_RANGE = 6, PICKUP_RANGE = 1.1;

    private LivingEntity follow;
    private int seek;

    public HealthOrbEntity(EntityType<? extends HealthOrbEntity> type, Level level) {
        super(type, level);
    }

    /** A Karma orb (the Karma Orbs ability): fills Karma instead of healing - blue, not red. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> KARMA = net.minecraft.network.syncher.SynchedEntityData.defineId(HealthOrbEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** A Karma orb fills this share of max Karma. */
    public static final float KARMA_FRACTION = 0.04F;
    /** Whose kill it came from (Orb Magnet), and how far it comes to them from (0: no magnet). */
    private java.util.UUID owner;
    private double magnet;

    public boolean karma() { return entityData.get(KARMA); }
    public void setKarma(boolean k) { entityData.set(KARMA, k); }
    public void setOwner(java.util.UUID o, double magnetRange) { owner = o; magnet = magnetRange; }

    @Override protected void defineSynchedData() { entityData.define(KARMA, false); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isAttackable() { return false; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    /** Who could take it: alive players (not spectators) and party bots that aren't down, who are missing health. */
    /** Who could take this one: a Karma orb only players short of Karma; a health orb anyone hurt (see the static one). */
    private boolean takes(Entity e) {
        if (!karma()) return collector(e);
        if (!(e instanceof net.minecraft.server.level.ServerPlayer p) || !p.isAlive() || p.isSpectator() || p instanceof net.minecraftforge.common.util.FakePlayer) return false;
        return p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).map(d -> d.getKarma() < d.karmaMax).orElse(false);
    }

    public static boolean collector(Entity e) {
        if (!(e instanceof LivingEntity l) || !l.isAlive() || l.getHealth() >= l.getMaxHealth()) return false;
        if (e instanceof Player p) return !p.isSpectator() && !(p instanceof net.minecraftforge.common.util.FakePlayer);
        return e instanceof PartyBotEntity b && !b.downed();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 25 == 0 && karma()) level().addParticle(ParticleTypes.GLOW, getX(), getY() + 0.5, getZ(), 0, 0.02, 0);
            else if (tickCount % 25 == 0) level().addParticle(ParticleTypes.HEART, getX() + (random.nextDouble() - 0.5) * 0.2, getY() + 0.5, getZ() + (random.nextDouble() - 0.5) * 0.2, 0, 0.02, 0);
            return;
        }
        if (tickCount > LIFE) { discard(); return; }
        // Orb Magnet: straight to whoever beat the enemy, from further away
        net.minecraft.world.entity.player.Player o = owner != null && magnet > 0 ? level().getPlayerByUUID(owner) : null;
        LivingEntity mine = o != null && o.level() == level() && o.distanceToSqr(this) < magnet * magnet && takes(o) ? o : null;
        double range = mine != null ? magnet : PULL_RANGE;
        if (mine != null) { if (follow != mine) magnetized++; follow = mine; seek = 10; }
        else if (--seek <= 0 || follow == null || !takes(follow) || follow.distanceToSqr(this) > PULL_RANGE * PULL_RANGE * 1.5) {
            seek = 10;
            follow = null;
            double best = PULL_RANGE * PULL_RANGE;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(PULL_RANGE), this::takes)) {
                double d = e.distanceToSqr(this);
                if (d < best) { best = d; follow = e; }
            }
        }
        Vec3 v = getDeltaMovement();
        if (follow != null) {                         // drifts in, faster the closer it gets (like experience)
            Vec3 to = follow.position().add(0, follow.getBbHeight() * 0.5, 0).subtract(position());
            double d = to.length();
            if (d < PICKUP_RANGE + follow.getBbWidth() * 0.5) { collect(follow); return; }
            double f = Math.max(0, 1.0 - d / range);
            v = v.add(to.normalize().scale((mine != null ? 0.08 : 0.03) + f * f * 0.2));
            setNoGravity(true);
        } else {
            setNoGravity(false);
            v = v.add(0, onGround() ? 0 : -0.02, 0);   // settles, then floats just above the ground (it bobs on the client)
        }
        move(MoverType.SELF, v);
        setDeltaMovement(v.scale(follow != null ? 0.9 : 0.7));
    }

    /** (tests) what it's doing. */
    public String debug() { return "follow=" + (follow == null ? "-" : follow.getName().getString()) + " v=" + getDeltaMovement() + " pos=" + position() + " age=" + tickCount + " removed=" + isRemoved(); }

    /** (Tests) orbs that went for their owner from afar; Karma orbs taken. */
    public static int magnetized, karmaTaken;

    /** Heals 2.5% of their max health (a Karma orb: 4% of their max Karma). */
    public void collect(LivingEntity who) {
        if (karma() && who instanceof net.minecraft.server.level.ServerPlayer sp) {
            float max = sp.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).map(d -> d.karmaMax).orElse(100F);
            com.xenowarriors.karma.Karma.add(sp, max * KARMA_FRACTION);
            if (level() instanceof ServerLevel l) {
                l.sendParticles(ParticleTypes.GLOW, who.getX(), who.getY() + who.getBbHeight() * 0.8, who.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
                l.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.4F + random.nextFloat() * 0.2F);
            }
            karmaTaken++;
            discard();
            return;
        }
        float heal = who.getMaxHealth() * HEAL_FRACTION;
        who.heal(heal);
        if (level() instanceof ServerLevel l) {
            l.sendParticles(ParticleTypes.HEART, who.getX(), who.getY() + who.getBbHeight() * 0.8, who.getZ(), 3, 0.3, 0.2, 0.3, 0);
            l.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F, 0.6F + random.nextFloat() * 0.2F);
        }
        HealthOrbs.collected(this, who, heal);
        discard();
    }
}
