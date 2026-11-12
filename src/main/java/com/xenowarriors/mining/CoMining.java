package com.xenowarriors.mining;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Mining together: someone else digging at the block you're digging makes it break faster.
 * <ul>
 *   <li>Another player helps when they're close to you ({@link #REACH} blocks) and are mining the same block - looking
 *       right at it.</li>
 *   <li>Your bots help in any mode (not only mining mode) when they aren't fighting and are close to you: they step up to
 *       the block from the side - never between you and it, so they're never in your way - look at it and dig along
 *       (see {@link PartyBotEntity}'s co-mine goal). They count once they're in place and looking at it.</li>
 *   <li>Each helper adds {@link #PER_HELPER} of your speed (at most {@link #MAX_HELPERS} helpers). Helpers who are
 *       players get the same boost on their own digging.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CoMining {
    private CoMining() {}

    public static final double REACH = 6;
    public static final float PER_HELPER = 0.6F;
    public static final int MAX_HELPERS = 4;

    /** player -> the block they're digging */
    private static final Map<UUID, BlockPos> MINING = new HashMap<>();
    /** bot -> the block it's helping with, and the last tick it was in place looking at it */
    private static final Map<UUID, Long> BOT_AT = new HashMap<>();
    private static final Map<UUID, BlockPos> BOT_POS = new HashMap<>();
    /** player -> {block, helpers} last worked out (the server's own dig speed uses it) */
    private static final Map<UUID, Help> HELP = new HashMap<>();
    /** When each digger last swung (dig) and last had the block in their crosshair: a click that never got its "stop"
     *  (a quick tap, a menu opened mid-dig, a combo click on a block) would otherwise leave them "digging" forever -
     *  and their bots swinging at it (the stuck attack animation). */
    private static final Map<UUID, Long> SWUNG = new HashMap<>(), LOOKED = new HashMap<>();
    private static final Map<UUID, Long> TEST_HOLD = new HashMap<>();
    static final int STALE_SWING = 30, STALE_LOOK = 20;
    record Help(BlockPos pos, int helpers) {}

    public static float multiplier(int helpers) { return 1F + PER_HELPER * Math.min(MAX_HELPERS, Math.max(0, helpers)); }

    /** The block this player is digging right now, or null. */
    public static BlockPos digging(ServerPlayer p) { return MINING.get(p.getUUID()); }

    /** A bot is in place, looking at this block and digging along. */
    public static void botHelping(PartyBotEntity b, BlockPos pos) {
        BOT_POS.put(b.getUUID(), pos.immutable());
        BOT_AT.put(b.getUUID(), b.level().getGameTime());
    }

    public static void botStopped(PartyBotEntity b) { BOT_POS.remove(b.getUUID()); BOT_AT.remove(b.getUUID()); }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        switch (e.getAction()) {
            case START -> { if (!e.isCanceled()) { MINING.put(p.getUUID(), e.getPos().immutable()); long t = p.level().getGameTime(); SWUNG.put(p.getUUID(), t); LOOKED.put(p.getUUID(), t); } }
            case STOP, ABORT -> stop(p);
            default -> {}
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p && e.getPos().equals(MINING.get(p.getUUID()))) stop(p);
    }

    private static void stop(ServerPlayer p) {
        MINING.remove(p.getUUID());
        SWUNG.remove(p.getUUID()); LOOKED.remove(p.getUUID()); TEST_HOLD.remove(p.getUUID());
        Help h = HELP.remove(p.getUUID());
        if (h != null && h.helpers > 0) ModNetwork.sendToPlayer(new CoMinePacket(BlockPos.ZERO, 0), p);
    }

    /** Is this player looking right at that block (their crosshair on it)? */
    static boolean lookingAt(ServerPlayer p, BlockPos pos) {
        Vec3 eye = p.getEyePosition();
        double reach = Math.max(4.5, p.getBlockReach());
        BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    /** How many are helping this player with the block they're digging. */
    public static int helpers(ServerPlayer p) {
        BlockPos pos = MINING.get(p.getUUID());
        if (pos == null || p.level().getBlockState(pos).isAir()) return 0;
        int n = 0;
        for (ServerPlayer q : p.serverLevel().players()) {
            if (q == p || !pos.equals(MINING.get(q.getUUID())) || q.distanceToSqr(p) > REACH * REACH || !lookingAt(q, pos)) continue;
            n++;
        }
        long now = p.level().getGameTime();
        for (PartyBotEntity b : PartyBots.bots(p)) {
            Long at = BOT_AT.get(b.getUUID());
            if (at != null && now - at <= 10 && pos.equals(BOT_POS.get(b.getUUID())) && b.distanceToSqr(p) <= REACH * REACH * 1.6) n++;
        }
        return Math.min(MAX_HELPERS, n);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 4 != 0) return;
        if (MINING.isEmpty() && HELP.isEmpty()) return;
        for (UUID id : new ArrayList<>(MINING.keySet())) {
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(id);
            if (p == null || !p.isAlive()) { MINING.remove(id); HELP.remove(id); continue; }
            BlockPos pos = MINING.get(id);
            if (p.level().getBlockState(pos).isAir() || p.distanceToSqr(Vec3.atCenterOf(pos)) > 8 * 8) { stop(p); continue; }
            long now = p.level().getGameTime();
            Long hold = TEST_HOLD.get(id);
            if (hold == null || now > hold) {                                    // (still really digging: swinging at it, crosshair on it)
                if (p.swinging || p.swingTime > 0) SWUNG.put(id, now);
                if (lookingAt(p, pos)) LOOKED.put(id, now);
                if (now - SWUNG.getOrDefault(id, now) > STALE_SWING || now - LOOKED.getOrDefault(id, now) > STALE_LOOK) { stop(p); continue; }
            }
            int n = helpers(p);
            Help old = HELP.get(id);
            if (old == null || old.helpers != n || !old.pos.equals(pos)) {
                HELP.put(id, new Help(pos, n));
                ModNetwork.sendToPlayer(new CoMinePacket(pos, n), p);
                if (n > 0) p.displayClientMessage(Component.literal("⛏ Mining together: " + n + (n == 1 ? " helper" : " helpers") + " - "
                        + String.format(Locale.ROOT, "%.1f", multiplier(n)) + "x faster").withStyle(ChatFormatting.AQUA), true);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        MINING.remove(id); HELP.remove(id); SWUNG.remove(id); LOOKED.remove(id); TEST_HOLD.remove(id);
    }

    /** Server side: the dig speed with the helpers (the client does the same from {@link CoMinePacket}). */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        if (e.getEntity().level().isClientSide) { CoMineClientHook.apply(e); return; }
        Help h = HELP.get(e.getEntity().getUUID());
        if (h == null || h.helpers <= 0 || e.getPosition().isEmpty() || !h.pos.equals(e.getPosition().get())) return;
        e.setNewSpeed(e.getNewSpeed() * multiplier(h.helpers));
    }

    /**
     * Where a bot can stand to help dig this block: within reach of it, on solid ground with room to stand, and never
     * between the player and the block (its body clear of the player's line of sight) or on top of the player.
     * The closest such spot to the bot, or null.
     */
    public static BlockPos helpSpot(PartyBotEntity b, ServerPlayer p, BlockPos pos) {
        var lv = b.level();
        Vec3 eye = p.getEyePosition(), c = Vec3.atCenterOf(pos);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -2; dy <= 1; dy++) {
            if (dx == 0 && dz == 0) continue;
            BlockPos at = pos.offset(dx, dy, dz);
            if (!lv.getBlockState(at).getCollisionShape(lv, at).isEmpty() || !lv.getBlockState(at.above()).getCollisionShape(lv, at.above()).isEmpty()) continue;
            if (lv.getBlockState(at.below()).getCollisionShape(lv, at.below()).isEmpty()) continue;
            Vec3 feet = Vec3.atBottomCenterOf(at), body = feet.add(0, 1, 0), head = feet.add(0, b.getEyeHeight(), 0);
            if (head.distanceTo(c) > 3.6) continue;                                  // (within its reach)
            if (feet.distanceToSqr(p.position()) < 1.5 * 1.5) continue;              // (not on the player)
            if (segDist(eye, c, body) < 1.05 || segDist(eye, c, feet.add(0, 0.3, 0)) < 0.9 || segDist(eye, c, head) < 1.0) continue;   // (out of the player's view of the block)
            double d = feet.distanceToSqr(b.position());
            if (d < bestD) { bestD = d; best = at; }
        }
        return best;
    }

    /** Distance from point x to the segment a-b. */
    static double segDist(Vec3 a, Vec3 b, Vec3 x) {
        Vec3 ab = b.subtract(a);
        double t = ab.lengthSqr() < 1.0E-6 ? 0 : Math.max(0, Math.min(1, x.subtract(a).dot(ab) / ab.lengthSqr()));
        return a.add(ab.scale(t)).distanceTo(x);
    }

    /** Is the bot standing in the player's line of sight to the block? */
    public static boolean inTheWay(PartyBotEntity b, ServerPlayer p, BlockPos pos) {
        Vec3 eye = p.getEyePosition(), c = Vec3.atCenterOf(pos);
        return b.getBoundingBox().inflate(0.15).clip(eye, c).isPresent();
    }

    /** (Tests) */
    public static int helpersForTest(ServerPlayer p) { Help h = HELP.get(p.getUUID()); return h == null ? 0 : h.helpers; }
    public static void startForTest(ServerPlayer p, BlockPos pos) { startForTest(p, pos, 400); }
    /** As if they started digging it; held (no swing / look checks) this many ticks - 0 for a stale click. */
    public static void startForTest(ServerPlayer p, BlockPos pos, int hold) {
        long t = p.level().getGameTime();
        MINING.put(p.getUUID(), pos.immutable()); SWUNG.put(p.getUUID(), t); LOOKED.put(p.getUUID(), t);
        if (hold > 0) TEST_HOLD.put(p.getUUID(), t + hold); else TEST_HOLD.remove(p.getUUID());
    }
    public static void stopForTest(ServerPlayer p) { stop(p); }
}
