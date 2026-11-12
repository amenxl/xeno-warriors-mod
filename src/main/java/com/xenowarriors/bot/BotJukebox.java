package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Music time - one jukebox for the whole team:
 * <ul>
 *   <li>Only one bot sets a jukebox down (one with a jukebox in its bag, or what it's made of: 8 planks and a
 *       diamond). From then on any bot near it with a disc can put a song on - one song at a time, never two at once.</li>
 *   <li>A song plays once (no looping): when it's over the disc goes back to whoever put it on, there's a short pause,
 *       and maybe someone puts on another (a different one if they can). Nobody does for a while: the bot that set it
 *       down packs it up. A fight, or you wandering off, packs it up straight away.</li>
 *   <li>A song you haven't heard before (from your bots' jukebox, or a disc you put in a jukebox near them) brings the
 *       whole team closer: bond with each bot listening, and between the bots.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotJukebox {
    private BotJukebox() {}

    /** Ticks between two jukeboxes the team sets down by itself (about 8 minutes, give or take). */
    public static final int GAP = 20 * 60 * 8;
    /** The pause after a song before the next one may go on; how long it waits for one before it's packed up. */
    public static final int PAUSE = 20 * 15, PACK_UP = 20 * 75;
    public static final float NEW_SONG_XP = 15F;
    private static final String LAST = "xw_jukebox_team_at", HEARD = "xw_heard_songs";

    /** The team's jukebox. */
    private static final class Box {
        BlockPos pos;
        UUID placer, dj;
        ItemStack disc = ItemStack.EMPTY;
        boolean lent;
        long songEnds, idleSince, nextSong;
        String lastSong = "";
        boolean songOn() { return !disc.isEmpty(); }
    }
    private static final Map<UUID, Box> BOXES = new HashMap<>();   // by owner
    /** (Tests) songs played; new songs heard; jukeboxes set down. */
    public static int played, newSongs, placed;

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

    private static String id(ItemStack s) { var k = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()); return k == null ? "" : k.toString(); }

    private static PartyBotEntity bot(ServerLevel l, UUID u) { return u != null && l.getEntity(u) instanceof PartyBotEntity b ? b : null; }

    // ---------------------------------------------------------------- every second, per bot

    public static void tick(PartyBotEntity b) {
        if (!(b.level() instanceof ServerLevel l)) return;
        ServerPlayer o = b.owner();
        if (o == null) return;
        if (radioTick(b, l, o)) return;                                              // (riding with you: the vehicle's music)
        Box box = BOXES.get(o.getUUID());
        long now = l.getGameTime();
        if (box != null) {
            if (!b.getUUID().equals(box.placer) && !b.getUUID().equals(box.dj) && !box.songOn() && now >= box.nextSong && quiet(b, o)
                    && b.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(box.pos)) < 12 * 12 && b.getRandom().nextInt(3) == 0) playNext(b, l, o, box);   // (someone else's turn)
            else if (b.getUUID().equals(box.placer)) placerTick(b, l, o, box, now);
            return;
        }
        long last = o.getPersistentData().getLong(LAST);
        if (last > 0 && now >= last && now - last < GAP) return;
        if (radioOn(o) || b.isPassenger()) return;
        if (!quiet(b, o) || b.getRandom().nextInt(4) != 0 || !com.xenowarriors.modes.ModeRules.canGrief(l)) return;
        if (BotSpar.event(o) != null && !BotPartyTime.active(o)) return;            // (not over a picnic, a camp...)
        start(b, l, o, false);
    }

    /** The bot that set it down looks after it: the song's end, the next one (its own), packing up. */
    private static void placerTick(PartyBotEntity b, ServerLevel l, ServerPlayer o, Box box, long now) {
        boolean there = l.getBlockState(box.pos).is(Blocks.JUKEBOX);
        boolean away = b.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(box.pos)) > 16 * 16 || o.level() != l || o.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(box.pos)) > 24 * 24;
        if (!there || away || !BotSpar.quiet(o) || b.downed()) { packUp(o); return; }
        if (box.songOn() && now >= box.songEnds) endSong(l, box);
        if (!box.songOn() && now >= box.nextSong && quiet(b, o) && b.getRandom().nextInt(5) == 0 && playNext(b, l, o, box)) return;
        if (!box.songOn() && now - box.idleSince > PACK_UP && !BotPartyTime.active(o)) packUp(o);   // (nobody's put anything on for a while)
    }

    // ---------------------------------------------------------------- the vehicle's music

    /** A song playing in a vehicle (the sound rides along with it). */
    private static final class Radio { int vehicle; UUID dj; String song = ""; net.minecraft.resources.ResourceLocation sound; long ends; }
    private static final Map<UUID, Radio> RADIOS = new HashMap<>();   // by owner
    public static final int RADIO_GAP = 20 * 60 * 2;
    private static final String RADIO_AT = "xw_radio_at";
    /** (Tests) songs played in vehicles. */
    public static int radioSongs;

    private static net.minecraft.world.entity.Entity ride(LivingEntity e) {
        var v = e.getVehicle();
        return v instanceof com.xenowarriors.vehicle.VehicleEntity || v instanceof net.minecraft.world.entity.vehicle.Boat ? v : null;
    }

    /**
     * Riding along with you (you in a vehicle too, close by): a bot with a disc in its bag now and then puts it on -
     * it plays from its vehicle and goes with it. One song at a time; it stops when you get out, a fight starts or the
     * song is over. True while this bot's song is on.
     */
    private static boolean radioTick(PartyBotEntity b, ServerLevel l, ServerPlayer o) {
        Radio r = RADIOS.get(o.getUUID());
        long now = l.getGameTime();
        if (r != null) {
            var v = l.getEntity(r.vehicle);
            boolean mine = b.getUUID().equals(r.dj);
            if (!mine) { if (bot(l, r.dj) == null) stopRadio(o, l); return false; }   // (the one playing it is gone)
            boolean ok = v != null && v.isAlive() && b.getVehicle() == v && ride(o) != null && o.distanceTo(v) < 24 && BotSpar.quiet(o) && !b.downed();
            if (!ok || now >= r.ends) { stopRadio(o, l); return false; }
            return true;
        }
        var v = ride(b);
        if (v == null || ride(o) == null || o.distanceTo(v) > 24 || songOn(o) || !BotSpar.quiet(o) || b.getTarget() != null) return false;
        long last = o.getPersistentData().getLong(RADIO_AT);
        if (last > 0 && now >= last && now - last < RADIO_GAP) return false;
        if (b.getRandom().nextInt(6) != 0) return false;
        int d = discSlot(b, o.getPersistentData().getString("xw_radio_last"));
        if (d < 0 || !(b.inventory.getItem(d).getItem() instanceof RecordItem rec)) return false;
        ItemStack disc = b.inventory.getItem(d);
        r = new Radio();
        r.vehicle = v.getId(); r.dj = b.getUUID(); r.song = id(disc);
        r.sound = rec.getSound().getLocation();
        r.ends = now + rec.getLengthInTicks() + 20;
        RADIOS.put(o.getUUID(), r);
        o.getPersistentData().putLong(RADIO_AT, now);
        o.getPersistentData().putString("xw_radio_last", r.song);
        l.playSound(null, v, rec.getSound(), net.minecraft.sounds.SoundSource.RECORDS, 2.5F, 1F);   // (bound to the vehicle: it moves with it)
        radioSongs++;
        o.displayClientMessage(Component.empty().append(b.label()).append(" puts a song on for the ride: ").append(disc.getHoverName().copy().withStyle(ChatFormatting.LIGHT_PURPLE)), true);
        heard(o, disc, v.blockPosition());
        return true;
    }

    private static void stopRadio(ServerPlayer o, ServerLevel l) {
        Radio r = RADIOS.remove(o.getUUID());
        if (r == null) return;
        var stop = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(r.sound, net.minecraft.sounds.SoundSource.RECORDS);
        var v = l.getEntity(r.vehicle);
        for (ServerPlayer p : l.players()) if (v == null || p.distanceTo(v) < 96) p.connection.send(stop);
        o.getPersistentData().putLong(RADIO_AT, l.getGameTime());
    }

    /** A song is playing in a vehicle for this team. */
    public static boolean radioOn(ServerPlayer o) { return RADIOS.containsKey(o.getUUID()); }

    // ---------------------------------------------------------------- songs

    /** The Xeno Warriors songs (this mod's music discs). */
    public static List<net.minecraft.world.item.Item> xwDiscs() {
        List<net.minecraft.world.item.Item> l = new ArrayList<>();
        for (var e : net.minecraftforge.registries.ForgeRegistries.ITEMS.getEntries())
            if (e.getKey().location().getNamespace().equals(XenoWarriors.MOD_ID) && e.getValue() instanceof RecordItem) l.add(e.getValue());
        return l;
    }

    private static boolean xw(ItemStack s) {
        return s.getItem() instanceof RecordItem && XenoWarriors.MOD_ID.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getNamespace());
    }

    /** Has (or can make) a jukebox, or the team's is already down: it can play at a party. */
    public static boolean canPlay(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        if (o != null && BOXES.containsKey(o.getUUID())) return true;
        return find(b, s -> s.is(Items.JUKEBOX)) >= 0 || count(b, s -> s.is(net.minecraft.tags.ItemTags.PLANKS)) >= 8 && count(b, s -> s.is(Items.DIAMOND)) >= 1;
    }

    /** A song from its bag (a different one from the last if it can): slot, or -1. */
    private static int discSlot(PartyBotEntity b, String not) {
        int any = -1;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (!(s.getItem() instanceof RecordItem)) continue;
            if (!id(s).equals(not)) return i;
            any = i;
        }
        return any;
    }

    /** Puts a song on in the team's jukebox: one of its discs. True if it did. */
    private static boolean playNext(PartyBotEntity b, ServerLevel l, ServerPlayer o, Box box) {
        if (box.songOn()) return false;
        int d = discSlot(b, box.lastSong);
        if (d < 0) return false;
        play(b, l, o, box, b.inventory.getItem(d).split(1), false);
        return true;
    }

    private static void play(PartyBotEntity b, ServerLevel l, ServerPlayer o, Box box, ItemStack record, boolean lent) {
        if (l.getBlockEntity(box.pos) instanceof JukeboxBlockEntity jb) {
            jb.setFirstItem(record.copy());
            l.setBlock(box.pos, l.getBlockState(box.pos).setValue(JukeboxBlock.HAS_RECORD, true), 2);
        }
        int len = record.getItem() instanceof RecordItem ri ? ri.getLengthInTicks() : 20 * 120;
        box.disc = record; box.lent = lent; box.dj = b.getUUID();
        box.songEnds = l.getGameTime() + len + 20;
        box.lastSong = id(record);
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        b.getLookControl().setLookAt(net.minecraft.world.phys.Vec3.atCenterOf(box.pos));
        played++;
        o.displayClientMessage(Component.empty().append(b.label()).append(" puts on some music: ").append(record.getHoverName().copy().withStyle(ChatFormatting.LIGHT_PURPLE)), true);
        heard(o, record, box.pos);
    }

    /** The song's over: it stops (once - no looping) and the disc goes back to whoever put it on. */
    private static void endSong(ServerLevel l, Box box) {
        if (l.getBlockEntity(box.pos) instanceof JukeboxBlockEntity jb) jb.clearContent();   // (no disc popping out)
        if (l.getBlockState(box.pos).is(Blocks.JUKEBOX)) l.setBlock(box.pos, l.getBlockState(box.pos).setValue(JukeboxBlock.HAS_RECORD, false), 2);
        l.levelEvent(1011, box.pos, 0);                                           // (the music stops for everyone)
        PartyBotEntity dj = bot(l, box.dj);
        if (!box.lent && !box.disc.isEmpty()) {
            if (dj != null) dj.inventory.addItem(box.disc.copy());
            else net.minecraft.world.Containers.dropItemStack(l, box.pos.getX() + 0.5, box.pos.getY() + 1, box.pos.getZ() + 0.5, box.disc.copy());
        }
        box.disc = ItemStack.EMPTY; box.dj = null;
        box.idleSince = l.getGameTime();
        box.nextSong = l.getGameTime() + PAUSE;
    }

    /** The jukebox goes back in its bag (the song stops, the disc goes home). */
    public static void packUp(ServerPlayer o) {
        Box box = BOXES.remove(o.getUUID());
        if (box == null) return;
        ServerLevel l = o.serverLevel();
        PartyBotEntity placer = bot(l, box.placer);
        ServerLevel bl = placer != null && placer.level() instanceof ServerLevel x ? x : l;
        if (box.songOn()) endSong(bl, box);
        if (bl.getBlockState(box.pos).is(Blocks.JUKEBOX)) {
            bl.removeBlock(box.pos, false);
            if (placer != null) placer.inventory.addItem(new ItemStack(Items.JUKEBOX));
            else net.minecraft.world.Containers.dropItemStack(bl, box.pos.getX() + 0.5, box.pos.getY() + 0.5, box.pos.getZ() + 0.5, new ItemStack(Items.JUKEBOX));
        }
        o.getPersistentData().putLong(LAST, l.getGameTime());
    }

    // ---------------------------------------------------------------- setting it down

    /**
     * A party: the team's jukebox plays Xeno Warriors music - one of its own Xeno Warriors discs, or (none of those)
     * one of the songs anyway, just for the party. Sets one down if there's none. Returns what happened ("playing ..." when it did).
     */
    public static String startParty(PartyBotEntity b, ServerLevel l, ServerPlayer o) {
        Box box = BOXES.get(o.getUUID());
        if (box != null && box.songOn()) return "playing already (the team's jukebox)";
        int own = find(b, BotJukebox::xw);
        List<net.minecraft.world.item.Item> songs = xwDiscs();
        if (own < 0 && songs.isEmpty()) return "no songs";
        ItemStack disc = own >= 0 ? b.inventory.getItem(own).split(1) : new ItemStack(songs.get(l.random.nextInt(songs.size())));
        if (box != null) { play(b, l, o, box, disc, own < 0); return "playing in the team's jukebox"; }
        if (!jukebox(b)) { if (own >= 0) b.inventory.addItem(disc); return "no jukebox"; }
        return place(b, l, o, disc, own < 0);
    }

    /** Sets the team's jukebox down and puts a song on (force: no waiting, for the tests). Returns what happened. */
    public static String start(PartyBotEntity b, ServerLevel l, ServerPlayer o, boolean force) {
        if (BOXES.containsKey(o.getUUID())) return "the team's jukebox is already down";
        int disc = find(b, x -> x.getItem() instanceof RecordItem);
        if (disc < 0) return "no disc";
        if (!jukebox(b)) return "no jukebox";
        return place(b, l, o, b.inventory.getItem(disc).split(1), false);
    }

    private static String place(PartyBotEntity b, ServerLevel l, ServerPlayer o, ItemStack record, boolean lent) {
        BlockPos spot = null;
        BlockPos at = b.blockPosition();
        for (int r = 1; r <= 2 && spot == null; r++)
            for (int dx = -r; dx <= r && spot == null; dx++) for (int dz = -r; dz <= r && spot == null; dz++) {
                BlockPos p = at.offset(dx, 0, dz);
                if (p.equals(o.blockPosition()) || p.equals(o.blockPosition().above())) continue;
                if (l.getBlockState(p).canBeReplaced() && !l.getBlockState(p).liquid() && l.getBlockState(p.below()).isFaceSturdy(l, p.below(), net.minecraft.core.Direction.UP)
                        && l.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(p)).isEmpty()) spot = p;
            }
        if (spot == null) { if (!lent) b.inventory.addItem(record); return "no room"; }
        take(b, x -> x.is(Items.JUKEBOX), 1);
        l.setBlockAndUpdate(spot, Blocks.JUKEBOX.defaultBlockState());
        Box box = new Box();
        box.pos = spot; box.placer = b.getUUID(); box.idleSince = l.getGameTime();
        BOXES.put(o.getUUID(), box);
        placed++;
        play(b, l, o, box, record, lent);
        return "playing at " + spot.toShortString();
    }

    // ---------------------------------------------------------------- new songs bring the team closer

    /** A song played near the team: the first time you hear it, everyone listening gets closer. */
    private static void heard(ServerPlayer o, ItemStack record, BlockPos at) {
        var pers = o.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        ListTag list = pers.getList(HEARD, 8);
        String song = id(record);
        for (int i = 0; i < list.size(); i++) if (list.getString(i).equals(song)) return;
        list.add(StringTag.valueOf(song));
        pers.put(HEARD, list);
        o.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        List<PartyBotEntity> near = new ArrayList<>();
        for (PartyBotEntity b : PartyBots.bots(o)) if (b.level() == o.level() && b.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at)) < 24 * 24 && !b.downed()) near.add(b);
        if (near.isEmpty() || o.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at)) > 32 * 32) return;
        newSongs++;
        for (PartyBotEntity b : near) {
            com.xenowarriors.party.Bonds.addXp(o, b.character().getId(), NEW_SONG_XP);
            for (PartyBotEntity c : near) if (c != b) com.xenowarriors.party.Bonds.addXp(b, c.character().getId(), NEW_SONG_XP);
            if (b.level() instanceof ServerLevel l) l.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, b.getX(), b.getEyeY() + 0.6, b.getZ(), 2, 0.3, 0.1, 0.3, 0.5);
        }
        o.displayClientMessage(Component.literal("A song you've never heard before - everyone listening gets a little closer (bond +)").withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    /** You put a disc in a jukebox near your bots: a new song counts too. */
    @SubscribeEvent
    public static void onUseJukebox(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        ItemStack s = e.getItemStack();
        if (!(s.getItem() instanceof RecordItem) || !e.getLevel().getBlockState(e.getPos()).is(Blocks.JUKEBOX) || e.getLevel().getBlockState(e.getPos()).getValue(JukeboxBlock.HAS_RECORD)) return;
        heard(p, s, e.getPos());
    }

    // ---------------------------------------------------------------- others asking

    /** The team's jukebox is playing a song right now. */
    public static boolean songOn(ServerPlayer o) { Box b = BOXES.get(o.getUUID()); return b != null && b.songOn(); }

    /** This bot's song is on right now (it put it on). */
    public static boolean playing(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        Box box = o == null ? null : BOXES.get(o.getUUID());
        return box != null && box.songOn() && b.getUUID().equals(box.dj);
    }

    /** A bot is going away (dismissed, gone): its song stops; the one that set the jukebox down packs it up. */
    public static void forget(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        if (o == null || !(b.level() instanceof ServerLevel l)) return;
        Box box = BOXES.get(o.getUUID());
        if (box == null) return;
        if (b.getUUID().equals(box.placer)) packUp(o);
        else if (b.getUUID().equals(box.dj) && box.songOn()) endSong(l, box);
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { packUp(p); stopRadio(p, p.serverLevel()); }
    }

    /** (Tests) */
    public static boolean playingForTest(PartyBotEntity b) { ServerPlayer o = b.owner(); return o != null && BOXES.containsKey(o.getUUID()); }
    public static void endForTest(PartyBotEntity b) { ServerPlayer o = b.owner(); if (o != null) packUp(o); }
    /** (Tests) the song ends now; the pause is skipped. */
    public static void songOverForTest(ServerPlayer o) { Box box = BOXES.get(o.getUUID()); if (box != null && box.songOn()) { endSong(o.serverLevel(), box); box.nextSong = 0; } }
    /** (Tests) a ride song now, no dice or wait. */
    public static boolean radioForTest(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        if (o == null || !(b.level() instanceof ServerLevel l)) return false;
        o.getPersistentData().remove(RADIO_AT);
        for (int i = 0; i < 200 && !RADIOS.containsKey(o.getUUID()); i++) radioTick(b, l, o);
        return RADIOS.containsKey(o.getUUID());
    }
    public static void radioStopForTest(ServerPlayer o) { stopRadio(o, o.serverLevel()); }

    /** (Tests) jukeboxes standing for this team (0 or 1). */
    public static int boxesForTest(ServerPlayer o) { return BOXES.containsKey(o.getUUID()) ? 1 : 0; }
}
