package com.xenowarriors.corrupted;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.CorruptionPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Animals possessed by a corrupted soul: they stop fleeing and hunt players, take on a purple glow and
 * leak soul fire. At sunrise (under the open sky) the soul is burned out and the animal is itself again.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CorruptedAnimals {
    public static final String TAG = "xw_corrupted";

    private CorruptedAnimals() {}

    public static boolean isCorrupted(LivingEntity e) {
        return e.getPersistentData().getBoolean(TAG);
    }

    public static boolean canPossess(Animal a) {
        if (isCorrupted(a) || a.isBaby()) return false;
        if (a instanceof TamableAnimal t && t.isTame()) return false;
        var data = a.getPersistentData();
        return !data.hasUUID("xw_owner") && !data.hasUUID("xw_wing_owner") && !a.hasCustomName();
    }

    private static final java.util.UUID HP_ID = java.util.UUID.fromString("b7a3c2e1-4d5f-4a6b-9c8d-1e2f3a4b5c61"),
            DMG_ID = java.util.UUID.fromString("b7a3c2e1-4d5f-4a6b-9c8d-1e2f3a4b5c62"),
            SPD_ID = java.util.UUID.fromString("b7a3c2e1-4d5f-4a6b-9c8d-1e2f3a4b5c63");

    /** Ordinary hostile mobs can be possessed too (not the corrupted themselves, and not bosses). */
    public static boolean canPossess(net.minecraft.world.entity.monster.Monster m) {
        return !isCorrupted(m) && !(m instanceof CorruptedSoulEntity) && !(m instanceof CSoulBossEntity)
                && !(m instanceof CorruptedVillagerEntity) && !(m instanceof net.minecraft.world.entity.boss.wither.WitherBoss)
                && !(m instanceof net.minecraft.world.entity.monster.warden.Warden)
                && (!m.hasCustomName() || !vanilla(m))           // other mods' enemies often come with names
                && m.getMaxHealth() <= MAX_HOST_HEALTH                  // not giant bosses
                && !m.getPersistentData().contains("xw_sealed");
    }

    /** Souls won't take bodies tougher than this (keeps them off big bosses, e.g. DragonMineZ saga bosses). */
    public static final float MAX_HOST_HEALTH = 400F;

    private static boolean vanilla(LivingEntity e) {
        return "minecraft".equals(net.minecraft.world.entity.EntityType.getKey(e.getType()).getNamespace());
    }

    /**
     * Other mods' people and creatures that aren't monsters or animals (DragonMineZ's Namekians and warriors, modded
     * villagers): the soul takes the body as it is, rather than turning it into a vanilla-looking possessed person.
     */
    public static boolean canPossessOther(net.minecraft.world.entity.PathfinderMob m) {
        return !vanilla(m) && !HiddenSouls.hidden(m) && !(m instanceof net.minecraft.world.entity.monster.Monster) && !(m instanceof Animal)
                && !(m instanceof TamableAnimal) && !isCorrupted(m) && m.getMaxHealth() <= MAX_HOST_HEALTH
                && m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != null
                && !m.getPersistentData().contains("xw_sealed") && !m.getPersistentData().contains("xw_owner");
    }

    public static boolean corruptOther(net.minecraft.world.entity.PathfinderMob m) {
        if (!canPossessOther(m)) return false;
        m.getPersistentData().putBoolean(TAG, true);
        m.setPersistenceRequired();
        boost(m, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, HP_ID, 0.6);
        boost(m, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, SPD_ID, 0.15);
        m.setHealth(m.getMaxHealth());
        addHuntGoals(m);
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> m), new CorruptionPacket(m.getId(), true));
        m.playSound(SoundEvents.WITHER_AMBIENT, 0.5F, 1.5F);
        return true;
    }

    private static void addHuntGoals(net.minecraft.world.entity.PathfinderMob m) {
        m.goalSelector.removeAllGoals(g -> g instanceof PanicGoal || g instanceof TemptGoal);
        if (m.goalSelector.getAvailableGoals().stream().noneMatch(w -> w.getGoal() instanceof CorruptedAttackGoal)) {
            m.goalSelector.addGoal(1, new CorruptedAttackGoal(m));
            m.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(m, Player.class, true));
        }
    }

    /** A possessed monster: tougher, harder-hitting and faster, glowing purple. */
    public static boolean corrupt(net.minecraft.world.entity.monster.Monster m) {
        if (!canPossess(m)) return false;
        m.getPersistentData().putBoolean(TAG, true);
        m.setPersistenceRequired();
        boost(m, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, HP_ID, 0.6);
        boost(m, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, DMG_ID, 0.5);
        boost(m, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, SPD_ID, 0.15);
        m.setHealth(m.getMaxHealth());
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> m), new CorruptionPacket(m.getId(), true));
        m.playSound(SoundEvents.WITHER_AMBIENT, 0.5F, 1.5F);
        return true;
    }

    private static void boost(LivingEntity e, net.minecraft.world.entity.ai.attributes.Attribute attr, java.util.UUID id, double amount) {
        var inst = e.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, "Corrupted soul", amount,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
    }

    /** Drives the soul out of a possessed monster or other creature (Mikiko's Hekublade). */
    public static void cleanseMob(net.minecraft.world.entity.Mob m) {
        cleanseMonster(m);
    }

    private static void cleanseMonster(net.minecraft.world.entity.Mob m) {
        m.getPersistentData().remove(TAG);
        if (!(m instanceof net.minecraft.world.entity.monster.Monster)) {
            m.goalSelector.removeAllGoals(g -> g instanceof CorruptedAttackGoal);
            m.targetSelector.removeAllGoals(g -> g instanceof NearestAttackableTargetGoal<?>);
            m.setTarget(null);
        }
        for (var pair : new Object[][]{{net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, HP_ID},
                {net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, DMG_ID},
                {net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, SPD_ID}}) {
            var inst = m.getAttribute((net.minecraft.world.entity.ai.attributes.Attribute) pair[0]);
            if (inst != null) inst.removeModifier((java.util.UUID) pair[1]);
        }
        if (m.getHealth() > m.getMaxHealth()) m.setHealth(m.getMaxHealth());
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> m), new CorruptionPacket(m.getId(), false));
        if (m.level() instanceof ServerLevel server) {
            server.sendParticles(CorruptedFx.BLACK, m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 14, 0.3, 0.3, 0.3, 0.06);
        }
        m.playSound(SoundEvents.SOUL_ESCAPE, 1F, 1F);
    }

    public static boolean corrupt(Animal a) {
        if (!canPossess(a)) return false;
        a.getPersistentData().putBoolean(TAG, true);
        a.setPersistenceRequired();
        addGoals(a);
        sync(a, true);
        a.playSound(SoundEvents.WITHER_AMBIENT, 0.4F, 1.8F);
        return true;
    }

    public static void cleanse(Animal a) {
        a.getPersistentData().remove(TAG);
        a.goalSelector.removeAllGoals(g -> g instanceof CorruptedAttackGoal);
        a.targetSelector.removeAllGoals(g -> g instanceof NearestAttackableTargetGoal<?>);
        a.goalSelector.addGoal(1, new PanicGoal(a, 1.4));
        a.setTarget(null);
        sync(a, false);
        if (a.level() instanceof ServerLevel server) {
            server.sendParticles(CorruptedFx.BLACK, a.getX(), a.getY() + a.getBbHeight(), a.getZ(), 14, 0.3, 0.3, 0.3, 0.06);
        }
        a.playSound(SoundEvents.SOUL_ESCAPE, 1F, 1F);
    }

    private static void addGoals(Animal a) {
        a.goalSelector.removeAllGoals(g -> g instanceof PanicGoal || g instanceof TemptGoal || g instanceof BreedGoal
                || g instanceof FollowParentGoal);
        a.goalSelector.addGoal(1, new CorruptedAttackGoal(a));
        a.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(a, Player.class, true));
    }

    private static void sync(Animal a, boolean on) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> a), new CorruptionPacket(a.getId(), on));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Animal a && isCorrupted(a)) addGoals(a);
        else if (!event.getLevel().isClientSide && event.getEntity() instanceof net.minecraft.world.entity.PathfinderMob m
                && !(m instanceof net.minecraft.world.entity.monster.Monster) && isCorrupted(m)) addHuntGoals(m);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity a && isCorrupted(a) && event.getEntity() instanceof ServerPlayer sp) {
            ModNetwork.sendToPlayer(new CorruptionPacket(a.getId(), true), sp);
        }
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob a) || a.level().isClientSide || a.tickCount % 20 != 0
                || !isCorrupted(a)) return;
        if (!(a instanceof net.minecraft.world.entity.PathfinderMob)) return;
        if (a.level().isDay() && a.level().canSeeSky(a.blockPosition())) {
            if (a instanceof Animal an) cleanse(an);
            else cleanseMonster(a);
            return;
        }
        if (a.level() instanceof ServerLevel server) {
            server.sendParticles(CorruptedFx.DARK, a.getX(), a.getY() + a.getBbHeight() * 0.8, a.getZ(), 2, 0.2, 0.15, 0.2, 0.01);
        }
    }

    /** Freeing a possessed animal gives a little extra XP. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.Mob a && isCorrupted(a) && a.level() instanceof ServerLevel server) {
            server.sendParticles(CorruptedFx.BLACK, a.getX(), a.getY() + a.getBbHeight(), a.getZ(), 16, 0.3, 0.4, 0.3, 0.05);
            net.minecraft.world.entity.ExperienceOrb.award(server, a.position(), 5);
        }
    }

    /** Animals have no attack strength of their own, so the soul's strength scales with the body's size. */
    static class CorruptedAttackGoal extends MeleeAttackGoal {
        private final net.minecraft.world.entity.PathfinderMob animal;

        CorruptedAttackGoal(net.minecraft.world.entity.PathfinderMob animal) {
            super(animal, 1.35, true);
            this.animal = animal;
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distSqr) {
            if (distSqr <= getAttackReachSqr(target) && isTimeToAttack()) {
                resetAttackCooldown();
                animal.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                float damage = Math.min(7F, 2F + animal.getMaxHealth() / 6F);
                if (target.hurt(animal.damageSources().mobAttack(animal), damage)) {
                    double dx = target.getX() - animal.getX(), dz = target.getZ() - animal.getZ();
                    double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
                    target.push(dx / len * 0.3, 0.15, dz / len * 0.3);
                }
            }
        }
    }

    @SuppressWarnings("unused")
    private static void unused(TickEvent e) {}
}
