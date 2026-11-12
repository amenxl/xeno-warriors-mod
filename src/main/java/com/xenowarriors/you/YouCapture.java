package com.xenowarriors.you;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

/**
 * The You race's Capture (Phase 2): weaken a creature - any mob that isn't a boss, a player, a teammate or someone's
 * pet - to {@value #HEALTH_PCT}% health or less (half, while Suppressed), then Capture it (a beam of light pulls its soul into line). It's
 * yours from then on: healed up, it follows you, fights whatever hurts you or whatever you hit, never turns on your
 * side, and waits / follows when you sneak + right-click it. It counts toward the party's pets. Its owner is kept in
 * its own saved data, so it stays yours across relogs and restarts.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class YouCapture {
    private YouCapture() {}

    public static final String OWNER = "xw_captured_owner", SIT = "xw_captured_sit";
    public static final int HEALTH_PCT = 35, SUPPRESSED_PCT = 50;

    /** How low it has to be: {@value #HEALTH_PCT}%, or {@value #SUPPRESSED_PCT}% while your Suppress holds it. */
    public static int threshold(LivingEntity t) { return t.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS) && t.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN) ? SUPPRESSED_PCT : HEALTH_PCT; }
    private static final Map<UUID, Set<UUID>> LIVE = new HashMap<>();
    /** (Tests) creatures captured. */
    public static int captured;

    public static UUID ownerOf(net.minecraft.world.entity.Entity e) { return e.getPersistentData().hasUUID(OWNER) ? e.getPersistentData().getUUID(OWNER) : null; }
    public static boolean sitting(net.minecraft.world.entity.Entity e) { return e.getPersistentData().getBoolean(SIT); }

    /** Captured creatures of this player's in the world now (they count as pets). */
    public static int count(ServerPlayer p) { Set<UUID> s = LIVE.get(p.getUUID()); return s == null ? 0 : s.size(); }

    private static ServerPlayer owner(Mob m) {
        UUID id = ownerOf(m);
        return id != null && m.level() instanceof ServerLevel l && l.getPlayerByUUID(id) instanceof ServerPlayer sp ? sp : null;
    }

    /** Why this can't be captured (null: it can). */
    public static String why(ServerPlayer p, LivingEntity t) {
        if (!(t instanceof Mob m) || t instanceof Player || t instanceof com.xenowarriors.bot.PartyBotEntity || t instanceof DevilShadowEntity || t instanceof com.xenowarriors.kaiplanet.KaiBuilderEntity) return "That can't be captured";
        if (com.xenowarriors.party.Parties.petOwner(t) != null) return "That already belongs to someone";
        if (com.xenowarriors.karma.KarmaSensing.boss(t) || t.getPersistentData().getBoolean("xw_tower_boss") || t instanceof com.xenowarriors.gov.UltimaPrototypeEntity)
            return "Bosses are too strong to capture";
        if (t instanceof net.minecraft.world.entity.monster.Creeper) return "A creeper won't hold still - it'd blow up";
        if (t.getPersistentData().getBoolean("xw_tower") || t.getPersistentData().getBoolean("xw_journey") || t.getTags().contains("xw_journey")) return "Not here - only out in the world";
        int pct = threshold(t);
        if (t.getHealth() > t.getMaxHealth() * pct / 100F) return "Weaken it first - under " + pct + "% health (" + Math.round(t.getHealth()) + "/" + Math.round(t.getMaxHealth()) + ")" + (pct == HEALTH_PCT ? " - or Suppress it" : "");
        return null;
    }

    /** The skill: Capture the creature you're looking at. */
    public static final class CaptureSkill implements Skill {
        @Override public String getId() { return "you_capture"; }
        @Override public int getCooldownTicks() { return 15 * 20; }
        @Override public int getKarmaCost() { return 30; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SkillTargeting.aimed(p, 16, 12);
            if (t == null) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            String why = why(p, t);
            if (why != null) { p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.GRAY), true); return false; }
            if (!com.xenowarriors.party.Parties.petRoom(p)) return false;
            capture(p, (Mob) t);
            return true;
        }
    }

    public static void capture(ServerPlayer p, Mob m) {
        ServerLevel l = p.serverLevel();
        var from = p.getEyePosition().subtract(0, 0.3, 0);
        var to = m.position().add(0, m.getBbHeight() / 2, 0);
        Vector3f c = new Vector3f(0.6F, 0.95F, 1F);
        for (int i = 0; i <= 20; i++) { var q = from.lerp(to, i / 20.0); l.sendParticles(new DustParticleOptions(c, 1.1F), q.x, q.y, q.z, 1, 0, 0, 0, 0); }
        m.getPersistentData().putUUID(OWNER, p.getUUID());
        m.getPersistentData().putBoolean(SIT, false);
        m.setPersistenceRequired();
        m.setTarget(null);
        m.setHealth(m.getMaxHealth());
        m.clearFire();
        m.removeEffect(net.minecraft.world.effect.MobEffects.WEAKNESS); m.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
        if (!m.hasCustomName()) m.setCustomName(Component.literal(p.getName().getString() + "'s " + m.getType().getDescription().getString()).withStyle(ChatFormatting.AQUA));
        configure(m);
        LIVE.computeIfAbsent(p.getUUID(), k -> new HashSet<>()).add(m.getUUID());
        captured++;
        l.sendParticles(ParticleTypes.HEART, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 6, .4, .3, .4, 0);
        l.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 1, m.getZ(), 20, .4, .7, .4, 0.03);
        l.playSound(null, m.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
        l.playSound(null, m.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1F, 1.2F);
        p.displayClientMessage(Component.literal("Captured! The " + m.getType().getDescription().getString() + " is yours now - sneak + right-click it to make it wait or follow").withStyle(ChatFormatting.AQUA), false);
    }

    /** Let go (from the Pets menu or a command): it goes back to the wild. */
    public static void release(Mob m) {
        UUID o = ownerOf(m);
        m.getPersistentData().remove(OWNER); m.getPersistentData().remove(SIT);
        Set<UUID> s = o == null ? null : LIVE.get(o);
        if (s != null) s.remove(m.getUUID());
    }

    /** Its own AI swapped for a companion's (goals aren't saved: this runs every time it loads). */
    private static void configure(Mob m) {
        if (m.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof Sit)) return;
        m.targetSelector.removeAllGoals(g -> true);
        m.targetSelector.addGoal(1, new Guard(m));
        m.goalSelector.addGoal(0, new Sit(m));
        m.goalSelector.addGoal(1, new Follow(m));
    }

    static final class Sit extends Goal {
        private final Mob m;
        Sit(Mob m) { this.m = m; setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP)); }
        @Override public boolean canUse() { return sitting(m); }
        @Override public void start() { m.getNavigation().stop(); m.setTarget(null); }
        @Override public void tick() { m.getNavigation().stop(); }
    }

    static final class Follow extends Goal {
        private final Mob m;
        private int recalc;
        Follow(Mob m) { this.m = m; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { ServerPlayer o = owner(m); return o != null && !sitting(m) && o.level() == m.level() && m.getTarget() == null && m.distanceToSqr(o) > 6 * 6; }
        @Override public boolean canContinueToUse() { ServerPlayer o = owner(m); return o != null && !sitting(m) && o.level() == m.level() && m.getTarget() == null && m.distanceToSqr(o) > 2.5 * 2.5; }
        @Override public void start() { recalc = 0; }
        @Override public void stop() { m.getNavigation().stop(); }
        @Override public void tick() {
            ServerPlayer o = owner(m);
            if (o == null) return;
            m.getLookControl().setLookAt(o, 10F, m.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = 10;
            if (m.distanceToSqr(o) > 24 * 24 && o.onGround()) m.teleportTo(o.getX() + m.getRandom().nextGaussian(), o.getY(), o.getZ() + m.getRandom().nextGaussian());
            else m.getNavigation().moveTo(o, 1.2);
        }
    }

    /** Whatever hurt you, whatever you hit, else something close by that's after you or your side. */
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

    // ---------------------------------------------------------------- waiting / following, keeping track

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND || !p.isShiftKeyDown() || !(event.getTarget() instanceof Mob m)) return;
        if (!p.getUUID().equals(ownerOf(m))) return;
        boolean sit = !sitting(m);
        m.getPersistentData().putBoolean(SIT, sit);
        m.getNavigation().stop();
        if (sit) m.setTarget(null);
        p.displayClientMessage(Component.literal(m.getName().getString() + (sit ? " waits here" : " follows you")).withStyle(ChatFormatting.AQUA), true);
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Mob m)) return;
        UUID o = ownerOf(m);
        if (o == null) return;
        configure(m);
        LIVE.computeIfAbsent(o, k -> new HashSet<>()).add(m.getUUID());
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        UUID o = ownerOf(event.getEntity());
        Set<UUID> s = o == null ? null : LIVE.get(o);
        if (s != null) { s.remove(event.getEntity().getUUID()); if (s.isEmpty()) LIVE.remove(o); }
    }

    /** It never turns on its own side. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide || event.getNewTarget() == null) return;
        if (ownerOf(event.getNewTarget()) != null && event.getEntity() instanceof net.minecraft.world.entity.animal.AbstractGolem) { event.setCanceled(true); return; }   // (golems leave a captured monster be)
        if (ownerOf(event.getEntity()) == null) return;
        if (sitting(event.getEntity()) || com.xenowarriors.combat.Allies.isAlly(event.getEntity(), event.getNewTarget())) event.setCanceled(true);
    }
}
