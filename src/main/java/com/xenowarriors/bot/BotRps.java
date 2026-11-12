package com.xenowarriors.bot;

import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.RpsPacket;
import com.xenowarriors.stats.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;

/**
 * Rock, paper, scissors at a party (BotPartyTime): every party has at least one game - the first about half a minute
 * in, then now and then. Two bots challenge each other ("Rock... paper... scissors... shoot!"), or a bot challenges
 * you: a little screen pops up to pick your hand (10 seconds). A draw goes again (three rounds at most). The winner
 * gets Xeno Warriors XP (Speed, Attack, Defense) and, with DragonMineZ, Dragon Ball TP.
 */
public final class BotRps {
    private BotRps() {}

    public static final String[] HANDS = {"Rock", "Paper", "Scissors"};
    public static final int FIRST = 20 * 30, PICK_TICKS = 20 * 12, MAX_ROUNDS = 3;
    public static final float PLAYER_CHANCE = 0.4F, XP = 25F;

    private static final class Game { long next; UUID vsYou; int round; long expires; }
    private static final Map<UUID, Game> GAMES = new HashMap<>();   // by party host
    /** (Tests) games played between bots / against you; your wins. */
    public static int botGames, playerGames, playerWins;
    /** (Tests) the next challenge goes to the player. */
    public static Boolean forcePlayer;

    /** 0 draw, 1 a wins, 2 b wins. */
    public static int beats(int a, int b) { return a == b ? 0 : (a - b + 3) % 3 == 1 ? 1 : 2; }

    static void partyStarted(ServerPlayer o, long now) {
        Game g = new Game();
        g.next = now + FIRST;                                                    // (every party: the first game half a minute in)
        GAMES.put(o.getUUID(), g);
    }

    static void partyEnded(ServerPlayer o) {
        Game g = GAMES.remove(o.getUUID());
        if (g != null && g.vsYou != null) ModNetwork.sendToPlayer(new RpsPacket(-1, "", 0, -1, -1, 3), o);   // (its screen closes)
    }

    static void tick(ServerPlayer o, List<LivingEntity> guests, long now) {
        Game g = GAMES.get(o.getUUID());
        if (g == null) return;
        if (g.vsYou != null) {                                                   // waiting for your pick
            if (now > g.expires) {
                g.vsYou = null;
                o.displayClientMessage(Component.literal("Too slow - the rock, paper, scissors challenge passed you by.").withStyle(ChatFormatting.GRAY), false);
                ModNetwork.sendToPlayer(new RpsPacket(-1, "", 0, -1, -1, 3), o);
                g.next = now + 20 * 30;
            }
            return;
        }
        if (now < g.next) return;
        List<PartyBotEntity> bots = new ArrayList<>();
        for (LivingEntity e : guests) if (e instanceof PartyBotEntity b && !b.downed() && b.getTarget() == null) bots.add(b);
        if (bots.isEmpty()) { g.next = now + 20 * 10; return; }
        Collections.shuffle(bots, new Random(o.getRandom().nextLong()));
        boolean you = forcePlayer != null ? forcePlayer : bots.size() < 2 || o.getRandom().nextFloat() < PLAYER_CHANCE;
        forcePlayer = null;
        g.next = now + 20 * (40 + o.getRandom().nextInt(30));
        if (you) challenge(o, g, bots.get(0), now);
        else botGame(o, bots.get(0), bots.get(1));
    }

    // ---------------------------------------------------------------- bot vs bot

    static void botGame(ServerPlayer o, PartyBotEntity a, PartyBotEntity b) {
        botGames++;
        say(o, a, "Hey " + b.character().getDisplayName().getString() + " - rock, paper, scissors!");
        a.getLookControl().setLookAt(b); b.getLookControl().setLookAt(a);
        round(o, a, b, 1);
    }

    private static void round(ServerPlayer o, PartyBotEntity a, PartyBotEntity b, int n) {
        String[] count = {"Rock...", "paper...", "scissors...", "shoot!"};
        for (int i = 0; i < count.length; i++) {
            int k = i;
            com.xenowarriors.skill.ServerScheduler.later(20 + i * 12, () -> {
                if (!a.isAlive() || !b.isAlive()) return;
                a.swing(net.minecraft.world.InteractionHand.MAIN_HAND); b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                if (k == count.length - 1) o.displayClientMessage(Component.literal(count[k]).withStyle(ChatFormatting.YELLOW), true);
            });
        }
        com.xenowarriors.skill.ServerScheduler.later(20 + count.length * 12, () -> {
            if (!a.isAlive() || !b.isAlive()) return;
            int pa = o.getRandom().nextInt(3), pb = o.getRandom().nextInt(3);
            int r = beats(pa, pb);
            o.displayClientMessage(Component.empty().append(a.label()).append(": " + HANDS[pa] + "!  ").append(b.label()).append(": " + HANDS[pb] + "!").withStyle(ChatFormatting.YELLOW), false);
            if (r == 0) {
                if (n < MAX_ROUNDS) { o.displayClientMessage(Component.literal("A draw - again!").withStyle(ChatFormatting.GRAY), false); round(o, a, b, n + 1); }
                else o.displayClientMessage(Component.literal("Three draws - they call it even.").withStyle(ChatFormatting.GRAY), false);
                return;
            }
            PartyBotEntity w = r == 1 ? a : b, lo = r == 1 ? b : a;
            o.displayClientMessage(Component.empty().append(w.label()).append(" wins rock, paper, scissors against ").append(lo.label()).append("!").withStyle(ChatFormatting.GOLD), false);
            reward(w);
            cheer(w);
        });
    }

    private static void reward(PartyBotEntity w) {
        for (Stat s : Stat.values()) if (s.ordinal() < 3) PartyBots.addXp(w, s, XP);
        float tp = BotSpar.tp(w, true, false) * 0.5F;
        if (tp > 0) com.xenowarriors.compat.DmzBots.giveTp(w, tp);
    }

    private static void cheer(LivingEntity w) {
        if (w.level() instanceof ServerLevel l) {
            l.sendParticles(ParticleTypes.HAPPY_VILLAGER, w.getX(), w.getEyeY() + 0.5, w.getZ(), 10, 0.4, 0.3, 0.4, 0.1);
            l.playSound(null, w.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5F, 1.6F);
        }
    }

    // ---------------------------------------------------------------- a bot vs you

    static void challenge(ServerPlayer o, Game g, PartyBotEntity b, long now) {
        playerGames++;
        g.vsYou = b.getUUID(); g.round = 1; g.expires = now + PICK_TICKS;
        b.getLookControl().setLookAt(o);
        say(o, b, "Rock, paper, scissors - you and me!");
        ModNetwork.sendToPlayer(new RpsPacket(b.getId(), b.character().getDisplayName().getString(), 1, -1, -1, -1), o);
    }

    /** Your pick (from the screen). */
    public static void picked(ServerPlayer o, int botId, int pick) {
        Game g = GAMES.get(o.getUUID());
        if (g == null || g.vsYou == null || pick < 0 || pick > 2 || !(o.level().getEntity(botId) instanceof PartyBotEntity b) || !b.getUUID().equals(g.vsYou)) return;
        int bp = o.getRandom().nextInt(3);
        int r = beats(pick, bp);
        String name = b.character().getDisplayName().getString();
        if (r == 0 && g.round < MAX_ROUNDS) {
            g.round++;
            g.expires = o.level().getGameTime() + PICK_TICKS;
            ModNetwork.sendToPlayer(new RpsPacket(b.getId(), name, g.round, bp, pick, 0), o);
            return;
        }
        g.vsYou = null;
        ModNetwork.sendToPlayer(new RpsPacket(b.getId(), name, g.round, bp, pick, r == 0 ? 0 : r), o);
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (r == 1) {
            playerWins++;
            o.displayClientMessage(Component.literal("You beat " + name + " at rock, paper, scissors! (" + HANDS[pick] + " beats " + HANDS[bp] + ")").withStyle(ChatFormatting.GOLD), false);
            for (Stat s : Stat.values()) if (s.ordinal() < 3) com.xenowarriors.stats.Stats.addXp(o, s, s.needed(com.xenowarriors.stats.Stats.level(o, s)) * 0.15F);
            float tp = BotSpar.tp(o, true, false) * 0.5F;
            if (tp >= 1) com.xenowarriors.compat.DmzTpRewards.give(o, Math.round(tp), "rock, paper, scissors");
            cheer(o);
        } else if (r == 2) {
            o.displayClientMessage(Component.literal(name + " beats you at rock, paper, scissors! (" + HANDS[bp] + " beats " + HANDS[pick] + ")").withStyle(ChatFormatting.YELLOW), false);
            reward(b);
            cheer(b);
        } else o.displayClientMessage(Component.literal("Three draws with " + name + " - call it even!").withStyle(ChatFormatting.GRAY), false);
    }

    private static void say(ServerPlayer o, PartyBotEntity b, String s) {
        o.displayClientMessage(Component.empty().append(b.label()).append(": \"" + s + "\"").withStyle(ChatFormatting.AQUA), false);
        PartyBots.testLog(b, "rps: " + s);
    }

    /** (Tests) a game now: you or two bots. */
    public static void nowForTest(ServerPlayer o, boolean you) { Game g = GAMES.get(o.getUUID()); if (g != null) { g.next = 0; g.vsYou = null; forcePlayer = you; } }
    public static boolean waitingForTest(ServerPlayer o) { Game g = GAMES.get(o.getUUID()); return g != null && g.vsYou != null; }
}
