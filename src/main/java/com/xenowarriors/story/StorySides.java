package com.xenowarriors.story;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.StoryDialoguePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Side missions: talk to someone you've met at a story hub and they ask for a hand. Out in your own world, their kind of
 * trouble shows up around you (more for a bigger party); beat it all and your bond with them grows - the way a full
 * party of 4 (no room for bots) grows bonds. One per character, again after a day.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StorySides {
    private StorySides() {}

    public static final String TAG = "xw_story_side";
    static final long COOLDOWN = 24000L, TIME_LIMIT = 20L * 60 * 10;

    static final class Side {
        final UUID host; final StoryDefs.Side def; final List<UUID> members = new ArrayList<>(); final Set<UUID> alive = new HashSet<>();
        int total, killed, spawned; long until;
        Side(UUID host, StoryDefs.Side def) { this.host = host; this.def = def; }
    }

    private static final Map<UUID, Side> SIDES = new HashMap<>();
    private static final Map<UUID, UUID> MEMBER = new HashMap<>();

    private static Side sideOf(Player p) { UUID h = MEMBER.get(p.getUUID()); return h == null ? null : SIDES.get(h); }

    /** Game ticks until this character's side mission can be taken again (0: now). */
    public static long readyIn(Player p, String ch) {
        long t = Story.root(p).getCompound("sideCd").getLong(ch) - p.level().getGameTime();
        return Math.max(0, t);
    }

    public static String objective(Player p) {
        Side s = sideOf(p);
        return s == null ? null : "Side mission - " + s.def.title() + ": " + s.killed + "/" + s.total;
    }

    private static void send(ServerPlayer p, List<StoryDefs.Line> lines, String ctx, List<String> choices) {
        CompoundTag t = new CompoundTag();
        ListTag ls = new ListTag();
        boolean sky = com.xenowarriors.character.Race.of(p).skylanderKind();
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag c = StoryRun.lineTag(lines.get(i), 1, sky, com.xenowarriors.you.You.name(p));
            if (i == lines.size() - 1 && choices != null) {
                ListTag ch = new ListTag();
                for (String o : choices) ch.add(net.minecraft.nbt.StringTag.valueOf(o));
                c.put("choice", ch);
            }
            ls.add(c);
        }
        t.put("lines", ls);
        t.putString("ctx", ctx);
        t.putBoolean("host", true);
        ModNetwork.sendToPlayer(new StoryDialoguePacket(t), p);
    }

    private static StoryDefs.Line line(String s, String t) { return new StoryDefs.Line(s, null, t, Map.of(), null, null, List.of()); }

    /** (Tests) as if they right-clicked this person at the hub. */
    public static void talkForTest(ServerPlayer p, String ch) { talk(p, ch); }

    /** Asked from a bot's menu: your bot gives you its next side mission, like at a hub (story or not, finished or not). */
    public static void askBot(ServerPlayer p, com.xenowarriors.bot.PartyBotEntity b) {
        String ch = b.character().getId();
        if (StoryDefs.SIDES.get(ch) == null) {
            p.displayClientMessage(Component.empty().append(b.label()).append(" has no side missions to give.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (StoryRun.inRun(p)) { p.displayClientMessage(Component.literal("Finish your story mission first.").withStyle(ChatFormatting.GRAY), true); return; }
        talk(p, ch);
    }

    /** Right-clicked someone at a hub. */
    static void talk(ServerPlayer p, String ch) {
        StoryDefs.Side d = StoryDefs.SIDES.get(ch);
        CharacterType t = CharacterType.byId(ch);
        String name = t.getDisplayName().getString();
        int bond = Story.bond(p, t), need = Story.need(t);
        String status = Story.playable(p, t) ? name + " is playable." : "Bond " + bond + "/" + need + " to play " + name + ".";
        if (d == null) { send(p, List.of(line(ch, "Hey."), line("narrator", status)), "side:" + ch, null); return; }
        if (sideOf(p) != null) { send(p, List.of(line(ch, "You're already helping someone. Finish that first."), line("narrator", status)), "side:" + ch, null); return; }
        long wait = readyIn(p, ch);
        if (wait > 0) { send(p, List.of(line(ch, "Thanks for earlier. Come back later, I might need you again."), line("narrator", status + " (" + (wait / 1200 + 1) + " min until their next request)")), "side:" + ch, null); return; }
        List<StoryDefs.Line> l = new ArrayList<>(d.start());
        l.add(line("narrator", "Side mission: " + d.title() + " - beat " + d.n() + " " + what(d.e()) + " out in the world. " + status));
        send(p, l, "side:" + ch, List.of("Accept (" + d.title() + ")", "Not now"));
    }

    static String what(String e) {
        return switch (e) {
            case "sky_hater" -> "Sky Haters"; case "gang" -> "gang members"; case "corrupted_soul" -> "Corrupted Souls"; case "gov" -> "Government agents";
            case "gov_elite" -> "Government elites"; case "sr" -> "SR members"; case "knight", "skylander" -> "knights"; case "dummy" -> "targets";
            case "dark_possessed" -> "possessed"; case "elementalist" -> "Elementalists"; case "vessel" -> "Vessels"; default -> e;
        };
    }

    static void dialogueDone(ServerPlayer p, String ctx, int choice) {
        String ch = ctx.substring(5);
        if (ch.equals("end") || choice != 0) return;
        StoryDefs.Side d = StoryDefs.SIDES.get(ch);
        if (d == null || sideOf(p) != null || readyIn(p, ch) > 0) return;
        // the whole party comes along: out of the hub, back into the world
        StoryRun.Run r = StoryRun.runOf(p);
        List<ServerPlayer> ps = new ArrayList<>();
        if (r != null) { ps.addAll(StoryRun.players(p.server, r)); StoryRun.close(p.server, r, null); }
        if (!ps.contains(p)) ps.add(p);
        Side s = new Side(p.getUUID(), d);
        for (ServerPlayer m : ps) { s.members.add(m.getUUID()); MEMBER.put(m.getUUID(), s.host); }
        s.total = d.n() + 2 * (ps.size() - 1);
        s.until = p.level().getGameTime() + TIME_LIMIT;
        SIDES.put(s.host, s);
        for (ServerPlayer m : ps) {
            m.sendSystemMessage(Component.literal("Side mission: " + d.title() + " (" + CharacterType.byId(ch).getDisplayName().getString() + ") - " + s.total + " " + what(d.e()) + " will show up around you. 10 minutes.").withStyle(ChatFormatting.AQUA));
            Story.sync(m);
        }
    }

    /** (Tests) the side mission's enemies out right now. */
    public static List<Mob> testFoes(ServerPlayer p) {
        Side s = sideOf(p);
        List<Mob> l = new ArrayList<>();
        if (s != null) for (UUID id : s.alive) if (p.serverLevel().getEntity(id) instanceof Mob m && m.isAlive()) l.add(m);
        return l;
    }

    public static boolean testActive(ServerPlayer p) { return sideOf(p) != null; }

    /** (Tests) the side mission's counts, and where its foes are. */
    public static String debugForTest(ServerPlayer p) {
        Side s = sideOf(p);
        if (s == null) return "none";
        StringBuilder b = new StringBuilder("alive " + s.alive.size() + " spawned " + s.spawned + " killed " + s.killed + "/" + s.total + " made " + made + " at");
        for (UUID id : s.alive) { Entity en = p.serverLevel().getEntity(id); b.append(' ').append(en == null ? "gone" : en.blockPosition().toShortString() + (en.isAlive() ? "" : "(dead)")); }
        return b.toString();
    }
    /** (Tests) foes made for side missions. */
    public static int made;

    public static void cancel(ServerPlayer p) {
        Side s = sideOf(p);
        if (s == null) return;
        end(p.server, s, false);
    }

    private static void end(MinecraftServer server, Side s, boolean won) {
        for (UUID id : s.alive) for (ServerLevel l : server.getAllLevels()) { Entity e = l.getEntity(id); if (e != null) e.discard(); }
        SIDES.remove(s.host);
        for (UUID id : s.members) {
            MEMBER.remove(id);
            ServerPlayer m = server.getPlayerList().getPlayer(id);
            if (m == null) continue;
            if (won) {
                CharacterType t = CharacterType.byId(s.def.ch());
                int lv = com.xenowarriors.party.Bonds.level(m, s.def.ch());
                com.xenowarriors.party.Bonds.set(m, s.def.ch(), lv + s.def.bond(), 0);
                CompoundTag cd = Story.root(m).getCompound("sideCd");
                cd.putLong(s.def.ch(), m.level().getGameTime() + COOLDOWN);
                Story.root(m).put("sideCd", cd);
                m.sendSystemMessage(Component.literal("Side mission done! Bond with " + t.getDisplayName().getString() + " +" + s.def.bond() + " (now " + Story.bond(m, t) + ")").withStyle(ChatFormatting.GOLD));
                send(m, s.def.end(), "side:end", null);
            } else m.displayClientMessage(Component.literal("Side mission over: " + s.def.title()).withStyle(ChatFormatting.GRAY), false);
            Story.sync(m);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || SIDES.isEmpty()) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 40 != 0) return;
        for (Side s : new ArrayList<>(SIDES.values())) {
            List<ServerPlayer> ps = new ArrayList<>();
            for (UUID id : s.members) { ServerPlayer m = server.getPlayerList().getPlayer(id); if (m != null && m.isAlive()) ps.add(m); }
            ServerPlayer host = server.getPlayerList().getPlayer(s.host);
            if (ps.isEmpty() || host == null || host.level().getGameTime() > s.until) { end(server, s, false); continue; }
            // foes that are gone without being beaten (despawned, burned away by day, wandered off out of the loaded
            // world) or that strayed far from everyone come back near you - so there's always enough left to finish it
            s.alive.removeIf(id -> {
                Entity en = ((ServerLevel) host.level()).getEntity(id);
                boolean far = en != null && en.isAlive() && ps.stream().noneMatch(m -> m.level() == en.level() && m.distanceToSqr(en) < 48 * 48);
                if (far) en.discard();
                if (en == null || !en.isAlive() || far) { s.spawned = Math.max(0, s.spawned - 1); respawned++; return true; }
                return false;
            });
            if (s.killed >= s.total) { end(server, s, true); continue; }
            while (s.alive.size() < 4 && s.spawned < s.total) {
                ServerPlayer m = ps.get(host.getRandom().nextInt(ps.size()));
                Mob mob = spawnNear(m, s);
                s.spawned++;
                if (mob == null) { s.killed++; continue; }     // (couldn't be made: counts as done, so it can't get stuck)
                s.alive.add(mob.getUUID());
            }
            for (ServerPlayer m : ps) m.displayClientMessage(Component.literal(objective(m)).withStyle(ChatFormatting.YELLOW), true);
        }
    }

    /** (Tests) foes brought back because they were gone without being beaten. */
    public static int respawned;

    /** (Tests) the side mission's foes vanish without being beaten (as if they'd despawned). */
    public static void vanishForTest(ServerPlayer p) {
        Side s = sideOf(p);
        if (s == null) return;
        for (UUID id : s.alive) { Entity en = p.serverLevel().getEntity(id); if (en != null) en.discard(); }
    }

    private static Mob spawnNear(ServerPlayer p, Side s) {
        ServerLevel l = p.serverLevel();
        double a = p.getRandom().nextDouble() * Math.PI * 2, d = 10 + p.getRandom().nextDouble() * 8;
        int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - p.getY()) > 12) y = (int) p.getY();
        StoryDefs.Spawn sp = new StoryDefs.Spawn(s.def.e(), 1, false, null, 0F, false);
        if (StoryRun.corrupted(s.def.e())) StoryRun.nightFor(p.server);                    // (C-souls: it's night first)
        Mob m = StoryRun.makeFoe(l, sp, new BlockPos(x, y, z));
        if (m == null) return null;
        m.moveTo(x + 0.5, y, z + 0.5, p.getRandom().nextFloat() * 360F, 0F);
        m.addTag(TAG);
        m.getPersistentData().putString("xw_story_side", s.host.toString());
        m.setPersistenceRequired();
        if ("dummy".equals(s.def.e())) m.setNoAi(true);
        else {
            float dmz = com.xenowarriors.compat.DmzScaling.partyScale(List.of(p));
            StoryRun.hard(m, s.def.e());                                              // (DragonMineZ: harder)
            if (dmz > 1F) com.xenowarriors.journey.JourneyWorld.scale(m, StoryRun.dmzHard() ? dmz : 1F + (dmz - 1F) * 0.8F);
        }
        if (!l.addFreshEntity(m)) return null;
        made++;
        l.sendParticles(ParticleTypes.PORTAL, m.getX(), m.getY() + 1, m.getZ(), 20, .4, .6, .4, .3);
        if (!"dummy".equals(s.def.e())) m.setTarget(p);
        return m;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!e.getEntity().getTags().contains(TAG) || e.getEntity().level().isClientSide) return;
        try {
            UUID h = UUID.fromString(e.getEntity().getPersistentData().getString("xw_story_side"));
            Side s = SIDES.get(h);
            if (s != null && s.alive.remove(e.getEntity().getUUID())) s.killed++;
        } catch (IllegalArgumentException x) {
            XenoWarriors.LOGGER.debug("Story side: bad tag", x);
        }
    }
}
