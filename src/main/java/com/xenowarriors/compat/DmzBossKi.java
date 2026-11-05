package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Our bosses throwing DragonMineZ's own ki attacks (a ki blast, a Kamehameha) - the same way bots do (DmzBotKi): its
 * TechniqueDispatcher with a StatsData of our own (on the server's stand-in player), charged then fired at the target.
 * How hard they hit is set by the boss (ZombieSaiyans rescales the hit). -1 when DragonMineZ isn't there or it fails.
 */
public final class DmzBossKi {
    private DmzBossKi() {}

    private static Boolean loaded;
    private static boolean broken;
    private static Method execute, castTime, instant, kiType;
    private static Object blastTech, beamTech;
    private static String blastId = "-", beamId = "-";

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (execute != null) return true;
        try {
            Class<?> stats = Class.forName("com.dragonminez.common.stats.StatsData");
            Class<?> kiData = Class.forName("com.dragonminez.common.stats.techniques.KiAttackData");
            castTime = kiData.getMethod("getActualCastTime");
            instant = kiData.getMethod("isInstantCast");
            kiType = kiData.getMethod("getKiType");
            Map<?, ?> reg = (Map<?, ?>) Class.forName("com.dragonminez.common.stats.techniques.PredefinedTechniques").getField("REGISTRY").get(null);
            beamTech = reg.get("kamehameha");
            for (var e : reg.entrySet()) {
                String type = String.valueOf(kiType.invoke(e.getValue()));
                if (blastTech == null && type.contains("BLAST") && !type.contains("LARGE")) { blastTech = e.getValue(); blastId = String.valueOf(e.getKey()); }
                if (beamTech == null && (type.contains("WAVE") || type.contains("BEAM"))) { beamTech = e.getValue(); beamId = String.valueOf(e.getKey()); }
            }
            if (beamTech != null && beamId.equals("-")) beamId = "kamehameha";
            if (blastTech == null) blastTech = beamTech;
            execute = Class.forName("com.dragonminez.common.stats.techniques.TechniqueDispatcher")
                    .getMethod("executeKiAttack", LivingEntity.class, Level.class, kiData, stats, float.class);
            XenoWarriors.LOGGER.info("XW boss ki: blast={} beam={}", blastId, beamId);
            return blastTech != null;
        } catch (Throwable t) {
            broken = true; execute = null;
            XenoWarriors.LOGGER.warn("Bosses can't use DragonMineZ ki attacks (its API changed?) - they use their own", t);
            return false;
        }
    }

    /** The techniques it uses, for the logs: "blast/beam". */
    public static String kit() { return ready() ? blastId + "/" + beamId : "none"; }

    private static Object stats(ServerLevel level) {
        Player stand = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        Object sd = DmzPerCharacter.blank(stand);
        if (sd == null) return null;
        try {
            Object st = sd.getClass().getMethod("getStats").invoke(sd);
            for (String m : new String[]{"setStrength", "setStrikePower", "setResistance", "setVitality", "setKiPower", "setEnergy"}) st.getClass().getMethod(m, int.class).invoke(st, 100);
            Object status = sd.getClass().getMethod("getStatus").invoke(sd);
            status.getClass().getMethod("setHasCreatedCharacter", boolean.class).invoke(status, true);
        } catch (Throwable ignored) {}
        return sd;
    }

    /** Starts one: returns its cast time in ticks (it's fired after that), or -1 (not available). */
    public static int fire(Mob caster, LivingEntity target, boolean beam) {
        if (!ready() || !(caster.level() instanceof ServerLevel level)) return -1;
        Object tech = beam && beamTech != null ? beamTech : blastTech;
        try {
            Object sd = stats(level);
            if (sd == null) return -1;
            int cast = (Boolean) instant.invoke(tech) ? 1 : Math.max(8, Math.min(40, (Integer) castTime.invoke(tech)));
            aim(caster, target);
            boolean ok = (Boolean) execute.invoke(null, caster, level, tech, sd, 0F);
            if (!ok) return -1;
            for (int i = 1; i < cast; i++) com.xenowarriors.skill.ServerScheduler.later(i, () -> { if (caster.isAlive() && target.isAlive()) aim(caster, target); });
            com.xenowarriors.skill.ServerScheduler.later(cast, () -> {
                if (!caster.isAlive() || caster.isRemoved()) return;
                if (target.isAlive() && target.level() == caster.level()) aim(caster, target);
                try { execute.invoke(null, caster, level, tech, sd, 1F); } catch (Throwable t) { broken = true; }
            });
            return cast;
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("Boss DragonMineZ ki attack failed - using its own from now on", t);
            return -1;
        }
    }

    private static void aim(Mob b, LivingEntity target) {
        Vec3 d = target.getBoundingBox().getCenter().subtract(b.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        b.setYRot(yaw); b.yRotO = yaw; b.setYHeadRot(yaw); b.yHeadRotO = yaw; b.yBodyRot = yaw; b.yBodyRotO = yaw;
        b.setXRot(pitch); b.xRotO = pitch;
    }

}
