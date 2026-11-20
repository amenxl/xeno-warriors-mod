package com.xenowarriors.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * (Once your Crafting is level 20 a bot takes a boat across instead - BotBoats - and lays blocks only when it can't.)
 * Bots with Building level {@value #LEVEL} or more don't swim across water: they lay a path of blocks from their bag
 * (cobblestone, dirt, stone, planks...) one step ahead of them on the water's surface and walk it - and pick each
 * block back up once they're past it (the water's put back as it was), so nothing's left behind and the blocks go
 * round again. Not while flying, riding or fighting in the water.
 */
public final class BotWaterBridge {
    private BotWaterBridge() {}

    public static final int LEVEL = 10;
    /** Blocks behind it further than this are picked back up. */
    static final double BEHIND = 2.6;

    private record Placed(BlockPos at, BlockState was, ItemStack item) {}
    private static final Map<UUID, Deque<Placed>> PATH = new HashMap<>();
    /** (Tests) blocks laid and picked back up. */
    public static int laid, lifted;

    private static final Set<Block> GOOD = Set.of(Blocks.COBBLESTONE, Blocks.DIRT, Blocks.STONE, Blocks.COBBLED_DEEPSLATE, Blocks.NETHERRACK,
            Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS,
            Blocks.MANGROVE_PLANKS, Blocks.CHERRY_PLANKS, Blocks.BAMBOO_PLANKS, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.DEEPSLATE,
            Blocks.TUFF, Blocks.BLACKSTONE, Blocks.COARSE_DIRT, Blocks.MUD_BRICKS, Blocks.STONE_BRICKS, Blocks.BRICKS, Blocks.END_STONE);

    /** Every tick, from the bot's own (server). */
    static void tick(PartyBotEntity b) {
        if (!(b.level() instanceof ServerLevel l)) return;
        Deque<Placed> path = PATH.get(b.getUUID());
        if (path != null) pickUp(b, l, path, false);
        if (b.tickCount % 2 != 0 || b.downed() || b.isPassenger() || b.isNoAi() || b.selfFlying || b.isNoGravity() || b.held()) return;
        boolean boats = BotBoats.unlocked(b);                                    // (Crafting 20: a boat instead)
        if (!boats && com.xenowarriors.stats.Building.level(b) < LEVEL) return;
        boolean blocks = com.xenowarriors.stats.Building.level(b) >= LEVEL;
        if (b.getTarget() != null && b.isInWater()) return;                      // (in a fight in the water: it fights)
        Vec3 goal = goal(b);
        if (goal == null) return;
        Vec3 to = new Vec3(goal.x - b.getX(), 0, goal.z - b.getZ());
        if (to.lengthSqr() < 2.25) return;
        Vec3 dir = to.normalize();
        // the water surface: under its feet (swimming), or a step ahead (standing on the bridge / the shore)
        BlockPos feet = b.blockPosition();
        BlockPos under = surface(l, feet);
        if (under != null && b.isInWater() && boats && BotBoats.cross(b, l)) return;
        if (under != null && b.isInWater() && blocks && lay(b, l, under)) { b.teleportTo(b.getX(), under.getY() + 1.0, b.getZ()); b.setDeltaMovement(Vec3.ZERO); return; }
        if (!b.onGround()) return;
        BlockPos ahead = BlockPos.containing(b.getX() + dir.x * 1.1, b.getY() - 0.5, b.getZ() + dir.z * 1.1);
        BlockPos s = surface(l, ahead.above());
        boolean onPath = onOwn(b);
        if (s != null && s.getY() == feet.getY() - 1 && !onPath && boats && BotBoats.cross(b, l)) return;
        if (s != null && s.getY() == feet.getY() - 1 && blocks && lay(b, l, s)) onPath = true;
        if (onPath) {                                                            // (walk it: the pathfinder won't plan across water)
            b.getNavigation().stop();
            b.getMoveControl().setWantedPosition(b.getX() + dir.x * 1.2, b.getY(), b.getZ() + dir.z * 1.2, 1.0);
            BotMoves.face(b, (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)), 30F);
        }
    }

    /** Standing on (or just stepping off) one of its own blocks. */
    private static boolean onOwn(PartyBotEntity b) {
        Deque<Placed> path = PATH.get(b.getUUID());
        if (path == null) return false;
        BlockPos below = b.blockPosition().below();
        for (Placed p : path) if (p.at.equals(below) || p.at.distSqr(below) <= 2) return true;
        return false;
    }

    /** The top water block of the column at this spot (open air above it), or null. */
    private static BlockPos surface(ServerLevel l, BlockPos p) {
        for (int k = 0; k < 3; k++) {
            BlockPos q = p.below(k);
            if (l.getFluidState(q).is(Fluids.WATER) && l.getBlockState(q).getBlock() == Blocks.WATER && l.getBlockState(q.above()).isAir()) return q;
        }
        return null;
    }

    private static Vec3 goal(PartyBotEntity b) {
        var nav = b.getNavigation();
        if (nav.getTargetPos() != null && !nav.isDone()) return Vec3.atBottomCenterOf(nav.getTargetPos());
        var o = b.owner();
        if (o != null && o.level() == b.level() && b.distanceToSqr(o) > 3 * 3) return o.position();
        return null;
    }

    private static boolean lay(PartyBotEntity b, ServerLevel l, BlockPos at) {
        int slot = -1;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (s.getItem() instanceof BlockItem bi && GOOD.contains(bi.getBlock())) { slot = i; break; }
        }
        if (slot < 0) return false;
        ItemStack s = b.inventory.getItem(slot);
        Block block = ((BlockItem) s.getItem()).getBlock();
        BlockState was = l.getBlockState(at);
        if (!l.setBlock(at, block.defaultBlockState(), 3)) return false;
        ItemStack one = s.split(1);
        b.inventory.setChanged();
        PATH.computeIfAbsent(b.getUUID(), k -> new ArrayDeque<>()).addLast(new Placed(at.immutable(), was, one));
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        l.playSound(null, at, block.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7F, 1F);
        laid++;
        return true;
    }

    /** Its blocks behind it go back in its bag and the water comes back. {@code all}: every one (it's gone). */
    private static void pickUp(PartyBotEntity b, ServerLevel l, Deque<Placed> path, boolean all) {
        for (Iterator<Placed> it = path.iterator(); it.hasNext(); ) {
            Placed p = it.next();
            double d = b.position().distanceTo(Vec3.atBottomCenterOf(p.at.above()));
            boolean standing = b.blockPosition().below().equals(p.at) || b.getBoundingBox().inflate(0.05).intersects(new net.minecraft.world.phys.AABB(p.at.above()));
            if (!all && (d < BEHIND || standing)) continue;
            if (l.isLoaded(p.at) && l.getBlockState(p.at).getBlock() == ((BlockItem) p.item.getItem()).getBlock()) {
                l.setBlock(p.at, p.was, 3);
                ItemStack left = b.inventory.addItem(p.item.copy());
                if (!left.isEmpty()) b.spawnAtLocation(left);
                l.playSound(null, p.at, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 1.4F);
                lifted++;
            }
            it.remove();
        }
        if (path.isEmpty()) PATH.remove(b.getUUID());
    }

    /** It's going away (dismissed, unloaded): its path is cleared up now. */
    static void forget(PartyBotEntity b) {
        Deque<Placed> path = PATH.get(b.getUUID());
        if (path != null && b.level() instanceof ServerLevel l) pickUp(b, l, path, true);
    }

    /** (Tests) blocks it has down right now. */
    public static int down(PartyBotEntity b) { Deque<Placed> p = PATH.get(b.getUUID()); return p == null ? 0 : p.size(); }
}
