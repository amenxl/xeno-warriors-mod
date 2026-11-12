package com.xenowarriors.gov;

import com.mojang.serialization.Codec;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.sky.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Government Prototype Facilities: big underground Government complexes, rare, in any biome of the Overworld and the
 * Recarnet world. A concrete bunker on the surface over a ladder shaft; below, a long lit corridor with a barracks of
 * soldiers, a lab full of healing pods, a storage room (supplies, a vault) and a control room (terminals), ending at a
 * blast door into a huge arena - where the Ultima Prototype is woken when someone walks in.
 *
 * <p>Laid out on a grid (one chance per 40x40-chunk region, decided by the world seed), so every chunk can build its
 * own slice of a complex that spans many chunks, and the server can tell where the arenas are without storing them.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GovDungeons extends Feature<NoneFeatureConfiguration> {
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> GOV_DUNGEON = com.xenowarriors.world.SkyIslandFeature.FEATURES.register("gov_dungeon",
            () -> new GovDungeons(NoneFeatureConfiguration.CODEC));
    private static final ResourceLocation LOOT = XenoWarriors.id("chests/gov_facility");
    public static final int REGION = 40, FLOOR_UP = 20;
    /** Footprint around the anchor (the shaft): x -23..23, z -3..106. */
    static final int MIN_X = -23, MAX_X = 23, MIN_Z = -3, MAX_Z = 106;
    /** The arena's inside (relative). */
    public static final int AX0 = -22, AX1 = 22, AZ0 = 63, AZ1 = 105, AH = 18;

    public GovDungeons(Codec<NoneFeatureConfiguration> c) { super(c); }

    public static void init() {}

    // ------------------------------------------------------------------ where they are

    public static boolean allowed(Level l) {
        return l.dimension() == Level.OVERWORLD || com.xenowarriors.recarnet.Recarnet.is(l);
    }

    /** The anchor (shaft) of the complex in this region, or null if the region has none. */
    public static BlockPos anchor(Level l, long seed, int rx, int rz) {
        Random r = new Random(seed ^ (rx * 341873128712L + rz * 132897987541L) ^ 0x5EEDB10CL);
        if (r.nextFloat() >= 0.55F) return null;
        int cx = rx * REGION + 4 + r.nextInt(REGION - 8), cz = rz * REGION + 4 + r.nextInt(REGION - 14);
        int x = cx * 16 + 8, z = cz * 16 + 8;
        if (com.xenowarriors.recarnet.Recarnet.is(l)) {                     // (not under Aurora City / the Royal Capital or the village)
            if (Math.sqrt((double) x * x + (double) z * z) < 700) return null;
            double vx = x - com.xenowarriors.recarnet.Recarnet.VILLAGE_X, vz = z - com.xenowarriors.recarnet.Recarnet.VILLAGE_Z;
            if (Math.sqrt(vx * vx + vz * vz) < 320) return null;
        }
        return new BlockPos(x, l.getMinBuildHeight() + FLOOR_UP, z);
    }

    /** Anchors whose complex could reach this block column. */
    public static List<BlockPos> anchorsNear(Level l, long seed, int x, int z) {
        List<BlockPos> out = new ArrayList<>();
        int rx = Math.floorDiv(x >> 4, REGION), rz = Math.floorDiv(z >> 4, REGION);
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
            BlockPos a = anchor(l, seed, rx + i, rz + j);
            if (a != null && x >= a.getX() + MIN_X - 1 && x <= a.getX() + MAX_X + 1 && z >= a.getZ() + MIN_Z - 1 && z <= a.getZ() + MAX_Z + 1) out.add(a);
        }
        return out;
    }

    /** The arena's inside, in the world. */
    public static AABB arena(BlockPos a) {
        return new AABB(a.getX() + AX0, a.getY(), a.getZ() + AZ0, a.getX() + AX1 + 1, a.getY() + AH, a.getZ() + AZ1 + 1);
    }

    // ------------------------------------------------------------------ building (one chunk's slice at a time)

    private int[] box;          // the chunk being built: minX, minZ, maxX, maxZ
    private WorldGenLevel level;

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel l = ctx.level();
        if (!allowed(l.getLevel())) return false;
        int cx = ctx.origin().getX() >> 4, cz = ctx.origin().getZ() >> 4;
        int x0 = cx << 4, z0 = cz << 4;
        boolean any = false;
        for (BlockPos a : anchorsNear(l.getLevel(), l.getSeed(), x0 + 8, z0 + 8)) {
            if (x0 + 15 < a.getX() + MIN_X || x0 > a.getX() + MAX_X || z0 + 15 < a.getZ() + MIN_Z || z0 > a.getZ() + MAX_Z) continue;
            synchronized (this) {
                box = new int[]{x0, z0, x0 + 15, z0 + 15};
                level = l;
                try { build(a, RandomSource.create(l.getSeed() ^ a.asLong())); } finally { box = null; level = null; }
            }
            any = true;
        }
        return any;
    }

    private boolean in(int x, int z) { return x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3]; }

    private void set(BlockPos a, int dx, int y, int dz, BlockState s) {
        int x = a.getX() + dx, z = a.getZ() + dz;
        if (in(x, z)) level.setBlock(new BlockPos(x, a.getY() + y, z), s, 2);
    }

    /** Fills a box (relative to the anchor; y relative to the floor), only the part in this chunk. */
    private void fill(BlockPos a, int dx0, int y0, int dz0, int dx1, int y1, int dz1, BlockState s) {
        int xa = Math.max(a.getX() + dx0, box[0]), xb = Math.min(a.getX() + dx1, box[2]);
        int za = Math.max(a.getZ() + dz0, box[1]), zb = Math.min(a.getZ() + dz1, box[3]);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) for (int y = y0; y <= y1; y++) level.setBlock(m.set(x, a.getY() + y, z), s, 2);
    }

    /** A room: a concrete shell with a floor, lit ceiling and air inside. */
    private void room(BlockPos a, int x0, int z0, int x1, int z1, int h, BlockState wall, BlockState floor) {
        fill(a, x0 - 1, -2, z0 - 1, x1 + 1, h, z1 + 1, wall);
        fill(a, x0, -1, z0, x1, -1, z1, floor);
        fill(a, x0, 0, z0, x1, h - 1, z1, AIR);
        for (int x = x0 + 2; x <= x1 - 2; x += 4) for (int z = z0 + 2; z <= z1 - 2; z += 4) set(a, x, h, z, LAMP);
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState(), WALL = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
            TRIM = Blocks.GRAY_CONCRETE.defaultBlockState(), DARK = Blocks.BLACK_CONCRETE.defaultBlockState(), FLOOR = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
            LAMP = Blocks.SEA_LANTERN.defaultBlockState(), GLASS = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), CYAN = Blocks.CYAN_CONCRETE.defaultBlockState(),
            WHITE = Blocks.SMOOTH_QUARTZ.defaultBlockState(), IRON = Blocks.IRON_BLOCK.defaultBlockState(), RED = Blocks.RED_CONCRETE.defaultBlockState();

    private void build(BlockPos a, RandomSource r) {
        // ---- the shaft and the bunker on top (only the anchor's chunk builds these, so its surface height is its own)
        if (in(a.getX(), a.getZ())) {
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, a.getX(), a.getZ()) - a.getY();
            int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, a.getX(), a.getZ()) - a.getY();
            fill(a, -3, -2, -3, 3, top + 4, 3, WALL);
            fill(a, -2, 0, -2, 2, top + 3, 2, AIR);
            for (int y = 0; y <= top + 2; y++) for (int x = -1; x <= 1; x++)
                set(a, x, y, -2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            for (int y = 4; y <= top; y += 6) { set(a, 2, y, 0, LAMP); set(a, -2, y, 0, LAMP); }
            // the bunker: grey with a dark band, a doorway south, a red light on the roof
            int b = Math.max(top, ground);
            fill(a, -3, b, -3, 3, b + 4, 3, WALL);
            fill(a, -2, b, -2, 2, b + 3, 2, AIR);
            fill(a, -3, b + 2, -3, 3, b + 2, 3, DARK);
            fill(a, -2, b + 2, -2, 2, b + 2, 2, AIR);
            fill(a, -1, b, 3, 1, b + 2, 3, AIR);
            fill(a, -2, b - 1, -2, 2, b - 1, -1, FLOOR);
            fill(a, -1, b - 1, 0, 1, b - 1, 2, FLOOR);
            fill(a, -1, b - 1, -2, 1, b - 1, -2, AIR);                       // (the ladder goes on up through the floor)
            set(a, 0, b + 5, 0, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
            set(a, 0, b + 4, 0, Blocks.REDSTONE_BLOCK.defaultBlockState());
        }
        // ---- the corridor, the four rooms and the arena: shells first, then the insides
        room(a, -3, 3, 3, 60, 6, WALL, FLOOR);
        room(a, 5, 8, 21, 26, 6, WALL, FLOOR);                              // barracks
        room(a, -21, 8, -5, 26, 6, WALL, Blocks.SMOOTH_QUARTZ.defaultBlockState());   // healing-pod lab
        room(a, 5, 32, 21, 48, 6, WALL, FLOOR);                             // storage
        room(a, -21, 32, -5, 48, 6, WALL, FLOOR);                           // control room
        room(a, AX0, AZ0, AX1, AZ1, AH, TRIM, FLOOR);                       // the arena
        // re-open the corridor where the rooms' shells overlapped it, and the doorways
        fill(a, -3, 0, 3, 3, 5, 60, AIR);
        for (int z : new int[]{16, 40}) { fill(a, 4, 0, z, 4, 2, z + 2, AIR); fill(a, -4, 0, z, -4, 2, z + 2, AIR); }
        fill(a, -3, 0, -2, 3, 3, 2, AIR);                                    // (shaft bottom to corridor)
        fill(a, -2, 0, 61, 2, 4, 62, AIR);                                   // the blast door's opening
        for (int x = -3; x <= 3; x++) { set(a, x, 5, 61, RED); set(a, x, 5, 62, RED); }
        // corridor dressing: light strips in the floor, the Gov blue band on the walls
        for (int z = 4; z <= 60; z += 3) { set(a, 0, -1, z, LAMP); }
        for (int z = 3; z <= 60; z++) { set(a, -4, 2, z, CYAN); set(a, 4, 2, z, CYAN); }
        for (int z : new int[]{16, 17, 18, 40, 41, 42}) { set(a, -4, 2, z, AIR); set(a, 4, 2, z, AIR); }
        // ---- the lab: two rows of healing pods with their consoles
        BlockState pod = ModBlocks.HEALING_POD.get().defaultBlockState();
        for (int z = 10; z <= 24; z += 4) for (int x : new int[]{-19, -7}) {
            Direction f = x < -12 ? Direction.EAST : Direction.WEST;
            set(a, x, 0, z, pod.setValue(HealingPodBlock.FACING, f).setValue(HealingPodBlock.HALF, DoubleBlockHalf.LOWER));
            set(a, x, 1, z, pod.setValue(HealingPodBlock.FACING, f).setValue(HealingPodBlock.HALF, DoubleBlockHalf.UPPER));
            set(a, x + (x < -12 ? 1 : -1), 0, z + 1, IRON);
            set(a, x + (x < -12 ? 1 : -1), 1, z + 1, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
        }
        for (int z = 12; z <= 22; z++) set(a, -13, -1, z, LAMP);
        // ---- the barracks: bunks and lockers
        for (int z = 10; z <= 24; z += 3) for (int x : new int[]{7, 19}) {
            set(a, x, 0, z, Blocks.GRAY_WOOL.defaultBlockState()); set(a, x, 2, z, Blocks.GRAY_WOOL.defaultBlockState());
            set(a, x, 1, z, AIR); set(a, x + (x == 7 ? -1 : 1), 0, z, IRON); set(a, x + (x == 7 ? -1 : 1), 1, z, IRON); set(a, x + (x == 7 ? -1 : 1), 2, z, IRON);
        }
        // ---- storage: supply chests, crates, the vault
        for (int z = 34; z <= 46; z += 3) { chest(a, r, 7, 0, z, Direction.EAST); chest(a, r, 19, 0, z, Direction.WEST); }
        for (int x = 10; x <= 16; x += 2) { set(a, x, 0, 40, Blocks.BARREL.defaultBlockState()); set(a, x, 1, 40, Blocks.BARREL.defaultBlockState()); }
        set(a, 13, 0, 47, GovItems.VAULT.get().defaultBlockState().setValue(GovVaultBlock.FACING, Direction.NORTH));
        set(a, 13, 1, 47, IRON);
        // ---- control room: terminals along the walls, screens above
        for (int z = 34; z <= 46; z += 4) {
            set(a, -20, 0, z, GovItems.TERMINAL.get().defaultBlockState().setValue(GovTerminalBlock.FACING, Direction.EAST));
            set(a, -21, 2, z, GLASS); set(a, -21, 3, z, GLASS);
        }
        for (int x = -16; x <= -10; x++) set(a, x, 0, 40, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        chest(a, r, -13, 1, 40, Direction.SOUTH);
        // ---- the arena: pillars, a ring of light strips, glass observation windows, the prototype's pod at the far end
        for (int x : new int[]{-16, -8, 8, 16}) for (int z : new int[]{72, 84, 96}) fill(a, x, 0, z, x, AH - 1, z, WALL);
        for (int x = AX0; x <= AX1; x++) { set(a, x, -1, AZ0 + 2, LAMP); set(a, x, -1, AZ1 - 2, LAMP); }
        for (int z = AZ0; z <= AZ1; z++) { set(a, AX0 + 2, -1, z, LAMP); set(a, AX1 - 2, -1, z, LAMP); }
        for (int z = AZ0 + 4; z <= AZ1 - 4; z += 2) { set(a, AX0 - 1, 10, z, GLASS); set(a, AX1 + 1, 10, z, GLASS); set(a, AX0 - 1, 11, z, GLASS); set(a, AX1 + 1, 11, z, GLASS); }
        fill(a, -4, 0, 100, 4, 0, 104, WHITE);                              // the platform
        fill(a, -1, 1, 103, 1, 4, 104, GLASS);                             // the empty prototype pod
        fill(a, -2, 5, 103, 2, 5, 104, CYAN);
        for (int x = -2; x <= 2; x++) set(a, x, 1, 104, IRON);
        // ---- people: soldiers in the barracks and the corridor, staff in the lab and control room
        spawnAgents(a, r, 13, 17, 5, true);
        spawnAgents(a, r, 0, 30, 3, true);
        spawnAgents(a, r, 0, 50, 3, true);
        spawnAgents(a, r, -13, 17, 3, false);
        spawnAgents(a, r, -13, 44, 2, false);
        spawnAgents(a, r, 13, 36, 2, true);
    }

    private void chest(BlockPos a, RandomSource r, int dx, int y, int dz, Direction f) {
        int x = a.getX() + dx, z = a.getZ() + dz;
        if (!in(x, z)) return;
        BlockPos p = new BlockPos(x, a.getY() + y, z);
        level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, f), 2);
        if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity be) be.setLootTable(LOOT, r.nextLong());
    }

    private void spawnAgents(BlockPos a, RandomSource r, int dx, int dz, int n, boolean guard) {
        if (!in(a.getX() + dx, a.getZ() + dz)) return;                      // (spawned once: with the chunk the spot is in)
        for (int i = 0; i < n; i++) {
            GovAgentEntity g = GovEntities.GOV_AGENT.get().create(level.getLevel());
            if (g == null) continue;
            int x = Math.max(box[0], Math.min(box[2], a.getX() + dx + r.nextInt(5) - 2)), z = Math.max(box[1], Math.min(box[3], a.getZ() + dz + r.nextInt(5) - 2));
            BlockPos at = new BlockPos(x, a.getY(), z);
            g.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360F, 0);
            g.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null, null);
            g.setup(guard, r.nextInt(2), at);
            g.setPersistenceRequired();
            level.addFreshEntityWithPassengers(g);
        }
    }

    // ------------------------------------------------------------------ the boss: woken when someone walks into the arena

    /** Arenas whose prototype has been beaten (per world), and the live ones: anchor -> boss. */
    public static final class Beaten extends SavedData {
        final Set<Long> done = new HashSet<>();
        static Beaten get(ServerLevel l) { return l.getDataStorage().computeIfAbsent(Beaten::load, Beaten::new, "xenowarriors_proto_arenas"); }
        static Beaten load(CompoundTag t) { Beaten b = new Beaten(); for (Tag x : t.getList("done", Tag.TAG_LONG)) b.done.add(((LongTag) x).getAsLong()); return b; }
        @Override public CompoundTag save(CompoundTag t) {
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (long v : done) l.add(LongTag.valueOf(v));
            t.put("done", l);
            return t;
        }
    }

    private static final Map<Long, UUID> LIVE = new HashMap<>();
    /** (For the tests) prototypes woken. */
    public static int woken;

    public static boolean beaten(ServerLevel l, BlockPos a) { return Beaten.get(l).done.contains(a.asLong()); }

    /** The prototype of this arena was destroyed. */
    public static void defeated(ServerLevel l, BlockPos a) {
        Beaten b = Beaten.get(l);
        if (b.done.add(a.asLong())) b.setDirty();
        LIVE.remove(a.asLong());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 7) return;
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (p.isSpectator() || !allowed(p.level())) continue;
            ServerLevel l = p.serverLevel();
            if (p.getY() > l.getMinBuildHeight() + FLOOR_UP + AH + 2) continue;   // (cheap: only down at the complexes' depth)
            for (BlockPos a : anchorsNear(l, l.getSeed(), p.getBlockX(), p.getBlockZ())) {
                if (!arena(a).contains(p.position())) continue;
                UUID id = LIVE.get(a.asLong());
                if (id != null && l.getEntity(id) instanceof UltimaPrototypeEntity u && u.isAlive()) continue;
                if (id != null) {                                              // (unloaded with the chunk, or gone): look for it
                    var found = l.getEntitiesOfClass(UltimaPrototypeEntity.class, arena(a).inflate(8), UltimaPrototypeEntity::isAlive);
                    if (!found.isEmpty()) { LIVE.put(a.asLong(), found.get(0).getUUID()); continue; }
                }
                if (beaten(l, a)) continue;
                wake(l, a, p);
            }
        }
    }

    /** The prototype steps out of its pod at the far end of the arena. */
    public static UltimaPrototypeEntity wake(ServerLevel l, BlockPos a, ServerPlayer who) {
        UltimaPrototypeEntity u = GovEntities.ULTIMA_PROTOTYPE.get().create(l);
        if (u == null) return null;
        u.moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 101.5, 180F, 0);
        u.setHome(a);
        List<ServerPlayer> ps = new ArrayList<>(l.getEntitiesOfClass(ServerPlayer.class, arena(a).inflate(24), pl -> pl.isAlive() && !pl.isSpectator()));
        if (!ps.contains(who)) ps.add(who);
        u.scaleTo(ps);
        u.setTarget(who);
        l.addFreshEntity(u);
        LIVE.put(a.asLong(), u.getUUID());
        woken++;
        l.playSound(null, u.blockPosition(), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.HOSTILE, 2F, 0.5F);
        for (ServerPlayer p : ps) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 60, 15));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal("An artificial Ultima - Government property").withStyle(ChatFormatting.GRAY)));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("ULTIMA PROTOTYPE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            p.sendSystemMessage(Component.literal("[Facility] ").withStyle(ChatFormatting.RED).append(Component.literal("Intruders in the test arena. Prototype U-0 activated.").withStyle(ChatFormatting.GRAY)));
        }
        return u;
    }
}
