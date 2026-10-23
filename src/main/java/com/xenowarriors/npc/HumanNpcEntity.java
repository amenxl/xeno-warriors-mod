package com.xenowarriors.npc;

import com.xenowarriors.corrupted.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.List;

/**
 * Townsfolk: they wander villages and the countryside, don't trade, and some carry swords and fight corrupted souls
 * off. A soul can take one over - openly (dark, red-eyed, hostile) or hidden (no sign at all). If nobody notices and
 * tells another person within three days, the soul takes them for good: they become one of the permanently possessed.
 * Told in time, people hold them down; worn down enough, the soul is driven out and they're themselves again.
 */
public class HumanNpcEntity extends PathfinderMob {
    public static final long PERMANENT_AFTER = 3 * 24000L;
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> POSSESSED = SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FIGHTER = SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private long possessedSince = -1;
    private boolean reported;

    public HumanNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setCanPickUpLoot(true);
        if (getNavigation() instanceof GroundPathNavigation nav) nav.setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANT, 0);
        entityData.define(POSSESSED, false);
        entityData.define(FIGHTER, false);
    }

    public int variant() { return entityData.get(VARIANT); }
    public boolean possessed() { return entityData.get(POSSESSED); }
    public boolean fighter() { return entityData.get(FIGHTER); }
    /** Has a soul in them at all (showing or hidden). */
    public boolean carriesSoul() { return possessed() || HiddenSouls.hidden(this); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, LivingEntity.class, 10F, 1.0, 1.35, this::scary) {
            @Override
            public boolean canUse() { return !fighter() && !possessed() && super.canUse(); }
        });
        goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.75));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, this::wantsToFight));
    }

    /** Something a normal person runs from. */
    private boolean scary(LivingEntity e) {
        return e instanceof CorruptedSoulEntity || e instanceof CorruptedVillagerEntity || e instanceof DarkPossessedEntity
                || (e instanceof HumanNpcEntity h && h.possessed()) || CorruptedAnimals.isCorrupted(e) || e instanceof Enemy;
    }

    private boolean wantsToFight(LivingEntity e) {
        if (possessed()) return e instanceof Player p && !p.isCreative() && !p.isSpectator() || (e instanceof HumanNpcEntity h && !h.possessed())
                || e instanceof Villager;
        if (!fighter() || HiddenSouls.hidden(this)) return false;
        return e instanceof CorruptedSoulEntity || e instanceof CorruptedVillagerEntity || e instanceof DarkPossessedEntity
                || (e instanceof HumanNpcEntity h && h.possessed()) || CorruptedAnimals.isCorrupted(e);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data, CompoundTag tag) {
        var r = level.getRandom();
        entityData.set(VARIANT, r.nextInt(NpcLooks.HUMANS.length));
        boolean fighter = r.nextFloat() < 0.3F || NpcLooks.human(variant()).equals("soldier");
        entityData.set(FIGHTER, fighter);
        if (fighter) setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(r.nextFloat() < 0.6F ? Items.IRON_SWORD : Items.STONE_SWORD));
        else if (r.nextFloat() < 0.08F) setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0.1F);
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    /** Only weapons - better than what they hold. */
    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        if (possessed()) return false;
        Item it = stack.getItem();
        if (!(it instanceof SwordItem || it instanceof AxeItem || it instanceof TridentItem)) return false;
        ItemStack held = getMainHandItem();
        return held.isEmpty() || damageOf(stack) > damageOf(held);
    }

    private static double damageOf(ItemStack s) {
        return s.getItem() instanceof SwordItem sw ? sw.getDamage() : s.getItem() instanceof AxeItem ax ? ax.getAttackDamage() : s.isEmpty() ? 0 : 1;
    }

    @Override
    public boolean canHoldItem(ItemStack stack) {
        return wantsToPickUp(stack);
    }

    @Override
    protected void pickUpItem(net.minecraft.world.entity.item.ItemEntity item) {
        super.pickUpItem(item);
        if (getMainHandItem().getItem() instanceof SwordItem || getMainHandItem().getItem() instanceof AxeItem) entityData.set(FIGHTER, true);  // armed now: will fight
    }

    // ------------------------------------------------------------------ possession

    /** A soul takes them: in the open (hostile, dark) or hidden (no sign). */
    public void possess(boolean hidden) {
        possessedSince = level().getGameTime();
        reported = false;
        if (hidden) HiddenSouls.hide(this);
        else entityData.set(POSSESSED, true);
        setTarget(null);
        setPersistenceRequired();
    }

    /** The soul's gone: themselves again. */
    public void free() {
        entityData.set(POSSESSED, false);
        HiddenSouls.clear(this);
        possessedSince = -1;
        reported = false;
        setTarget(null);
        if (level() instanceof ServerLevel s) s.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.6, getZ(), 14, 0.3, 0.3, 0.3, 0.05);
        playSound(SoundEvents.SOUL_ESCAPE, 1F, 1F);
    }

    /** Someone told another person: the clock stops, the soul is exposed and people hold them down. */
    public void report(LivingEntity by) {
        if (!carriesSoul()) return;
        reported = true;
        if (HiddenSouls.hidden(this)) { HiddenSouls.clear(this); entityData.set(POSSESSED, true); }
        for (HumanNpcEntity h : level().getEntitiesOfClass(HumanNpcEntity.class, getBoundingBox().inflate(32), h -> h.fighter() && !h.carriesSoul())) {
            h.setTarget(this);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // held down after being reported: worn low enough, the soul is driven out instead of them dying
        if (!level().isClientSide && possessed() && reported && getHealth() - amount < getMaxHealth() * 0.25F) {
            setHealth(getMaxHealth() * 0.25F);
            free();
            return true;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (possessed() && random.nextInt(3) == 0) {
                level().addParticle(CorruptedFx.BLACK, getRandomX(0.5), getRandomY(), getRandomZ(0.5), 0, 0.02, 0);
            }
            return;
        }
        if (tickCount % 100 == 0 && carriesSoul() && !reported && possessedSince >= 0
                && level().getGameTime() - possessedSince >= PERMANENT_AFTER) becomePermanent();
    }

    /** Three days and nobody helped: the soul has them for good. */
    private void becomePermanent() {
        if (!(level() instanceof ServerLevel s)) return;
        DarkPossessedEntity dark = ModNpcs.DARK_POSSESSED.get().create(s);
        if (dark == null) return;
        dark.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
        dark.setVariant(random.nextInt(NpcLooks.DARK.length));
        dark.setPersistenceRequired();
        s.addFreshEntity(dark);
        s.sendParticles(CorruptedFx.BLACK, getX(), getY() + 1, getZ(), 40, 0.4, 0.8, 0.4, 0.05);
        playSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 1F, 0.6F);
        discard();
    }

    // ------------------------------------------------------------------ talking to them

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || possessed()) return super.mobInteract(player, hand);
        if (level().isClientSide) return InteractionResult.SUCCESS;
        List<HumanNpcEntity> near = level().getEntitiesOfClass(HumanNpcEntity.class, getBoundingBox().inflate(64), h -> h != this && h.carriesSoul() && !h.reported);
        if (!near.isEmpty()) {
            for (HumanNpcEntity h : near) h.report(this);
            if (fighter()) setTarget(near.get(0));
            player.sendSystemMessage(Component.literal("\"Possessed?! Over there? We'll hold them down - don't let it get away!\"").withStyle(ChatFormatting.YELLOW));
        } else {
            String[] lines = {"\"Hey. Strange things going on lately.\"", "\"Keep an eye out for those dark spirits.\"", "\"Nice day, huh?\"",
                    "\"If you see anyone acting weird, tell somebody.\""};
            player.sendSystemMessage(Component.literal(lines[random.nextInt(lines.length)]).withStyle(ChatFormatting.GRAY));
        }
        getLookControl().setLookAt(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return !carriesSoul() && !isPersistenceRequired() && dist > 128 * 128;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
        tag.putBoolean("Possessed", possessed());
        tag.putBoolean("Fighter", fighter());
        tag.putLong("PossessedSince", possessedSince);
        tag.putBoolean("Reported", reported);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(VARIANT, tag.getInt("Variant"));
        entityData.set(POSSESSED, tag.getBoolean("Possessed"));
        entityData.set(FIGHTER, tag.getBoolean("Fighter"));
        possessedSince = tag.contains("PossessedSince") ? tag.getLong("PossessedSince") : -1;
        reported = tag.getBoolean("Reported");
    }
}
