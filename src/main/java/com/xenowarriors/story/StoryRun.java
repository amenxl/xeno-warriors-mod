package com.xenowarriors.story;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.StoryDialoguePacket;
import com.xenowarriors.tower.Tower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * A story mission being played: the party (up to 4; the host's story is the one followed) is taken to the mission's
 * places, goes through its steps - scenes of dialogue (with choices), fights in waves (bosses that flee, allies fighting
 * beside you), surviving for a while - and comes back home at the end. Also the hub visits, where the characters you've
 * met stand around to talk to (and give side missions).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StoryRun {
    private StoryRun() {}

    public static final String NPC_TAG = "xw_story_npc", BOT_TAG = "xw_story_bot", FOE_TAG = "xw_story_foe", BACK = "xw_story_back";
    static final int SCENE = 0, FIGHT = 1, SURVIVE = 2, TALKING = 3, DONE = 4, HUB = 5;
    private static final UUID HP = UUID.fromString("3a7f2c11-5b6e-4c1d-9e0f-1a2b3c4d5e01"), DMG = UUID.fromString("3a7f2c11-5b6e-4c1d-9e0f-1a2b3c4d5e02");
    private static final UUID HARD_HP = UUID.fromString("3a7f2c11-5b6e-4c1d-9e0f-1a2b3c4d5e03"), HARD_DMG = UUID.fromString("3a7f2c11-5b6e-4c1d-9e0f-1a2b3c4d5e04");
    /** With DragonMineZ installed the story is a lot harder (the user's call): every foe has this much more health and damage,
     *  one more of each kind turns up, and they keep up with your DragonMineZ power fully. */
    public static final double DMZ_HARD_HP = 2.5, DMZ_HARD_DMG = 1.75;
    /** Each extra player in the party (and each of the party's bots, half as much): more health and damage on every foe -
     *  plus one more of each kind per extra player and per two bots (spawnWave). */
    public static final double PARTY_HP = 0.6, PARTY_DMG = 0.12, BOT_HP = 0.3, BOT_DMG = 0.06;

    public static boolean dmzHard() { return net.minecraftforge.fml.ModList.get() != null && net.minecraftforge.fml.ModList.get().isLoaded("dragonminez"); }

    public static final class Run {
        public final UUID host;
        final String path;
        final StoryDefs.Mission mission;
        final int slot;
        final List<UUID> members = new ArrayList<>();
        public final boolean hub;
        int step = -1, phase = SCENE, wave, deaths, endAt = -1;
        long surviveUntil;
        String place;
        boolean sky;
        final List<UUID> foes = new ArrayList<>(), allies = new ArrayList<>(), npcs = new ArrayList<>();
        final Map<UUID, Float> escapes = new HashMap<>();
        /** The dialogue waiting on the host: what comes after the choice being shown, and that choice. */
        List<StoryDefs.Line> rest = new ArrayList<>();
        StoryDefs.Line asking;
        String ctx = "";
        boolean escaping;
        /** The lines last shown (to show the host again if they closed them). */
        CompoundTag last;

        Run(UUID host, String path, StoryDefs.Mission m, int slot, boolean hub) { this.host = host; this.path = path; this.mission = m; this.slot = slot; this.hub = hub; }

        StoryDefs.Step cur() { return mission == null || step < 0 || step >= mission.steps().size() ? null : mission.steps().get(step); }
        int size() { return Math.max(1, members.size()); }
        /** The party's own bots fighting along (not the story's allies), counted at each wave: they make it harder too. */
        int bots;
        /** The last time anyone fought (a hit by or on a foe or one of the party): a minute without and the next wave comes. */
        long lastCombat;

        public String objective() {
            if (hub) return "Visiting " + StoryScenes.title(place) + " - talk to people (right-click), or leave from the Story menu";
            StoryDefs.Step s = cur();
            if (s == null) return mission == null ? "" : mission.title();
            if (phase == SURVIVE) return (s.obj() == null ? "Survive" : s.obj()) + " (" + Math.max(0, (surviveUntil - now) / 20) + "s)";
            if (phase == FIGHT) return (s.obj() == null ? "Win the fight" : s.obj()) + " - wave " + (wave + 1) + "/" + s.waves().size();
            return mission.title();
        }
        long now;
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Map<UUID, UUID> MEMBER = new HashMap<>();

    public static Run runOf(Player p) { UUID h = MEMBER.get(p.getUUID()); return h == null ? null : RUNS.get(h); }
    public static boolean inRun(Player p) { return MEMBER.containsKey(p.getUUID()); }

    // ------------------------------------------------------------------ the menu

    public static void openMenu(ServerPlayer p) {
        Story.sync(p);
        ModNetwork.sendToPlayer(new StoryDialoguePacket(menuTag()), p);
    }

    private static CompoundTag menuTag() { CompoundTag t = new CompoundTag(); t.putBoolean("menu", true); return t; }

    private static ServerLevel world(MinecraftServer s) { return s.getLevel(Tower.LEVEL); }

    private static int freeSlot() {
        Set<Integer> used = new HashSet<>();
        for (Run r : RUNS.values()) used.add(r.slot);
        int i = 0;
        while (used.contains(i)) i++;
        return i;
    }

    /** Who comes along: the host and their party members who are online and free. */
    private static List<ServerPlayer> party(ServerPlayer host) {
        List<ServerPlayer> l = new ArrayList<>();
        l.add(host);
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(host)) {
            if (m == host || l.size() >= 4 || inRun(m) || com.xenowarriors.journey.Journey.inLobbyOrRun(m) || !m.isAlive()) continue;
            l.add(m);
        }
        return l;
    }

    private static String blocked(ServerPlayer p) {
        if (!p.isAlive()) return "You're down";
        if (inRun(p)) return "You're already on a story mission";
        if (com.xenowarriors.journey.Journey.inLobbyOrRun(p)) return "You're on a Journey";
        if (Tower.is(p.level()) || com.xenowarriors.vr.Vr.is(p.level()) || com.xenowarriors.room.RoomTech.is(p.level()) || com.xenowarriors.mikiko.HekubladeRealm.inRealm(p))
            return "Not from here - go back to the world first";
        if (com.xenowarriors.party.Parties.inParty(p) && !com.xenowarriors.party.Parties.isLeader(p)) return "Only your party's leader can start story missions (you follow their story)";
        return null;
    }

    /** Starts the host's current mission. */
    public static void begin(ServerPlayer host) {
        Run in = runOf(host);
        if (in != null) {                        // (already on one: show the scene's lines again, if it's waiting on them)
            if (in.host.equals(host.getUUID()) && in.last != null && (in.phase == SCENE || in.escaping)) {
                CompoundTag t = in.last.copy(); t.putBoolean("host", true);
                ModNetwork.sendToPlayer(new StoryDialoguePacket(t), host);
            }
            return;
        }
        if (!Story.active(host)) { host.displayClientMessage(Component.literal("Story mode is off (/xeno story start)").withStyle(ChatFormatting.RED), true); return; }
        String why = blocked(host);
        if (why != null) { host.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), true); return; }
        StoryDefs.Mission m = Story.mission(host);
        if (m == null) { host.displayClientMessage(Component.literal("Pick a storyline first").withStyle(ChatFormatting.RED), true); return; }
        long kl = com.xenowarriors.karma.KarmaLevel.of(host);
        if (kl < m.karma() && !host.isCreative()) {
            host.displayClientMessage(Component.literal("\"" + m.title() + "\" needs Karma Level " + com.xenowarriors.karma.KarmaLevel.fmt(m.karma()) + " (yours: " + com.xenowarriors.karma.KarmaLevel.fmt(kl) + ")").withStyle(ChatFormatting.RED), false);
            return;
        }
        Run r = new Run(host.getUUID(), Story.current(host), m, freeSlot(), false);
        r.sky = com.xenowarriors.character.Race.of(host).skylanderKind();
        enter(host, r);
        for (ServerPlayer p : party(host)) {
            if (p == host) continue;
            enter(p, r);
            p.sendSystemMessage(Component.literal(host.getGameProfile().getName() + " started \"" + m.title() + "\" - you're in their story.").withStyle(ChatFormatting.AQUA));
        }
        RUNS.put(r.host, r);
        for (ServerPlayer p : players(host.server, r)) title(p, m.title(), StoryDefs.path(r.path) == null ? "" : StoryDefs.path(r.path).title());
        next(host.server, r);
    }

    /** Visits the current storyline's hub: the people you've met, to talk to. */
    public static void visitHub(ServerPlayer host) {
        String why = blocked(host);
        if (why != null) { host.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), true); return; }
        String path = Story.current(host);
        if (path == null) { List<String> d = new ArrayList<>(Story.done(host)); path = d.isEmpty() ? null : d.get(d.size() - 1); }
        StoryDefs.Path pd = StoryDefs.path(path);
        if (pd == null || pd.hub() == null) { host.displayClientMessage(Component.literal("No place to visit yet - start a storyline").withStyle(ChatFormatting.RED), true); return; }
        Run r = new Run(host.getUUID(), path, null, freeSlot(), true);
        r.phase = HUB;
        for (ServerPlayer p : party(host)) enter(p, r);
        RUNS.put(r.host, r);
        goTo(host.server, r, pd.hub());
        List<String> who = new ArrayList<>();
        for (String c : pd.npcs()) if (Story.met(host, c) && !botted(host.server, r, c)) who.add(c);
        spawnNpcs(host.server, r, who);
        for (ServerPlayer p : players(host.server, r)) { title(p, StoryScenes.title(pd.hub()), "Right-click someone to talk"); Story.sync(p); }
    }

    /** (Tests, videos) stands this player in a story place with these people around, like a hub visit. */
    public static void testPlace(ServerPlayer p, String place, List<String> who) {
        if (inRun(p)) quit(p, null);
        Run r = new Run(p.getUUID(), "rhalix", null, freeSlot(), true);
        r.phase = HUB;
        enter(p, r);
        RUNS.put(r.host, r);
        goTo(p.server, r, place);
        spawnNpcs(p.server, r, who);
        Story.sync(p);
    }

    /** (Tests) the enemies of this player's story fight right now. */
    public static List<Mob> testFoes(ServerPlayer p) {
        Run r = runOf(p);
        List<Mob> l = new ArrayList<>();
        ServerLevel w = world(p.server);
        if (r == null || w == null) return l;
        for (UUID id : r.foes) if (w.getEntity(id) instanceof Mob m && m.isAlive()) l.add(m);
        return l;
    }

    /** (Tests) the people standing around in this player's story place. */
    public static List<PartyBotEntity> testNpcs(ServerPlayer p) {
        Run r = runOf(p);
        List<PartyBotEntity> l = new ArrayList<>();
        ServerLevel w = world(p.server);
        if (r == null || w == null) return l;
        for (UUID id : r.npcs) if (w.getEntity(id) instanceof PartyBotEntity b) l.add(b);
        return l;
    }

    private static void enter(ServerPlayer p, Run r) {
        r.members.add(p.getUUID());
        MEMBER.put(p.getUUID(), r.host);
        CompoundTag b = new CompoundTag();
        b.putString("dim", p.level().dimension().location().toString());
        b.putDouble("x", p.getX()); b.putDouble("y", p.getY()); b.putDouble("z", p.getZ()); b.putFloat("yaw", p.getYRot());
        p.getPersistentData().put(BACK, b);
    }

    static List<ServerPlayer> players(MinecraftServer s, Run r) {
        List<ServerPlayer> l = new ArrayList<>();
        for (UUID id : r.members) { ServerPlayer p = s.getPlayerList().getPlayer(id); if (p != null) l.add(p); }
        return l;
    }

    private static void title(ServerPlayer p, String a, String b) {
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(8, 50, 12));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(a).withStyle(ChatFormatting.GOLD)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal(b).withStyle(ChatFormatting.GRAY)));
    }

    /** Takes the party to a place (built the first time). */
    private static void goTo(MinecraftServer s, Run r, String place) {
        if (place == null || place.equals(r.place)) return;
        ServerLevel l = world(s);
        if (l == null) return;
        clearNpcs(s, r);
        StoryScenes.ensure(l, r.slot, place);
        r.place = place;
        int k = 0;
        for (ServerPlayer p : players(s, r)) {
            Vec3 at = StoryScenes.entry(r.slot, place, k++);
            p.teleportTo(l, at.x, at.y, at.z, 180F, 0F);
            p.fallDistance = 0;
        }
        for (UUID a : r.allies) {
            Entity e = l.getEntity(a);
            if (e != null) { Vec3 at = StoryScenes.entry(r.slot, place, k++); e.teleportTo(at.x, at.y, at.z); }
        }
    }

    // ------------------------------------------------------------------ the steps

    private static boolean ok(Run r, MinecraftServer s, String ifFlag) {
        if (ifFlag == null) return true;
        ServerPlayer h = s.getPlayerList().getPlayer(r.host);
        return h != null && Story.flag(h, ifFlag);
    }

    private static void next(MinecraftServer s, Run r) {
        clearFoes(s, r);
        r.step++;
        StoryDefs.Step st = r.cur();
        while (st != null && !ok(r, s, st.ifFlag())) { r.step++; st = r.cur(); }
        if (st == null) { complete(s, r); return; }
        goTo(s, r, st.place());
        r.wave = 0; r.escaping = false;
        switch (st.type()) {
            case "fight", "survive" -> {
                clearNpcs(s, r);
                spawnAllies(s, r, st.allies());
                r.phase = st.type().equals("survive") ? SURVIVE : FIGHT;
                ServerLevel l = world(s);
                r.surviveUntil = (l == null ? 0 : l.getGameTime()) + st.seconds() * 20L;
                spawnWave(s, r);
                for (ServerPlayer p : players(s, r)) p.displayClientMessage(Component.literal(r.objective()).withStyle(ChatFormatting.YELLOW), true);
            }
            default -> {
                r.phase = SCENE;
                List<String> who = new ArrayList<>();
                for (String c : st.npcs()) if (CharacterType.byId(bodyOf(c)) != CharacterType.NONE) who.add(c);
                clearNpcs(s, r);                            // (this scene's people only)
                spawnNpcs(s, r, who);
                dialogue(s, r, filter(s, r, st.lines()), "step");
            }
        }
        for (ServerPlayer p : players(s, r)) Story.sync(p);
    }

    private static List<StoryDefs.Line> filter(MinecraftServer s, Run r, List<StoryDefs.Line> in) {
        List<StoryDefs.Line> out = new ArrayList<>();
        for (StoryDefs.Line l : in) {
            if (l.ifFlag() != null && !ok(r, s, l.ifFlag())) continue;
            if (l.unlessFlag() != null && ok(r, s, l.unlessFlag())) continue;
            out.add(l);
        }
        return out;
    }

    /** Sends lines up to (and including) the next choice to everyone; the rest waits for the host. */
    private static void dialogue(MinecraftServer s, Run r, List<StoryDefs.Line> lines, String ctx) {
        quiet(s, r);
        List<StoryDefs.Line> chunk = new ArrayList<>();
        r.asking = null;
        int i = 0;
        for (; i < lines.size(); i++) {
            chunk.add(lines.get(i));
            if (!lines.get(i).choice().isEmpty()) { r.asking = lines.get(i); i++; break; }
        }
        r.rest = new ArrayList<>(lines.subList(Math.min(i, lines.size()), lines.size()));
        r.ctx = ctx;
        CompoundTag t = lineTag(s, r, chunk);
        t.putString("ctx", ctx);
        t.putString("place", r.place == null ? "" : StoryScenes.title(r.place));
        r.last = t.copy();
        for (ServerPlayer p : players(s, r)) {
            CompoundTag mine = t.copy();
            mine.putBoolean("host", p.getUUID().equals(r.host));
            ModNetwork.sendToPlayer(new StoryDialoguePacket(mine), p);
        }
    }

    /** The lines as the clients show them (variants picked for this party). */
    static CompoundTag lineTag(MinecraftServer s, Run r, List<StoryDefs.Line> chunk) {
        CompoundTag t = new CompoundTag();
        ListTag ls = new ListTag();
        ServerPlayer h = s.getPlayerList().getPlayer(r.host);
        String you = r.size() > 1 ? "you all" : h == null ? "you" : com.xenowarriors.you.You.name(h);
        for (StoryDefs.Line l : chunk) ls.add(lineTag(l, r.size(), r.sky, you));
        t.put("lines", ls);
        return t;
    }

    static CompoundTag lineTag(StoryDefs.Line l, int size, boolean sky, String you) {
        CompoundTag c = new CompoundTag();
        c.putString("s", l.s() == null ? "narrator" : l.s());
        if (l.n() != null) c.putString("n", l.n());
        String text = l.text(size, sky).replace("{you}", you).replace("{You}", you.isEmpty() ? you : Character.toUpperCase(you.charAt(0)) + you.substring(1));
        c.putString("t", text);
        if (!l.choice().isEmpty()) {
            ListTag ch = new ListTag();
            for (StoryDefs.Choice o : l.choice()) ch.add(net.minecraft.nbt.StringTag.valueOf(o.text()));
            c.put("choice", ch);
        }
        return c;
    }

    /** The host finished reading (and picked a choice, if there was one). */
    public static void dialogueDone(ServerPlayer p, String ctx, int choice) {
        if (ctx.startsWith("side:")) { StorySides.dialogueDone(p, ctx, choice); return; }
        Run r = runOf(p);
        if (r == null || !p.getUUID().equals(r.host) || !ctx.equals(r.ctx)) return;
        MinecraftServer s = p.server;
        List<StoryDefs.Line> more = new ArrayList<>();
        if (r.asking != null) {
            int i = Math.max(0, Math.min(r.asking.choice().size() - 1, choice));
            StoryDefs.Choice c = r.asking.choice().get(i);
            if (c.set() != null) for (ServerPlayer m : players(s, r)) Story.setFlag(m, c.set());
            more.addAll(c.reply());
        }
        more.addAll(r.rest);
        more = filter(s, r, more);
        if (!more.isEmpty()) { dialogue(s, r, more, ctx); return; }
        r.asking = null; r.rest = new ArrayList<>();
        if (ctx.equals("escape")) { r.escaping = false; advanceWave(s, r); return; }
        if (ctx.equals("step")) {
            StoryDefs.Step st = r.cur();
            if (st != null) for (ServerPlayer m : players(s, r)) {
                for (String c : st.meet()) Story.meet(m, c);
                boolean msky = com.xenowarriors.character.Race.of(m).skylanderKind();
                if (msky) for (var e : st.skyBond().entrySet())
                    if (com.xenowarriors.party.Bonds.level(m, e.getKey()) < e.getValue()) com.xenowarriors.party.Bonds.set(m, e.getKey(), e.getValue(), 0);
            }
            next(s, r);
        }
    }

    // ------------------------------------------------------------------ fights

    private static void spawnAllies(MinecraftServer s, Run r, List<String> who) {
        ServerPlayer h = s.getPlayerList().getPlayer(r.host);
        ServerLevel l = world(s);
        if (h == null || l == null) return;
        Set<String> have = present(s, r);                                              // (no doubles: anyone's bot, a played character, an ally)
        int k = 4;
        for (String c : who) {
            CharacterType t = CharacterType.byId(bodyOf(c));
            if (t == CharacterType.NONE || taken(have, c)) continue;
            PartyBotEntity b = com.xenowarriors.bot.PartyBots.spawn(h, t, new CompoundTag());
            if (b == null) continue;
            possess(b, c);
            b.addTag(BOT_TAG);
            b.getPersistentData().putBoolean("xw_journey_bot", true);
            Vec3 at = StoryScenes.entry(r.slot, r.place, k++);
            b.teleportTo(at.x, at.y, at.z);
            r.allies.add(b.getUUID());
            have.add(c); have.add(bodyOf(c)); have.add(soulOf(c));
        }
    }

    private static void spawnWave(MinecraftServer s, Run r) {
        StoryDefs.Step st = r.cur();
        ServerLevel l = world(s);
        if (st == null || l == null || st.waves().isEmpty()) return;
        List<StoryDefs.Spawn> w = st.waves().get(Math.min(r.wave, st.waves().size() - 1));
        for (StoryDefs.Spawn sp : w) if (corrupted(sp.e())) { nightFor(s); break; }       // (C-souls: it's night first)
        List<ServerPlayer> ps = players(s, r);
        float dmz = com.xenowarriors.compat.DmzScaling.partyScale(ps);
        if (!dmzHard()) dmz = 1F + (dmz - 1F) * 0.8F;                                  // (with DragonMineZ: all of it)
        r.lastCombat = l.getGameTime();
        r.bots = 0;                                                                      // (a bigger party: a harder fight)
        for (ServerPlayer p : ps) for (PartyBotEntity b : com.xenowarriors.bot.PartyBots.bots(p)) if (b.isAlive() && !b.getTags().contains(BOT_TAG)) r.bots++;
        for (StoryDefs.Spawn sp : w) {
            int n = sp.boss() ? 1 : sp.n() + (r.size() - 1) + r.bots / 2 + (dmzHard() && !"dummy".equals(sp.e()) ? 1 : 0);
            for (int i = 0; i < n; i++) {
                Mob m = create(l, sp, StoryScenes.center(r.slot, r.place), i == 0);
                if (m == null) continue;
                Vec3 at = StoryScenes.spawnSpot(r.slot, r.place, l.getRandom());
                m.moveTo(at.x, at.y, at.z, l.getRandom().nextFloat() * 360F, 0F);
                prepare(m, sp, r, dmz);
                l.addFreshEntity(m);
                l.sendParticles(ParticleTypes.PORTAL, m.getX(), m.getY() + 1, m.getZ(), 20, .4, .6, .4, .3);
                if (!ps.isEmpty()) m.setTarget(ps.get(l.getRandom().nextInt(ps.size())));
                r.foes.add(m.getUUID());
                if (sp.boss() && sp.escape() > 0) r.escapes.put(m.getUUID(), sp.escape());
            }
        }
    }

    /** A C-soul or other corrupted foe (they fade, sink or are cleansed in daylight). */
    public static boolean corrupted(String e) {
        return e != null && (e.contains("soul") || e.contains("corrupted"));
    }

    /**
     * Before a mission's C-souls show up it turns night (the world's clock - the story's places and the overworld
     * share it), so they don't fade in the sun. Missions' C-souls also stand daylight now (MISSION, see CorruptedSoulEntity).
     */
    public static void nightFor(MinecraftServer s) {
        ServerLevel ow = s.overworld();
        long t = ow.getDayTime(), day = Math.floorMod(t, 24000L);
        if (day >= 13000L && day < 23000L) return;                                     // (night already)
        ow.setDayTime(t - day + (day >= 23000L ? 24000L : 0L) + 13500L);               // (tonight - or the next one, past dawn)
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (Story.active(p))
            p.displayClientMessage(Component.literal("Night falls...").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    /** One of the story's enemies (a side mission's too), by its code (see the story JSON files). */
    public static Mob makeFoe(ServerLevel l, StoryDefs.Spawn sp, BlockPos at) { return create(l, sp, at, true); }

    private static Mob create(ServerLevel l, StoryDefs.Spawn sp, BlockPos c, boolean first) {
        String e = sp.e() == null ? "sky_hater" : sp.e();
        Entity en = null;
        try {
            if (e.startsWith("sr:")) {
                var m = com.xenowarriors.sr.SrEntities.SR_MEMBER.get().create(l);
                String who = e.substring(3), host = "";                                    // "sr:ava@kyrie": Ava, in Kyrie's body
                if (who.contains("@")) { host = who.substring(who.indexOf('@') + 1); who = who.substring(0, who.indexOf('@')); }
                int idx = Arrays.asList(com.xenowarriors.sr.SrMemberEntity.NAMED).indexOf(who);
                if (m != null) { m.moveTo(c.getX(), c.getY() + 1, c.getZ()); m.setup(idx < 0 ? 0 : 2 + idx, c); m.setHost(host); }
                en = m;
            } else switch (e) {
                case "sky_hater", "gang" -> {
                    var h = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(l);
                    if (h != null) {
                        h.moveTo(c.getX(), c.getY() + 1, c.getZ());
                        try { h.finalizeSpawn(l, l.getCurrentDifficultyAt(c), MobSpawnType.EVENT, null, null); } catch (Throwable ignored) {}
                        h.makeRaider(sp.captain() && first);
                        if (e.equals("gang")) h.setCustomName(Component.literal(sp.captain() && first ? "Gang Boss" : "Gang Member").withStyle(ChatFormatting.RED));
                    }
                    en = h;
                }
                case "sr" -> { var m = com.xenowarriors.sr.SrEntities.SR_MEMBER.get().create(l); if (m != null) { m.moveTo(c.getX(), c.getY() + 1, c.getZ()); m.setup(l.getRandom().nextInt(2), c); } en = m; }
                case "gov", "gov_elite" -> { var g = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(l); if (g != null) { g.moveTo(c.getX(), c.getY() + 1, c.getZ()); g.setup(true, e.equals("gov_elite") ? 2 : 1, c); } en = g; }
                case "knight", "skylander" -> en = byId(l, "xenowarriors:royal_guard");
                case "csoul_boss" -> en = com.xenowarriors.corrupted.ModCorrupted.C_SOUL.get().create(l);
                case "ultima", "ultima_proto" -> en = byId(l, "xenowarriors:ultima_prototype");
                case "wolf" -> en = EntityType.WOLF.create(l);
                case "bear" -> en = EntityType.POLAR_BEAR.create(l);
                case "dummy" -> {
                    var h = EntityType.HUSK.create(l);
                    if (h != null) { h.setNoAi(true); h.setCustomName(Component.literal("Target")); h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(12); h.setHealth(12); }
                    en = h;
                }
                default -> en = byId(l, e.contains(":") ? e : "xenowarriors:" + e);
            }
        } catch (Throwable x) {
            XenoWarriors.LOGGER.warn("Story: couldn't make {}", e, x);
        }
        if (!(en instanceof Mob m)) { if (en != null) en.discard(); return null; }
        m.getPersistentData().putBoolean(com.xenowarriors.corrupted.CorruptedSoulEntity.MISSION, true);   // (a mission's: daylight doesn't send it away)
        return m;
    }

    private static Entity byId(ServerLevel l, String id) {
        EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id));
        if (t == null) return null;
        Entity e = t.create(l);
        if (e instanceof Mob m) try { m.finalizeSpawn(l, l.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null); } catch (Throwable ignored) {}
        return e;
    }

    /** Stronger with the mission's level and the party's size (and DragonMineZ power); bosses get a name and a boss-sized health pool. */
    private static void prepare(Mob m, StoryDefs.Spawn sp, Run r, float dmz) {
        m.addTag(FOE_TAG);
        m.addTag("xw_pack");
        m.setPersistenceRequired();
        m.getPersistentData().putString("xw_story_run", r.host.toString());
        int lvl = r.mission == null ? 1 : r.mission.lvl();
        var mh = m.getAttribute(Attributes.MAX_HEALTH);
        if (mh != null && !"dummy".equals(sp.e())) {
            if (sp.boss()) mh.setBaseValue(Math.min(mh.getBaseValue(), 60 + 25 * lvl) * ("ultima".equals(sp.e()) ? 2.0 : 1.0));
            mh.addPermanentModifier(new AttributeModifier(HP, "Story", (0.15 * lvl) + PARTY_HP * (r.size() - 1) + BOT_HP * r.bots, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        var md = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (md != null && !"dummy".equals(sp.e())) md.addPermanentModifier(new AttributeModifier(DMG, "Story", 0.1 * lvl + PARTY_DMG * (r.size() - 1) + BOT_DMG * r.bots, AttributeModifier.Operation.MULTIPLY_TOTAL));
        if ("gov_elite".equals(sp.e()) && mh != null) mh.setBaseValue(mh.getBaseValue() * 1.6);
        hard(m, sp.e());
        m.setHealth(m.getMaxHealth());
        if (dmz > 1F && !"dummy".equals(sp.e())) com.xenowarriors.journey.JourneyWorld.scale(m, dmz);
        if (sp.name() != null) { m.setCustomName(Component.literal(sp.name()).withStyle(sp.boss() ? ChatFormatting.DARK_RED : ChatFormatting.WHITE)); m.setCustomNameVisible(true); }
        if (m instanceof net.minecraft.world.entity.animal.Wolf w) w.setRemainingPersistentAngerTime(20 * 120);
        if (m instanceof net.minecraft.world.entity.animal.PolarBear b) b.setRemainingPersistentAngerTime(20 * 120);
        if (m instanceof PathfinderMob pm && (m instanceof com.xenowarriors.npc.SkylanderEntity))
            pm.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(pm, 1.25, true));
    }

    /** DragonMineZ installed: the story's harder (more health and damage; see DMZ_HARD_HP). Side missions' foes too. */
    public static void hard(Mob m, String e) {
        if (!dmzHard() || "dummy".equals(e)) return;
        var mh = m.getAttribute(Attributes.MAX_HEALTH);
        if (mh != null && mh.getModifier(HARD_HP) == null) mh.addPermanentModifier(new AttributeModifier(HARD_HP, "Story (DragonMineZ)", DMZ_HARD_HP - 1, AttributeModifier.Operation.MULTIPLY_TOTAL));
        var md = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (md != null && md.getModifier(HARD_DMG) == null) md.addPermanentModifier(new AttributeModifier(HARD_DMG, "Story (DragonMineZ)", DMZ_HARD_DMG - 1, AttributeModifier.Operation.MULTIPLY_TOTAL));
        m.setHealth(m.getMaxHealth());
    }

    private static void clearFoes(MinecraftServer s, Run r) {
        ServerLevel l = world(s);
        if (l != null) for (UUID id : r.foes) { Entity e = l.getEntity(id); if (e != null) e.discard(); }
        r.foes.clear(); r.escapes.clear();
    }

    private static void advanceWave(MinecraftServer s, Run r) {
        StoryDefs.Step st = r.cur();
        if (st == null) return;
        clearFoes(s, r);
        r.wave++;
        if (r.phase == FIGHT && r.wave >= st.waves().size()) { next(s, r); return; }
        if (r.phase == SURVIVE) r.wave = Math.min(r.wave, st.waves().size() - 1);
        spawnWave(s, r);
        for (ServerPlayer p : players(s, r)) p.displayClientMessage(Component.literal(r.objective()).withStyle(ChatFormatting.YELLOW), true);
    }

    // ------------------------------------------------------------------ people standing around

    /**
     * Someone in another's body is written "body:soul" in the story files ("lexy:persephone": Persephone, in Lexy's body;
     * "kyrie:ava": Ava, in Kyrie's). They're drawn as the body with red eyes and named "Body (Soul)".
     */
    static String bodyOf(String token) { int i = token.indexOf(':'); return i < 0 ? token : token.substring(0, i); }
    static String soulOf(String token) { int i = token.indexOf(':'); return i < 0 ? token : token.substring(i + 1); }
    private static boolean taken(Set<String> have, String token) { return have.contains(token) || have.contains(bodyOf(token)) || have.contains(soulOf(token)); }

    /** Draws a story person as a possessed body, if they are one. */
    private static void possess(PartyBotEntity b, String token) {
        b.getPersistentData().putString("xw_story_char", token);
        if (!token.contains(":")) return;
        b.setPossessed(true);
        CharacterType body = CharacterType.byId(bodyOf(token)), soul = CharacterType.byId(soulOf(token));
        b.setCustomName(Component.literal(body.getDisplayName().getString() + " (" + soul.getDisplayName().getString() + ")"));
    }

    /** Who a story bot stands for: its token ("lexy:persephone"), or its character. */
    private static void addWho(Set<String> out, PartyBotEntity b) {
        out.add(b.character().getId());
        String tk = b.getPersistentData().getString("xw_story_char");
        if (!tk.isEmpty()) { out.add(tk); out.add(bodyOf(tk)); out.add(soulOf(tk)); }
    }

    private static boolean botted(MinecraftServer s, Run r, String c) {
        for (ServerPlayer p : players(s, r)) for (PartyBotEntity b : com.xenowarriors.bot.PartyBots.bots(p)) if (b.character().getId().equals(c) && !b.getTags().contains(BOT_TAG)) return true;
        return false;
    }

    /**
     * Everyone already there as someone (no doubles): the characters the run's players are playing, their own bots, the
     * story's allies still standing (they stay from a fight into the next scene) and its people standing around.
     */
    private static Set<String> present(MinecraftServer s, Run r) {
        Set<String> out = new HashSet<>();
        for (ServerPlayer p : players(s, r)) {
            CharacterType t = com.xenowarriors.character.ModCapabilities.getType(p);
            if (t != CharacterType.NONE && t != CharacterType.YOU) out.add(t.getId());
            for (PartyBotEntity b : com.xenowarriors.bot.PartyBots.bots(p)) if (b.isAlive()) out.add(b.character().getId());
        }
        ServerLevel l = world(s);
        if (l != null) {
            for (UUID a : r.allies) if (l.getEntity(a) instanceof PartyBotEntity b && b.isAlive()) addWho(out, b);
            for (UUID a : r.npcs) if (l.getEntity(a) instanceof PartyBotEntity b && b.isAlive()) addWho(out, b);
        }
        out.remove(CharacterType.YOU.getId());
        return out;
    }

    private static void spawnNpcs(MinecraftServer s, Run r, List<String> who) {
        ServerLevel l = world(s);
        if (l == null) return;
        Set<String> there = new HashSet<>();
        int standing = 0;
        for (UUID id : r.npcs) if (l.getEntity(id) instanceof PartyBotEntity b) { addWho(there, b); standing++; }
        List<String> add = new ArrayList<>();
        Set<String> here = present(s, r);                                              // (no doubles: a played character, a bot, an ally)
        for (String c : who) if (!taken(there, c) && !taken(here, c) && !botted(s, r, bodyOf(c)) && !botted(s, r, soulOf(c)) && !add.contains(c)) add.add(c);
        int n = standing + add.size(), i = standing;
        for (String c : add) {
            PartyBotEntity b = com.xenowarriors.bot.BotEntities.BOT.get().create(l);
            if (b == null) continue;
            if (CharacterType.byId(bodyOf(c)) == CharacterType.NONE) continue;
            b.setCharacter(CharacterType.byId(bodyOf(c)));
            Vec3 at = StoryScenes.npcSpot(r.slot, r.place, i++, Math.max(n, 1));
            b.moveTo(at.x, at.y, at.z, 0F, 0F);
            b.setYRot(0F); b.setYHeadRot(0F); b.yBodyRot = 0F;
            b.setNoAi(true);
            b.setInvulnerable(true);
            b.setPersistenceRequired();
            b.addTag(NPC_TAG);
            possess(b, c);
            b.getPersistentData().putString("xw_story_run", r.host.toString());
            l.addFreshEntity(b);
            r.npcs.add(b.getUUID());
        }
    }

    private static void clearNpcs(MinecraftServer s, Run r) {
        ServerLevel l = world(s);
        if (l != null) for (UUID id : r.npcs) { Entity e = l.getEntity(id); if (e != null) e.discard(); }
        r.npcs.clear();
    }

    // ------------------------------------------------------------------ ending

    private static void complete(MinecraftServer s, Run r) {
        r.phase = DONE;
        r.endAt = 60;
        clearFoes(s, r);
        StoryDefs.Mission m = r.mission;
        for (ServerPlayer p : players(s, r)) {
            title(p, "MISSION COMPLETE", m.title());
            if (m.money() > 0) com.xenowarriors.economy.Money.add(p, m.money(), true);
            for (var e : m.bond().entrySet()) {
                int lv = com.xenowarriors.party.Bonds.level(p, e.getKey());
                com.xenowarriors.party.Bonds.set(p, e.getKey(), lv + e.getValue(), 0);
            }
            if (!m.bond().isEmpty()) p.sendSystemMessage(Component.literal("Bonds: " + String.join(", ", m.bond().entrySet().stream()
                    .map(e -> CharacterType.byId(e.getKey()).getDisplayName().getString() + " +" + e.getValue()).toList())).withStyle(ChatFormatting.LIGHT_PURPLE));
            Story.completeMission(p, r.path, m.id());              // (the host's own; a member on the same mission moves on too)
        }
    }

    /** Leaves (or ends, for the host) the run this player is in. */
    public static void quit(ServerPlayer p, String why) {
        Run r = runOf(p);
        if (r == null) { StorySides.cancel(p); return; }
        if (r.host.equals(p.getUUID())) close(p.server, r, why);
        else { r.members.remove(p.getUUID()); MEMBER.remove(p.getUUID()); sendHome(p); if (why != null) p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.GRAY), true); Story.sync(p); }
    }

    static void close(MinecraftServer s, Run r, String why) {
        clearFoes(s, r);
        clearNpcs(s, r);
        ServerLevel l = world(s);
        if (l != null) for (UUID a : r.allies) { Entity e = l.getEntity(a); if (e != null) e.discard(); }
        r.allies.clear();
        for (ServerPlayer p : players(s, r)) {
            sendHome(p);
            if (why != null) p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.GRAY), true);
            ModNetwork.sendToPlayer(new StoryDialoguePacket(closeTag()), p);
        }
        for (UUID id : r.members) MEMBER.remove(id);
        RUNS.remove(r.host);
        for (ServerPlayer p : players(s, r)) Story.sync(p);
        for (UUID id : new ArrayList<>(r.members)) { ServerPlayer p = s.getPlayerList().getPlayer(id); if (p != null) Story.sync(p); }
    }

    private static CompoundTag closeTag() { CompoundTag t = new CompoundTag(); t.putBoolean("close", true); return t; }

    static void sendHome(ServerPlayer p) {
        CompoundTag b = p.getPersistentData().getCompound(BACK);
        p.getPersistentData().remove(BACK);
        ServerLevel dest = null;
        if (b.contains("dim")) dest = p.server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new ResourceLocation(b.getString("dim"))));
        if (dest == null || dest.dimension() == Tower.LEVEL) {
            dest = p.server.overworld();
            BlockPos sp = dest.getSharedSpawnPos();
            p.teleportTo(dest, sp.getX() + .5, sp.getY() + 1, sp.getZ() + .5, p.getYRot(), 0F);
        } else p.teleportTo(dest, b.getDouble("x"), b.getDouble("y"), b.getDouble("z"), b.getFloat("yaw"), 0F);
        p.fallDistance = 0;
    }

    public static void onLogin(ServerPlayer p) {
        if (!inRun(p) && p.getPersistentData().contains(BACK)) {
            com.xenowarriors.skill.ServerScheduler.later(5, () -> { if (!p.hasDisconnected() && !inRun(p)) sendHome(p); });
        }
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || RUNS.isEmpty()) return;
        MinecraftServer s = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerLevel l = s == null ? null : world(s);
        if (l == null) return;
        for (Run r : new ArrayList<>(RUNS.values())) {
            r.now = l.getGameTime();
            // who's still here
            for (UUID id : new ArrayList<>(r.members)) {
                ServerPlayer p = s.getPlayerList().getPlayer(id);
                if (p == null || !StoryScenes.inArea(p)) {
                    if (p != null && r.place != null && r.now % 20 == 0) { r.members.remove(id); MEMBER.remove(id); p.getPersistentData().remove(BACK); Story.sync(p); }
                    else if (p == null) { r.members.remove(id); MEMBER.remove(id); }
                    continue;
                }
                if (p.getY() < StoryScenes.Y - 12) { Vec3 at = StoryScenes.entry(r.slot, r.place, 0); p.teleportTo(at.x, at.y, at.z); p.fallDistance = 0; }
                else if (r.place != null && offIsland(r, p)) {                                 // (no flying off the mission's island)
                    Vec3 at = backOn(r, p);
                    p.connection.teleport(at.x, at.y, at.z, p.getYRot(), p.getXRot());
                    p.setDeltaMovement(Vec3.ZERO); p.fallDistance = 0;
                    if (r.now % 40 == 0) p.displayClientMessage(Component.literal("Stay on the island - the mission's here.").withStyle(ChatFormatting.GOLD), true);
                }
            }
            if (!r.members.contains(r.host)) { close(s, r, "The host left - the mission ended"); continue; }
            if (r.phase == DONE) { if (--r.endAt <= 0) close(s, r, null); continue; }
            if (r.phase == HUB) {
                if (r.now % 40 == 0) for (UUID id : new ArrayList<>(r.npcs)) {
                    if (l.getEntity(id) instanceof PartyBotEntity b && botted(s, r, b.character().getId())) { b.discard(); r.npcs.remove(id); }
                }
                continue;
            }
            if ((r.phase == FIGHT || r.phase == SURVIVE) && !r.escaping) tickFight(s, l, r);
            else if (r.now % 10 == 0) quiet(s, r);                                            // (talking: nobody attacks anybody)
            if (r.now % 20 == 0) for (ServerPlayer p : players(s, r)) if (r.phase == FIGHT || r.phase == SURVIVE) p.displayClientMessage(Component.literal(r.objective()).withStyle(ChatFormatting.YELLOW), true);
        }
    }

    /**
     * While people talk, nobody fights: anything hostile left on the island (a straggler, something a boss summoned, a
     * monster that wandered in) is gone, and the party's bots drop their targets (see PartyBotEntity.storyHold).
     */
    static void quiet(MinecraftServer s, Run r) {
        ServerLevel l = world(s);
        if (l == null || r.place == null) return;
        BlockPos c = StoryScenes.center(r.slot, r.place);
        AABB box = new AABB(c).inflate(ISLAND_EDGE + 12, ISLAND_CEILING + 12, ISLAND_EDGE + 12);
        for (Mob m : l.getEntitiesOfClass(Mob.class, box, x -> x.isAlive() && !(x instanceof PartyBotEntity)
                && !x.getTags().contains(NPC_TAG) && (x instanceof net.minecraft.world.entity.monster.Enemy || r.foes.contains(x.getUUID())
                || x.getTarget() instanceof Player || x.getTarget() instanceof PartyBotEntity))) {
            l.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 1, m.getZ(), 6, .3, .5, .3, .02);
            m.discard();
        }
        r.foes.clear(); r.escapes.clear();
        for (ServerPlayer p : players(s, r)) for (PartyBotEntity b : com.xenowarriors.bot.PartyBots.bots(p)) { b.setTarget(null); }
        for (UUID a : r.allies) if (l.getEntity(a) instanceof PartyBotEntity b) b.setTarget(null);
    }

    /** True while a story run this bot belongs to (as a party bot or the story's ally) is talking - it holds its attacks. */
    public static boolean holding(PartyBotEntity b) {
        if (RUNS.isEmpty()) return false;
        for (Run r : RUNS.values()) {
            boolean talking = r.phase == SCENE || r.phase == TALKING || r.phase == HUB || r.phase == DONE || r.escaping;
            if (!talking) continue;
            if (r.allies.contains(b.getUUID()) || r.npcs.contains(b.getUUID())) return true;
            UUID o = b.ownerId();
            if (o != null && r.members.contains(o)) return true;
        }
        return false;
    }

    /** How far from the island's middle (sideways) and above its floor anyone may go during a mission. */
    static final int ISLAND_EDGE = StoryScenes.R + 3, ISLAND_CEILING = 22;
    /** A minute without a hit either way and the next wave comes (nobody gets stuck on a foe they can't reach). */
    static final int NO_COMBAT_TICKS = 60 * 20;

    static boolean offIsland(Run r, Entity e) {
        BlockPos c = StoryScenes.center(r.slot, r.place);
        double dx = e.getX() - (c.getX() + 0.5), dz = e.getZ() - (c.getZ() + 0.5);
        return dx * dx + dz * dz > ISLAND_EDGE * ISLAND_EDGE || e.getY() > StoryScenes.Y + ISLAND_CEILING;
    }

    /** The nearest spot back on the island (just inside the edge, no higher than the ceiling). */
    static Vec3 backOn(Run r, Entity e) {
        BlockPos c = StoryScenes.center(r.slot, r.place);
        double cx = c.getX() + 0.5, cz = c.getZ() + 0.5, dx = e.getX() - cx, dz = e.getZ() - cz, d = Math.sqrt(dx * dx + dz * dz);
        double k = d > StoryScenes.R - 2 ? (StoryScenes.R - 2) / d : 1;
        return new Vec3(cx + dx * k, Math.max(StoryScenes.Y + 1, Math.min(e.getY(), StoryScenes.Y + ISLAND_CEILING - 4)), cz + dz * k);
    }

    /** The run's player (or one of the party's bots) nearest to this foe - who it goes for. */
    private static LivingEntity nearestOf(MinecraftServer s, Run r, Mob m) {
        LivingEntity best = null; double bd = Double.MAX_VALUE;
        for (ServerPlayer p : players(s, r)) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) continue;
            double d = p.distanceToSqr(m); if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    private static void tickFight(MinecraftServer s, ServerLevel l, Run r) {
        List<ServerPlayer> ps = players(s, r);
        int alive = 0;
        if (r.lastCombat > 0 && r.now - r.lastCombat > NO_COMBAT_TICKS && !r.foes.isEmpty()) {        // (no soft-locks)
            for (ServerPlayer p : ps) p.displayClientMessage(Component.literal("No fighting for a minute - on to the next wave.").withStyle(ChatFormatting.GOLD), false);
            if (r.phase == SURVIVE && r.now + NO_COMBAT_TICKS >= r.surviveUntil) { next(s, r); return; }
            advanceWave(s, r);
            return;
        }
        for (UUID id : new ArrayList<>(r.foes)) {
            Entity e = l.getEntity(id);
            if (!(e instanceof Mob m) || !m.isAlive()) { r.foes.remove(id); continue; }
            alive++;
            if (m.getY() < StoryScenes.Y - 12) { m.discard(); r.foes.remove(id); continue; }
            if (r.place != null && offIsland(r, m)) {                                         // (enemies don't fly off the island either)
                Vec3 at = backOn(r, m); m.teleportTo(at.x, at.y, at.z); m.setDeltaMovement(Vec3.ZERO); m.fallDistance = 0;
            }
            Float esc = r.escapes.get(id);
            if (esc != null && m.getHealth() < m.getMaxHealth() * esc && r.foes.size() > 1) {   // (the rest of the wave first: no getting away
                m.setHealth(m.getMaxHealth() * esc + 1F);                                      //  - and no talking - while they still fight)
                if (r.now % 60 == 0) for (ServerPlayer p : ps)
                    p.displayClientMessage(Component.literal("Take out the rest of them first!").withStyle(ChatFormatting.GOLD), true);
                continue;
            }
            if (esc != null && m.getHealth() < m.getMaxHealth() * esc) {     // the boss gets away through a portal
                l.sendParticles(ParticleTypes.PORTAL, m.getX(), m.getY() + 1, m.getZ(), 60, .5, 1, .5, .4);
                StoryDefs.Step st = r.cur();
                r.escaping = true;
                clearFoes(s, r);
                if (st != null && !st.escapeLines().isEmpty()) dialogue(s, r, filter(s, r, st.escapeLines()), "escape");
                else { r.escaping = false; advanceWave(s, r); }
                return;
            }
            if (r.now % 20 == 0 && !m.isNoAi()) {                                              // (they lock on to you and come for you)
                LivingEntity t = m.getTarget();
                boolean ours = t instanceof ServerPlayer tp && r.members.contains(tp.getUUID()) && tp.isAlive()
                        || t instanceof PartyBotEntity tb && tb.isAlive() && !tb.downed();
                if (!ours) { LivingEntity n = nearestOf(s, r, m); if (n != null) m.setTarget(n); }
            }
        }
        if (r.phase == SURVIVE) {
            if (r.now >= r.surviveUntil) { next(s, r); return; }
            if (alive == 0) advanceWave(s, r);
            return;
        }
        if (alive == 0) advanceWave(s, r);
    }

    /** Any hit by or on a story foe (or on one of a run's players) counts as fighting (see NO_COMBAT_TICKS). */
    @SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        Run r = null;
        if (v.getTags().contains(FOE_TAG)) r = runByFoe(v);
        else if (v instanceof ServerPlayer p) r = runOf(p);
        Entity a = e.getSource().getEntity();
        if (r == null && a != null) {
            if (a.getTags().contains(FOE_TAG) && a instanceof LivingEntity la) r = runByFoe(la);
            else if (a instanceof ServerPlayer ap) r = runOf(ap);
        }
        if (r != null) r.lastCombat = v.level().getGameTime();
    }

    private static Run runByFoe(LivingEntity m) {
        String h = m.getPersistentData().getString("xw_story_run");
        if (h.isEmpty()) return null;
        try { return RUNS.get(UUID.fromString(h)); } catch (IllegalArgumentException x) { return null; }
    }

    /** Falling in a story fight isn't death: you're back at the start of the place. Too many falls and the mission fails. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Run r = runOf(p);
        if (r == null || !StoryScenes.inArea(p)) return;
        e.setCanceled(true);
        p.setHealth(p.getMaxHealth());
        p.removeAllEffects();
        p.clearFire();
        Vec3 at = StoryScenes.entry(r.slot, r.place, 0);
        p.teleportTo(at.x, at.y, at.z);
        p.fallDistance = 0;
        r.deaths++;
        int limit = 3 * r.size();
        if (r.deaths >= limit && !r.hub) {
            for (ServerPlayer m : players(p.server, r)) title(m, "MISSION FAILED", "Train up and try again (Main Menu > Story)");
            close(p.server, r, "Mission failed");
        } else for (ServerPlayer m : players(p.server, r)) m.displayClientMessage(Component.literal(p.getGameProfile().getName() + " went down (" + r.deaths + "/" + limit + " before the mission fails)").withStyle(ChatFormatting.RED), false);
    }

    /** Talking to someone at a hub (or in a scene). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!e.getTarget().getTags().contains(NPC_TAG)) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (e.getLevel().isClientSide || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p)) return;
        Run r = runOf(p);
        if (r == null || r.phase != HUB) return;
        StorySides.talk(p, soulOf(e.getTarget().getPersistentData().getString("xw_story_char")));
    }

    // ------------------------------------------------------------------ Rhalix drops in

    /** During his storyline Rhalix sometimes turns up beside you in a fight (out in the world) for a few minutes. */
    static void rhalixDropIn(ServerPlayer p) {
        long now = p.level().getGameTime();
        for (PartyBotEntity b : new ArrayList<>(com.xenowarriors.bot.PartyBots.bots(p))) {
            long until = b.getPersistentData().getLong("xw_story_dropin");
            if (until > 0 && now > until) {
                p.displayClientMessage(Component.literal("Rhalix: Gotta go - SP business. Later!").withStyle(ChatFormatting.LIGHT_PURPLE), false);
                b.discard();
            }
        }
        if (!Story.active(p) || !"rhalix".equals(Story.current(p)) || inRun(p) || Tower.is(p.level())) return;
        if (p.getPersistentData().getLong("xw_story_rhalix_cd") > now) return;
        for (PartyBotEntity b : com.xenowarriors.bot.PartyBots.bots(p)) if (b.character() == CharacterType.RHALIX) return;
        boolean danger = !p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p).isEmpty();
        if (!danger || p.getRandom().nextFloat() > 0.35F) return;
        p.getPersistentData().putLong("xw_story_rhalix_cd", now + 20 * 60 * 8);
        PartyBotEntity b = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.RHALIX, new CompoundTag());
        if (b == null) return;
        b.getPersistentData().putLong("xw_story_dropin", now + 20 * 150);
        b.getPersistentData().putBoolean("xw_journey_bot", true);
        p.displayClientMessage(Component.literal("Rhalix: Need a hand? I got you!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
    }

    @SuppressWarnings("unused")
    private static boolean inWorld(Player p, ResourceKey<Level> k) { return p.level().dimension() == k; }
    @SuppressWarnings("unused")
    private static AABB box(BlockPos c) { return new AABB(c).inflate(StoryScenes.R + 6, 30, StoryScenes.R + 6); }
}
