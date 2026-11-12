package com.xenowarriors.bot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Music time: a bot with a music disc and a jukebox in its bag - or what a jukebox is made of (8 planks and a diamond),
 * which it crafts into one - now and then sets the jukebox down next to you when things are quiet and plays a disc.
 * When the song's over, a fight starts or you wander off, it takes the disc and the jukebox back.
 */
public final class BotJukebox {
    private BotJukebox() {}

    /** Ticks between two songs it puts on by itself (about 8 minutes, give or take). */
    public static final int GAP = 20 * 60 * 8;
    private static final String LAST = "xw_jukebox_at";

    private record Session(BlockPos pos, ItemStack disc, long ends) {}
    private static final Map<UUID, Session> PLAYING = new HashMap<>();
    /** (Tests) songs played. */
    public static int played;

    private static int find(PartyBotEntity b, java.util.function.Predicate<ItemStack> p) {
        var inv = b.inventory;
        for (int i = 0; i < inv.getContainerSize(); i++) if (p.test(inv.getItem(i))) return i;
        return -1;
    }

    private static int count(PartyBotEntity b, java.util.function.Predicate<ItemStack> p) {
        int n = 0;
        var inv = b.inventory;
        for (int i = 0; i < inv.getContainerSize(); i++) if (p.test(inv.getItem(i))) n += inv.getItem(i).getCount();
        return n;
    }

    private static void take(PartyBotEntity b, java.util.function.Predicate<ItemStack> p, int n) {
        var inv = b.inventory;
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!p.test(s)) continue;
            int k = Math.min(n, s.getCount());
            s.shrink(k); n -= k;
        }
    }

    /** It has a jukebox, or crafts one from 8 planks and a diamond in its bag. */
    private static boolean jukebox(PartyBotEntity b) {
        if (find(b, s -> s.is(Items.JUKEBOX)) >= 0) return true;
        java.util.function.Predicate<ItemStack> planks = s -> s.is(net.minecraft.tags.ItemTags.PLANKS);
        if (count(b, planks) < 8 || count(b, s -> s.is(Items.DIAMOND)) < 1) return false;
        take(b, planks, 8);
        take(b, s -> s.is(Items.DIAMOND), 1);
        b.inventory.addItem(new ItemStack(Items.JUKEBOX));
        return true;
    }

    private static boolean quiet(PartyBotEntity b, ServerPlayer o) {
        return b.getTarget() == null && !b.downed() && !b.journey() && !b.isPassenger() && !b.sparring() && BotErrands.danger(b) == null
                && o != null && o.level() == b.level() && b.distanceToSqr(o) < 10 * 10;
    }

    /** Every second from the bot's tick. */
    public static void tick(PartyBotEntity b) {
        if (!(b.level() instanceof ServerLevel l)) return;
        ServerPlayer o = b.owner();
        Session s = PLAYING.get(b.getUUID());
        if (s != null) {
            boolean there = l.getBlockState(s.pos).is(Blocks.JUKEBOX);
            if (!there || l.getGameTime() >= s.ends || !quiet(b, o) || b.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(s.pos)) > 16 * 16) stop(b, l, s, there);
            return;
        }
        long now = l.getGameTime(), last = b.getPersistentData().getLong(LAST);
        if (last > 0 && now >= last && now - last < GAP) return;
        if (!quiet(b, o) || b.getRandom().nextInt(4) != 0 || !com.xenowarriors.modes.ModeRules.canGrief(l)) return;
        start(b, l, o, false);
    }

    /** Puts a song on (force: no waiting, for the tests). Returns what happened. */
    public static String start(PartyBotEntity b, ServerLevel l, ServerPlayer o, boolean force) {
        if (PLAYING.containsKey(b.getUUID())) return "already playing";
        int disc = find(b, x -> x.getItem() instanceof RecordItem);
        if (disc < 0) return "no disc";
        if (!jukebox(b)) return "no jukebox";
        BlockPos spot = null;
        BlockPos at = b.blockPosition();
        for (int r = 1; r <= 2 && spot == null; r++)
            for (int dx = -r; dx <= r && spot == null; dx++) for (int dz = -r; dz <= r && spot == null; dz++) {
                BlockPos p = at.offset(dx, 0, dz);
                if (p.equals(o.blockPosition()) || p.equals(o.blockPosition().above())) continue;
                if (l.getBlockState(p).canBeReplaced() && !l.getBlockState(p).liquid() && l.getBlockState(p.below()).isFaceSturdy(l, p.below(), net.minecraft.core.Direction.UP)
                        && l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(p)).isEmpty()) spot = p;
            }
        if (spot == null) return "no room";
        b.getPersistentData().putLong(LAST, l.getGameTime());
        take(b, x -> x.is(Items.JUKEBOX), 1);
        ItemStack record = b.inventory.getItem(disc).split(1);
        l.setBlockAndUpdate(spot, Blocks.JUKEBOX.defaultBlockState());
        if (l.getBlockEntity(spot) instanceof JukeboxBlockEntity jb) {
            jb.setFirstItem(record.copy());
            l.setBlock(spot, l.getBlockState(spot).setValue(JukeboxBlock.HAS_RECORD, true), 2);
        }
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        b.getLookControl().setLookAt(net.minecraft.world.phys.Vec3.atCenterOf(spot));
        int len = record.getItem() instanceof RecordItem ri ? ri.getLengthInTicks() : 20 * 120;
        PLAYING.put(b.getUUID(), new Session(spot, record, l.getGameTime() + len + 40));
        played++;
        o.displayClientMessage(Component.empty().append(b.label()).append(" puts on some music: ").append(record.getHoverName().copy().withStyle(ChatFormatting.LIGHT_PURPLE)), true);
        return "playing at " + spot.toShortString();
    }

    private static void stop(PartyBotEntity b, ServerLevel l, Session s, boolean there) {
        PLAYING.remove(b.getUUID());
        if (there) {
            if (l.getBlockEntity(s.pos) instanceof JukeboxBlockEntity jb) jb.clearContent();             // (stops the song, no disc popping out)
            l.levelEvent(1011, s.pos, 0);                                                                // (the music stops for everyone)
            l.removeBlock(s.pos, false);
            b.inventory.addItem(new ItemStack(Items.JUKEBOX));
            b.inventory.addItem(s.disc.copy());
        }
    }

    /** A bot is going away (dismissed, gone): pack the music up. */
    public static void forget(PartyBotEntity b) {
        Session s = PLAYING.get(b.getUUID());
        if (s != null && b.level() instanceof ServerLevel l) stop(b, l, s, l.getBlockState(s.pos).is(Blocks.JUKEBOX));
    }

    /** (Tests) */
    public static boolean playingForTest(PartyBotEntity b) { return PLAYING.containsKey(b.getUUID()); }
    public static void endForTest(PartyBotEntity b) {
        Session s = PLAYING.get(b.getUUID());
        if (s != null && b.level() instanceof ServerLevel l) stop(b, l, s, l.getBlockState(s.pos).is(Blocks.JUKEBOX));
    }
}
