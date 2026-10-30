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
            try { out.add(techId.invoke(t) + "(" + kiType.invoke(t) + ", cd " + cooldown.invoke(t) + ", cast " + castTime.invoke(t) + ")"); } catch (Throwable ignored) {}
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
        List<Object> ready = new ArrayList<>();
        for (Object t : k.techniques) {
            try { if (now >= k.ready.getOrDefault((String) techId.invoke(t), 0L)) ready.add(t); } catch (Throwable ignored) {}
        }
        if (ready.isEmpty()) return false;
        Object tech = ready.get(b.getRandom().nextInt(ready.size()));
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
            com.xenowarriors.skill.ServerScheduler.later(cast, () -> release(b, target, tech, kk, stats, id));
            return true;
        } catch (Throwable t) { fail(t); return false; }
    }

    private static void release(PartyBotEntity b, LivingEntity target, Object tech, Kit k, Object stats, String id) {
        k.charging = false;
        if (!b.isAlive() || b.isRemoved() || b.downed() || !(b.level() instanceof ServerLevel level)) { drop(b); note("release " + id + " dropped (bot down/gone)"); return; }
        if (target != null && target.isAlive() && target.level() == b.level()) aim(b, target);
        try {
            boolean ok = (Boolean) execute.invoke(null, b, level, tech, stats, 1F);
            note("release " + id + " ok=" + ok);
        } catch (Throwable t) { fail(t); drop(b); return; }
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        com.xenowarriors.anim.Poses.play(b, com.xenowarriors.anim.Poses.KA_BREAK, 10);
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
