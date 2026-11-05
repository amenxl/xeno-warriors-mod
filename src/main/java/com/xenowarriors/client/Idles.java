package com.xenowarriors.client;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * One body animation at a time. Every idle-type animation (Mikiko's sitting, stretches and song dances, dancing to
 * Garry's beatbox, Garry's beatbox, Keita dozing, Yuri's laptop, Rhalix's book, standing in the rain...) holds the
 * character's body while it shows ({@link #hold}); the others see it's {@link #taken} and don't start (their
 * stand-still timers start over), so two idles never play over each other. Actions - skills, combos, casts, poses,
 * attacks, guarding (see {@link Busy#action}) - beat every idle.
 */
public final class Idles {
    public static final String MIKIKO = "mikiko", GARRY = "garry_beatbox", DOZE = "keita_doze", LAPTOP = "yuri_laptop",
            BOOK = "rhalix_book", RAIN = "rain";
    private static final int LINGER = 3;               // ticks a hold lasts without being renewed
    private static final Map<Integer, Object[]> HOLD = com.xenowarriors.client.ClientReset.track(new HashMap<>());   // id -> {who, tick}

    private Idles() {}

    private static Object[] live(Player p) {
        Object[] h = HOLD.get(p.getId());
        if (h == null) return null;
        int age = p.tickCount - (Integer) h[1];
        if (age < 0 || age > LINGER) { HOLD.remove(p.getId()); return null; }
        return h;
    }

    /** Someone other than {@code who} has the body right now. */
    public static boolean taken(Player p, String who) {
        Object[] h = live(p);
        return h != null && !h[0].equals(who);
    }

    /** {@code who} is showing this tick: keeps (or takes, if free) the body. False if another idle already has it. */
    public static boolean hold(Player p, String who) {
        if (taken(p, who)) return false;
        HOLD.put(p.getId(), new Object[]{who, p.tickCount});
        if (HOLD.size() > 128) HOLD.clear();
        return true;
    }

    /** Any idle showing. */
    public static boolean any(Player p) { return live(p) != null; }

    /** Who has the body (tests), or "none". */
    public static String holder(Player p) {
        Object[] h = live(p);
        return h == null ? "none" : (String) h[0];
    }

    /** Free for {@code who}: nothing going on (no action) and no other idle showing. */
    public static boolean free(Player p, String who) {
        return !Busy.action(p) && !taken(p, who);
    }
}
