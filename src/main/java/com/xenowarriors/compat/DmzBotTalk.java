package com.xenowarriors.compat;

import com.xenowarriors.XwConfig;
import com.xenowarriors.bot.BotErrands;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * With DragonMineZ: a bot teammate walks up to the people its own missions wait on and talks to them itself - a "talk
 * to" objective (Dende, a master...), handing a finished mission in, or taking a mission from its giver when you start
 * yours. It goes to the real one when they're within 48 blocks and it can walk there, else to the copy that came to
 * you (DmzTalkNpcs); the talk counts exactly as yours would for it, a chat line says so, and the copy leaves once
 * nobody needs it. Only when things are quiet (the same test as its errands: no fight, nobody hurt, no enemies about),
 * one at a time, giving up after 30 s or when it can't get closer (you can still talk to them for it). Off with
 * botsTalkToNpcs=false.
 */
public final class DmzBotTalk extends Goal {
    public static final double REACH = 48;
    private static final int GIVE_UP = 20 * 30, RETRY = 20 * 30;

    private final PartyBotEntity b;
    private final Map<String, Integer> resting = new HashMap<>();
    private int nextLook, until, stuck;
    private double closest;
    private Entity to;
    private String[] job;
    private boolean done;

    public DmzBotTalk(PartyBotEntity b) {
        this.b = b;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        nextLook = 40 + b.getRandom().nextInt(20);
    }

    private static boolean on() { return XwConfig.get(XwConfig.DMZ_BOTS_TALK) && DmzBotQuests.ready(); }

    private boolean free() {
        ServerPlayer o = b.owner();
        return o != null && o.level() == b.level() && o.distanceToSqr(b) < 64 * 64 && !b.journey() && !b.isPassenger() && !b.isNoAi()
                && b.errand == null && BotErrands.danger(b) == null;
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public boolean canUse() {
        if (b.tickCount < nextLook) return false;
        nextLook = b.tickCount + 20;
        if (!on() || !free()) return false;
        for (String[] j : DmzBotQuests.talkJobs(b)) {
            String id = j[2] + "|" + j[0];
            Integer rest = resting.get(id);
            if (rest != null && b.tickCount < rest) continue;
            Entity e = find(b, j[1], true);
            if (e == null) continue;
            job = j; to = e;
            return true;
        }
        return false;
    }

    @Override public boolean canContinueToUse() { return to != null && !done && to.isAlive() && to.level() == b.level() && b.tickCount < until && free(); }

    @Override public void start() {
        closest = 1e9; stuck = 0; done = false;
        until = b.tickCount + GIVE_UP;
        PartyBots.testLog(b, "dmz talk: going to " + job[1] + " (" + job[2] + " " + job[0] + ", " + (real(to) ? "real" : "copy")
                + String.format(Locale.ROOT, " %.1f blocks)", Math.sqrt(to.distanceToSqr(b))));
    }

    @Override public void stop() {
        if (!done && job != null) {                                                  // (the fight, the owner walking off, out of reach: later, or you talk for it)
            resting.put(job[2] + "|" + job[0], b.tickCount + RETRY);
            PartyBots.testLog(b, "dmz talk: gave up on " + job[1] + " for now");
        }
        b.getNavigation().stop();
        to = null; job = null; done = false;
    }

    @Override public void tick() {
        if (to == null || job == null) return;
        b.getLookControl().setLookAt(to, 30F, 30F);
        double d2 = b.distanceToSqr(to);
        if (d2 > 2.8 * 2.8) {
            if (d2 < closest - 0.25) { closest = d2; stuck = 0; }
            if (++stuck > 100) { until = 0; return; }                                 // (5 s without getting closer)
            if (b.getNavigation().isDone() || b.tickCount % 10 == 0) b.getNavigation().moveTo(to, 1.1);
            return;
        }
        b.getNavigation().stop();
        b.swing(InteractionHand.MAIN_HAND);
        boolean ok = job[2].equals("take") ? DmzBotQuests.botTook(b, to) : DmzBotQuests.botTalked(b, to);
        PartyBots.testLog(b, "dmz talk: talked to " + job[1] + " (" + job[2] + " " + job[0] + ") counted=" + ok);
        done = true;
        if (!ok) resting.put(job[2] + "|" + job[0], b.tickCount + RETRY);
        ServerPlayer o = b.owner();
        if (ok && o != null) DmzTalkNpcs.update(o);                                  // (a copy nobody needs any more leaves now)
    }

    // ------------------------------------------------------------------ who, where

    static boolean real(Entity e) { return !e.getPersistentData().getBoolean(DmzTalkNpcs.TAG); }

    /**
     * The person with this id the bot can go to: the real one within 48 blocks it can walk to first, else a copy that
     * came for its owner (also within 48 blocks). Null = nobody.
     */
    static Entity find(PartyBotEntity b, String npc, boolean copies) {
        if (npc == null || npc.isBlank()) return null;
        List<Entity> found = b.level().getEntities((Entity) null, b.getBoundingBox().inflate(REACH, 24, REACH), x -> x.isAlive()
                && ForgeRegistries.ENTITY_TYPES.getKey(x.getType()).getNamespace().equals("dragonminez") && (copies || real(x)) && npc.equals(DmzTalkNpcs.idOf(x)));
        found.sort(Comparator.comparing((Entity x) -> !real(x)).thenComparingDouble(x -> x.distanceToSqr(b)));
        for (Entity e : found) {
            if (e.distanceToSqr(b) < 2.8 * 2.8 || reachable(b, e)) return e;
        }
        return null;
    }

    private static boolean reachable(PartyBotEntity b, Entity e) {
        try {
            Path p = b.getNavigation().createPath(e, 1);
            if (p != null && p.canReach()) return true;
            if (p == null || p.getEndNode() == null) return false;
            Vec3 end = Vec3.atBottomCenterOf(p.getEndNode().asBlockPos());
            return end.distanceToSqr(e.position()) < 3 * 3;                          // (close enough to talk)
        } catch (Throwable t) { return false; }
    }

    /** When you start a mission: its giver, if they're really close by the bot (it goes and takes it from them). */
    static Entity giverNearby(PartyBotEntity b, Object quest) {
        if (quest == null || !on() || b.isNoAi() || b.journey() || BotErrands.danger(b) != null) return null;
        try {
            String giver = (String) DmzBotQuests.m(quest, "getQuestGiver");
            return find(b, giver, false);
        } catch (Throwable t) { return null; }
    }
}
