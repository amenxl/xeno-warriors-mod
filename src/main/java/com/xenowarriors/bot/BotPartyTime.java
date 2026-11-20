package com.xenowarriors.bot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PartyHatsPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Party time! Now and then (rarely - at most every 40 minutes), when nobody's fighting, one of your bots asks to throw
 * a party (a request card). Say yes and for 3 minutes:
 * <ul>
 *   <li>everyone there - you, party members nearby and your bots - wears a party hat;</li>
 *   <li>confetti rains down and fireworks go up now and then;</li>
 *   <li>cakes are set out - one, and another for every two more guests (up to 6) - taken away again at the end if
 *       nobody finished them; everyone gets a snack and Regeneration;</li>
 *   <li>the closer you are with the bots there (your average bond), the more barrels of party supplies - extra food,
 *       drinks, fireworks, and better things the higher it is (1 barrel from bond 20, 2 from 45, 3 from 70);</li>
 *   <li>sometimes party crashers turn up (or whatever starts trouble is called that): beat them all within a minute
 *       and every bit of bond from the party counts double; fail and the party breaks up;</li>
 *   <li>a little party tune plays and the bots dance about - hops, spins and arm-waves;</li>
 *   <li>bonds grow: you with each bot and the bots with each other, a bit every 20 seconds and a good chunk at the end.</li>
 * </ul>
 * A fight starting ends it early (no end bonus then).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotPartyTime {
    private BotPartyTime() {}

    public static final int LENGTH = 20 * 60 * 3, GAP = 20 * 60 * 40, ASK_TICKS = 20 * 20;
    /** A party breaks up after this long of real trouble in a row, or of you being too far away. */
    public static final int TROUBLE_TICKS = 20 * 3, AWAY_TICKS = 20 * 10;
    public static final float BOND_TICK = 5F, BOND_END = 60F;
    /** A party that breaks up early: this share of the end bond, by how far it got. */
    public static final float EARLY_SHARE = 0.5F;
    private static final String LAST = "xw_party_time_at";
    /** pentatonic steps for the tune (a random, cheerful little melody - nothing in particular) */
    private static final float[] NOTES = {0.5F, 0.561F, 0.63F, 0.749F, 0.841F, 1.0F, 1.122F, 1.26F, 1.498F, 1.682F, 2.0F};

    private static final class Party {
        long end; BlockPos center; int note; final Set<UUID> guests = new HashSet<>();
        final List<BlockPos> cakes = new ArrayList<>(), barrels = new ArrayList<>();
        /** party crashers: who they are, the time limit to beat them, when (if) they'll turn up; bond given so far; doubled */
        final Set<UUID> crashers = new HashSet<>(); long crashEnd, crashAt; float given; boolean doubled, crashedOnce;
        /** ticks of real trouble in a row (a party only breaks up after a few seconds of it), and of you being away */
        int trouble, away;
        /** the bot playing its jukebox (null: none), the beatboxers (Garry / Stevie bots, when there's no music) */
        UUID dj; final List<UUID> beatbox = new ArrayList<>();
        /** rap battles still to come (pairs of character ids), the one going on, its next line and when */
        final ArrayDeque<String[]> battles = new ArrayDeque<>(); String[] battle; int line; long nextLine;
    }

    /** Garry or Stevie there (as a bot or played by someone): everyone enjoys the music and the rap battles - more bond. */
    public static final float RAPPERS_BONUS = 1.5F;
    /** (Tests) parties with a jukebox / beatboxing, rap battles held. */
    public static int jukeboxParties, beatboxParties, rapBattles;

    /** Who's at the party by character (bots and players). */
    private static Map<String, LivingEntity> byChar(List<LivingEntity> gs) {
        Map<String, LivingEntity> m = new LinkedHashMap<>();
        for (LivingEntity g : gs) {
            String c = g instanceof PartyBotEntity b ? b.charKey() : com.xenowarriors.party.Bonds.charOf(g);
            if (c != null) m.putIfAbsent(c, g);
        }
        return m;
    }

    private static boolean rappers(List<LivingEntity> gs) { Map<String, LivingEntity> m = byChar(gs); return m.containsKey("garry") || m.containsKey("stevie"); }

    /** Lines for the rap battles (original): each character's own boasts. */
    private static final Map<String, String[]> BARS = Map.of(
            "garry", new String[]{"Mic in my hand and the beat in my chest - step to the Dojo, you're gonna need rest!",
                    "I drop one bar and the whole room bounces - you drop one bar and the crowd announces: nope!",
                    "Beatbox king, every snare is a strike - your flow's so slow it needs training wheels and a bike!"},
            "stevie", new String[]{"Little brother, big talk, but the mic don't lie - I wrote the rhymes that you're borrowing, why?",
                    "Karma Blast flow, I light up the stage - you're still on page one, I'm turning the page!",
                    "Disco lights on me, the crowd knows the name - you copy my moves, but it's never the same!"},
            "mikiko", new String[]{"Hekublade sharp and my rhymes cut deeper - your spells are cheap, mine come with a keeper!",
                    "Fox fire burning, I'm hot on the beat - Zane, take a seat, this flame can't be beat!",
                    "Orenji pride and my karma's on fire - your broom is a joke, I'm a whole different flier!"},
            "zane", new String[]{"Mana in my verses, I cast and I curse - you swing a big sword, but your bars got worse!",
                    "Fire, ice, thunder - pick one, I've got three - Mikiko, you're a warm-up act for me!",
                    "Past mages behind me, they're nodding along - you lost this battle the moment I started my song!"});

    private static final String[][] PAIRS = {{"garry", "stevie"}, {"mikiko", "zane"}};
    private static final Map<UUID, Party> PARTIES = new HashMap<>();
    /** (Tests) parties asked for / thrown / finished; fireworks launched. */
    public static int asked, thrown, finished, fireworks;

    public static boolean active(ServerPlayer o) { return PARTIES.containsKey(o.getUUID()); }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer o) || o instanceof FakePlayer) return;
        Party pt = PARTIES.get(o.getUUID());
        if (pt != null) { partyTick(o, pt); return; }
        if (o.tickCount % 200 != 157) return;
        long now = o.level().getGameTime(), last = o.getPersistentData().getLong(LAST);
        if (last > 0 && now >= last && now - last < GAP) return;
        if (o.getRandom().nextInt(12) != 0 || !BotSpar.quiet(o) || o.isSpectator() || o.getVehicle() != null) return;
        List<PartyBotEntity> bots = new ArrayList<>();
        for (PartyBotEntity b : PartyBots.bots(o)) if (!b.downed() && !b.journey() && b.getTarget() == null && b.distanceToSqr(o) < 16 * 16) bots.add(b);
        if (bots.isEmpty()) return;
        ask(bots.get(o.getRandom().nextInt(bots.size())), o);
        o.getPersistentData().putLong(LAST, now);
    }

    public static void ask(PartyBotEntity b, ServerPlayer o) {
        asked++;
        BotRequests.send(o, new com.xenowarriors.network.RequestPopupPacket(b.character().getDisplayName().getString() + " wants to throw a party!",
                "Party hats, cake, confetti and fireworks - and everyone gets closer.", "/xenoparty yes " + b.getId(), "/xenoparty no " + b.getId(), ASK_TICKS, 0xFFFF6FD8));
        o.displayClientMessage(Component.empty().append(b.label()).append(": \"Hey - how about a party?\"").withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    /** Starts the party around its player. */
    public static boolean start(ServerPlayer o) {
        if (PARTIES.containsKey(o.getUUID())) return false;
        Party pt = new Party();
        pt.end = o.level().getGameTime() + LENGTH;
        pt.center = o.blockPosition();
        ServerLevel l = o.serverLevel();
        List<LivingEntity> first0 = guests(o, pt);
        if (com.xenowarriors.modes.ModeRules.canGrief(l)) {                   // the cakes (more for a bigger party), the barrels (closer bonds)
            int nCakes = cakesFor(first0.size() + 1), nBarrels = barrelsFor(avgBond(o, first0));
            for (int ring = 1; ring <= 3 && (pt.cakes.size() < nCakes || pt.barrels.size() < nBarrels); ring++)
                for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    BlockPos p = pt.center.offset(dx, 0, dz);
                    if (!l.getBlockState(p).isAir() || !l.getBlockState(p.below()).isFaceSturdy(l, p.below(), net.minecraft.core.Direction.UP)) continue;
                    if (pt.cakes.size() < nCakes && (ring == 1 || (dx + dz) % 2 == 0)) { l.setBlockAndUpdate(p, Blocks.CAKE.defaultBlockState()); pt.cakes.add(p); }
                    else if (pt.barrels.size() < nBarrels && ring >= 2) { l.setBlockAndUpdate(p, Blocks.BARREL.defaultBlockState()); fillBarrel(l, p, avgBond(o, first0), pt.barrels.size()); pt.barrels.add(p); }
                }
            cakesSet += pt.cakes.size(); barrelsSet += pt.barrels.size();
            if (!pt.barrels.isEmpty()) o.sendSystemMessage(Component.literal("Your bots brought " + (pt.barrels.size() == 1 ? "a barrel" : pt.barrels.size() + " barrels") + " of party supplies!").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (l.random.nextFloat() < CRASH_CHANCE) pt.crashAt = l.getGameTime() + 20 * (40 + l.random.nextInt(80));   // (party crashers on the way)
        PARTIES.put(o.getUUID(), pt);
        BotRps.partyStarted(o, o.level().getGameTime());
        BotPartyGames.partyStarted(o, o.level().getGameTime());              // (arm wrestling, Memory)                        // (rock, paper, scissors: at least one game every party)
        thrown++;
        List<LivingEntity> first = guests(o, pt);
        for (LivingEntity g : first)                                          // a bot with a jukebox: Xeno Warriors music
            if (g instanceof PartyBotEntity b && com.xenowarriors.modes.ModeRules.canGrief(l) && BotJukebox.canPlay(b) && BotJukebox.startParty(b, l, o).startsWith("playing")) {
                pt.dj = b.getUUID(); jukeboxParties++;
                o.sendSystemMessage(Component.empty().append(b.label()).append(" sets up a jukebox - Xeno Warriors music!").withStyle(ChatFormatting.LIGHT_PURPLE));
                break;
            }
        if (pt.dj == null) for (LivingEntity g : first)                        // no music: Garry and Stevie beatbox it
            if (g instanceof PartyBotEntity b && (b.character() == com.xenowarriors.character.CharacterType.GARRY || b.character() == com.xenowarriors.character.CharacterType.STEVIE)) pt.beatbox.add(b.getUUID());
        if (!pt.beatbox.isEmpty()) { beatboxParties++; o.sendSystemMessage(Component.literal("No music? No problem - the beatboxing starts!").withStyle(ChatFormatting.LIGHT_PURPLE)); }
        Map<String, LivingEntity> who = byChar(first);
        for (String[] pair : PAIRS) if (who.containsKey(pair[0]) && who.containsKey(pair[1])) pt.battles.add(pair);
        if (!pt.battles.isEmpty()) pt.nextLine = l.getGameTime() + 20 * 40;    // (the first rap battle, 40 s in)
        for (LivingEntity g : guests(o, pt)) {
            g.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 60, 0, false, true));
            if (g instanceof ServerPlayer gp) gp.getInventory().add(new ItemStack(Items.COOKIE, 4));
        }
        for (ServerPlayer p : l.players()) if (p.distanceToSqr(o) < 40 * 40)
            p.displayClientMessage(Component.literal("Party time! Hats on, everyone!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
        l.playSound(null, pt.center, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1F, 1.2F);
        firework(l, pt, 3);
        return true;
    }

    /** Everyone at the party: its player, party members close by and its player's bots close by. */
    private static List<LivingEntity> guests(ServerPlayer o, Party pt) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3 c = Vec3.atCenterOf(pt.center);
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(o)) if (m.level() == o.level() && m.position().distanceTo(c) < 24) out.add(m);
        for (PartyBotEntity b : PartyBots.bots(o)) if (b.level() == o.level() && !b.downed() && b.position().distanceTo(c) < 24) out.add(b);
        for (LivingEntity g : out) pt.guests.add(g.getUUID());
        return out;
    }

    private static void partyTick(ServerPlayer o, Party pt) {
        ServerLevel l = o.serverLevel();
        long now = l.getGameTime();
        if (crashTick(o, pt, l, now)) return;                                     // (party crashers: beat them in time)
        pt.trouble = BotSpar.trouble(o) ? pt.trouble + 1 : 0;                  // (a monster idling nearby, a pet, a bot's own scuffle: not a reason)
        if (pt.trouble == 1 && !pt.crashedOnce && crashers(o, pt, l, now)) pt.trouble = 0;   // (whoever's starting trouble: party crashers)
        if (!pt.crashers.isEmpty()) pt.trouble = 0;                             // (fighting the party crashers doesn't break the party up)
        boolean far = o.position().distanceTo(Vec3.atCenterOf(pt.center)) > 48;
        pt.away = far ? pt.away + 1 : 0;                                        // (a few seconds' slack: a knockback, a quick hop off)
        boolean fight = pt.trouble >= TROUBLE_TICKS;
        if (now >= pt.end || fight || !o.isAlive() || pt.away >= AWAY_TICKS) { end(o, pt, !fight && now >= pt.end); return; }
        List<LivingEntity> gs = guests(o, pt);
        if (o.tickCount % 20 == 0) {                                          // hats on (and the confetti) for everyone near
            int[] ids = gs.stream().mapToInt(LivingEntity::getId).toArray();
            int left = (int) (pt.end - now);
            for (ServerPlayer p : l.players()) if (p.distanceToSqr(o) < 64 * 64) ModNetwork.sendToPlayer(new PartyHatsPacket(ids, left, pt.center), p);
        }
        boolean music = BotJukebox.songOn(o);                                  // (the team's jukebox: one song at a time - nothing else plays over it)
        if (!music) pt.dj = null;
        boolean battling = battleTick(o, pt, gs, l, now);
        if (!music && (!pt.beatbox.isEmpty() || battling)) beat(o, pt, l);    // beatboxing (and under every rap battle) - not over a song
        else if (!music && o.tickCount % 8 == 0) {                     // the tune: a random cheerful melody
            float pitch = NOTES[Math.floorMod(pt.note + l.random.nextInt(5) - 2, NOTES.length)];
            pt.note = Math.floorMod(pt.note + l.random.nextInt(5) - 2, NOTES.length);
            l.playSound(null, pt.center, o.tickCount % 32 == 0 ? SoundEvents.NOTE_BLOCK_BASS.value() : (o.tickCount % 16 == 0 ? SoundEvents.NOTE_BLOCK_CHIME.value() : SoundEvents.NOTE_BLOCK_BELL.value()),
                    SoundSource.RECORDS, 0.5F, pitch);
        }
        if (l.random.nextInt(170) == 0) firework(l, pt, 1 + l.random.nextInt(2));
        for (LivingEntity g : gs) if (g instanceof PartyBotEntity b && o.tickCount % 10 == b.getId() % 10
                && !(pt.battle != null && (b.charKey().equals(pt.battle[0]) || b.charKey().equals(pt.battle[1])))) dance(b, pt);   // (the rappers stay put)
        if (o.tickCount % 400 == 0) partyBond(o, pt, gs, BOND_TICK * (rappers(gs) ? RAPPERS_BONUS : 1F));
        if (o.tickCount % 10 == 3) BotRps.tick(o, gs, now);
        if (o.tickCount % 10 == 7) BotPartyGames.tick(o, gs, now);
    }

    /** A beat: kick, hat, snare, hat (the beatboxers bob along). */
    private static void beat(ServerPlayer o, Party pt, ServerLevel l) {
        if (o.tickCount % 4 != 0) return;
        int step = (o.tickCount / 4) % 8;
        var sound = step % 4 == 0 ? SoundEvents.NOTE_BLOCK_BASEDRUM.value() : step % 4 == 2 ? SoundEvents.NOTE_BLOCK_SNARE.value() : SoundEvents.NOTE_BLOCK_HAT.value();
        BlockPos at = pt.center;
        for (UUID id : pt.beatbox) if (l.getEntity(id) instanceof PartyBotEntity b) { at = b.blockPosition(); if (step % 4 == 0) b.swing(net.minecraft.world.InteractionHand.MAIN_HAND); break; }
        l.playSound(null, at, sound, SoundSource.RECORDS, step % 4 == 0 ? 0.9F : 0.55F, step == 6 ? 1.3F : 1F);
    }

    /**
     * A rap battle (Garry vs Stevie, Mikiko vs Zane - when both are there): they square up in the middle and trade three
     * bars each over a beat, everyone else cheers, a winner, and everyone gets closer. True while one's on.
     */
    private static boolean battleTick(ServerPlayer o, Party pt, List<LivingEntity> gs, ServerLevel l, long now) {
        if (pt.battle == null) {
            if (pt.battles.isEmpty() || now < pt.nextLine || pt.end - now < 20 * 30) return false;
            pt.battle = pt.battles.poll(); pt.line = 0; pt.nextLine = now + 40;
            Map<String, LivingEntity> who = byChar(gs);
            LivingEntity a = who.get(pt.battle[0]), b = who.get(pt.battle[1]);
            if (a == null || b == null) { pt.battle = null; return false; }
            say(l, o, "RAP BATTLE! " + name(a) + " vs " + name(b) + "!", ChatFormatting.GOLD);
            rapBattles++;
            return true;
        }
        Map<String, LivingEntity> who = byChar(gs);
        LivingEntity a = who.get(pt.battle[0]), b = who.get(pt.battle[1]);
        if (a == null || b == null) { pt.battle = null; return false; }
        Vec3 c = Vec3.atBottomCenterOf(pt.center);                               // face to face in the middle
        face(a, b, c.add(-1.2, 0, 0)); face(b, a, c.add(1.2, 0, 0));
        if (now < pt.nextLine) return true;
        if (pt.line < 6) {
            LivingEntity mc = pt.line % 2 == 0 ? a : b;
            String who2 = pt.line % 2 == 0 ? pt.battle[0] : pt.battle[1];
            String[] bars = BARS.get(who2);
            say(l, o, name(mc) + ": \"" + bars[pt.line / 2] + "\"", ChatFormatting.AQUA);
            mc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            l.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, mc.getX(), mc.getY() + 2.2, mc.getZ(), 4, 0.4, 0.2, 0.4, 1);
            for (LivingEntity g : gs) if (g != a && g != b && g instanceof PartyBotEntity cb && cb.onGround() && l.random.nextBoolean()) cb.getJumpControl().jump();   // (the crowd)
            l.playSound(null, pt.center, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.25F, 1.6F + l.random.nextFloat() * 0.3F);
            pt.line++; pt.nextLine = now + 20 * 4;
            return true;
        }
        LivingEntity win = l.random.nextBoolean() ? a : b;                      // the crowd decides
        say(l, o, "The crowd goes wild - " + name(win) + " wins the rap battle!", ChatFormatting.GOLD);
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, win.getX(), win.getY() + 1, win.getZ(), 30, 0.5, 0.8, 0.5, 0.3);
        partyBond(o, pt, gs, BOND_TICK * 2);                                      // (everyone enjoyed that)
        String ca = pt.battle[0], cb2 = pt.battle[1];
        if (a instanceof PartyBotEntity ba) com.xenowarriors.party.Bonds.addXp(ba, cb2, BOND_TICK * 2);
        if (b instanceof PartyBotEntity bb) com.xenowarriors.party.Bonds.addXp(bb, ca, BOND_TICK * 2);
        pt.battle = null; pt.nextLine = now + 20 * 20;
        return false;
    }

    private static void face(LivingEntity who, LivingEntity other, Vec3 spot) {
        if (who instanceof PartyBotEntity b) {
            if (b.position().distanceTo(spot) > 0.8) b.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
            b.getLookControl().setLookAt(other, 30F, 30F);
        }
    }

    private static String name(LivingEntity e) { return e instanceof PartyBotEntity b ? b.character().getDisplayName().getString() : e.getName().getString(); }

    private static void say(ServerLevel l, ServerPlayer o, String s, ChatFormatting col) {
        for (ServerPlayer p : l.players()) if (p.distanceToSqr(o) < 48 * 48) p.sendSystemMessage(Component.literal(s).withStyle(col));
    }

    /** (Tests) */
    public static boolean djForTest(ServerPlayer o) { Party pt = PARTIES.get(o.getUUID()); return pt != null && pt.dj != null; }
    /** Where parties are beatboxing right now (Garry / Stevie, or the beat under a rap battle - no song on), in this level. */
    public static List<BlockPos> beatboxing(net.minecraft.server.level.ServerLevel l) {
        List<BlockPos> out = new ArrayList<>();
        for (var en : PARTIES.entrySet()) {
            ServerPlayer o = l.getServer().getPlayerList().getPlayer(en.getKey());
            Party pt = en.getValue();
            if (o == null || o.level() != l || pt.center == null || BotJukebox.songOn(o)) continue;
            if (!pt.beatbox.isEmpty() || pt.battle != null) out.add(pt.center);
        }
        return out;
    }

    public static int beatboxersForTest(ServerPlayer o) { Party pt = PARTIES.get(o.getUUID()); return pt == null ? -1 : pt.beatbox.size(); }
    public static void battleNowForTest(ServerPlayer o) { Party pt = PARTIES.get(o.getUUID()); if (pt != null) pt.nextLine = 0; }
    public static boolean battlingForTest(ServerPlayer o) { Party pt = PARTIES.get(o.getUUID()); return pt != null && pt.battle != null; }

    /** The bots dance about near the middle: hops, spins and arm-waves. */
    private static void dance(PartyBotEntity b, Party pt) {
        var r = b.getRandom();
        Vec3 c = Vec3.atBottomCenterOf(pt.center);
        if (b.position().distanceTo(c) > 5 && b.getNavigation().isDone()) {
            double a = r.nextDouble() * Math.PI * 2;
            b.getNavigation().moveTo(c.x + Math.cos(a) * 2.5, c.y, c.z + Math.sin(a) * 2.5, 1.0);
            return;
        }
        switch (r.nextInt(4)) {
            case 0 -> { if (b.onGround()) b.getJumpControl().jump(); }
            case 1 -> { b.setYRot(b.getYRot() + 90F); b.yBodyRot = b.getYRot(); b.setYHeadRot(b.getYRot()); }
            case 2 -> b.swing(r.nextBoolean() ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND);
            default -> b.getLookControl().setLookAt(c.x, c.y + 1.6, c.z);
        }
    }

    private static void firework(ServerLevel l, Party pt, int n) {
        var r = l.random;
        for (int i = 0; i < n; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            CompoundTag fw = new CompoundTag(), ex = new CompoundTag();
            ex.putByte("Type", (byte) r.nextInt(5));
            int[] pal = {0xFF4FA0, 0x40C0FF, 0xFFE040, 0x7CFF6A, 0xC070FF, 0xFFFFFF};
            ex.putIntArray("Colors", new int[]{pal[r.nextInt(pal.length)], pal[r.nextInt(pal.length)]});
            ex.putBoolean("Twinkle", r.nextBoolean());
            ListTag exs = new ListTag(); exs.add(ex);
            fw.put("Explosions", exs);
            fw.putByte("Flight", (byte) 2);
            rocket.getOrCreateTag().put("Fireworks", fw);
            double a = r.nextDouble() * Math.PI * 2, d = 5 + r.nextDouble() * 3;
            Vec3 at = Vec3.atBottomCenterOf(pt.center).add(Math.cos(a) * d, 0.5, Math.sin(a) * d);
            l.addFreshEntity(new FireworkRocketEntity(l, at.x, at.y, at.z, rocket));   // (it goes off high up - nobody gets hurt)
            fireworks++;
        }
    }

    // ------------------------------------------------------------------ cakes, barrels, party crashers

    public static final float CRASH_CHANCE = 0.3F;
    /** How long you have to beat the party crashers. */
    public static final int CRASH_TIME = 20 * 60;
    /** (Tests) cakes and barrels set out; crashes, crashes beaten. */
    public static int cakesSet, barrelsSet, crashes, crashesBeaten;

    /** One cake, and another for every two more guests (up to 6). */
    public static int cakesFor(int guests) { return Math.max(1, Math.min(6, 1 + (guests - 1) / 2)); }

    /** Barrels of supplies by the average bond: 1 from 20, 2 from 45, 3 from 70. */
    public static int barrelsFor(int bond) { return bond >= 70 ? 3 : bond >= 45 ? 2 : bond >= 20 ? 1 : 0; }

    /** Your average bond with the bots (and party members) at the party. */
    private static int avgBond(ServerPlayer o, List<LivingEntity> gs) {
        int n = 0, sum = 0;
        for (LivingEntity g : gs) if (g != o) { String c = g instanceof PartyBotEntity b ? b.charKey() : com.xenowarriors.party.Bonds.charOf(g); if (c == null) continue; sum += com.xenowarriors.party.Bonds.level(o, c); n++; }
        return n == 0 ? 0 : sum / n;
    }

    /** A barrel of party supplies: food and drink, fireworks - better things the closer everyone is. */
    private static void fillBarrel(ServerLevel l, BlockPos p, int bond, int which) {
        if (!(l.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity b)) return;
        var r = l.random;
        List<ItemStack> in = new ArrayList<>();
        in.add(new ItemStack(Items.BREAD, 4 + r.nextInt(5)));
        in.add(new ItemStack(Items.COOKIE, 8 + r.nextInt(9)));
        in.add(new ItemStack(Items.PUMPKIN_PIE, 2 + r.nextInt(3)));
        in.add(new ItemStack(r.nextBoolean() ? Items.COOKED_BEEF : Items.COOKED_CHICKEN, 4 + r.nextInt(4)));
        in.add(new ItemStack(Items.SWEET_BERRIES, 6 + r.nextInt(8)));
        in.add(new ItemStack(Items.HONEY_BOTTLE, 2 + r.nextInt(3)));
        in.add(new ItemStack(Items.MELON_SLICE, 6 + r.nextInt(6)));
        ItemStack rockets = new ItemStack(Items.FIREWORK_ROCKET, 6 + r.nextInt(6));
        in.add(rockets);
        if (bond >= 45) { in.add(new ItemStack(Items.GOLDEN_CARROT, 3 + r.nextInt(4))); in.add(new ItemStack(Items.CAKE)); in.add(new ItemStack(Items.GLOW_BERRIES, 6)); }
        if (bond >= 70) { in.add(new ItemStack(Items.GOLDEN_APPLE, 1 + r.nextInt(2))); in.add(new ItemStack(Items.EMERALD, 2 + r.nextInt(5))); in.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 3 + r.nextInt(4))); }
        if (bond >= 90 && which == 0) in.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
        int slot = 0;
        for (ItemStack it : in) { if (slot >= b.getContainerSize()) break; b.setItem(slot, it); slot += 1 + (r.nextInt(3) == 0 ? 1 : 0); }
        b.setCustomName(Component.literal("Party Supplies"));
        b.setChanged();
    }

    /** Whoever's starting trouble now is a party crasher (the clock starts). True if there were any. */
    private static boolean crashers(ServerPlayer o, Party pt, ServerLevel l, long now) {
        List<net.minecraft.world.entity.Mob> foes = l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, o.getBoundingBox().inflate(24), m -> m.isAlive() && !BotSpar.ours(o, m)
                && m instanceof net.minecraft.world.entity.monster.Enemy && m.getTarget() != null && (m.getTarget() == o || BotSpar.ours(o, m.getTarget())));
        if (foes.isEmpty()) return false;
        startCrash(o, pt, l, now, foes);
        return true;
    }

    private static void startCrash(ServerPlayer o, Party pt, ServerLevel l, long now, List<net.minecraft.world.entity.Mob> foes) {
        pt.crashedOnce = true; pt.crashAt = 0;
        for (var m : foes) {
            m.setCustomName(Component.literal("Party Crasher").withStyle(ChatFormatting.RED));
            m.setCustomNameVisible(true);
            m.addTag(CRASHER);
            pt.crashers.add(m.getUUID());
        }
        pt.crashEnd = now + CRASH_TIME;
        crashes++;
        say(l, o, "PARTY CRASHERS! Beat them all within a minute and the party's bond counts double!", ChatFormatting.RED);
        l.playSound(null, pt.center, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6F, 1.4F);
    }

    static final String CRASHER = "xw_party_crasher";

    /** Party crashers barge in: a gang of zombies, husks and skeletons from a little way off, coming for the host. */
    public static void crashNow(ServerPlayer o) {
        Party pt = PARTIES.get(o.getUUID());
        if (pt == null) return;
        ServerLevel l = o.serverLevel();
        List<net.minecraft.world.entity.Mob> foes = new ArrayList<>();
        int n = 3 + Math.min(5, pt.guests.size() / 2);
        for (int i = 0; i < n; i++) {
            net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type = switch (l.random.nextInt(3)) {
                case 0 -> net.minecraft.world.entity.EntityType.ZOMBIE; case 1 -> net.minecraft.world.entity.EntityType.HUSK; default -> net.minecraft.world.entity.EntityType.SKELETON; };
            net.minecraft.world.entity.Mob m = type.create(l);
            if (m == null) continue;
            double a = l.random.nextDouble() * Math.PI * 2, d = 12 + l.random.nextDouble() * 4;
            int x = (int) Math.floor(pt.center.getX() + Math.cos(a) * d), z = (int) Math.floor(pt.center.getZ() + Math.sin(a) * d);
            int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - pt.center.getY()) > 6) y = pt.center.getY();
            m.moveTo(x + 0.5, y, z + 0.5, l.random.nextFloat() * 360F, 0F);
            m.finalizeSpawn(l, l.getCurrentDifficultyAt(m.blockPosition()), net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
            if (m instanceof net.minecraft.world.entity.monster.Zombie z2) z2.setBaby(false);
            m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));   // (no burning by day, and a bit of a costume)
            m.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0F);
            l.addFreshEntity(m);
            m.setTarget(o);
            l.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, m.getX(), m.getY() + 1, m.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            foes.add(m);
        }
        if (!foes.isEmpty()) startCrash(o, pt, l, l.getGameTime(), foes);
    }

    /** Party crashers about: done when they're all beaten (in time: double bond) or the time runs out (the party's ruined). True while it's on. */
    private static boolean crashTick(ServerPlayer o, Party pt, ServerLevel l, long now) {
        if (pt.crashers.isEmpty()) {
            if (pt.crashAt > 0 && now >= pt.crashAt && !pt.crashedOnce && pt.end - now > 20 * 50) crashNow(o);
            return false;
        }
        pt.crashers.removeIf(id -> !(l.getEntity(id) instanceof LivingEntity m) || !m.isAlive() || m.distanceToSqr(o) > 64 * 64);
        if (pt.crashers.isEmpty()) {
            crashesBeaten++;
            pt.doubled = true;
            bondTick(o, guests(o, pt), pt.given);                                  // (everything so far, again - double from here on)
            say(l, o, "The party crashers are gone! Everyone pulled together - the party's bond counts double!", ChatFormatting.GOLD);
            firework(l, pt, 3);
            return false;
        }
        if (now >= pt.crashEnd) {
            say(l, o, "The party crashers ruined the party...", ChatFormatting.GRAY);
            pt.crashers.clear();
            end(o, pt, false);
            return true;
        }
        if (o.tickCount % 20 == 0) {
            int left = (int) ((pt.crashEnd - now) / 20);
            o.displayClientMessage(Component.literal("Party crashers: " + pt.crashers.size() + " left - " + left + " s").withStyle(ChatFormatting.RED), true);
        }
        return false;
    }

    /** (Tests) */
    public static int cakesForTest(ServerPlayer o) { Party pt = PARTIES.get(o.getUUID()); return pt == null ? -1 : pt.cakes.size(); }
    public static int barrelsForTest(ServerPlayer o) { Party pt = PARTIES.get(o.getUUID()); return pt == null ? -1 : pt.barrels.size(); }
    public static boolean doubledForTest(ServerPlayer o) { Party pt = PARTIES.get(o.getUUID()); return pt != null && pt.doubled; }
    public static int crashersForTest(ServerPlayer o) { Party pt = PARTIES.get(o.getUUID()); return pt == null ? -1 : pt.crashers.size(); }

    /** Bond for everyone at a party (doubled once the party crashers are seen off), and kept count of. */
    private static void partyBond(ServerPlayer o, Party pt, List<LivingEntity> gs, float xp) {
        float x = pt.doubled ? xp * 2 : xp;
        pt.given += xp;
        bondTick(o, gs, x);
    }

    private static void bondTick(ServerPlayer o, List<LivingEntity> gs, float xp) {
        List<PartyBotEntity> bots = new ArrayList<>();
        for (LivingEntity g : gs) if (g instanceof PartyBotEntity b) bots.add(b);
        for (PartyBotEntity b : bots) {
            com.xenowarriors.party.Bonds.addXp(o, b.charKey(), xp);
            for (PartyBotEntity b2 : bots) if (b2 != b) com.xenowarriors.party.Bonds.addXp(b, b2.charKey(), xp);
        }
        for (LivingEntity g : gs) if (g instanceof ServerPlayer m && m != o) {     // (party members: with the host's character and each other)
            com.xenowarriors.party.Bonds.addXp(m, com.xenowarriors.party.Bonds.charOf(o), xp);
            com.xenowarriors.party.Bonds.addXp(o, com.xenowarriors.party.Bonds.charOf(m), xp);
        }
    }

    /** (Tests) parties that broke up early but still gave some bond. */
    public static int earlyBond;

    private static void end(ServerPlayer o, Party pt, boolean full) {
        PARTIES.remove(o.getUUID());
        BotRps.partyEnded(o);
        BotPartyGames.partyEnded(o);
        ServerLevel l = o.serverLevel();
        for (BlockPos c : pt.cakes) if (l.getBlockState(c).is(Blocks.CAKE)) l.removeBlock(c, false);
        for (BlockPos b : pt.barrels) if (l.getBlockState(b).is(Blocks.BARREL)) {           // (what's left in the barrels is yours: it pops out)
            if (l.getBlockEntity(b) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity be) { be.setCustomName(null); net.minecraft.world.Containers.dropContents(l, b, be); }
            l.removeBlock(b, false);
        }
        BotJukebox.packUp(o);                                                  // (the jukebox packed up)
        for (ServerPlayer p : l.players()) if (p.distanceToSqr(o) < 64 * 64) ModNetwork.sendToPlayer(new PartyHatsPacket(new int[0], 0, pt.center), p);
        finished++;
        if (full) {
            List<LivingEntity> gs = guests(o, pt);
            partyBond(o, pt, gs, BOND_END * (rappers(gs) ? RAPPERS_BONUS : 1F));
            firework(l, pt, 4);
            o.displayClientMessage(Component.literal("What a party! Everyone there feels a lot closer.").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        } else {
            float got = Mth.clamp(1F - (pt.end - l.getGameTime()) / (float) LENGTH, 0F, 1F);   // (how far it got)
            float xp = BOND_END * EARLY_SHARE * got * (rappers(guests(o, pt)) ? RAPPERS_BONUS : 1F);
            if (xp >= 1F) { partyBond(o, pt, guests(o, pt), xp); earlyBond++; }
            o.displayClientMessage(Component.literal("The party's broken up" + (xp >= 1F ? " - still, everyone had some fun." : ".")).withStyle(ChatFormatting.GRAY), false);
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenoparty")
                .then(Commands.literal("yes").then(Commands.argument("bot", IntegerArgumentType.integer()).executes(c -> answer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "bot"), true))))
                .then(Commands.literal("no").then(Commands.argument("bot", IntegerArgumentType.integer()).executes(c -> answer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "bot"), false)))));
    }

    private static int answer(ServerPlayer p, int id, boolean yes) {
        com.xenowarriors.network.RequestPopupPacket.dismiss(p, "/xenoparty yes " + id);
        if (!(p.level().getEntity(id) instanceof PartyBotEntity b) || !p.getUUID().equals(b.ownerId())) return 0;
        if (!yes) { p.displayClientMessage(Component.empty().append(b.label()).append(": \"Aw. Next time!\"").withStyle(ChatFormatting.GRAY), true); return 1; }
        if (!BotSpar.quiet(p)) { p.displayClientMessage(Component.literal("Not now - there's trouble about.").withStyle(ChatFormatting.RED), true); return 0; }
        return start(p) ? 1 : 0;
    }

    /** (Tests) breaks a party up halfway through. */
    public static void endEarlyForTest(ServerPlayer o) { Party pt = PARTIES.get(o.getUUID()); if (pt != null) { pt.end = o.level().getGameTime() + LENGTH / 2; end(o, pt, false); } }

    /** (Tests) ends a party now (as if its time were up). */
    public static void endForTest(ServerPlayer o) { Party pt = PARTIES.get(o.getUUID()); if (pt != null) end(o, pt, true); }

    /** The world closed (back to the title screen / another world): nothing of this one carries over. (ServerReset) */
    public static void serverReset() { PARTIES.clear(); }
}
