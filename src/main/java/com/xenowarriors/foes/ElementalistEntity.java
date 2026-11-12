package com.xenowarriors.foes;

import com.xenowarriors.ka.Element;
import com.xenowarriors.ka.Elements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * An Elementalist: a rogue who already has both their elements ("everyone eventually gets two") and hunts strong
 * Karma users at night. They switch between the two after every cast - bolts, waves, charged blasts you can clash -
 * and fight hand to hand between. Beaten, they may leave an Element Crystal of one of their elements.
 */
public class ElementalistEntity extends PathfinderMob implements Enemy {
    private static final EntityDataAccessor<String> ELS = SynchedEntityData.defineId(ElementalistEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(ElementalistEntity.class, EntityDataSerializers.INT);
    /** Wizards: robes, a tall pointed hat, a staff with an orb (tools/xw/npcs/wizards.py). */
    public static final String[] LOOKS = {"wiz_storm", "wiz_ember", "wiz_sage", "wiz_frost", "wiz_shadow"};
    private long lastCd = -1;

    public ElementalistEntity(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 44).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ARMOR, 4);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(ELS, ""); entityData.define(LOOK, 0); }

    public String look() { return LOOKS[Math.floorMod(entityData.get(LOOK), LOOKS.length)]; }

    public Element first() { String[] a = entityData.get(ELS).split(","); return a.length > 0 ? Element.byId(a[0]) : null; }
    public Element second() { String[] a = entityData.get(ELS).split(","); return a.length > 1 ? Element.byId(a[1]) : null; }

    /** Two different elements, a look, a name. */
    public void setup(Element a, Element b, int look) {
        entityData.set(ELS, a.id() + "," + b.id());
        entityData.set(LOOK, look);
        Elements.give(this, a);
        setCustomName(Component.literal(a.title() + " / " + b.title() + " Elementalist").withStyle(st -> st.withColor(a.color)));
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);                     // (its staff is part of its robes)
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> !((Player) p).isCreative() && !p.isSpectator()));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, com.xenowarriors.bot.PartyBotEntity.class, 10, true, false, e -> true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || tickCount % 5 != 0) return;
        long cd = getPersistentData().getLong("xw_el_cd");                         // (Elements sets it at every cast: then the other element)
        if (lastCd >= 0 && cd != lastCd) {
            Element now = Elements.of(this), a = first(), b = second();
            if (a != null && b != null) Elements.give(this, now == a ? b : a);
        }
        lastCd = cd;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource src, int looting, boolean hit) {
        super.dropCustomDeathLoot(src, looting, hit);
        if (!(src.getEntity() instanceof Player) && !(src.getEntity() instanceof com.xenowarriors.bot.PartyBotEntity)) return;
        if (random.nextFloat() < Foes.CRYSTAL_CHANCE + looting * 0.05F) {
            Element el = random.nextBoolean() ? first() : second();
            if (el != null) spawnAtLocation(com.xenowarriors.you.YouItems.crystal(el));
        }
        if (random.nextInt(3) == 0) spawnAtLocation(new ItemStack(Items.AMETHYST_SHARD, 1 + random.nextInt(2)));
    }

    @Override public int getExperienceReward() { return 15; }
    @Override protected boolean shouldDespawnInPeaceful() { return true; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putString("Els", entityData.get(ELS)); t.putInt("Look", entityData.get(LOOK)); }

    @Override
    public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); entityData.set(ELS, t.getString("Els")); entityData.set(LOOK, t.getInt("Look")); }

}
