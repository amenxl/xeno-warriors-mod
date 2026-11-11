package com.xenowarriors.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Xeno Dragon (with DragonMineZ): what the Ender Dragon turns into when it's beaten - a humanoid dragon with the
 * Ender Dragon's moves (dragon breath clouds, ender fireballs, a wing gust, a rising dive, End Crystals that heal it,
 * short ender teleports) and the Saiyan forms: Super Saiyan at 75% health, Super Saiyan 2 at 50%, Super Saiyan 3 at
 * 25%; beaten as a Super Saiyan 3 it goes Super Saiyan God (fully healed). Beaten as a god it falls, then comes back in
 * Ultra Instinct (Mastered): silver, dodging, very fast, and throwing the Dragon Bomb (a Spirit Bomb: a huge sphere
 * charged overhead, then thrown - hurt it enough while it charges to stop it, or get out of the way). Every form makes
 * it a lot faster (moves and attacks). Only beaten in Ultra Instinct does it drop its loot.
 */
public class XenoDragonEntity extends Monster {
    public static final int BASE = 0, SSJ1 = 1, SSJ2 = 2, SSJ3 = 3, SSG = 4, UI = 5;
    public static final int ACT_NONE = 0, ACT_EMERGE = 1, ACT_POWER = 2, ACT_GOD = 3, ACT_AWAKEN = 4, ACT_BREATH = 5, ACT_SHOOT = 6,
            ACT_GUST = 7, ACT_RISE = 8, ACT_DIVE = 9, ACT_BOMB = 10, ACT_THROW = 11, ACT_STUN = 12, ACT_COMBO = 13, ACT_CRYSTAL = 14;
    public static final String[] FORM_NAMES = {"", "Super Saiyan", "Super Saiyan 2", "Super Saiyan 3", "Super Saiyan God", "Ultra Instinct (Mastered)"};
    /** Aura colour per form (0 = none). */
    public static final int[] AURA = {0, 0xFFD23A, 0xFFE04A, 0xFFC830, 0xFF3A2A, 0xDDE6FF};
    /** Movement speed attribute per form (x base). Mobs move with about the square of it, so the real speed goes x1, x1.4, x1.85, x2.3, x2.85, x3.5. */
    public static final double[] SPEED = {1.0, 1.19, 1.36, 1.51, 1.69, 1.87};
    /** Cooldowns and the gaps between combo hits per form (x base): every form attacks a lot faster. */
    public static final float[] CD = {1.0F, 0.8F, 0.65F, 0.52F, 0.42F, 0.32F};
    public static final double[] DMG = {0, 0.2, 0.45, 0.75, 1.2, 2.0}, ARM = {0, 2, 4, 6, 9, 12};
    public static final double BASE_SPEED = 0.28;
    public static final int EMERGE_TICKS = 80, POWER_TICKS = 30, GOD_TICKS = 60, KO_TICKS = 50, AWAKEN_TICKS = 50, BREATH_TICKS = 30, BREATH_FIRE = 10,
            SHOOT_TICKS = 14, GUST_TICKS = 18, GUST_FIRE = 6, RISE_TICKS = 18, DIVE_TICKS = 30, BOMB_TICKS = 100, THROW_TICKS = 14, STUN_TICKS = 40;
    /** Damage that stops a Dragon Bomb while it's charging (share of its max health). */
    public static float BOMB_INTERRUPT = 0.08F;
    /** Ultra Instinct: chance to dodge a hit. */
    public static float DODGE = 0.4F;

    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(XenoDragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> KO = SynchedEntityData.defineId(XenoDragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACT = SynchedEntityData.defineId(XenoDragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACT_T = SynchedEntityData.defineId(XenoDragonEntity.class, EntityDataSerializers.INT);
    private static final UUID FORM_DMG = UUID.fromString("6a1c2e70-3d4b-4f5a-8c21-0e5d7b3a9f01"), FORM_SPD = UUID.fromString("6a1c2e70-3d4b-4f5a-8c21-0e5d7b3a9f02"),
            FORM_ARM = UUID.fromString("6a1c2e70-3d4b-4f5a-8c21-0e5d7b3a9f03"), FORM_HP = UUID.fromString("6a1c2e70-3d4b-4f5a-8c21-0e5d7b3a9f04"),
            SCALE_HP = UUID.fromString("6a1c2e70-3d4b-4f5a-8c21-0e5d7b3a9f05"), SCALE_DMG = UUID.fromString("6a1c2e70-3d4b-4f5a-8c21-0e5d7b3a9f06");

    private final ServerBossEvent bar = (ServerBossEvent) new ServerBossEvent(Component.literal("Xeno Dragon"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(true);
    private int meleeCd = 20, breathCd = 60, shootCd = 40, gustCd = 100, diveCd = 120, blinkCd = 60, bombCd = 100, dodgeCd, comboT = -1, stuckT, crystalFor = -1;
    private boolean scaled;
    private Vec3 diveAt, emergeFrom;
    private BlockPos home;
    private float bombDamage;
    private int bombId = -1;
    private final List<UUID> crystals = new ArrayList<>();
    /** (For the tests) counts. */
    public int dodges, bombsThrown, bombsStopped, gusts, breaths, fireballs, dives, blinks;

    public XenoDragonEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 5000;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 700.0)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FORM, BASE);
        entityData.define(KO, 0);
        entityData.define(ACT, ACT_NONE);
        entityData.define(ACT_T, 0);
    }

    public int form() { return entityData.get(FORM); }
    public int ko() { return entityData.get(KO); }
    public int act() { return entityData.get(ACT); }
    public int actTicks() { return entityData.get(ACT_T); }
    private void act(int a, int t) { entityData.set(ACT, a); entityData.set(ACT_T, t); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override public boolean canUse() { return free() && act() == ACT_NONE && super.canUse(); }
            @Override public boolean canContinueToUse() { return free() && (act() == ACT_NONE || act() == ACT_COMBO) && super.canContinueToUse(); }
            @Override protected void checkAndPerformAttack(LivingEntity t, double d) {}     // (its own combo does the hitting)
        });
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, com.xenowarriors.bot.PartyBotEntity.class, false));
    }

    /** Not knocked out, emerging or transforming. */
    public boolean free() {
        int a = act();
        return ko() <= 0 && a != ACT_EMERGE && a != ACT_POWER && a != ACT_GOD && a != ACT_AWAKEN && a != ACT_STUN;
    }

    /** Untouchable: emerging, a god's ritual, knocked out or waking up in Ultra Instinct. */
    public boolean untouchable() { int a = act(); return ko() > 0 || a == ACT_EMERGE || a == ACT_GOD || a == ACT_AWAKEN; }

    // ---------------------------------------------------------------- spawning out of the Ender Dragon

    /** Starts the emerging cutscene: it hangs where the dragon died, then glides down to {@code land}. */
    public void emerge(BlockPos land) {
        home = land;
        emergeFrom = position();
        setNoGravity(true);
        act(ACT_EMERGE, EMERGE_TICKS);
    }

    public BlockPos home() { return home; }

    // ---------------------------------------------------------------- scaling

    /** Scales to the party nearby: health with the strongest DragonMineZ damage and how many fight it, its hits with their health. */
    public void scaleToParty() {
        scaled = true;
        double dm = 1, hp = 1;
        int n = 0;
        for (ServerPlayer p : ((ServerLevel) level()).players()) {
            if (p.isSpectator() || p.distanceToSqr(this) > 128 * 128) continue;
            n++;
            dm = Math.max(dm, Math.max(com.xenowarriors.compat.DmzScaling.multiplier(p, true), com.xenowarriors.compat.DmzScaling.multiplier(p, false)));
            hp = Math.max(hp, p.getMaxHealth() / 20.0);
            for (var b : com.xenowarriors.bot.PartyBots.bots(p)) if (b.isAlive() && b.distanceToSqr(this) < 128 * 128) n++;
        }
        float frac = getHealth() / getMaxHealth();
        mod(Attributes.MAX_HEALTH, SCALE_HP, Math.max(0, dm - 1.0) + 0.3 * Math.max(0, n - 1), AttributeModifier.Operation.MULTIPLY_TOTAL);
        mod(Attributes.ATTACK_DAMAGE, SCALE_DMG, Math.max(0, Math.pow(hp, 0.9) - 1.0), AttributeModifier.Operation.MULTIPLY_TOTAL);
        setHealth(getMaxHealth() * Math.max(0.05F, frac));
        int lvl = 60 + (int) Math.round(10 * Math.log(Math.max(1, dm)) / Math.log(2)) + form() * 6;
        getPersistentData().putInt("xw_lvl", lvl);
        getPersistentData().putBoolean("xw_boss", true);
        com.xenowarriors.network.ModNetwork.sendToTrackingEntityAndSelf(new com.xenowarriors.network.EnemyLevelPacket(getId(), lvl), this);
        XenoDragons.note("scaled to " + n + " fighters: dmz x" + String.format(java.util.Locale.ROOT, "%.2f", dm) + " hp " + Math.round(getMaxHealth())
                + " dmg " + String.format(java.util.Locale.ROOT, "%.1f", getAttributeValue(Attributes.ATTACK_DAMAGE)) + " lv " + lvl);
    }

    private void mod(net.minecraft.world.entity.ai.attributes.Attribute a, UUID id, double amount, AttributeModifier.Operation op) {
        AttributeInstance in = getAttribute(a);
        if (in == null) return;
        in.removeModifier(id);
        if (amount != 0) in.addPermanentModifier(new AttributeModifier(id, "Xeno Dragon", amount, op));
    }

    private void applyForm(int f) {
        mod(Attributes.ATTACK_DAMAGE, FORM_DMG, DMG[f], AttributeModifier.Operation.MULTIPLY_BASE);
        mod(Attributes.MOVEMENT_SPEED, FORM_SPD, SPEED[f] - 1.0, AttributeModifier.Operation.MULTIPLY_BASE);
        mod(Attributes.ARMOR, FORM_ARM, ARM[f], AttributeModifier.Operation.ADDITION);
        mod(Attributes.MAX_HEALTH, FORM_HP, f == UI ? 0.5 : f == SSG ? 0.25 : 0, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    /** Speed now (the attribute) - for the logs and tests. */
    public double speed() { return getAttributeValue(Attributes.MOVEMENT_SPEED); }

    private int cd(int base) { return Math.max(4, Math.round(base * CD[form()])); }

    // ---------------------------------------------------------------- the fight

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && getY() < level().getMinBuildHeight() - 8) rescue();   // (never lost to the void: back to where it landed)
    }

    private void rescue() {
        BlockPos h = home != null ? home : XenoDragons.landing((ServerLevel) level(), position());
        teleportTo(h.getX() + 0.5, h.getY() + 0.5, h.getZ() + 0.5);
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0;
        if (act() == ACT_DIVE || act() == ACT_RISE) act(ACT_NONE, 0);
        setNoGravity(false);
        XenoDragons.note("rescued from the void -> " + h.toShortString());
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (!scaled && tickCount >= 2) scaleToParty();
        updateBar();
        if (tickCount % 20 == 0) crystals(level);
        int ko = ko();
        if (ko > 0) {                                                            // fell as a god
            getNavigation().stop();
            setNoGravity(false);
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            entityData.set(KO, ko - 1);
            if (ko % 8 == 0) level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.3, getZ(), 6, 0.9, 0.1, 0.9, 0.01);
            if (ko - 1 == 0) startAwaken();
            return;
        }
        int a = act(), t = actTicks();
        if (a != ACT_NONE && a != ACT_COMBO) entityData.set(ACT_T, t - 1);
        switch (a) {
            case ACT_EMERGE -> { tickEmerge(level, t); return; }
            case ACT_POWER -> {
                stand();
                if (t % 4 == 0) level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.5, getZ(), 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 3, 1.4, 0.05, 1.4, 0.1);
                if (t - 1 <= 0) { act(ACT_NONE, 0); transform(form() + 1); }
                return;
            }
            case ACT_GOD -> {                                                     // the god ritual: red flames, it rises a little
                getNavigation().stop();
                setDeltaMovement(0, t > GOD_TICKS / 2 ? 0.04 : 0, 0);
                setNoGravity(true);
                level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.5, getZ(), 6, 0.8, 1.2, 0.8, 0.05);
                if (t % 6 == 0) level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 1, getZ(), 3, 0.6, 0.6, 0.6, 0);
                if (t - 1 <= 0) { act(ACT_NONE, 0); setNoGravity(false); transform(SSG); setHealth(getMaxHealth()); scaleToParty(); setHealth(getMaxHealth()); }
                return;
            }
            case ACT_AWAKEN -> {                                                  // Ultra Instinct: silver light, a shockwave
                stand();
                level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.5, getZ(), 8, 0.9, 1.4, 0.9, 0.06);
                if (t == AWAKEN_TICKS / 2) shockwave(0.4F);
                if (t - 1 <= 0) { act(ACT_NONE, 0); transform(UI); setHealth(getMaxHealth()); scaleToParty(); setHealth(getMaxHealth()); bombCd = 100; }
                return;
            }
            case ACT_STUN -> { stand(); if (t % 5 == 0) level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 3.2, getZ(), 4, 0.4, 0.1, 0.4, 0.1); if (t - 1 <= 0) act(ACT_NONE, 0); return; }
            case ACT_BREATH -> { tickBreath(level, t); return; }
            case ACT_SHOOT -> { stand(); lookTarget(); if (t == SHOOT_TICKS / 2) shoot(level); if (t - 1 <= 0) act(ACT_NONE, 0); return; }
            case ACT_GUST -> {
                stand();
                if (t > GUST_FIRE) level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.5, getZ(), 4, 3.5, 1, 3.5, -0.15);
                if (t == GUST_FIRE) gust(level);
                if (t - 1 <= 0) act(ACT_NONE, 0);
                return;
            }
            case ACT_RISE -> { tickRise(level, t); return; }
            case ACT_DIVE -> { tickDive(level, t); return; }
            case ACT_BOMB -> { tickBomb(level, t); return; }
            case ACT_CRYSTAL -> { stand(); if (t - 1 <= 0) { act(ACT_NONE, 0); summonCrystals(level); } return; }
            case ACT_THROW -> { stand(); if (t - 1 <= 0) { act(ACT_NONE, 0); setNoGravity(false); } return; }
            default -> {}
        }
        // Saiyan forms as it's worn down (SSJ at 75%, SSJ2 50%, SSJ3 25%)
        float frac = getHealth() / getMaxHealth();
        int want = frac < 0.25F ? SSJ3 : frac < 0.5F ? SSJ2 : frac < 0.75F ? SSJ1 : BASE;
        if (form() < SSJ3 && want > form()) { startPowerUp(); return; }
        LivingEntity target = getTarget();
        meleeCd--; breathCd--; shootCd--; gustCd--; diveCd--; blinkCd--; bombCd--; dodgeCd--;
        if (comboT >= 0) { combo(target); return; }
        if (target == null || !target.isAlive()) return;
        double d = distanceTo(target);
        boolean los = hasLineOfSight(target);
        // stuck or far off: a short ender teleport next to it
        if (getNavigation().isDone() && d > 4) stuckT++; else stuckT = Math.max(0, stuckT - 1);
        if (crystalFor != form() && (form() == SSJ2 || form() == SSG)) { crystalFor = form(); act(ACT_CRYSTAL, 16); playSound(SoundEvents.END_PORTAL_FRAME_FILL, 3F, 0.6F); return; }
        if (form() == UI && bombCd <= 0 && d < 48) { startBomb(level, target); return; }
        if (d < 3.8 && meleeCd <= 0) { comboT = 0; act(ACT_COMBO, 0); return; }
        if (gustCd <= 0 && (d < 4.5 || enemiesNear(7) >= 2)) { gustCd = cd(220); act(ACT_GUST, GUST_TICKS); playSound(SoundEvents.ENDER_DRAGON_FLAP, 4F, 0.7F); return; }
        if (breathCd <= 0 && d < 10 && los) { breathCd = cd(170); act(ACT_BREATH, BREATH_TICKS); playSound(SoundEvents.ENDER_DRAGON_GROWL, 3F, 1.2F); return; }
        if (diveCd <= 0 && d > 6 && d < 26 && onGround()) { startRise(target); return; }
        if (shootCd <= 0 && d > 5 && d < 40 && los) { shootCd = cd(110); act(ACT_SHOOT, SHOOT_TICKS); return; }
        if (blinkCd <= 0 && (d > 20 || stuckT > 40 || (!los && d > 8))) { blinkCd = cd(90); stuckT = 0; blinkNear(target, 3, 6); }
    }

    private void stand() {
        getNavigation().stop();
        setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
    }

    private void lookTarget() { if (getTarget() != null) getLookControl().setLookAt(getTarget(), 60F, 60F); }

    private int enemiesNear(double r) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r), this::enemy).size();
    }

    /** Players, bots and what's fighting it (not other monsters, not its own crystals). */
    boolean enemy(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof XenoDragonEntity) return false;
        if (e instanceof Player p) return !p.isSpectator() && !p.isCreative();
        return e instanceof com.xenowarriors.bot.PartyBotEntity || e == getTarget() || e == getLastHurtByMob()
                || (e instanceof net.minecraft.world.entity.TamableAnimal ta && ta.isTame());
    }

    private void say(String s, ChatFormatting... style) {
        for (ServerPlayer p : ((ServerLevel) level()).players())
            if (p.distanceToSqr(this) < 72 * 72) p.displayClientMessage(Component.literal(s).withStyle(style), true);
    }

    // ---------------------------------------------------------------- emerging

    private void tickEmerge(ServerLevel level, int t) {
        getNavigation().stop();
        int done = EMERGE_TICKS - t;                                             // 0 .. EMERGE_TICKS
        if (done < 30) {                                                         // taking shape in the dragon's last light
            setDeltaMovement(Vec3.ZERO);
            for (int i = 0; i < 6; i++) {
                double ang = random.nextDouble() * Math.PI * 2, r = 3 + random.nextDouble() * 3;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX() + Math.cos(ang) * r, getY() + 1.5 + (random.nextDouble() - .5) * 3, getZ() + Math.sin(ang) * r, 0,
                        -Math.cos(ang) * 0.4, 0, -Math.sin(ang) * 0.4, 1);
            }
            level.sendParticles(ParticleTypes.DRAGON_BREATH, getX(), getY() + 1.5, getZ(), 6, 0.7, 1.2, 0.7, 0.01);
            if (done == 29) {
                level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.5, getZ(), 3, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.5, getZ(), 1, 0, 0, 0, 0);
                playSound(SoundEvents.ENDER_DRAGON_GROWL, 6F, 0.6F);
                say("The Xeno Dragon awakens!", ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
            }
        } else {                                                                 // gliding down to the island
            Vec3 to = home == null ? position() : Vec3.atBottomCenterOf(home);
            Vec3 from = emergeFrom == null ? position() : emergeFrom;
            double k = (done - 30) / (double) (EMERGE_TICKS - 30);
            Vec3 at = from.lerp(to, Math.min(1, k * k * (3 - 2 * k)));
            setDeltaMovement(at.subtract(position()));
            if (done % 6 == 0) playSound(SoundEvents.ENDER_DRAGON_FLAP, 2F, 0.9F);
            level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.5, getZ(), 4, 0.6, 1, 0.6, 0.3);
        }
        if (t - 1 <= 0) {
            act(ACT_NONE, 0);
            setNoGravity(false);
            if (home != null) teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            shockwave(0.3F);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 6F, 0.8F);
            XenoDragons.note("landed at " + blockPosition().toShortString() + " hp " + Math.round(getHealth()) + "/" + Math.round(getMaxHealth()) + " speed " + fmt(speed()));
        }
    }

    // ---------------------------------------------------------------- forms

    private void startPowerUp() {
        act(ACT_POWER, POWER_TICKS);
        comboT = -1;
        setNoGravity(false);
        playSound(ZombieSaiyanEntity.dmzSound("aura_start", SoundEvents.BEACON_POWER_SELECT), 2.5F, 0.8F);
        XenoDragons.note("powering up from " + formName(form()) + " at " + Math.round(getHealth()) + "/" + Math.round(getMaxHealth()));
    }

    private void transform(int f) {
        int from = form();
        double before = speed();
        entityData.set(FORM, f);
        applyForm(f);
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.5, getZ(), 2, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
        playSound(ZombieSaiyanEntity.dmzSound("transform_on", SoundEvents.LIGHTNING_BOLT_THUNDER), 3F, f >= SSG ? 0.6F : 1.0F);
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 4F, f == UI ? 1.4F : 0.9F);
        if (f == SSJ2 || f == SSJ3) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.5, getZ(), 50, 0.8, 1.4, 0.8, 0.3);
        say("The Xeno Dragon went " + formName(f) + "!", f == UI ? ChatFormatting.WHITE : f == SSG ? ChatFormatting.RED : ChatFormatting.GOLD);
        XenoDragons.note("form " + formName(from) + " -> " + formName(f) + " hp " + Math.round(getHealth()) + "/" + Math.round(getMaxHealth())
                + " dmg " + fmt(getAttributeValue(Attributes.ATTACK_DAMAGE)) + " speed " + fmt(before) + " -> " + fmt(speed()) + " cd x" + CD[f]);
    }

    private void startGod() {
        act(ACT_GOD, GOD_TICKS);
        comboT = -1;
        setHealth(Math.max(1F, getHealth()));
        playSound(SoundEvents.BEACON_ACTIVATE, 4F, 0.6F);
        say("The Xeno Dragon's ki turns divine...", ChatFormatting.RED, ChatFormatting.BOLD);
        XenoDragons.note("beaten as Super Saiyan 3: the god ritual starts");
    }

    private void knockOut() {
        setHealth(1F);
        entityData.set(KO, KO_TICKS);
        act(ACT_NONE, 0);
        comboT = -1;
        setNoGravity(false);
        stopBomb(false);
        playSound(SoundEvents.ENDER_DRAGON_HURT, 4F, 0.5F);
        say("The Xeno Dragon falls...", ChatFormatting.GRAY);
        XenoDragons.note("knocked out as Super Saiyan God");
    }

    private void startAwaken() {
        act(ACT_AWAKEN, AWAKEN_TICKS);
        setHealth(Math.max(getHealth(), getMaxHealth() * 0.1F));
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 6F, 1.3F);
        playSound(SoundEvents.BEACON_ACTIVATE, 4F, 1.6F);
        say("...its body moves on its own. Ultra Instinct!", ChatFormatting.WHITE, ChatFormatting.BOLD);
        XenoDragons.note("gets back up: Ultra Instinct awakening");
    }

    /** Everyone near is thrown back (and hurt a bit times {@code dmg}). */
    private void shockwave(float dmg) {
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 2, 1, 0.2, 1, 0);
        for (int i = 0; i < 48; i++) {
            double ang = i * Math.PI * 2 / 48;
            level.sendParticles(ParticleTypes.CLOUD, getX() + Math.cos(ang) * 2, getY() + 0.3, getZ() + Math.sin(ang) * 2, 0, Math.cos(ang), 0.05, Math.sin(ang), 0.9);
        }
        playSound(SoundEvents.GENERIC_EXPLODE, 3F, 0.7F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(9), this::enemy)) push(e, 2.0, 0.6, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * dmg);
    }

    private void push(LivingEntity e, double out, double up, float dmg) {
        Vec3 d = e.position().subtract(position()).multiply(1, 0, 1);
        if (d.lengthSqr() < 1e-4) d = new Vec3(1, 0, 0);
        d = d.normalize();
        if (dmg > 0) { e.invulnerableTime = 0; e.hurt(damageSources().mobAttack(this), dmg); }
        double kb = 1 - (e.getAttribute(Attributes.KNOCKBACK_RESISTANCE) == null ? 0 : e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) * 0.6;
        e.setDeltaMovement(d.x * out * kb, up, d.z * out * kb);
        e.hurtMarked = true;
    }

    // ---------------------------------------------------------------- melee: two claw swipes and a tail sweep

    private void combo(LivingEntity target) {
        int t = comboT++, k = Math.max(3, Math.round(7 * CD[form()]));
        if (target == null || !target.isAlive() || distanceTo(target) > 5.5) { comboT = -1; act(ACT_NONE, 0); meleeCd = cd(20); return; }
        lookTarget();
        getNavigation().moveTo(target, 1.0);
        entityData.set(ACT_T, t);
        if (t == 0 || t == k) {
            swing(t == 0 ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND, true);
            target.invulnerableTime = 0;
            target.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.7F);
            playSound(ZombieSaiyanEntity.dmzSound("punch" + (1 + random.nextInt(3)), SoundEvents.PLAYER_ATTACK_SWEEP), 1.3F, 0.7F);
        }
        if (t == 2 * k) {                                                        // the tail sweep: everything around it
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.8, 1, 3.8), this::enemy))
                push(e, 1.4, 0.45, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.2F);
            ((ServerLevel) level()).sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1, getZ(), 6, 2, 0.3, 2, 0);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2F, 0.5F);
        }
        if (t >= 2 * k + 2) { comboT = -1; act(ACT_NONE, 0); meleeCd = cd(26); }
    }

    // ---------------------------------------------------------------- dragon breath

    private void tickBreath(ServerLevel level, int t) {
        stand();
        lookTarget();
        Vec3 look = getViewVector(1F).multiply(1, 0, 1).normalize();
        Vec3 mouth = position().add(look.scale(1.6)).add(0, 3.0, 0);
        if (t > BREATH_FIRE) {                                                   // telegraph: purple gathering in its mouth
            level.sendParticles(ParticleTypes.DRAGON_BREATH, mouth.x, mouth.y, mouth.z, 5, 0.25, 0.25, 0.25, 0.01);
            level.sendParticles(ParticleTypes.PORTAL, mouth.x, mouth.y, mouth.z, 4, 0.5, 0.5, 0.5, 0.5);
        } else if (t == BREATH_FIRE) {
            LivingEntity target = getTarget();
            Vec3 dir = target != null ? target.position().subtract(position()).multiply(1, 0, 1) : look;
            if (dir.lengthSqr() < 1e-3) dir = look;
            dir = dir.normalize();
            for (int i = 1; i <= 3; i++) {
                Vec3 at = position().add(dir.scale(1.5 + i * 2.2));
                AreaEffectCloud c = new AreaEffectCloud(level, at.x, ground(at), at.z);
                c.setOwner(this);
                c.setParticle(ParticleTypes.DRAGON_BREATH);
                c.setRadius(2.2F + i * 0.4F);
                c.setDuration(100);
                c.setRadiusPerTick((7.0F - c.getRadius()) / c.getDuration() * 0.3F);
                c.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1));
                c.getPersistentData().putBoolean("xw_xeno_breath", true);
                level.addFreshEntity(c);
            }
            for (int i = 0; i < 30; i++) {
                double s = 0.4 + random.nextDouble() * 0.6;
                level.sendParticles(ParticleTypes.DRAGON_BREATH, mouth.x, mouth.y, mouth.z, 0, dir.x * s + (random.nextDouble() - .5) * .3, -0.25, dir.z * s + (random.nextDouble() - .5) * .3, 1);
            }
            playSound(SoundEvents.ENDER_DRAGON_SHOOT, 3F, 0.7F);
            breaths++;
            XenoDragons.note("dragon breath (" + formName(form()) + ")");
        }
        if (t - 1 <= 0) act(ACT_NONE, 0);
    }

    private double ground(Vec3 at) {
        BlockPos p = BlockPos.containing(at.x, getY() + 2, at.z);
        for (int i = 0; i < 8 && level().getBlockState(p.below()).isAir(); i++) p = p.below();
        return p.getY();
    }

    // ---------------------------------------------------------------- ender fireballs (or DragonMineZ ki)

    private void shoot(ServerLevel level) {
        LivingEntity target = getTarget();
        if (target == null) return;
        if (form() >= SSJ1 && random.nextFloat() < 0.3F) {                       // a Saiyan's ki now and then
            int cast = com.xenowarriors.compat.DmzBossKi.fire(this, target, form() >= SSJ3);
            if (cast >= 0) { act(ACT_SHOOT, Math.max(SHOOT_TICKS, cast)); XenoDragons.note("ki " + (form() >= SSJ3 ? "beam" : "blast") + " (DragonMineZ) cast " + cast); return; }
        }
        int n = 1 + (form() + 1) / 2;                                            // 1, 2, 2, 3, 3, 4
        Vec3 eye = position().add(getViewVector(1F).multiply(1, 0, 1).normalize().scale(1.4)).add(0, 3.0, 0);
        for (int i = 0; i < n; i++) {
            Vec3 aim = target.position().add(0, target.getBbHeight() * 0.4, 0).add((random.nextDouble() - .5) * 3 * i, 0, (random.nextDouble() - .5) * 3 * i).subtract(eye);
            DragonFireball fb = new DragonFireball(level, this, aim.x, aim.y, aim.z);
            fb.moveTo(eye.x, eye.y, eye.z, getYRot(), getXRot());
            level.addFreshEntity(fb);
            fireballs++;
        }
        playSound(SoundEvents.ENDER_DRAGON_SHOOT, 3F, 1.0F);
    }

    // ---------------------------------------------------------------- wing gust

    private void gust(ServerLevel level) {
        int hit = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8, 3, 8), this::enemy)) {
            push(e, 2.4, 0.7, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6F);
            hit++;
        }
        for (int i = 0; i < 60; i++) {
            double ang = i * Math.PI * 2 / 60;
            level.sendParticles(ParticleTypes.CLOUD, getX() + Math.cos(ang) * 1.5, getY() + 0.5, getZ() + Math.sin(ang) * 1.5, 0, Math.cos(ang), 0.1, Math.sin(ang), 1.2);
        }
        playSound(SoundEvents.ENDER_DRAGON_FLAP, 5F, 0.5F);
        gusts++;
        XenoDragons.note("wing gust hit " + hit);
    }

    // ---------------------------------------------------------------- rise and dive

    private void startRise(LivingEntity target) {
        diveCd = cd(240);
        diveAt = target.position();
        act(ACT_RISE, RISE_TICKS);
        setNoGravity(true);
        playSound(SoundEvents.ENDER_DRAGON_FLAP, 4F, 0.8F);
    }

    private void tickRise(ServerLevel level, int t) {
        getNavigation().stop();
        LivingEntity target = getTarget();
        if (target != null && t > 6) diveAt = target.position();                 // (the marker follows until the last moment)
        setDeltaMovement(0, t > 6 ? 0.55 : 0.0, 0);
        if (diveAt != null) {                                                    // telegraph: a ring where it will land
            for (int i = 0; i < 16; i++) {
                double ang = i * Math.PI / 8 + t * 0.2;
                level.sendParticles(ParticleTypes.DRAGON_BREATH, diveAt.x + Math.cos(ang) * 4.5, diveAt.y + 0.15, diveAt.z + Math.sin(ang) * 4.5, 1, 0, 0, 0, 0);
            }
            lookAtPos(diveAt);
        }
        if (t - 1 <= 0) { act(ACT_DIVE, DIVE_TICKS); playSound(SoundEvents.ENDER_DRAGON_GROWL, 3F, 1.4F); }
    }

    private void lookAtPos(Vec3 p) {
        Vec3 d = p.subtract(position());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F);
        setYRot(yaw); setYHeadRot(yaw); yBodyRot = yaw;
    }

    private void tickDive(ServerLevel level, int t) {
        getNavigation().stop();
        Vec3 to = diveAt == null ? position().add(0, -1, 0) : diveAt;
        Vec3 d = to.subtract(position());
        double sp = 1.3 / CD[form()] * 0.6 + 0.6;
        setDeltaMovement(d.lengthSqr() < 1e-3 ? new Vec3(0, -1, 0) : d.normalize().scale(Math.min(sp, d.length())));
        level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.5, getZ(), 6, 0.4, 0.8, 0.4, 0.5);
        if (d.length() < 1.3 || (onGround() && t < DIVE_TICKS - 3) || t - 1 <= 0) {
            act(ACT_NONE, 0);
            setNoGravity(false);
            setDeltaMovement(Vec3.ZERO);
            int hit = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4.5, 2, 4.5), this::enemy)) {
                push(e, 1.2, 0.9, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.4F);
                hit++;
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.DRAGON_BREATH, getX(), getY() + 0.3, getZ(), 60, 3, 0.2, 3, 0.05);
            playSound(SoundEvents.GENERIC_EXPLODE, 3F, 0.8F);
            dives++;
            XenoDragons.note("dive slam hit " + hit);
        }
    }

    // ---------------------------------------------------------------- ender teleports (and the Ultra Instinct dodge)

    /** Teleports onto solid ground near {@code e}, between min and max blocks away. */
    public boolean blinkNear(Entity e, double min, double max) {
        ServerLevel level = (ServerLevel) level();
        for (int i = 0; i < 16; i++) {
            double ang = random.nextDouble() * Math.PI * 2, r = min + random.nextDouble() * (max - min);
            int x = (int) Math.floor(e.getX() + Math.cos(ang) * r), z = (int) Math.floor(e.getZ() + Math.sin(ang) * r);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinBuildHeight() + 1 || Math.abs(y - e.getY()) > 6) continue;
            BlockPos at = new BlockPos(x, y, z);
            if (!level.getBlockState(at.below()).isSolid() || !level.noCollision(this, getType().getAABB(x + 0.5, y, z + 0.5))) continue;
            level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.5, getZ(), 40, 0.5, 1.2, 0.5, 0.6);
            teleportTo(x + 0.5, y, z + 0.5);
            setDeltaMovement(Vec3.ZERO);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.5, getZ(), 30, 0.5, 1.2, 0.5, 0.3);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 2F, 0.6F);
            blinks++;
            return true;
        }
        return false;
    }

    private boolean dodge(DamageSource src) {
        Entity by = src.getEntity();
        Vec3 at = position();
        boolean ok = blinkNear(by != null ? by : this, 3, 5);
        if (!ok) return false;
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.5, at.z, 20, 0.4, 1.2, 0.4, 0.02);    // a silver afterimage
        playSound(ZombieSaiyanEntity.dmzSound("zanzoken", SoundEvents.ENDERMAN_TELEPORT), 1.5F, 1.4F);
        dodges++;
        dodgeCd = 12;
        if (dodges % 5 == 1) XenoDragons.note("Ultra Instinct dodge #" + dodges + " (" + src.getMsgId() + ")");
        return true;
    }

    // ---------------------------------------------------------------- End Crystals (from Super Saiyan 2, and again as a god)

    private void summonCrystals(ServerLevel level) {
        int made = 0, want = 2 - crystals.size();                                 // (two at most at a time)
        for (int i = 0; i < 12 && made < want; i++) {
            double ang = random.nextDouble() * Math.PI * 2, r = 7 + random.nextDouble() * 3;
            double x = getX() + Math.cos(ang) * r, z = getZ() + Math.sin(ang) * r;
            if (level.dimension() == Level.END && x * x + z * z < 8 * 8) continue;      // (never on the exit portal's crystal spots)
            int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            if (gy <= level.getMinBuildHeight() + 1) continue;
            EndCrystal c = new EndCrystal(level, x, gy + 4.5, z);
            c.setShowBottom(false);
            c.addTag(XenoDragons.CRYSTAL_TAG);
            level.addFreshEntity(c);
            crystals.add(c.getUUID());
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, gy + 4.5, z, 40, 0.5, 0.5, 0.5, 0.3);
            made++;
        }
        if (made > 0) say("End Crystals rise around the Xeno Dragon - break them, they heal it!", ChatFormatting.LIGHT_PURPLE);
        XenoDragons.note("summoned " + made + " end crystals (" + formName(form()) + ")");
    }

    /** Each living crystal heals it (0.6% a second) and beams at it. */
    private void crystals(ServerLevel level) {
        if (crystals.isEmpty()) return;
        crystals.removeIf(id -> !(level.getEntity(id) instanceof EndCrystal c) || !c.isAlive());
        for (UUID id : crystals) {
            if (!(level.getEntity(id) instanceof EndCrystal c)) continue;
            c.setBeamTarget(blockPosition().above(2));
            if (c.distanceToSqr(this) < 48 * 48 && !untouchable()) heal(getMaxHealth() * 0.006F);
        }
    }

    public int crystalCount() { return crystals.size(); }

    public void discardCrystals() {
        if (!(level() instanceof ServerLevel level)) return;
        for (UUID id : crystals) if (level.getEntity(id) instanceof EndCrystal c) c.discard();
        crystals.clear();
    }

    // ---------------------------------------------------------------- Dragon Bomb

    private void startBomb(ServerLevel level, LivingEntity target) {
        bombCd = cd(1250);                                                       // (UI: 400 ticks = 20 s)
        bombDamage = 0;
        comboT = -1;
        act(ACT_BOMB, BOMB_TICKS);
        setNoGravity(true);
        DragonBombEntity b = XenoDragons.DRAGON_BOMB.get().create(level);
        if (b == null) { act(ACT_NONE, 0); return; }
        b.start(this, target);
        level.addFreshEntity(b);
        bombId = b.getId();
        playSound(SoundEvents.BEACON_ACTIVATE, 5F, 0.5F);
        playSound(ZombieSaiyanEntity.dmzSound("ki_charge_loop", SoundEvents.BEACON_AMBIENT), 4F, 0.6F);
        say("The Xeno Dragon is gathering a DRAGON BOMB! Hit it hard to stop it - or run!", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        XenoDragons.note("dragon bomb: charging at " + target.getName().getString());
    }

    private DragonBombEntity bomb() { return level().getEntity(bombId) instanceof DragonBombEntity b && b.isAlive() ? b : null; }

    private void tickBomb(ServerLevel level, int t) {
        getNavigation().stop();
        setDeltaMovement(0, t > BOMB_TICKS - 20 ? 0.12 : 0, 0);                  // rises a little and holds it up
        lookTarget();
        DragonBombEntity b = bomb();
        if (b == null) { act(ACT_NONE, 0); setNoGravity(false); return; }
        if (t - 1 <= 0) {
            LivingEntity target = getTarget();
            b.throwAt(target != null ? target.position() : position().add(getViewVector(1F).scale(12)));
            bombsThrown++;
            act(ACT_THROW, THROW_TICKS);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 5F, 1.2F);
            XenoDragons.note("dragon bomb thrown at " + b.targetPos().toShortString() + " radius " + fmt(b.radius()));
        }
    }

    /** Stops the bomb: {@code interrupted} = hurt out of it (stunned), else just dropped. */
    private void stopBomb(boolean interrupted) {
        DragonBombEntity b = bomb();
        if (b != null && !b.thrown()) b.collapse();
        bombId = -1;
        if (interrupted) {
            bombsStopped++;
            act(ACT_STUN, STUN_TICKS);
            setNoGravity(false);
            bombCd = cd(700);
            say("The Dragon Bomb collapsed!", ChatFormatting.AQUA);
            XenoDragons.note("dragon bomb interrupted after " + Math.round(bombDamage) + " damage");
        }
    }

    // ---------------------------------------------------------------- damage, death

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide) return false;
        boolean bypass = src.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (!bypass && src.getEntity() == this) return false;                   // (its own breath and blasts)
        if (!bypass && untouchable()) return false;
        if (!bypass && (src.is(DamageTypeTags.IS_FALL) || src.is(DamageTypeTags.IS_FIRE))) return false;
        if (!bypass && act() == ACT_POWER) amount *= 0.3F;
        if (!bypass && form() == UI && act() != ACT_BOMB && act() != ACT_STUN && dodgeCd <= 0 && src.getEntity() != null && random.nextFloat() < DODGE && dodge(src)) return false;
        if (!bypass && act() == ACT_BOMB) {
            bombDamage += amount;
            if (bombDamage >= getMaxHealth() * BOMB_INTERRUPT) stopBomb(true);
        }
        if (!bypass && src.isIndirect() && form() < UI && random.nextFloat() < 0.25F && blinkCd <= 0 && free() && act() == ACT_NONE && src.getEntity() != null) {
            blinkCd = cd(90);                                                    // like an enderman: shot at, it may teleport closer
            blinkNear(src.getEntity(), 3, 6);
        }
        if (!bypass && form() < UI && amount >= getHealth()) {
            if (src.getEntity() instanceof LivingEntity l && l != this) setLastHurtByMob(l);
            stopBomb(false);
            if (form() == SSG) { knockOut(); return true; }
            if (form() == SSJ3) { startGod(); return true; }
            setHealth(Math.max(1F, getMaxHealth() * 0.2F));                    // (can't be one-shot past its forms)
            startPowerUp();
            return true;
        }
        return super.hurt(src, amount);
    }

    @Override
    public void die(DamageSource src) {
        super.die(src);
        if (!level().isClientSide) {
            discardCrystals();
            stopBomb(false);
            ((ServerLevel) level()).sendParticles(ParticleTypes.DRAGON_BREATH, getX(), getY() + 1.5, getZ(), 120, 1.5, 2, 1.5, 0.05);
            playSound(SoundEvents.ENDER_DRAGON_DEATH, 5F, 1.2F);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) discardCrystals();
        super.remove(reason);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource src, int looting, boolean hit) {
        super.dropCustomDeathLoot(src, looting, hit);
        if (form() == UI) XenoDragons.dropLoot(this, src);                       // (only beaten for real, in its last form)
        else XenoDragons.note("died as " + formName(form()) + " (" + src.getMsgId() + ") - no loot");
    }

    @Override
    public int getExperienceReward() { return form() == UI ? xpReward : 0; }

    @Override
    public boolean removeWhenFarAway(double dist) { return false; }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource src) { return false; }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.ENDER_DRAGON_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ENDER_DRAGON_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.ENDER_DRAGON_DEATH; }
    @Override
    protected float getSoundVolume() { return 2.0F; }
    @Override
    public int getAmbientSoundInterval() { return 200; }

    public static String formName(int f) { return f == BASE ? "base" : FORM_NAMES[Math.max(0, Math.min(UI, f))]; }

    static String fmt(double d) { return String.format(java.util.Locale.ROOT, "%.3f", d); }

    private void updateBar() {
        int f = form();
        String name = "Xeno Dragon" + (f > BASE ? " - " + FORM_NAMES[f] : "") + (ko() > 0 ? " (down)" : "");
        ChatFormatting c = f == UI ? ChatFormatting.WHITE : f == SSG ? ChatFormatting.RED : f > BASE ? ChatFormatting.GOLD : ChatFormatting.DARK_PURPLE;
        if (!bar.getName().getString().equals(name)) bar.setName(Component.literal(name).withStyle(c));
        bar.setColor(f == UI ? BossEvent.BossBarColor.WHITE : f == SSG ? BossEvent.BossBarColor.RED : f > BASE ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.PURPLE);
        bar.setProgress(Math.max(0F, Math.min(1F, getHealth() / getMaxHealth())));
    }

    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bar.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bar.removePlayer(p); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("XenoForm", form());
        tag.putInt("XenoKO", ko());
        tag.putBoolean("XenoScaled", scaled);
        tag.putInt("XenoCrystalFor", crystalFor);
        if (home != null) tag.putLong("XenoHome", home.asLong());
        int a = act();                                                           // (mid-cutscene or mid-ritual: picks it up again)
        if (a == ACT_EMERGE || a == ACT_GOD || a == ACT_AWAKEN || a == ACT_POWER) { tag.putInt("XenoAct", a); tag.putInt("XenoActT", actTicks()); }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(FORM, tag.getInt("XenoForm"));
        entityData.set(KO, tag.getInt("XenoKO"));
        scaled = tag.getBoolean("XenoScaled");
        crystalFor = tag.contains("XenoCrystalFor") ? tag.getInt("XenoCrystalFor") : -1;
        if (tag.contains("XenoHome")) home = BlockPos.of(tag.getLong("XenoHome"));
        if (tag.contains("XenoAct")) {
            int a = tag.getInt("XenoAct");
            if (a == ACT_EMERGE) { act(ACT_NONE, 0); setNoGravity(false); if (home != null) setPos(home.getX() + 0.5, home.getY(), home.getZ() + 0.5); }
            else act(a, Math.max(1, tag.getInt("XenoActT")));
        } else setNoGravity(false);
        if (hasCustomName()) bar.setName(getDisplayName());
    }

    // ---------------------------------------------------------------- tests

    /** (For the tests) jump to a form now. */
    public void setFormForTest(int f) { act(ACT_NONE, 0); entityData.set(KO, 0); setNoGravity(false); transform(f); }
    /** (For the tests) pose as if doing this. */
    public void poseForTest(int a, int t) { act(a, t); }
    /** (For the tests) the Dragon Bomb now, at the target. */
    public void bombForTest(LivingEntity target) { startBomb((ServerLevel) level(), target); }
    public void bombCdForTest(int t) { bombCd = t; }
    public int bombIdForTest() { return bombId; }
    /** (For the tests) skip the emerging cutscene. */
    public void skipEmergeForTest() { if (act() == ACT_EMERGE) entityData.set(ACT_T, 1); }
}
