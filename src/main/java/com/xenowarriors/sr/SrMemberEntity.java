package com.xenowarriors.sr;

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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A Sky Rebellion member: a Skylander in the black SR hoodie (the white eye on the chest), sworn to "Father" - and a
 * demon vessel. Hostile to anyone not wearing the SR hoodie. At half health the demon inside wakes up: faster,
 * stronger, red-black aura.
 */
public class SrMemberEntity extends Monster {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(SrMemberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DEMON = SynchedEntityData.defineId(SrMemberEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WINGS = SynchedEntityData.defineId(SrMemberEntity.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.55F, 0.05F, 0.1F), 1.2F);
    private static final String[] LINES = {"For Father.", "Nice hoodie. Welcome, brother.", "The Royal capital will be ours.", "Father sees everything.", "Stay out of the rebellion's way."};
    private BlockPos post;
    private int leapCd;

    public SrMemberEntity(EntityType<? extends Monster> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 50.0).add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ARMOR, 4.0).add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(VARIANT, 0); entityData.define(DEMON, false); entityData.define(WINGS, false); }

    /** The named Sky Rebellion bosses (variants 2..8): each one rarely leads a camp. */
    public static final String[] NAMED = {"senju", "logan", "kill", "keita", "maddie", "persephone", "ava"};
    private static final String[] TITLES = {"Senju, SR Leader", "Logan", "Kill", "Keita", "Maddie", "Persephone", "Ava"};
    private static final String[][] TAUNTS = {
            {"You came all this way to kneel?", "Father's will is my blade."},
            {"Heh. Fresh meat!", "Try and keep up!"},
            {"Don't move. This'll be quick.", "You're in our camp now."},
            {"Ooh, a visitor!", "Let's make this fun."},
            {"...", "You shouldn't have looked at me."},
            {"I don't want to do this. She does.", "Run. Please."},
            {"Leave me alone.", "You'll regret waking me."}};
    private final net.minecraft.server.level.ServerBossEvent bar = new net.minecraft.server.level.ServerBossEvent(Component.literal("SR"),
            net.minecraft.world.BossEvent.BossBarColor.RED, net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10);
    private int specialCd = 80;

    public int named() { return entityData.get(VARIANT) - 2; }
    public boolean isNamed() { return named() >= 0 && named() < NAMED.length; }
    public String key() { return isNamed() ? NAMED[named()] : ""; }

    public String look() {
        if (isNamed()) return "sr_" + key() + (demon() && (key().equals("persephone") || key().equals("ava")) ? "_demon" : "");
        return "sr_" + (entityData.get(VARIANT) % 2 + 1);
    }
    public boolean demon() { return entityData.get(DEMON); }
    /** Wings out: they're Skylanders, but only summon their wings when they need to fly. */
    public boolean wings() { return entityData.get(WINGS); }
    private int stuck, landed;

    private void setWings(boolean w) {
        if (w == wings()) return;
        entityData.set(WINGS, w);
        setNoGravity(w);
        if (w) {
            getNavigation().stop();
            if (level() instanceof ServerLevel l) {
                l.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.2, getZ(), 10, .5, .3, .5, .03);
                l.playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 0.8F, 0.8F);
            }
        }
    }

    /** Needs to fly: its target is up high or out of reach on foot, or it's falling a long way. */
    private boolean needsFlight(net.minecraft.world.entity.LivingEntity t) {
        if (fallDistance > 4F) return true;
        if (t == null) return false;
        double up = t.getY() - getY();
        if (up > 3.5 && distanceToSqr(t) < 24 * 24) return true;
        return stuck > 40;
    }

    private void flyTick(net.minecraft.world.entity.LivingEntity t) {
        Vec3 dest = t != null ? t.position().add(0, 0.5, 0) : position().add(0, -1, 0);
        Vec3 to = dest.subtract(position());
        Vec3 want = to.length() > 0.38 ? to.normalize().scale(0.38) : to;
        Vec3 v = getDeltaMovement().lerp(want, 0.18);
        setDeltaMovement(v);
        if (v.horizontalDistanceSqr() > 1.0E-4) { setYRot((float) Math.toDegrees(Math.atan2(v.z, v.x)) - 90F); yBodyRot = getYRot(); yHeadRot = getYRot(); }
        if (t != null && distanceTo(t) < 2.2 && tickCount % 15 == 0) doHurtTarget(t);
        // put the wings away once it's back on its feet and can walk to the target
        boolean reachable = t == null || (Math.abs(t.getY() - getY()) < 2.5);
        if ((onGround() || horizontalCollision && verticalCollision) && reachable) { if (++landed > 10) { setWings(false); stuck = 0; } }
        else landed = 0;
        if (t == null && onGround()) setWings(false);
    }

    public void setup(int variant, BlockPos at) {
        entityData.set(VARIANT, variant);
        post = at;
        restrictTo(at, 10);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(variant % 2 == 0 ? Items.IRON_SWORD : Items.STONE_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0.05F);
        setPersistenceRequired();
        if (isNamed()) makeBoss();
    }

    /** (UI test only) show the wings / the demon form. */
    public void uiTestPose(boolean wings, boolean demon) {
        if (wings) { entityData.set(WINGS, true); setNoGravity(true); }
        if (demon) entityData.set(DEMON, true);
    }

    /** A named member: tougher, a boss bar, their own special move and loot. */
    private void makeBoss() {
        String k = key();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(k.equals("senju") ? 300 : 200);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(k.equals("senju") ? 14 : 11);
        getAttribute(Attributes.ARMOR).setBaseValue(10);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(k.equals("kill") || k.equals("keita") ? 0.34 : 0.31);
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6);
        setHealth(getMaxHealth());
        ItemStack hand = switch (k) {
            case "senju" -> new ItemStack(com.xenowarriors.item.ModItems.SENJU_BLADE.get());
            case "logan", "kill", "keita" -> new ItemStack(Items.IRON_SWORD);
            default -> ItemStack.EMPTY;
        };
        setItemSlot(EquipmentSlot.MAINHAND, hand);
        setDropChance(EquipmentSlot.MAINHAND, 0F);
        setCustomName(Component.literal(TITLES[named()]).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        setCustomNameVisible(true);
        xpReward = 120;
        restrictTo(post == null ? blockPosition() : post, 14);
    }

    @Override public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer p) { super.startSeenByPlayer(p); if (isNamed()) bar.addPlayer(p); }
    @Override public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer p) { super.stopSeenByPlayer(p); bar.removePlayer(p); }

    @Override
    public void setTarget(net.minecraft.world.entity.LivingEntity t) {
        if (isNamed() && t instanceof Player p && getTarget() != t && !level().isClientSide)
            p.sendSystemMessage(Component.literal(TITLES[named()] + ": ").withStyle(ChatFormatting.DARK_RED)
                    .append(Component.literal(TAUNTS[named()][random.nextInt(2)]).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        super.setTarget(t);
    }

    /** Each one's special move, every few seconds in a fight. */
    private void special(net.minecraft.world.entity.LivingEntity t) {
        ServerLevel l = (ServerLevel) level();
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        switch (key()) {
            case "senju" -> {        // Father's blade: a sweeping ring of red shards
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 12;
                    l.sendParticles(DARK, getX() + Math.cos(a) * 4, getY() + 1, getZ() + Math.sin(a) * 4, 3, .2, .4, .2, 0);
                }
                l.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2F, 0.6F);
                for (var e : l.getEntitiesOfClass(Player.class, getBoundingBox().inflate(5))) { e.hurt(damageSources().mobAttack(this), dmg * 0.8F); e.knockback(1.0, getX() - e.getX(), getZ() - e.getZ()); }
            }
            case "logan" -> {        // wings out, up, and a diving strike
                setWings(true);
                setDeltaMovement(0, 1.1, 0); hurtMarked = true;
                l.playSound(null, blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 1F, 1.2F);
            }
            case "kill", "keita" -> { // a dash slash straight through
                Vec3 d = t.position().subtract(position()).normalize();
                setDeltaMovement(d.x * 1.6, 0.2, d.z * 1.6); hurtMarked = true;
                l.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1, getZ(), 4, .5, .3, .5, 0);
                if (distanceTo(t) < 6) t.hurt(damageSources().mobAttack(this), dmg);
                l.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1F, 1.4F);
            }
            case "maddie" -> {       // her KA: the red eye - meet her gaze and you're frozen in dread
                for (var p : l.getEntitiesOfClass(Player.class, getBoundingBox().inflate(12), p -> p.hasLineOfSight(this))) {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3));
                    p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
                    p.displayClientMessage(Component.literal("Maddie's eye glows red...").withStyle(ChatFormatting.RED), true);
                }
                l.playSound(null, blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.5F, 1.2F);
            }
            default -> {             // the demons: a burst of dark fire
                l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 50, 2, .6, 2, .05);
                l.playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1F, 0.6F);
                for (var e : l.getEntitiesOfClass(Player.class, getBoundingBox().inflate(4))) { e.hurt(damageSources().mobAttack(this), dmg * (demon() ? 0.9F : 0.6F)); e.setSecondsOnFire(4); }
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource src, int looting, boolean hit) {
        super.dropCustomDeathLoot(src, looting, hit);
        if (!isNamed()) return;
        spawnAtLocation(new ItemStack(Items.DIAMOND, 2 + random.nextInt(3 + looting)));
        spawnAtLocation(new ItemStack(Items.EMERALD, 4 + random.nextInt(6)));
        if (key().equals("senju")) {
            spawnAtLocation(new ItemStack(Items.NETHERITE_SCRAP, 2));
            if (random.nextFloat() < 0.35F + 0.1F * looting) spawnAtLocation(new ItemStack(com.xenowarriors.item.ModItems.SENJU_BLADE.get()));
        }
        if (random.nextFloat() < 0.5F) spawnAtLocation(new ItemStack(com.xenowarriors.item.ModItems.SR_HOODIE.get()));
        if (src.getEntity() instanceof Player p)
            p.level().players().stream().filter(o -> o.distanceTo(this) < 48).forEach(o -> o.sendSystemMessage(
                    Component.literal(TITLES[named()] + " has been defeated!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
    }

    /** Wearing the SR hoodie: they take you for one of their own. */
    public static boolean disguised(Player p) {
        return p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof com.xenowarriors.item.SrHoodieItem
                || p.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof com.xenowarriors.item.SrHoodieItem;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.55));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(SrMemberEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> !disguised((Player) p)));
    }

    @Override
    protected InteractionResult mobInteract(Player p, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND && disguised(p) && getTarget() != p) {
            p.sendSystemMessage(Component.literal("SR Member: ").withStyle(ChatFormatting.DARK_RED)
                    .append(Component.literal(LINES[p.getRandom().nextInt(LINES.length)]).withStyle(ChatFormatting.GRAY)));
            getLookControl().setLookAt(p);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean r = super.hurt(src, amount);
        if (r && !level().isClientSide && !demon() && getHealth() < getMaxHealth() * 0.5F) {        // the demon wakes up
            entityData.set(DEMON, true);
            addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, -1, 1, false, false));
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 1, false, false));
            heal(8F);
            ServerLevel l = (ServerLevel) level();
            l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 30, .4, .8, .4, .08);
            l.playSound(null, blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.7F, 1.4F);
            if (isNamed()) {
                String k = key();
                Component line = Component.literal(k.equals("persephone") ? "Persephone's horns break through - the demon is awake!"
                        : k.equals("ava") ? "Ava's eyes turn red - the demon is awake!" : TITLES[named()] + "'s demon wakes up!");
                l.players().stream().filter(o -> o.distanceTo(this) < 32).forEach(o -> o.sendSystemMessage(line.copy().withStyle(ChatFormatting.DARK_RED)));
                bar.setColor(net.minecraft.world.BossEvent.BossBarColor.PURPLE);
            } else setCustomName(Component.literal("SR Member (Demon Vessel)").withStyle(ChatFormatting.DARK_RED));
        }
        return r;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (leapCd > 0) leapCd--;
        var t = getTarget();
        if (isNamed()) {
            bar.setName(getDisplayName());
            bar.setProgress(getHealth() / getMaxHealth());
            if (specialCd > 0) specialCd--;
            else if (t != null && distanceTo(t) < 10) { special(t); specialCd = 100 + random.nextInt(60); }
        }
        // stuck: has a target, isn't getting closer on foot
        if (t != null && !wings()) { if (getNavigation().isDone() && distanceTo(t) > 3) stuck++; else stuck = Math.max(0, stuck - 2); }
        if (!wings() && needsFlight(t)) setWings(true);
        if (wings()) { fallDistance = 0; flyTick(t); }
        if (wings()) { if (demon() && tickCount % 4 == 0) ((ServerLevel) level()).sendParticles(DARK, getX(), getY() + 1, getZ(), 2, .3, .6, .3, 0); return; }
        if (t != null && onGround() && leapCd == 0 && distanceTo(t) > 4 && distanceTo(t) < 10) {          // wing-assisted leap
            Vec3 d = t.position().subtract(position());
            setDeltaMovement(d.x * 0.17, 0.55, d.z * 0.17); hurtMarked = true; leapCd = 70;
            ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 6, .3, .1, .3, .02);
        }
        if (demon() && tickCount % 4 == 0) ((ServerLevel) level()).sendParticles(DARK, getX(), getY() + 1, getZ(), 2, .3, .6, .3, 0);
    }

    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return !wings() && super.causeFallDamage(d, m, s); }

    @Override public boolean removeWhenFarAway(double d) { return post == null && super.removeWhenFarAway(d); }

    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t); t.putInt("Variant", entityData.get(VARIANT)); t.putBoolean("Demon", demon()); t.putBoolean("Wings", wings());
        if (post != null) t.putLong("Post", post.asLong());
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t); entityData.set(VARIANT, t.getInt("Variant")); if (hasCustomName()) bar.setName(getCustomName()); entityData.set(DEMON, t.getBoolean("Demon")); setWings(t.getBoolean("Wings"));
        if (t.contains("Post")) { post = BlockPos.of(t.getLong("Post")); restrictTo(post, 10); }
    }
}
