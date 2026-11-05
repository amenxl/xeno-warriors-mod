package com.xenowarriors.npc.rebuild;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.npc.HumanNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Villages rebuild themselves. The first time a village is seen (its bell; in Recarnet, the beds of its towns; a
 * townsperson's hut) its buildings are recorded block by block (VillageSnapshot). When something breaks them -
 * explosions, mobs, fire, players digging - the spots are noticed (block events, plus a slow sweep that looks at a few
 * positions a tick for whatever slipped past) and the people living there put it back the way it was: in the daytime,
 * with no monsters about, townsfolk and villagers walk over and set the blocks back one at a time (a swing, the
 * block's sound, a puff of its particles), from the bottom up, exact block states, door and bed halves together.
 * Only gaps are filled (air, fire, flowing water or lava, grass or snow that blew in): a different block somebody put
 * there is left alone. Chests and other containers come back empty, crops come back as fresh plantings. Nothing is
 * placed into an entity or within 3 blocks of a player. Gamerule xwVillageRebuild (on by default); never in the modes'
 * arenas or the Hekublade's realm, and never in Government facilities. Bounded work per tick, and never loads a chunk.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class VillageRebuild {
    public static final GameRules.Key<GameRules.BooleanValue> RULE = GameRules.register("xwVillageRebuild", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));

    private static final int DISCOVER_EVERY = 100, ASSIGN_EVERY = 10, SWEEP_PER_TICK = 24, SUSPECTS_PER_TICK = 256;
    private static final int MAX_WORKERS = 6, PICK_SCAN = 6000;
    /** Test hook: >1 makes them place blocks that many times faster. */
    public static int speed = 1;

    private static final Map<ResourceKey<Level>, RebuildData> DATA = new HashMap<>();
    private static final Map<ResourceKey<Level>, VillageSnapshot> SCANS = new HashMap<>();
    /** Anchors that won't make a village (nothing built, a Government facility), so they aren't looked at again. */
    private static final Set<Long> REJECTED = new HashSet<>();
    private static final Map<Integer, Job> JOBS = new HashMap<>();

    private VillageRebuild() {}

    /** One worker's current spot. */
    private static final class Job {
        final Mob w; final ServerLevel l; final RebuildData.Village v;
        int idx; long since, next, checked;
        String last = "walking";
        Job(Mob w, ServerLevel l, RebuildData.Village v, int idx, long now) { this.w = w; this.l = l; this.v = v; this.idx = idx; since = now; next = now + 10; }
    }

    // ================================================================== data

    @SubscribeEvent
    public static void onLoad(LevelEvent.Load e) {
        if (e.getLevel() instanceof ServerLevel l) DATA.put(l.dimension(), l.getDataStorage().computeIfAbsent(RebuildData::load, RebuildData::new, RebuildData.NAME));
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload e) {
        if (e.getLevel() instanceof ServerLevel l) {
            DATA.remove(l.dimension());
            SCANS.remove(l.dimension());
            JOBS.values().removeIf(j -> j.l == l);
        }
    }

    /** Nothing carries over into the next world (singleplayer: same JVM, another save - its villages may sit where this one's rejected anchors were). */
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent e) { REJECTED.clear(); SCANS.clear(); JOBS.clear(); DATA.clear(); }

    public static RebuildData data(ServerLevel l) { return DATA.get(l.dimension()); }

    public static boolean enabled(ServerLevel l) {
        return l.getGameRules().getBoolean(RULE) && com.xenowarriors.modes.ModeRules.canGrief(l);
    }

    // ================================================================== noticing damage

    private static void mark(Level level, BlockPos p) {
        if (!(level instanceof ServerLevel l)) return;
        RebuildData d = DATA.get(l.dimension());
        if (d == null) return;
        List<RebuildData.Village> vs = d.inChunk(p.getX() >> 4, p.getZ() >> 4);
        if (vs == null) return;
        for (RebuildData.Village v : vs) {
            int i = v.index(p);
            if (i >= 0 && !v.queued.get(i)) { v.queued.set(i); v.suspects.enqueue(i); }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onNeighbor(BlockEvent.NeighborNotifyEvent e) {
        if (e.getLevel() instanceof Level l) mark(l, e.getPos());           // (most block changes: fire burning out, endermen, pistons...)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!e.isCanceled() && e.getLevel() instanceof Level l) mark(l, e.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplode(ExplosionEvent.Detonate e) {
        for (BlockPos p : e.getAffectedBlocks()) mark(e.getLevel(), p);
    }

    /**
     * Does this spot need its block back? Only gaps: the same block (whatever its state - an opened door, a grown crop)
     * is fine, and a different block someone put there is theirs.
     */
    static boolean needs(ServerLevel l, RebuildData.Village v, int i) {
        BlockState want = v.state(i), cur = l.getBlockState(v.pos(i));
        if (cur.is(want.getBlock())) return false;
        if (cur.isAir() || cur.is(BlockTags.FIRE)) return true;
        if (cur.getBlock() instanceof LiquidBlock) return !(want.getBlock() instanceof LiquidBlock);
        if (want.is(BlockTags.DIRT) && cur.is(BlockTags.DIRT)) return false;                     // (grass / dirt / path wear)
        if ((want.getBlock() instanceof FarmBlock || want.getBlock() instanceof DirtPathBlock) && cur.is(BlockTags.DIRT)) return true;   // trampled, dug up
        return cur.canBeReplaced() && cur.getFluidState().isEmpty();                             // grass or snow that grew into the gap
    }

    /** Looks at every position of this village now (tests, and a fresh load). Returns how many need rebuilding. */
    public static int checkAll(ServerLevel l, RebuildData.Village v) {
        for (int i = 0; i < v.size(); i++) {
            BlockPos p = v.pos(i);
            if (!l.isLoaded(p)) continue;
            if (needs(l, v, i)) v.pending.set(i); else v.pending.clear(i);
        }
        return v.pendingCount();
    }

    // ================================================================== ticking

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = e.getServer();
        long tick = server.getTickCount();
        for (ServerLevel l : server.getAllLevels()) {
            RebuildData d = DATA.get(l.dimension());
            if (d == null) continue;
            if (!enabled(l)) { release(l); continue; }
            VillageSnapshot scan = SCANS.get(l.dimension());
            if (scan != null) {
                if (scan.step()) { SCANS.remove(l.dimension()); finish(l, d, scan); }
            } else if (tick % DISCOVER_EVERY == 7) discover(l, d);
            if (d.villages.isEmpty()) continue;
            List<ServerPlayer> players = l.players();
            for (RebuildData.Village v : d.villages) {
                boolean near = false;
                for (ServerPlayer p : players) if (p.blockPosition().closerThan(v.anchor, 160)) { near = true; break; }
                if (near) v.lastSeen = l.getGameTime();
                int n = 0;
                while (!v.suspects.isEmpty() && n++ < SUSPECTS_PER_TICK) {
                    int i = v.suspects.dequeueInt();
                    v.queued.clear(i);
                    BlockPos p = v.pos(i);
                    if (!l.isLoaded(p)) continue;
                    if (needs(l, v, i)) v.pending.set(i); else v.pending.clear(i);
                }
                if (near && v.size() > 0) for (int k = 0; k < SWEEP_PER_TICK; k++) {       // the slow sweep
                    int i = v.cursor++ % v.size();
                    if (v.cursor >= v.size()) v.cursor = 0;
                    BlockPos p = v.pos(i);
                    if (!l.isLoaded(p)) continue;
                    if (needs(l, v, i)) v.pending.set(i); else v.pending.clear(i);
                }
                if (near && tick % ASSIGN_EVERY == v.id % ASSIGN_EVERY && !v.pending.isEmpty()) assign(l, v);
            }
        }
        if (!JOBS.isEmpty()) {
            for (Iterator<Job> it = JOBS.values().iterator(); it.hasNext(); ) if (!work(it.next())) it.remove();
        }
    }

    private static void release(ServerLevel l) { JOBS.values().removeIf(j -> j.l == l); }

    // ================================================================== finding villages

    private static void discover(ServerLevel l, RebuildData d) {
        for (ServerPlayer p : l.players()) {
            if (p.isSpectator()) continue;
            BlockPos at = p.blockPosition();
            VillageSnapshot s = null;
            for (BlockPos bell : l.getPoiManager().findAll(h -> h.is(PoiTypes.MEETING), q -> true, at, 64, PoiManager.Occupancy.ANY).toList()) {
                if (fresh(l, d, bell)) { s = VillageSnapshot.forBell(l, bell); break; }
            }
            boolean town = com.xenowarriors.recarnet.Recarnet.inVillage(l, at) || com.xenowarriors.recarnet.Recarnet.inAurora(l, at);
            if (s == null && town) {
                for (BlockPos bed : l.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), q -> true, at, 40, PoiManager.Occupancy.ANY).toList()) {
                    if (fresh(l, d, bed)) { s = VillageSnapshot.forTown(l, bed); break; }
                }
            }
            if (s == null) for (HumanNpcEntity h : l.getEntitiesOfClass(HumanNpcEntity.class, p.getBoundingBox().inflate(48), h -> h.home() != null)) {
                if (fresh(l, d, h.home())) { s = VillageSnapshot.forHut(l, h.home()); break; }
            }
            if (s != null) {
                if (!s.loaded()) continue;                      // (try again when more of it is loaded)
                SCANS.put(l.dimension(), s);
                return;
            }
        }
    }

    private static boolean fresh(ServerLevel l, RebuildData d, BlockPos anchor) {
        return l.isLoaded(anchor) && d.at(anchor) == null && !REJECTED.contains(key(l, anchor));
    }

    private static long key(ServerLevel l, BlockPos p) { return p.asLong() * 31 + l.dimension().location().hashCode(); }

    private static void finish(ServerLevel l, RebuildData d, VillageSnapshot s) {
        if ("chunk".equals(s.failed)) return;                   // not all loaded any more: later
        RebuildData.Village v = s.failed == null ? s.finish(d.nextId) : null;
        if (v == null) { REJECTED.add(key(l, s.anchor)); return; }
        d.nextId++;
        v.lastSeen = l.getGameTime();
        d.add(v);
        XenoWarriors.LOGGER.info("Village #{} recorded at {} ({}): {} blocks, {} block states, box {}", v.id, v.anchor.toShortString(), v.kind, v.size(), v.paletteSize(), v.box);
    }

    /** Test hook: record the village around this bell right away (all at once). */
    public static RebuildData.Village snapshotNow(ServerLevel l, BlockPos bell) {
        RebuildData d = DATA.get(l.dimension());
        if (d == null) return null;
        RebuildData.Village old = d.at(bell);
        if (old != null) return old;
        VillageSnapshot s = VillageSnapshot.forBell(l, bell);
        while (!s.step()) {}
        int before = d.villages.size();
        finish(l, d, s);
        return d.villages.size() > before ? d.villages.get(d.villages.size() - 1) : null;
    }

    // ================================================================== workers

    /** Is this person / villager free to help, here and now? */
    private static boolean canWork(Mob w, ServerLevel l) {
        if (!w.isAlive() || w.isBaby() || w.isPassenger() || w.getTarget() != null || w.isNoAi() || !l.isDay()) return false;
        if (w instanceof HumanNpcEntity h) { if (h.carriesSoul() || h.raider() || h.reaction() != 0) return false; }
        else if (w instanceof Villager v) {
            if (v.getType() != net.minecraft.world.entity.EntityType.VILLAGER || v.isSleeping() || v.isTrading() || v.getBrain().isActive(Activity.PANIC)) return false;
        } else return false;
        return l.getEntitiesOfClass(Mob.class, w.getBoundingBox().inflate(14), m -> m.isAlive() && (m instanceof Enemy || (m instanceof HumanNpcEntity h && h.possessed()))).isEmpty();
    }

    private static void assign(ServerLevel l, RebuildData.Village v) {
        int working = 0;
        for (Job j : JOBS.values()) if (j.v == v) working++;
        int want = Math.min(MAX_WORKERS, Math.max(2, v.pendingCount() / 8));
        if (working >= want) return;
        AABB area = new AABB(v.box.minX() - 16, v.box.minY() - 8, v.box.minZ() - 16, v.box.maxX() + 17, v.box.maxY() + 9, v.box.maxZ() + 17);
        List<Mob> cands = new ArrayList<>(l.getEntitiesOfClass(Mob.class, area, m -> (m instanceof HumanNpcEntity || m instanceof Villager) && !JOBS.containsKey(m.getId())));
        long now = l.getGameTime();
        cands.sort(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(v.anchor.getX(), v.anchor.getY(), v.anchor.getZ())));
        int busy = 0, none = 0, tried = 0;
        for (Mob w : cands) {
            if (working >= want || tried >= 16) break;
            if (!canWork(w, l)) { busy++; continue; }
            tried++;
            int i = pick(l, v, w, now);
            if (i < 0) { none++; continue; }                   // nothing open this one can get to
            JOBS.put(w.getId(), new Job(w, l, v, i, now));
            working++;
        }
        lastAssign = "candidates " + cands.size() + ", busy " + busy + ", no spot " + none;
    }

    private static boolean taken(RebuildData.Village v, int i) {
        for (Job j : JOBS.values()) if (j.v == v && j.idx == i) return true;
        return false;
    }

    /**
     * Built from the bottom up so nothing floats: the spot right under it isn't still waiting, and nothing waits
     * diagonally under it either - unless it can hang on to a block beside it that's already there (a wall going up
     * next to a corner whose bit of crater nobody can get at yet).
     */
    private static boolean supported(ServerLevel l, RebuildData.Village v, BlockPos p) {
        int below = v.index(p.below());
        if (below >= 0 && v.pending.get(below)) return false;
        boolean diag = false;
        for (int dx = -1; dx <= 1 && !diag; dx++) for (int dz = -1; dz <= 1; dz++) {
            int j = (dx | dz) == 0 ? -1 : v.index(p.offset(dx, -1, dz));
            if (j >= 0 && v.pending.get(j)) { diag = true; break; }
        }
        if (!diag) return true;
        if (!l.getBlockState(p.below()).isAir()) return true;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos q = p.relative(d);
            int j = v.index(q);
            if ((j < 0 || !v.pending.get(j)) && l.getBlockState(q).isSolidRender(l, q)) return true;
        }
        return false;
    }

    /** The best open spot for this worker: low first, then close by. -1: none. */
    private static int pick(ServerLevel l, RebuildData.Village v, Mob w, long now) {
        int best = -1, firstY = Integer.MIN_VALUE, scanned = 0;
        double bestScore = Double.MAX_VALUE;
        double far = w instanceof Villager ? 40 * 40 : 96 * 96;                    // (townsfolk walk long ways in steps - see RebuildGoal)
        for (int i = v.pending.nextSetBit(0); i >= 0 && scanned < PICK_SCAN; i = v.pending.nextSetBit(i + 1), scanned++) {
            BlockPos p = v.pos(i);
            if (!l.isLoaded(p) || taken(v, i) || v.skipUntil.get(i) > now) continue;
            if (!needs(l, v, i)) { v.pending.clear(i); continue; }
            BlockState s = v.state(i);
            if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                int lo = v.index(p.below());
                if (lo >= 0 && v.pending.get(lo)) continue;                          // (goes in with its lower half)
            }
            if (!supported(l, v, p)) continue;
            double d2 = w.distanceToSqr(p.getX() + .5, p.getY() + .5, p.getZ() + .5);
            if (d2 > far) continue;                                                 // (too far to walk to: someone closer)
            if (firstY == Integer.MIN_VALUE) firstY = p.getY();
            double score = d2 + (p.getY() - firstY) * 6.0;   // (close by, a little lower first)
            if (score < bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    /** How far they can set a block from: normally, after trying to get closer for 8 s, after 20 s (roof tops nobody can climb to). */
    private static final double REACH = 4.6, FAR_REACH = 7.5, LONG_REACH = 11.0;

    private static double reach(long tried) { return tried > 400 ? LONG_REACH : tried > 160 ? FAR_REACH : REACH; }

    /** One tick of one worker. False: done (released). */
    private static boolean work(Job j) {
        ServerLevel l = j.l;
        long now = l.getGameTime();
        if (j.w.isRemoved() || j.w.level() != l || !j.v.pending.get(j.idx) && !repick(j, now)) return false;
        if (now - j.checked >= 20) { j.checked = now; if (!canWork(j.w, l) || !enabled(l)) { stopWalking(j.w); return false; } }
        if (!needs(l, j.v, j.idx)) { j.v.pending.clear(j.idx); if (!repick(j, now)) { stopWalking(j.w); return false; } }
        BlockPos p = j.v.pos(j.idx);
        Vec3 c = Vec3.atCenterOf(p);
        double dist = j.w.getEyePosition().distanceTo(c);
        double reach = reach(now - j.since);
        j.w.getLookControl().setLookAt(c.x, c.y, c.z);
        if (j.w instanceof Villager v && now % 10 == 0) {                // (a villager's brain walks it; a townsperson's RebuildGoal walks them)
            v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(p, 0.6F, 2));
            v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(p));
        }
        if (dist <= reach && now >= j.next) {
            Place r = place(l, j.v, j.idx, j.w);
            j.last = r == Place.WAIT ? "wait:" + why : r.name();
            if (r == Place.DONE) {
                j.next = now + (20 + l.random.nextInt(20)) / Math.max(1, speed);
                if (!repick(j, now)) { stopWalking(j.w); return false; }
            } else if (r == Place.WAIT) {
                j.v.skipUntil.put(j.idx, now + 30);
                if (!repick(j, now)) { stopWalking(j.w); return false; }
            }
        } else if (now - j.since > 600) {                         // can't get there: someone else, later (and longer each time)
            int f = j.v.fails.merge(j.idx, 1, Integer::sum);
            j.v.skipUntil.put(j.idx, now + 300L * (1L << Math.min(f, 6)));
            j.last = "unreachable";
            if (!repick(j, now)) { stopWalking(j.w); return false; }
        }
        return true;
    }

    private static boolean repick(Job j, long now) {
        int was = j.idx;
        j.idx = -2;                                            // (so it doesn't count as taken by itself)
        int i = pick(j.l, j.v, j.w, now);
        if (i < 0) { j.idx = was; return false; }
        j.idx = i; j.since = now;
        return true;
    }

    private static void stopWalking(Mob w) {
        if (w instanceof Villager v) { v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET); v.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET); }
        else w.getNavigation().stop();
    }

    /** Where this townsperson is rebuilding right now (null: they aren't) - for their RebuildGoal. */
    public static BlockPos spotFor(Mob w) {
        Job j = JOBS.get(w.getId());
        return j == null || j.w != w || j.idx < 0 ? null : j.v.pos(j.idx);
    }

    public static boolean inReach(Mob w, BlockPos p) {
        Job j = JOBS.get(w.getId());
        double reach = j == null ? REACH : reach(j.l.getGameTime() - j.since);
        return w.getEyePosition().distanceTo(Vec3.atCenterOf(p)) <= reach - 0.6;
    }

    /** How many are rebuilding this village right now (tests). */
    public static int workers(RebuildData.Village v) {
        int n = 0;
        for (Job j : JOBS.values()) if (j.v == v) n++;
        return n;
    }

    /** Who's rebuilding this village right now: [humans, villagers] (tests). */
    private static String why = "", lastAssign = "";

    /** What each worker of this village is doing (tests). */
    public static String debug(RebuildData.Village v) {
        StringBuilder sb = new StringBuilder("[" + lastAssign + "] ");
        for (Job j : JOBS.values()) if (j.v == v) {
            BlockPos p = j.idx >= 0 ? v.pos(j.idx) : null;
            sb.append(j.w instanceof Villager ? "V" : "H").append('@').append(j.w.blockPosition().toShortString()).append("->")
              .append(p == null ? "-" : p.toShortString() + " " + v.state(j.idx).getBlock().getName().getString())
              .append(String.format(" d=%.1f ", p == null ? -1 : j.w.getEyePosition().distanceTo(Vec3.atCenterOf(p)))).append(j.last).append("; ");
        }
        return sb.toString();
    }

    public static int[] workerKinds(RebuildData.Village v) {
        int[] n = new int[2];
        for (Job j : JOBS.values()) if (j.v == v) n[j.w instanceof Villager ? 1 : 0]++;
        return n;
    }

    // ================================================================== placing

    private enum Place { DONE, WAIT, GONE }

    /** The other half of a door, tall plant or bed, if it's in the snapshot. */
    private static BlockPos partner(BlockState s, BlockPos p) {
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))
            return s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? p.above() : p.below();
        if (s.getBlock() instanceof BedBlock) {
            Direction f = s.getValue(BedBlock.FACING);
            return s.getValue(BedBlock.PART) == BedPart.FOOT ? p.relative(f) : p.relative(f.getOpposite());
        }
        return null;
    }

    private static BlockState fresh(BlockState s) {
        if (s.getBlock() instanceof CropBlock c) return c.getStateForAge(0);          // crops: replanted, not regrown (no free harvest)
        if (s.getBlock() instanceof StemBlock && s.hasProperty(StemBlock.AGE)) return s.setValue(StemBlock.AGE, 0);
        return s;
    }

    /** Nobody stands in it, and no player is close enough to be shut in. */
    private static boolean clear(ServerLevel l, BlockPos p, BlockState s) {
        if (l.getNearestPlayer(p.getX() + .5, p.getY() + .5, p.getZ() + .5, 3.0, EntitySelector.NO_SPECTATORS) != null) return false;
        if (s.getCollisionShape(l, p).isEmpty()) return true;
        List<Entity> in = l.getEntitiesOfClass(Entity.class, new AABB(p), e -> !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb) && !(e instanceof Projectile) && !e.isSpectator());
        for (Entity e : in) if (e instanceof Mob m && (m instanceof HumanNpcEntity || m instanceof Villager || m instanceof net.minecraft.world.entity.animal.Animal)) {
            double dx = m.getX() - (p.getX() + .5), dz = m.getZ() - (p.getZ() + .5), len = Math.sqrt(dx * dx + dz * dz);   // "excuse me": step out of the way
            if (len < 0.05) { double a = l.random.nextDouble() * Math.PI * 2; dx = Math.cos(a); dz = Math.sin(a); len = 1; }
            m.setDeltaMovement(dx / len * 0.35, 0.1, dz / len * 0.35);
            m.hurtMarked = true;
        }
        return in.isEmpty();
    }

    private static Place place(ServerLevel l, RebuildData.Village v, int i, Mob w) {
        if (!needs(l, v, i)) { v.pending.clear(i); return Place.GONE; }
        BlockPos p = v.pos(i);
        BlockState s = fresh(v.state(i));
        BlockPos q = partner(s, p);
        int qi = q == null ? -1 : v.index(q);
        BlockState s2 = qi >= 0 && v.state(qi).is(s.getBlock()) && needs(l, v, qi) ? fresh(v.state(qi)) : null;
        if (s2 == null) q = null;
        boolean lowerFirst = q == null || q.getY() >= p.getY();
        BlockPos a = lowerFirst ? p : q, b = lowerFirst ? q : p;
        BlockState sa = lowerFirst ? s : s2, sb = lowerFirst ? s2 : s;
        if (!sa.canSurvive(l, a)) { why = "survive"; return Place.WAIT; }
        if (!clear(l, a, sa) || (b != null && !clear(l, b, sb))) { why = "blocked"; return Place.WAIT; }
        if (b != null) {
            l.setBlock(a, sa, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            l.setBlock(b, sb, Block.UPDATE_ALL);
            sa.updateNeighbourShapes(l, a, Block.UPDATE_ALL);
            l.blockUpdated(a, sa.getBlock());
            v.pending.clear(qi);
            v.restored++;
        } else l.setBlock(a, sa, Block.UPDATE_ALL);
        v.pending.clear(i);
        v.restored++;
        w.swing(InteractionHand.MAIN_HAND);
        l.gameEvent(w, GameEvent.BLOCK_PLACE, p);
        var sound = s.getSoundType();
        l.playSound(null, p, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1F) / 2F, sound.getPitch() * 0.8F);
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), p.getX() + .5, p.getY() + .5, p.getZ() + .5, 10, 0.3, 0.3, 0.3, 0.05);
        if (l.random.nextInt(4) == 0) l.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + .5, p.getY() + 1.0, p.getZ() + .5, 2, 0.3, 0.2, 0.3, 0);
        return Place.DONE;
    }
}
