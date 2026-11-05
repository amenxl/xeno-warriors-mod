package com.xenowarriors.boss;

import net.minecraft.ChatFormatting;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * The Zombie Saiyan (with DragonMineZ): a risen Saiyan warrior in a torn orange gi. A rare night-time boss that goes
 * Super Saiyan as it's worn down - SSJ1 at 80% health, SSJ2 (lightning) at 50%, SSJ3 (long hair) at 25% - fighting
 * with melee combos, dashes and ki blasts (DragonMineZ's own ki attacks when it's there; Kamehameha from SSJ2).
 * Beaten as a Super Saiyan 3 it collapses (knocked out, ~3 s), then gets back up screaming - a shockwave throws
 * everyone back - and comes back fully healed as a Super Saiyan 4, far stronger. Only that death is final.
 * Scaled to the nearby players' DragonMineZ power (not the Lv-1 rule of DragonMineZ's own mobs) so it's always a fight.
 */
public class ZombieSaiyanEntity extends Monster {
    public static final int BASE = 0, SSJ1 = 1, SSJ2 = 2, SSJ3 = 3, SSJ4 = 4;
    public static final int ACT_NONE = 0, ACT_POWER = 1, ACT_SCREAM = 2, ACT_KI = 3, ACT_COMBO = 4;
    public static final String[] FORM_NAMES = {"", "Super Saiyan", "Super Saiyan 2", "Super Saiyan 3", "Super Saiyan 4"};
    /** Aura colour per form (0 = none). */
    public static final int[] AURA = {0, 0xFFD23A, 0xFFE04A, 0xFFC830, 0xFF3A1A};
    public static final int KO_TICKS = 60, SCREAM_TICKS = 40, POWER_TICKS = 24;
    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(ZombieSaiyanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> KO = SynchedEntityData.defineId(ZombieSaiyanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACT = SynchedEntityData.defineId(ZombieSaiyanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACT_T = SynchedEntityData.defineId(ZombieSaiyanEntity.class, EntityDataSerializers.INT);
    private static final UUID FORM_DMG = UUID.fromString("2f7d3a40-5b1e-4c8a-9e21-7a5d0c3b1e01"), FORM_SPD = UUID.fromString("2f7d3a40-5b1e-4c8a-9e21-7a5d0c3b1e02"),
            FORM_ARM = UUID.fromString("2f7d3a40-5b1e-4c8a-9e21-7a5d0c3b1e03"), FORM_HP = UUID.fromString("2f7d3a40-5b1e-4c8a-9e21-7a5d0c3b1e04"),
            SCALE_HP = UUID.fromString("2f7d3a40-5b1e-4c8a-9e21-7a5d0c3b1e05"), SCALE_DMG = UUID.fromString("2f7d3a40-5b1e-4c8a-9e21-7a5d0c3b1e06");

    private final ServerBossEvent bar = (ServerBossEvent) new ServerBossEvent(Component.literal("Zombie Saiyan"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(true);
    private int meleeCd = 20, kiCd = 60, dashCd = 40, beamCd = 200, comboT = -1;
    private boolean scaled;
    private String kiKind = "";

    public ZombieSaiyanEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 1000;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 260.0)
                .add(Attributes.ATTACK_DAMAGE, 11.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
                .add(Attributes.FOLLOW_RANGE, 48.0);
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
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true) {
            @Override public boolean canUse() { return free() && super.canUse(); }
            @Override public boolean canContinueToUse() { return free() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, com.xenowarriors.bot.PartyBotEntity.class, true));
    }

    /** Not knocked out, screaming or powering up. */
    public boolean free() { return ko() <= 0 && act() != ACT_POWER && act() != ACT_SCREAM; }

    // ---------------------------------------------------------------- scaling

    /** Scales to the strongest player nearby: health with their DragonMineZ damage, its hits with their health. */
    public void scaleToPlayers() {
        scaled = true;
        Player p = level().getNearestPlayer(this, 96);
        double dm = 1, hp = 1;
        if (p != null) {
            dm = Math.max(com.xenowarriors.compat.DmzScaling.multiplier(p, true), com.xenowarriors.compat.DmzScaling.multiplier(p, false));
            hp = Math.max(1.0, p.getMaxHealth() / 20.0);
        }
        float frac = getHealth() / getMaxHealth();
        mod(Attributes.MAX_HEALTH, SCALE_HP, Math.max(0, dm - 1.0), AttributeModifier.Operation.MULTIPLY_TOTAL);
        mod(Attributes.ATTACK_DAMAGE, SCALE_DMG, Math.max(0, Math.pow(hp, 0.9) - 1.0), AttributeModifier.Operation.MULTIPLY_TOTAL);
        setHealth(getMaxHealth() * Math.max(0.05F, frac));
        int lvl = 40 + (int) Math.round(10 * Math.log(Math.max(1, dm)) / Math.log(2)) + form() * 5;
        getPersistentData().putInt("xw_lvl", lvl);                                // (its own level: EnemyScaling leaves it alone)
        getPersistentData().putBoolean("xw_boss", true);
        if (level() instanceof ServerLevel) com.xenowarriors.network.ModNetwork.sendToTrackingEntityAndSelf(new com.xenowarriors.network.EnemyLevelPacket(getId(), lvl), this);
        ZombieSaiyans.note("scaled to " + (p == null ? "nobody" : p.getGameProfile().getName()) + ": dmz x" + String.format(java.util.Locale.ROOT, "%.2f", dm)
                + " hp " + Math.round(getMaxHealth()) + " dmg " + String.format(java.util.Locale.ROOT, "%.1f", getAttributeValue(Attributes.ATTACK_DAMAGE)) + " lv " + lvl);
    }

    private void mod(net.minecraft.world.entity.ai.attributes.Attribute a, UUID id, double amount, AttributeModifier.Operation op) {
        AttributeInstance in = getAttribute(a);
        if (in == null) return;
        in.removeModifier(id);
        if (amount != 0) in.addPermanentModifier(new AttributeModifier(id, "Zombie Saiyan", amount, op));
    }

    private void applyForm(int f) {
        double[] dmg = {0, 0.25, 0.5, 0.8, 1.6}, spd = {0, 0.1, 0.15, 0.2, 0.3}, arm = {0, 2, 4, 6, 12};
        mod(Attributes.ATTACK_DAMAGE, FORM_DMG, dmg[f], AttributeModifier.Operation.MULTIPLY_BASE);
        mod(Attributes.MOVEMENT_SPEED, FORM_SPD, spd[f], AttributeModifier.Operation.MULTIPLY_BASE);
        mod(Attributes.ARMOR, FORM_ARM, arm[f], AttributeModifier.Operation.ADDITION);
        mod(Attributes.MAX_HEALTH, FORM_HP, f == SSJ4 ? 1.4 : 0, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    // ---------------------------------------------------------------- the fight

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (!scaled && tickCount >= 2) scaleToPlayers();
        updateBar();
        int ko = ko();
        if (ko > 0) {                                                            // knocked out on the ground
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            entityData.set(KO, ko - 1);
            if (ko % 10 == 0) level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.3, getZ(), 4, 0.6, 0.1, 0.6, 0.01);
            if (ko - 1 == 0) startScream();
            return;
        }
        int a = act(), t = actTicks();
        if (a == ACT_POWER) {                                                     // powering up into the next form
            getNavigation().stop();
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            entityData.set(ACT_T, t - 1);
            if (t % 4 == 0) level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 3, 1.2, 0.05, 1.2, 0.08);
            if (t - 1 <= 0) { act(ACT_NONE, 0); transform(form() + 1); }
            return;
        }
        if (a == ACT_SCREAM) {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            entityData.set(ACT_T, t - 1);
            if (t == SCREAM_TICKS - 10) shockwave();
            if (t % 3 == 0) level.sendParticles(ParticleTypes.EXPLOSION, getX() + (random.nextDouble() - .5) * 3, getY() + random.nextDouble() * 2, getZ() + (random.nextDouble() - .5) * 3, 1, 0, 0, 0, 0);
            if (t - 1 <= 0) { act(ACT_NONE, 0); transform(SSJ4); setHealth(getMaxHealth()); scaleToPlayers(); setHealth(getMaxHealth()); }
            return;
        }
        if (a == ACT_KI) {                                                        // (charging: it stands still)
            getNavigation().stop();
            entityData.set(ACT_T, t - 1);
            if (getTarget() != null) getLookControl().setLookAt(getTarget(), 60F, 60F);
            if (t - 1 <= 0) act(ACT_NONE, 0);
            return;
        }
        // going Super Saiyan as it's worn down
        float frac = getHealth() / getMaxHealth();
        int want = frac < 0.25F ? SSJ3 : frac < 0.5F ? SSJ2 : frac < 0.8F ? SSJ1 : BASE;
        if (form() < SSJ3 && want > form()) { startPowerUp(); return; }
        LivingEntity target = getTarget();
        meleeCd--; kiCd--; dashCd--; beamCd--;
        if (comboT >= 0) { combo(target); return; }
        if (target == null || !target.isAlive()) return;
        double d = distanceTo(target);
        if (d < 3.4 && meleeCd <= 0) { comboT = 0; act(ACT_COMBO, 16); return; }
        if (d > 6 && d < 18 && dashCd <= 0 && hasLineOfSight(target) && onGround()) { dash(target); return; }
        if (d > 5 && d < 32 && hasLineOfSight(target)) {
            if (form() >= SSJ2 && beamCd <= 0) { beamCd = form() == SSJ4 ? 160 : 260; kiCd = 60; fireKi(target, true); return; }
            if (kiCd <= 0) { kiCd = Math.max(25, 70 - form() * 11); fireKi(target, false); }
        }
    }

    private void startPowerUp() {
        act(ACT_POWER, POWER_TICKS);
        comboT = -1;
        playSound(dmzSound("aura_start", SoundEvents.BEACON_POWER_SELECT), 2F, 0.8F);
        ZombieSaiyans.note("powering up from " + formName(form()) + " at " + Math.round(getHealth()) + "/" + Math.round(getMaxHealth()));
    }

    private void transform(int f) {
        entityData.set(FORM, f);
        applyForm(f);
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 2, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
        playSound(dmzSound("transform_on", SoundEvents.LIGHTNING_BOLT_THUNDER), 3F, f == SSJ4 ? 0.6F : 1.0F);
        if (f == SSJ2 || f == SSJ3) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 40, 0.6, 1, 0.6, 0.3);
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(this) < 48 * 48)
                p.displayClientMessage(Component.literal("The Zombie Saiyan went " + formName(f) + "!").withStyle(f == SSJ4 ? ChatFormatting.RED : ChatFormatting.GOLD), true);
        ZombieSaiyans.note("form -> " + formName(f) + " hp " + Math.round(getHealth()) + "/" + Math.round(getMaxHealth()) + " dmg " + String.format(java.util.Locale.ROOT, "%.1f", getAttributeValue(Attributes.ATTACK_DAMAGE)));
    }

    private void knockOut() {
        setHealth(1F);
        entityData.set(KO, KO_TICKS);
        act(ACT_NONE, 0);
        comboT = -1;
        getNavigation().stop();
        playSound(SoundEvents.ZOMBIE_DEATH, 2F, 0.6F);
        for (ServerPlayer p : ((ServerLevel) level()).players())
            if (p.distanceToSqr(this) < 48 * 48) p.displayClientMessage(Component.literal("The Zombie Saiyan collapses...").withStyle(ChatFormatting.GRAY), true);
        ZombieSaiyans.note("knocked out at Super Saiyan 3");
    }

    private void startScream() {
        act(ACT_SCREAM, SCREAM_TICKS);
        setHealth(Math.max(getHealth(), getMaxHealth() * 0.1F));
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 4F, 0.7F);
        playSound(SoundEvents.RAVAGER_ROAR, 3F, 0.6F);
        playSound(dmzSound("oozaru_growl_player", SoundEvents.WARDEN_ROAR), 3F, 1.0F);
        for (ServerPlayer p : ((ServerLevel) level()).players())
            if (p.distanceToSqr(this) < 64 * 64) p.displayClientMessage(Component.literal("...it gets back up, screaming!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        ZombieSaiyans.note("gets back up and screams");
    }

    /** The scream's blast: everyone near is thrown back and hurt. */
    private void shockwave() {
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 2, 1, 0.2, 1, 0);
        for (int i = 0; i < 48; i++) {
            double ang = i * Math.PI * 2 / 48;
            level.sendParticles(ParticleTypes.CLOUD, getX() + Math.cos(ang) * 2, getY() + 0.3, getZ() + Math.sin(ang) * 2, 0, Math.cos(ang), 0.05, Math.sin(ang), 0.9);
        }
        playSound(SoundEvents.GENERIC_EXPLODE, 3F, 0.7F);
        int hit = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8), e -> e != this && e.isAlive())) {
            Vec3 d = e.position().subtract(position()).multiply(1, 0, 1);
            if (d.lengthSqr() < 1e-4) d = new Vec3(1, 0, 0);
            d = d.normalize();
            e.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8F);
            e.setDeltaMovement(d.x * 2.2, 0.7, d.z * 2.2);
            e.hurtMarked = true;
            hit++;
        }
        ZombieSaiyans.note("scream shockwave hit " + hit);
    }

    private void combo(LivingEntity target) {
        int t = comboT++;
        if (target == null || !target.isAlive() || distanceTo(target) > 4.5) { comboT = -1; act(ACT_NONE, 0); meleeCd = 20; return; }
        getLookControl().setLookAt(target, 60F, 60F);
        getNavigation().moveTo(target, 1.0);
        if (t == 0 || t == 6 || t == 12) {
            boolean fin = t == 12;
            swing(fin ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND, true);
            target.invulnerableTime = 0;
            float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * (fin ? 1.3F : 0.6F);
            if (target.hurt(damageSources().mobAttack(this), dmg) && fin) {
                Vec3 d = target.position().subtract(position());
                target.knockback(1.6, -d.x, -d.z);
                target.hurtMarked = true;
                ((ServerLevel) level()).sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1, target.getZ(), 1, 0, 0, 0, 0);
            }
            playSound(dmzSound(fin ? "critic_punch1" : "punch" + (1 + random.nextInt(3)), SoundEvents.PLAYER_ATTACK_STRONG), 1.2F, 0.9F + random.nextFloat() * 0.2F);
        }
        if (t >= 14) { comboT = -1; act(ACT_NONE, 0); meleeCd = Math.max(16, 40 - form() * 5); }
    }

    private void dash(LivingEntity target) {
        dashCd = 70;
        Vec3 d = target.position().subtract(position()).normalize();
        setDeltaMovement(d.x * 1.7, 0.25, d.z * 1.7);
        hurtMarked = true;
        playSound(dmzSound("zanzoken", SoundEvents.ENDERMAN_TELEPORT), 1.5F, 1.0F);
        ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 8, 0.3, 0.5, 0.3, 0.05);
    }

    private void fireKi(LivingEntity target, boolean beam) {
        int cast = com.xenowarriors.compat.DmzBossKi.fire(this, target, beam);
        if (cast >= 0) {
            act(ACT_KI, Math.max(6, cast));
            kiKind = beam ? "beam" : "blast";
            ZombieSaiyans.note((beam ? "kamehameha" : "ki blast") + " (DragonMineZ) at " + target.getType().getDescriptionId() + " cast " + cast);
            return;
        }
        // without DragonMineZ: its own ki bolts
        Vec3 dir = target.getEyePosition().subtract(getEyePosition()).normalize();
        int n = beam ? 5 : 1;
        for (int i = 0; i < n; i++) {
            final int k = i;
            com.xenowarriors.skill.ServerScheduler.later(i * 3, () -> {
                if (!isAlive() || !(level() instanceof ServerLevel)) return;
                var bolt = new com.xenowarriors.corrupted.SoulBoltEntity(level(), this, dir.x, dir.y, dir.z);
                bolt.setPos(getX() + dir.x, getEyeY() - 0.3, getZ() + dir.z);
                bolt.getPersistentData().putBoolean("xw_saiyan_ki", true);
                level().addFreshEntity(bolt);
            });
        }
        act(ACT_KI, beam ? 16 : 8);
        playSound(SoundEvents.BLAZE_SHOOT, 1.2F, 1.4F);
    }

    /** How hard its ki hits (a beam much harder). */
    public float kiDamage(boolean beam) {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * (beam ? 2.2F : 1.1F);
    }

    // ---------------------------------------------------------------- damage, death

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide) return false;
        boolean bypass = src.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (!bypass && (ko() > 0 || act() == ACT_SCREAM)) return false;
        if (!bypass && act() == ACT_POWER) amount *= 0.3F;
        if (!bypass && form() < SSJ4 && amount >= getHealth()) {
            if (src.getEntity() instanceof LivingEntity l && l != this) setLastHurtByMob(l);
            if (form() == SSJ3) { knockOut(); return true; }
            setHealth(Math.max(1F, getMaxHealth() * 0.2F));                    // (can't be one-shot past its forms)
            startPowerUp();
            return true;
        }
        return super.hurt(src, amount);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource src, int looting, boolean hit) {
        super.dropCustomDeathLoot(src, looting, hit);
        ZombieSaiyans.dropLoot(this, src);
    }

    @Override
    public boolean removeWhenFarAway(double dist) { return form() == BASE && ko() <= 0; }   // (once the fight's on, it stays)

    @Override
    public MobType getMobType() { return MobType.UNDEAD; }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.ZOMBIE_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ZOMBIE_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ZOMBIE_DEATH; }
    @Override public float getVoicePitch() { return 0.75F; }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource src) { return false; }    // (a Saiyan lands on his feet)

    static SoundEvent dmzSound(String name, SoundEvent fallback) {
        SoundEvent s = net.minecraftforge.fml.ModList.get().isLoaded("dragonminez") ? ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.resources.ResourceLocation("dragonminez", name)) : null;
        return s != null ? s : fallback;
    }

    public static String formName(int f) { return f == BASE ? "base" : FORM_NAMES[Math.max(0, Math.min(4, f))]; }

    private void updateBar() {
        int f = form();
        String name = "Zombie Saiyan" + (f > BASE ? " - " + FORM_NAMES[f] : "") + (ko() > 0 ? " (knocked out)" : "");
        if (!bar.getName().getString().equals(name)) bar.setName(Component.literal(name).withStyle(f == SSJ4 ? ChatFormatting.RED : f > BASE ? ChatFormatting.GOLD : ChatFormatting.DARK_GREEN));
        bar.setColor(f == SSJ4 ? BossEvent.BossBarColor.RED : f > BASE ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.GREEN);
        bar.setProgress(Math.max(0F, Math.min(1F, getHealth() / getMaxHealth())));
    }

    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bar.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bar.removePlayer(p); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SaiyanForm", form());
        tag.putInt("SaiyanKO", ko());
        tag.putBoolean("SaiyanScaled", scaled);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(FORM, tag.getInt("SaiyanForm"));
        entityData.set(KO, tag.getInt("SaiyanKO"));
        scaled = tag.getBoolean("SaiyanScaled");
        if (hasCustomName()) bar.setName(getDisplayName());
    }

    /** (For the tests) jump to a form now. */
    public void setFormForTest(int f) { act(ACT_NONE, 0); entityData.set(KO, 0); transform(f); }
    /** (For the tests) knocked out now / its ki attacks. */
    public void knockOutForTest() { knockOut(); }
    public String kiKindForTest() { return kiKind; }

}
