package com.xenowarriors.client;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-side skill info for the HUD and visuals. */
public final class ClientSkillState {
    private static final long[] COOLDOWN_END = new long[5];
    private static final int[] COOLDOWN_TOTAL = new int[5];
    private static final Map<UUID, Long> KISHI_SEEN = new HashMap<>();
    private static final Map<UUID, Integer> KISHI_LEVEL = new HashMap<>();
    private static final Map<Integer, KishiCast> KISHI_CASTS = new HashMap<>();

    /** When a player started their Kishi cast animation (in their own tick count) and whether they were in the air. */
    public record KishiCast(int startTick, boolean airborne) {}

    private static float karma = 100F, maxKarma = 100F;

    private ClientSkillState() {}

    public static void setKarma(float value, float max) {
        karma = value;
        maxKarma = max;
    }

    public static float karma() { return karma; }
    public static float maxKarma() { return maxKarma; }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static void setCooldown(int slot, int remaining, int total) {
        if (slot < 0 || slot >= 5) return;
        COOLDOWN_END[slot] = now() + remaining;
        COOLDOWN_TOTAL[slot] = total;
    }

    public static int cooldownTicks(int slot) {
        return (int) Math.max(0, COOLDOWN_END[slot] - now());
    }

    /** 1 = just used, 0 = ready. */
    public static float cooldownFraction(int slot) {
        int total = COOLDOWN_TOTAL[slot];
        return total <= 0 ? 0F : Math.min(1F, cooldownTicks(slot) / (float) total);
    }

    /** Called every tick by a Kishi dome so we know its owner is casting it. */
    public static void markKishi(UUID owner, int level) {
        if (owner == null) return;
        KISHI_SEEN.put(owner, now());
        KISHI_LEVEL.put(owner, level);
    }

    public static boolean isCastingKishi(UUID player) {
        Long seen = KISHI_SEEN.get(player);
        return seen != null && now() - seen <= 2;
    }

    public static int kishiLevel(UUID player) {
        return isCastingKishi(player) ? KISHI_LEVEL.getOrDefault(player, 1) : 0;
    }

    public static void startKishiCast(int entityId, boolean airborne) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        net.minecraft.world.entity.Entity e = mc.level.getEntity(entityId);
        if (e != null) KISHI_CASTS.put(entityId, new KishiCast(e.tickCount, airborne));
    }

    /** The Kishi cast a player is in the middle of, or null. */
    public static KishiCast kishiCast(net.minecraft.world.entity.Entity e) {
        KishiCast cast = KISHI_CASTS.get(e.getId());
        if (cast == null) return null;
        int age = e.tickCount - cast.startTick();
        if (age < 0 || age > com.xenowarriors.client.KishiCastAnimation.DURATION) {
            KISHI_CASTS.remove(e.getId());
            return null;
        }
        return cast;
    }

    public static void clear() {
        karma = 100F;
        maxKarma = 100F;
        KISHI_CASTS.clear();
        for (int i = 0; i < 5; i++) {
            COOLDOWN_END[i] = 0;
            COOLDOWN_TOTAL[i] = 0;
        }
        KISHI_SEEN.clear();
        KISHI_LEVEL.clear();
    }
}
