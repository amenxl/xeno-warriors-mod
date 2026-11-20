package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enemies don't all pile on you: some go after your bot teammates. A foe going for a player with bots close by (16
 * blocks) may turn on the nearest of those bots instead - about one foe in five in a small fight, one in three once
 * three or more are on you, and half of them once there are six or more. Each foe decides the same way every time, so
 * they don't flip back and forth.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotAggro {
    private BotAggro() {}

    /** (Tests) foes turned on bots. */
    public static int turned;
    private static final Map<UUID, int[]> ON = new HashMap<>();   // player -> {game time counted, foes on them}

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent e) {
        if (!(e.getEntity() instanceof Mob m) || m.tickCount % 40 != 17 || !(m.level() instanceof ServerLevel l) || m instanceof PartyBotEntity) return;
        if (!(m.getTarget() instanceof ServerPlayer p) || !p.isAlive() || p.isCreative() || p.isSpectator()) return;
        if (!(m instanceof Enemy) && !m.getTags().contains(com.xenowarriors.story.StoryRun.FOE_TAG)) return;
        if (m instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame()) return;
        if (com.xenowarriors.story.StoryRun.inSplit(m)) return;                // (a split story fight: the rooms decide)
        int attackers = attackers(l, p);
        int every = attackers >= 6 ? 2 : attackers >= 3 ? 3 : 5;
        if (Math.floorMod(m.getUUID().hashCode(), every) != 0) return;
        PartyBotEntity best = null; double bd = 16 * 16;
        for (PartyBotEntity b : PartyBots.bots(p)) {
            if (b.level() != l || b.downed() || b.journey() || !b.isAlive() || b.isInvisible()) continue;
            double d = b.distanceToSqr(m);
            if (d < bd) { bd = d; best = b; }
        }
        if (best == null) return;
        m.setTarget(best);
        turned++;
    }

    /** How many foes are going for this player right now (counted once a tick). */
    private static int attackers(ServerLevel l, ServerPlayer p) {
        int[] c = ON.get(p.getUUID());
        long now = l.getGameTime();
        if (c != null && c[0] == (int) now) return c[1];
        int n = l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), x -> x.isAlive() && x.getTarget() == p).size();
        ON.put(p.getUUID(), new int[]{(int) now, n});
        return n;
    }
}
