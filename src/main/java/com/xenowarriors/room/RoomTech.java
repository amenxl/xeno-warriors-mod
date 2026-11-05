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

    /** Survival: opening a Room costs this much Karma, and there has to be at least one enemy around to pull in. */
    public static final float KARMA = 40F;
    /** Survival: ticks before the next Room can be opened (from when you open one). */
    public static final int COOLDOWN = 1200;
    /** Items in a Room that may go back with you: what mobs (and players) dropped when they died, and what players threw. */
    private static final String KEEP = "xw_room_keep", NEXT = "xw_room_next";

    /** Whether this player has a Room open right now. */
    public static boolean open(ServerPlayer p) { return BY_PLAYER.containsKey(p.getUUID()); }

    /** Opens a Room (or leaves the one you're in). Returns true if a new Room was opened. */
    public static boolean use(ServerPlayer p) {
        if (com.xenowarriors.journey.Journey.inRun(p)) { p.displayClientMessage(Component.literal("Not on a Journey").withStyle(ChatFormatting.GRAY), true); return false; }
        Room existing = BY_PLAYER.get(p.getUUID());
        if (existing != null) { close(p.server, existing, "You left the Room."); return false; }
        if (is(p.level())) return false;
        if (com.xenowarriors.tower.Tower.is(p.level()) || com.xenowarriors.mikiko.HekubladeRealm.inRealm(p)) {
            p.displayClientMessage(Component.literal("The Room can't copy this place").withStyle(ChatFormatting.GRAY), true); return false;
        }
        boolean survival = !p.isCreative() && !p.isSpectator();
        if (survival) {
            AABB around = new AABB(p.blockPosition()).inflate(R, 8, R);
            boolean any = !p.serverLevel().getEntitiesOfClass(Mob.class, around, m -> m instanceof Enemy && m.isAlive() && !m.isPassenger()
                    && m.canChangeDimensions() && !m.getTags().contains("xw_blade_anchor")).isEmpty();
            if (!any) { p.displayClientMessage(Component.literal("No enemies nearby to pull into a Room").withStyle(ChatFormatting.GRAY), true); return false; }
            long wait = p.getPersistentData().getLong(NEXT) - p.serverLevel().getGameTime();
            if (wait > 0 && wait <= COOLDOWN) {
                p.displayClientMessage(Component.literal("Room Technology is recharging (" + (wait + 19) / 20 + "s)").withStyle(ChatFormatting.GRAY), true); return false;
            }
            if (!com.xenowarriors.karma.Karma.trySpend(p, KARMA)) return false;
            p.getPersistentData().putLong(NEXT, p.serverLevel().getGameTime() + COOLDOWN);
        }
        ServerLevel room = p.server.getLevel(LEVEL);
        if (room == null) { p.sendSystemMessage(Component.literal("The Room dimension isn't loaded.").withStyle(ChatFormatting.RED)); return false; }
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
        setBack(p, r);
        // pull in the enemies around you
        List<Mob> mobs = src.getEntitiesOfClass(Mob.class, new AABB(r.origin).inflate(R, 8, R), m -> m instanceof Enemy && m.isAlive() && !m.isPassenger() && !m.getTags().contains("xw_blade_anchor"));
        for (Mob m : mobs) {
            Vec3 to = toRoom(r, m.position());
            Entity moved = move(m, room, to, m.getYRot());
            if (moved != null) r.enemies.add(moved.getUUID());
        }
        src.playSound(null, r.origin, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1F, 1.6F);
        move(p, room, toRoom(r, p.position()), p.getYRot());
        p.displayClientMessage(Component.literal("ROOM • " + r.enemies.size() + " enem" + (r.enemies.size() == 1 ? "y" : "ies") + " pulled in").withStyle(ChatFormatting.AQUA), true);
        return true;
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

    /** Where to send you back, kept on you too (so a server stop or crash while you're in a Room still gets you home). */
    private static final String BACK = "xw_room_back";

    private static void setBack(ServerPlayer p, Room r) {
        if (r == null) { p.getPersistentData().remove(BACK); return; }
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putString("dim", r.from.location().toString());
        t.putDouble("x", r.back.x); t.putDouble("y", r.back.y); t.putDouble("z", r.back.z);
        t.putFloat("yRot", r.yRot); t.putFloat("xRot", r.xRot);
        p.getPersistentData().put(BACK, t);
    }

    private static void close(MinecraftServer server, Room r, String why) {
        ROOMS.remove(r.slot);
        BY_PLAYER.remove(r.player);
        ServerLevel room = server.getLevel(LEVEL), back = server.getLevel(r.from);
        if (room == null || back == null) return;
        AABB area = new AABB(r.center).inflate(R + 2, UP + DOWN + 4, R + 2);
        // survivors, drops and XP go back with you
        for (Entity e : room.getEntitiesOfClass(Entity.class, area, e -> !(e instanceof ServerPlayer)))
            if ((e instanceof ItemEntity && e.getPersistentData().getBoolean(KEEP)) || e instanceof ExperienceOrb || (e instanceof LivingEntity le && le.isAlive()))
                move(e, back, fromRoom(r, e.position()), e.getYRot());
            else e.discard();
        ServerPlayer p = server.getPlayerList().getPlayer(r.player);
        if (p != null) setBack(p, null);                       // (offline: kept, so their login puts them back)
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

    // ------------------------------------------------------------------ survival: the copy is only a copy
    // Everything in a Room is a copy of the real world, so nothing taken out of its blocks may come back with you
    // (mining a copied diamond block would make diamonds from nothing): broken blocks drop nothing, explosions just
    // clear blocks, and only what the fighters dropped (and items players threw) goes back when the Room closes.

    @SubscribeEvent
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (is(event.getEntity().level())) for (ItemEntity it : event.getDrops()) it.getPersistentData().putBoolean(KEEP, true);
    }

    @SubscribeEvent
    public static void onToss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (is(event.getEntity().level())) event.getEntity().getPersistentData().putBoolean(KEEP, true);
    }

    @SubscribeEvent
    public static void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !is(event.getLevel()) || event.loadedFromDisk()) return;
        if (event.getEntity() instanceof ItemEntity it && !it.getPersistentData().getBoolean(KEEP)) event.setCanceled(true);   // (dropped by a copied block)
    }

    @SubscribeEvent
    public static void onBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level l && is(l)) event.setExpToDrop(0);
    }

    @SubscribeEvent
    public static void onExplode(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (!is(event.getLevel())) return;
        for (BlockPos b : event.getAffectedBlocks()) if (!event.getLevel().getBlockState(b).is(Blocks.BARRIER)) event.getLevel().setBlock(b, Blocks.AIR.defaultBlockState(), 3);
        event.getAffectedBlocks().clear();
    }

    /** Logging out inside your Room closes it first: you (and what's left in it) go back where you opened it, and that's what's saved. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && BY_PLAYER.get(p.getUUID()) instanceof Room r) close(p.server, r, "The Room closed.");
    }

    /** Nothing carries over into the next world (singleplayer: same JVM, another save). */
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) { ROOMS.clear(); BY_PLAYER.clear(); }

    /** Logged in inside a Room that's gone (e.g. after a crash): back where you opened it (or to spawn, if that's unknown). */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && is(p.level()) && !BY_PLAYER.containsKey(p.getUUID())) {
            net.minecraft.nbt.CompoundTag t = p.getPersistentData().getCompound(BACK);
            p.getPersistentData().remove(BACK);
            ServerLevel to = t.contains("dim") ? p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(t.getString("dim")))) : null;
            if (to != null && !is(to)) {
                p.teleportTo(to, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yRot"), t.getFloat("xRot"));
                p.displayClientMessage(Component.literal("The Room closed while you were away - you're back where you opened it.").withStyle(ChatFormatting.AQUA), false);
                return;
            }
            ServerLevel ow = p.server.overworld();
            BlockPos s = p.getRespawnPosition() != null && p.getRespawnDimension() == Level.OVERWORLD ? p.getRespawnPosition() : ow.getSharedSpawnPos();
            p.teleportTo(ow, s.getX() + .5, s.getY() + 1, s.getZ() + .5, p.getYRot(), 0);
        }
    }
}
