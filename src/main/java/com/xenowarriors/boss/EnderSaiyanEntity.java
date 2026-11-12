package com.xenowarriors.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * The Ender Saiyan: an Enderman that woke up a warrior's power. Like the Zombie Saiyan it powers up as it's worn
 * down - Awakened at 75% health, Ascended at 50%, Overcharged at 25% - and, beaten Overcharged, it collapses, then
 * tears itself back together in its Void Form, fully healed and far stronger. Only that death is final.
 * <p>
 * It fights like an Enderman made strong:
 * <ul>
 *   <li><b>Blink strikes</b>: it teleports behind its target and hits (more often each stage).</li>
 *   <li><b>Void bolts</b>: purple bolts from its hands (two, then three at a time later on).</li>
 *   <li><b>Block throw</b>: it rips a block out of the ground (a copy - nothing is dug up) and hurls it.</li>
 *   <li><b>Void pull</b> (Ascended and up): drags everyone near toward it, then a burst throws them back.</li>
 *   <li><b>The stare</b>: look it in the eyes and it teleports right in front of you, faster for a moment.</li>
 *   <li><b>Dodges</b> arrows and other projectiles by teleporting, like Endermen - and water hurts it.</li>
 *   <li><b>Void Form</b>: afterimages - shadow copies of itself that lunge at you and burst.</li>
 * </ul>
 * Scaled to the nearby players' DragonMineZ power when that's installed, like the Zombie Saiyan.
 */
public class EnderSaiyanEntity extends Monster {
    public static final int BASE = 0, AWAKENED = 1, ASCENDED = 2, OVERCHARGED = 3, VOID = 4;
    public static final String[] FORM_NAMES = {"", "Awakened", "Ascended", "Overcharged", "Void Form"};
    /** The crest / aura colour per stage. */
    public static final int[] COLOR = {0xB35AF2, 0xC77DFF, 0xE8B3FF, 0xFFD27A, 0x6A00C8};
    public static final int KO_TICKS = 60, RISE_TICKS = 40, POWER_TICKS = 24;
    public static final int ACT_NONE = 0, ACT_POWER = 1, ACT_RISE = 2, ACT_THROW = 3, ACT_PULL = 4;
    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(EnderSaiyanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> KO = SynchedEntityData.defineId(EnderSaiyanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACT = SynchedEntityData.defineId(EnderSaiyanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACT_T = SynchedEntityData.defineId(EnderSaiyanEntity.class, EntityDataSerializers.INT);
    private static final UUID FORM_DMG = UUID.fromString("5a1e9c20-6d3b-4f7a-8e21-1b4d0c7a2e01"), FORM_SPD = UUID.fromString("5a1e9c20-6d3b-4f7a-8e21-1b4d0c7a2e02"),
            FORM_ARM = UUID.fromString("5a1e9c20-6d3b-4f7a-8e21-1b4d0c7a2e03"), FORM_HP = UUID.fromString("5a1e9c20-6d3b-4f7a-8e21-1b4d0c7a2e04"),
            SCALE_HP = UUID.fromString("5a1e9c20-6d3b-4f7a-8e21-1b4d0c7a2e05"), SCALE_DMG = UUID.fromString("5a1e9c20-6d3b-4f7a-8e21-1b4d0c7a2e06");

    private final ServerBossEvent bar = (ServerBossEvent) new ServerBossEvent(Component.literal("Ender Saiyan"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(true);
    private int meleeCd = 20, blinkCd = 60, boltCd = 50, throwCd = 120, pullCd = 260, stareCd = 0, cloneCd = 200;
    private boolean scaled;
    private BlockState thrown = Blocks.END_STONE.defaultBlockState();
    /** (Tests) moves used. */
    public int blinks, bolts, throwsDone, pulls, stares, dodges, clones;

    public EnderSaiyanEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 1200;
        setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 240.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
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
    public static String formName(int f) { return f == BASE ? "base" : FORM_NAMES[Math.max(0, Math.min(4, f))]; }

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

    public boolean free() { return ko() <= 0 && act() != ACT_POWER && act() != ACT_RISE && act() != ACT_THROW && act() != ACT_PULL; }

    // ---------------------------------------------------------------- scaling and stages

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
        int lvl = 45 + (int) Math.round(10 * Math.log(Math.max(1, dm)) / Math.log(2)) + form() * 5;
        getPersistentData().putInt("xw_lvl", lvl);
        getPersistentData().putBoolean("xw_boss", true);
        if (level() instanceof ServerLevel) com.xenowarriors.network.ModNetwork.sendToTrackingEntityAndSelf(new com.xenowarriors.network.EnemyLevelPacket(getId(), lvl), this);
        EnderSaiyans.note("scaled: dmz x" + String.format(java.util.Locale.ROOT, "%.2f", dm) + " hp " + Math.round(getMaxHealth()) + " lv " + lvl);
    }

    private void mod(net.minecraft.world.entity.ai.attributes.Attribute a, UUID id, double amount, AttributeModifier.Operation op) {
        AttributeInstance in = getAttribute(a);
        if (in == null) return;
        in.removeModifier(id);
        if (amount != 0) in.addPermanentModifier(new AttributeModifier(id, "Ender Saiyan", amount, op));
    }

    private void applyForm(int f) {
        double[] dmg = {0, 0.25, 0.5, 0.8, 1.6}, spd = {0, 0.1, 0.15, 0.22, 0.3}, arm = {0, 2, 4, 6, 12};
        mod(Attributes.ATTACK_DAMAGE, FORM_DMG, dmg[f], AttributeModifier.Operation.MULTIPLY_BASE);
        mod(Attributes.MOVEMENT_SPEED, FORM_SPD, spd[f], AttributeModifier.Operation.MULTIPLY_BASE);
        mod(Attributes.ARMOR, FORM_ARM, arm[f], AttributeModifier.Operation.ADDITION);
        mod(Attributes.MAX_HEALTH, FORM_HP, f == VOID ? 1.4 : 0, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    private DustParticleOptions dust(float size) {
        int c = COLOR[form()];
        return new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), size);
    }

    private void tell(String msg, ChatFormatting col) {
        for (ServerPlayer p : ((ServerLevel) level()).players()) if (p.distanceToSqr(this) < 56 * 56) p.displayClientMessage(Component.literal(msg).withStyle(col), true);
    }

    // ---------------------------------------------------------------- the fight

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (!scaled && tickCount >= 2) scaleToPlayers();
        updateBar();
        if (isInWaterRainOrBubble() && tickCount % 20 == 0) {                      // water burns it, like any Enderman
            super.hurt(damageSources().drown(), 2F);
            if (getTarget() != null && random.nextInt(3) == 0) teleportNear(getTarget(), 6);
        }
        if (form() > BASE && tickCount % 3 == 0) level.sendParticles(dust(1.3F), getX(), getY() + random.nextDouble() * 2.8, getZ(), 2, 0.45, 0.2, 0.45, 0);
        int ko = ko();
        if (ko > 0) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            entityData.set(KO, ko - 1);
            if (ko % 6 == 0) level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 0.5, getZ(), 12, 0.6, 0.3, 0.6, 0.5);
            if (ko - 1 == 0) startRise();
            return;
        }
        int a = act(), t = actTicks();
        if (a == ACT_POWER) {
            getNavigation().stop();
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            entityData.set(ACT_T, t - 1);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.4, getZ(), 8, 0.6, 1, 0.6, 0.05);
            if (t - 1 <= 0) { act(ACT_NONE, 0); transform(form() + 1); }
            return;
        }
        if (a == ACT_RISE) {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            entityData.set(ACT_T, t - 1);
            level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.4, getZ(), 30, 1.2, 1.5, 1.2, 1.2);
            if (t == RISE_TICKS - 10) burst(9, 0.9F);
            if (t - 1 <= 0) { act(ACT_NONE, 0); transform(VOID); setHealth(getMaxHealth()); scaleToPlayers(); setHealth(getMaxHealth()); }
            return;
        }
        if (a == ACT_THROW) { throwTick(t); return; }
        if (a == ACT_PULL) { pullTick(t); return; }
        float frac = getHealth() / getMaxHealth();
        int want = frac < 0.25F ? OVERCHARGED : frac < 0.5F ? ASCENDED : frac < 0.75F ? AWAKENED : BASE;
        if (form() < OVERCHARGED && want > form()) { startPowerUp(); return; }
        LivingEntity target = getTarget();
        meleeCd--; blinkCd--; boltCd--; throwCd--; pullCd--; stareCd--; cloneCd--;
        stare();
        if (target == null || !target.isAlive()) return;
        double d = distanceTo(target);
        if (form() == VOID && cloneCd <= 0) { afterimages(target); return; }
        if (form() >= ASCENDED && pullCd <= 0 && d < 10) { pullCd = form() == VOID ? 180 : 260; act(ACT_PULL, 30); pulls++; tell("The Ender Saiyan pulls at the void!", ChatFormatting.DARK_PURPLE); return; }
        if (blinkCd <= 0 && d > 2.5 && d < 24) { blinkStrike(target); return; }
        if (d < 3.6 && meleeCd <= 0) { melee(target); return; }
        if (d > 5 && d < 28 && hasLineOfSight(target)) {
            if (throwCd <= 0 && onGround()) { startThrow(); return; }
            if (boltCd <= 0) voidBolts(target);
        }
    }

    private void startPowerUp() {
        act(ACT_POWER, POWER_TICKS);
        playSound(SoundEvents.ENDERMAN_SCREAM, 2F, 0.6F);
        EnderSaiyans.note("powering up from " + formName(form()));
    }

    private void transform(int f) {
        entityData.set(FORM, f);
        applyForm(f);
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.5, getZ(), 2, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.5, getZ(), 80, 0.8, 1.4, 0.8, 0.3);
        playSound(SoundEvents.END_PORTAL_SPAWN, 1.5F, f == VOID ? 0.6F : 1.2F);
        tell("The Ender Saiyan is " + formName(f) + "!", f == VOID ? ChatFormatting.DARK_PURPLE : ChatFormatting.LIGHT_PURPLE);
        EnderSaiyans.note("form -> " + formName(f) + " hp " + Math.round(getHealth()) + "/" + Math.round(getMaxHealth()));
    }

    private void knockOut() {
        setHealth(1F);
        entityData.set(KO, KO_TICKS);
        act(ACT_NONE, 0);
        getNavigation().stop();
        playSound(SoundEvents.ENDERMAN_DEATH, 2F, 0.6F);
        tell("The Ender Saiyan falls apart into the void...", ChatFormatting.GRAY);
        EnderSaiyans.note("knocked out Overcharged");
    }

    private void startRise() {
        act(ACT_RISE, RISE_TICKS);
        setHealth(Math.max(getHealth(), getMaxHealth() * 0.1F));
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 4F, 0.9F);
        playSound(SoundEvents.ENDERMAN_SCREAM, 3F, 0.4F);
        tell("...it pulls itself back together out of the void!", ChatFormatting.DARK_PURPLE);
    }

    /** A void burst: everyone near is thrown back and hurt. */
    private void burst(double r, float mult) {
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        for (int i = 0; i < 40; i++) {
            double ang = i * Math.PI * 2 / 40;
            level.sendParticles(dust(1.8F), getX() + Math.cos(ang) * 2, getY() + 0.4, getZ() + Math.sin(ang) * 2, 0, Math.cos(ang), 0.02, Math.sin(ang), 0.8);
        }
        playSound(SoundEvents.GENERIC_EXPLODE, 2.5F, 0.6F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r), e -> e != this && e.isAlive() && !(e instanceof EnderSaiyanEntity))) {
            Vec3 d = e.position().subtract(position()).multiply(1, 0, 1);
            d = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
            e.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * mult);
            e.setDeltaMovement(d.x * 2.0, 0.6, d.z * 2.0);
            e.hurtMarked = true;
        }
    }

    private void melee(LivingEntity target) {
        meleeCd = Math.max(14, 30 - form() * 4);
        swing(InteractionHand.MAIN_HAND, true);
        doHurtTarget(target);
    }

    /** Teleports somewhere near this spot (a safe floor), like an Enderman. */
    private boolean teleportTo(Vec3 at) {
        Vec3 from = position();
        for (int i = 0; i < 10; i++) {
            double x = at.x + (i == 0 ? 0 : (random.nextDouble() - 0.5) * 3), z = at.z + (i == 0 ? 0 : (random.nextDouble() - 0.5) * 3);
            if (randomTeleport(x, at.y + 1, z, false)) {
                ServerLevel level = (ServerLevel) level();
                level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.4, from.z, 30, 0.4, 1, 0.4, 0.4);
                level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.4, getZ(), 30, 0.4, 1, 0.4, 0.4);
                level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, getSoundSource(), 1F, 1F);
                playSound(SoundEvents.ENDERMAN_TELEPORT, 1F, 1F);
                return true;
            }
        }
        return false;
    }

    private boolean teleportNear(LivingEntity t, double r) {
        double a = random.nextDouble() * Math.PI * 2;
        return teleportTo(t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
    }

    /** Behind its target in a blink, and a hit. */
    private void blinkStrike(LivingEntity t) {
        blinkCd = Math.max(40, 120 - form() * 20);
        Vec3 back = t.getLookAngle().multiply(1, 0, 1);
        back = back.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : back.normalize();
        if (!teleportTo(t.position().subtract(back.scale(1.6)))) return;
        blinks++;
        getLookControl().setLookAt(t, 90F, 90F);
        lookAt(t, 180F, 90F);
        swing(InteractionHand.MAIN_HAND, true);
        t.invulnerableTime = 0;
        t.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.1F);
        meleeCd = 10;
    }

    /** Purple bolts from its hands at the target (instant, they need a clear line). */
    private void voidBolts(LivingEntity t) {
        boltCd = Math.max(26, 70 - form() * 10);
        int n = form() >= OVERCHARGED ? 3 : form() >= AWAKENED ? 2 : 1;
        ServerLevel level = (ServerLevel) level();
        for (int k = 0; k < n; k++) {
            final int kk = k;
            com.xenowarriors.skill.ServerScheduler.later(k * 6, () -> {
                if (!isAlive() || !t.isAlive()) return;
                Vec3 from = getEyePosition().add(getLookAngle().scale(0.6)), to = t.getBoundingBox().getCenter();
                Vec3 d = to.subtract(from);
                int steps = (int) Math.ceil(d.length() * 2);
                for (int i = 0; i <= steps; i++) {
                    Vec3 p = from.add(d.scale(i / (double) steps));
                    level.sendParticles(dust(1.1F), p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
                }
                if (hasLineOfSight(t)) {
                    t.invulnerableTime = 0;
                    t.hurt(damageSources().indirectMagic(this, this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.55F);
                }
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.SHULKER_SHOOT, getSoundSource(), 1F, 0.6F + kk * 0.1F);
            });
        }
        bolts++;
    }

    private void startThrow() {
        throwCd = Math.max(80, 160 - form() * 20);
        BlockPos below = blockPosition().below();
        BlockState s = level().getBlockState(below);
        thrown = s.isAir() || !s.isSolid() || s.getDestroySpeed(level(), below) < 0 ? Blocks.END_STONE.defaultBlockState() : s;
        act(ACT_THROW, 20);
        playSound(SoundEvents.STONE_BREAK, 1.5F, 0.7F);
    }

    /** Rips a block out (a copy - nothing is dug up), holds it up, hurls it. */
    private void throwTick(int t) {
        getNavigation().stop();
        entityData.set(ACT_T, t - 1);
        LivingEntity target = getTarget();
        ServerLevel level = (ServerLevel) level();
        if (target != null) getLookControl().setLookAt(target, 60F, 60F);
        Vec3 hold = position().add(0, 3.3, 0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, thrown), hold.x, hold.y, hold.z, 3, 0.3, 0.3, 0.3, 0);
        if (t - 1 > 0) return;
        act(ACT_NONE, 0);
        if (target == null || !target.isAlive()) return;
        throwsDone++;
        swing(InteractionHand.MAIN_HAND, true);
        Vec3 to = target.position();
        Vec3 d = to.subtract(hold);
        int flight = Math.max(4, (int) (d.length() / 1.6));
        for (int i = 1; i <= flight; i++) {
            final int ii = i;
            com.xenowarriors.skill.ServerScheduler.later(i, () -> {
                double f = ii / (double) flight;
                Vec3 p = hold.add(d.scale(f)).add(0, Math.sin(f * Math.PI) * 2, 0);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, thrown), p.x, p.y, p.z, 6, 0.25, 0.25, 0.25, 0);
                if (ii == flight) {
                    level.sendParticles(ParticleTypes.EXPLOSION, to.x, to.y + 0.5, to.z, 1, 0, 0, 0, 0);
                    level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE, getSoundSource(), 1.2F, 1.1F);
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(to, to).inflate(2.5), e -> e != this && e.isAlive() && !(e instanceof EnderSaiyanEntity))) {
                        e.invulnerableTime = 0;
                        e.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.2F);
                    }
                }
            });
        }
    }

    /** Drags everyone near in, then throws them back with a burst. */
    private void pullTick(int t) {
        getNavigation().stop();
        entityData.set(ACT_T, t - 1);
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.4, getZ(), 12, 3, 1, 3, 0.2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(11), e -> e != this && e.isAlive() && !(e instanceof EnderSaiyanEntity))) {
            Vec3 d = position().subtract(e.position()).multiply(1, 0, 1);
            if (d.lengthSqr() < 2.5 * 2.5) continue;
            e.setDeltaMovement(e.getDeltaMovement().scale(0.5).add(d.normalize().scale(0.35)));
            e.hurtMarked = true;
        }
        if (t - 1 <= 0) { act(ACT_NONE, 0); burst(5, 0.7F); }
    }

    /** Look it in the eyes and it comes for you. */
    private void stare() {
        if (stareCd > 0 || tickCount % 5 != 0) return;
        for (Player p : level().players()) {
            if (p.isCreative() || p.isSpectator() || p.distanceToSqr(this) > 40 * 40 || p.distanceToSqr(this) < 6 * 6 || !lookingAtMe(p)) continue;
            stareCd = 160;
            stares++;
            setTarget(p);
            if (teleportTo(p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.2)))) {
                addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 60, 1));
                playSound(SoundEvents.ENDERMAN_STARE, 2F, 0.7F);
                p.displayClientMessage(Component.literal("You looked it in the eyes...").withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            return;
        }
    }

    private boolean lookingAtMe(Player p) {
        Vec3 look = p.getViewVector(1F).normalize();
        Vec3 to = new Vec3(getX() - p.getX(), getEyeY() - p.getEyeY(), getZ() - p.getZ());
        double dist = to.length();
        return look.dot(to.normalize()) > 1.0 - 0.025 / dist && p.hasLineOfSight(this);
    }

    /** Void Form: shadow copies of itself lunge at the target and burst. */
    private void afterimages(LivingEntity t) {
        cloneCd = 220;
        clones++;
        ServerLevel level = (ServerLevel) level();
        playSound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 2F, 0.6F);
        for (int k = 0; k < 3; k++) {
            double a = k * Math.PI * 2 / 3 + random.nextDouble();
            Vec3 start = t.position().add(Math.cos(a) * 6, 0, Math.sin(a) * 6);
            for (int i = 0; i <= 10; i++) {
                final int ii = i;
                com.xenowarriors.skill.ServerScheduler.later(i * 2, () -> {
                    Vec3 p = start.add(t.position().subtract(start).scale(ii / 10.0));
                    level.sendParticles(dust(2.2F), p.x, p.y + 1.4, p.z, 10, 0.25, 1.0, 0.25, 0);
                    if (ii == 10 && t.isAlive()) {
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 1, p.z, 30, 0.5, 0.8, 0.5, 0.3);
                        t.invulnerableTime = 0;
                        t.hurt(damageSources().indirectMagic(this, this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6F);
                    }
                });
            }
        }
    }

    // ---------------------------------------------------------------- hits

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide) return false;
        boolean bypass = src.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (!bypass && (ko() > 0 || act() == ACT_RISE)) return false;
        if (!bypass && src.getDirectEntity() instanceof Projectile && src.getDirectEntity() != src.getEntity()
                && ko() <= 0 && random.nextFloat() < 0.4F + form() * 0.1F) {            // dodges projectiles by teleporting, like Endermen
            LivingEntity t = getTarget() != null ? getTarget() : src.getEntity() instanceof LivingEntity l ? l : null;
            if (t != null && teleportNear(t, 4)) { dodges++; return false; }
        }
        if (!bypass && act() == ACT_POWER) amount *= 0.3F;
        if (!bypass && form() < VOID && amount >= getHealth()) {
            if (src.getEntity() instanceof LivingEntity l && l != this) setLastHurtByMob(l);
            if (form() == OVERCHARGED) { knockOut(); return true; }
            setHealth(Math.max(1F, getMaxHealth() * 0.2F));
            startPowerUp();
            return true;
        }
        return super.hurt(src, amount);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource src, int looting, boolean hit) {
        super.dropCustomDeathLoot(src, looting, hit);
        if (form() == VOID) EnderSaiyans.dropLoot(this);
        else EnderSaiyans.note("died as " + formName(form()) + " (" + src.getMsgId() + ") - no loot");
    }

    @Override public boolean removeWhenFarAway(double dist) { return form() == BASE && ko() <= 0; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.ENDERMAN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ENDERMAN_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ENDERMAN_DEATH; }
    @Override public float getVoicePitch() { return 0.7F; }
    @Override public boolean causeFallDamage(float dist, float mult, DamageSource src) { return false; }
    @Override protected float getStandingEyeHeight(Pose pose, EntityDimensions d) { return 2.55F; }

    private void updateBar() {
        int f = form();
        String name = "Ender Saiyan" + (f > BASE ? " - " + FORM_NAMES[f] : "") + (ko() > 0 ? " (fallen apart)" : "");
        if (!bar.getName().getString().equals(name)) bar.setName(Component.literal(name).withStyle(f == VOID ? ChatFormatting.DARK_PURPLE : ChatFormatting.LIGHT_PURPLE));
        bar.setColor(f >= OVERCHARGED ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.PURPLE);
        if (f == VOID) bar.setColor(BossEvent.BossBarColor.RED);
        bar.setProgress(Math.max(0F, Math.min(1F, getHealth() / getMaxHealth())));
    }

    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bar.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bar.removePlayer(p); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("EnderForm", form());
        tag.putInt("EnderKO", ko());
        tag.putBoolean("EnderScaled", scaled);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(FORM, tag.getInt("EnderForm"));
        entityData.set(KO, tag.getInt("EnderKO"));
        scaled = tag.getBoolean("EnderScaled");
        applyForm(form());
    }

    /** (Tests) */
    public void setFormForTest(int f) { act(ACT_NONE, 0); entityData.set(KO, 0); transform(f); }
    public void knockOutForTest() { knockOut(); }
    public void blinkForTest(LivingEntity t) { blinkStrike(t); }
    public void boltsForTest(LivingEntity t) { voidBolts(t); }
    public void throwForTest() { startThrow(); }
    public void pullForTest() { act(ACT_PULL, 30); pulls++; }
    public void clonesForTest(LivingEntity t) { afterimages(t); }
}
