package com.xenowarriors.ka;

import com.xenowarriors.corrupted.CSoulBossEntity;
import com.xenowarriors.corrupted.CorruptedSoulEntity;
import com.xenowarriors.corrupted.CorruptedVillagerEntity;
import com.xenowarriors.npc.HumanNpcEntity;
import com.xenowarriors.npc.SkylanderEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

/** Karma for enemies and NPCs: a pool that KAs (and elements) spend, refilling slowly like a player's. */
public final class EnemyKarma {
    private static final String K = "xw_karma", MAX = "xw_karma_max";

    private EnemyKarma() {}

    public static boolean usesKarma(LivingEntity e) {
        return e instanceof HumanNpcEntity || e instanceof SkylanderEntity || e instanceof CorruptedSoulEntity
                || e instanceof CorruptedVillagerEntity || e instanceof CSoulBossEntity;
    }

    public static float max(LivingEntity e) {
        CompoundTag t = e.getPersistentData();
        if (!t.contains(MAX)) {
            var r = e.getRandom();
            float m = e instanceof CSoulBossEntity ? 400 : e instanceof SkylanderEntity s ? (s.guardian() ? 160 : 60 + r.nextInt(80))
                    : e instanceof HumanNpcEntity ? 40 + r.nextInt(60) : 50 + r.nextInt(70);
            if (Kas.of(e) != null) m += 40;                                     // KA users have more to draw on
            t.putFloat(MAX, m);
            t.putFloat(K, m);
        }
        return t.getFloat(MAX);
    }

    public static float get(LivingEntity e) { max(e); return e.getPersistentData().getFloat(K); }

    public static boolean has(LivingEntity e, float amount) { return get(e) >= amount; }

    public static void spend(LivingEntity e, float amount) { e.getPersistentData().putFloat(K, Math.max(0, get(e) - amount)); }

    /** Once a second: +1.5, like a player. */
    public static void regen(LivingEntity e) {
        float m = max(e);
        e.getPersistentData().putFloat(K, Math.min(m, get(e) + 1.5F));
    }

    /** A KA's cost for this user right now: rises 10% per point of strain. */
    public static int kaCost(LivingEntity user, Ka ka) {
        return Math.round(ka.karma * (1F + Kas.strain(user) * 0.1F));
    }
}
