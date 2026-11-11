package com.xenowarriors.raid;

import com.xenowarriors.gov.GovAgentEntity;
import com.xenowarriors.npc.HumanNpcEntity;
import com.xenowarriors.sr.SrMemberEntity;
import com.xenowarriors.vehicle.VehicleEntity;
import com.xenowarriors.vehicle.VehicleKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Who comes, how many and from where (the numbers):
 * <ul>
 * <li>Government: soldiers per wave = 10 + 4 per wave + 4 if Hated + 5 per extra fighter (players; bots count half),
 * at most 40. Helicopters = 1 + (wave-1)/2 + 1 if Hated (max 3), each carrying ~55% of the soldiers between them
 * (4 to 10 aboard: 6 in the cabin, 4 hanging off the skids); armored trucks / Mk II cars = 1 + wave/2 + 1 with company
 * (max 3) carry the rest. Helicopters start ~60 blocks out at +16, ground vehicles ~40 blocks out. Mk II helicopters
 * from wave 3 (or when Hated), an Armored Truck Mk II and the Commander on the last wave. They wear their Karma Scanners
 * (and flee for a while when one breaks).</li>
 * <li>Sky Rebellion: 4 + 2 per wave + 2 if Hated + 3 per extra fighter (max 24), diving from 18-24 blocks up, 16-26 out;
 * Logan, Kill or Keita leads the last wave.</li>
 * <li>Townsfolk: 5 + 2 per wave + 2 if Hated + 3 per extra fighter (max 24) on foot, 26-34 blocks out, from the
 * direction of the nearest town (villagers / townsfolk within 128 blocks); a Militia Captain leads the last wave.</li>
 * </ul>
 */
public final class RaidSpawns {
    private RaidSpawns() {}

    /** Ground under / around a column: the top of whatever blocks motion (trees too). */
    static int surface(ServerLevel l, double x, double z) {
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
    }

    /** A spot on solid, dry ground with room for a vehicle, roughly {@code dist} out at {@code angle} (null if none in ticking chunks). */
    static Vec3 groundSpot(ServerLevel l, Vec3 c, double angle, double dist, double room) {
        for (int tries = 0; tries < 20; tries++) {
            double a = angle + (tries % 2 == 0 ? 1 : -1) * (tries / 2) * 0.3;
            double d = Math.max(10, dist - (tries / 4) * 6);
            int x = (int) Math.floor(c.x + Math.cos(a) * d), z = (int) Math.floor(c.z + Math.sin(a) * d);
            BlockPos top = new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            if (!l.isPositionEntityTicking(top) || Math.abs(top.getY() - c.y) > 14) continue;
            BlockState below = l.getBlockState(top.below());
            if (!below.getFluidState().isEmpty() || below.is(BlockTags.LEAVES) || !below.isFaceSturdy(l, top.below(), Direction.UP)) continue;
            if (!l.noCollision(new AABB(top).inflate(room / 2, 0, room / 2).expandTowards(0, 2.5, 0).move(0, 0.05, 0))) continue;
            return Vec3.atBottomCenterOf(top);
        }
        return null;
    }

    /** A spot in open air {@code up} blocks over the ground there. */
    static Vec3 airSpot(ServerLevel l, Vec3 c, double angle, double dist, double up) {
        for (int tries = 0; tries < 16; tries++) {
            double a = angle + (tries % 2 == 0 ? 1 : -1) * (tries / 2) * 0.3;
            double d = Math.max(12, dist - (tries / 3) * 8);
            double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
            BlockPos col = BlockPos.containing(x, c.y, z);
            if (!l.isPositionEntityTicking(col)) continue;
            double y = Math.max(surface(l, x, z), c.y) + up;
            if (y > l.getMaxBuildHeight() - 4) y = l.getMaxBuildHeight() - 4;
            Vec3 p = new Vec3(x, y, z);
            if (!l.noCollision(new AABB(p, p).inflate(2, 0, 2).expandTowards(0, 3, 0))) continue;
            return p;
        }
        return null;
    }

    static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0);
    }

    private static void tag(FactionRaids.Raid r, Entity e, boolean vehicle) {
        e.getPersistentData().putUUID(FactionRaids.TAG, r.id);
        (vehicle ? r.vehicles : r.raiders).add(e.getUUID());
    }

    private static void untag(FactionRaids.Raid r, Entity e) {
        r.vehicles.remove(e.getUUID());
        r.raiders.remove(e.getUUID());
    }

    private static void reach(Mob m) {
        var a = m.getAttribute(Attributes.FOLLOW_RANGE);
        if (a != null && a.getBaseValue() < 48) a.setBaseValue(48);
    }

    // ------------------------------------------------------------------ Government

    static void gov(FactionRaids.Raid r, ServerPlayer t, int extra) {
        ServerLevel l = r.level;
        boolean last = r.wave == r.waves;
        int budget = Math.min(40, 10 + 4 * r.wave + 4 * r.severity + 5 * extra);
        int helis = Math.min(3, 1 + (r.wave - 1) / 2 + r.severity);
        int grounds = Math.min(3, 1 + r.wave / 2 + (extra > 0 ? 1 : 0));
        int perHeli = Math.max(4, Math.min(10, (int) Math.ceil(budget * 0.55 / helis)));
        int groundBudget = Math.max(grounds * 2, budget - perHeli * helis);
        double base = l.random.nextDouble() * Math.PI * 2;
        Vec3 c = t.position();
        for (int i = 0; i < helis; i++) {
            Vec3 at = airSpot(l, c, base + (i - (helis - 1) / 2.0) * 0.45, 60, 16);
            if (at == null) continue;
            VehicleKind k = (r.wave >= 3 || r.severity > 0) ? VehicleKind.HELICOPTER_MK2 : VehicleKind.HELICOPTER;
            govVehicle(r, k, at, yawTo(at, c), perHeli, last && i == 0);
        }
        for (int i = 0; i < grounds; i++) {
            VehicleKind k = last && i == 0 ? VehicleKind.GOV_TRUCK_MK2 : i % 2 == 0 ? VehicleKind.GOV_TRUCK : VehicleKind.CAR_MK2;
            Vec3 at = groundSpot(l, c, base + 0.8 + (i - (grounds - 1) / 2.0) * 0.5, 40, k.width + 1);
            if (at == null) continue;
            int n = Math.max(2, Math.min(k.seats, (int) Math.ceil(groundBudget / (double) grounds)));
            govVehicle(r, k, at, yawTo(at, c), n, false);
        }
    }

    /** A Gov vehicle with soldiers aboard (the first one drives); the commander, if any, rides third (he jumps out with the squad). */
    public static VehicleEntity govVehicle(FactionRaids.Raid r, VehicleKind kind, Vec3 at, float yaw, int riders, boolean commander) {
        ServerLevel l = r.level;
        VehicleEntity v = com.xenowarriors.vehicle.Vehicles.TYPES.get(kind).get().create(l);
        if (v == null) return null;
        v.moveTo(at.x, at.y, at.z, yaw, 0F);
        v.setYHeadRot(yaw);
        v.yBodyRot = yaw;
        v.setVariant(kind.base() == VehicleKind.GOV_TRUCK ? 0 : 2);          // (Gov trucks, black cars and helicopters)
        tag(r, v, true);
        if (!l.addFreshEntity(v)) { untag(r, v); return null; }
        int n = Math.min(riders, kind.seats);
        for (int i = 0; i < n; i++) {
            GovAgentEntity g = soldier(r, at, commander && i == 2);
            if (g != null && !g.startRiding(v, true)) { g.moveTo(at.x, at.y, at.z); }
        }
        return v;
    }

    static GovAgentEntity soldier(FactionRaids.Raid r, Vec3 at, boolean commander) {
        ServerLevel l = r.level;
        GovAgentEntity g = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(l);
        if (g == null) return null;
        BlockPos b = BlockPos.containing(at);
        g.moveTo(at.x, at.y, at.z, l.random.nextFloat() * 360F, 0F);
        g.setup(true, l.random.nextInt(2), b);
        g.finalizeSpawn(l, l.getCurrentDifficultyAt(b), MobSpawnType.EVENT, null, null);
        if (commander) {
            g.makeCommander(90 + 30 * r.severity, 9 + 2 * r.severity);
            g.setCustomName(Component.literal("Government Commander").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            g.setCustomNameVisible(true);
        }                                                               // (Karma Scanners: every guard rolls its own - see gov.GovScanning)
        g.clearRestriction();
        reach(g);
        g.setDropChance(EquipmentSlot.MAINHAND, 0F);
        tag(r, g, false);
        if (!l.addFreshEntity(g)) { untag(r, g); return null; }
        ServerPlayer t = r.targetPlayer();
        if (t != null) g.setTarget(t);
        return g;
    }

    // ------------------------------------------------------------------ Sky Rebellion

    private static final int[] LEADERS = {1, 2, 3};      // Logan, Kill, Keita (SrMemberEntity.NAMED)

    static void sr(FactionRaids.Raid r, ServerPlayer t, int extra) {
        ServerLevel l = r.level;
        int n = Math.min(24, 4 + 2 * r.wave + 2 * r.severity + 3 * extra);
        boolean last = r.wave == r.waves;
        double base = l.random.nextDouble() * Math.PI * 2;
        Vec3 c = t.position();
        for (int i = 0; i < n + (last ? 1 : 0); i++) {
            Vec3 at = airSpot(l, c, base + (l.random.nextDouble() - 0.5) * 2.2, 16 + l.random.nextInt(11), 18 + l.random.nextInt(7));
            if (at == null) continue;
            rebel(r, at, last && i == 0 ? LEADERS[l.random.nextInt(LEADERS.length)] : -1);
        }
    }

    /** A rebel diving in at the target (named: an index into {@link SrMemberEntity#NAMED}, -1 for a member). */
    public static SrMemberEntity rebel(FactionRaids.Raid r, Vec3 at, int named) {
        ServerLevel l = r.level;
        SrMemberEntity m = com.xenowarriors.sr.SrEntities.SR_MEMBER.get().create(l);
        if (m == null) return null;
        BlockPos b = BlockPos.containing(at);
        m.moveTo(at.x, at.y, at.z, l.random.nextFloat() * 360F, 0F);
        m.finalizeSpawn(l, l.getCurrentDifficultyAt(b), MobSpawnType.EVENT, null, null);
        m.setup(named >= 0 ? 2 + named : l.random.nextInt(2), b);
        m.clearRestriction();
        reach(m);
        tag(r, m, false);
        if (!l.addFreshEntity(m)) { untag(r, m); return null; }
        ServerPlayer t = r.targetPlayer();
        if (t != null) {
            m.setTarget(t);
            Vec3 d = t.position().subtract(at).normalize();
            m.setDeltaMovement(d.x * 0.5, -0.3, d.z * 0.5);              // diving in (the wings come out as it falls)
        }
        return m;
    }

    // ------------------------------------------------------------------ Townsfolk

    static void town(FactionRaids.Raid r, ServerPlayer t, int extra) {
        ServerLevel l = r.level;
        int n = Math.min(24, 5 + 2 * r.wave + 2 * r.severity + 3 * extra);
        boolean last = r.wave == r.waves;
        Vec3 c = t.position();
        // they march out from the nearest town
        double base = l.random.nextDouble() * Math.PI * 2;
        Mob home = null;
        double best = 128 * 128;
        for (Mob m : l.getEntitiesOfClass(Mob.class, t.getBoundingBox().inflate(128, 48, 128),
                m -> m instanceof Villager || (m instanceof HumanNpcEntity h && !h.raider() && !FactionRaids.isRaider(h)))) {
            double d = m.distanceToSqr(t);
            if (d < best && d > 16 * 16) { best = d; home = m; }
        }
        if (home != null) base = Math.atan2(home.getZ() - c.z, home.getX() - c.x);
        for (int i = 0; i < n + (last ? 1 : 0); i++) {
            Vec3 at = groundSpot(l, c, base + (l.random.nextDouble() - 0.5) * 1.2, 26 + l.random.nextInt(9), 1);
            if (at == null) continue;
            militia(r, at, last && i == 0);
        }
    }

    public static HumanNpcEntity militia(FactionRaids.Raid r, Vec3 at, boolean captain) {
        ServerLevel l = r.level;
        HumanNpcEntity h = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(l);
        if (h == null) return null;
        BlockPos b = BlockPos.containing(at);
        h.moveTo(at.x, at.y, at.z, l.random.nextFloat() * 360F, 0F);
        h.finalizeSpawn(l, l.getCurrentDifficultyAt(b), MobSpawnType.EVENT, null, null);
        h.makeMilitia(captain);
        reach(h);
        tag(r, h, false);
        if (!l.addFreshEntity(h)) { untag(r, h); return null; }
        ServerPlayer t = r.targetPlayer();
        if (t != null) h.setTarget(t);
        return h;
    }
}
