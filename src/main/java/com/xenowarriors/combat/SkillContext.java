package com.xenowarriors.combat;

import net.minecraft.world.entity.Entity;

/**
 * "A skill is running right now, cast by X": set while a skill, ultimate, KA or Xeno Blast runs on the server and
 * carried along into its later steps (ServerScheduler tasks), so any damage it deals - even plain
 * playerAttack/indirectMagic damage - is known to be skill damage (for DragonMineZ scaling and ally safety).
 */
public final class SkillContext {
    private SkillContext() {}

    private static Entity caster;
    private static int depth;

    /** Who's casting, or null outside a skill. (Server thread only.) */
    public static Entity caster() { return depth > 0 ? caster : null; }
    public static boolean active() { return depth > 0 && caster != null; }

    /** Runs {@code r} as part of {@code who}'s skill. */
    public static void run(Entity who, Runnable r) {
        Entity was = caster;
        int d = depth;
        caster = who;
        depth = d + 1;
        try { r.run(); } finally { caster = was; depth = d; }
    }

    public static <T> T call(Entity who, java.util.function.Supplier<T> s) {
        Object[] out = new Object[1];
        run(who, () -> out[0] = s.get());
        @SuppressWarnings("unchecked") T t = (T) out[0];
        return t;
    }

    /** Wraps a task scheduled from inside a skill so it runs inside that skill too. */
    public static Runnable carry(Runnable r) {
        Entity who = caster();
        if (who == null) return r;
        return () -> run(who, r);
    }
}
