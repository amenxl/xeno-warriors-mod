package com.xenowarriors.gov;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.WireFxPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * The Ultima Prototype (U-0): the Government's artificial Ultima, waiting in the arena of a Prototype Facility
 * (GovDungeons). Steel plates, red eyes, cables. Scales to the party that woke it - their Character Levels and, with
 * DragonMineZ, their power - and fights with:
 * <ul>
 *   <li><b>Prototype Dash</b>: blurs in and drives a heavy punch.</li>
 *   <li><b>Output Beam</b>: aims (a red line), then fires a beam down it.</li>
 *   <li><b>Overload Pulse</b>: crackles, then blasts everything around it away.</li>
 *   <li><b>"Stop right there!"</b>: everyone near freezes for a moment.</li>
 *   <li><b>Cable Grab</b>: seizes a player in its cables and crushes them - mash attack to break free, or a teammate
 *       hits it three times to make it drop them. If nobody does, it slams them down.</li>
 *   <li>At half health, <b>Limiter Removed</b>: faster, harder, and it calls two soldiers.</li>
 * </ul>
 */
public class UltimaPrototypeEntity extends Monster {
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1F, 0.1F, 0.1F), 1.1F),
            STEEL = new DustParticleOptions(new Vector3f(0.7F, 0.75F, 0.85F), 0.9F);
    public static final float BASE_HP = 900F, BASE_DMG = 14F;
    public static final int MASH = 12, GRAB_TIME = 120, SAVE_HITS = 3;
    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Ultima Prototype U-0").withStyle(ChatFormatting.RED),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private int dashCd = 40, beamCd = 120, pulseCd = 200, stopCd = 260, grabCd = 160;
    private int beamT = -1, pulseT = -1;
    private Vec3 beamDir;
    private boolean limiter;
    private BlockPos home;
    // the grab
    private LivingEntity grabbed;
    private int grabT, mash, saveHits;

    public UltimaPrototypeEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 400;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, BASE_HP).add(Attributes.ATTACK_DAMAGE, BASE_DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.34).add(Attributes.ARMOR, 12.0).add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.85);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, PartyBotEntity.class, false));
    }

    public void setHome(BlockPos a) { home = a; }
    public BlockPos home() { return home; }

    /**
     * Fitted to the party that woke it: health and damage grow with their Character Levels (and bots' levels), more
     * health for every extra fighter, and - with DragonMineZ - up to all of their power.
     */
    public void scaleTo(List<ServerPlayer> ps) {
        float lvl = 0F;
        int n = 0;
        for (ServerPlayer p : ps) {
            lvl += com.xenowarriors.stats.Stats.characterLevel(p); n++;
            for (PartyBotEntity b : PartyBots.bots(p)) if (b.distanceToSqr(p) < 48 * 48) { lvl += b.botLevel(); n++; }
        }
        float avg = n == 0 ? 1F : lvl / n;
        float eff = com.xenowarriors.stats.Stat.effective(Math.round(avg));
        float hp = BASE_HP * (1F + eff * 0.06F) * (1F + 0.6F * Math.max(0, n - 1));
        float dmg = BASE_DMG * (1F + eff * 0.035F);
        var h = getAttribute(Attributes.MAX_HEALTH); if (h != null) h.setBaseValue(hp);
        var d = getAttribute(Attributes.ATTACK_DAMAGE); if (d != null) d.setBaseValue(dmg);
        setHealth(getMaxHealth());
        if (!ps.isEmpty()) com.xenowarriors.journey.JourneyWorld.scale(this, com.xenowarriors.compat.DmzScaling.partyScale(ps));
        getPersistentData().putFloat("xw_proto_level", avg);
    }

    private float dmg() { return (float) getAttributeValue(Attributes.ATTACK_DAMAGE); }
    private int cd(int t) { return limiter ? Math.round(t * 0.7F) : t; }

    @Override
    public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bar.addPlayer(p); }

    @Override
    public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bar.removePlayer(p); }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        ServerLevel l = (ServerLevel) level();
        bar.setProgress(getHealth() / getMaxHealth());
        if (home != null && tickCount % 10 == 0) ArenaLock.seal(this, GovDungeons.arena(home));   // (the arena is sealed while it's fighting)
        if (home != null && tickCount % 20 == 1) ArenaProtect.mark(this);       // (and its blocks can't be broken)
        if (tickCount % 4 == 0) l.sendParticles(limiter ? RED : STEEL, getX(), getY() + 1.1, getZ(), 1, 0.3, 0.6, 0.3, 0);
        if (!limiter && getHealth() < getMaxHealth() * 0.5F) limiterRemoved(l);
        if (grabbed != null) { tickGrab(l); return; }
        if (beamT >= 0) { tickBeam(l); return; }
        if (pulseT >= 0) { tickPulse(l); return; }
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive()) return;
        dashCd--; beamCd--; pulseCd--; stopCd--; grabCd--;
        double d = distanceTo(t);
        if (grabCd <= 0 && d < 3.5 && (t instanceof ServerPlayer || t instanceof PartyBotEntity)) { startGrab(l, t); return; }
        if (pulseCd <= 0 && l.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(7), this::foe).size() >= 2 || pulseCd <= -100) { startPulse(l); return; }
        if (beamCd <= 0 && d > 5 && d < 26 && hasLineOfSight(t)) { startBeam(l, t); return; }
        if (stopCd <= 0 && d < 12) { stop(l); return; }
        if (dashCd <= 0 && d > 4 && d < 18) dash(l, t);
    }

    private boolean foe(LivingEntity e) {
        return e != this && e.isAlive() && (e instanceof Player p && !p.isSpectator() && !p.isCreative() || e instanceof PartyBotEntity);
    }

    private void say(ServerLevel l, String line, ChatFormatting c) {
        for (ServerPlayer p : l.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(40)))
            p.displayClientMessage(Component.literal("Ultima Prototype: ").withStyle(ChatFormatting.RED).append(Component.literal(line).withStyle(c)), true);
    }

    // ---- Prototype Dash
    private void dash(ServerLevel l, LivingEntity t) {
        Vec3 v = t.position().subtract(position()).normalize();
        setDeltaMovement(v.x * 1.7, 0.25, v.z * 1.7); hurtMarked = true;
        dashCd = cd(70 + random.nextInt(40));
        l.playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 0.9F, 1.4F);
        l.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 8, 0.2, 0.1, 0.2, 0.05);
    }

    // ---- Output Beam: a red aiming line for a second, then the beam
    private void startBeam(ServerLevel l, LivingEntity t) {
        beamT = 0;
        beamDir = t.getEyePosition().subtract(getEyePosition()).normalize();
        getNavigation().stop();
        say(l, "Output beam - locking on.", ChatFormatting.GRAY);
        l.playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.5F, 1.8F);
    }

    private void tickBeam(ServerLevel l) {
        beamT++;
        getNavigation().stop();
        Vec3 from = getEyePosition();
        if (beamT < 22) {
            if (getTarget() != null && beamT < 14) beamDir = getTarget().getEyePosition().subtract(from).normalize();   // (tracks you, then holds still: dodge!)
            for (double s = 1; s < 26; s += 1.2) { Vec3 p = from.add(beamDir.scale(s)); l.sendParticles(RED, p.x, p.y, p.z, 1, 0, 0, 0, 0); }
            return;
        }
        for (double s = 1; s < 26; s += 0.6) { Vec3 p = from.add(beamDir.scale(s)); l.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01); }
        l.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 1.6F);
        Vec3 to = from.add(beamDir.scale(26));
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.2), this::foe)) {
            Vec3 c = e.getBoundingBox().getCenter();
            double along = c.subtract(from).dot(beamDir);
            if (along < 0 || c.distanceTo(from.add(beamDir.scale(along))) > 1.3) continue;
            e.hurt(damageSources().mobAttack(this), dmg() * 1.8F);
        }
        beamT = -1;
        beamCd = cd(160 + random.nextInt(60));
    }

    // ---- Overload Pulse: crackles for 1.5 s, then blasts everything near away (no blocks broken)
    private void startPulse(ServerLevel l) {
        pulseT = 0;
        getNavigation().stop();
        say(l, "Overload... in progress.", ChatFormatting.GOLD);
        l.playSound(null, blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 2F, 1.5F);
    }

    private void tickPulse(ServerLevel l) {
        pulseT++;
        getNavigation().stop();
        if (pulseT < 30) {
            double r = 7 * pulseT / 30.0;
            for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6 + pulseT * 0.2; l.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + Math.cos(a) * r, getY() + 0.2, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0); }
            return;
        }
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        l.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2F, 0.8F);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(7), this::foe)) {
            e.hurt(damageSources().mobAttack(this), dmg() * 1.5F);
            Vec3 v = e.position().subtract(position()).normalize();
            e.push(v.x * 1.6, 0.6, v.z * 1.6); e.hurtMarked = true;
        }
        pulseT = -1;
        pulseCd = cd(220 + random.nextInt(80));
    }

    // ---- "Stop right there!"
    private void stop(ServerLevel l) {
        stopCd = cd(280 + random.nextInt(100));
        say(l, "Stop right there!", ChatFormatting.RED);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12), this::foe)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 6, false, true));
            e.addEffect(new MobEffectInstance(MobEffects.JUMP, 30, 128, false, false));
        }
        l.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1F, 1.6F);
    }

    // ---- Limiter Removed (once, at half health)
    private void limiterRemoved(ServerLevel l) {
        limiter = true;
        say(l, "Limiter removed. Output 100%.", ChatFormatting.DARK_RED);
        var s = getAttribute(Attributes.MOVEMENT_SPEED); if (s != null) s.setBaseValue(s.getBaseValue() * 1.3);
        var d = getAttribute(Attributes.ATTACK_DAMAGE); if (d != null) d.setBaseValue(d.getBaseValue() * 1.25);
        l.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        l.playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1F, 1.6F);
        for (int i = 0; i < 2; i++) {
            GovAgentEntity g = GovEntities.GOV_AGENT.get().create(l);
            if (g == null) continue;
            g.moveTo(getX() + (i == 0 ? -3 : 3), getY(), getZ() - 2, getYRot(), 0);
            g.setup(true, 1, blockPosition());
            if (getTarget() != null) g.setTarget(getTarget());
            l.addFreshEntity(g);
        }
    }

    // ---- Cable Grab
    private void startGrab(ServerLevel l, LivingEntity t) {
        grabbed = t; grabT = 0; mash = 0; saveHits = 0;
        grabCd = cd(300 + random.nextInt(100));
        getNavigation().stop();
        say(l, "Target acquired. Crushing.", ChatFormatting.RED);
        l.playSound(null, t.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.5F, 0.5F);
        send(true);
        if (t instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("Grabbed by the Ultima Prototype! Mash attack to break free!").withStyle(ChatFormatting.RED), true);
    }

    private void send(boolean on) {
        if (grabbed == null) return;
        WireFxPacket pk = new WireFxPacket(getId(), on ? WireFxPacket.BIND : WireFxPacket.UNBIND, grabbed.getId(), Math.round(mash * 100F / MASH));
        ModNetwork.sendToTrackingEntityAndSelf(pk, this);
    }

    private void tickGrab(ServerLevel l) {
        LivingEntity g = grabbed;
        grabT++;
        getNavigation().stop();
        if (!g.isAlive() || g.level() != l) { release(l, false); return; }
        // held up in front of it
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        Vec3 at = position().add(f.scale(1.2)).add(0, 0.6, 0);
        g.teleportTo(at.x, at.y, at.z);
        g.setDeltaMovement(Vec3.ZERO);
        g.fallDistance = 0;
        if (grabT % 20 == 0) {
            g.invulnerableTime = 0;
            g.hurt(damageSources().mobAttack(this), dmg() * 0.4F);
            l.sendParticles(ParticleTypes.ELECTRIC_SPARK, g.getX(), g.getY() + 1, g.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
        }
        if (g instanceof PartyBotEntity && grabT % 10 == 0 && random.nextInt(3) == 0) mash(g);   // (a bot struggles too)
        if (grabT >= GRAB_TIME) {                                                // nobody got them out: slammed into the floor
            g.invulnerableTime = 0;
            g.hurt(damageSources().mobAttack(this), dmg() * 2.5F);
            Vec3 v = f.scale(1.2);
            release(l, false);
            g.setDeltaMovement(v.x, -0.4, v.z); g.hurtMarked = true;
            l.playSound(null, g.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2F, 0.6F);
            l.sendParticles(ParticleTypes.EXPLOSION, g.getX(), g.getY(), g.getZ(), 2, 0.3, 0.1, 0.3, 0);
        }
    }

    /** (Tests) grab this one now. */
    public void grabForTest(LivingEntity t) { if (level() instanceof ServerLevel l) startGrab(l, t); }
    public LivingEntity grabbedForTest() { return grabbed; }

    /** The grabbed one mashed attack (players: from the wire-bind mash packet). */
    public void mash(LivingEntity who) {
        if (grabbed != who) return;
        mash++;
        send(true);
        if (mash >= MASH && level() instanceof ServerLevel l) {
            if (who instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("You broke free!").withStyle(ChatFormatting.GREEN), true);
            release(l, true);
        }
    }

    private void release(ServerLevel l, boolean freed) {
        send(false);
        LivingEntity g = grabbed;
        grabbed = null;
        if (freed && g != null) {                                                // tossed free, and it reels for a moment
            Vec3 v = g.position().subtract(position()).normalize();
            g.push(v.x * 0.8, 0.3, v.z * 0.8); g.hurtMarked = true;
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false));
            l.playSound(null, blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 1F, 0.6F);
        }
    }

    /** Whoever holds this player in a grab, or null. */
    public static UltimaPrototypeEntity holding(ServerPlayer p) {
        for (UltimaPrototypeEntity u : p.serverLevel().getEntitiesOfClass(UltimaPrototypeEntity.class, p.getBoundingBox().inflate(6)))
            if (u.grabbed == p) return u;
        return null;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (grabbed != null && src.getEntity() == grabbed) return false;        // (can't punch your way out: mash)
        boolean r = super.hurt(src, amount);
        // a teammate hitting it while it holds someone: three hits and it drops them
        if (r && grabbed != null && src.getEntity() instanceof LivingEntity att && att != grabbed && level() instanceof ServerLevel l) {
            saveHits++;
            if (saveHits >= SAVE_HITS) {
                if (grabbed instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("Saved! You're free!").withStyle(ChatFormatting.GREEN), true);
                if (att instanceof ServerPlayer ap) ap.displayClientMessage(Component.literal("You made it let go!").withStyle(ChatFormatting.GREEN), true);
                release(l, true);
            }
        }
        return r;
    }

    @Override
    public void remove(RemovalReason why) {
        ArenaProtect.unmark(this);
        super.remove(why);
    }

    @Override
    public void die(DamageSource src) {
        if (level() instanceof ServerLevel l) {
            release(l, false);
            ArenaLock.release(this);
            say(l, "Error... system... shutting... down.", ChatFormatting.GRAY);
            if (home != null) GovDungeons.defeated(l, home);
            for (ServerPlayer p : l.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48))) {
                long money = 2000L + Math.round(getPersistentData().getFloat("xw_proto_level") * 20F);
                com.xenowarriors.economy.Money.add(p, money, true);
                p.sendSystemMessage(Component.literal("Ultima Prototype destroyed! +" + com.xenowarriors.economy.Money.fmt(money) + " Money").withStyle(ChatFormatting.GOLD));
            }
        }
        bar.removeAllPlayers();
        super.die(src);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource src, int looting, boolean hit) {
        super.dropCustomDeathLoot(src, looting, hit);
        spawnAtLocation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_SCRAP, 2 + random.nextInt(3)));
        spawnAtLocation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 4 + random.nextInt(5)));
        spawnAtLocation(new net.minecraft.world.item.ItemStack(GovItems.KEYCARD.get(), 1));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        if (home != null) t.putLong("xw_home", home.asLong());
        t.putBoolean("xw_limiter", limiter);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        if (t.contains("xw_home")) home = BlockPos.of(t.getLong("xw_home"));
        limiter = t.getBoolean("xw_limiter");
        if (hasCustomName()) bar.setName(getDisplayName());
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
}
