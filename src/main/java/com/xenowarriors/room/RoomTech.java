package com.xenowarriors.room;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Function;

/**
 * Room technology: copies the area around you into a pocket dimension and pulls you and the enemies nearby into it,
 * so the fight can't wreck the real world. The Room vanishes when you leave it or when no enemies are left in it -
 * you (and whatever dropped) go back to where you were.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RoomTech {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, XenoWarriors.id("room"));
    public static final int R = 20, DOWN = 6, UP = 16, SPACING = 512;
    private static final DustParticleOptions SHIMMER = new DustParticleOptions(new Vector3f(0.45F, 0.8F, 1F), 1.4F);

    private static final class Room {
        UUID player;
        ResourceKey<Level> from;
        BlockPos origin, center;
        Vec3 back;
        float yRot, xRot;
        final Set<UUID> enemies = new HashSet<>();
        int slot;
        long clearedAt = -1;
    }

    private static final Map<Integer, Room> ROOMS = new HashMap<>();
    private static final Map<UUID, Room> BY_PLAYER = new HashMap<>();

    private RoomTech() {}

    public static boolean is(Level l) { return l.dimension() == LEVEL; }

    public static void use(ServerPlayer p) {
        Room existing = BY_PLAYER.get(p.getUUID());
        if (existing != null) { close(p.server, existing, "You left the Room."); return; }
        if (is(p.level())) return;
        ServerLevel room = p.server.getLevel(LEVEL);
        if (room == null) { p.sendSystemMessage(Component.literal("The Room dimension isn't loaded.").withStyle(ChatFormatting.RED)); return; }
        ServerLevel src = p.serverLevel();
        int slot = 0;
        while (ROOMS.containsKey(slot)) slot++;
        Room r = new Room();
        r.player = p.getUUID(); r.from = src.dimension(); r.slot = slot;
        r.origin = p.blockPosition(); r.back = p.position(); r.yRot = p.getYRot(); r.xRot = p.getXRot();
        r.center = new BlockPos(slot * SPACING + SPACING / 2, Math.max(room.getMinBuildHeight() + DOWN + 2, Math.min(room.getMaxBuildHeight() - UP - 2, r.origin.getY())), 0);
        copy(src, room, r);
        ROOMS.put(slot, r);
        BY_PLAYER.put(p.getUUID(), r);
        // pull in the enemies around you
        List<Mob> mobs = src.getEntitiesOfClass(Mob.class, new AABB(r.origin).inflate(R, 8, R), m -> m instanceof Enemy && m.isAlive() && !m.isPassenger());
        for (Mob m : mobs) {
            Vec3 to = toRoom(r, m.position());
            Entity moved = move(m, room, to, m.getYRot());
            if (moved != null) r.enemies.add(moved.getUUID());
        }
        src.playSound(null, r.origin, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1F, 1.6F);
        move(p, room, toRoom(r, p.position()), p.getYRot());
        p.displayClientMessage(Component.literal("ROOM • " + r.enemies.size() + " enem" + (r.enemies.size() == 1 ? "y" : "ies") + " pulled in").withStyle(ChatFormatting.AQUA), true);
    }

    private static Vec3 toRoom(Room r, Vec3 pos) { return pos.subtract(Vec3.atLowerCornerOf(r.origin)).add(Vec3.atLowerCornerOf(r.center)); }
    private static Vec3 fromRoom(Room r, Vec3 pos) { return pos.subtract(Vec3.atLowerCornerOf(r.center)).add(Vec3.atLowerCornerOf(r.origin)); }

    /** The copy: every block in the area (contents of chests etc. stay behind), inside a shimmering glass shell. */
    private static void copy(ServerLevel src, ServerLevel room, Room r) {
        BlockPos.MutableBlockPos a = new BlockPos.MutableBlockPos(), b = new BlockPos.MutableBlockPos();
        BlockState shell = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        for (int dx = -R - 1; dx <= R + 1; dx++)
            for (int dz = -R - 1; dz <= R + 1; dz++)
                for (int dy = -DOWN - 1; dy <= UP + 1; dy++) {
                    b.set(r.center.getX() + dx, r.center.getY() + dy, r.center.getZ() + dz);
                    boolean edge = Math.abs(dx) == R + 1 || Math.abs(dz) == R + 1 || dy == -DOWN - 1 || dy == UP + 1;
                    BlockState s;
                    if (edge) s = dy == -DOWN - 1 ? Blocks.BARRIER.defaultBlockState() : shell;
                    else {
                        a.set(r.origin.getX() + dx, r.origin.getY() + dy, r.origin.getZ() + dz);
                        s = src.getBlockState(a);
                        if (s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.END_PORTAL) || s.is(Blocks.END_GATEWAY)) s = Blocks.AIR.defaultBlockState();
                    }
                    room.setBlock(b, s, 2 | 16);
                }
    }

    private static void clear(ServerLevel room, Room r) {
        BlockPos.MutableBlockPos b = new BlockPos.MutableBlockPos();
        for (int dx = -R - 1; dx <= R + 1; dx++)
            for (int dz = -R - 1; dz <= R + 1; dz++)
                for (int dy = -DOWN - 1; dy <= UP + 1; dy++)
                    room.setBlock(b.set(r.center.getX() + dx, r.center.getY() + dy, r.center.getZ() + dz), Blocks.AIR.defaultBlockState(), 2 | 16);
    }

    public static Entity move(Entity e, ServerLevel to, Vec3 pos, float yRot) {
        if (e.level() == to) { e.teleportTo(pos.x, pos.y, pos.z); return e; }
        if (e instanceof ServerPlayer p) { p.teleportTo(to, pos.x, pos.y, pos.z, yRot, p.getXRot()); return p; }
        return e.changeDimension(to, new ITeleporter() {
            @Override public PortalInfo getPortalInfo(Entity entity, ServerLevel dest, Function<ServerLevel, PortalInfo> def) {
                return new PortalInfo(pos, Vec3.ZERO, yRot, entity.getXRot());
            }
            @Override public boolean playTeleportSound(ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld) { return false; }
        });
    }

    private static void close(MinecraftServer server, Room r, String why) {
        ROOMS.remove(r.slot);
        BY_PLAYER.remove(r.player);
        ServerLevel room = server.getLevel(LEVEL), back = server.getLevel(r.from);
        if (room == null || back == null) return;
        AABB area = new AABB(r.center).inflate(R + 2, UP + DOWN + 4, R + 2);
        // survivors, drops and XP go back with you
        for (Entity e : room.getEntitiesOfClass(Entity.class, area, e -> !(e instanceof ServerPlayer)))
            if (e instanceof ItemEntity || e instanceof ExperienceOrb || (e instanceof LivingEntity le && le.isAlive())) move(e, back, fromRoom(r, e.position()), e.getYRot());
            else e.discard();
        ServerPlayer p = server.getPlayerList().getPlayer(r.player);
        if (p != null && is(p.level()) && p.isAlive()) {
            p.teleportTo(back, r.back.x, r.back.y, r.back.z, r.yRot, r.xRot);
            p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.AQUA), true);
            back.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1F, 1.6F);
        }
        clear(room, r);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ROOMS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        ServerLevel room = server.getLevel(LEVEL);
        if (room == null) return;
        long now = room.getGameTime();
        for (Room r : new ArrayList<>(ROOMS.values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(r.player);
            if (p == null || !is(p.level())) { close(server, r, "The Room closed."); continue; }
            if (now % 4 == 0) {                                          // the shell shimmers
                double a = (now * 0.07) % (Math.PI * 2);
                for (int i = 0; i < 4; i++) {
                    double t = a + i * Math.PI / 2;
                    room.sendParticles(SHIMMER, r.center.getX() + Math.cos(t) * (R + 0.5), r.center.getY() + 1 + (now % 40) / 4.0, r.center.getZ() + Math.sin(t) * (R + 0.5), 1, 0, 0, 0, 0);
                }
            }
            if (now % 10 != 0) continue;
            r.enemies.removeIf(id -> !(room.getEntity(id) instanceof LivingEntity le) || !le.isAlive());
            if (r.enemies.isEmpty()) {
                if (r.clearedAt < 0) { r.clearedAt = now; p.displayClientMessage(Component.literal("Room cleared! Returning...").withStyle(ChatFormatting.GREEN), true); }
                else if (now - r.clearedAt >= 60) close(server, r, "Room cleared.");
            }
        }
    }

    /** Logged in inside a Room that's gone (e.g. after a restart): back to spawn. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && is(p.level()) && !BY_PLAYER.containsKey(p.getUUID())) {
            ServerLevel ow = p.server.overworld();
            BlockPos s = p.getRespawnPosition() != null && p.getRespawnDimension() == Level.OVERWORLD ? p.getRespawnPosition() : ow.getSharedSpawnPos();
            p.teleportTo(ow, s.getX() + .5, s.getY() + 1, s.getZ() + .5, p.getYRot(), 0);
        }
    }
}
