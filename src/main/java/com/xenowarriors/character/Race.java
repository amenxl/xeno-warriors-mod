package com.xenowarriors.character;

/**
 * The races of Xeno Warriors Recarnet (from the user): who a character is, separate from whether they can fly.
 * Rebringers were reincarnated by Kid as copies of Xeno; Xeno has traits of every race, but a Rebringer only takes a
 * race's traits when someone of that race passes their blood on (Kaeru took Fox Girl's: she's a Fox Kin with a tail).
 */
public enum Race {
    HUMAN("Human", 0xC0C0A0),
    MODIFIED_HUMAN("Modified Human", 0x7AD0C0),          // Government modifications: Ultima, Kyle, high-rank soldiers
    SKYLANDER("Skylander", 0x80C8FF),                    // angel descendants
    ARTIFICIAL_SKYLANDER("Artificial Skylander", 0xF080C0),   // a human made into one (Technica)
    DEMON_SKYLANDER("Demon Skylander", 0xC06080),        // a Skylander fused with a demon (Lexy, Kill)
    DEMON("Demon", 0xD04040),
    DEVIL("Devil", 0xA01020),                            // stronger than demons (Ai-oni, Duo)
    FOX_KIN("Fox Kin", 0xFF9A40),
    SEEDLING("Seedling", 0x70D050),
    REBRINGER("Rebringer", 0xFFB060),
    ANGEL("Angel", 0xFFF2B0),                            // what Skylanders descend from; creative-only for You characters for now
    YOU("You", 0xC8A0FF);                                // made by the Government to capture and control distortions

    /** The races a You character can be made as, in creator order. ANGEL needs creative mode. */
    public static final Race[] CREATABLE = {HUMAN, FOX_KIN, SEEDLING, SKYLANDER, DEMON, YOU, ANGEL};

    /** Creator blurb: what this race is and what you get. */
    public String info() {
        return switch (this) {
            case HUMAN -> "The everyday people of Recarnet. No special traits, but humans grow into their element the fastest and can earn Government modifications later on.";
            case FOX_KIN -> "Fox people, like Fox Girl. Nimble healers: Fox Heal from the start, and the Kitsune attack grows through three tiers as you level.";
            case SEEDLING -> "Plant people. You heal while in water and live on sunlight instead of food - at night, carry Sunlight. If you fall, you become a seed: 30 seconds in water and you sprout back with half your health, but an enemy can break the seed.";
            case SKYLANDER -> "Angel descendants with wings. You can fly (H puts the wings away), and you choose a Lunar or Solar form that empowers you at night or by day.";
            case DEMON -> "Horned and winged, with a human form to blend in (the transform key switches). Demons hit hard and can one day become Devils.";
            case YOU -> "Made by the Government to capture and control distortions. Hardy and balanced; the capture skill comes later.";
            case ANGEL -> "The first winged ones. Far stronger than anyone else from level 1, with seraph wings. Creative mode only for now.";
            default -> title;
        };
    }

    /** Elements a You character of this race may pick. */
    public com.xenowarriors.ka.Element[] elements() {
        return switch (this) {
            case SKYLANDER, ANGEL -> com.xenowarriors.ka.Element.SKYLANDER;
            case DEMON -> new com.xenowarriors.ka.Element[]{com.xenowarriors.ka.Element.FIRE, com.xenowarriors.ka.Element.SHADOW, com.xenowarriors.ka.Element.SMOKE, com.xenowarriors.ka.Element.POISON, com.xenowarriors.ka.Element.LIGHTNING};
            case SEEDLING -> new com.xenowarriors.ka.Element[]{com.xenowarriors.ka.Element.PLANT, com.xenowarriors.ka.Element.WATER, com.xenowarriors.ka.Element.EARTH, com.xenowarriors.ka.Element.LIGHT, com.xenowarriors.ka.Element.POISON};
            case FOX_KIN -> new com.xenowarriors.ka.Element[]{com.xenowarriors.ka.Element.FIRE, com.xenowarriors.ka.Element.WIND, com.xenowarriors.ka.Element.SOUND, com.xenowarriors.ka.Element.LIGHTNING, com.xenowarriors.ka.Element.ICE};
            default -> com.xenowarriors.ka.Element.HUMAN;
        };
    }

    /** Has wings and can fly (as a You character). */
    public boolean wings() { return this == SKYLANDER || this == ANGEL || this == DEMON || skylanderKind(); }

    public final String title;
    public final int color;

    Race(String title, int color) { this.title = title; this.color = color; }

    /** Skylanders of any kind (born, made, or fused with a demon). */
    public boolean skylanderKind() { return this == SKYLANDER || this == ARTIFICIAL_SKYLANDER || this == DEMON_SKYLANDER; }

    /** The race of whoever this player is playing - a You character's own race, or their show character's. */
    public static Race of(net.minecraft.world.entity.player.Player p) {
        CharacterType t = ModCapabilities.getType(p);
        if (t == CharacterType.YOU) {
            var d = p.getCapability(ModCapabilities.PLAYER_CHARACTER).orElse(null);
            return d == null ? HUMAN : d.you().race;
        }
        return of(t);
    }

    public static Race of(CharacterType t) {
        return switch (t) {
            case MIKIKO, KAERU -> REBRINGER;
            case ULTIMA -> MODIFIED_HUMAN;
            case TECHNICA -> ARTIFICIAL_SKYLANDER;
            case LEXY, KILL -> DEMON_SKYLANDER;
            case AIONI -> DEVIL;
            case RHALIX, KYRIE, MAX, SAM, LOGAN, KEITA -> SKYLANDER;
            default -> HUMAN;                            // Stevie, Garry, Yuri, Zane
        };
    }

    /** The race as shown on screens (with blood a Rebringer has taken on). */
    public static String label(CharacterType t) {
        return t == CharacterType.KAERU ? "Rebringer (Fox Kin blood)" : of(t).title;
    }
}
