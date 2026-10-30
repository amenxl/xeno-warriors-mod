package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * With DragonMineZ: bot teammates also use the ki techniques their character has equipped in their own DragonMineZ
 * character (the one saved for that character - see {@link DmzPerCharacter}). The bot itself throws them, the way
 * DragonMineZ's own fighters do: charged in its hands for the cast time, then fired at what it's fighting. Its hits
 * count like its other hits, and they never hurt its owner, the party or their bots (see PartyBotEntity.isAlliedTo
 * and PartyBots.onAttack). The bot's DMZ character is read into a throwaway copy - the owner's own data is never
 * touched. All by reflection against DragonMineZ 2.1.x; without it (or if its API changed) bots simply don't.
 */
public final class DmzBotKi {
    private DmzBotKi() {}

    /** At least this long between two ki techniques from one bot, and before the same technique again. */
    private static final int MIN_GAP = 60, REFRESH = 600, MAX_CAST = 40;

    private static Boolean loaded;
    private static boolean broken;
    private static Method execute, statsLoad, getTechniques, equipped, unlocked, cooldown, castTime, instant, kiType, utility, techId;
    private static Constructor<?> statsNew;
    private static Class<?> kiData;
    // ki melee (DragonMineZ's "strike" techniques)
    private static Class<?> strikeData;
    private static Method strikeDamage, sDamageMult, sDuration, sCooldown, sCast, strikeSource, techConfig, strikeConfig, cfgDamageMult;

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (execute != null) return true;
        try {
            Class<?> stats = Class.forName("com.dragonminez.common.stats.StatsData");
            kiData = Class.forName("com.dragonminez.common.stats.techniques.KiAttackData");
            Class<?> tech = Class.forName("com.dragonminez.common.stats.techniques.Techniques");
            Class<?> data = Class.forName("com.dragonminez.common.stats.techniques.TechniqueData");
            statsNew = stats.getConstructor(Player.class);
            statsLoad = stats.getMethod("load", CompoundTag.class);
            getTechniques = stats.getMethod("getTechniques");
            equipped = tech.getMethod("getEquippedSlots");
            unlocked = tech.getMethod("getUnlockedTechniques");
            cooldown = kiData.getMethod("getActualCooldown");
            castTime = kiData.getMethod("getActualCastTime");
            instant = kiData.getMethod("isInstantCast");
            kiType = kiData.getMethod("getKiType");
            utility = kiData.getMethod("getEffectiveUtility");
            techId = data.getMethod("getId");
            execute = Class.forName("com.dragonminez.common.stats.techniques.TechniqueDispatcher")
                    .getMethod("executeKiAttack", LivingEntity.class, Level.class, kiData, stats, float.class);
            try {                                                                   // ki melee: optional (ki attacks still work without it)
                strikeData = Class.forName("com.dragonminez.common.stats.techniques.StrikeAttackData");
                strikeDamage = stats.getMethod("getStrikeDamage");
                sDamageMult = strikeData.getMethod("getDamageMultiplier");
                sDuration = strikeData.getMethod("getDurationTicks");
                sCooldown = strikeData.getMethod("getActualCooldown");
                sCast = strikeData.getMethod("getActualCastTime");
                strikeSource = Class.forName("com.dragonminez.common.init.MainDamageTypes").getMethod("strikeAttack", Level.class, Entity.class, String.class);
                techConfig = Class.forName("com.dragonminez.common.config.ConfigManager").getMethod("getTechniqueConfig");
                strikeConfig = techConfig.getReturnType().getMethod("getStrikeConfig", String.class);
                cfgDamageMult = strikeConfig.getReturnType().getMethod("getDamageMultiplier");
            } catch (Throwable t) {
                strikeData = null;
                XenoWarriors.LOGGER.warn("Bots can't use DragonMineZ ki melee techniques (its API changed?)", t);
            }
            return true;
        } catch (Throwable t) {
            broken = true; execute = null;
            XenoWarriors.LOGGER.warn("Bots can't use DragonMineZ ki techniques (its API changed?)", t);
            return false;
        }
    }

    private static void fail(Throwable t) {
        if (broken) return;
        broken = true;
        XenoWarriors.LOGGER.warn("Bots stop using DragonMineZ ki techniques (it didn't work)", t);
    }

    /** A bot's DMZ character, read into its own StatsData (refreshed now and then, or when the saved one changes). */
    private static final class Kit {
        Object stats;
        CompoundTag from;
        long readAt = -1_000_000L;
        final List<Object> techniques = new ArrayList<>();
        final Map<String, Long> ready = new HashMap<>();
        long nextAny;
        boolean charging;
    }

    private static final Map<UUID, Kit> KITS = new HashMap<>();

    /** (For the tests) the last things that happened, newest last. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    private static void note(String s) {
        if (LOG.size() > 40) LOG.remove(0);
        LOG.add(s);
    }

    /** (For the tests) its techniques are ready again. */
    public static void resetForTest(UUID bot) { Kit k = KITS.get(bot); if (k != null) { k.ready.clear(); k.nextAny = 0; k.charging = false; } }

    /** (For the tests) its DMZ character's ki damage and the ki-attack modifier a player would get. */
    public static String kiDamageForTest(PartyBotEntity b) {
        Kit k = KITS.get(b.getUUID());
        if (k == null || k.stats == null) return "-";
        StringBuilder sb = new StringBuilder();
        for (String m : new String[]{"getKiDamage", "getKiAttackDamageModifier"})
            try { sb.append(m).append('=').append(k.stats.getClass().getMethod(m).invoke(k.stats)).append(' '); } catch (Throwable t) { sb.append(m).append("=? "); }
        return sb.toString().trim();
    }

    /** A bot is gone: forget its kit. */
    public static void forget(UUID bot) { KITS.remove(bot); }

    private static Kit kit(PartyBotEntity b, java.util.function.Supplier<Player> standIn) {
        ServerPlayer o = b.owner();
        if (o == null) return null;
        String key = o.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER)
                .map(d -> com.xenowarriors.character.PlayerCharacter.key(b.character(), d.youSlot)).orElse(b.character().getId());
        long now = b.level().getGameTime();
        Kit k = KITS.computeIfAbsent(b.getUUID(), u -> new Kit());
        boolean fresh = now - k.readAt < REFRESH && now >= k.readAt;
        // a character put away is the same tag until it's saved again; one being played is re-read every 30 s
        CompoundTag tag = null;
        if (isLive(o, key)) { if (fresh) return k; tag = DmzPerCharacter.saved(o, key); }
        else { tag = DmzPerCharacter.saved(o, key); if (fresh && tag == k.from) return k; }
        k.from = tag; k.readAt = now;
        k.stats = null; k.techniques.clear();
        if (tag == null) return k;
        try {
            Object stats = statsNew.newInstance(standIn.get());                     // (a copy of its own: the owner's data is never touched)
            statsLoad.invoke(stats, tag.copy());
            Object tech = getTechniques.invoke(stats);
            String[] slots = (String[]) equipped.invoke(tech);
            Map<?, ?> all = (Map<?, ?>) unlocked.invoke(tech);
            if (slots != null) for (String id : slots) {
                if (id == null || id.isEmpty()) continue;
                Object t = all.get(id);
                if (strikeData != null && strikeData.isInstance(t)) { k.techniques.add(t); continue; }   // ki melee
                if (!kiData.isInstance(t) || !usable(t)) continue;
                k.techniques.add(t);
            }
            k.stats = stats;
        } catch (Throwable t) { fail(t); }
        return k;
    }

    private static boolean isLive(ServerPlayer o, String key) {
        return key.equals(o.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getString("xw_dmz_live"));
    }

    /** Attacks only: no healing, no barriers, no fake moons. */
    private static boolean usable(Object t) {
        try {
            String u = String.valueOf(utility.invoke(t)), type = String.valueOf(kiType.invoke(t)), id = String.valueOf(techId.invoke(t));
            return !u.contains("HEAL") && !type.contains("BARRIER") && !id.equals("fake_moon");
        } catch (Throwable e) { return false; }
    }

    /** The ki techniques this bot would use (ids), for the tests and the logs. */
    public static List<String> techniques(PartyBotEntity b, java.util.function.Supplier<Player> standIn) {
        List<String> out = new ArrayList<>();
        if (!ready()) return out;
        Kit k = kit(b, standIn);
        if (k != null) for (Object t : k.techniques) {
            try {
                if (isStrike(t)) out.add(techId.invoke(t) + "(STRIKE, cd " + sCooldown.invoke(t) + ", " + sDuration.invoke(t) + " ticks)");
                else out.add(techId.invoke(t) + "(" + kiType.invoke(t) + ", cd " + cooldown.invoke(t) + ", cast " + castTime.invoke(t) + ")");
            } catch (Throwable ignored) {}
        }
        return out;
    }

    /**
     * Maybe throw one of its ki techniques at the target instead of a skill. The caller has already checked it's worth
     * a skill (not a "No skills" battle, a strong enough enemy, in range). True when one was started.
     */
    public static boolean tryFire(PartyBotEntity b, LivingEntity target, java.util.function.Supplier<Player> standIn, boolean always) {
        if (!ready() || !(b.level() instanceof ServerLevel level) || target == null || !target.isAlive()) return false;
        long now = level.getGameTime();
        Kit k = KITS.get(b.getUUID());
        if (k != null && (k.charging || now < k.nextAny)) return false;
        if (!always && b.getRandom().nextInt(2) != 0) return false;                 // (its own skills get their turn too)
        k = kit(b, standIn);
        if (k == null || k.stats == null || k.techniques.isEmpty()) return false;
        if (!b.hasLineOfSight(target)) return false;
        boolean near = b.distanceTo(target) <= STRIKE_RANGE;
        List<Object> ready = new ArrayList<>();
        for (Object t : k.techniques) {
            if (isStrike(t) && !near) continue;                                     // (ki melee: it flashes over to them, but not from across the field)
            try { if (now >= k.ready.getOrDefault((String) techId.invoke(t), 0L)) ready.add(t); } catch (Throwable ignored) {}
        }
        if (ready.isEmpty()) return false;
        Object tech = ready.get(b.getRandom().nextInt(ready.size()));
        if (isStrike(tech)) return strike(b, target, tech, k, now);
        try {
            String id = (String) techId.invoke(tech);
            int cd = Math.max(MIN_GAP, Math.min(20 * 60, (Integer) cooldown.invoke(tech)));
            int cast = (Boolean) instant.invoke(tech) ? 1 : Math.max(8, Math.min(MAX_CAST, (Integer) castTime.invoke(tech)));
            aim(b, target);
            boolean started = (Boolean) execute.invoke(null, b, level, tech, k.stats, 0F);   // (charging in its hands; a plain blast flies at once)
            note("start " + id + " ok=" + started + " cast=" + cast + " cd=" + cd + " target=" + target.getType().getDescriptionId());
            if (!started) { k.nextAny = now + MIN_GAP; return false; }
            k.ready.put(id, now + cd);
            k.nextAny = now + Math.max(MIN_GAP, cast + 20);
            k.charging = true;
            final Kit kk = k;
            com.xenowarriors.anim.Poses.play(b, com.xenowarriors.anim.Poses.EL_CHARGE, cast);
            final Object stats = k.stats;
            for (int i = 1; i < cast; i++) com.xenowarriors.skill.ServerScheduler.later(i, () -> { if (kk.charging && b.isAlive() && target.isAlive()) aim(b, target); });   // (keeps facing it while charging)
            com.xenowarriors.skill.ServerScheduler.later(cast, () -> release(b, target, tech, kk, stats, id));
            return true;
        } catch (Throwable t) { fail(t); return false; }
    }

    // ---------------------------------------------------------------- ki melee

    private static final double STRIKE_RANGE = 12;

    private static boolean isStrike(Object t) { return strikeData != null && strikeData.isInstance(t); }

    /**
     * A ki melee technique, the way DragonMineZ runs them for players (its own handler only works for real players):
     * a short wind-up, it appears right in front of the target, then a flurry - a hit every half second for the
     * technique's duration (65% of the damage) and a final blow (35%) that knocks them back. The damage is
     * DragonMineZ's: the character's strike damage x the technique's multiplier x the server's config multiplier.
     */
    private static boolean strike(PartyBotEntity b, LivingEntity target, Object tech, Kit k, long now) {
        try {
            String id = (String) techId.invoke(tech);
            double cfg = 1.0;
            try { cfg = Math.max(0.0, ((Number) cfgDamageMult.invoke(strikeConfig.invoke(techConfig.invoke(null), id))).doubleValue()); } catch (Throwable ignored) {}
            double total = ((Number) strikeDamage.invoke(k.stats)).doubleValue() * ((Number) sDamageMult.invoke(tech)).doubleValue() * cfg;
            if (!(total > 0)) { k.ready.put(id, now + MIN_GAP); return false; }
            int duration = Math.max(20, (Integer) sDuration.invoke(tech)), hits = Math.max(1, (int) Math.ceil(duration / 10.0));
            float perHit = (float) (total * 0.65 / hits), last = (float) (total * 0.35);
            int cd = Math.max(MIN_GAP, Math.min(20 * 60, (Integer) sCooldown.invoke(tech)));
            int windup = Math.max(4, Math.min(20, (Integer) sCast.invoke(tech)));
            k.ready.put(id, now + cd);
            k.nextAny = now + windup + duration + 20;
            k.charging = true;
            note("strike start " + id + " total=" + Math.round(total * 10) / 10.0 + " hits=" + hits + " cd=" + cd + " target=" + target.getType().getDescriptionId());
            aim(b, target);
            com.xenowarriors.anim.Poses.play(b, com.xenowarriors.anim.Poses.EL_CHARGE, windup);
            final Kit kk = k;
            com.xenowarriors.skill.ServerScheduler.later(windup, () -> {
                if (!canStrike(b, target)) { kk.charging = false; note("strike " + id + " called off"); return; }
                appear(b, target);
                for (int i = 0; i <= hits; i++) {
                    final int n = i;
                    com.xenowarriors.skill.ServerScheduler.later(i * 10, () -> {
                        if (!kk.charging) return;
                        if (!canStrike(b, target) || b.distanceTo(target) > 6) { kk.charging = false; note("strike " + id + " broke off after " + n + " hits"); return; }
                        if (b.distanceTo(target) > 2.5) appear(b, target);
                        aim(b, target);
                        b.getNavigation().stop();
                        target.setDeltaMovement(target.getDeltaMovement().multiply(0, 1, 0));   // (held in place by the flurry)
                        boolean fin = n == hits;
                        hit(b, target, id, fin ? last : perHit, fin);
                        if (fin) { kk.charging = false; note("strike " + id + " done"); }
                    });
                }
            });
            return true;
        } catch (Throwable t) { fail(t); return false; }
    }

    private static boolean canStrike(PartyBotEntity b, LivingEntity target) {
        return b.isAlive() && !b.isRemoved() && !b.downed() && target != null && target.isAlive() && target.level() == b.level();
    }

    /** Flashes over to just in front of the target (if there's room - otherwise it stays where it is). */
    private static void appear(PartyBotEntity b, LivingEntity target) {
        Vec3 dir = b.position().subtract(target.position()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-4) dir = Vec3.directionFromRotation(0, target.getYRot());
        dir = dir.normalize().scale(target.getBbWidth() / 2 + b.getBbWidth() / 2 + 0.5);
        Vec3 to = target.position().add(dir);
        var box = b.getBoundingBox().move(to.subtract(b.position()));
        if (!b.level().noCollision(b, box)) return;
        if (b.level() instanceof ServerLevel l) l.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, b.getX(), b.getY() + 1, b.getZ(), 6, .2, .4, .2, .02);
        b.teleportTo(to.x, to.y, to.z);
        aim(b, target);
    }

    private static void hit(PartyBotEntity b, LivingEntity target, String id, float amount, boolean last) {
        net.minecraft.world.damagesource.DamageSource src;
        try { src = (net.minecraft.world.damagesource.DamageSource) strikeSource.invoke(null, b.level(), b, id); }
        catch (Throwable t) { src = b.damageSources().mobAttack(b); }
        target.invulnerableTime = 0;
        boolean landed = target.hurt(src, Math.max(0.01F, amount));
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        com.xenowarriors.anim.Poses.play(b, last ? com.xenowarriors.anim.Poses.KA_BREAK : com.xenowarriors.anim.Poses.EL_THRUST, last ? 10 : 6);
        if (b.level() instanceof ServerLevel l) {
            Vec3 c = target.getBoundingBox().getCenter();
            l.sendParticles(last ? net.minecraft.core.particles.ParticleTypes.EXPLOSION : net.minecraft.core.particles.ParticleTypes.CRIT, c.x, c.y, c.z, last ? 2 : 6, .3, .3, .3, last ? 0 : .3);
            l.playSound(null, target.blockPosition(), last ? net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK : net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, last ? 0.8F : 1.1F + b.getRandom().nextFloat() * 0.2F);
        }
        if (last && landed) {
            Vec3 d = target.position().subtract(b.position());
            target.knockback(1.4, -d.x, -d.z);
            target.hurtMarked = true;
        }
        note("strike hit " + id + " " + Math.round(amount * 100) / 100.0 + (landed ? "" : " (blocked)") + (last ? " final" : ""));
    }

    private static void release(PartyBotEntity b, LivingEntity target, Object tech, Kit k, Object stats, String id) {
        k.charging = false;
        if (!b.isAlive() || b.isRemoved() || b.downed() || !(b.level() instanceof ServerLevel level)) { drop(b); note("release " + id + " dropped (bot down/gone)"); return; }
        if (target != null && target.isAlive() && target.level() == b.level()) aim(b, target);
        try {
            boolean ok = (Boolean) execute.invoke(null, b, level, tech, stats, 1F);
            note("release " + id + " ok=" + ok + " facing " + Math.round(b.getYHeadRot()) + "/" + Math.round(b.getXRot()) + " " + inAir(b));
            com.xenowarriors.skill.ServerScheduler.later(3, () -> note("after " + id + ": " + inAir(b)));
        } catch (Throwable t) { fail(t); drop(b); return; }
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        com.xenowarriors.anim.Poses.play(b, com.xenowarriors.anim.Poses.KA_BREAK, 10);
    }

    /** (For the logs) its ki attacks around: kind, firing, which way they point, their damage. */
    private static String inAir(PartyBotEntity b) {
        if (!(b.level() instanceof ServerLevel level)) return "";
        StringBuilder sb = new StringBuilder("[");
        for (Projectile p : level.getEntitiesOfClass(Projectile.class, b.getBoundingBox().inflate(24), e -> e.getOwner() == b && com.xenowarriors.ka.DmzKi.isKi(e))) {
            Object dmg = null;
            try { dmg = p.getClass().getMethod("getKiDamage").invoke(p); } catch (Throwable ignored) {}
            sb.append(p.getClass().getSimpleName()).append(" firing=").append(firing(p)).append(" rot=").append(Math.round(p.getYRot())).append('/').append(Math.round(p.getXRot()))
                    .append(" dmg=").append(dmg).append(" at ").append(p.blockPosition().toShortString()).append("; ");
        }
        return sb.append(']').toString();
    }

    /** Anything it was still charging goes away (it went down mid-cast). */
    private static void drop(PartyBotEntity b) {
        if (!(b.level() instanceof ServerLevel level)) return;
        for (Projectile p : level.getEntitiesOfClass(Projectile.class, b.getBoundingBox().inflate(8), e -> e.getOwner() == b && com.xenowarriors.ka.DmzKi.isKi(e)))
            if (!firing(p)) p.discard();
    }

    private static boolean firing(Entity ki) {
        try { return (Boolean) ki.getClass().getMethod("isFiring").invoke(ki); } catch (Throwable t) { return true; }
    }

    /** Faces it: body, head and pitch (DragonMineZ aims where the thrower looks). */
    private static void aim(PartyBotEntity b, LivingEntity target) {
        Vec3 d = target.getBoundingBox().getCenter().subtract(b.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        b.setYRot(yaw); b.yRotO = yaw; b.setYHeadRot(yaw); b.yHeadRotO = yaw; b.yBodyRot = yaw; b.yBodyRotO = yaw;
        b.setXRot(pitch); b.xRotO = pitch;
        b.getLookControl().setLookAt(target, 360F, 360F);
    }
}
