package com.xenowarriors.you;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.Race;
import com.xenowarriors.faction.Faction;
import com.xenowarriors.faction.Reputation;
import com.xenowarriors.npc.HumanNpcEntity;
import com.xenowarriors.npc.ModNpcs;
import com.xenowarriors.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Angel Ascension (survival): Skylanders descend from angels, and a born Skylander can rise to be one.
 * Their other path (Lunar / Solar) opens at Level 50; each path's mastery grows by using that form at its own time of
 * day (Lunar at night, Solar by day). At Level 80, both paths mastered, they pray at the Royal Capital at dawn (sneak,
 * still, a few seconds): a trial of three waves - Sky Haters and corrupted souls - and if they hold out, they ascend as
 * an Angel: seraph wings, Divine Light, Angel Eye, both forms, +20 health and 1.5x damage.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class YouAngel {
    private YouAngel() {}

    public static final int SECOND_FORM_LEVEL = 50, LEVEL = 80, MASTERY = 30, WAVES = 3, PRAY_SECONDS = 4;
    private static final int TRIAL_TICKS = 6 * 60 * 20;

    private static final class Trial { int wave; final List<UUID> foes = new ArrayList<>(); long until; Vec3 at; }
    private static final Map<UUID, Trial> TRIALS = new HashMap<>();
    /** The Sky Haters of trials (so their raid bookkeeping keeps them). */
    public static final Set<UUID> TRIAL_RAIDERS = new HashSet<>();
    /** (Tests) trials started, ascensions. */
    public static int trials, ascensions;

    /** A born Skylander that hasn't ascended. */
    static boolean candidate(YouSlot s) { return s != null && s.race == Race.SKYLANDER && !s.ascended; }

    /** A form used at its own time of day: that path's mastery grows. */
    public static void formUsed(ServerPlayer p, boolean lunar) {
        YouSlot s = You.slot(p);
        if (!candidate(s)) return;
        int before = lunar ? s.lunarM : s.solarM;
        if (before >= MASTERY) return;
        if (lunar) s.lunarM++; else s.solarM++;
        int now = before + 1;
        if (now == MASTERY) p.sendSystemMessage(Component.literal("Your " + (lunar ? "Lunar" : "Solar") + " path is mastered." + (s.lunarM >= MASTERY && s.solarM >= MASTERY
                ? " Both paths are yours - at Level " + LEVEL + ", pray at the Royal Capital at dawn to ascend." : "")).withStyle(ChatFormatting.GOLD));
        else if (now % 10 == 0) p.displayClientMessage(Component.literal((lunar ? "Lunar" : "Solar") + " path " + now + " / " + MASTERY).withStyle(ChatFormatting.YELLOW), true);
        com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p);
    }

    /** Ready to ascend: Level 80, both paths mastered. */
    public static boolean ready(ServerPlayer p, YouSlot s) {
        return candidate(s) && com.xenowarriors.character.Seasons.unlocked(p.server, SEASON) && s.lunarM >= MASTERY && s.solarM >= MASTERY && com.xenowarriors.stats.Stats.characterLevel(p) >= LEVEL;
    }

    /** Angel Ascension is Season 3 (the Skylanders' angel ancestry). */
    public static final int SEASON = 3;

    static boolean dawn(ServerLevel l) { long t = l.getDayTime() % 24000L; return t >= 22500 || t < 1500; }

    // ------------------------------------------------------------------ the prayer and the trial

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 20 != 13) return;
        Trial t = TRIALS.get(p.getUUID());
        if (t != null) { trialTick(p, t); return; }
        YouSlot s = You.slot(p);
        if (!ready(p, s)) return;
        ServerLevel l = p.serverLevel();
        boolean praying = p.isShiftKeyDown() && p.getDeltaMovement().horizontalDistanceSqr() < 0.001 && dawn(l) && com.xenowarriors.recarnet.RoyalCapitalFeature.inCapital(l, p.blockPosition());
        int n = praying ? p.getPersistentData().getInt("xw_angel_pray") + 1 : 0;
        p.getPersistentData().putInt("xw_angel_pray", n);
        if (!praying) return;
        l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 2.2, p.getZ(), 6, 0.4, 0.2, 0.4, 0.01);
        if (n < PRAY_SECONDS) { p.displayClientMessage(Component.literal("You pray as the sun rises..." + " " + "*".repeat(n)).withStyle(ChatFormatting.GOLD), true); return; }
        p.getPersistentData().remove("xw_angel_pray");
        start(p);
    }

    /** The trial begins (tests call it directly). */
    public static void start(ServerPlayer p) {
        Trial t = new Trial();
        t.at = p.position();
        t.until = p.level().getGameTime() + TRIAL_TICKS;
        TRIALS.put(p.getUUID(), t);
        trials++;
        p.sendSystemMessage(Component.literal("A voice from above: \"Prove you can stand as your ancestors did.\" Hold out through three waves.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1F, 0.8F);
        wave(p, t);
    }

    private static void wave(ServerPlayer p, Trial t) {
        ServerLevel l = p.serverLevel();
        t.wave++;
        t.foes.clear();
        int haters = 2 + t.wave * 2, souls = t.wave;
        for (int i = 0; i < haters + souls; i++) {
            Mob m = i < haters ? ModNpcs.HUMAN.get().create(l) : com.xenowarriors.corrupted.ModCorrupted.CORRUPTED_SOUL.get().create(l);
            if (m == null) continue;
            double a = l.random.nextDouble() * Math.PI * 2, d = 9 + l.random.nextInt(6);
            int x = (int) Math.round(t.at.x + Math.cos(a) * d), z = (int) Math.round(t.at.z + Math.sin(a) * d);
            BlockPos at = new BlockPos(x, (int) t.at.y, z);
            if (!l.isLoaded(at)) continue;
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - t.at.y) > 12) y = (int) t.at.y + 1;                   // (the capital's islands: stay on this one)
            m.moveTo(x + 0.5, y, z + 0.5, l.random.nextFloat() * 360F, 0);
            m.finalizeSpawn(l, l.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
            if (m instanceof HumanNpcEntity h) { h.makeRaider(i == 0 && t.wave == WAVES); TRIAL_RAIDERS.add(h.getUUID()); }
            m.setTarget(p);
            m.addTag("xw_angel_trial");
            l.addFreshEntity(m);
            t.foes.add(m.getUUID());
        }
        p.displayClientMessage(Component.literal("Trial - wave " + t.wave + " / " + WAVES).withStyle(ChatFormatting.GOLD), false);
    }

    private static void trialTick(ServerPlayer p, Trial t) {
        ServerLevel l = p.serverLevel();
        boolean down = com.xenowarriors.party.Duels.isDowned(p) || !p.isAlive();
        if (down || l.getGameTime() > t.until || p.position().distanceTo(t.at) > 64 || !com.xenowarriors.recarnet.Recarnet.is(l)) {
            com.xenowarriors.XenoWarriors.LOGGER.info("Angel trial over for {}: down {}, out of time {}, {} blocks from where it began, in Recarnet {}", p.getName().getString(), down,
                    l.getGameTime() > t.until, Math.round(p.position().distanceTo(t.at)), com.xenowarriors.recarnet.Recarnet.is(l));
            end(p, t);
            p.sendSystemMessage(Component.literal(down ? "You fell. The trial is over - pray again at another dawn." : "You left the trial. Pray again at another dawn.").withStyle(ChatFormatting.GRAY));
            return;
        }
        int alive = 0;
        for (UUID id : t.foes) if (l.getEntity(id) instanceof LivingEntity e && e.isAlive()) { alive++; if (e instanceof Mob m && m.getTarget() != p) m.setTarget(p); }
        if (alive > 0) return;
        if (t.wave < WAVES) { wave(p, t); return; }
        end(p, t);
        ascend(p);
    }

    private static void end(ServerPlayer p, Trial t) {
        TRIALS.remove(p.getUUID());
        for (UUID id : t.foes) { Entity e = p.serverLevel().getEntity(id); if (e != null) e.discard(); TRIAL_RAIDERS.remove(id); }
    }

    /** (Tests) is this player in the trial; which wave. */
    public static int trialWave(ServerPlayer p) { Trial t = TRIALS.get(p.getUUID()); return t == null ? 0 : t.wave; }

    /** (Tests) the foes of the current wave. */
    public static List<UUID> foesForTest(ServerPlayer p) { Trial t = TRIALS.get(p.getUUID()); return t == null ? List.of() : new ArrayList<>(t.foes); }

    public static void ascend(ServerPlayer p) {
        YouSlot s = You.slot(p);
        if (s == null || s.ascended) return;
        s.ascended = true;
        Reputation.add(p, Faction.SKY, 100);
        YouPaths.changed(p);
        ascensions++;
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 120, 0.8, 1.6, 0.8, 0.12);
        l.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1F, 1.1F);
        p.sendSystemMessage(Component.literal(s.name + " has ascended as an ANGEL.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.literal("Seraph wings, +20 health and 1.5x damage. New skills: Divine Light and Angel Eye - and both your Lunar and Solar forms.").withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ Angel Eye

    private static final String EYE = "xw_angel_eye";

    /** Angel Eye: for 10 seconds you see what's coming - enemies near you glow, and half the hits at you are foreseen and slipped. */
    public static final class AngelEye implements Skill {
        @Override public String getId() { return "you_angel_eye"; }
        @Override public int getCooldownTicks() { return 45 * 20; }
        @Override public int getKarmaCost() { return 35; }
        @Override public boolean use(ServerPlayer p) {
            p.getPersistentData().putLong(EYE, p.level().getGameTime() + 200);
            for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24), e -> e != p && e instanceof net.minecraft.world.entity.monster.Enemy))
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getEyeY(), p.getZ(), 30, 0.4, 0.3, 0.4, 0.05);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.6F);
            p.displayClientMessage(Component.literal("Angel Eye - you see what's coming.").withStyle(ChatFormatting.GOLD), true);
            return true;
        }
    }

    public static boolean eyeOn(LivingEntity p) { return p.getPersistentData().getLong(EYE) > p.level().getGameTime(); }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !eyeOn(p) || e.getSource().getEntity() == null || p.getRandom().nextFloat() >= 0.5F) return;
        e.setCanceled(true);                                                       // foreseen: a step aside
        Vec3 from = e.getSource().getEntity().position();
        Vec3 side = p.position().subtract(from).cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() > 1e-4) { p.setDeltaMovement(side.normalize().scale(0.5).add(0, 0.1, 0)); p.hurtMarked = true; }
        p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
        foreseen++;
    }

    /** (Tests) hits foreseen. */
    public static int foreseen;
}
