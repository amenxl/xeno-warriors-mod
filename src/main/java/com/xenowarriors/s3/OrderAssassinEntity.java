package com.xenowarriors.s3;

import com.xenowarriors.character.Seasons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A member of the Order of Assassins (Season 3). They only kill criminals, and only take jobs from their own -
 * in the show that's Chase and Zack, so a player can't take a contract here. Leave them alone: they hide their will
 * to kill, and if you start something they vanish for a moment and strike from where you're not looking.
 */
public class OrderAssassinEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(OrderAssassinEntity.class, EntityDataSerializers.INT);
    private static final String[] LINES = {
            "The Order only takes jobs from its own. You're not one of us.",
            "We only go after criminals. Whatever you're here for, it isn't work.",
            "Revenge seekers come through here all the time. They don't last.",
            "A good assassin hides his will to kill. You'd never make the cut.",
            "No contracts for outsiders. Come back never.",
            "If you're looking for someone in the Order and you don't see them, you're in the wrong place."};
    private BlockPos post;
    private int vanishCd;

    public OrderAssassinEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 70.0).add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33).add(Attributes.ARMOR, 6.0).add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(VARIANT, 0); }

    public String look() { return "order_" + (entityData.get(VARIANT) % 2 + 1); }

    public void setup(int variant, BlockPos at) {
        entityData.set(VARIANT, variant);
        post = at;
        restrictTo(at, 7);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(variant % 2 == 0 ? Items.IRON_SWORD : Items.NETHERITE_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0F);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.35, true));
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    @Override
    protected InteractionResult mobInteract(Player p, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            String line = LINES[p.getRandom().nextInt(LINES.length)];
            p.sendSystemMessage(Component.literal("Order Assassin: ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(line).withStyle(ChatFormatting.GRAY)));
            getLookControl().setLookAt(p);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean r = super.hurt(src, amount);
        if (r && !level().isClientSide && vanishCd <= 0 && src.getEntity() != null) {   // hides his will to kill: gone for a moment
            addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false));
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false));
            vanishCd = 200;
        }
        return r;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (vanishCd > 0) vanishCd--;
        if (!level().isClientSide && tickCount % 200 == 0 && !Seasons.unlocked(level().getServer(), 3)) discard();   // Season 3 content
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }

    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t); t.putInt("Variant", entityData.get(VARIANT));
        if (post != null) t.putLong("Post", post.asLong());
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t); entityData.set(VARIANT, t.getInt("Variant"));
        if (t.contains("Post")) { post = BlockPos.of(t.getLong("Post")); restrictTo(post, 7); }
    }
}
