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
    REBRINGER("Rebringer", 0xFFB060);

    public final String title;
    public final int color;

    Race(String title, int color) { this.title = title; this.color = color; }

    /** Skylanders of any kind (born, made, or fused with a demon). */
    public boolean skylanderKind() { return this == SKYLANDER || this == ARTIFICIAL_SKYLANDER || this == DEMON_SKYLANDER; }

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
