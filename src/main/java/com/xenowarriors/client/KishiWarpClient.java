package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.KishiFxPacket;
import com.xenowarriors.network.KishiWarpPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rhalix's Kishi Warp on the client: sending it (held attack while running, see ComboClient - with its own silent
 * 5-second cooldown: no HUD, no message, nothing), and what everyone sees of it - him knelt with a hand to the floor
 * while the Kishi floods out along the ground (RhalixComboAnim, RhalixComboFx), the warp, the slash that follows.
 */
public final class KishiWarpClient {
    /** One use of it: who, when he knelt, the front of the wave so far, how it ended. */
    public static final class Wave {
        public final int entity; public final long start; public final List<Vec3> path = new ArrayList<>();
        public long end = -1, warpAt = -1; public int warpTick = Integer.MIN_VALUE, target = -1; public boolean fizzled;
        public Vec3 from, to;
        Wave(int entity, long start, Vec3 origin) { this.entity = entity; this.start = start; path.add(origin); }
    }
    private static final Map<Integer, Wave> WAVES = ClientReset.track(new HashMap<>());
    private static long readyAt, warpingUntil;

    private KishiWarpClient() {}

    private static long now() { var l = Minecraft.getInstance().level; return l == null ? 0 : l.getGameTime(); }

    /** Off cooldown (your own; nothing shows it). */
    public static boolean ready() { return now() >= readyAt; }

    /** You're knelt sending it out (no moving, no other attacks). */
    public static boolean warping() { return now() < warpingUntil; }

    public static void fire(LocalPlayer p, LivingEntity hint) {
        readyAt = now() + com.xenowarriors.combat.RhalixKishiWarp.COOLDOWN;
        warpingUntil = now() + 34;
        p.setSprinting(false);
        p.setDeltaMovement(0, p.getDeltaMovement().y, 0);
        ModNetwork.sendToServer(new KishiWarpPacket(hint == null ? -1 : hint.getId()));
        RhalixComboFx.log("kishi warp sent (hint " + (hint == null ? "none" : hint.getType().getDescriptionId()) + ")");
    }

    public static void receive(KishiFxPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        Vec3 a = new Vec3(m.ax(), m.ay(), m.az());
        Wave w = WAVES.get(m.entity());
        switch (m.phase()) {
            case KishiFxPacket.START -> WAVES.put(m.entity(), new Wave(m.entity(), now, a));
            case KishiFxPacket.HEAD -> { if (w != null) w.path.add(a); }
            case KishiFxPacket.WARP, KishiFxPacket.FIZZLE -> {
                if (w == null) { w = new Wave(m.entity(), now - 6, a); WAVES.put(m.entity(), w); }
                w.end = now;
                if (m.phase() == KishiFxPacket.FIZZLE) { w.fizzled = true; w.path.add(a); }
                else {
                    w.warpAt = now; w.from = a; w.to = new Vec3(m.bx(), m.by(), m.bz()); w.target = m.target();
                    Entity e = mc.level.getEntity(m.entity());
                    if (e != null) w.warpTick = e.tickCount;
                    if (mc.level.getEntity(m.target()) instanceof LivingEntity t) w.path.add(t.position());
                    RhalixComboFx.warpBurst(mc.level, w);
                }
                if (mc.player != null && m.entity() == mc.player.getId()) {
                    warpingUntil = 0;
                    if (m.phase() == KishiFxPacket.WARP) com.xenowarriors.client.combo.ComboClient.warpString(mc.player, m.target());
                }
            }
            default -> {}
        }
        if (System.getenv("XW_UITEST_FOCUS") != null && m.phase() != KishiFxPacket.HEAD)
            XenoWarriors.LOGGER.info("XW-TEST rhalixcombo client kishi phase " + m.phase() + " entity " + m.entity() + " target " + m.target() + " at " + now);
    }

    /** The waves to draw (RhalixComboFx), dropping old ones. */
    static Iterable<Wave> waves() {
        long now = now();
        WAVES.values().removeIf(w -> now - w.start > 120 || w.end >= 0 && now - w.end > 50);
        return WAVES.values();
    }

    /** Ticks since he knelt (while knelt: until the warp or for a moment after it fizzles), or -1. */
    public static float crouchTicks(Entity e, float partial) {
        Wave w = WAVES.get(e.getId());
        if (w == null) return -1F;
        float t = now() - w.start + partial;
        if (w.end >= 0 && now() + partial > w.end + (w.fizzled ? 3 : 0)) return -1F;
        return t > 40 ? -1F : t;
    }

    /** This swing (started at the entity's tick {@code swingStart}) is the slash he warped in with. */
    public static boolean warpSlash(Entity e, int swingStart) {
        Wave w = WAVES.get(e.getId());
        return w != null && w.warpTick != Integer.MIN_VALUE && Math.abs(swingStart - w.warpTick) <= 5;
    }
}
