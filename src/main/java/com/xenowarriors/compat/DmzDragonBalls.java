package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.*;

/**
 * With DragonMineZ: a bot carrying all seven Dragon Balls of a set (Earth's on the Overworld, Namek's on Namek - the
 * worlds DragonMineZ lets that set be used in) puts them down in a ring, one-star to seven-star in order, once it's
 * out of a fight on a flat spot near you, and calls the dragon the way you would (DragonMineZ's own right-click on a
 * ball, with you as the summoner). The dragon is yours: its wish menu opens for you and the wish is yours to pick.
 * Placed balls are registered with DragonMineZ's Dragon Ball tracking the same as balls a player puts down.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzDragonBalls {
    private DmzDragonBalls() {}

    /** The sets: id, the ball block per star, the worlds they work in (DragonMineZ's data asks first; these are its defaults). */
    private record BallSet(String id, String suffix, String dim) {}
    private static final BallSet[] SETS = {new BallSet("earth", "", "minecraft:overworld"), new BallSet("namek", "_namek", "dragonminez:namek")};
    private static final int[][] RING = new int[7][];
    static {
        for (int k = 0; k < 7; k++) {
            double a = Math.toRadians(-90 + k * 360.0 / 7);
            RING[k] = new int[]{(int) Math.round(Math.cos(a) * 2), (int) Math.round(Math.sin(a) * 2)};
        }
    }

    private static Boolean loaded;
    private static Method getBallSet, supportsDim;

    /** (For the tests) what happened, newest last. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    private static void note(String s) {
        if (LOG.size() > 40) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz dragon balls: {}", s);
    }

    static boolean ready() {
        if (loaded == null) {
            loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
            if (loaded) try {
                Class<?> defs = Class.forName("com.dragonminez.common.dragonball.DragonBallDefinitions");
                getBallSet = defs.getMethod("getBallSet", String.class);
                supportsDim = getBallSet.getReturnType().getMethod("supportsDimension", net.minecraft.resources.ResourceKey.class);
            } catch (Throwable t) { getBallSet = null; }
        }
        return loaded;
    }

    private static Item ball(BallSet s, int star) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation("dragonminez", "dball" + star + s.suffix));
    }

    private static boolean worksHere(BallSet s, ServerLevel level) {
        if (getBallSet != null) {
            try {
                Object set = getBallSet.invoke(null, s.id);
                if (set != null) return (Boolean) supportsDim.invoke(set, level.dimension());
            } catch (Throwable ignored) {}
        }
        return level.dimension().location().toString().equals(s.dim);
    }

    /** The set this bot carries all seven of (and can use in this world), or null. */
    static BallSet carried(PartyBotEntity b, ServerLevel level) {
        for (BallSet s : SETS) {
            if (!worksHere(s, level)) continue;
            boolean all = true;
            for (int star = 1; star <= 7 && all; star++) { Item it = ball(s, star); all = it != null && it != net.minecraft.world.item.Items.AIR && slot(b, it) >= 0; }
            if (all) return s;
        }
        return null;
    }

    private static int slot(PartyBotEntity b, Item it) {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).is(it)) return i;
        return -1;
    }

    // ---------------------------------------------------------------- when and where

    private static final Map<UUID, Long> BUSY = new HashMap<>(), NEXT_TRY = new HashMap<>();

    /** Out of a fight: nothing it's after, nothing hostile coming for it or you, not hit lately. */
    static boolean calm(PartyBotEntity b, ServerPlayer o) {
        if (b.getTarget() != null && b.getTarget().isAlive()) return false;
        if (b.tickCount - b.getLastHurtByMobTimestamp() < 200 && b.getLastHurtByMob() != null) return false;
        return b.level().getEntitiesOfClass(Mob.class, b.getBoundingBox().inflate(16), m -> m instanceof Enemy && m.isAlive()
                && (m.getTarget() == b || m.getTarget() == o || m.distanceTo(b) < 8)).isEmpty();
    }

    private static boolean spotOk(ServerLevel l, BlockPos at) {
        BlockState below = l.getBlockState(at.below());
        return l.getBlockState(at).canBeReplaced() && l.getFluidState(at).isEmpty() && below.isFaceSturdy(l, at.below(), Direction.UP)
                && l.getBlockState(at.above()).canBeReplaced();
    }

    /** A ring's center near the bot where all seven spots are flat, open ground (or null). */
    static BlockPos flatSpot(ServerLevel l, BlockPos near) {
        for (int r = 0; r <= 4; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos c = near.offset(dx, dy, dz);
                        if (!spotOk(l, c)) continue;
                        boolean ok = true;
                        for (int[] o : RING) if (!spotOk(l, c.offset(o[0], 0, o[1]))) { ok = false; break; }
                        if (ok) return c;
                    }
                }
        return null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer o) || o instanceof FakePlayer || o.tickCount % 40 != 17 || !ready()) return;
        for (PartyBotEntity b : PartyBots.bots(o)) {
            if (b.journey() || b.downed() || b.sparring() || !b.onGround() || BUSY.containsKey(b.getUUID())) continue;
            if (b.level() != o.level() || b.distanceTo(o) > 24) continue;
            long now = o.level().getGameTime();
            if (now < NEXT_TRY.getOrDefault(b.getUUID(), 0L)) continue;
            if (!(b.level() instanceof ServerLevel level)) continue;
            BallSet set = carried(b, level);
            if (set == null) continue;
            if (!calm(b, o)) continue;
            start(b, o, set, level, false);
        }
    }

    /** Starts putting the balls down (true if it found somewhere to). Tests call it with now=true (no waiting for a calm moment). */
    public static boolean start(PartyBotEntity b, ServerPlayer o, Object setOrNull, ServerLevel level, boolean test) {
        BallSet set = setOrNull instanceof BallSet s ? s : carried(b, level);
        if (set == null) { note("no full set of usable balls on " + b.character().getId()); return false; }
        BlockPos c = flatSpot(level, b.blockPosition());
        long now = level.getGameTime();
        if (c == null) {
            NEXT_TRY.put(b.getUUID(), now + 600);
            note(b.character().getId() + " found no flat spot near " + b.blockPosition().toShortString());
            if (o.distanceTo(b) < 24) o.displayClientMessage(Component.literal("").append(b.label()).append(" wants a flat, open spot to put the Dragon Balls down").withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        BUSY.put(b.getUUID(), now);
        NEXT_TRY.put(b.getUUID(), now + 1200);
        note(b.character().getId() + " placing the " + set.id + " Dragon Balls around " + c.toShortString());
        o.displayClientMessage(Component.literal("").append(b.label()).append(" is gathering the Dragon Balls...").withStyle(ChatFormatting.GOLD), true);
        b.getNavigation().moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 1.0);
        BlockPos[] at = new BlockPos[7];
        for (int k = 0; k < 7; k++) at[k] = c.offset(RING[k][0], 0, RING[k][1]);
        for (int k = 0; k < 7; k++) {
            final int star = k + 1;
            com.xenowarriors.skill.ServerScheduler.later(20 + k * 14, () -> place(b, o, set, level, at[star - 1], star));
        }
        com.xenowarriors.skill.ServerScheduler.later(20 + 7 * 14 + 20, () -> summon(b, o, set, level, at[6]));
        return true;
    }

    private static boolean ok(PartyBotEntity b) { return b.isAlive() && !b.isRemoved() && !b.downed() && BUSY.containsKey(b.getUUID()); }

    private static void place(PartyBotEntity b, ServerPlayer o, BallSet set, ServerLevel level, BlockPos pos, int star) {
        if (!ok(b)) return;
        Item it = ball(set, star);
        int s = slot(b, it);
        if (s < 0 || !(it instanceof BlockItem bi)) { BUSY.remove(b.getUUID()); note("star " + star + " missing from the bag - stopped"); return; }
        if (!spotOk(level, pos)) { BUSY.remove(b.getUUID()); note("spot for star " + star + " taken - stopped"); return; }
        if (b.distanceToSqr(Vec3.atCenterOf(pos)) > 6 * 6) b.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);   // (it got pushed away: back over)
        b.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5);
        b.swing(InteractionHand.MAIN_HAND, true);
        com.xenowarriors.anim.Poses.play(b, com.xenowarriors.anim.Poses.PLACE, 10);
        Block block = bi.getBlock();
        BlockSnapshot snap = BlockSnapshot.create(level.dimension(), level, pos);
        BlockState st = block.defaultBlockState();
        try {                                                                   // (facing whoever's watching, like a player placing it)
            var facing = st.getBlock().getStateDefinition().getProperty("facing");
            if (facing instanceof net.minecraft.world.level.block.state.properties.DirectionProperty dp)
                st = st.setValue(dp, Direction.fromYRot(b.getYRot()).getOpposite());
        } catch (Throwable ignored) {}
        level.setBlock(pos, st, Block.UPDATE_ALL);
        MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snap, level.getBlockState(pos.below()), b));   // (DragonMineZ's tracking / radar)
        b.inventory.removeItem(s, 1);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.NEUTRAL, 1F, 0.8F + star * 0.06F);
        note("placed " + star + "-star (" + ForgeRegistries.BLOCKS.getKey(block) + ") at " + pos.toShortString());
    }

    private static void summon(PartyBotEntity b, ServerPlayer o, BallSet set, ServerLevel level, BlockPos pos) {
        if (!ok(b)) return;
        BUSY.remove(b.getUUID());
        BlockState st = level.getBlockState(pos);
        if (st.isAir()) { note("summon: the last ball is gone"); return; }
        // DragonMineZ's own summoning: right-clicking a ball with all seven nearby - the owner is the one who summons
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        Set<Integer> before = new HashSet<>();
        for (var e : level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(pos).inflate(32), Dmz6Dragon::is)) before.add(e.getId());
        var res = st.use(level, o, InteractionHand.MAIN_HAND, hit);
        b.swing(InteractionHand.MAIN_HAND, true);
        net.minecraft.world.entity.Entity dragon = null;
        for (var e : level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(pos).inflate(32), Dmz6Dragon::is))
            if (!before.contains(e.getId())) dragon = e;
        note("summon via " + ForgeRegistries.BLOCKS.getKey(st.getBlock()) + " -> " + res + " dragon=" + (dragon == null ? "none" : ForgeRegistries.ENTITY_TYPES.getKey(dragon.getType()) + " owner=" + Dmz6Dragon.owner(dragon)));
        if (dragon == null) return;
        o.sendSystemMessage(Component.literal("").append(b.label()).append(" summoned the dragon for you - make your wish!").withStyle(ChatFormatting.GOLD));
        final int id = dragon.getId();
        com.xenowarriors.skill.ServerScheduler.later(30, () -> {                // (once your game has the dragon)
            if (!o.hasDisconnected()) com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.DmzWishPacket(id), o);
        });
    }

    /** DragonMineZ's dragon entities (Shenron, Porunga...), read by reflection. */
    static final class Dmz6Dragon {
        static boolean is(net.minecraft.world.entity.Entity e) {
            return e.getClass().getName().equals("com.dragonminez.common.init.entities.dragon.DragonWishEntity") || e.getClass().getSuperclass().getName().equals("com.dragonminez.common.init.entities.dragon.DragonWishEntity");
        }

        static String owner(net.minecraft.world.entity.Entity e) {
            try { return String.valueOf(e.getClass().getMethod("getOwnerName").invoke(e)); } catch (Throwable t) { return "?"; }
        }
    }

    /** (Tests) gives the bot all seven balls of the set usable where it is. */
    public static String giveSetForTest(PartyBotEntity b) {
        if (!ready() || !(b.level() instanceof ServerLevel level)) return null;
        note("give set: world " + level.dimension().location() + " earth works=" + worksHere(SETS[0], level) + " namek works=" + worksHere(SETS[1], level));
        for (BallSet s : SETS) {
            if (!worksHere(s, level)) continue;
            for (int star = 7; star >= 1; star--) b.inventory.addItem(new ItemStack(ball(s, star)));   // (out of order on purpose)
            return s.id;
        }
        return null;
    }
}
