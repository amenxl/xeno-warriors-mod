package com.xenowarriors.ka;

import net.minecraft.ChatFormatting;

/**
 * KAs: rare abilities that manipulate one thing. They go in the super ultimate slot. Every KA costs its user something
 * tied to what it manipulates, and the more it's used the worse that gets (see Kas.strain) until it burns out.
 */
public enum Ka {
    // simple - rare on townsfolk and Skylanders
    WEIGHT(false, 30, 0x8A8A8A, "how heavy things are", "the user's own body grows heavy"),
    GRIP(false, 25, 0x9FE0FF, "friction", "the user loses their own footing"),
    STRIDE(false, 25, 0x7CFF9A, "distance", "distances stretch for the user afterwards"),
    HOLD(false, 30, 0xFFB35C, "grip on things", "the user's own hands go weak"),
    REACH(false, 35, 0xFF7AD9, "range", "the user's own reach shrinks after"),
    PACE(false, 30, 0xC9C24A, "speed", "the user's own pace drags"),
    SIGHT(false, 35, 0xFFFFFF, "where you look", "the user's own vision blurs"),
    // rare - guardians, raid leaders, bosses
    DELAY(true, 55, 0x6E7BFF, "timing", "hits on the user land all at once, later"),
    MIRROR(true, 50, 0xD8F6FF, "reflection", "some of what bounces back hits the user too"),
    TETHER(true, 60, 0xB04CFF, "connection", "the link pulls both ways"),
    FLIP(true, 45, 0xFF5050, "direction", "the user's own controls flip after"),
    MEMORY(true, 50, 0xFFD24A, "memory", "the user forgets things too"),
    SWAP(true, 45, 0x4CFFE0, "position", "the user's position slips"),
    BIND(true, 60, 0x5A1A8A, "the will to move", "the user has to hold still too");

    public final boolean rare;
    /** Karma it takes to use (more as the user's strain builds - their control slips). */
    public final int karma;
    public final int color;
    public final String manipulates, cost;

    Ka(boolean rare, int karma, int color, String manipulates, String cost) {
        this.rare = rare;
        this.karma = karma;
        this.color = color;
        this.manipulates = manipulates;
        this.cost = cost;
    }

    public String id() { return name().toLowerCase(); }

    public String title() { return name().charAt(0) + name().substring(1).toLowerCase(); }

    public ChatFormatting chat() { return rare ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA; }

    public static Ka byId(String id) {
        for (Ka k : values()) if (k.id().equals(id)) return k;
        return null;
    }

    public static final Ka[] SIMPLE = {WEIGHT, GRIP, STRIDE, HOLD, REACH, PACE, SIGHT};
    public static final Ka[] RARE = {DELAY, MIRROR, TETHER, FLIP, MEMORY, SWAP, BIND};
}
