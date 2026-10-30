package com.xenowarriors.ka;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * Clashes with DragonMineZ: our blast skills meet DragonMineZ ki attacks (ki blasts, waves, lasers, disks...) and
 * push against them like any clash. Fire a clashing skill at a ki attack coming your way - or have one fired at you
 * just after you fired yours - and they lock together between you and whoever threw it. You mash attack to push;
 * the other side pushes with what the ki attack carries (its damage) - and mashes too if they're a player with this
 * mod. The ki attack is held where the blasts meet, and spent when the clash ends; the loser takes the hit.
 * DragonMineZ isn't needed to build or run: everything here goes through reflection and switches itself off.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzKi {
    private DmzKi() {}

    private static Boolean loaded;
    private static boolean broken;
    private static Class<?> kiClass;
    private static Method ownerUuid, kiDamage, color, lock, unlock, locked, firing, beam;

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (kiClass != null) return true;
        try {
            kiClass = Class.forName("com.dragonminez.common.init.entities.ki.AbstractKiProjectile");
            ownerUuid = kiClass.getMethod("getOwnerUUID");
            kiDamage = kiClass.getMethod("getKiDamage");
            color = kiClass.getMethod("getColor");
            lock = kiClass.getMethod("setClashLock", float.class, java.util.UUID.class);
            unlock = kiClass.getMethod("clearClashLock");
            locked = kiClass.getMethod("isClashLocked");
            firing = kiClass.getMethod("isFiring");
            beam = kiClass.getMethod("isClashableBeam");
            return true;
        } catch (Throwable t) {
            broken = true; kiClass = null;
            XenoWarriors.LOGGER.warn("DragonMineZ ki attacks can't clash with Xeno Warriors skills (its API changed?)", t);
            return false;
        }
    }

    public static boolean isKi(Entity e) { return e != null && ready() && kiClass.isInstance(e); }

    private static Object call(Method m, Entity e, Object... args) {
        try { return m.invoke(e, args); } catch (Throwable t) { return null; }
    }

    /** Who threw it (living), if they're still around. */
    static LivingEntity owner(Entity ki) {
        if (ki instanceof Projectile pr && pr.getOwner() instanceof LivingEntity l) return l;
        Object id = call(ownerUuid, ki);
        if (id instanceof java.util.UUID u && ki.level() instanceof ServerLevel sl && sl.getEntity(u) instanceof LivingEntity l) return l;
        return null;
    }

    /** A ki attack heading for this player, in front of them, not already clashing. */
    static Entity incoming(ServerPlayer p) { return incoming(p, null); }

    /** (why: each ki attack nearby and why it can or can't clash - for the tests' logs) */
    private static Entity incoming(ServerPlayer p, StringBuilder why) {
        if (!ready()) return null;
        Vec3 look = p.getViewVector(1F), eye = p.getEyePosition();
        Entity best = null; double bd = Double.MAX_VALUE;
        for (Projectile e : p.serverLevel().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(24), e -> kiClass.isInstance(e) && e.isAlive())) {
            String no = reject(p, e, look, eye);
            if (why != null) why.append(e.getClass().getSimpleName()).append('@').append(Math.round(e.distanceTo(p))).append(':').append(no == null ? "ok" : no).append(' ');
            if (no != null) continue;
            double d = e.distanceTo(p);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private static String reject(ServerPlayer p, Projectile e, Vec3 look, Vec3 eye) {
        LivingEntity o = owner(e);
        if (o == p) return "own";
        if (Boolean.TRUE.equals(call(locked, e)) || Clashes.isKiClashing(e)) return "locked";
        if (Boolean.FALSE.equals(call(firing, e))) return "charging";                          // still being charged in their hands
        if (o != null && !com.xenowarriors.skill.SkillTargeting.canTarget(p, o)) return "friend";
        if (beam(e)) {                                                                          // a beam: starts at the thrower, points somewhere
            Vec3 dir = Vec3.directionFromRotation(e.getXRot(), e.getYRot());
            Vec3 at = eye.subtract(e.position());
            if (at.length() > 30) return "far";
            if (at.normalize().dot(dir) < Math.cos(Math.toRadians(25))) return "aimed-away";
            if (at.normalize().scale(-1).dot(look) < Math.cos(Math.toRadians(40))) return "behind";
            return null;
        }
        Vec3 to = e.position().subtract(eye);
        double d = to.length();
        if (d < 0.5 || to.normalize().dot(look) < Math.cos(Math.toRadians(40))) return "behind";   // in front of them
        Vec3 v = e.getDeltaMovement();
        if (v.lengthSqr() < 1.0E-4) return "still";
        if (v.normalize().dot(to.normalize().scale(-1)) < 0.5) return "not-coming";
        return null;
    }

    private static boolean beam(Entity ki) { return Boolean.TRUE.equals(call(beam, ki)); }

    /** For the tests: the ki attacks around this player and whether they could clash. */
    public static String why(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        incoming(p, sb);
        return sb.length() == 0 ? "(no ki attacks near)" : sb.toString();
    }

    /** A player fired a clashing skill: a ki attack coming at them turns it into a clash. */
    static boolean tryClash(ServerPlayer p, int ourColor) {
        Entity ki = incoming(p);
        if (ki == null) return false;
        LivingEntity o = owner(ki);
        if (o == null || o.distanceTo(p) > 32) return false;
        Object dmg = call(kiDamage, ki);
        float kd = dmg instanceof Number n ? n.floatValue() : 10F;
        Object col = call(color, ki);
        int kc = col instanceof Number n ? n.intValue() & 0xFFFFFF : 0xFFE060;
        float bias = (float) Math.max(0.6, Math.min(2.2, Math.sqrt(Math.max(1F, kd) / 12F)));   // a heavy ki attack pushes harder
        Vec3 mid = p.getEyePosition().lerp(o.getEyePosition(), 0.5);
        if (beam(ki)) hold(ki, mid, p); else call(lock, ki, 0.5F, p.getUUID());
        Clashes.startKi(p, o, ki, ourColor, kc, Math.max(12F, kd), bias);
        return true;
    }

    /** Held where the blasts meet, not flying anywhere. */
    static void hold(Entity ki, Vec3 at, LivingEntity against) {
        if (beam(ki)) {                     // a beam stays put at its thrower and is cut off where the blasts meet (DragonMineZ's own clash lock: no damage while locked)
            call(lock, ki, (float) Math.max(0.5, ki.position().distanceTo(at)), against.getUUID());
            return;
        }
        ki.setDeltaMovement(Vec3.ZERO);
        ki.setPos(at.x, at.y, at.z);
        ki.hurtMarked = true;
    }

    /** The clash is over: the ki attack is spent. */
    static void release(Entity ki) {
        call(unlock, ki);
        ki.discard();
    }

    /** The other way round: a ki attack fired at someone who just fired a clashing skill of their own. */
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ready()) return;
        var server = event.getServer();
        if (server.getTickCount() % 2 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            long until = p.getPersistentData().getLong("xw_clash_ready");
            if (until < p.level().getGameTime() || Clashes.inClash(p)) continue;
            int c = p.getPersistentData().getInt("xw_clash_ready_color");
            if (tryClash(p, c)) p.getPersistentData().remove("xw_clash_ready");
        }
    }
}
