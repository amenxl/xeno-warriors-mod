package com.xenowarriors.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/** What's around a character (for their face): enemies close by, other people, or only animals and peaceful mobs. */
public final class Surroundings {
    public static final int NONE = 0, PEACEFUL = 1, PEOPLE = 2, ENEMIES = 3;
    private static final double ENEMY_RANGE = 14, OTHER_RANGE = 10;
    private static final Map<Integer, int[]> CACHE = new HashMap<>();    // id -> {state, tick checked}

    private Surroundings() {}

    public static int of(Player p) {
        int[] c = CACHE.computeIfAbsent(p.getId(), k -> new int[]{NONE, -100});
        if (Math.abs(p.tickCount - c[1]) < 10) return c[0];
        c[1] = p.tickCount;
        var level = Minecraft.getInstance().level;
        if (level == null) return c[0] = NONE;
        boolean people = false, peaceful = false;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(ENEMY_RANGE),
                e -> e != p && e.isAlive() && !e.isSpectator() && !e.isInvisible())) {
            if (e instanceof Enemy) {
                CACHE.put(p.getId(), new int[]{ENEMIES, p.tickCount});
                return ENEMIES;
            }
            if (e.distanceToSqr(p) > OTHER_RANGE * OTHER_RANGE) continue;
            if (e instanceof Player) people = true;
            else peaceful = true;
        }
        c[0] = people ? PEOPLE : peaceful ? PEACEFUL : NONE;
        return c[0];
    }
}
