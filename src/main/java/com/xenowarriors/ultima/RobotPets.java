package com.xenowarriors.ultima;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * With DragonMineZ: Ultima can tame the Xenoverse Robot (dragonminez:robotxv) with Data Shards - right-click it with
 * one, a 1-in-3 chance per shard (like a wolf and its bones). A tamed robot is his: it follows him, fights whatever
 * hurts him or whatever he hits, never touches his side, and sits / stands up when he right-clicks it. The owner is
 * kept in the robot's own saved data, so it stays his across relogs and restarts.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RobotPets {
    public static final ResourceLocation ROBOT = new ResourceLocation("dragonminez", "robotxv");
    public static final String OWNER = "xw_robot_owner", SIT = "xw_robot_sit";
    public static final float TAME_CHANCE = 1F / 3F;
    /** Tests: forces the next tries' outcome (null = random). */
    public static Boolean forceTame;

    private static final Map<UUID, Set<UUID>> LIVE = new HashMap<>();

    private RobotPets() {}

    public static boolean isRobot(Entity e) {
        return e instanceof Mob && ROBOT.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
    }

    public static UUID ownerOf(Entity e) {
        return e.getPersistentData().hasUUID(OWNER) ? e.getPersistentData().getUUID(OWNER) : null;
    }

    public static boolean sitting(Entity e) { return e.getPersistentData().getBoolean(SIT); }

    /** Robots of this player's that are in the world right now. */
    public static int count(ServerPlayer p) {
        Set<UUID> s = LIVE.get(p.getUUID());
        return s == null ? 0 : s.size();
    }

    private static ServerPlayer owner(Mob m) {
        UUID id = ownerOf(m);
        return id != null && m.level() instanceof ServerLevel l && l.getPlayerByUUID(id) instanceof ServerPlayer sp ? sp : null;
    }

    // ------------------------------------------------------------------ taming and sitting
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!isRobot(event.getTarget()) || !(event.getTarget() instanceof Mob robot)) return;
        if (event.getLevel().isClientSide) {          // (the server decides; a shard isn't also absorbed as a right-click in the air)
            if (event.getItemStack().getItem() instanceof DataShardItem) { event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS); }
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {           // (one answer per click)
            if (ownerOf(robot) != null || event.getItemStack().getItem() instanceof DataShardItem) { event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS); }
            return;
        }
        UUID owner = ownerOf(robot);
        ItemStack st = event.getItemStack();
        if (owner != null) {
            if (!owner.equals(event.getEntity().getUUID())) return;
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (!(event.getEntity() instanceof ServerPlayer p)) return;
            if (st.getItem() instanceof DataShardItem && robot.getHealth() < robot.getMaxHealth()) {      // a shard repairs it
                robot.heal(robot.getMaxHealth() * 0.25F);
                if (!p.isCreative()) st.shrink(1);
                p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, robot.getX(), robot.getY() + 1, robot.getZ(), 8, .4, .5, .4, 0);
                return;
            }
            setSitting(robot, !sitting(robot));
            p.displayClientMessage(Component.literal(sitting(robot) ? "Your robot powers down and waits here" : "Your robot is following you").withStyle(ChatFormatting.GREEN), true);
            return;
        }
        if (!(st.getItem() instanceof DataShardItem)) return;
        if (!(event.getEntity() instanceof ServerPlayer p) || ModCapabilities.getType(p) != CharacterType.ULTIMA) {
            if (event.getEntity().level().isClientSide) return;
            event.getEntity().displayClientMessage(Component.literal("Only Ultima can rewrite a robot with Data").withStyle(ChatFormatting.GRAY), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        tryTame(p, robot, st);
    }

    /** One Data Shard fed to a wild robot: a chance it's rewritten to serve him. */
    public static boolean tryTame(ServerPlayer p, Mob robot, ItemStack st) {
        if (!com.xenowarriors.party.Parties.petRoom(p)) return false;
        if (!p.isCreative()) st.shrink(1);
        ServerLevel l = p.serverLevel();
        boolean ok = forceTame != null ? forceTame : p.getRandom().nextFloat() < TAME_CHANCE;
        if (!ok) {
            l.sendParticles(ParticleTypes.SMOKE, robot.getX(), robot.getY() + robot.getBbHeight() * 0.7, robot.getZ(), 10, .3, .3, .3, 0.02);
            l.playSound(null, robot.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 0.7F, 1.6F);
            p.displayClientMessage(Component.literal("The robot rejected the Data...").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        tame(p, robot);
        return true;
    }

    public static void tame(ServerPlayer p, Mob robot) {
        robot.getPersistentData().putUUID(OWNER, p.getUUID());
        robot.getPersistentData().putBoolean(SIT, false);
        robot.setPersistenceRequired();
        robot.setTarget(null);
        if (!robot.hasCustomName()) robot.setCustomName(Component.literal(p.getName().getString() + "'s Xenoverse Robot").withStyle(ChatFormatting.GREEN));
        configure(robot);
        LIVE.computeIfAbsent(p.getUUID(), k -> new HashSet<>()).add(robot.getUUID());
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.HEART, robot.getX(), robot.getY() + robot.getBbHeight() + 0.3, robot.getZ(), 7, .4, .3, .4, 0);
        l.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.2F, 1F, .5F), 1.2F), robot.getX(), robot.getY() + 1, robot.getZ(), 30, .4, .7, .4, 0);
        l.playSound(null, robot.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.8F, 1.6F);
        p.displayClientMessage(Component.literal("The Xenoverse Robot is yours now - right-click it to make it wait or follow").withStyle(ChatFormatting.GREEN), false);
        XenoWarriors.LOGGER.info("Ultima {} tamed a Xenoverse Robot ({})", p.getGameProfile().getName(), robot.getUUID());
    }

    public static void setSitting(Mob robot, boolean sit) {
        robot.getPersistentData().putBoolean(SIT, sit);
        robot.getNavigation().stop();
        if (sit) robot.setTarget(null);
    }

    /** Its own AI swapped for a companion's (goals aren't saved, so this runs every time it loads). */
    private static void configure(Mob robot) {
        if (robot.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof Sit)) return;
        robot.targetSelector.removeAllGoals(g -> true);         // (no more hunting players)
        robot.targetSelector.addGoal(1, new Guard(robot));
        robot.goalSelector.addGoal(0, new Sit(robot));
        robot.goalSelector.addGoal(3, new Follow(robot));
    }

    /** Waits where it was told to. */
    static final class Sit extends Goal {
        private final Mob m;
        Sit(Mob m) { this.m = m; setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP)); }
        @Override public boolean canUse() { return sitting(m); }
        @Override public void start() { m.getNavigation().stop(); m.setTarget(null); }
        @Override public void tick() { m.getNavigation().stop(); }
    }

    /** Stays near Ultima; pops over to him if left far behind. */
    static final class Follow extends Goal {
        private final Mob m;
        private int recalc;
        Follow(Mob m) { this.m = m; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            ServerPlayer o = owner(m);
            return o != null && !sitting(m) && o.level() == m.level() && m.getTarget() == null && m.distanceToSqr(o) > 6 * 6;
        }
        @Override public boolean canContinueToUse() {
            ServerPlayer o = owner(m);
            return o != null && !sitting(m) && o.level() == m.level() && m.getTarget() == null && m.distanceToSqr(o) > 2.5 * 2.5;
        }
        @Override public void start() { recalc = 0; }
        @Override public void stop() { m.getNavigation().stop(); }
        @Override public void tick() {
            ServerPlayer o = owner(m);
            if (o == null) return;
            m.getLookControl().setLookAt(o, 10F, m.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = 10;
            if (m.distanceToSqr(o) > 24 * 24 && o.onGround()) m.teleportTo(o.getX() + m.getRandom().nextGaussian(), o.getY(), o.getZ() + m.getRandom().nextGaussian());
            else m.getNavigation().moveTo(o, 1.25);
        }
    }

    /** Whatever hurt Ultima, whatever he hit, else a monster close by that's after him. */
    static final class Guard extends TargetGoal {
        private LivingEntity pick;
        Guard(Mob m) { super(m, false); setFlags(EnumSet.of(Flag.TARGET)); }
        @Override public boolean canUse() {
            ServerPlayer o = owner(mob);
            if (o == null || sitting(mob) || o.level() != mob.level()) return false;
            pick = null;
            LivingEntity a = o.getLastHurtByMob(), b = o.getLastHurtMob();
            if (ok(a, o)) pick = a;
            else if (ok(b, o)) pick = b;
            else for (Mob e : mob.level().getEntitiesOfClass(Mob.class, o.getBoundingBox().inflate(12), x -> x instanceof Enemy && x.getTarget() != null)) {
                if (com.xenowarriors.combat.Allies.isAlly(o, e.getTarget()) && ok(e, o)) { pick = e; break; }
            }
            return pick != null;
        }
        private boolean ok(LivingEntity e, ServerPlayer o) {
            return e != null && e.isAlive() && e != mob && !com.xenowarriors.combat.Allies.isAlly(o, e) && e.distanceToSqr(o) < 24 * 24;
        }
        @Override public void start() { mob.setTarget(pick); super.start(); }
    }

    // ------------------------------------------------------------------ keeping track
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Mob m) || !isRobot(m)) return;
        UUID o = ownerOf(m);
        if (o == null) return;
        configure(m);
        LIVE.computeIfAbsent(o, k -> new HashSet<>()).add(m.getUUID());
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide || !isRobot(event.getEntity())) return;
        UUID o = ownerOf(event.getEntity());
        Set<UUID> s = o == null ? null : LIVE.get(o);
        if (s != null) { s.remove(event.getEntity().getUUID()); if (s.isEmpty()) LIVE.remove(o); }
    }

    /** Its old programming never turns it on its own side. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide || !isRobot(event.getEntity())) return;
        UUID o = ownerOf(event.getEntity());
        if (o == null || event.getNewTarget() == null) return;
        if (sitting(event.getEntity()) || com.xenowarriors.combat.Allies.isAlly(event.getEntity(), event.getNewTarget())) event.setCanceled(true);
    }
}
