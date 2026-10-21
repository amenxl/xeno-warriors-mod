package com.xenowarriors.fox;

/** Skins a tamed fox can wear; the better ones unlock as it levels up. Index 0 = its natural coat. */
public final class FoxSkins {
    public record Skin(String id, String name, int level) {}

    public static final Skin[] ALL = {
            new Skin("classic", "Natural", 1),
            new Skin("ember", "Ember", 5),
            new Skin("midnight", "Midnight", 10),
            new Skin("sakura", "Sakura", 15),
            new Skin("kitsune", "Kitsune", 20),
            new Skin("golden", "Golden", 30),
            new Skin("celestial", "Celestial", 40),
            new Skin("fox_king", "Fox King", 50),
    };

    private FoxSkins() {}

    public static Skin get(int i) {
        return ALL[Math.max(0, Math.min(ALL.length - 1, i))];
    }

    public static boolean unlocked(int skin, int level) {
        return skin >= 0 && skin < ALL.length && level >= ALL[skin].level();
    }

    /** How many bag slots a fox has at this level: 9, then 18 at level 10, 27 at level 25. */
    public static int bagSlots(int level) {
        return level >= 25 ? 27 : level >= 10 ? 18 : 9;
    }
}
