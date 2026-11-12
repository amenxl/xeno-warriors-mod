package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.stats.Stat;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Sparring in quiet times. When neither you nor your bots are in a fight, now and then
 * <ul>
 *   <li>a bot asks you to spar (a request card: accept with the Accept key) - the same spar as from its menu, or</li>
 *   <li>two of your bots spar each other: a real fight between them, until one is down to 30% health (nobody goes
 *       down), or a minute is up, or something hostile turns up, or</li>
 *   <li>(three or more bots) a bot asks for a 2v2: it names its partner and the bot on your side ({@link Match}).</li>
 * </ul>
 * Sparring gives Speed / Attack / Defense XP, and with DragonMineZ Dragon Ball TP too ({@link #tp}) - for you
 * sparring a bot, and for both bots sparring each other.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotSpar {
    private BotSpar() {}

    /** Ticks between two spars started by themselves (about 5 minutes); longest bot-vs-bot spar; how long a request lasts. */
    public static final int GAP = 20 * 60 * 30, MAX_SPAR = 20 * 60, ASK_TICKS = 20 * 20;
    private static final String LAST = "xw_spar_event_at";
    /** bot -> when its spar with another bot ends */
    private static final Map<UUID, Long> BOT_SPAR = new HashMap<>();
    /** (Tests) spars asked for, bot-vs-bot spars started and finished. */
    public static int asked, started, finished;

    // ------------------------------------------------------------------ quiet?

    static boolean quiet(ServerPlayer o) {
        if (o.getLastHurtByMob() != null && o.tickCount - o.getLastHurtByMobTimestamp() < 20 * 30) return false;
        return o.level().getEntitiesOfClass(Mob.class, o.getBoundingBox().inflate(20), m -> m.isAlive() && (m instanceof Enemy || m.getTarget() == o)
                && !(m instanceof PartyBotEntity)).isEmpty();
    }

    /**
     * Something else is going on (a party, a picnic, a night camp or hang-out, music playing, a Journey, an arena or
     * the Tower, a sleeping owner): bots don't start sparring each other - and stop one that's going.
     */
    public static String event(ServerPlayer o) {
        if (BotPartyTime.active(o)) return "a party";
        if (BotPicnic.active(o)) return "a picnic";
        if (BotLife.busy(o)) return "a night camp";
        if (o.isSleeping()) return "bedtime";
        if (com.xenowarriors.journey.Journey.inLobbyOrRun(o)) return "a Journey";
        if (com.xenowarriors.modes.ModeRules.arena(o.level())) return "an event";
        if (BotJukebox.songOn(o) || BotJukebox.radioOn(o)) return "the music";
        return null;
    }

    private static boolean free(PartyBotEntity b, ServerPlayer o) {
        return !b.downed() && !b.sparring() && !b.journey() && !b.isPassenger() && b.getTarget() == null && b.getHealth() > b.getMaxHealth() * 0.8F
                && b.level() == o.level() && b.distanceToSqr(o) < 16 * 16 && BotErrands.danger(b) == null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer o) || o instanceof FakePlayer) return;
        if (o.tickCount % 20 == 13) { watch(o); watchTeams(o); }
        if (o.tickCount % 200 != 113) return;
        long now = o.level().getGameTime(), last = o.getPersistentData().getLong(LAST);
        if (last > 0 && now >= last && now - last < GAP) return;
        if (o.isSpectator() || o.getAbilities().flying || o.getVehicle() != null || !quiet(o) || MATCHES.containsKey(o.getUUID()) || event(o) != null) return;
        if (o.getRandom().nextInt(3) != 0) return;
        List<PartyBotEntity> free = new ArrayList<>();
        for (PartyBotEntity b : PartyBots.bots(o)) if (free(b, o)) free.add(b);
        if (free.isEmpty()) return;
        Collections.shuffle(free, new Random(o.getRandom().nextLong()));
        if (free.size() >= 3 && o.getRandom().nextInt(3) == 0) askTeams(free.get(0), free.get(1), free.get(2), o);
        else if (free.size() >= 2 && o.getRandom().nextBoolean()) startBots(free.get(0), free.get(1), o);
        else ask(free.get(0), o);
        o.getPersistentData().putLong(LAST, now);
    }

    /** A bot asks its player for a spar. */
    public static void ask(PartyBotEntity b, ServerPlayer o) {
        asked++;
        String yes = "/xenospar yes " + b.getId(), no = "/xenospar no " + b.getId();
        BotRequests.send(o, new com.xenowarriors.network.RequestPopupPacket(b.character().getDisplayName().getString() + " wants to spar",
                "A friendly fight for some training? (nobody goes down)" + (PartyBots.sparBoostReady(o) ? " - spar XP boost ready!" : ""), yes, no, ASK_TICKS, 0xFFFFB040));
        o.displayClientMessage(Component.empty().append(b.label()).append(": \"Hey - want to spar?\"").withStyle(ChatFormatting.GOLD), false);
    }

    // ------------------------------------------------------------------ 2v2

    /**
     * A team spar: the bot that asked and its partner ("red") against you and the bot it put on your side ("blue").
     * Anyone hit down to their limit (a bot at 30% health, you at 1 heart) is out and stops; the last side standing
     * wins (two minutes at most - a draw). Everyone who took part gets spar XP (and TP with DragonMineZ).
     */
    public static final class Match {
        final UUID owner;
        final List<UUID> red = new ArrayList<>(), blue = new ArrayList<>();
        final Set<UUID> out = new HashSet<>();
        final long end;
        Match(UUID owner, long end) { this.owner = owner; this.end = end; }
        boolean red(UUID u) { return red.contains(u); }
        boolean member(UUID u) { return red.contains(u) || blue.contains(u); }
    }

    public static final int MAX_TEAM_SPAR = 20 * 120;
    private static final Map<UUID, Match> MATCHES = new HashMap<>();               // by owner
    private static final Map<UUID, UUID> IN_MATCH = new HashMap<>();               // member -> owner
    /** (Tests) team spars asked for, started and finished. */
    public static int teamAsked, teamStarted, teamFinished;

    private static Match matchOf(Entity e) {
        if (e == null) return null;
        UUID o = IN_MATCH.get(e.getUUID());
        return o == null ? null : MATCHES.get(o);
    }

    /** Still fighting in a 2v2 (not knocked out of it). */
    public static boolean inMatch(Entity e) {
        Match m = matchOf(e);
        return m != null && !m.out.contains(e.getUUID());
    }

    /** Both still fighting in the same 2v2, on opposite sides. */
    public static boolean teamFoes(Entity a, Entity b) {
        Match m = matchOf(a);
        if (m == null || m != matchOf(b) || m.out.contains(a.getUUID()) || m.out.contains(b.getUUID())) return false;
        return m.red(a.getUUID()) != m.red(b.getUUID());
    }

    /** Who a sparring bot goes for: the nearest opponent still in its 2v2, the bot it's sparring, or its owner. */
    public static LivingEntity pick(PartyBotEntity b) {
        Match m = matchOf(b);
        if (m != null) {
            LivingEntity best = null; double bd = Double.MAX_VALUE;
            for (UUID u : m.red(b.getUUID()) ? m.blue : m.red) {
                if (m.out.contains(u)) continue;
                LivingEntity e = living(b, u);
                if (e == null || !e.isAlive()) continue;
                double d = e.distanceToSqr(b);
                if (d < bd) { bd = d; best = e; }
            }
            return best;
        }
        PartyBotEntity f = foeOf(b);
        return f != null ? f : b.owner();
    }

    private static LivingEntity living(Entity near, UUID u) {
        if (!(near.level() instanceof net.minecraft.server.level.ServerLevel l)) return null;
        Player p = l.getPlayerByUUID(u);
        if (p != null) return p;
        return l.getEntity(u) instanceof LivingEntity e ? e : null;
    }

    /** A bot asks for a 2v2: it and {@code mate} against you and {@code yours}. */
    public static void askTeams(PartyBotEntity asker, PartyBotEntity mate, PartyBotEntity yours, ServerPlayer o) {
        teamAsked++;
        String ids = asker.getId() + " " + mate.getId() + " " + yours.getId();
        String a = asker.character().getDisplayName().getString(), m = mate.character().getDisplayName().getString(), y = yours.character().getDisplayName().getString();
        BotRequests.send(o, new com.xenowarriors.network.RequestPopupPacket(a + " wants a 2v2 spar",
                a + " & " + m + " vs you & " + y + " - everyone gets spar XP" + (PartyBots.sparBoostReady(o) ? " (XP boost ready!)" : ""),
                "/xenospar team yes " + ids, "/xenospar team no " + ids, ASK_TICKS, 0xFFFFB040));
        o.displayClientMessage(Component.empty().append(asker.label()).append(": \"2v2? Me and ").append(mate.label())
                .append(" against you and ").append(yours.label()).append("!\"").withStyle(ChatFormatting.GOLD), false);
    }

    /** Starts a 2v2 (red: a + mate; blue: the owner + yours). */
    public static boolean startTeams(PartyBotEntity a, PartyBotEntity mate, PartyBotEntity yours, ServerPlayer o) {
        Set<PartyBotEntity> bots = new HashSet<>(List.of(a, mate, yours));
        if (bots.size() < 3 || MATCHES.containsKey(o.getUUID()) || inMatch(o)) return false;
        for (PartyBotEntity b : bots) if (b.sparring() || b.downed() || !o.getUUID().equals(b.ownerId()) || b.level() != o.level()) return false;
        Match m = new Match(o.getUUID(), o.level().getGameTime() + MAX_TEAM_SPAR);
        m.red.add(a.getUUID()); m.red.add(mate.getUUID());
        m.blue.add(o.getUUID()); m.blue.add(yours.getUUID());
        MATCHES.put(o.getUUID(), m);
        for (UUID u : m.red) IN_MATCH.put(u, o.getUUID());
        for (UUID u : m.blue) IN_MATCH.put(u, o.getUUID());
        for (PartyBotEntity b : bots) {
            b.sparFoe = null;
            b.setSparring(true);
            b.setSparringBot(b == yours);                                           // (you can lock on to the two against you, not your partner)
            b.setTarget(pick(b));
        }
        teamStarted++;
        o.displayClientMessage(Component.literal("2v2 spar: ").append(a.label()).append(" & ").append(mate.label()).append(" vs you & ").append(yours.label())
                .append("! Down to 1 heart (bots 30%) and you're out.").withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    /** Someone in a 2v2 has been hit down to their limit: out of it (the match ends when one side is all out). */
    public static void out(Entity e) {
        Match m = matchOf(e);
        if (m == null || !m.out.add(e.getUUID())) return;
        if (e instanceof PartyBotEntity b) {
            b.setHealth(Math.max(b.getHealth(), b.getMaxHealth() * 0.3F));
            b.setSparring(false);
        }
        ServerPlayer o = e.getServer() == null ? null : e.getServer().getPlayerList().getPlayer(m.owner);
        if (o != null) o.displayClientMessage((e instanceof PartyBotEntity b ? Component.empty().append(b.label()) : Component.literal("You"))
                .append(e instanceof PartyBotEntity ? " is out of the spar!" : "'re out of the spar - your partner fights on!").withStyle(ChatFormatting.YELLOW), false);
        boolean redLeft = false, blueLeft = false;
        for (UUID u : m.red) if (!m.out.contains(u)) redLeft = true;
        for (UUID u : m.blue) if (!m.out.contains(u)) blueLeft = true;
        if (!redLeft || !blueLeft) endTeams(m, o, !redLeft ? Boolean.FALSE : Boolean.TRUE, "won");
    }

    /** A 2v2 is over: redWon null = a draw / stopped. Everyone who took part gets XP (unless an enemy broke it up). */
    static void endTeams(Match m, ServerPlayer o, Boolean redWon, String why) {
        MATCHES.remove(m.owner);
        List<PartyBotEntity> bots = new ArrayList<>();
        for (UUID u : m.red) { IN_MATCH.remove(u); if (o != null && living(o, u) instanceof PartyBotEntity b) bots.add(b); }
        for (UUID u : m.blue) { IN_MATCH.remove(u); if (o != null && living(o, u) instanceof PartyBotEntity b) bots.add(b); }
        teamFinished++;
        for (PartyBotEntity b : bots) {
            b.sparFoe = null;
            b.setSparring(false);
            b.setHealth(Math.max(b.getHealth(), b.getMaxHealth() * 0.5F));
        }
        if (o == null) return;
        boolean stopped = why.startsWith("an enemy") || why.startsWith("it's off");
        String red = names(m.red, o), blue = "you & " + names(m.blue, o);
        o.displayClientMessage(Component.literal(redWon == null ? "2v2 spar over (" + why + ")" : redWon ? red + " win the 2v2!" : "You win the 2v2 with " + names(m.blue, o) + "!")
                .withStyle(ChatFormatting.GOLD), false);
        if (stopped) return;
        int redLv = 0, blueLv = 0, rn = 0, bn = 0;
        for (PartyBotEntity b : bots) if (m.red(b.getUUID())) { redLv += b.botLevel(); rn++; } else { blueLv += b.botLevel(); bn++; }
        int my = com.xenowarriors.stats.Stats.characterLevel(o);
        float redAvg = rn == 0 ? my : redLv / (float) rn, blueAvg = (blueLv + my) / (float) (bn + 1);
        for (PartyBotEntity x : bots) {
            boolean isRed = m.red(x.getUUID());
            boolean won = redWon != null && redWon == isRed;
            float other = isRed ? blueAvg : redAvg;
            float mult = (won ? 1F : 0.6F) * Math.max(0.5F, Math.min(3F, 1F + (other - x.botLevel()) / 10F));
            for (Stat s : Stat.values()) if (s.ordinal() < 3) PartyBots.addXp(x, s, 40F * mult);
            float tp = tp(x, won, true);
            if (tp > 0) com.xenowarriors.compat.DmzBots.giveTp(x, tp);
        }
        PartyBots.sparReward(o, Math.round(redAvg), redWon != null && !redWon);
    }

    private static String names(List<UUID> l, ServerPlayer o) {
        List<String> n = new ArrayList<>();
        for (UUID u : l) if (living(o, u) instanceof PartyBotEntity b) n.add(b.character().getDisplayName().getString());
        return String.join(" & ", n);
    }

    /** Once a second: a 2v2 ends on time, distance, a real enemy, or its people gone; bots keep after the other side. */
    private static void watchTeams(ServerPlayer o) {
        Match m = MATCHES.get(o.getUUID());
        if (m == null) return;
        long now = o.level().getGameTime();
        for (UUID u : m.red) if (!(living(o, u) instanceof PartyBotEntity b) || !b.isAlive() || b.distanceToSqr(o) > 48 * 48) { endTeams(m, o, null, "it's off"); return; }
        for (UUID u : m.blue) if (!u.equals(o.getUUID()) && (!(living(o, u) instanceof PartyBotEntity b) || !b.isAlive() || b.distanceToSqr(o) > 48 * 48)) { endTeams(m, o, null, "it's off"); return; }
        if (!o.isAlive() || !quiet(o)) { endTeams(m, o, null, o.isAlive() ? "an enemy turned up" : "it's off"); return; }
        String ev = event(o);
        if (ev != null) { endTeams(m, o, null, "stopped for " + ev); return; }
        if (now >= m.end) { endTeams(m, o, null, "time's up - a draw"); return; }
        for (UUID u : m.red.size() + m.blue.size() > 0 ? concat(m.red, m.blue) : List.<UUID>of()) {
            if (m.out.contains(u) || !(living(o, u) instanceof PartyBotEntity b)) continue;
            LivingEntity t = b.getTarget();
            if (t == null || !teamFoes(b, t)) b.setTarget(pick(b));
        }
    }

    private static List<UUID> concat(List<UUID> a, List<UUID> b) { List<UUID> l = new ArrayList<>(a); l.addAll(b); return l; }

    /** (Logout or a world change: the 2v2 just stops.) */
    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        Match m = MATCHES.get(e.getEntity().getUUID());
        if (m != null && e.getEntity() instanceof ServerPlayer o) endTeams(m, o, null, "it's off");
    }

    /** (Tests) */
    public static boolean teamMatchFor(ServerPlayer o) { return MATCHES.containsKey(o.getUUID()); }

    /** Two bots spar each other. */
    public static boolean startBots(PartyBotEntity a, PartyBotEntity b, ServerPlayer o) {
        if (a == b || a.sparring() || b.sparring()) return false;
        long end = a.level().getGameTime() + MAX_SPAR;
        a.sparFoe = b.getUUID(); b.sparFoe = a.getUUID();
        a.setSparring(true); b.setSparring(true);
        a.setSparringBot(true); b.setSparringBot(true);
        a.setTarget(b); b.setTarget(a);
        BOT_SPAR.put(a.getUUID(), end); BOT_SPAR.put(b.getUUID(), end);
        started++;
        o.displayClientMessage(Component.empty().append(a.label()).append(" and ").append(b.label()).append(" start a sparring match!").withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    private static PartyBotEntity foeOf(PartyBotEntity b) {
        if (b.sparFoe == null || !(b.level() instanceof net.minecraft.server.level.ServerLevel l)) return null;
        return l.getEntity(b.sparFoe) instanceof PartyBotEntity f ? f : null;
    }

    /** Once a second: keeps bot-vs-bot spars going (targets), and ends them on time, distance or a real enemy. */
    private static void watch(ServerPlayer o) {
        long now = o.level().getGameTime();
        for (PartyBotEntity b : PartyBots.bots(o)) {
            Long end = BOT_SPAR.get(b.getUUID());
            if (end == null) continue;
            PartyBotEntity f = foeOf(b);
            if (f == null || !f.isAlive() || !b.isAlive() || f.downed() || b.downed()) { endBots(b, f, null, o, "it's off"); continue; }
            boolean danger = !quiet(o) || BotErrands.danger(b) != null;
            if (danger) { endBots(b, f, null, o, "an enemy turned up"); continue; }
            String ev = event(o);
            if (ev != null) { endBots(b, f, null, o, "stopped for " + ev); continue; }
            if (now >= end) { endBots(b, f, null, o, "time's up - a draw"); continue; }
            if (b.getTarget() != f) b.setTarget(f);
            if (f.getTarget() != b) f.setTarget(b);
        }
    }

    /** A bot-vs-bot spar is over (winner null: a draw or stopped). */
    static void endBots(PartyBotEntity a, PartyBotEntity b, PartyBotEntity winner, ServerPlayer o, String why) {
        for (PartyBotEntity x : new PartyBotEntity[]{a, b}) {
            if (x == null) continue;
            BOT_SPAR.remove(x.getUUID());
            x.sparFoe = null;
            x.setSparring(false);
            x.setHealth(Math.max(x.getHealth(), x.getMaxHealth() * 0.5F));
        }
        if (a == null || b == null) return;
        finished++;
        boolean stopped = why.startsWith("an enemy") || why.startsWith("it's off");
        if (o != null) {
            Component msg = winner != null ? Component.empty().append(winner.label()).append(" wins the spar against ").append(winner == a ? b.label() : a.label()).append("!")
                    : Component.empty().append(a.label()).append(" and ").append(b.label()).append(": spar over (" + why + ")");
            o.displayClientMessage(msg.copy().withStyle(ChatFormatting.GOLD), false);
        }
        if (stopped) return;
        for (PartyBotEntity x : new PartyBotEntity[]{a, b}) {
            PartyBotEntity other = x == a ? b : a;
            boolean won = x == winner;
            float mult = (won ? 1F : 0.6F) * Math.max(0.5F, Math.min(3F, 1F + (other.botLevel() - x.botLevel()) / 10F));
            for (Stat s : Stat.values()) if (s.ordinal() < 3) PartyBots.addXp(x, s, 40F * mult);
            float tp = tp(x, won, true);
            if (tp > 0) com.xenowarriors.compat.DmzBots.giveTp(x, tp);
        }
    }

    /** Dragon Ball TP for a spar (0 without DragonMineZ): about as much as clearing ~8 levels of a training minigame. */
    public static float tp(LivingEntity who, boolean won, boolean big) {
        float per = com.xenowarriors.compat.DmzBotMinigames.perLevelTp(who);
        if (per <= 0) return 0;
        return per * (big ? 8F : 3F) * (won ? 1F : 0.7F);
    }

    /** A bot sparring a bot: it never goes down - at 30% health the spar is over and the other one wins. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof PartyBotEntity b)) return;
        if (inMatch(b)) {                                                         // a 2v2: at 30% it's out of the match
            if (!PartyBots.sparFoes(b, e.getSource().getEntity()) || b.getHealth() - e.getAmount() >= b.getMaxHealth() * 0.3F) return;
            e.setCanceled(true);
            out(b);
            return;
        }
        if (b.sparFoe == null) return;
        if (!(e.getSource().getEntity() instanceof PartyBotEntity f) || !b.sparFoe.equals(f.getUUID())) return;
        if (b.getHealth() - e.getAmount() >= b.getMaxHealth() * 0.3F) return;
        e.setCanceled(true);
        endBots(b, f, f, b.owner(), "won");
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenospar")
                .then(Commands.literal("yes").then(Commands.argument("bot", IntegerArgumentType.integer()).executes(c -> answer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "bot"), true))))
                .then(Commands.literal("no").then(Commands.argument("bot", IntegerArgumentType.integer()).executes(c -> answer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "bot"), false))))
                .then(Commands.literal("team").then(teamAnswer("yes", true)).then(teamAnswer("no", false))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> teamAnswer(String word, boolean yes) {
        return Commands.literal(word).then(Commands.argument("a", IntegerArgumentType.integer()).then(Commands.argument("b", IntegerArgumentType.integer())
                .then(Commands.argument("c", IntegerArgumentType.integer()).executes(c -> answerTeams(c.getSource().getPlayerOrException(), yes,
                        IntegerArgumentType.getInteger(c, "a"), IntegerArgumentType.getInteger(c, "b"), IntegerArgumentType.getInteger(c, "c"))))));
    }

    private static int answerTeams(ServerPlayer p, boolean yes, int a, int b, int c) {
        com.xenowarriors.network.RequestPopupPacket.dismiss(p, "/xenospar team yes " + a + " " + b + " " + c);
        if (!(p.level().getEntity(a) instanceof PartyBotEntity ba) || !(p.level().getEntity(b) instanceof PartyBotEntity bb) || !(p.level().getEntity(c) instanceof PartyBotEntity bc)) return 0;
        if (!yes) { p.displayClientMessage(Component.empty().append(ba.label()).append(": \"Aw. Another time!\"").withStyle(ChatFormatting.GRAY), true); return 1; }
        if (!startTeams(ba, bb, bc, p)) { p.displayClientMessage(Component.literal("They can't spar right now.").withStyle(ChatFormatting.GRAY), true); return 0; }
        return 1;
    }

    private static int answer(ServerPlayer p, int id, boolean yes) {
        if (!(p.level().getEntity(id) instanceof PartyBotEntity b) || !p.getUUID().equals(b.ownerId())) return 0;
        com.xenowarriors.network.RequestPopupPacket.dismiss(p, "/xenospar yes " + id);
        if (!yes) { p.displayClientMessage(Component.empty().append(b.label()).append(": \"Maybe later, then.\"").withStyle(ChatFormatting.GRAY), true); return 1; }
        if (b.downed() || b.sparring() || b.getTarget() != null) return 0;
        PartyBots.action(p, b.getId(), PartyBots.ACT_SPAR, 0);
        return 1;
    }
}
