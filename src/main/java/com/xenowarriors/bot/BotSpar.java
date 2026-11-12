package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.stats.Stat;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
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
 *       down), or a minute is up, or something hostile turns up.</li>
 * </ul>
 * Sparring gives Speed / Attack / Defense XP, and with DragonMineZ Dragon Ball TP too ({@link #tp}) - for you
 * sparring a bot, and for both bots sparring each other.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotSpar {
    private BotSpar() {}

    /** Ticks between two spars started by themselves (about 5 minutes); longest bot-vs-bot spar; how long a request lasts. */
    public static final int GAP = 20 * 60 * 5, MAX_SPAR = 20 * 60, ASK_TICKS = 20 * 20;
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

    private static boolean free(PartyBotEntity b, ServerPlayer o) {
        return !b.downed() && !b.sparring() && !b.journey() && !b.isPassenger() && b.getTarget() == null && b.getHealth() > b.getMaxHealth() * 0.8F
                && b.level() == o.level() && b.distanceToSqr(o) < 16 * 16 && BotErrands.danger(b) == null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer o) || o instanceof FakePlayer) return;
        if (o.tickCount % 20 == 13) watch(o);
        if (o.tickCount % 200 != 113) return;
        long now = o.level().getGameTime(), last = o.getPersistentData().getLong(LAST);
        if (last > 0 && now >= last && now - last < GAP) return;
        if (o.isSpectator() || o.getAbilities().flying || o.getVehicle() != null || !quiet(o)) return;
        if (o.getRandom().nextInt(3) != 0) return;
        List<PartyBotEntity> free = new ArrayList<>();
        for (PartyBotEntity b : PartyBots.bots(o)) if (free(b, o)) free.add(b);
        if (free.isEmpty()) return;
        Collections.shuffle(free, new Random(o.getRandom().nextLong()));
        if (free.size() >= 2 && o.getRandom().nextBoolean()) startBots(free.get(0), free.get(1), o);
        else ask(free.get(0), o);
        o.getPersistentData().putLong(LAST, now);
    }

    /** A bot asks its player for a spar. */
    public static void ask(PartyBotEntity b, ServerPlayer o) {
        asked++;
        String yes = "/xenospar yes " + b.getId(), no = "/xenospar no " + b.getId();
        com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.RequestPopupPacket(b.character().getDisplayName().getString() + " wants to spar",
                "A friendly fight for some training? (nobody goes down)", yes, no, ASK_TICKS, 0xFFFFB040), o);
        o.displayClientMessage(Component.empty().append(b.label()).append(": \"Hey - want to spar?\"").withStyle(ChatFormatting.GOLD), false);
    }

    /** Two bots spar each other. */
    public static boolean startBots(PartyBotEntity a, PartyBotEntity b, ServerPlayer o) {
        if (a == b || a.sparring() || b.sparring()) return false;
        long end = a.level().getGameTime() + MAX_SPAR;
        a.sparFoe = b.getUUID(); b.sparFoe = a.getUUID();
        a.setSparring(true); b.setSparring(true);
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
        if (e.isCanceled() || !(e.getEntity() instanceof PartyBotEntity b) || b.sparFoe == null) return;
        if (!(e.getSource().getEntity() instanceof PartyBotEntity f) || !b.sparFoe.equals(f.getUUID())) return;
        if (b.getHealth() - e.getAmount() >= b.getMaxHealth() * 0.3F) return;
        e.setCanceled(true);
        endBots(b, f, f, b.owner(), "won");
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenospar")
                .then(Commands.literal("yes").then(Commands.argument("bot", IntegerArgumentType.integer()).executes(c -> answer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "bot"), true))))
                .then(Commands.literal("no").then(Commands.argument("bot", IntegerArgumentType.integer()).executes(c -> answer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "bot"), false)))));
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
