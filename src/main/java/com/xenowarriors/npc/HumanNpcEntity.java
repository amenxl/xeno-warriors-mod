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
    private static final EntityDataAccessor<Boolean> SKY_HATER = SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.BOOLEAN);
    /** What they're reacting to right now (see HumanReactions), shown as a pose. */
    private static final EntityDataAccessor<Integer> REACTION = SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private int reactUntil;
    /** What they've gathered (logs, stone, ore, planks, ingots), and the hut they built. */
    private final net.minecraft.world.SimpleContainer pack = new net.minecraft.world.SimpleContainer(12);
    private net.minecraft.core.BlockPos home;
    public net.minecraft.world.SimpleContainer pack() { return pack; }
    public net.minecraft.core.BlockPos home() { return home; }
    public void setHome(net.minecraft.core.BlockPos p) { home = p; }
    private static final EntityDataAccessor<Boolean> RAIDER = SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static Boolean dmz;
    private int rhalixTauntAt = -10000;
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
        entityData.define(SKY_HATER, false);
        entityData.define(FLYING, false);
        entityData.define(RAIDER, false);
        entityData.define(REACTION, 0);
    }

    public int variant() { return entityData.get(VARIANT); }
    public boolean possessed() { return entityData.get(POSSESSED); }
    public boolean fighter() { return entityData.get(FIGHTER); }
    public boolean flying() { return entityData.get(FLYING); }

    static boolean dmz() {
        if (dmz == null) dmz = net.minecraftforge.fml.ModList.get() != null && net.minecraftforge.fml.ModList.get().isLoaded("dragonminez");
        return dmz;
    }

    /** Who shoots what: the gunner fires bullets, the phone and briefcase guys throw theirs, fighters throw Ki (DMZ). */
    public boolean canShoot(NpcRangedGoal.Kind kind) {
        if (possessed() || HiddenSouls.hidden(this)) return false;
        String look = NpcLooks.human(variant());
        return switch (kind) {
            case BULLET -> look.equals("gunner");
            case PHONE -> look.equals("phone");
            case BRIEFCASE -> look.equals("briefcase");
            case KI -> fighter() && dmz() && !look.equals("gunner");
            default -> false;
        };
    }

    /** Part of a Sky Hater raid on a village: hostile to everyone there, Mikiko included (and he can fight back). */
    public boolean raider() { return entityData.get(RAIDER); }

    public int reaction() { return entityData.get(REACTION); }

    /** Shows a reaction for a while (a stronger one isn't cut short by a weaker one). */
    public void react(int type, int ticks) {
        if (level().isClientSide || carriesSoul()) return;
        int cur = reaction();
        if (cur != 0 && tickCount < reactUntil && HumanReactions.priority(cur) > HumanReactions.priority(type)) return;
        entityData.set(REACTION, type);
        if (net.minecraftforge.fml.ModList.get().isLoaded("epicfight")) com.xenowarriors.compat.epicfight.EfPoses.reaction(this, type);
        reactUntil = tickCount + ticks;
    }

    public void makeRaider(boolean captain) {
        entityData.set(SKY_HATER, true);
        entityData.set(FIGHTER, true);
        entityData.set(RAIDER, true);
        setCustomName(Component.literal(captain ? "Sky Hater Captain" : "Sky Hater Raider").withStyle(captain ? ChatFormatting.GOLD : ChatFormatting.RED));
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(captain ? Items.DIAMOND_SWORD : random.nextFloat() < 0.4F ? Items.IRON_AXE : Items.IRON_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0.05F);
        if (captain) {
            setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            var hp = getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(60);
            var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmg != null) dmg.setBaseValue(5);
            setHealth(getMaxHealth());
        }
        setPersistenceRequired();
    }

    /** Hates Skylanders: goes for their wings (red armband). */
    public boolean skyHater() { return entityData.get(SKY_HATER); }

    static boolean isMikiko(Entity e) {
        return e instanceof Player p && com.xenowarriors.character.ModCapabilities.getType(p) == com.xenowarriors.character.CharacterType.MIKIKO;
    }

    static boolean isRhalixWithWings(Entity e) {
        return e instanceof Player p && !p.isCreative() && !p.isSpectator()
                && com.xenowarriors.character.ModCapabilities.getType(p) == com.xenowarriors.character.CharacterType.RHALIX
                && com.xenowarriors.character.RhalixWings.out(p);
    }

    /** People never go after Mikiko (only a soul wearing them would). */
    @Override
    public void setTarget(LivingEntity target) {
        if (target != null && !possessed() && !raider() && isMikiko(target)) target = null;
        super.setTarget(target);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (!possessed() && !raider() && isMikiko(target)) return false;
        return super.canAttack(target);
    }
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
        goalSelector.addGoal(3, new NpcRangedGoal(this, NpcRangedGoal.Kind.BULLET, 28, 3, 18));
        goalSelector.addGoal(3, new NpcRangedGoal(this, NpcRangedGoal.Kind.PHONE, 60, 3, 12));
        goalSelector.addGoal(3, new NpcRangedGoal(this, NpcRangedGoal.Kind.BRIEFCASE, 70, 3, 10));
        goalSelector.addGoal(3, new NpcRangedGoal(this, NpcRangedGoal.Kind.KI, 90, 4, 18));
        goalSelector.addGoal(4, new HumanWorkGoal(this));      // chopping, mining, crafting, building their hut
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.75));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, this::wantsToFight));
    }

    /** Something a normal person runs from. */
    private boolean scary(LivingEntity e) {
        return e instanceof CorruptedSoulEntity || e instanceof CorruptedVillagerEntity || e instanceof DarkPossessedEntity
                || (e instanceof HumanNpcEntity h && (h.possessed() || h.raider())) || CorruptedAnimals.isCorrupted(e) || e instanceof Enemy;
    }

    private boolean wantsToFight(LivingEntity e) {
        if (raider()) return (e instanceof Player p && !p.isCreative() && !p.isSpectator()) || e instanceof Villager
                || e instanceof SkylanderEntity || e instanceof net.minecraft.world.entity.animal.IronGolem
                || (e instanceof HumanNpcEntity h && !h.raider());
        if (e instanceof HumanNpcEntity h && h.raider()) return fighter() && !carriesSoul();      // defend the village
        if (skyHater() && !carriesSoul()) {
            if (e instanceof SkylanderEntity sk && !sk.flying() && !sk.wingless()) return true;
            if (isRhalixWithWings(e)) return true;
        }
        if (possessed()) return e instanceof Player p && !p.isCreative() && !p.isSpectator() || (e instanceof HumanNpcEntity h && !h.possessed())
                || e instanceof Villager;
        if (!fighter() || HiddenSouls.hidden(this)) return false;
        if (e instanceof Player p && com.xenowarriors.faction.Reputation.hostile(p, com.xenowarriors.faction.Faction.TOWN)) return true;     // townsfolk who hate you fight you
        return e instanceof CorruptedSoulEntity || e instanceof CorruptedVillagerEntity || e instanceof DarkPossessedEntity
                || (e instanceof HumanNpcEntity h && h.possessed()) || CorruptedAnimals.isCorrupted(e)
                || e instanceof net.minecraft.world.entity.monster.Zombie      // fighters stand up to zombies too
                || (e instanceof HumanNpcEntity h && h.skyHater() && h.getTarget() instanceof SkylanderEntity);   // and stop wing-rippers
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data, CompoundTag tag) {
        var r = level.getRandom();
        entityData.set(VARIANT, r.nextInt(NpcLooks.HUMANS.length));
        String look = NpcLooks.human(variant());
        boolean fighter = r.nextFloat() < 0.3F || look.equals("soldier") || look.equals("gunner");
        entityData.set(FIGHTER, fighter);
        if (!NpcLooks.human(variant()).equals("soldier") && r.nextFloat() < 0.2F) {
            entityData.set(SKY_HATER, true);
            setCustomName(Component.literal("Sky Hater").withStyle(ChatFormatting.RED));
        }
        // each kind of person fights their own way (with Epic Fight: its moves for that weapon)
        if (fighter) {
            ItemStack weapon = switch (look) {
                case "soldier", "woman" -> new ItemStack(Items.IRON_SWORD);
                case "redhair" -> new ItemStack(Items.IRON_AXE);
                case "waver" -> new ItemStack(r.nextBoolean() ? Items.STONE_SWORD : Items.TRIDENT);
                case "capboy", "messy", "spiky", "girl", "gunner" -> ItemStack.EMPTY;          // fists (gunner shoots)
                default -> new ItemStack(r.nextFloat() < 0.6F ? Items.IRON_SWORD : Items.STONE_SWORD);
            };
            setItemSlot(EquipmentSlot.MAINHAND, weapon);
        }
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

    /** Sky Haters: worn down enough, a Skylander's wings get torn off. Rhalix's won't come off, however hard they pull. */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && skyHater() && !carriesSoul()) {
            if (target instanceof SkylanderEntity sk && sk.isAlive() && sk.getHealth() < sk.getMaxHealth() * 0.4F) {
                sk.loseWings(this);
                setTarget(null);
            } else if (target instanceof Player p && isRhalixWithWings(p) && p.getHealth() < p.getMaxHealth() * 0.4F && tickCount - rhalixTauntAt > 200) {
                rhalixTauntAt = tickCount;
                p.displayClientMessage(Component.literal("The Sky Hater grabs at your wings - but they won't tear!").withStyle(ChatFormatting.RED), true);
            }
        }
        return hit;
    }

    // ------------------------------------------------------------------ possession

    /** A soul takes them: in the open (hostile, dark) or hidden (no sign). */
    public void possess(boolean hidden) {
        if (com.xenowarriors.journey.JourneyGuide.is(this)) return;          // (the Journey Guide stays himself)
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
        // Mikiko won't hurt ordinary people - only the corrupted (or a Sky Hater caught going for a Skylander)
        if (!level().isClientSide && !possessed() && !raider() && isMikiko(source.getEntity()) && !(skyHater() && getTarget() instanceof SkylanderEntity)) return false;
        if (!level().isClientSide && source.getEntity() instanceof Player p && getTarget() instanceof SkylanderEntity sk) sk.helpedBy(p);
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
        if (!level().isClientSide && raider() && tickCount % 100 == 50 && !SkyHaterRaids.listed(getUUID())) { discard(); return; }   // (its raid is over)
        if (level().isClientSide) {
            if (possessed() && random.nextInt(3) == 0) {
                level().addParticle(CorruptedFx.BLACK, getRandomX(0.5), getRandomY(), getRandomZ(0.5), 0, 0.02, 0);
            }
            return;
        }
        flightTick();
        if (reaction() != 0 && tickCount >= reactUntil) entityData.set(REACTION, 0);
        if (tickCount % 20 == 7) HumanReactions.look(this);
        if (tickCount % 100 == 0 && carriesSoul() && !reported && possessedSince >= 0
                && level().getGameTime() - possessedSince >= PERMANENT_AFTER) becomePermanent();
    }

    /**
     * With DragonMineZ, fighters fly: up after anything in the air they're fighting, and back down when it lands.
     */
    private void flightTick() {
        LivingEntity t = getTarget();
        boolean want = dmz() && fighter() && !carriesSoul() && t != null && t.isAlive()
                && (t.getY() - getY() > 2.5 || (!t.onGround() && !t.isInWater() && t.getY() - getY() > 1.2) || (flying() && !t.onGround()));
        if (want != flying()) {
            entityData.set(FLYING, want);
            setNoGravity(want);
            if (want) {
                setDeltaMovement(getDeltaMovement().add(0, 0.5, 0));
                if (level() instanceof ServerLevel s) s.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 8, 0.3, 0.05, 0.3, 0.05);
            }
        }
        if (!flying() || t == null) return;
        net.minecraft.world.phys.Vec3 to = t.position().add(0, 0.2, 0).subtract(position());
        double d = to.length();
        net.minecraft.world.phys.Vec3 want3 = d > 1.6 ? to.normalize().scale(Math.min(0.42, d * 0.12)) : net.minecraft.world.phys.Vec3.ZERO;
        setDeltaMovement(getDeltaMovement().lerp(want3, 0.25));
        getLookControl().setLookAt(t);
        fallDistance = 0;
        if (tickCount % 4 == 0 && level() instanceof ServerLevel s)
            s.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1F, 1F, 0.85F), 1F),
                    getX(), getY() + 0.2, getZ(), 2, 0.25, 0.1, 0.25, 0);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !(dmz() && fighter()) && super.causeFallDamage(distance, multiplier, source);
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
        if (skyHater() || raider()) return InteractionResult.PASS;                   // (Sky Haters have nothing to say to you)
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (com.xenowarriors.journey.JourneyGuide.is(this) && player instanceof net.minecraft.server.level.ServerPlayer sp) { com.xenowarriors.journey.JourneyGuide.talk(sp, this); return InteractionResult.SUCCESS; }
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
        tag.put("Pack", pack.createTag());
        if (home != null) tag.putLong("Home", home.asLong());
        tag.putInt("Variant", variant());
        tag.putBoolean("Possessed", possessed());
        tag.putBoolean("Fighter", fighter());
        tag.putBoolean("SkyHater", skyHater());
        tag.putBoolean("Raider", raider());
        tag.putBoolean("Flying", flying());
        tag.putLong("PossessedSince", possessedSince);
        tag.putBoolean("Reported", reported);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        pack.fromTag(tag.getList("Pack", 10));
        if (tag.contains("Home")) home = net.minecraft.core.BlockPos.of(tag.getLong("Home"));
        entityData.set(VARIANT, tag.getInt("Variant"));
        entityData.set(POSSESSED, tag.getBoolean("Possessed"));
        entityData.set(FIGHTER, tag.getBoolean("Fighter"));
        entityData.set(SKY_HATER, tag.getBoolean("SkyHater"));
        entityData.set(RAIDER, tag.getBoolean("Raider"));
        entityData.set(FLYING, tag.getBoolean("Flying"));
        setNoGravity(flying());
        possessedSince = tag.contains("PossessedSince") ? tag.getLong("PossessedSince") : -1;
        reported = tag.getBoolean("Reported");
    }
}
