package com.xenowarriors.compat;

import com.xenowarriors.network.DmzFxPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

/**
 * With DragonMineZ: some of our energy attacks are drawn with DragonMineZ's ki particles (its ki trail, shedding,
 * splash and explosion effects, tinted the attack's colour) instead of plain dust - Ultima's Energy Blast and Full
 * Output Beam, Karma Blast. Without DragonMineZ (or with kiVisuals=false) the callers keep their own visuals.
 */
public final class DmzFx {
    private DmzFx() {}

    private static Boolean loaded;

    /** Use DragonMineZ's look? */
    public static boolean on() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        return loaded && com.xenowarriors.XwConfig.get(com.xenowarriors.XwConfig.DMZ_KI_VISUALS);
    }

    private static void send(ServerLevel l, DmzFxPacket m) {
        for (ServerPlayer p : l.players()) if (p.distanceToSqr(m.ax(), m.ay(), m.az()) < 72 * 72) ModNetwork.sendToPlayer(m, p);
    }

    /** A ki ball at pos, flying along dir. */
    public static void ball(ServerLevel l, Vec3 pos, Vec3 dir, int rgb, float size) {
        send(l, new DmzFxPacket(DmzFxPacket.BALL, pos.x, pos.y, pos.z, dir.x, dir.y, dir.z, rgb, size));
    }

    /** A ki beam from a to b (one tick of it). */
    public static void beam(ServerLevel l, Vec3 a, Vec3 b, int rgb, float width) {
        send(l, new DmzFxPacket(DmzFxPacket.BEAM, a.x, a.y, a.z, b.x, b.y, b.z, rgb, width));
    }

    /** Ki going off at a point. */
    public static void burst(ServerLevel l, Vec3 at, int rgb, float size) {
        send(l, new DmzFxPacket(DmzFxPacket.BURST, at.x, at.y, at.z, 0, 0, 0, rgb, size));
    }

    public static int rgb(org.joml.Vector3f c) {
        return (Math.round(c.x() * 255) << 16) | (Math.round(c.y() * 255) << 8) | Math.round(c.z() * 255);
    }
}
