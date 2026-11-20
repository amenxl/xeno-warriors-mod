package com.xenowarriors.bot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.stats.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * More party games (BotPartyTime), now and then through a party:
 * <ul>
 * <li><b>Arm wrestling.</b> Two bots grab hands at the middle and strain (the stronger Attack usually wins), or a bot
 * challenges you (a card to accept): mash left click to push its arm down before it pushes yours - the stronger it is
 * next to you, the faster you'll have to click. Beat it for Xeno Warriors XP (Attack, Defense) and, with DragonMineZ,
 * TP.</li>
 * <li><b>Memory</b> (also called Concentration or Pairs). A bot asks you to play: 16 cards face down, 8 pairs of the
 * show's characters. Flip two - a pair stays up and you go again, otherwise it's the bot's turn (it remembers what it's
 * seen, mostly). Most pairs wins: XP (Speed) and TP. Either way you two get a little closer.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = com.xenowarriors.XenoWarriors.MOD_ID)
public final class BotPartyGames {
    private BotPartyGames() {}

    public static final int FIRST = 20 * 75, ASK_TICKS = 20 * 15, ARM_TIME = 20, MEM_PAIRS = 8;
    public static final float PLAYER_CHANCE = 0.5F, ARM_BOND = 6F, MEM_BOND = 10F, BOT_XP = 25F;
    /** The faces Memory's cards can have: the show's characters (portrait_<id>_normal). */
    public static final String[] FACES = {"mikiko", "rhalix", "lexy", "kill", "kyrie", "kaeru", "max", "aioni", "garry", "keita", "logan", "kyle",
            "chase", "ava", "persephone", "athrea", "ranrea", "fox_girl", "kaitlyn"};

    private static final class Games { long next; int kind; UUID bot; long asked, started; boolean playing; }
    private static final Map<UUID, Games> GAMES = new HashMap<>();
    /** (Tests) */
    public static int botArms, armAsks, armPlayed, armWins, memAsks, memPlayed, memWins;
    public static Integer forceKind;    // 0 arm (bots), 1 arm (you), 2 memory (you)

    static void partyStarted(ServerPlayer o, long now) {
        Games g = new Games();
        g.next = now + FIRST;
        GAMES.put(o.getUUID(), g);
    }

    static void partyEnded(ServerPlayer o) {
        Games g = GAMES.remove(o.getUUID());
        if (g != null && g.bot != null) {
            com.xenowarriors.network.RequestPopupPacket.dismiss(o, "/xenopartygame yes " + g.kind);
            ModNetwork.sendToPlayer(new com.xenowarriors.network.PartyGamePacket(-1, -1, "", 0F, ""), o);   // (its screen closes)
        }
    }

    static void tick(ServerPlayer o, List<LivingEntity> guests, long now) {
        Games g = GAMES.get(o.getUUID());
        if (g == null) return;
        if (g.bot != null) {                                                     // a challenge waiting for you, or a game on
            long limit = g.playing ? g.started + 20 * 90 : g.asked + ASK_TICKS;
            if (now > limit) {
                if (!g.playing) o.displayClientMessage(Component.literal("The " + (g.kind == 2 ? "Memory" : "arm wrestling") + " challenge passed you by.").withStyle(ChatFormatting.GRAY), false);
                com.xenowarriors.network.RequestPopupPacket.dismiss(o, "/xenopartygame yes " + g.kind);
                g.bot = null; g.playing = false; g.next = now + 20 * 30;
            }
            return;
        }
        if (now < g.next || BotRps.waitingForTest(o)) return;
        List<PartyBotEntity> bots = new ArrayList<>();
        for (LivingEntity e : guests) if (e instanceof PartyBotEntity b && !b.downed() && b.getTarget() == null) bots.add(b);
        if (bots.isEmpty()) { g.next = now + 20 * 10; return; }
        Collections.shuffle(bots, new Random(o.getRandom().nextLong()));
        int kind = forceKind != null ? forceKind : bots.size() >= 2 && o.getRandom().nextFloat() > PLAYER_CHANCE ? 0 : o.getRandom().nextBoolean() ? 1 : 2;
        forceKind = null;
        g.next = now + 20 * (50 + o.getRandom().nextInt(40));
        if (kind == 0 && bots.size() >= 2) { botArm(o, bots.get(0), bots.get(1)); return; }
        if (kind == 0) kind = 1;
        PartyBotEntity b = bots.get(0);
        g.kind = kind; g.bot = b.getUUID(); g.asked = now; g.playing = false;
        String n = b.character().getDisplayName().getString();
        if (kind == 1) {
            armAsks++;
            say(o, b, "Arm wrestle me! Bet you can't beat me.");
            BotRequests.send(o, new com.xenowarriors.network.RequestPopupPacket(n + " wants to arm wrestle!", "Mash left click to push their arm down - win for XP and TP.",
                    "/xenopartygame yes 1", "/xenopartygame no 1", ASK_TICKS, 0xFFFFB040));
        } else {
            memAsks++;
            say(o, b, "Want to play Memory? Flip the cards, find the pairs!");
            BotRequests.send(o, new com.xenowarriors.network.RequestPopupPacket(n + " wants to play Memory!", "Flip two cards at a time and find the pairs - most pairs wins.",
                    "/xenopartygame yes 2", "/xenopartygame no 2", ASK_TICKS, 0xFF60D0FF));
        }
    }

    // ---------------------------------------------------------------- arm wrestling, bot vs bot

    static void botArm(ServerPlayer o, PartyBotEntity a, PartyBotEntity b) {
        botArms++;
        say(o, a, "Hey " + b.character().getDisplayName().getString() + " - arm wrestle!");
        ServerLevel l = o.serverLevel();
        Vec3 mid = a.position().add(b.position()).scale(0.5);
        for (int i = 0; i < 8; i++) {
            int k = i;
            com.xenowarriors.skill.ServerScheduler.later(10 + i * 8, () -> {
                if (!a.isAlive() || !b.isAlive()) return;
                a.getNavigation().stop(); b.getNavigation().stop();
                a.getLookControl().setLookAt(b); b.getLookControl().setLookAt(a);
                if (k % 2 == 0) a.swing(net.minecraft.world.InteractionHand.MAIN_HAND); else b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                l.sendParticles(ParticleTypes.CRIT, mid.x, mid.y + 1.1, mid.z, 3, 0.15, 0.1, 0.15, 0.05);
            });
        }
        com.xenowarriors.skill.ServerScheduler.later(10 + 8 * 8 + 6, () -> {
            if (!a.isAlive() || !b.isAlive()) return;
            float pa = PartyBots.statLevel(a, Stat.ATTACK) * (0.7F + o.getRandom().nextFloat() * 0.6F), pb = PartyBots.statLevel(b, Stat.ATTACK) * (0.7F + o.getRandom().nextFloat() * 0.6F);
            PartyBotEntity w = pa >= pb ? a : b, lo = w == a ? b : a;
            o.displayClientMessage(Component.empty().append(w.label()).append(" slams ").append(lo.label()).append("'s arm down - wins the arm wrestle!").withStyle(ChatFormatting.GOLD), false);
            botReward(w);
            com.xenowarriors.party.Bonds.addXp(a, b.charKey(), ARM_BOND);
            com.xenowarriors.party.Bonds.addXp(b, a.charKey(), ARM_BOND);
            cheer(w);
        });
    }

    // ---------------------------------------------------------------- with you

    private static int answer(ServerPlayer o, int kind, boolean yes) {
        Games g = GAMES.get(o.getUUID());
        com.xenowarriors.network.RequestPopupPacket.dismiss(o, "/xenopartygame yes " + kind);
        if (g == null || g.bot == null || g.playing || g.kind != kind || !(o.serverLevel().getEntity(g.bot) instanceof PartyBotEntity b)) return 0;
        if (!yes) {
            o.displayClientMessage(Component.empty().append(b.label()).append(": \"Next time, then!\"").withStyle(ChatFormatting.GRAY), true);
            g.bot = null; g.next = o.level().getGameTime() + 20 * 30;
            return 1;
        }
        start(o, g, b);
        return 1;
    }

    private static void start(ServerPlayer o, Games g, PartyBotEntity b) {
        g.playing = true; g.started = o.level().getGameTime();
        b.getNavigation().stop();
        b.getLookControl().setLookAt(o);
        String n = b.character().getDisplayName().getString();
        if (g.kind == 1) {
            armPlayed++;
            ModNetwork.sendToPlayer(new com.xenowarriors.network.PartyGamePacket(1, b.getId(), n, armPush(o, b), b.character().getId()), o);
        } else {
            memPlayed++;
            List<String> faces = new ArrayList<>(Arrays.asList(FACES));
            Collections.shuffle(faces, new Random(o.getRandom().nextLong()));
            String deck = String.join(",", faces.subList(0, MEM_PAIRS));
            float skill = Mth.clamp(0.45F + b.botLevel() * 0.004F, 0.45F, 0.85F);   // (how well it remembers)
            ModNetwork.sendToPlayer(new com.xenowarriors.network.PartyGamePacket(2, b.getId(), n, skill, deck + ";" + o.getRandom().nextLong()), o);
        }
    }

    /** How hard the bot pushes your arm back, each tick (the screen): stronger next to you, harder. */
    public static float armPush(ServerPlayer o, PartyBotEntity b) {
        float you = Math.max(1, com.xenowarriors.stats.Stats.level(o, Stat.ATTACK)), it = Math.max(1, PartyBots.statLevel(b, Stat.ATTACK));
        return 0.0095F * Mth.clamp(it / you, 0.6F, 1.7F);
    }

    /** The game's over on your screen: {@code mine} and {@code theirs} - for arm wrestling 1/0 (you won) or 0/1. */
    public static void result(ServerPlayer o, int botId, int mine, int theirs) {
        Games g = GAMES.get(o.getUUID());
        if (g == null || g.bot == null || !g.playing || !(o.level().getEntity(botId) instanceof PartyBotEntity b) || !b.getUUID().equals(g.bot)) return;
        long took = o.level().getGameTime() - g.started;
        int kind = g.kind;
        g.bot = null; g.playing = false; g.next = o.level().getGameTime() + 20 * 45;
        if (took < 20) return;                                                   // (too quick to be real)
        String n = b.character().getDisplayName().getString();
        com.xenowarriors.party.Bonds.addXp(o, b.charKey(), kind == 1 ? ARM_BOND : MEM_BOND);
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        boolean won = mine > theirs;
        if (kind == 1) {
            if (won) {
                armWins++;
                o.displayClientMessage(Component.literal("You slam " + n + "'s arm down - you win the arm wrestle!").withStyle(ChatFormatting.GOLD), false);
                reward(o, new Stat[]{Stat.ATTACK, Stat.DEFENSE}, 0.2F, 0.6F, "arm wrestling");
            } else {
                o.displayClientMessage(Component.literal(n + " pins your arm - they win the arm wrestle!").withStyle(ChatFormatting.YELLOW), false);
                botReward(b); cheer(b);
            }
        } else {
            if (won) {
                memWins++;
                o.displayClientMessage(Component.literal("You win Memory against " + n + ", " + mine + " pairs to " + theirs + "!").withStyle(ChatFormatting.GOLD), false);
                reward(o, new Stat[]{Stat.SPEED}, 0.25F, 0.5F, "Memory");
            } else if (mine == theirs) o.displayClientMessage(Component.literal("Memory with " + n + ": " + mine + " pairs each - a draw!").withStyle(ChatFormatting.GRAY), false);
            else {
                o.displayClientMessage(Component.literal(n + " wins Memory, " + theirs + " pairs to " + mine + "!").withStyle(ChatFormatting.YELLOW), false);
                botReward(b); cheer(b);
            }
        }
    }

    private static void reward(ServerPlayer o, Stat[] stats, float share, float tpShare, String what) {
        for (Stat s : stats) com.xenowarriors.stats.Stats.addXp(o, s, s.needed(com.xenowarriors.stats.Stats.level(o, s)) * share);
        float tp = BotSpar.tp(o, true, false) * tpShare;
        if (tp >= 1) com.xenowarriors.compat.DmzTpRewards.give(o, Math.round(tp), what);
        cheer(o);
    }

    private static void botReward(PartyBotEntity w) {
        for (Stat s : Stat.values()) PartyBots.addXp(w, s, BOT_XP);
        float tp = BotSpar.tp(w, true, false) * 0.5F;
        if (tp > 0) com.xenowarriors.compat.DmzBots.giveTp(w, tp);
    }

    private static void cheer(LivingEntity w) {
        if (w.level() instanceof ServerLevel l) {
            l.sendParticles(ParticleTypes.HAPPY_VILLAGER, w.getX(), w.getEyeY() + 0.5, w.getZ(), 10, 0.4, 0.3, 0.4, 0.1);
            l.playSound(null, w.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5F, 1.6F);
        }
    }

    private static void say(ServerPlayer o, PartyBotEntity b, String s) {
        o.displayClientMessage(Component.empty().append(b.label()).append(": \"" + s + "\"").withStyle(ChatFormatting.AQUA), false);
        PartyBots.testLog(b, "party game: " + s);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("xenopartygame")
                .then(Commands.literal("yes").then(Commands.argument("kind", IntegerArgumentType.integer(1, 2)).executes(c -> answer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "kind"), true))))
                .then(Commands.literal("no").then(Commands.argument("kind", IntegerArgumentType.integer(1, 2)).executes(c -> answer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "kind"), false)))));
    }

    /** (Tests) a game now: 0 bots arm wrestle, 1 a bot challenges you to arm wrestle, 2 to Memory. */
    public static void nowForTest(ServerPlayer o, int kind) { Games g = GAMES.get(o.getUUID()); if (g != null) { g.next = 0; g.bot = null; g.playing = false; forceKind = kind; } }
    public static boolean askingForTest(ServerPlayer o) { Games g = GAMES.get(o.getUUID()); return g != null && g.bot != null && !g.playing; }
    public static int answerForTest(ServerPlayer o, int kind) { return answer(o, kind, true); }
    public static void ageForTest(ServerPlayer o, int ticks) { Games g = GAMES.get(o.getUUID()); if (g != null) g.started -= ticks; }

    /** The world closed (back to the title screen / another world): nothing of this one carries over. (ServerReset) */
    public static void serverReset() { GAMES.clear(); }
}
