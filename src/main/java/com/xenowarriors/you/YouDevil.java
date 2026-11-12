package com.xenowarriors.you;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.faction.Faction;
import com.xenowarriors.faction.Reputation;
import com.xenowarriors.soul.SoulSplit;
import com.xenowarriors.soul.SoulSplitterSkill;
import com.xenowarriors.toggle.Toggles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Demon -> Devil (Season 3, like Ai-oni): from Character Level 50, every kill a demon (born or made) makes in demon form
 * feeds its Devil's Hunger. Full, the next night its shadow steps out of it - a dark copy with its strength - and they
 * fight. Win and the demon awakens as a Devil: horns, bigger darker wings, a dark aura, 1.3x damage and +10 health,
 * Self-Split (its other half fights beside it), Karma Mask (its Karma can't be read or sensed) and True Devil
 * Unleashed. Lose (go down) and the hunger drops by half. The Skylanders trust a Devil less (Sky -50).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class YouDevil {
    private YouDevil() {}

    public static final int HUNGER_MAX = 60, LEVEL = 50, SEASON = 3;
    private static final int DUEL_TICKS = 4 * 60 * 20;
    public static final String SHADOW_TAG = "xw_devil_shadow";

    private static final class Duel { UUID shadow; long until; }
    private static final Map<UUID, Duel> DUELS = new HashMap<>();
    /** (Tests) duels started, won, lost; Devils awakened. */
    public static int duels, wins, losses;

    /** Hungry: a demon not yet a Devil, at Level 50+ (the meter only fills from there). */
    public static boolean hungry(ServerPlayer p, YouSlot s) {
        return s != null && s.demon() && !s.devil && com.xenowarriors.stats.Stats.characterLevel(p) >= LEVEL;
    }

    // ------------------------------------------------------------------ the hunger

    @SubscribeEvent
    public static void onKill(LivingDeathEvent e) {
        Entity src = e.getSource().getEntity();
        if (src instanceof Projectile pr) src = pr.getOwner();
        if (!(src instanceof ServerPlayer p) || e.getEntity() instanceof Player || e.getEntity().getTags().contains(SHADOW_TAG)) return;
        LivingEntity dead = e.getEntity();
        if (!(dead instanceof Enemy) && !(dead instanceof com.xenowarriors.gov.GovAgentEntity) && !com.xenowarriors.karma.KarmaSensing.boss(dead)) return;
        if (dead.getTags().contains("xw_journey") || dead.getPersistentData().getBoolean("xw_tower")) return;   // (nothing from the modes is real)
        YouSlot s = You.slot(p);
        if (!hungry(p, s) || s.humanForm || s.hunger >= HUNGER_MAX) return;
        s.hunger = Math.min(HUNGER_MAX, s.hunger + (com.xenowarriors.karma.KarmaSensing.boss(dead) ? 5 : 1));
        if (s.hunger >= HUNGER_MAX) {
            p.sendSystemMessage(Component.literal("Your Devil's Hunger is full. When night falls, your shadow will come for you.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.6F, 0.6F);
        } else if (s.hunger % 10 == 0) p.displayClientMessage(Component.literal("Devil's Hunger " + s.hunger + " / " + HUNGER_MAX).withStyle(ChatFormatting.DARK_RED), true);
        com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p);
    }

    // ------------------------------------------------------------------ the shadow duel

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 20 != 7) return;
        Duel d = DUELS.get(p.getUUID());
        if (d != null) { duelTick(p, d); return; }
        YouSlot s = You.slot(p);
        if (!hungry(p, s) || s.hunger < HUNGER_MAX) return;
        ServerLevel l = p.serverLevel();
        if (!l.isNight() || !com.xenowarriors.modes.ModeRules.canGrief(l) || com.xenowarriors.party.Duels.isDowned(p) || p.isSpectator() || com.xenowarriors.journey.Journey.inRun(p)) return;
        long day = l.getDayTime() / 24000L;
        if (p.getPersistentData().getLong("xw_shadow_night") == day + 1) return;  // (one try a night)
        if (!com.xenowarriors.character.Seasons.unlocked(p.server, SEASON)) {
            p.getPersistentData().putLong("xw_shadow_night", day + 1);
            p.displayClientMessage(Component.literal("Something stirs in your shadow... (Devils are Season 3 content - locked)").withStyle(ChatFormatting.DARK_GRAY), false);
            return;
        }
        p.getPersistentData().putLong("xw_shadow_night", day + 1);
        start(p);
    }

    /** The shadow steps out (tests call it directly). */
    public static DevilShadowEntity start(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        DevilShadowEntity sh = YouEntities.DEVIL_SHADOW.get().create(l);
        if (sh == null) return null;
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : look.normalize();
        Vec3 at = p.position().add(look.scale(5));
        sh.moveTo(at.x, p.getY(), at.z, p.getYRot() + 180, 0);
        sh.copy(p);
        l.addFreshEntity(sh);
        Duel d = new Duel();
        d.shadow = sh.getUUID();
        d.until = l.getGameTime() + DUEL_TICKS;
        DUELS.put(p.getUUID(), d);
        duels++;
        l.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, p.getY() + 1, at.z, 40, 0.5, 1, 0.5, 0.03);
        l.playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5F, 1.4F);
        p.sendSystemMessage(Component.literal("Your shadow peels away from you - and turns to fight. Beat it to awaken as a Devil.").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        return sh;
    }

    private static void duelTick(ServerPlayer p, Duel d) {
        ServerLevel l = p.serverLevel();
        Entity e = l.getEntity(d.shadow);
        boolean down = com.xenowarriors.party.Duels.isDowned(p) || !p.isAlive();
        if (defeated.remove(d.shadow)) {                                         // (beaten)
            DUELS.remove(p.getUUID());
            wins++;
            awaken(p);
            return;
        }
        if (down) {
            DUELS.remove(p.getUUID());
            losses++;
            if (e != null) e.discard();
            YouSlot s = You.slot(p);
            if (s != null) { s.hunger /= 2; com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p); }
            p.sendSystemMessage(Component.literal("Your shadow sinks back into you. The hunger fades to half - feed it again.").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (l.getGameTime() > d.until || e == null || e.level() != p.level() || e.distanceToSqr(p) > 64 * 64) {   // (ran off, too long, or gone: back next night)
            DUELS.remove(p.getUUID());
            if (e != null) e.discard();
            p.sendSystemMessage(Component.literal("Your shadow slips back into you... it will come again another night.").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Shadows beaten (their uuids), seen by the owner's next duel tick. */
    private static final java.util.Set<UUID> defeated = new java.util.HashSet<>();

    static void shadowDied(DevilShadowEntity sh) { defeated.add(sh.getUUID()); }

    /** (Tests) is this player fighting their shadow? */
    public static boolean dueling(ServerPlayer p) { return DUELS.containsKey(p.getUUID()); }

    /** The awakening. */
    public static void awaken(ServerPlayer p) {
        YouSlot s = You.slot(p);
        if (s == null || s.devil) return;
        s.devil = true;
        s.hunger = 0;
        s.humanForm = false;
        Reputation.add(p, Faction.SKY, -50);
        YouPaths.changed(p);
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1, p.getZ(), 80, 0.8, 1.2, 0.8, 0.08);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 40, 0.8, 1.2, 0.8, 0.04);
        l.playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.8F, 0.7F);
        p.sendSystemMessage(Component.literal(s.name + " has awakened as a DEVIL.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.literal("Horns, bigger wings, 1.3x damage and more health. New: Self-Split (skill), Karma Mask (on the wheel), and Devil Unleashed is now True Devil Unleashed.").withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ what a Devil has

    /** Karma Mask: a Devil's Karma can't be read by scanners or sensed (switched on / off on the wheel). */
    public static boolean masked(Entity e) {
        if (!(e instanceof ServerPlayer p)) return false;
        YouSlot s = You.slot(p);
        return s != null && s.devil && Toggles.enabled(p, Toggles.Entry.KARMA_MASK);
    }

    /** True Devil Unleashed: a Devil's Devil Unleashed - stronger and longer. */
    public static void trueDevil(ServerPlayer p) {
        YouSlot s = You.slot(p);
        if (s == null || !s.devil) return;
        com.xenowarriors.skill.ffr.SrSkills.extendDevil(p, 100);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 340, 2));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 340, 0));
        p.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1, p.getZ(), 60, 0.7, 1.1, 0.7, 0.05);
        p.displayClientMessage(Component.literal("TRUE Devil Unleashed!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
    }

    /** Self-Split: split off your other half to fight beside you for 20 seconds (the devil skill Senju and Kill use). */
    public static final class SelfSplit extends SoulSplitterSkill {
        @Override public String getId() { return "devil_self_split"; }
        @Override public int getCooldownTicks() { return 60 * 20; }
        @Override public int getKarmaCost() { return 35; }
        @Override public boolean use(ServerPlayer p) {
            if (!super.use(p)) return false;
            p.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.9, 0.5, 0.04);
            com.xenowarriors.skill.ServerScheduler.later(20 * 20, () -> { if (SoulSplit.active(p)) SoulSplit.end(p, "Your other half returns to you"); });
            return true;
        }
    }

    /** The shadow's strength: about the demon's own (health a little above, hits a little below). */
    static float[] shadowStats(ServerPlayer p) {
        float hp = Math.max(40F, p.getMaxHealth() * 1.25F);
        double atk = Math.max(4.0, p.getAttributeValue(Attributes.ATTACK_DAMAGE)) * YouRaces.damageMult(p)
                * (1.0 + com.xenowarriors.stats.Stat.ATTACK.bonus(com.xenowarriors.stats.Stats.level(p, com.xenowarriors.stats.Stat.ATTACK)));
        return new float[]{hp, (float) Math.min(atk * 0.7, p.getMaxHealth() * 0.2)};   // (never more than a fifth of your health a hit)
    }

    /** (Tests) fills the hunger. */
    public static void fillForTest(ServerPlayer p) { YouSlot s = You.slot(p); if (s != null) { s.hunger = HUNGER_MAX; com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p); } }
}
