package com.xenowarriors.compat;

import com.xenowarriors.XwConfig;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.*;

/**
 * With DragonMineZ: a bot teammate with spare TP walks over to a DragonMineZ master standing within 40 blocks (Goku,
 * Roshi, Piccolo, King Kai, Vegeta...), talks to them and learns one of the ki or strike techniques they teach - the
 * way you'd buy it from them ({@link DmzBotTechniques#learn}). The same rules as its other talks ({@link DmzBotTalk}):
 * only when things are quiet, one at a time, giving up after 30 s or when it can't get closer. Off with
 * botsTalkToNpcs=false (it then never walks to masters; nothing else changes).
 */
public final class DmzBotMasters extends Goal {
    private static final int LOOK_EVERY = 100, GIVE_UP = 20 * 30, RETRY = 20 * 60;

    private final PartyBotEntity b;
    private final Map<String, Integer> resting = new HashMap<>();
    private int nextLook, until, stuck;
    private double closest;
    private Entity to;
    private String master, skill;
    private boolean done;
    /** (Its TP and techniques when it last found nothing to learn: no need to look again until they change.) */
    private int nothingSig;

    public DmzBotMasters(PartyBotEntity b) {
        this.b = b;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        nextLook = 80 + b.getRandom().nextInt(40);
        GOALS.put(b, this);
    }

    private static boolean on() { return XwConfig.get(XwConfig.DMZ_BOTS_TALK) && DmzBotTechniques.ready() && DmzBotQuests.ready(); }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    /** The masters within reach, nearest first (real ones, and the copies that came to its owner). */
    private List<Entity> masters() {
        double r = DmzBotTechniques.MASTER_REACH;
        List<Entity> found = b.level().getEntities((Entity) null, b.getBoundingBox().inflate(r, 16, r),
                x -> x.isAlive() && DmzBotTechniques.masterName(x) != null && x.distanceToSqr(b) <= r * r);
        found.sort(Comparator.comparingDouble(x -> x.distanceToSqr(b)));
        return found;
    }

    @Override public boolean canUse() {
        if (b.tickCount < nextLook) return false;
        nextLook = b.tickCount + LOOK_EVERY;
        if (!on() || !DmzBotTalk.free(b) || b.getTarget() != null) return false;
        if (b.owner() == null || DmzPerCharacter.viewing(b.owner()) != null) return false;   // (its Dragon Ball menu open: its TP is yours to spend)
        List<Entity> ms = masters();
        if (ms.isEmpty()) return false;
        var tag = DmzPerCharacter.saved(b.owner(), DmzPerCharacter.botKey(b));
        if (tag == null) return false;
        int sig = Objects.hash(tag.getCompound("Resources").getFloat("TrainingPointsF"), tag.getCompound("Techniques").hashCode(), tag.getCompound("Stats").hashCode(),
                ms.stream().map(DmzBotTechniques::masterName).toList());
        if (sig == nothingSig) return false;
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null || d.live) return false;
        try {
            for (Entity e : ms) {
                String name = DmzBotTechniques.masterName(e);
                Integer rest = resting.get(name);
                if (rest != null && b.tickCount < rest) continue;
                StringBuilder why = new StringBuilder();
                String s = DmzBotTechniques.lesson(d, name, why);
                if (s == null) continue;
                if (e.distanceToSqr(b) > 2.8 * 2.8 && !DmzBotTalk.reachable(b, e)) { resting.put(name, b.tickCount + RETRY); continue; }
                to = e; master = name; skill = s;
                return true;
            }
        } catch (Throwable t) { com.xenowarriors.XenoWarriors.LOGGER.warn("A bot couldn't look at what a DragonMineZ master teaches", t); }
        nothingSig = sig;
        return false;
    }

    @Override public boolean canContinueToUse() {
        return to != null && !done && to.isAlive() && to.level() == b.level() && b.tickCount < until && DmzBotTalk.free(b) && b.getTarget() == null;
    }

    @Override public void start() {
        closest = 1e9; stuck = 0; done = false;
        until = b.tickCount + GIVE_UP;
        PartyBots.testLog(b, "dmz master: going to " + master + " to learn " + skill + String.format(Locale.ROOT, " (%.1f blocks)", Math.sqrt(to.distanceToSqr(b))));
    }

    @Override public void stop() {
        if (!done && master != null) {
            resting.put(master, b.tickCount + RETRY);
            PartyBots.testLog(b, "dmz master: gave up on " + master + " for now");
        }
        b.getNavigation().stop();
        to = null; master = null; skill = null; done = false;
    }

    @Override public void tick() {
        if (to == null || master == null || done) return;
        b.getLookControl().setLookAt(to, 30F, 30F);
        double d2 = b.distanceToSqr(to);
        if (d2 > 2.8 * 2.8) {
            if (d2 < closest - 0.25) { closest = d2; stuck = 0; }
            if (++stuck > 100) { until = 0; return; }
            if (b.getNavigation().isDone() || b.tickCount % 10 == 0) b.getNavigation().moveTo(to, 1.1);
            return;
        }
        b.getNavigation().stop();
        b.swing(InteractionHand.MAIN_HAND);
        boolean ok = DmzBotTechniques.learn(b, to, master, skill);
        PartyBots.testLog(b, "dmz master: talked to " + master + ", learned " + skill + " = " + ok);
        done = true;
        if (ok) nextLook = b.tickCount + 40;                                         // (maybe another one from them shortly)
        else resting.put(master, b.tickCount + RETRY);
    }

    private static final Map<PartyBotEntity, DmzBotMasters> GOALS = new WeakHashMap<>();

    /** (Tests) this bot looks for a master again right away. */
    public static void lookNowForTest(PartyBotEntity b) { DmzBotMasters g = GOALS.get(b); if (g != null) { g.nextLook = 0; g.nothingSig = 0; g.resting.clear(); } }
}
