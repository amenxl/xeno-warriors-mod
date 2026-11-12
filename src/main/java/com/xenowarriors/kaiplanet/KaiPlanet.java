package com.xenowarriors.kaiplanet;

import com.xenowarriors.XenoWarriors;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.BitSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * King Kai's planet keeps itself in one piece (with DragonMineZ): its builder, Brik (KaiBuilderEntity), lives up there
 * and puts back anything that gets broken - dug, blasted, burnt, whoever did it - exactly as DragonMineZ made it
 * (KaiBlueprint). Only gaps are filled: a different block someone put in a hole is theirs and stays. Damage is
 * noticed straight away (block events) and by a slow sweep while anyone's around. There's always one builder: he
 * turns up near King Kai's house when someone's there and he isn't. Never touches an unloaded chunk.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class KaiPlanet {
    private KaiPlanet() {}

    public static final ResourceKey<Level> OTHERWORLD = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("dragonminez", "otherworld"));
    private static final int SWEEP_PER_TICK = 600, SUSPECTS_PER_TICK = 512, SPAWN_AFTER = 10 * 20;

    private static CompletableFuture<KaiBlueprint> loading;
    private static KaiBlueprint plan;
    static BitSet pending = new BitSet();
    private static BitSet queued = new BitSet();
    private static final IntArrayFIFOQueue SUSPECTS = new IntArrayFIFOQueue();
    private static int cursor, missingFor;
    private static UUID builder;
    /** (Tests) blocks put back, builders made, times he teleported home. */
    public static int restored, spawned, teleports;

    public static KaiBlueprint plan() { return plan; }

    static boolean otherworld(Level l) { return l.dimension() == OTHERWORLD; }

    // ------------------------------------------------------------------ noticing damage

    private static void mark(Level level, BlockPos p) {
        if (plan == null || !otherworld(level) || level.isClientSide) return;
        int i = plan.index(p);
        if (i >= 0 && !queued.get(i)) { queued.set(i); SUSPECTS.enqueue(i); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onNeighbor(BlockEvent.NeighborNotifyEvent e) { if (e.getLevel() instanceof Level l) mark(l, e.getPos()); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent e) { if (!e.isCanceled() && e.getLevel() instanceof Level l) mark(l, e.getPos()); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplode(ExplosionEvent.Detonate e) { for (BlockPos p : e.getAffectedBlocks()) mark(e.getLevel(), p); }

    /** The block there now, only if its chunk is loaded already (null: not loaded - never loads one). */
    static BlockState now(ServerLevel l, BlockPos p) {
        LevelChunk ch = l.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
        return ch == null ? null : ch.getBlockState(p);
    }

    /** Does this spot need its block back? Only gaps (air, fire, a liquid that flowed in, grass that grew in). */
    static boolean needs(ServerLevel l, int i) {
        BlockState want = plan.state(i), cur = now(l, plan.pos(i));
        if (cur == null || cur.is(want.getBlock())) return false;
        if (cur.isAir() || cur.is(BlockTags.FIRE)) return true;
        if (cur.getBlock() instanceof LiquidBlock) return !(want.getBlock() instanceof LiquidBlock);
        return cur.canBeReplaced() && cur.getFluidState().isEmpty();
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel l) || !otherworld(l)) return;
        if (plan == null) {
            if (loading == null) {
                var blocks = l.holderLookup(Registries.BLOCK);
                loading = CompletableFuture.supplyAsync(() -> KaiBlueprint.load(blocks));
            }
            if (!loading.isDone()) return;
            plan = loading.getNow(null);
            if (plan == null) return;                                            // (no map: nothing to look after)
            XenoWarriors.LOGGER.info("King Kai's planet: {} blocks to look after ({} kinds)", plan.size(), plan.palette.length);
        }
        if (l.players().isEmpty()) return;
        int n = 0;
        while (!SUSPECTS.isEmpty() && n++ < SUSPECTS_PER_TICK) {
            int i = SUSPECTS.dequeueInt();
            queued.clear(i);
            if (needs(l, i)) pending.set(i); else pending.clear(i);
        }
        boolean near = false;
        for (var p : l.players()) if (KaiBlueprint.near(p.getX(), p.getY(), p.getZ())) { near = true; break; }
        if (!near) return;
        for (int k = 0; k < SWEEP_PER_TICK; k++) {                                // the slow sweep (the whole planet every ~25 s)
            int i = cursor++;
            if (cursor >= plan.size()) cursor = 0;
            if (needs(l, i)) pending.set(i); else pending.clear(i);
        }
        keepBuilder(l);
    }

    /** One builder, always: near King Kai's house when someone's on the planet and he's nowhere about. */
    private static void keepBuilder(ServerLevel l) {
        if (l.getGameTime() % 20 != 0) return;
        BlockPos home = home(l);
        if (home == null || !l.isPositionEntityTicking(home)) { missingFor = 0; return; }
        KaiBuilderEntity found = builder != null && l.getEntity(builder) instanceof KaiBuilderEntity b && b.isAlive() ? b : null;
        if (found == null) {
            var all = l.getEntitiesOfClass(KaiBuilderEntity.class, new AABB(home).inflate(140), Entity::isAlive);
            if (!all.isEmpty()) { found = all.get(0); builder = found.getUUID(); }
            for (int i = 1; i < all.size(); i++) all.get(i).discard();          // (never two)
        }
        if (found != null) {
            missingFor = 0;
            for (KaiBuilderEntity o : l.getEntitiesOfClass(KaiBuilderEntity.class, new AABB(home).inflate(140), x -> x.isAlive() && !x.getUUID().equals(builder))) o.discard();   // (never two: one turned up from far off)
            return;
        }
        missingFor += 20;
        if (missingFor < SPAWN_AFTER) return;                                    // (entities load a little after their chunks)
        KaiBuilderEntity b = KaiPlanetEntities.BUILDER.get().create(l);
        if (b == null) return;
        b.moveTo(home.getX() + .5, home.getY(), home.getZ() + .5, l.random.nextFloat() * 360F, 0);
        b.setPersistenceRequired();
        l.addFreshEntity(b);
        builder = b.getUUID();
        missingFor = 0;
        spawned++;
        l.sendParticles(ParticleTypes.END_ROD, b.getX(), b.getY() + 1, b.getZ(), 20, 0.4, 0.8, 0.4, 0.05);
    }

    /** Where he lives: on the planet, a few steps from King Kai (null: that part isn't loaded). */
    static BlockPos home(ServerLevel l) {
        BlockPos at = KaiBlueprint.KING_KAI.offset(6, 0, 6);
        if (l.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null) return null;
        return l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        pending = new BitSet(); queued = new BitSet(); SUSPECTS.clear(); cursor = 0; builder = null; missingFor = 0;
    }

    // ------------------------------------------------------------------ for the builder

    /** How much is waiting to be put back. */
    public static int pendingCount() { return pending.cardinality(); }

    /** Something to hold on to: a block of it already there (it isn't floating). */
    static boolean attached(ServerLevel l, BlockPos p) {
        for (Direction d : Direction.values()) {
            BlockPos q = p.relative(d);
            int j = plan.index(q);
            if (j >= 0 && pending.get(j)) continue;
            BlockState s = now(l, q);
            if (s != null && !s.isAir() && s.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    /** The open spot closest to this point that can go in now (-1: none). Scans at most `limit` waiting spots. */
    static int pick(ServerLevel l, double x, double y, double z, int limit, java.util.function.IntPredicate skip) {
        int best = -1, scanned = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = pending.nextSetBit(0); i >= 0 && scanned < limit; i = pending.nextSetBit(i + 1), scanned++) {
            if (!needs(l, i)) { pending.clear(i); continue; }
            if (skip.test(i)) continue;
            BlockPos p = plan.pos(i);
            BlockState s = plan.state(i);
            if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) continue;   // (goes in with its lower half)
            if (!attached(l, p)) continue;
            double d = p.distToCenterSqr(x, y, z);
            if (d < bestD) { bestD = d; best = i; }
        }
        if (best < 0 && scanned > 0) {                                           // (all floating: start anywhere - he holds it up)
            for (int i = pending.nextSetBit(0); i >= 0; i = pending.nextSetBit(i + 1)) if (needs(l, i) && !skip.test(i)) return i;
        }
        return best;
    }

    private static BlockPos partner(BlockState s, BlockPos p) {
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))
            return s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? p.above() : p.below();
        if (s.getBlock() instanceof BedBlock) {
            Direction f = s.getValue(BedBlock.FACING);
            return s.getValue(BedBlock.PART) == BedPart.FOOT ? p.relative(f) : p.relative(f.getOpposite());
        }
        return null;
    }

    /** Nobody stands where it goes. */
    private static boolean clear(ServerLevel l, BlockPos p, BlockState s) {
        if (s.getCollisionShape(l, p).isEmpty()) return true;
        if (l.getNearestPlayer(p.getX() + .5, p.getY() + .5, p.getZ() + .5, 1.2, EntitySelector.NO_SPECTATORS) != null) return false;
        return l.getEntitiesOfClass(Entity.class, new AABB(p), e -> !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb) && !(e instanceof Projectile)
                && !(e instanceof KaiBuilderEntity) && !e.isSpectator()).isEmpty();
    }

    /** Puts spot i back (and its other half). True if it went in. */
    static boolean place(ServerLevel l, int i, Entity by) {
        if (!needs(l, i)) { pending.clear(i); return false; }
        BlockPos p = plan.pos(i);
        BlockState s = plan.state(i);
        if (!clear(l, p, s)) return false;
        BlockPos q = partner(s, p);
        int qi = q == null ? -1 : plan.index(q);
        boolean pair = qi >= 0 && plan.state(qi).is(s.getBlock()) && needs(l, qi) && clear(l, q, plan.state(qi));
        if (pair) {
            l.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            l.setBlock(q, plan.state(qi), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            data(l, qi, q);
            pending.clear(qi);
            restored++;
        } else l.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);   // (as it was: no shape updates pulling things apart)
        data(l, i, p);
        pending.clear(i);
        restored++;
        l.gameEvent(by, GameEvent.BLOCK_PLACE, p);
        var sound = s.getSoundType();
        l.playSound(null, p, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1F) / 2F * 0.7F, sound.getPitch() * 0.85F);
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), p.getX() + .5, p.getY() + .5, p.getZ() + .5, 6, 0.3, 0.3, 0.3, 0.05);
        return true;
    }

    /** A sign's words, a bed's colour come back too; a container comes back empty (no free copies of what was in it). */
    private static void data(ServerLevel l, int i, BlockPos p) {
        var tag = plan.blockEntity(i);
        if (tag == null) return;
        BlockEntity be = l.getBlockEntity(p);
        if (be == null || be instanceof BaseContainerBlockEntity) return;
        var t = tag.copy();
        t.putInt("x", p.getX()); t.putInt("y", p.getY()); t.putInt("z", p.getZ());
        be.load(t);
        be.setChanged();
        l.sendBlockUpdated(p, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ------------------------------------------------------------------ tests

    /** (Tests) every spot looked at now. */
    public static int checkAllForTest(ServerLevel l) {
        if (plan == null) return -1;
        for (int i = 0; i < plan.size(); i++) if (needs(l, i)) pending.set(i); else pending.clear(i);
        return pendingCount();
    }

    public static boolean readyForTest() { return plan != null; }

    /** Is this the planet's builder? (Another one that turns up - from a chunk far off that loaded again - goes.) */
    static boolean theOne(ServerLevel l, KaiBuilderEntity b) {
        if (builder == null || builder.equals(b.getUUID())) { builder = b.getUUID(); return true; }
        return !(l.getEntity(builder) instanceof KaiBuilderEntity other && other.isAlive());
    }

    public static KaiBuilderEntity builderForTest(ServerLevel l) { return builder != null && l.getEntity(builder) instanceof KaiBuilderEntity b ? b : null; }
}
