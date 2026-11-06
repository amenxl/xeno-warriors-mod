package com.xenowarriors.bot;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.party.TeamUltimate;
import com.xenowarriors.skill.SkillManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bot teammates and team ultimates. Only in a fight that's worth one (see {@link #worthIt}: a lot of enemies around -
 * at least {@link #CROWD} within {@link #CROWD_RADIUS} blocks of the bot or its player - or a very strong one: a boss,
 * or something at least 3x the player's Karma Sense strength, the same "very strong" as the +100 Stat Boost drops),
 * a Rhalix or Mikiko bot whose ultimate and Karma are ready looks for its partner nearby:
 * <ul>
 *   <li>another bot that's ready too: they just do it together (most of the time - a few seconds at most);</li>
 *   <li>a player that's ready too: the bot asks them ("Rhalix: Ready for Blazing Judgment!"), and they join with
 *       their ultimate key or [Join]. Not more than once every {@link #ASK_COOLDOWN} ticks per bot, and
 *       {@link #PLAYER_COOLDOWN} per player, and never while a request is already waiting.</li>
 * </ul>
 * While a partner is ready (and the fight is worth it), a bot also holds back its own solo ultimate so the two can
 * team up. A couple of weak mobs never get a team ultimate, a request or a held-back ultimate.
 */
public final class BotTeamUlt {
    public static final int ASK_COOLDOWN = 30 * 20, PLAYER_COOLDOWN = 15 * 20;
    /** "A lot of enemies": at least this many hostile ones within {@link #CROWD_RADIUS} blocks of the bot or its player. */
    public static final int CROWD = 6;
    public static final double CROWD_RADIUS = 16;
    private static final Map<UUID, Long> NEXT_ASK = new HashMap<>(), PLAYER_NEXT = new HashMap<>();
    /** Bots waiting for their player's answer: who they asked (they keep their Karma for it meanwhile). */
    private static final Map<UUID, UUID> WAITING = new HashMap<>();
    /** (tests) requests sent and bot + bot team ultimates started. */
    public static int asked, botPairs;

    private BotTeamUlt() {}

    public static boolean ultReady(PartyBotEntity b, int karma) {
        return b.isAlive() && !b.downed() && b.level().getGameTime() >= b.skillReady[SkillManager.ULTIMATE_SLOT] && b.karma >= karma;
    }

    public static void spend(PartyBotEntity b, int karma, int cooldown) {
        b.karma = Math.max(0F, b.karma - karma);
        b.skillReady[SkillManager.ULTIMATE_SLOT] = b.level().getGameTime() + cooldown;
    }

    /** (tests) the bot's ultimate ready now, with full Karma. */
    public static void readyForTest(PartyBotEntity b) {
        b.karma = 100F;
        b.skillReady[SkillManager.ULTIMATE_SLOT] = 0;
        NEXT_ASK.remove(b.getUUID());
        WAITING.remove(b.getUUID());
        if (b.owner() != null) PLAYER_NEXT.remove(b.owner().getUUID());
    }

    private static boolean pairCharacter(PartyBotEntity b) {
        return b.character() == CharacterType.RHALIX || b.character() == CharacterType.MIKIKO;
    }

    /**
     * A fight worth a team ultimate: a lot of enemies ({@link #CROWD}+ hostile ones within {@link #CROWD_RADIUS} blocks
     * of the bot or its player), or a very strong one - a boss, or at least 3x the player's Karma Sense strength
     * ({@link com.xenowarriors.statboost.StatBoosts#veryStrong}; the bot's own strength when it has no player).
     */
    public static boolean worthIt(PartyBotEntity b, LivingEntity target) {
        ServerPlayer o = b.owner();
        LivingEntity ref = o != null && o.level() == b.level() ? o : b;
        Set<LivingEntity> foes = new HashSet<>(TeamUltimate.foesNear(b, CROWD_RADIUS));
        if (ref != b) foes.addAll(TeamUltimate.foesNear(ref, CROWD_RADIUS));
        if (foes.size() >= CROWD) return true;
        if (target != null && target.isAlive() && target != ref) foes.add(target);           // (its target even when it's further away - or a player it duels)
        for (LivingEntity f : foes) if (com.xenowarriors.statboost.StatBoosts.veryStrong(f, ref)) return true;
        return false;
    }

    /** A partner that's ready for a team ultimate right now (or null). */
    static LivingEntity readyPartner(PartyBotEntity b) {
        if (!pairCharacter(b) || b.sparring() || b.journey()) return null;
        for (LivingEntity o : TeamUltimate.partners(b)) if (TeamUltimate.ready(o, false)) return o;
        return null;
    }

    /** Hold back the solo ultimate: a partner is ready to team up instead (only in a fight worth a team ultimate). */
    public static boolean saveUltimate(PartyBotEntity b, LivingEntity target) {
        return b.getHealth() > b.getMaxHealth() * 0.3F && readyPartner(b) != null && worthIt(b, target);
    }

    /**
     * Called with the bot's skill checks (every half second in a fight). True if it did something team-ultimate
     * (started one, asked its player, or is in one) - no other skill this time.
     */
    public static boolean tick(PartyBotEntity b, LivingEntity target) {
        if (!pairCharacter(b)) return false;
        if (TeamUltimate.busy(b)) return true;
        UUID waitingOn = WAITING.get(b.getUUID());
        if (waitingOn != null) {                                                    // waiting for an answer: no other skills (it keeps the Karma)
            ServerPlayer p = b.getServer() == null ? null : b.getServer().getPlayerList().getPlayer(waitingOn);
            Object[] pend = p == null ? null : TeamUltimate.pending(p);
            if (pend != null && b.getUUID().equals(pend[0])) return true;
            WAITING.remove(b.getUUID());
        }
        if (b.sparring() || b.journey() || !ultReady(b, TeamUltimate.KARMA) || target == null || !worthIt(b, target)) return false;
        long now = b.level().getGameTime();
        for (LivingEntity o : TeamUltimate.partners(b)) {
            if (!TeamUltimate.ready(o, false)) continue;
            List<TeamUltimate.Kind> kinds = TeamUltimate.kinds(b, o);
            TeamUltimate.Kind k = kinds.size() > 1 && (TeamUltimate.foesNear(target, 8).size() >= 2 || b.getRandom().nextInt(10) < 6)
                    ? TeamUltimate.Kind.BLAZING_JUDGMENT : kinds.get(b.getRandom().nextInt(kinds.size()));
            if (o instanceof PartyBotEntity ob) {
                if (b.getRandom().nextInt(3) != 0) return false;                  // (one of the two goes first, within a second or two)
                if (TeamUltimate.startNow(b, ob, k)) { botPairs++; return true; }
                continue;
            }
            if (o instanceof ServerPlayer p) {
                if (now < NEXT_ASK.getOrDefault(b.getUUID(), 0L) || now < PLAYER_NEXT.getOrDefault(p.getUUID(), 0L) || TeamUltimate.pending(p) != null) return false;
                TeamUltimate.ask(b, p, k);
                WAITING.put(b.getUUID(), p.getUUID());
                NEXT_ASK.put(b.getUUID(), now + ASK_COOLDOWN);
                PLAYER_NEXT.put(p.getUUID(), now + PLAYER_COOLDOWN);
                asked++;
                PartyBots.testLog(b, "team ult request " + k.title + " -> " + p.getGameProfile().getName());
                return true;
            }
        }
        return false;
    }

    public static void forget(UUID bot) { NEXT_ASK.remove(bot); WAITING.remove(bot); }
}
