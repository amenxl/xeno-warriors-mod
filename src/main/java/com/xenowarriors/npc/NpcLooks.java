package com.xenowarriors.npc;

/** The townspeople's looks (textures and models under npc/), and the four permanently possessed. */
public final class NpcLooks {
    public static final String[] HUMANS = {"capboy", "redhair", "messy", "spiky", "waver", "gunner", "briefcase", "woman", "girl", "phone", "soldier"};
    public static final String[] DARK = {"dark_slime", "dark_vest", "dark_kick", "dark_hood"};

    private NpcLooks() {}

    public static String human(int variant) {
        return HUMANS[Math.floorMod(variant, HUMANS.length)];
    }

    public static String dark(int variant) {
        return DARK[Math.floorMod(variant, DARK.length)];
    }
}
